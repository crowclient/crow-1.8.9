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
 * Modules submit per-tick {@link Request}s describing a target yaw/pitch and a
 * profile. SilentAim runs a critically-damped spring with Fitts-law velocity
 * scaling, GCD-snapped output, sub-degree settle tremor, and a small reaction
 * delay on big target jumps. The result is a server yaw/pitch that's smooth
 * AND noisy on the right scales — fast on big sweeps, slow on fine corrections,
 * with quiet sub-degree wobble while parked on target.
 *
 * The visual rotation ({@code mc.thePlayer.rotationYaw/Pitch}) is left alone:
 * the rotation only goes out on the C03 packet via UpdateEvent.setYaw/setPitch,
 * so first-person camera continues to follow the user's mouse. In third-person,
 * use {@link #applyToLook(LookEvent)} to keep the camera in sync.
 *
 * Typical module usage (inside an UpdateEvent.PRE handler):
 * <pre>
 *   if (e.isPre() &amp;&amp; haveTarget) {
 *       SilentAim.Request r = new SilentAim.Request();
 *       r.yaw = computedYaw;
 *       r.pitch = computedPitch;
 *       r.profile = SilentAim.Profile.COMBAT;
 *       r.priority = 100;
 *       r.claimant = this;
 *       SilentAim.aim(r);
 *       SilentAim.applyToUpdate(e);
 *   }
 *   if (SilentAim.isReady(2.5f)) attack();
 * </pre>
 */
public final class SilentAim {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /* ===================================================================== */
    /* Public API                                                             */
    /* ===================================================================== */

    public enum Profile {
        /** Fast tracking for combat (KillAura). */
        COMBAT(/*stiff*/ 0.55f, /*damp*/ 0.78f, /*yawCap*/ 36f, /*pitCap*/ 22f,
               /*minSpd*/ 1.6f, /*tremor*/ 0.10f, /*reactMin*/ 1, /*reactMax*/ 3),
        /**
         * Medium fluidity for block-place modules (BlockIn / Scaffold). Lower
         * stiffness than COMBAT — settles in 6–8 ticks instead of 2–3, which
         * reads as one continuous arc to the eye instead of a snap.
         */
        PLACE (/*stiff*/ 0.26f, /*damp*/ 0.86f, /*yawCap*/ 14f, /*pitCap*/ 10f,
               /*minSpd*/ 0.8f, /*tremor*/ 0.06f, /*reactMin*/ 0, /*reactMax*/ 1),
        /** Slow, careful — for one-shot precise placements. */
        PRECISE(/*stiff*/ 0.34f, /*damp*/ 0.88f, /*yawCap*/ 18f, /*pitCap*/ 14f,
               /*minSpd*/ 0.9f, /*tremor*/ 0.05f, /*reactMin*/ 0, /*reactMax*/ 2);

        final float stiffness;
        final float damping;
        final float yawCapDeg;
        final float pitchCapDeg;
        final float minSpeedDeg;
        final float tremorAmpDeg;
        final int reactionTicksMin;
        final int reactionTicksMax;

        Profile(float stiffness, float damping, float yawCap, float pitchCap,
                float minSpeed, float tremorAmp, int reactMin, int reactMax) {
            this.stiffness = stiffness;
            this.damping = damping;
            this.yawCapDeg = yawCap;
            this.pitchCapDeg = pitchCap;
            this.minSpeedDeg = minSpeed;
            this.tremorAmpDeg = tremorAmp;
            this.reactionTicksMin = reactMin;
            this.reactionTicksMax = reactMax;
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
        /** 0 = use profile default. Higher = more drag on velocity. */
        public float damping = 0f;
        /** Sync head/body visual yaw to server yaw (recommended for combat). */
        public boolean syncVisualHead = true;
        /** Override movement-input yaw with server yaw (fixMovement). */
        public boolean fixMovement = true;
        /**
         * Disable settle-tremor for this request. Use for placement modules where
         * any sub-degree wobble breaks raycast-based anticheat checks
         * (e.g. Grim's RotationPlace).
         */
        public boolean disableTremor = false;
        /**
         * Disable the 1–3 tick reaction-delay that fires when the target jumps
         * &gt;30° in one tick. Useful for placement-chain modules (BlockIn,
         * Scaffold) which routinely switch targets between adjacent placements
         * — the freeze-then-resume reads as a flick rather than a fluid arc.
         */
        public boolean disableReaction = false;
        /** Diagnostic / debug; not used for control. */
        public Object claimant;
    }

    /** Submit an aim request for this tick. Highest priority wins; ties → first call. */
    public static void aim(Request req) {
        if (req == null) return;
        if (mc == null || mc.thePlayer == null) return;

        int tick = mc.thePlayer.ticksExisted;

        if (tick != lastSteppedTick) {
            // First aim() call this tick — capture rest-state if we were dormant
            if (activeForTicks <= 0) {
                seedFromPlayer();
            }

            // Snapshot pre-step state so a higher-priority later call can rewind
            preStepYaw = serverYaw;
            preStepPitch = serverPitch;
            preStepYawVel = yawVel;
            preStepPitchVel = pitchVel;

            prevServerYaw = serverYaw;
            prevServerPitch = serverPitch;

            applyRequest(req, /*resnap=*/ false);
            lastSteppedTick = tick;
            currentReq = req;
            currentPriority = req.priority;
        } else if (req.priority > currentPriority) {
            // Higher priority: rewind to start-of-tick, re-step to new target
            serverYaw = preStepYaw;
            serverPitch = preStepPitch;
            yawVel = preStepYawVel;
            pitchVel = preStepPitchVel;
            applyRequest(req, /*resnap=*/ true);
            currentReq = req;
            currentPriority = req.priority;
        }
        // else: lower-priority same-tick call → ignored

        activeForTicks = 2;
    }

    /** Whether silent aim is currently driving the server-side rotation. */
    public static boolean isActive() {
        return activeForTicks > 0;
    }

    /** Server yaw being sent in the C03 packet. */
    public static float getServerYaw() {
        return serverYaw;
    }

    /** Server pitch being sent in the C03 packet. */
    public static float getServerPitch() {
        return serverPitch;
    }

    public static float getPrevServerYaw() {
        return prevServerYaw;
    }

    public static float getPrevServerPitch() {
        return prevServerPitch;
    }

    /** True if the spring is settled within {@code thresholdDeg} of the target. */
    public static boolean isReady(float thresholdDeg) {
        if (currentReq == null) return false;
        float yawErr = MathHelper.wrapAngleTo180_float(currentReq.yaw - serverYaw);
        float pitErr = currentReq.pitch - serverPitch;
        return Math.abs(yawErr) <= thresholdDeg && Math.abs(pitErr) <= thresholdDeg;
    }

    public static boolean isReady() {
        return isReady(3.0f);
    }

    /** Convenience: write the current server rotation onto an UpdateEvent. */
    public static void applyToUpdate(UpdateEvent e) {
        if (!isActive()) return;
        e.setYaw(serverYaw);
        e.setPitch(serverPitch);
    }

    /**
     * Convenience: align the third-person camera to the silent rotation.
     * No-op in first person — silent aim stays silent there because the camera
     * follows the user's mouse and only the packet gets overridden.
     */
    public static void applyToLook(LookEvent e) {
        if (!isActive()) return;
        if (mc.gameSettings == null || mc.gameSettings.thirdPersonView == 0) return;
        e.setYaw(serverYaw);
        e.setPitch(serverPitch);
        e.setPrevYaw(prevServerYaw);
        e.setPrevPitch(prevServerPitch);
        syncVisualHead();
    }

    /** Convenience: fixMovement — make movement direction track the server yaw. */
    public static void applyToMove(MoveInputEvent e) {
        if (!isActive()) return;
        if (currentReq == null || !currentReq.fixMovement) return;
        e.setYaw(serverYaw);
    }

    /** Force-clear all state (e.g. on world unload, dimension switch). */
    public static void reset() {
        activeForTicks = 0;
        currentReq = null;
        currentPriority = Integer.MIN_VALUE;
        yawVel = pitchVel = 0f;
        reactionLeft = 0;
        lastTargetYaw = Float.NaN;
        lastTargetPitch = Float.NaN;
    }

    /* ===================================================================== */
    /* Internal state                                                         */
    /* ===================================================================== */

    private static float serverYaw, serverPitch;
    private static float prevServerYaw, prevServerPitch;
    private static float yawVel, pitchVel;

    // Per-tick checkpointing for priority rewind
    private static float preStepYaw, preStepPitch;
    private static float preStepYawVel, preStepPitchVel;

    // Reaction delay — engages on big target deltas (target acquisition / new lock)
    private static int reactionLeft;
    private static float lastTargetYaw = Float.NaN;
    private static float lastTargetPitch = Float.NaN;

    // Settle tremor — slow sub-degree wobble while parked on target
    private static double tremorYawPhaseA, tremorYawPhaseB;
    private static double tremorPitchPhaseA, tremorPitchPhaseB;

    private static int activeForTicks;
    private static int lastSteppedTick = -1;
    private static Request currentReq;
    private static int currentPriority = Integer.MIN_VALUE;

    /* ===================================================================== */
    /* Spring step                                                            */
    /* ===================================================================== */

    private static void applyRequest(Request req, boolean resnap) {
        Profile p = req.profile != null ? req.profile : Profile.COMBAT;

        float targetYaw = req.yaw;
        float targetPitch = MathHelper.clamp_float(req.pitch, -89.5f, 89.5f);

        // Reaction delay: trigger when target jumps significantly between ticks.
        // Skipped for placement-chain modules so target switches don't stutter.
        if (!req.disableReaction && !Float.isNaN(lastTargetYaw)) {
            float jumpYaw = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - lastTargetYaw));
            float jumpPit = Math.abs(targetPitch - lastTargetPitch);
            float jump = Math.max(jumpYaw, jumpPit * 1.5f); // pitch counts more
            if (jump > 30f && reactionLeft <= 0 && !resnap) {
                reactionLeft = p.reactionTicksMin
                        + ThreadLocalRandom.current().nextInt(
                                Math.max(1, p.reactionTicksMax - p.reactionTicksMin + 1));
            }
        }
        lastTargetYaw = targetYaw;
        lastTargetPitch = targetPitch;

        if (reactionLeft > 0) {
            reactionLeft--;
            // hold position; no spring step, no tremor (humans freeze briefly)
            yawVel *= 0.5f;
            pitchVel *= 0.5f;
            return;
        }

        float effStiffness = req.stiffness > 0f ? req.stiffness : p.stiffness;
        float effDamping   = req.damping   > 0f ? req.damping   : p.damping;

        // ----- yaw -----
        float yawErr = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float yawCap = req.maxYawStepDeg > 0f ? req.maxYawStepDeg : p.yawCapDeg;
        float yawStep = stepAxis(yawErr, yawCap, p.minSpeedDeg, effStiffness, effDamping, /*isYaw=*/ true);

        // ----- pitch -----
        float pitErr = targetPitch - serverPitch;
        float pitCap = req.maxPitchStepDeg > 0f ? req.maxPitchStepDeg : p.pitchCapDeg;
        float pitStep = stepAxis(pitErr, pitCap, p.minSpeedDeg, effStiffness, effDamping, /*isYaw=*/ false);

        // Settle tremor: tiny sinusoidal noise when the spring is near-target.
        // Two phases per axis at incommensurate frequencies → unpredictable but smooth.
        // Disabled for placement requests where Grim raycasts the hit vec.
        float closeness = req.disableTremor ? 0f : settleCloseness(Math.abs(yawErr), Math.abs(pitErr));
        if (closeness > 0f) {
            tremorYawPhaseA   += 0.18 + ThreadLocalRandom.current().nextDouble() * 0.04;
            tremorYawPhaseB   += 0.061 + ThreadLocalRandom.current().nextDouble() * 0.012;
            tremorPitchPhaseA += 0.155 + ThreadLocalRandom.current().nextDouble() * 0.035;
            tremorPitchPhaseB += 0.047 + ThreadLocalRandom.current().nextDouble() * 0.010;

            float yawTremor = (float) (
                    Math.sin(tremorYawPhaseA) * 0.62 + Math.sin(tremorYawPhaseB) * 0.38)
                    * p.tremorAmpDeg * closeness;
            float pitTremor = (float) (
                    Math.sin(tremorPitchPhaseA) * 0.55 + Math.sin(tremorPitchPhaseB) * 0.45)
                    * p.tremorAmpDeg * 0.7f * closeness;

            yawStep += yawTremor;
            pitStep += pitTremor;
        }

        // GCD snap so server-side angle deltas are integer multiples of the
        // mouse-sensitivity GCD. This is what Grim's AimModulo360 verifies:
        // a real mouse produces yaw deltas of n*GCD for integer n. We snap
        // AND cap-without-overshoot in one step so the final delta stays
        // GCD-aligned even at the brink of the target.
        yawStep = snapStepToGcd(yawStep, yawErr);
        pitStep = snapStepToGcd(pitStep, pitErr);

        serverYaw += yawStep;
        serverPitch = MathHelper.clamp_float(serverPitch + pitStep, -89.5f, 89.5f);

        // Sync head/body visual yaw per-tick so other players see the rotation
        // (and so first-person body model swings correctly). Does NOT touch
        // mc.thePlayer.rotationYaw — first-person camera stays on the user's mouse.
        if (req.syncVisualHead) {
            syncVisualHead();
        }
    }

    /**
     * Critically-damped spring step, capped by Fitts-law velocity scaling.
     * Big errors → high cap (fast sweep); small errors → low cap (precise tracking).
     */
    private static float stepAxis(float err, float capDeg, float minSpd,
                                  float stiffness, float damping, boolean isYaw) {
        float vel = isYaw ? yawVel : pitchVel;

        // Fitts-ish cap: cap = baseCap * log2(1 + |err|/refDist)/log2(1 + maxRef/refDist)
        // Practically: clamp the cap based on remaining distance so small errors
        // don't get full cap velocity (avoids zip-snap behaviour).
        float distScaled = (float) Math.min(1.0,
                Math.log(1.0 + Math.abs(err) / 6.0) / Math.log(1.0 + 60.0 / 6.0));
        float effectiveCap = Math.max(minSpd, capDeg * (0.20f + 0.80f * distScaled));

        // Spring physics: a = stiffness*err - damping*vel  (pseudo-discrete; dt = 1 tick)
        float accel = stiffness * err - damping * vel;
        vel += accel;

        // Cap velocity
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
     * 0.0 .. 1.0 — how settled we are on target. 1.0 = within 0.6° both axes.
     * Used to scale settle-tremor amplitude up smoothly as we arrive.
     */
    private static float settleCloseness(float yawErrAbs, float pitErrAbs) {
        float worst = Math.max(yawErrAbs, pitErrAbs);
        if (worst >= 6f) return 0f;
        if (worst <= 0.6f) return 1f;
        // smoothstep from 6° down to 0.6°
        float t = (6f - worst) / (6f - 0.6f);
        return t * t * (3f - 2f * t);
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
     * (well within rotReady's degree-scale thresholds).
     */
    private static float snapStepToGcd(float rawStep, float remaining) {
        // Promote to double for snapping math — the multiplication n*gcd in
        // float can leave 1e-7 residuals that survive accumulation. Grim's
        // AimModulo360 tolerance is tight enough that even small float errors
        // can flag, especially after a long active period.
        double gcd = (double) Utils.Player.getGcd();
        if (gcd <= 0.0) return rawStep;

        long n = Math.round((double) rawStep / gcd);
        double snapped = (double) n * gcd;

        // Cap to largest GCD-multiple in the same direction not exceeding |remaining|.
        if (Math.signum(snapped) == Math.signum((double) remaining)
                && Math.abs(snapped) > Math.abs((double) remaining)) {
            long k = (long) Math.floor(Math.abs((double) remaining) / gcd);
            snapped = Math.copySign((double) k * gcd, (double) remaining);
        }
        return (float) snapped;
    }

    /**
     * Sync visible head-yaw/render-yaw to the server yaw. Only mc.thePlayer.rotationYaw
     * itself is left untouched (so first-person camera tracks the user's mouse).
     */
    private static void syncVisualHead() {
        if (mc.thePlayer == null) return;
        if (currentReq != null && !currentReq.syncVisualHead) return;
        mc.thePlayer.prevRotationYawHead = mc.thePlayer.rotationYawHead;
        mc.thePlayer.rotationYawHead = serverYaw;
        mc.thePlayer.prevRenderYawOffset = mc.thePlayer.renderYawOffset;
        mc.thePlayer.renderYawOffset += MathHelper.wrapAngleTo180_float(
                serverYaw - mc.thePlayer.renderYawOffset) * 0.4f;
    }

    private static void seedFromPlayer() {
        if (mc.thePlayer == null) return;
        serverYaw = mc.thePlayer.rotationYaw;
        serverPitch = mc.thePlayer.rotationPitch;
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;
        yawVel = pitchVel = 0f;
    }

    /* ===================================================================== */
    /* Bus subscriber — decay activeFor and auto-apply to look/move           */
    /* ===================================================================== */

    @Subscribe
    public void onUpdatePost(UpdateEvent e) {
        if (!e.isPost()) return;
        if (activeForTicks > 0) {
            activeForTicks--;
            if (activeForTicks == 0) {
                yawVel = pitchVel = 0f;
                reactionLeft = 0;
                currentReq = null;
                currentPriority = Integer.MIN_VALUE;
            }
        }
    }

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
