package crow.client.utils;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.LookEvent;
import crow.client.event.impl.MoveInputEvent;
import crow.client.event.impl.UpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Universal silent-aim driver.
 *
 * <h2>Tick contract — read this before changing anything</h2>
 *
 * Vanilla runs, in this order, inside one {@code Minecraft.runTick}:
 * <pre>
 *   runTick HEAD
 *     -&gt; EntityPlayerSP.onUpdate
 *          -&gt; ... -&gt; moveEntityWithHeading -&gt; moveFlying   (MoveInputEvent)
 *          -&gt; onUpdateWalkingPlayer                        (UpdateEvent.PRE, C03 send)
 * </pre>
 * The rotation cannot change between those two points, so vanilla always
 * <i>moves</i> with exactly the yaw it <i>sends</i>. Simulation-based anticheats
 * (Grim et al.) rebuild your position delta from the yaw in the packet and the
 * nine possible WASD combinations; if the yaw you moved with differs from the
 * yaw you sent, the rebuild misses and you flag — every single tick you turn.
 *
 * Therefore the spring is stepped exactly once per tick, in {@link #beginCycle()}
 * at {@code runTick} HEAD, <b>before</b> the player entity updates. {@link #aim}
 * only submits a target for the <i>next</i> step. Both {@code moveFlying} and
 * the C03 packet then read one settled value, {@link #getServerYaw()}, and they
 * agree by construction. The cost is one tick of aim latency, which is
 * unavoidable: nothing can send a yaw that movement has already consumed.
 *
 * The visual rotation ({@code mc.thePlayer.rotationYaw/Pitch}) is left alone,
 * so the first-person camera keeps following the user's mouse. The whole
 * movement phase, however, runs with {@code rotationYaw} swapped to
 * {@link #getServerYaw()} by the {@code EntityPlayerSP.onLivingUpdate} mixin —
 * {@code moveFlying} is not its only consumer, {@code EntityLivingBase.jump()}
 * reads the field directly for the sprint-jump boost. That swap also leaves the
 * rendered head and body on the reported yaw, so F5 shows what the server sees;
 * only pitch needs {@link #beginPlayerRender}.
 *
 * Typical module usage (inside an UpdateEvent.PRE handler):
 * <pre>
 *   if (e.isPre() &amp;&amp; haveTarget) {
 *       SilentAim.Request r = new SilentAim.Request();
 *       r.yaw = computedYaw;              // aim from SilentAim.getServerYaw(),
 *       r.pitch = computedPitch;          // not from mc.thePlayer.rotationYaw
 *       r.profile = SilentAim.Profile.COMBAT;
 *       r.priority = 100;
 *       r.claimant = this;
 *       SilentAim.aim(r);
 *   }
 *   if (SilentAim.isClaimedBy(this) &amp;&amp; aimedCloseEnough()) attack();
 * </pre>
 * Writing the rotation onto the packet is not the module's job — the
 * {@code onUpdateWalkingPlayer} mixin calls {@link #applyToUpdate(UpdateEvent)}
 * after every module has been polled, so the packet carries the silent rotation
 * on every tick the movement is being steered by it, including the glide back
 * to the camera after the last module lets go.
 */
public final class SilentAim {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /* ===================================================================== */
    /* Public API                                                             */
    /* ===================================================================== */

    public enum Profile {
        /** Fast tracking for combat (KillAura). */
        COMBAT (/*stiff*/ 0.55f, /*yawCap*/ 36f, /*pitCap*/ 22f, /*minSpd*/ 1.6f, /*tremor*/ 0.10f),
        /** Medium fluidity for block-place modules (BlockIn / Scaffold). */
        PLACE  (/*stiff*/ 0.26f, /*yawCap*/ 14f, /*pitCap*/ 10f, /*minSpd*/ 0.8f, /*tremor*/ 0.06f),
        /** Slow, careful — for one-shot precise placements. */
        PRECISE(/*stiff*/ 0.34f, /*yawCap*/ 18f, /*pitCap*/ 14f, /*minSpd*/ 0.9f, /*tremor*/ 0.05f);

        final float stiffness;
        final float yawCapDeg;
        final float pitchCapDeg;
        final float minSpeedDeg;
        final float tremorAmpDeg;

        Profile(float stiffness, float yawCap, float pitchCap, float minSpeed, float tremorAmp) {
            this.stiffness = stiffness;
            this.yawCapDeg = yawCap;
            this.pitchCapDeg = pitchCap;
            this.minSpeedDeg = minSpeed;
            this.tremorAmpDeg = tremorAmp;
        }
    }

    public static final class Request {
        public float yaw;
        public float pitch;
        public Profile profile = Profile.COMBAT;
        public int priority = 0;
        /** 0 = use profile default. Otherwise overrides per-tick yaw cap. */
        public float maxYawStepDeg = 0f;
        /** 0 = use profile default. Otherwise overrides per-tick pitch cap. */
        public float maxPitchStepDeg = 0f;
        /**
         * 0 = use profile default. Higher = snappier (settles faster, less
         * smooth); lower = silkier (more ticks to arrive). Useful when a
         * module has its own user-facing "Rotation Speed" slider — wire that
         * slider through this override so the slider has a real effect on
         * settle time, not just on the per-tick clamp.
         * Practical range: 0.15 (very smooth) to 0.95 (near-snap).
         */
        public float stiffness = 0f;
        /**
         * 0 = critically damped for the effective stiffness, which is what you
         * want almost always: fastest approach with no overshoot. Raise it to
         * deliberately overdamp (slower, but guaranteed never to sail past the
         * target) — Clutch does this on its final, life-or-death placement.
         * Lowering it below critical makes the spring ring around the target.
         */
        public float damping = 0f;
        /**
         * Disable settle-tremor for this request. Use for placement modules where
         * any sub-degree wobble breaks raycast-based anticheat checks
         * (e.g. Grim's RotationPlace).
         */
        public boolean disableTremor = false;
        /** Apply the target delta directly while still snapping it to the mouse GCD. */
        public boolean instant = false;
        /** Diagnostic / debug; not used for control. */
        public Object claimant;
    }

    /**
     * Advance the silent rotation by one tick. Called from {@code Minecraft.runTick}
     * HEAD — before the player entity updates, so {@code moveFlying} and the C03
     * packet later in the same tick both read the value produced here.
     */
    public static void beginCycle() {
        refreshContext();
        if (mc.thePlayer == null) return;

        Request req = pending;
        pending = null;
        currentPriority = Integer.MIN_VALUE;

        if (req == null && !active) return;

        if (!active) {
            // First tick of a new engagement. moveFlying has not run yet this
            // tick, so seeding from the live rotation here still leaves movement
            // and packet agreeing on the stepped value below.
            seedFromPlayer();
            active = true;
        }

        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;

        if (req != null) {
            currentReq = req;
            returnTicks = 0;
            stepSpring(req);
            return;
        }

        // Nobody aimed last tick. Glide back to the user's camera instead of
        // dropping the rotation: an instant hand-back puts one large, non
        // GCD-aligned delta on the wire at the end of every engagement, and
        // desyncs movement from the packet on the tick it happens.
        currentReq = null;
        RETURN.yaw = mc.thePlayer.rotationYaw;
        RETURN.pitch = mc.thePlayer.rotationPitch;
        stepSpring(RETURN);
        if (++returnTicks >= RETURN_MAX_TICKS || atCamera()) {
            standDown();
        }
    }

    /**
     * Submit an aim target for the next step. Highest priority in a tick wins;
     * ties go to the first caller. The rotation this produces goes out on the
     * <i>following</i> tick's packet — see the class docs.
     */
    public static void aim(Request req) {
        if (req == null) return;
        if (mc == null || mc.thePlayer == null) return;

        refreshContext();

        if (pending == null || req.priority > currentPriority) {
            pending = req;
            currentPriority = req.priority;
        }
    }

    /** Whether silent aim is currently driving the server-side rotation. */
    public static boolean isActive() {
        return active;
    }

    /** True only while {@code claimant} owns the rotation currently being sent. */
    public static boolean isClaimedBy(Object claimant) {
        return claimant != null
                && active
                && currentReq != null
                && currentReq.claimant == claimant;
    }

    /**
     * Hand the rotation back when it still belongs to {@code claimant}. This does
     * not snap: the next {@link #beginCycle()} starts the glide back to the
     * camera, keeping movement and packet in step the whole way down.
     */
    public static void release(Object claimant) {
        if (claimant == null) return;
        if (pending != null && pending.claimant == claimant) {
            pending = null;
            currentPriority = Integer.MIN_VALUE;
        }
        if (currentReq != null && currentReq.claimant == claimant) {
            currentReq = null;
        }
    }

    /** Server yaw being sent in the C03 packet, and used by {@code moveFlying}. */
    public static float getServerYaw() {
        return serverYaw;
    }

    /** Server pitch being sent in the C03 packet. */
    public static float getServerPitch() {
        return serverPitch;
    }

    /** Server yaw of the previous tick — {@code serverYaw - prevServerYaw} is this tick's applied step. */
    public static float getPrevServerYaw() {
        return prevServerYaw;
    }

    public static float getPrevServerPitch() {
        return prevServerPitch;
    }

    /**
     * Write the current server rotation onto an UpdateEvent. Called from the
     * {@code onUpdateWalkingPlayer} mixin once every module has been polled;
     * modules do not need to call it themselves.
     */
    public static void applyToUpdate(UpdateEvent e) {
        if (!active) return;
        e.setYaw(serverYaw);
        e.setPitch(serverPitch);
    }

    /**
     * Align the client-side look <i>vector</i> ({@code Entity.getLook}, used for
     * ray traces) to the silent rotation while in third person. This is not the
     * camera and not the rendered model — for the model see
     * {@link #beginPlayerRender}.
     */
    public static void applyToLook(LookEvent e) {
        if (!active) return;
        if (mc.gameSettings == null || mc.gameSettings.thirdPersonView == 0) return;
        e.setYaw(serverYaw);
        e.setPitch(serverPitch);
        e.setPrevYaw(prevServerYaw);
        e.setPrevPitch(prevServerPitch);
    }

    /**
     * Steer movement by the reported yaw, but keep WASD pointing where the
     * <i>camera</i> is facing.
     *
     * <p>The yaw override is not negotiable — the server rebuilds the position
     * delta from the yaw in the packet, so walking on the camera yaw while
     * sending the aim yaw is a guaranteed simulation mismatch. What <i>is</i>
     * negotiable is which movement input we hand it. Previously we passed the
     * user's raw input straight through, so WASD silently rotated with the aim:
     * with the aim 60° off the camera, W walked 60° off course.
     *
     * <p>Instead, work out the world direction the input asked for in the camera
     * frame, then pick the {@code (strafe, forward)} pair that comes closest to
     * it in the reported-yaw frame. The candidates are exactly the eight vanilla
     * key combinations, rescaled by the magnitude the caller already applied
     * (0.98 walking, ×0.3 sneaking, ×0.2 using an item — both axes always carry
     * the same factor, so one scalar preserves it). That matters: the server
     * reconstructs movement by replaying those same nine combinations against
     * the packet yaw, so anything off-grid would fail to rebuild. This stays on
     * the grid, which is why it needs no "move fix" toggle and cannot desync.
     *
     * <p>ponytail: eight directions means the result can sit up to 22.5° off the
     * direction asked for, and that ceiling is inherent — a rotated yaw simply
     * has no other legal directions to offer. In practice the aim and the camera
     * both point near the target, so the offset is small and the snap rarely
     * bites. Sub-grid accuracy would need off-grid inputs, which is the desync
     * this exists to avoid.
     */
    public static void applyToMove(MoveInputEvent e) {
        if (!active) return;
        e.setYaw(serverYaw);

        float rawStrafe = e.getStrafe();
        float rawForward = e.getForward();
        float scale = Math.max(Math.abs(rawStrafe), Math.abs(rawForward));
        if (scale <= 1.0E-4f) return;

        double cam = Math.toRadians(cameraYaw);
        double sinCam = Math.sin(cam), cosCam = Math.cos(cam);
        double wantX = rawStrafe * cosCam - rawForward * sinCam;
        double wantZ = rawForward * cosCam + rawStrafe * sinCam;
        double wantLen = Math.sqrt(wantX * wantX + wantZ * wantZ);
        if (wantLen <= 1.0E-6) return;
        wantX /= wantLen;
        wantZ /= wantLen;

        double srv = Math.toRadians(serverYaw);
        double sinSrv = Math.sin(srv), cosSrv = Math.cos(srv);

        int bestF = 0, bestS = 0;
        double bestDot = -Double.MAX_VALUE, heldDot = -Double.MAX_VALUE;
        for (int fi = -1; fi <= 1; fi++) {
            for (int si = -1; si <= 1; si++) {
                if (fi == 0 && si == 0) continue;
                double vx = si * cosSrv - fi * sinSrv;
                double vz = fi * cosSrv + si * sinSrv;
                double dot = (vx * wantX + vz * wantZ) / Math.sqrt(vx * vx + vz * vz);
                if (dot > bestDot) { bestDot = dot; bestF = fi; bestS = si; }
                if (fi == heldMoveF && si == heldMoveS) heldDot = dot;
            }
        }

        // Hysteresis. The settle tremor jitters the reported yaw by a fraction of
        // a degree, so a target sitting on a 45° boundary would otherwise flip
        // between two directions every tick. Keep the previous choice until the
        // new one is clearly better.
        if (heldDot > -Double.MAX_VALUE && bestDot - heldDot < 0.04) {
            bestF = heldMoveF;
            bestS = heldMoveS;
        }
        heldMoveF = bestF;
        heldMoveS = bestS;

        e.setStrafe(bestS * scale);
        e.setForward(bestF * scale);
    }

    /**
     * Run the movement phase on the reported yaw. Called around
     * {@code super.onLivingUpdate()} from the {@code EntityPlayerSP} mixin.
     *
     * <p>{@code moveFlying} is not the only consumer of the field —
     * {@code EntityLivingBase.jump()} reads {@code rotationYaw} directly for the
     * sprint-jump boost — so the whole phase is swapped rather than each call
     * site patched. Stashing the camera yaw here is also what lets
     * {@link #applyToMove} still know which way the user was facing.
     */
    public static void beginMovementPhase() {
        if (!active || movementSwapped || mc.thePlayer == null) return;
        cameraYaw = mc.thePlayer.rotationYaw;
        mc.thePlayer.rotationYaw = serverYaw;
        movementSwapped = true;
    }

    /** Undo {@link #beginMovementPhase}, restoring the camera yaw. */
    public static void endMovementPhase() {
        if (!movementSwapped || mc.thePlayer == null) return;
        mc.thePlayer.rotationYaw = cameraYaw;
        movementSwapped = false;
    }

    /**
     * Put the rendered model's <i>pitch</i> on the silent rotation for the length
     * of the render call. Driven from {@code RenderPlayerEvent.Pre}, undone in
     * {@link #endPlayerRender}.
     *
     * <p>Yaw needs no help here. The movement phase now runs with
     * {@code rotationYaw} swapped to {@link #getServerYaw()} (see the
     * {@code EntityPlayerSP.onLivingUpdate} mixin), so
     * {@code EntityPlayer.onLivingUpdate}'s {@code rotationYawHead = rotationYaw}
     * and the {@code renderYawOffset} update both land on the reported yaw on
     * their own, keeping vanilla's head/body lag and its ±75° clamp intact.
     *
     * <p>Pitch is the exception: it is restored after the movement phase because
     * {@code rotationPitch} <i>is</i> the first-person camera, so the only place
     * it can be corrected is inside the render call.
     */
    public static void beginPlayerRender(net.minecraft.entity.player.EntityPlayer p) {
        if (!active || renderSwapped || p == null || p != mc.thePlayer) return;

        stashPitch = p.rotationPitch;
        stashPrevPitch = p.prevRotationPitch;

        p.rotationPitch = serverPitch;
        p.prevRotationPitch = prevServerPitch;

        renderSwapped = true;
    }

    /** Undo {@link #beginPlayerRender}. Called from {@code RenderPlayerEvent.Post}. */
    public static void endPlayerRender(net.minecraft.entity.player.EntityPlayer p) {
        if (!renderSwapped || p == null || p != mc.thePlayer) return;

        p.rotationPitch = stashPitch;
        p.prevRotationPitch = stashPrevPitch;

        renderSwapped = false;
    }

    /** Force-clear all state (e.g. on world unload, dimension switch). */
    public static void reset() {
        clearState();
        trackedPlayer = null;
        trackedWorld = null;
    }

    /* ===================================================================== */
    /* Internal state                                                         */
    /* ===================================================================== */

    private static float serverYaw, serverPitch;
    private static float prevServerYaw, prevServerPitch;
    private static float yawVel, pitchVel;

    // Settle tremor — slow sub-degree wobble applied to the target
    private static double tremorYawPhaseA, tremorYawPhaseB;
    private static double tremorPitchPhaseA, tremorPitchPhaseB;

    private static boolean active;
    private static Request pending;
    private static Request currentReq;
    private static int currentPriority = Integer.MIN_VALUE;
    private static int returnTicks;
    private static Object trackedPlayer;
    private static Object trackedWorld;

    // Render-time pitch swap — see beginPlayerRender.
    private static boolean renderSwapped;
    private static float stashPitch, stashPrevPitch;

    // Movement-phase yaw swap — see beginMovementPhase.
    private static boolean movementSwapped;
    private static float cameraYaw;

    // Last movement grid direction handed to moveFlying — see applyToMove.
    private static int heldMoveF, heldMoveS;

    /** Glide profile used to hand the rotation back to the camera. */
    private static final Request RETURN = new Request();
    static {
        RETURN.profile = Profile.PRECISE;
        RETURN.disableTremor = true;
        RETURN.maxYawStepDeg = 14f;
        RETURN.maxPitchStepDeg = 10f;
    }

    /** ponytail: flat tick budget, not a tunable. It only bounds how long a
     *  hand-back may chase a camera the user keeps spinning. */
    private static final int RETURN_MAX_TICKS = 20;

    private static void clearState() {
        active = false;
        pending = null;
        currentReq = null;
        currentPriority = Integer.MIN_VALUE;
        returnTicks = 0;
        serverYaw = serverPitch = 0f;
        prevServerYaw = prevServerPitch = 0f;
        yawVel = pitchVel = 0f;
        tremorYawPhaseA = tremorYawPhaseB = 0.0;
        tremorPitchPhaseA = tremorPitchPhaseB = 0.0;
    }

    private static void standDown() {
        active = false;
        currentReq = null;
        returnTicks = 0;
        yawVel = pitchVel = 0f;
    }

    private static boolean atCamera() {
        return Math.abs(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - serverYaw)) < 0.5f
                && Math.abs(mc.thePlayer.rotationPitch - serverPitch) < 0.5f;
    }

    /* ===================================================================== */
    /* Spring step                                                            */
    /* ===================================================================== */

    private static void stepSpring(Request req) {
        Profile p = req.profile != null ? req.profile : Profile.COMBAT;

        float targetYaw = req.yaw;
        float targetPitch = MathHelper.clamp_float(req.pitch, -89.5f, 89.5f);

        if (req.instant) {
            float yErr = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
            float pErr = targetPitch - serverPitch;
            yawVel = pitchVel = 0f;
            serverYaw += snapStepToGcd(yErr, yErr);
            serverPitch = MathHelper.clamp_float(
                    serverPitch + snapStepToGcd(pErr, pErr), -89.5f, 89.5f);
            return;
        }

        // Settle tremor perturbs the TARGET, not the step. Perturbing the step
        // ran it through snapStepToGcd's overshoot clamp, which only clips motion
        // *toward* the target — so the wobble was rectified into a steady push
        // away from it that the spring then spent every tick fighting. Two phases
        // per axis at incommensurate frequencies keep it unpredictable.
        if (!req.disableTremor && p.tremorAmpDeg > 0f) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            tremorYawPhaseA   += 0.18  + r.nextDouble() * 0.04;
            tremorYawPhaseB   += 0.061 + r.nextDouble() * 0.012;
            tremorPitchPhaseA += 0.155 + r.nextDouble() * 0.035;
            tremorPitchPhaseB += 0.047 + r.nextDouble() * 0.010;

            targetYaw += (float) (Math.sin(tremorYawPhaseA) * 0.62
                    + Math.sin(tremorYawPhaseB) * 0.38) * p.tremorAmpDeg;
            targetPitch += (float) (Math.sin(tremorPitchPhaseA) * 0.55
                    + Math.sin(tremorPitchPhaseB) * 0.45) * p.tremorAmpDeg * 0.7f;
        }

        float k = req.stiffness > 0f ? req.stiffness : p.stiffness;
        float c = req.damping > 0f ? req.damping : criticalDamping(k);

        float yawErr = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float pitErr = targetPitch - serverPitch;

        float yawCap = req.maxYawStepDeg > 0f ? req.maxYawStepDeg : p.yawCapDeg;
        float pitCap = req.maxPitchStepDeg > 0f ? req.maxPitchStepDeg : p.pitchCapDeg;

        // GCD snap so server-side angle deltas are integer multiples of the
        // mouse-sensitivity GCD. This is what Grim's AimModulo360 verifies:
        // a real mouse can only produce yaw deltas of n*GCD. Snap AND
        // cap-without-overshoot in one step so the final delta stays
        // GCD-aligned even at the brink of the target.
        float yawStep = snapStepToGcd(
                stepAxis(yawErr, yawCap, p.minSpeedDeg, k, c, /*isYaw=*/ true), yawErr);
        float pitStep = snapStepToGcd(
                stepAxis(pitErr, pitCap, p.minSpeedDeg, k, c, /*isYaw=*/ false), pitErr);

        serverYaw += yawStep;
        serverPitch = MathHelper.clamp_float(serverPitch + pitStep, -89.5f, 89.5f);
    }

    /**
     * Damping that puts the discrete spring exactly on the critical boundary.
     *
     * <p>The step below is {@code v += k*err - c*v; x += v}, whose error state
     * {@code [err, v]} has characteristic polynomial
     * {@code λ² - (2 - k - c)λ + (1 - c)}. The roots collide when
     * {@code c = 2√k - k}, giving a repeated root of {@code 1 - √k}: the fastest
     * monotone approach available, with no overshoot at all.
     *
     * <p>COMBAT used to carry a hard-coded 0.78 against k=0.55, where critical is
     * 0.93. That put the roots complex (|λ| 0.47, ~8 tick period) so every sweep
     * sailed ~5% past the target and rang its way back. On a 60° acquisition
     * that is a 3° overshoot followed by a reversal — the head snap.
     */
    private static float criticalDamping(float stiffness) {
        return 2f * (float) Math.sqrt(stiffness) - stiffness;
    }

    /**
     * One spring step on one axis, capped by Fitts-law velocity scaling.
     * Big errors → high cap (fast sweep); small errors → low cap (precise tracking).
     */
    private static float stepAxis(float err, float capDeg, float minSpd,
                                  float stiffness, float damping, boolean isYaw) {
        float vel = isYaw ? yawVel : pitchVel;

        // Fitts-ish cap: clamp the cap based on remaining distance so small
        // errors don't get full cap velocity (avoids zip-snap behaviour).
        float distScaled = (float) Math.min(1.0,
                Math.log(1.0 + Math.abs(err) / 6.0) / Math.log(1.0 + 60.0 / 6.0));
        float effectiveCap = Math.max(minSpd, capDeg * (0.20f + 0.80f * distScaled));

        vel += stiffness * err - damping * vel;

        if (vel > effectiveCap) vel = effectiveCap;
        if (vel < -effectiveCap) vel = -effectiveCap;

        // Tiny gaussian-ish jitter on velocity to avoid identical successive steps
        float noise = (float) ((ThreadLocalRandom.current().nextDouble() - ThreadLocalRandom.current().nextDouble())
                * effectiveCap * 0.02);
        vel += noise;

        if (isYaw) yawVel = vel; else pitchVel = vel;
        return vel;
    }

    /**
     * Snap a raw per-tick rotation step to an integer multiple of the player's
     * mouse-sensitivity GCD AND cap it so we never overshoot the remaining error,
     * keeping the result GCD-aligned in both cases.
     *
     * Grim (and similar anticheats) flag deltas where {@code |delta % gcd| > tol}
     * as AimModulo360 violations — a real mouse can only produce deltas of
     * {@code n*gcd}. If the spring would overshoot the target, we floor the
     * magnitude down to the largest {@code k*gcd} not exceeding |remaining|,
     * accepting that we may finish up to one GCD short of perfect alignment
     * (well within every caller's degree-scale readiness threshold).
     */
    private static float snapStepToGcd(float rawStep, float remaining) {
        // Promote to double for snapping math — the multiplication n*gcd in
        // float can leave 1e-7 residuals that survive accumulation. Grim's
        // AimModulo360 tolerance is tight enough that even small float errors
        // can flag, especially after a long active period.
        double gcd = Utils.Player.getGcd();
        if (gcd <= 0.0) return rawStep;

        long n = Math.round(rawStep / gcd);
        double snapped = n * gcd;

        // Cap to largest GCD-multiple in the same direction not exceeding |remaining|.
        if (Math.signum(snapped) == Math.signum((double) remaining)
                && Math.abs(snapped) > Math.abs((double) remaining)) {
            long k = (long) Math.floor(Math.abs((double) remaining) / gcd);
            snapped = Math.copySign(k * gcd, remaining);
        }
        return (float) snapped;
    }

    private static void seedFromPlayer() {
        if (mc.thePlayer == null) return;
        serverYaw = mc.thePlayer.rotationYaw;
        serverPitch = mc.thePlayer.rotationPitch;
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;
        yawVel = pitchVel = 0f;
    }

    private static void refreshContext() {
        Object player = mc == null ? null : mc.thePlayer;
        Object world = mc == null ? null : mc.theWorld;
        if (player == trackedPlayer && world == trackedWorld) return;

        clearState();
        trackedPlayer = player;
        trackedWorld = world;
        if (player != null) {
            seedFromPlayer();
        }
    }

    /* ===================================================================== */
    /* Bus subscribers — auto-apply to look/move                              */
    /* ===================================================================== */

    @Subscribe
    public void onLook(LookEvent e) {
        applyToLook(e);
    }

    @Subscribe
    public void onMove(MoveInputEvent e) {
        applyToMove(e);
    }

    /* ===================================================================== */
    /* Singleton bootstrap                                                    */
    /* ===================================================================== */

    private static final SilentAim INSTANCE = new SilentAim();

    public static SilentAim instance() {
        return INSTANCE;
    }

    private SilentAim() {}
}
