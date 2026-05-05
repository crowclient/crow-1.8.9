package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.LookEvent;
import crow.client.event.impl.MoveInputEvent;
import crow.client.event.impl.UpdateEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BlockIn extends Module {

    private static final int[][] WALL_OFFSETS = {
            { 1, 0, 0}, {-1, 0, 0}, { 0, 0, 1}, { 0, 0,-1},
            { 1, 1, 0}, {-1, 1, 0}, { 0, 1, 1}, { 0, 1,-1},
    };
    private static final int[][] TOP_OFFSET = {{ 0, 2, 0 }};
    private static final int[][] BOTTOM_OFFSET = {{ 0,-1, 0 }};

    private static final float MIN_RANDOM_OFFSET = 0.05F;

    public enum RaycastMode {
        Normal,
        Strict,
        Grim
    }

    private final SliderSetting placeRange;
    private final SliderSetting placeDelay;
    private final SliderSetting rotationSpeed;
    private final TickSetting topCap;
    private final TickSetting bottomCap;
    private final TickSetting swing;
    private final TickSetting autoDisable;
    private final TickSetting grimBypass;
    private final SliderSetting randomOffsetStrength;
    private final ComboSetting<RaycastMode> raycastMode;

    private final List<BlockPos> placeQueue = new ArrayList<>();

    private int previousSlot = -1;
    private boolean active;
    private BlockPos anchorPos;
    private PlaceInfo currentTarget;
    private boolean hasTarget;
    private boolean placeThisPost;
    private int delayTicks;

    private float serverYaw;
    private float serverPitch;
    private float prevServerYaw;
    private float prevServerPitch;

    private int rotationTicks;

    public BlockIn() {
        super("BlockIn", ModuleCategory.combat);
        this.registerSetting(placeRange = new SliderSetting("Place Range", 4.5D, 3.0D, 6.0D, 0.1D));
        this.registerSetting(placeDelay = new SliderSetting("Place Delay", 0.0D, 0.0D, 8.0D, 1.0D));
        this.registerSetting(rotationSpeed = new SliderSetting("Rotation Speed", 28.0D, 2.0D, 40.0D, 0.5D));
        this.registerSetting(topCap = new TickSetting("Top cap", true));
        this.registerSetting(bottomCap = new TickSetting("Bottom cap", false));
        this.registerSetting(swing = new TickSetting("Swing arm", true));
        this.registerSetting(autoDisable = new TickSetting("Auto disable", true));
        this.registerSetting(grimBypass = new TickSetting("Grim Bypass Mode", true));
        this.registerSetting(randomOffsetStrength = new SliderSetting("Random Offset Strength", 0.45D, 0.05D, 1.5D, 0.05D));
        this.registerSetting(raycastMode = new ComboSetting<>("Raycast Mode", RaycastMode.Grim));
    }

    @Override
    public void onEnable() {
        resetRuntime(false);
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
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null || mc.currentScreen != null) {
            resetRuntime(true);
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
        if (!hasTarget) {
            return;
        }
        e.setYaw(serverYaw);
    }

    @Subscribe
    public void onLook(LookEvent e) {
        if (!hasTarget || mc.gameSettings.thirdPersonView == 0) {
            return;
        }

        e.setYaw(serverYaw);
        e.setPitch(serverPitch);
        e.setPrevYaw(prevServerYaw);
        e.setPrevPitch(prevServerPitch);
        syncVisualRotations();
    }

    private void tickPre(UpdateEvent e) {
        placeThisPost = false;

        if (!active) {
            tryStart();
        }

        if (!active) {
            hasTarget = false;
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
            prevServerYaw = serverYaw;
            prevServerPitch = serverPitch;
            e.setYaw(serverYaw);
            e.setPitch(serverPitch);
            return;
        }

        if (!isHoldingPlaceableBlock()) {
            int blockSlot = findBestBlockSlot();
            if (blockSlot == -1) {
                finish();
                e.setYaw(serverYaw);
                e.setPitch(serverPitch);
                return;
            }
            mc.thePlayer.inventory.currentItem = blockSlot;
        }

        if (delayTicks > 0) {
            delayTicks--;
        }

        if (currentTarget == null || !isReplaceable(currentTarget.targetPos)) {
            acquireNextTarget();
        }

        if (currentTarget == null) {
            finish();
            e.setYaw(serverYaw);
            e.setPitch(serverPitch);
            return;
        }

        hasTarget = true;
        tickRotation();
        e.setYaw(serverYaw);
        e.setPitch(serverPitch);

        if (delayTicks <= 0 && rotReady()) {

            attemptPlace();
        }
    }

    private void tickPost() {

    }

    private void attemptPlace() {
        if (currentTarget == null) {
            return;
        }
        if (!isReplaceable(currentTarget.targetPos)) {
            currentTarget = null;
            return;
        }
        if (!passesRaycast(currentTarget)) {

            placeQueue.add(currentTarget.targetPos);
            currentTarget = null;
            return;
        }
        if (doPlace(currentTarget)) {
            delayTicks = getNextDelayTicks();
        }
        currentTarget = null;
        if (placeQueue.isEmpty()) {
            acquireNextTarget();
            if (currentTarget == null && autoDisable.isToggled()) {
                finish();
            }
        }
    }

    private void tryStart() {
        anchorPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        buildPlaceQueue();

        if (placeQueue.isEmpty()) {
            if (autoDisable.isToggled()) {
                this.disable();
            }
            return;
        }

        int blockSlot = findBestBlockSlot();
        if (blockSlot == -1) {
            if (autoDisable.isToggled()) {
                this.disable();
            }
            return;
        }

        if (previousSlot == -1) {
            previousSlot = mc.thePlayer.inventory.currentItem;
        }
        mc.thePlayer.inventory.currentItem = blockSlot;
        active = true;
        delayTicks = 0;
        acquireNextTarget();
    }

    private void acquireNextTarget() {
        currentTarget = null;
        rotationTicks = 0;

        int requeued = 0;
        int maxRetries = placeQueue.size();

        while (!placeQueue.isEmpty() && requeued <= maxRetries) {
            BlockPos target = placeQueue.remove(0);
            if (!isReplaceable(target)) {

                requeued = 0;
                continue;
            }

            PlaceInfo found = findPlacement(target);
            if (found == null) {

                placeQueue.add(target);
                requeued++;
                continue;
            }

            float[] rotations = computePlacementAngles(found.hitVec);
            float[] randomized = applyPlacementRandomization(rotations[0], rotations[1], target);
            found.rotationYaw = randomized[0];
            found.rotationPitch = randomized[1];
            currentTarget = found;
            return;
        }
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

    private float[] applyPlacementRandomization(float yaw, float pitch, BlockPos targetPos) {
        long seed = Double.doubleToLongBits((double) targetPos.getX())
                  ^ Double.doubleToLongBits((double) targetPos.getY()) * 0x9E3779B97F4A7C15L
                  ^ Double.doubleToLongBits((double) targetPos.getZ()) * 0xBF58476D1CE4E5B9L;
        float fYaw   = ((seed         & 0xFFFF) / 65535.0F) - 0.5F;
        float fPitch = (((seed >>> 16) & 0xFFFF) / 65535.0F) - 0.5F;

        float strength = Math.max(MIN_RANDOM_OFFSET, (float) randomOffsetStrength.getInput());
        float yawAmp   = Math.min(0.45F, strength * 0.6F);
        float pitchAmp = Math.min(0.30F, strength * 0.45F);

        yaw   += fYaw   * 2.0F * yawAmp;
        pitch += fPitch * 2.0F * pitchAmp;
        pitch = MathHelper.clamp_float(pitch, -89.5F, 89.5F);
        return new float[]{yaw, pitch};
    }

    private void tickRotation() {
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;
        rotationTicks++;

        float targetYaw = currentTarget.rotationYaw;
        float targetPitch = currentTarget.rotationPitch;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        float baseSpeed = Math.max(1.0F, (float) rotationSpeed.getInput());
        float maxTurn = baseSpeed * (0.88F + random.nextFloat() * 0.18F);

        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float pitchDelta = targetPitch - serverPitch;

        float yawProportion = 0.55F + random.nextFloat() * 0.10F;
        float pitchProportion = 0.50F + random.nextFloat() * 0.10F;

        float yawStep = MathHelper.clamp_float(yawDelta * yawProportion, -maxTurn, maxTurn);
        float pitchStep = MathHelper.clamp_float(pitchDelta * pitchProportion, -maxTurn * 0.85F, maxTurn * 0.85F);

        float jitter = baseSpeed * 0.010F;
        yawStep += (random.nextFloat() - 0.5F) * jitter;
        pitchStep += (random.nextFloat() - 0.5F) * jitter * 0.6F;

        yawStep = patchRotationStep(yawStep, yawDelta);
        pitchStep = patchRotationStep(pitchStep, pitchDelta);

        serverYaw += yawStep;
        serverPitch = MathHelper.clamp_float(serverPitch + pitchStep, -89.5F, 89.5F);
        syncVisualRotations();
    }

    private float patchRotationStep(float step, float targetDelta) {
        if (Math.abs(targetDelta) <= 0.001F) {
            return 0.0F;
        }

        float patched = Utils.Player.patchGCD(step);
        if (patched == 0.0F) {
            float gcd = Utils.Player.getGcd();
            patched = (float) Math.copySign(Math.min(Math.abs(targetDelta), gcd), targetDelta);
        }

        if (Math.abs(patched) > Math.abs(targetDelta)) {
            patched = targetDelta;
        }
        return patched;
    }

    private boolean rotReady() {
        if (currentTarget == null) {
            return false;
        }

        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(currentTarget.rotationYaw - serverYaw));
        float pitchDiff = Math.abs(currentTarget.rotationPitch - serverPitch);
        float threshold = grimBypass.isToggled() ? 2.15F : 4.0F;
        return yawDiff <= threshold && pitchDiff <= threshold;
    }

    private boolean passesRaycast(PlaceInfo info) {
        if (raycastMode.getMode() == RaycastMode.Normal) {
            return true;
        }

        MovingObjectPosition hit = rayTrace(serverYaw, serverPitch, placeRange.getInput() + 0.75D);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }

        return info.neighbor.equals(hit.getBlockPos()) && info.face == hit.sideHit;
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch, double reach) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = getVectorForRotation(pitch, yaw);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        return mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
    }

    private Vec3 getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    private boolean doPlace(PlaceInfo info) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return false;
        }

        boolean placed = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, info.neighbor, info.face, info.hitVec);

        if (placed && swing.isToggled()) {
            mc.thePlayer.swingItem();
        }

        return placed;
    }

    private int getNextDelayTicks() {
        int base = (int) placeDelay.getInput();
        return base + ThreadLocalRandom.current().nextInt(0, 2);
    }

    private void buildPlaceQueue() {
        placeQueue.clear();
        if (anchorPos == null) {
            return;
        }

        for (int[] offset : WALL_OFFSETS) {
            placeQueue.add(anchorPos.add(offset[0], offset[1], offset[2]));
        }
        if (topCap.isToggled()) {
            for (int[] offset : TOP_OFFSET) {
                placeQueue.add(anchorPos.add(offset[0], offset[1], offset[2]));
            }
        }
        if (bottomCap.isToggled()) {
            for (int[] offset : BOTTOM_OFFSET) {
                placeQueue.add(anchorPos.add(offset[0], offset[1], offset[2]));
            }
        }

        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double maxDistance = placeRange.getInput();
        placeQueue.removeIf(pos -> {
            if (!isReplaceable(pos)) {
                return true;
            }
            double dist = eyes.distanceTo(new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
            return dist > maxDistance + 2.0D;
        });

        placeQueue.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingDouble(pos ->
                        eyes.distanceTo(new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D))));
    }

    private PlaceInfo findPlacement(BlockPos target) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double maxDistance = placeRange.getInput();
        PlaceInfo best = null;
        double bestDistance = Double.MAX_VALUE;

        for (EnumFacing face : EnumFacing.values()) {
            BlockPos neighbor = target.offset(face);
            if (!isSolid(neighbor)) {
                continue;
            }

            EnumFacing clickFace = face.getOpposite();
            Vec3 hit = new Vec3(
                    neighbor.getX() + 0.5D + clickFace.getFrontOffsetX() * 0.48D,
                    neighbor.getY() + 0.5D + clickFace.getFrontOffsetY() * 0.48D,
                    neighbor.getZ() + 0.5D + clickFace.getFrontOffsetZ() * 0.48D);

            double distance = eyes.distanceTo(hit);
            if (distance <= maxDistance && distance < bestDistance) {
                bestDistance = distance;
                best = new PlaceInfo(target, neighbor, clickFace, hit);
            }
        }

        return best;
    }

    private boolean isHoldingPlaceableBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) held.getItem()).getBlock();
        return block != null && (block.isFullBlock() || block.isFullCube());
    }

    private int findBestBlockSlot() {
        int woolSlot = -1;
        int bestSlot = -1;
        int bestCount = 0;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (block == null || !block.isFullBlock() && !block.isFullCube()) {
                continue;
            }

            if (block == Blocks.wool && woolSlot == -1) {
                woolSlot = slot;
            }
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
        return block != Blocks.air && !(block instanceof BlockLiquid);
    }

    private void syncVisualRotations() {
        mc.thePlayer.prevRotationYawHead = mc.thePlayer.rotationYawHead;
        mc.thePlayer.rotationYawHead = serverYaw;
        mc.thePlayer.prevRenderYawOffset = mc.thePlayer.renderYawOffset;
        mc.thePlayer.renderYawOffset += MathHelper.wrapAngleTo180_float(serverYaw - mc.thePlayer.renderYawOffset) * 0.4F;
    }

    private void finish() {
        resetRuntime(true);
        if (autoDisable.isToggled()) {
            this.disable();
        }
    }

    private void resetRuntime(boolean restoreSlot) {
        if (restoreSlot && previousSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }

        placeQueue.clear();
        previousSlot = restoreSlot ? -1 : previousSlot;
        active = false;
        anchorPos = null;
        currentTarget = null;
        hasTarget = false;
        placeThisPost = false;
        delayTicks = 0;
        rotationTicks = 0;
    }

    private static final class PlaceInfo {
        private final BlockPos targetPos;
        private final BlockPos neighbor;
        private final EnumFacing face;
        private final Vec3 hitVec;
        private float rotationYaw;
        private float rotationPitch;

        private PlaceInfo(BlockPos targetPos, BlockPos neighbor, EnumFacing face, Vec3 hitVec) {
            this.targetPos = targetPos;
            this.neighbor = neighbor;
            this.face = face;
            this.hitVec = hitVec;
        }
    }
}
