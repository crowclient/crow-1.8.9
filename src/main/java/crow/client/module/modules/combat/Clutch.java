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
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.concurrent.ThreadLocalRandom;

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
    private int previousSlot = -1;
    private long lastPlaceMs;
    private int rotationTicks;

    private int slotSyncTicks;

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
        this.registerSetting(swing                = new TickSetting("Swing", false));
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

            return;
        }

        if (e.isPre()) {
            tickPre(e);
        } else {
            tickPost();
        }
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent e) {
        if (!freezeMove.isToggled()) return;
        if (state == State.IDLE) return;
        e.setStrafe(0F);
        e.setForward(0F);
    }

    private void tickPre(UpdateEvent e) {
        // Single source of truth: are we in a fall situation that warrants clutch?
        // Avoids the old decline/cooldown loop which caused state to flicker between
        // ROTATING and IDLE during a fall, snapping the silent rotation each cycle.
        if (!isInDanger()) {
            if (state != State.IDLE) {
                wrapUp();
            }
            return;
        }

        // Acquire / refresh target.
        if (currentTarget == null || !isValidPlacement(currentTarget)) {
            currentTarget = null;
            if (!findAndArmTarget()) {
                // No reachable target this tick. Don't aim, don't reset state —
                // try again next tick as the player falls closer.
                return;
            }
            state = State.ROTATING;
        } else {
            // Player has moved; update angles toward the same hit vec.
            float[] freshRot = computePlacementAngles(currentTarget.hitVec);
            currentTarget.rotationYaw = freshRot[0];
            currentTarget.rotationPitch = freshRot[1];
        }

        rotationTicks++;
        if (slotSyncTicks > 0) slotSyncTicks--;

        if (state == State.ROTATING && rotReady() && slotSyncTicks <= 0) {
            state = State.PLACING;
        }

        float speed = (float) rotationSpeed.getInput();
        float t = MathHelper.clamp_float((speed - 4f) / (60f - 4f), 0f, 1f);
        // Stiffness ramps higher at max speed so emergency clutches converge
        // in 1–2 ticks instead of 3+. Keep the low end gentle so casual
        // placements still look organic.
        float stiff = 0.34f + t * (0.96f - 0.34f);

        SilentAim.Request req = new SilentAim.Request();
        req.yaw = currentTarget.rotationYaw;
        req.pitch = currentTarget.rotationPitch;
        req.profile = SilentAim.Profile.COMBAT;
        req.priority = 80;
        // Allow up to 1.4× the speed setting per tick so the spring can land
        // a full 90° wall→wall in 2 ticks rather than 3. SilentAim still
        // applies its own anti-snap cap below for raw safety.
        req.maxYawStepDeg = speed * 1.4f;
        req.maxPitchStepDeg = speed * 1.2f;
        req.stiffness = stiff;
        req.fixMovement = false;
        req.disableTremor = true;
        req.disableReaction = true;
        req.claimant = this;
        SilentAim.aim(req);
        SilentAim.applyToUpdate(e);
    }

    private void tickPost() {
        if (state != State.PLACING || currentTarget == null) return;
        if (System.currentTimeMillis() - lastPlaceMs < placeDelayMs()) return;

        if (!isValidPlacement(currentTarget)) {
            currentTarget = null;
            state = State.ROTATING;
            return;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            currentTarget = null;
            state = State.ROTATING;
            return;
        }

        if (doPlace(currentTarget)) {
            placedCount++;
            lastPlaceMs = System.currentTimeMillis();
            currentTarget = null;
            if (placedCount >= (int) maxBlocks.getInput()) {
                wrapUp();
                return;
            }
            // Stay in chain: next tick will re-arm if still in danger.
            state = State.ROTATING;
        } else {
            // Place failed (server rejected, client raycast missed, etc).
            currentTarget = null;
            state = State.ROTATING;
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
        return true;
    }

    private void wrapUp() {
        if (swapBack.isToggled() && previousSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        previousSlot = -1;
        currentTarget = null;
        placedCount = 0;
        rotationTicks = 0;
        slotSyncTicks = 0;
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

            slotSyncTicks = 1;
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
        FallVerdict(Danger d, boolean p) { this.danger = d; this.preventable = p; }
    }

    private FallVerdict assess() {
        FallProjection p = projectFall(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                                        mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ,
                                        PROJECTION_TICKS);

        double threshold = damageThreshold.getInput() + jumpBoostBonus();

        if (p.voidImpact) {
            return new FallVerdict(Danger.VOID, canFindAnyTarget());
        }
        if (p.fallDistAtImpact > threshold) {
            int firstPlaceTick = estimatedTicksToFirstPlace();
            FallProjection p2 = projectFall(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                                             mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ,
                                             firstPlaceTick);
            boolean stillInWindow = p2.fallDistAtEnd <= threshold;
            boolean canPlace = canFindAnyTarget();
            return new FallVerdict(Danger.DAMAGE, canPlace && stillInWindow);
        }
        return new FallVerdict(Danger.SAFE, false);
    }

    private int estimatedTicksToFirstPlace() {
        float speed = (float) rotationSpeed.getInput();
        int rotTicks = MathHelper.clamp_int(Math.round(20.0F / Math.max(2.0F, speed)) + 1, 1, 6);
        return rotTicks + 1;
    }

    private double jumpBoostBonus() {
        PotionEffect eff = mc.thePlayer.getActivePotionEffect(Potion.jump);
        if (eff == null) return 0.0D;
        return eff.getAmplifier() + 1;
    }

    private FallProjection projectFall(double px, double py, double pz,
                                        double mx, double my, double mz, int horizon) {
        FallProjection out = new FallProjection();
        double fall = 0.0D;
        for (int t = 1; t <= horizon; t++) {
            my = (my - GRAVITY) * DRAG_Y;
            if (my < TERMINAL_VEL) my = TERMINAL_VEL;
            mx *= DRAG_XZ;
            mz *= DRAG_XZ;
            px += mx; py += my; pz += mz;

            if (my < 0) fall += -my;

            if (py < VOID_Y) {
                out.voidImpact = true;
                out.ticksUntilImpact = t;
                out.fallDistAtImpact = fall;
                out.fallDistAtEnd = fall;
                return out;
            }
            if (hasFootSupport(px, py, pz)) {
                out.ticksUntilImpact = t;
                out.fallDistAtImpact = fall;
                out.fallDistAtEnd = fall;
                return out;
            }
        }
        out.ticksUntilImpact = -1;
        out.fallDistAtImpact = fall;
        out.fallDistAtEnd = fall;
        return out;
    }

    private static final class FallProjection {
        boolean voidImpact;
        int ticksUntilImpact;
        double fallDistAtImpact;
        double fallDistAtEnd;
    }

    private boolean hasFootSupport(double x, double y, double z) {

        double half = mc.thePlayer.width / 2.0D;
        double yProbe = y - 0.05D;
        return isSolidAt(x - half, yProbe, z - half)
            || isSolidAt(x + half, yProbe, z - half)
            || isSolidAt(x - half, yProbe, z + half)
            || isSolidAt(x + half, yProbe, z + half)
            || isSolidAt(x,        yProbe, z);
    }

    private boolean isSolidAt(double x, double y, double z) {
        BlockPos bp = new BlockPos(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
        return isSolid(bp);
    }

    private boolean canFindAnyTarget() {
        return findBestTarget() != null;
    }

    private boolean findAndArmTarget() {
        PlaceTarget t = findBestTarget();
        if (t == null) return false;

        if (!swapToBlockSlot()) return false;
        currentTarget = t;
        float[] rot = computePlacementAngles(t.hitVec);
        currentTarget.rotationYaw = rot[0];
        currentTarget.rotationPitch = rot[1];
        rotationTicks = 0;
        return true;
    }

    private PlaceTarget findBestTarget() {
        if (mc.thePlayer == null) return null;
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);

        double maxReach = Math.min(searchRadius.getInput(), 4.5D);

        double pX = mc.thePlayer.posX;
        double pY = mc.thePlayer.posY;
        double pZ = mc.thePlayer.posZ;
        BlockPos feet = new BlockPos(
                MathHelper.floor_double(pX),
                MathHelper.floor_double(pY - 0.2D),
                MathHelper.floor_double(pZ));

        PlaceTarget best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        int rad = (int) Math.ceil(maxReach);
        for (int dy = rad; dy >= -rad; dy--) {
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    BlockPos place = feet.add(dx, dy, dz);
                    if (!isReplaceable(place)) continue;
                    if (placeWouldCollideWithPlayer(place)) continue;

                    for (EnumFacing face : EnumFacing.values()) {
                        BlockPos support = place.offset(face);
                        if (!isSolid(support)) continue;
                        if (!isExposedFace(support, face.getOpposite())) continue;

                        EnumFacing clickFace = face.getOpposite();
                        Vec3 hit = new Vec3(
                                support.getX() + 0.5D + clickFace.getFrontOffsetX() * 0.48D,
                                support.getY() + 0.5D + clickFace.getFrontOffsetY() * 0.48D,
                                support.getZ() + 0.5D + clickFace.getFrontOffsetZ() * 0.48D);

                        if (eyes.distanceTo(hit) > maxReach) continue;

                        double bx = place.getX() + 0.5D - pX;
                        double by = place.getY() + 0.5D - (pY - 0.5D);
                        double bz = place.getZ() + 0.5D - pZ;
                        double feetDist = Math.sqrt(bx * bx + by * by + bz * bz);
                        double score = -feetDist * 30.0D;

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
        float[] rot = computePlacementAngles(hit);
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - SilentAim.getServerYaw()));
        float pitchDiff = Math.abs(rot[1] - SilentAim.getServerPitch());
        return yawDiff + pitchDiff;
    }

    private boolean isValidPlacement(PlaceTarget t) {
        if (t == null) return false;
        if (!isReplaceable(t.placePos)) return false;
        if (!isSolid(t.support)) return false;

        if (mc.thePlayer.getPositionEyes(1.0F).distanceTo(t.hitVec) > 4.5D) return false;
        return true;
    }

    private float[] computePlacementAngles(Vec3 target) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double dx = target.xCoord - eyes.xCoord;
        double dy = target.yCoord - eyes.yCoord;
        double dz = target.zCoord - eyes.zCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{yaw, MathHelper.clamp_float(pitch, -89.5F, 89.5F)};
    }

    private boolean rotReady() {
        if (currentTarget == null) return false;
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(
                currentTarget.rotationYaw - SilentAim.getServerYaw()));
        float pitchDiff = Math.abs(currentTarget.rotationPitch - SilentAim.getServerPitch());

        // Tighter threshold at higher speeds (spring converges fast → can
        // afford accuracy), looser at low speeds so the spring doesn't
        // oscillate inside the window without ever settling.
        float speed = (float) rotationSpeed.getInput();
        float speedT = MathHelper.clamp_float((speed - 4f) / (60f - 4f), 0f, 1f);
        float threshold = 3.0F - speedT * 1.0F;   // 3° at slow → 2° at fast
        return yawDiff <= threshold && pitchDiff <= threshold;
    }

    private boolean doPlace(PlaceTarget t) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return false;

        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, t.support, t.face, t.hitVec);

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

    private boolean isSolid(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == Blocks.air) return false;
        if (block instanceof BlockLiquid) return false;
        if (block.getMaterial().isReplaceable()) return false;
        return true;
    }

    private void resetRuntime(boolean restoreSlot) {
        if (restoreSlot && previousSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        previousSlot = -1;
        currentTarget = null;
        placedCount = 0;
        rotationTicks = 0;
        slotSyncTicks = 0;
        lastPlaceMs = 0L;
        state = State.IDLE;
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
