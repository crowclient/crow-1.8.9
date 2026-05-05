package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.LookEvent;
import crow.client.event.impl.MoveInputEvent;
import crow.client.event.impl.UpdateEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
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

    private enum State { IDLE, ROTATING, PLACING, COOLDOWN }
    private State state = State.IDLE;

    private PlaceTarget currentTarget;
    private int placedCount;
    private int previousSlot = -1;
    private long lastPlaceMs;
    private long cooldownUntilMs;
    private int rotationTicks;

    private int slotSyncTicks;

    private float serverYaw, serverPitch;
    private float prevServerYaw, prevServerPitch;

    private static final double GRAVITY = 0.08D;
    private static final double DRAG_Y  = 0.98D;
    private static final double DRAG_XZ = 0.91D;
    private static final double TERMINAL_VEL = -3.92D;

    private static final int PROJECTION_TICKS = 80;
    private static final int VOID_Y = 0;
    private static final long DECLINE_COOLDOWN_MS = 150L;
    private static final long POST_CHAIN_COOLDOWN_MS = 200L;

    public Clutch() {
        super("Clutch", ModuleCategory.combat);
        this.registerSetting(maxBlocks            = new SliderSetting("Max Blocks", 5.0D, 1.0D, 10.0D, 1.0D));
        this.registerSetting(damageThreshold      = new SliderSetting("Damage Threshold", 3.0D, 1.0D, 6.0D, 0.5D));
        this.registerSetting(rotationSpeed        = new SliderSetting("Rotation Speed", 30.0D, 4.0D, 50.0D, 0.5D));
        this.registerSetting(placeDelay           = new SliderSetting("Place Delay (ticks)", 0.0D, 0.0D, 4.0D, 1.0D));
        this.registerSetting(searchRadius         = new SliderSetting("Search Radius", 4.5D, 2.0D, 5.0D, 0.5D));
        this.registerSetting(saveFromVoid         = new TickSetting("Save From Void", true));
        this.registerSetting(cancelIfUnpreventable= new TickSetting("Cancel If Unpreventable", true));
        this.registerSetting(autoSwap             = new TickSetting("Auto Swap", true));

        this.registerSetting(swapBack             = new TickSetting("Swap Back", false));
        this.registerSetting(swing                = new TickSetting("Swing Arm", true));
        this.registerSetting(freezeMove           = new TickSetting("Freeze Movement", true));
    }

    @Override
    public void onEnable() {
        resetRuntime(true);
        if (Utils.Player.isPlayerInGame()) {
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
            prevServerYaw = serverYaw;
            prevServerPitch = serverPitch;
        }
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
    public void onLook(LookEvent e) {

        if (state == State.IDLE || state == State.COOLDOWN) return;
        if (mc.gameSettings.thirdPersonView == 0) return;
        e.setYaw(serverYaw);
        e.setPitch(serverPitch);
        e.setPrevYaw(prevServerYaw);
        e.setPrevPitch(prevServerPitch);
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent e) {

        if (!freezeMove.isToggled()) return;
        if (state == State.IDLE || state == State.COOLDOWN) return;
        e.setStrafe(0F);
        e.setForward(0F);
    }

    private void tickPre(UpdateEvent e) {
        long now = System.currentTimeMillis();

        switch (state) {
            case IDLE:
                if (now < cooldownUntilMs) break;
                tryArm();
                break;
            case ROTATING:

                if (currentTarget == null || !isValidPlacement(currentTarget)) {
                    if (!findAndArmTarget()) { decline(); break; }
                } else {
                    float[] freshRot = computePlacementAngles(currentTarget.hitVec);
                    currentTarget.rotationYaw = freshRot[0];
                    currentTarget.rotationPitch = freshRot[1];
                }
                tickRotation();
                if (slotSyncTicks > 0) slotSyncTicks--;
                if (rotReady() && slotSyncTicks <= 0) {

                    serverYaw = currentTarget.rotationYaw;
                    serverPitch = currentTarget.rotationPitch;
                    state = State.PLACING;
                }
                break;
            case PLACING:

                break;
            case COOLDOWN:
                if (now >= cooldownUntilMs) {
                    state = State.IDLE;
                }
                break;
        }

        if (state == State.ROTATING || state == State.PLACING) {
            e.setYaw(serverYaw);
            e.setPitch(serverPitch);
        }
    }

    private void tickPost() {
        if (state != State.PLACING || currentTarget == null) return;

        if (!isValidPlacement(currentTarget)) {
            currentTarget = null;
            state = State.ROTATING;
            return;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            decline();
            return;
        }

        if (System.currentTimeMillis() - lastPlaceMs < placeDelayMs()) return;

        if (doPlace(currentTarget)) {
            placedCount++;
            lastPlaceMs = System.currentTimeMillis();
            currentTarget = null;

            FallVerdict v = assess();
            if (v.danger == Danger.SAFE || placedCount >= (int) maxBlocks.getInput()) {
                finishChain();
                return;
            }
            if (!findAndArmTarget()) { finishChain(); return; }
            state = State.ROTATING;
        } else {
            decline();
        }
    }

    private void tryArm() {
        if (mc.thePlayer.onGround) return;
        if (mc.thePlayer.motionY > -0.18D) return;
        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) return;
        if (findBestBlockSlot() == -1) return;

        FallVerdict v = assess();
        switch (v.danger) {
            case SAFE:
                return;
            case DAMAGE:
                if (cancelIfUnpreventable.isToggled() && !v.preventable) {
                    decline();
                    return;
                }
                break;
            case VOID:
                if (!saveFromVoid.isToggled() && !v.preventable) {
                    decline();
                    return;
                }
                break;
        }

        if (!findAndArmTarget()) {
            decline();
            return;
        }

        serverYaw = mc.thePlayer.rotationYaw;
        serverPitch = mc.thePlayer.rotationPitch;
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;
        state = State.ROTATING;
        rotationTicks = 0;
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

    private double angularDeltaToFaceDeg(Vec3 hit) {
        float[] rot = computePlacementAngles(hit);
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - serverYaw));
        float pitchDiff = Math.abs(rot[1] - serverPitch);
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

        long seed = Double.doubleToLongBits(Math.floor(target.xCoord * 32.0))
                  ^ Double.doubleToLongBits(Math.floor(target.yCoord * 32.0)) * 0x9E3779B97F4A7C15L
                  ^ Double.doubleToLongBits(Math.floor(target.zCoord * 32.0)) * 0xBF58476D1CE4E5B9L;
        float yawJitter   = ((seed         & 0xFFFF) / 65535.0F - 0.5F) * 0.90F;
        float pitchJitter = (((seed >>> 16) & 0xFFFF) / 65535.0F - 0.5F) * 0.60F;
        yaw   += yawJitter;
        pitch += pitchJitter;

        return new float[]{yaw, MathHelper.clamp_float(pitch, -89.5F, 89.5F)};
    }

    private void tickRotation() {
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;
        rotationTicks++;
        state = State.ROTATING;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float baseSpeed = Math.max(2.0F, (float) rotationSpeed.getInput());
        float maxTurn = baseSpeed * (0.88F + random.nextFloat() * 0.18F);

        float yawDelta = MathHelper.wrapAngleTo180_float(currentTarget.rotationYaw - serverYaw);
        float pitchDelta = currentTarget.rotationPitch - serverPitch;

        float yawProportion = 0.70F + random.nextFloat() * 0.10F;
        float pitchProportion = 0.62F + random.nextFloat() * 0.10F;

        float yawStep = MathHelper.clamp_float(yawDelta * yawProportion, -maxTurn, maxTurn);
        float pitchStep = MathHelper.clamp_float(pitchDelta * pitchProportion, -maxTurn * 0.85F, maxTurn * 0.85F);

        float jitter = baseSpeed * 0.010F;
        yawStep   += (random.nextFloat() - 0.5F) * jitter;
        pitchStep += (random.nextFloat() - 0.5F) * jitter * 0.6F;

        yawStep   = patchRotationStep(yawStep, yawDelta);
        pitchStep = patchRotationStep(pitchStep, pitchDelta);

        serverYaw += yawStep;
        serverPitch = MathHelper.clamp_float(serverPitch + pitchStep, -89.5F, 89.5F);
    }

    private float patchRotationStep(float step, float targetDelta) {
        if (Math.abs(targetDelta) <= 0.001F) return 0.0F;
        float patched = Utils.Player.patchGCD(step);
        if (patched == 0.0F) {
            float gcd = Utils.Player.getGcd();
            patched = (float) Math.copySign(Math.min(Math.abs(targetDelta), gcd), targetDelta);
        }
        if (Math.abs(patched) > Math.abs(targetDelta)) patched = targetDelta;
        return patched;
    }

    private boolean rotReady() {
        if (currentTarget == null) return false;
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(currentTarget.rotationYaw - serverYaw));
        float pitchDiff = Math.abs(currentTarget.rotationPitch - serverPitch);

        return yawDiff <= 4.0F && pitchDiff <= 4.0F;
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

    private void decline() {
        currentTarget = null;
        cooldownUntilMs = System.currentTimeMillis() + DECLINE_COOLDOWN_MS;
        state = State.COOLDOWN;
    }

    private void finishChain() {
        currentTarget = null;
        if (swapBack.isToggled() && previousSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        previousSlot = -1;
        placedCount = 0;
        cooldownUntilMs = System.currentTimeMillis() + POST_CHAIN_COOLDOWN_MS;
        state = State.COOLDOWN;
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
        cooldownUntilMs = 0L;
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
