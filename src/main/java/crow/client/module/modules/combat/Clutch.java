package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.MoveInputEvent;
import crow.client.event.impl.UpdateEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.SilentAim;
import crow.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.List;

public class Clutch extends Module {

    private final SliderSetting maxBlocks;
    private final SliderSetting damageThreshold;
    private final SliderSetting rotationSpeed;
    private final SliderSetting placeDelay;
    private final SliderSetting searchRadius;
    private final TickSetting saveFromVoid;
    private final TickSetting cancelIfUnpreventable;
    private final TickSetting autoSwap;
    private final TickSetting swapBack;
    private final TickSetting swing;
    private final TickSetting freezeMove;

    private enum State { IDLE, ROTATING, PLACING }
    private State state = State.IDLE;

    private PlaceTarget currentTarget;
    private int placedCount;
    private boolean placementBudgetExhausted;
    private int previousSlot = -1;
    private long lastPlaceMs;
    private int rotationTicks;

    /** Ticks until the projected fall impact, refreshed every {@link #assess()}.
     *  -1 means no impact in the projection horizon. Drives the emergency
     *  snap-and-place path so a fast fall still lands a block in time. */
    private int lastTicksToImpact = -1;
    /** True when impact is close enough to drop the cosmetic place delay and
     *  firm up the rotation. Never a snap — see tickPre. */
    private boolean emergencyPlace = false;

    /** Projected landing spot, refreshed by {@link #assess()}. Drives pre-aim. */
    private double impactX, impactY, impactZ;
    private boolean haveImpact = false;

    /** Impact horizon (ticks) at or below which we enter emergency mode. */
    private static final int EMERGENCY_TICKS = 4;

    /** An armed clutch is life-saving work and must beat combat aim (100).
     *  Pre-aim stays below combat so an unreachable fall does not steal aim. */
    private static final int ARMED_AIM_PRIORITY = 120;
    private static final int PRE_AIM_PRIORITY = 50;

    private static final double GRAVITY = 0.08D;
    private static final double DRAG_Y  = 0.98D;
    private static final double DRAG_XZ = 0.91D;
    private static final double TERMINAL_VEL = -3.92D;

    private static final int PROJECTION_TICKS = 80;
    private static final int VOID_Y = 0;

    public Clutch() {
        super("Clutch", ModuleCategory.combat);
        this.registerSetting(maxBlocks            = new SliderSetting("Max blocks", 5.0D, 1.0D, 10.0D, 1.0D));
        this.registerSetting(damageThreshold      = new SliderSetting("Dmg threshold", 2.0D, 0.5D, 6.0D, 0.5D));
        this.registerSetting(rotationSpeed        = new SliderSetting("Rot speed", 48.0D, 4.0D, 60.0D, 0.5D));
        this.registerSetting(placeDelay           = new SliderSetting("Place delay", 0.0D, 0.0D, 4.0D, 1.0D));
        this.registerSetting(searchRadius         = new SliderSetting("Search range", 4.5D, 2.0D, 5.0D, 0.5D));
        this.registerSetting(saveFromVoid         = new TickSetting("Save void", true));
        this.registerSetting(cancelIfUnpreventable= new TickSetting("Cancel if lost", false));
        this.registerSetting(autoSwap             = new TickSetting("Auto swap", true));

        this.registerSetting(swapBack             = new TickSetting("Swap back", false));
        // Vanilla swings the arm on every successful place (Minecraft#rightClickMouse),
        // so not swinging is itself the anomaly.
        this.registerSetting(swing                = new TickSetting("Swing", true));
        this.registerSetting(freezeMove           = new TickSetting("Freeze move", true));
    }

    @Override
    public String getHudSuffix() {
        switch (state) {
            case ROTATING: return "rot";
            case PLACING:  return "place(" + placedCount + ")";
            default:       return "";
        }
    }

    @Override
    public void onEnable() {
        resetRuntime(true);
    }

    @Override
    public void onDisable() {
        resetRuntime(true);
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) {
            resetRuntime(true);
            return;
        }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
            resetRuntime(true);
            return;
        }

        if (e.isPre()) {
            tickPre();
        } else {
            tickPost();
        }
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent e) {
        if (!freezeMove.isToggled()) return;
        if (state == State.IDLE) return;
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null
                || mc.thePlayer.onGround || mc.thePlayer.isInWater() || mc.thePlayer.isInLava()
                || (mc.currentScreen != null
                    && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat))) return;

        e.setStrafe(0F);
        e.setForward(0F);
    }

    private void tickPre() {
        // Single source of truth: are we in a fall situation that warrants clutch?
        // Avoids the old decline/cooldown loop which caused state to flicker between
        // ROTATING and IDLE during a fall, snapping the silent rotation each cycle.
        if (!isInDanger()) {
            placementBudgetExhausted = false;
            if (state != State.IDLE) {
                wrapUp();
            }
            SilentAim.release(this);
            emergencyPlace = false;
            return;
        }
        // Max blocks is a per-fall safety budget. wrapUp() clears the active
        // chain, but this latch prevents the next falling tick from starting a
        // fresh chain with a reset placedCount.
        if (placementBudgetExhausted) {
            if (state != State.IDLE) {
                wrapUp();
            }
            SilentAim.release(this);
            emergencyPlace = false;
            return;
        }


        // isInDanger() refreshed lastTicksToImpact via assess(). Imminent impact
        // firms the rotation up and drops the cosmetic place delay — it does NOT
        // snap. A teleporting rotation is the single most obvious tell there is.
        emergencyPlace = lastTicksToImpact >= 0 && lastTicksToImpact <= EMERGENCY_TICKS;

        // Acquire / refresh target.
        boolean armed;
        if (currentTarget == null || !isValidPlacement(currentTarget)) {
            currentTarget = null;
            armed = findAndArmTarget();
            if (armed) state = State.ROTATING;
        } else {
            // Player has moved; update angles toward the same hit vec.
            float[] freshRot = computePlacementAngles(currentTarget.hitVec, leadEyes());
            currentTarget.rotationYaw = freshRot[0];
            currentTarget.rotationPitch = freshRot[1];
            armed = true;
        }

        // Where to look this tick. With a live placement we track its hit vec;
        // with none we pre-aim at the projected landing spot so the spring is
        // already pointed down-range by the time a support comes into reach.
        float aimYaw, aimPitch;
        if (armed) {
            aimYaw = currentTarget.rotationYaw;
            aimPitch = currentTarget.rotationPitch;
        } else {
            float[] pre = preAimAngles();
            if (pre == null) return;
            aimYaw = pre[0];
            aimPitch = pre[1];
        }

        rotationTicks++;

        // Pre-aim remains a gentle human arc. Once a real catch block exists,
        // the remaining trajectory supplies a hard placement deadline.
        float speed = (float) rotationSpeed.getInput();
        float t = MathHelper.clamp_float((speed - 4f) / (60f - 4f), 0f, 1f);
        float stiff        = 0.18f + t * (0.42f - 0.18f);
        float maxYawStep   = 12.0f + t * 10.0f;   // 12 -> 22 deg/tick
        float maxPitchStep =  9.0f + t *  8.0f;   //  9 -> 17 deg/tick

        int deadlineTicks = lastTicksToImpact;
        if (armed) {
            int interceptTicks = projectedInterceptTick(currentTarget.placePos);
            if (interceptTicks > 0) deadlineTicks = interceptTicks;
            if (deadlineTicks <= 0) deadlineTicks = estimatedTicksToFirstPlace();

            emergencyPlace = deadlineTicks <= EMERGENCY_TICKS;
            int turnsLeft = Math.max(1, deadlineTicks);
            float yawError = Math.abs(MathHelper.wrapAngleTo180_float(
                    aimYaw - SilentAim.getServerYaw()));
            float pitchError = Math.abs(aimPitch - SilentAim.getServerPitch());
            float gcdMargin = Math.max(0.1F, Utils.Player.getGcd() * 2.0F);

            maxYawStep = Math.max(maxYawStep,
                    reliableTurnCap(yawError, turnsLeft, gcdMargin));
            maxPitchStep = Math.max(maxPitchStep,
                    reliableTurnCap(pitchError, turnsLeft, gcdMargin));

            float deadlineStiffness = turnsLeft <= 1 ? 0.98F
                    : turnsLeft == 2 ? 0.86F : 0.68F;
            stiff = Math.max(stiff, deadlineStiffness);
        } else if (emergencyPlace) {
            // No face is reachable yet, but keep the pre-aim moving decisively.
            stiff = Math.min(0.62F, stiff * 1.6F);
            maxYawStep *= 1.6F;
            maxPitchStep *= 1.6F;
        }

        SilentAim.Request req = new SilentAim.Request();
        req.yaw = aimYaw;
        req.pitch = aimPitch;
        req.profile = SilentAim.Profile.PLACE;
        req.priority = armed ? ARMED_AIM_PRIORITY : PRE_AIM_PRIORITY;
        req.maxYawStepDeg = maxYawStep;
        req.maxPitchStepDeg = maxPitchStep;
        req.stiffness = stiff;
        // Exact placement never accepts settle wobble.
        req.disableTremor = armed;
        req.claimant = this;
        SilentAim.aim(req);

        // rotReady() tests the rotation going out in *this* tick's look packet,
        // which SilentAim stepped at runTick HEAD — before moveFlying, so the
        // movement and the packet agree. The target submitted just above lands
        // on the next tick, which is why the state machine gates on the value
        // already applied rather than on the one just requested. Deliberately
        // NOT gated on isClaimedBy here: that reports the *previous* tick's
        // winner, so gating the transition on it burned the one tick where our
        // rotation first hits the wire. tickPost re-checks it before placing.
        if (armed && state == State.ROTATING && rotReady()) {
            state = State.PLACING;
        }
    }

    private void tickPost() {
        if (state != State.PLACING || currentTarget == null) return;
        // One attempt per tick; tickPre promotes us back to PLACING when the
        // rotation is still on target.
        state = State.ROTATING;

        if (!isValidPlacement(currentTarget)) {
            currentTarget = null;
            return;
        }

        if (!SilentAim.isClaimedBy(this) || !isHoldingPlaceableBlock()) return;

        // The raycast is the authoritative readiness test: it reproduces
        // exactly what the server sees this tick (the eye position and the
        // rotation both go out in this tick's C03, and this runs after that
        // send). rotReady() is only a cheap pre-filter.
        MovingObjectPosition hit = placementRaycast(currentTarget);
        // A miss means the spring has not arrived yet — keep the target and
        // let it keep converging. Dropping it here re-ran findBestTarget()
        // next tick, which could pick a different face, restarting the
        // rotation from scratch every tick so it never settled at all.
        if (hit == null) return;

        // Skip the cosmetic place delay when impact is imminent — every tick
        // of delay can consume the entire valid placement window.
        if (!emergencyPlace && System.currentTimeMillis() - lastPlaceMs < placeDelayMs()) return;

        // Send the point the ray actually struck, not the precomputed face
        // centre — vanilla sends objectMouseOver.hitVec, and a hit vec that
        // disagrees with the rotation on the wire is exactly what Grim's
        // RotationPlace flags.
        if (doPlace(currentTarget, hit.hitVec)) {
            placedCount++;
            lastPlaceMs = System.currentTimeMillis();
            currentTarget = null;
            if (placedCount >= (int) maxBlocks.getInput()) {
                placementBudgetExhausted = true;
                wrapUp();
            }
        } else {
            // Place failed (wrong item, controller refused).
            currentTarget = null;
        }
    }

    /**
     * Returns true if the player is in a fall that warrants clutching.
     * Filters out small jumps via fallDistance + assess() projection.
     *
     * Note: we deliberately do NOT short-circuit on {@code !v.preventable}.
     * Early in a tall fall, the floor (and thus any reachable placement) is
     * outside the 4.5-block reach. A "no target right now" should not turn
     * into "give up forever" — Clutch will retry every tick and arm as the
     * player falls within range.
     */
    private boolean isInDanger() {
        if (mc.thePlayer.onGround) return false;
        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) return false;
        if (findBestBlockSlot() == -1) return false;

        if (mc.thePlayer.motionY > -0.05D && mc.thePlayer.fallDistance < 0.4F) return false;

        FallVerdict v = assess();
        if (v.danger == Danger.SAFE) return false;
        if (v.danger == Danger.VOID && !saveFromVoid.isToggled()) return false;

        // "Cancel if lost" — give up only when impact is imminent and the
        // fall is genuinely unpreventable. The imminence guard preserves
        // tall-fall clutching: high up, no placement is reachable yet so
        // the verdict is "not preventable", but impact is many ticks away
        // and a target will come into range as we fall, so we keep trying.
        // Once impact is within the rotate-and-place window and we still
        // can't save it, bail instead of flailing the silent rotation.
        if (cancelIfUnpreventable.isToggled() && !v.preventable
                && v.ticksUntilImpact >= 0
                && v.ticksUntilImpact <= estimatedTicksToFirstPlace() + 1) {
            return false;
        }
        return true;
    }

    private void wrapUp() {
        SilentAim.release(this);
        if (swapBack.isToggled() && previousSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        previousSlot = -1;
        clearTransientState();
        state = State.IDLE;
    }

    private boolean swapToBlockSlot() {

        if (isHoldingPlaceableBlock()) return true;
        if (!autoSwap.isToggled()) return false;
        int slot = findBestBlockSlot();
        if (slot == -1) return false;
        if (mc.thePlayer.inventory.currentItem != slot) {
            if (previousSlot == -1) previousSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = slot;
        }
        return true;
    }

    private boolean isHoldingPlaceableBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) held.getItem()).getBlock();
        if (block == null) return false;
        if (!block.isFullBlock() && !block.isFullCube()) return false;
        return block != Blocks.tnt && block != Blocks.sand && block != Blocks.gravel;
    }

    private enum Danger { SAFE, DAMAGE, VOID }

    private static final class FallVerdict {
        final Danger danger;
        final boolean preventable;
        final int ticksUntilImpact;
        FallVerdict(Danger d, boolean p, int ticks) {
            this.danger = d;
            this.preventable = p;
            this.ticksUntilImpact = ticks;
        }
    }

    private FallVerdict assess() {
        FallProjection p = projectFall(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                                        mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ,
                                        PROJECTION_TICKS);

        impactX = p.impactX; impactY = p.impactY; impactZ = p.impactZ;
        haveImpact = true;

        double threshold = damageThreshold.getInput();

        if (p.voidImpact) {
            lastTicksToImpact = p.ticksUntilImpact;
            return new FallVerdict(Danger.VOID, canFindAnyTarget(), p.ticksUntilImpact);
        }
        if (predictedFallDamage(p.fallDistAtImpact) >= threshold) {
            lastTicksToImpact = p.ticksUntilImpact;
            int firstPlaceTick = estimatedTicksToFirstPlace();
            FallProjection p2 = projectFall(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                                             mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ,
                                             firstPlaceTick);
            boolean stillInWindow = predictedFallDamage(p2.fallDistAtEnd) < threshold;
            boolean canPlace = canFindAnyTarget();
            return new FallVerdict(Danger.DAMAGE, canPlace && stillInWindow, p.ticksUntilImpact);
        }
        lastTicksToImpact = -1;
        haveImpact = false;
        return new FallVerdict(Danger.SAFE, false, -1);
    }

    /**
     * Angles toward the projected landing spot. Used to pre-aim while still
     * falling: the placement we eventually make is always below us, so looking
     * down-range early puts the spring within a few degrees of the real target.
     * That is what lets the rotation stay slow and human — it gets the whole
     * descent to converge instead of the single tick a support is in reach.
     */
    private float[] preAimAngles() {
        if (!haveImpact) return null;
        return computePlacementAngles(new Vec3(impactX, impactY, impactZ), leadEyes());
    }

    /**
     * Eye position the C03 packet will carry on the tick the rotation we submit
     * *now* actually lands on.
     *
     * <p>This is the difference between clutching and splattering. SilentAim
     * steps the spring at runTick HEAD, so a target submitted in this tick's
     * UpdateEvent.PRE first reaches the wire on the next tick. Aiming it at the
     * angles measured from where the eyes are right now leaves the rotation
     * permanently one tick of falling behind the geometry. Standing still that
     * is invisible; falling at 1-3 blocks/tick toward a face a couple of blocks
     * away it is 5-20 degrees of error that never shrinks, so rotReady()'s
     * few-degree window simply never opened and the block never got placed.
     *
     * <p>moveEntityWithHeading has already applied gravity and drag to motion
     * by the time UpdateEvent.PRE fires, so pos + motion is next tick's
     * position exactly — no re-integration needed here.
     */
    private Vec3 leadEyes() {
        return mc.thePlayer.getPositionEyes(1.0F).addVector(
                mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ);
    }

    private int estimatedTicksToFirstPlace() {
        float speed = (float) rotationSpeed.getInput();
        int rotTicks = MathHelper.clamp_int(Math.round(20.0F / Math.max(2.0F, speed)) + 1, 1, 6);
        return rotTicks + 1;
    }

    /**
     * SilentAim's Fitts cap scales the supplied cap down for small errors. Invert
     * that scale here so the actual allowed step can still close the remaining
     * angle inside the placement deadline.
     */
    private float reliableTurnCap(float error, int turnsLeft, float gcdMargin) {
        float requiredStep = error * 1.15F / Math.max(1, turnsLeft) + gcdMargin;
        double distanceScale = Math.min(1.0D,
                Math.log(1.0D + error / 6.0D) / Math.log(11.0D));
        float fittsScale = (float) (0.20D + 0.80D * distanceScale);
        return Math.min(180.0F, requiredStep / Math.max(0.20F, fittsScale));
    }

    private double jumpBoostBonus() {
        PotionEffect eff = mc.thePlayer.getActivePotionEffect(Potion.jump);
        if (eff == null) return 0.0D;
        return eff.getAmplifier() + 1;
    }

    /** Mirrors EntityLivingBase#fall for the normal 1.0 damage multiplier. */
    private int predictedFallDamage(double fallDistance) {
        return Math.max(0, MathHelper.ceiling_float_int(
                (float) (fallDistance - 3.0D - jumpBoostBonus())));
    }

    private FallProjection projectFall(double px, double py, double pz,
                                        double mx, double my, double mz, int horizon) {
        FallProjection out = new FallProjection();
        // fallDistance has already accumulated all completed movement ticks.
        // Seeding from zero made an imminent landing look harmless precisely
        // when it finally entered placement reach.
        double fall = Math.max(0.0D, mc.thePlayer.fallDistance);
        AxisAlignedBB box = projectedPlayerBox(px, py, pz);

        for (int t = 1; t <= horizon; t++) {
            // Vanilla moves with the current velocity first. Gravity and drag
            // prepare the following tick; applying them here before movement
            // shifts every impact deadline one tick early.
            double requestedX = mx;
            double requestedY = my;
            double requestedZ = mz;

            List<AxisAlignedBB> collisions = mc.theWorld.getCollidingBoundingBoxes(
                    mc.thePlayer, box.addCoord(requestedX, requestedY, requestedZ));

            double movedY = requestedY;
            for (AxisAlignedBB collision : collisions) {
                movedY = collision.calculateYOffset(box, movedY);
            }
            box = box.offset(0.0D, movedY, 0.0D);

            double movedX = requestedX;
            for (AxisAlignedBB collision : collisions) {
                movedX = collision.calculateXOffset(box, movedX);
            }
            box = box.offset(movedX, 0.0D, 0.0D);

            double movedZ = requestedZ;
            for (AxisAlignedBB collision : collisions) {
                movedZ = collision.calculateZOffset(box, movedZ);
            }
            box = box.offset(0.0D, 0.0D, movedZ);

            px = (box.minX + box.maxX) * 0.5D;
            py = box.minY;
            pz = (box.minZ + box.maxZ) * 0.5D;

            boolean landed = requestedY < 0.0D && movedY > requestedY + 1.0E-7D;
            if (landed) {
                // updateFallState inflicts damage with the distance accumulated
                // before this collision tick, then clears it. Do not add the
                // clipped final movement segment.
                out.ticksUntilImpact = t;
                out.fallDistAtImpact = fall;
                out.fallDistAtEnd = fall;
                out.impactX = px; out.impactY = py; out.impactZ = pz;
                return out;
            }

            if (movedY < 0.0D) fall -= movedY;

            if (py < VOID_Y) {
                out.voidImpact = true;
                out.ticksUntilImpact = t;
                out.fallDistAtImpact = fall;
                out.fallDistAtEnd = fall;
                out.impactX = px; out.impactY = py; out.impactZ = pz;
                return out;
            }

            if (Math.abs(movedX - requestedX) > 1.0E-7D) mx = 0.0D;
            if (Math.abs(movedY - requestedY) > 1.0E-7D) my = 0.0D;
            if (Math.abs(movedZ - requestedZ) > 1.0E-7D) mz = 0.0D;

            my = (my - GRAVITY) * DRAG_Y;
            if (my < TERMINAL_VEL) my = TERMINAL_VEL;
            mx *= DRAG_XZ;
            mz *= DRAG_XZ;
        }
        out.ticksUntilImpact = -1;
        out.fallDistAtImpact = fall;
        out.fallDistAtEnd = fall;
        out.impactX = px; out.impactY = py; out.impactZ = pz;
        return out;
    }

    private AxisAlignedBB projectedPlayerBox(double x, double y, double z) {
        AxisAlignedBB current = mc.thePlayer.getEntityBoundingBox();
        double currentX = (current.minX + current.maxX) * 0.5D;
        double currentZ = (current.minZ + current.maxZ) * 0.5D;
        return current.offset(x - currentX, y - current.minY, z - currentZ);
    }

    /**
     * Returns the tick on which the falling player's horizontal footprint
     * crosses the top of a candidate block. A reachable face is useless if
     * momentum carries the player past the block before it can catch them.
     */
    private int projectedInterceptTick(BlockPos place) {
        if (mc.thePlayer == null || place == null) return -1;

        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        double mx = mc.thePlayer.motionX;
        double my = mc.thePlayer.motionY;
        double mz = mc.thePlayer.motionZ;
        double top = place.getY() + 1.0D;
        double halfWidth = mc.thePlayer.width * 0.5D;

        // A candidate below an already-projected landing surface cannot catch
        // the player; the existing collision stops the fall first.
        if (haveImpact && lastTicksToImpact >= 0 && top < impactY - 0.05D) return -1;

        for (int tick = 1; tick <= PROJECTION_TICKS; tick++) {
            if (lastTicksToImpact >= 0 && tick > lastTicksToImpact) return -1;
            double nextX = px + mx;
            double nextY = py + my;
            double nextZ = pz + mz;

            if (my < 0.0D && py >= top - 1.0E-7D && nextY <= top + 1.0E-7D) {
                double verticalTravel = py - nextY;
                double fraction = verticalTravel <= 1.0E-7D
                        ? 0.0D : MathHelper.clamp_double((py - top) / verticalTravel, 0.0D, 1.0D);
                double crossX = px + (nextX - px) * fraction;
                double crossZ = pz + (nextZ - pz) * fraction;

                double overlapX = Math.min(crossX + halfWidth, place.getX() + 1.0D)
                        - Math.max(crossX - halfWidth, place.getX());
                double overlapZ = Math.min(crossZ + halfWidth, place.getZ() + 1.0D)
                        - Math.max(crossZ - halfWidth, place.getZ());
                if (overlapX > 0.05D && overlapZ > 0.05D) return tick;
            }

            px = nextX;
            py = nextY;
            pz = nextZ;
            if (py < VOID_Y) return -1;

            my = (my - GRAVITY) * DRAG_Y;
            if (my < TERMINAL_VEL) my = TERMINAL_VEL;
            mx *= DRAG_XZ;
            mz *= DRAG_XZ;
        }
        return -1;
    }

    private static final class FallProjection {
        boolean voidImpact;
        int ticksUntilImpact;
        double fallDistAtImpact;
        double fallDistAtEnd;
        /** Where the projection says we end up — drives pre-aim. */
        double impactX, impactY, impactZ;
    }

    /** ponytail: one scan per tick. findBestTarget() walks an 11x11x11 cube x6
     *  faces (~8k block lookups); assess() and findAndArmTarget() both want it
     *  in the same tick, so cache it rather than paying for it 2-3 times while
     *  falling. Invalidated by tick, so it never goes stale mid-fall. */
    private int targetCacheTick = -1;
    private PlaceTarget cachedTarget;

    private PlaceTarget findBestTargetCached() {
        int tick = mc.thePlayer.ticksExisted;
        if (tick != targetCacheTick) {
            targetCacheTick = tick;
            cachedTarget = findBestTarget();
        }
        return cachedTarget;
    }

    private boolean canFindAnyTarget() {
        return findBestTargetCached() != null;
    }

    private boolean findAndArmTarget() {
        PlaceTarget t = findBestTargetCached();
        if (t == null) return false;

        if (!swapToBlockSlot()) return false;
        currentTarget = t;
        float[] rot = computePlacementAngles(t.hitVec, leadEyes());
        currentTarget.rotationYaw = rot[0];
        currentTarget.rotationPitch = rot[1];
        rotationTicks = 0;
        return true;
    }

    private PlaceTarget findBestTarget() {
        if (mc.thePlayer == null) return null;
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);

        double maxReach = placementReach();

        double pX = mc.thePlayer.posX;
        double pY = mc.thePlayer.posY;
        double pZ = mc.thePlayer.posZ;
        BlockPos feet = new BlockPos(
                MathHelper.floor_double(pX),
                MathHelper.floor_double(pY - 0.2D),
                MathHelper.floor_double(pZ));

        PlaceTarget best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        // Cover the blocks that come into reach on the tick our aim actually
        // lands. A fall moves 1-3 blocks per tick, so a box sized to the
        // present footprint alone armed a tick too late to ever place in time.
        int rad = (int) Math.ceil(maxReach + Math.abs(mc.thePlayer.motionY));
        for (int dy = rad; dy >= -rad; dy--) {
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    BlockPos place = feet.add(dx, dy, dz);
                    if (!isReplaceable(place)) continue;
                    if (placeWouldCollideWithPlayer(place)) continue;
                    int interceptTicks = projectedInterceptTick(place);
                    if (interceptTicks < 0) continue;

                    for (EnumFacing face : EnumFacing.values()) {
                        BlockPos support = place.offset(face);
                        if (!isFullPlacementSupport(support)) continue;
                        if (!isExposedFace(support, face.getOpposite())) continue;

                        EnumFacing clickFace = face.getOpposite();
                        Vec3 hit = new Vec3(
                                support.getX() + 0.5D + clickFace.getFrontOffsetX() * 0.48D,
                                support.getY() + 0.5D + clickFace.getFrontOffsetY() * 0.48D,
                                support.getZ() + 0.5D + clickFace.getFrontOffsetZ() * 0.48D);

                        if (!withinReach(hit)) continue;

                        double bx = place.getX() + 0.5D - pX;
                        double by = place.getY() + 0.5D - (pY - 0.5D);
                        double bz = place.getZ() + 0.5D - pZ;
                        double feetDist = Math.sqrt(bx * bx + by * by + bz * bz);
                        double score = -feetDist * 30.0D;

                        // Require a real trajectory intercept above, then prefer
                        // catches close to the projected landing footprint and
                        // ones that become useful before the deadline closes.
                        score += Math.max(0.0D, 140.0D - interceptTicks * 12.0D);
                        if (haveImpact) {
                            double impactDx = place.getX() + 0.5D - impactX;
                            double impactDz = place.getZ() + 0.5D - impactZ;
                            score -= Math.sqrt(impactDx * impactDx + impactDz * impactDz) * 18.0D;
                        }

                        if (dx == 0 && dz == 0 && dy == -1) score += 120.0D;

                        if (place.getY() == support.getY()) score += 25.0D;

                        if (place.getY() < support.getY()) score -= 60.0D;

                        score -= eyes.distanceTo(hit) * 1.5D;
                        score -= angularDeltaToFaceDeg(hit) * 0.05D;

                        if (score > bestScore) {
                            bestScore = score;
                            best = new PlaceTarget(place, support, clickFace, hit);
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean isExposedFace(BlockPos support, EnumFacing face) {
        BlockPos neighbor = support.offset(face);
        return isReplaceable(neighbor);
    }

    /**
     * The server rejects block placements that would intersect the player's
     * bounding box. Skipping these client-side keeps the chain from stalling
     * on a placement that the server will silently fail.
     */
    private boolean placeWouldCollideWithPlayer(BlockPos pos) {
        AxisAlignedBB blockBB = new AxisAlignedBB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
        return mc.thePlayer.getEntityBoundingBox().intersectsWith(blockBB);
    }

    private double angularDeltaToFaceDeg(Vec3 hit) {
        float[] rot = computePlacementAngles(hit, mc.thePlayer.getPositionEyes(1.0F));
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - SilentAim.getServerYaw()));
        float pitchDiff = Math.abs(rot[1] - SilentAim.getServerPitch());
        return yawDiff + pitchDiff;
    }

    private boolean isValidPlacement(PlaceTarget t) {
        if (t == null) return false;
        if (!isReplaceable(t.placePos)) return false;
        if (!isFullPlacementSupport(t.support)) return false;
        if (!isExposedFace(t.support, t.face)) return false;
        if (placeWouldCollideWithPlayer(t.placePos)) return false;
        if (projectedInterceptTick(t.placePos) < 0) return false;
        if (!withinReach(t.hitVec)) return false;
        return true;
    }

    private double placementReach() {
        double controllerReach = mc.playerController == null
                ? 4.5D : mc.playerController.getBlockReachDistance();
        return Math.min(searchRadius.getInput(), controllerReach);
    }

    /**
     * Reachable from where the eyes are now, or from where they will be on the
     * tick the aim request submitted this tick actually lands. Testing only the
     * present position discards every target that is about to come into range —
     * during a fall that is most of them, and by the time one passes the test
     * there is no longer time left to rotate onto it.
     */
    private boolean withinReach(Vec3 hit) {
        double reach = placementReach();
        return mc.thePlayer.getPositionEyes(1.0F).distanceTo(hit) <= reach
                || leadEyes().distanceTo(hit) <= reach;
    }

    /**
     * Final server-view validation, performed in POST immediately before C08.
     * Returns the hit when the rotation on the wire really does land on the
     * intended face, so the caller can send that exact hit vec.
     */
    private MovingObjectPosition placementRaycast(PlaceTarget t) {
        if (t == null || mc.theWorld == null || mc.thePlayer == null) return null;

        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = rotationVector(SilentAim.getServerPitch(), SilentAim.getServerYaw());
        double reach = placementReach() + 0.5D;
        Vec3 end = eyes.addVector(
                look.xCoord * reach,
                look.yCoord * reach,
                look.zCoord * reach);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
        return hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && t.support.equals(hit.getBlockPos())
                && hit.sideHit == t.face ? hit : null;
    }

    private Vec3 rotationVector(float pitch, float yaw) {
        float yawCos = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float yawSin = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float pitchCos = -MathHelper.cos(-pitch * 0.017453292F);
        float pitchSin = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(yawSin * pitchCos, pitchSin, yawCos * pitchCos);
    }

    private float[] computePlacementAngles(Vec3 target, Vec3 eyes) {
        double dx = target.xCoord - eyes.xCoord;
        double dy = target.yCoord - eyes.yCoord;
        double dz = target.zCoord - eyes.zCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{yaw, MathHelper.clamp_float(pitch, -89.5F, 89.5F)};
    }

    /**
     * Cheap pre-filter for "is it worth raycasting this tick".
     *
     * <p>Compares against the angles measured from where the eyes are *now*,
     * not the lead angles in {@code currentTarget} — {@code serverYaw} was
     * produced by last tick's request, whose target was this tick's predicted
     * eye position, so present-tense angles are the like-for-like comparison.
     *
     * <p>The window is deliberately loose. {@link #placementRaycast} decides
     * whether the shot is actually on the face, and a miss now costs nothing
     * because tickPost keeps the target and retries next tick. A tight window
     * only delays the first attempt.
     */
    private boolean rotReady() {
        if (currentTarget == null) return false;
        float[] now = computePlacementAngles(currentTarget.hitVec, mc.thePlayer.getPositionEyes(1.0F));
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(now[0] - SilentAim.getServerYaw()));
        float pitchDiff = Math.abs(now[1] - SilentAim.getServerPitch());
        float threshold = emergencyPlace ? 12.0F : 6.0F;
        return yawDiff <= threshold && pitchDiff <= threshold;
    }

    private boolean doPlace(PlaceTarget t, Vec3 hitVec) {
        if (!isHoldingPlaceableBlock()) return false;
        ItemStack held = mc.thePlayer.getHeldItem();

        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, t.support, t.face, hitVec);

        if (placed && swing.isToggled()) mc.thePlayer.swingItem();

        return placed;
    }

    private long placeDelayMs() {
        return (long) (placeDelay.getInput() * 50.0D);
    }

    private int findBestBlockSlot() {
        int woolSlot = -1;
        int bestSlot = -1;
        int bestCount = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemBlock)) continue;
            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (block == null) continue;
            if (!block.isFullBlock() && !block.isFullCube()) continue;
            if (block == Blocks.tnt || block == Blocks.sand || block == Blocks.gravel) continue;
            if (block == Blocks.wool && woolSlot == -1) woolSlot = slot;
            if (stack.stackSize > bestCount) {
                bestCount = stack.stackSize;
                bestSlot = slot;
            }
        }
        return woolSlot != -1 ? woolSlot : bestSlot;
    }

    private boolean isReplaceable(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == Blocks.air || block instanceof BlockLiquid || block.getMaterial().isReplaceable();
    }

    private boolean isFullPlacementSupport(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.air) return false;
        if (block instanceof BlockLiquid) return false;
        if (block.getMaterial().isReplaceable()) return false;
        // A material-only check accepts signs and torches; merely requiring a
        // collision box still accepts slabs and fences, whose faces are not
        // where the face-centre hit vec says they are. So require a full
        // 1x1x1 collision box.
        //
        // This used to test isFullBlock() && isFullCube(). isFullBlock is just
        // isOpaqueCube() captured at construction, so every non-opaque cube —
        // glass and stained glass above all — was rejected as a support. That
        // is precisely the block you are falling past on a bedwars bridge, and
        // it made the module useless there. The geometry, not the opacity, is
        // what the hit vec depends on.
        AxisAlignedBB bb = block.getCollisionBoundingBox(mc.theWorld, pos, state);
        return bb != null
                && bb.maxX - bb.minX > 0.999D
                && bb.maxY - bb.minY > 0.999D
                && bb.maxZ - bb.minZ > 0.999D;
    }

    private void resetRuntime(boolean restoreSlot) {
        SilentAim.release(this);
        if (restoreSlot && previousSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        previousSlot = -1;
        clearTransientState();
        placementBudgetExhausted = false;
        state = State.IDLE;
    }

    private void clearTransientState() {
        currentTarget = null;
        placedCount = 0;
        rotationTicks = 0;
        lastPlaceMs = 0L;
        emergencyPlace = false;
        lastTicksToImpact = -1;
        haveImpact = false;
        impactX = 0.0D;
        impactY = 0.0D;
        impactZ = 0.0D;
        targetCacheTick = -1;
        cachedTarget = null;
    }

    private static final class PlaceTarget {
        final BlockPos placePos;
        final BlockPos support;
        final EnumFacing face;
        final Vec3 hitVec;
        float rotationYaw;
        float rotationPitch;
        PlaceTarget(BlockPos placePos, BlockPos support, EnumFacing face, Vec3 hitVec) {
            this.placePos = placePos;
            this.support = support;
            this.face = face;
            this.hitVec = hitVec;
        }
    }
}
