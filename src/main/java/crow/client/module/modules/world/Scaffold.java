package crow.client.module.modules.world;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.GameLoopEvent;
import crow.client.event.impl.MoveInputEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.event.impl.UpdateEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.module.modules.HUD;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.SilentAim;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class Scaffold extends Module {

    public enum ScaffoldMode  { Normal, Eagle, Tower }
    public enum BypassProfile { None, Hypixel }

    private final ComboSetting<ScaffoldMode>  mode;
    private final ComboSetting<BypassProfile> bypass;
    private final TickSetting  safeWalk;
    private final TickSetting  autoSwitch;
    private final TickSetting  keepY;
    private final TickSetting  silentForwardWalk;

    private final SliderSetting placeRange;
    private final SliderSetting minDelay;
    private final SliderSetting maxDelay;

    private final SliderSetting rotSpeed;
    private final SliderSetting pitchTarget;
    private final SliderSetting jitter;

    private final TickSetting   eagleSneak;
    private final SliderSetting towerMotion;

    private float targetYaw, targetPitch;

    private boolean hasTarget;
    private boolean sneakActive;
    private int     delayTicks;
    private double  keepYLevel;
    private int     blockCount;

    private boolean   hypWaitPlace;
    private PlaceInfo hypPending;
    private Vec3      hypHitVec;
    private int       hypOrigSlot;
    private int       hypBlockSlot;
    private int       hypDelay;

    public Scaffold() {
        super("Scaffold", ModuleCategory.world);

        registerSetting(mode    = new ComboSetting<>("Mode",    ScaffoldMode.Normal));
        registerSetting(bypass  = new ComboSetting<>("Bypass",  BypassProfile.None));
        registerSetting(safeWalk          = new TickSetting("SafeWalk",       true));
        registerSetting(keepY             = new TickSetting("Keep Y",         true));
        registerSetting(autoSwitch        = new TickSetting("Auto switch",    true));
        registerSetting(silentForwardWalk = new TickSetting("Silent fwd",     true));

        registerSetting(new DescriptionSetting("--- Placement ---"));
        registerSetting(placeRange = new SliderSetting("Place range", 4.2, 3.0, 5.0, 0.1));
        registerSetting(minDelay   = new SliderSetting("Min delay",   0,   0,   5,   1));
        registerSetting(maxDelay   = new SliderSetting("Max delay",   1,   0,   5,   1));

        registerSetting(new DescriptionSetting("--- Rotation ---"));
        registerSetting(rotSpeed    = new SliderSetting("Rot speed",  14.0, 4.0,  30.0, 0.5));
        registerSetting(pitchTarget = new SliderSetting("Pitch",      78.0, 60.0, 85.0, 1.0));
        registerSetting(jitter      = new SliderSetting("Jitter",      0.4, 0.0,   2.0, 0.05));

        registerSetting(new DescriptionSetting("--- Eagle ---"));
        registerSetting(eagleSneak = new TickSetting("Eagle sneak", true));

        registerSetting(new DescriptionSetting("--- Tower ---"));
        registerSetting(towerMotion = new SliderSetting("Tower motion", 0.42, 0.10, 1.0, 0.02));
    }

    @Override
    public void onEnable() {
        if (Utils.Player.isPlayerInGame()) {
            targetYaw       = mc.thePlayer.rotationYaw;
            targetPitch     = mc.thePlayer.rotationPitch;
            keepYLevel      = mc.thePlayer.posY;
        }
        hasTarget   = false;
        sneakActive = false;
        delayTicks  = 0;
        clearHypixel();
    }

    @Override
    public void onDisable() {
        hasTarget   = false;
        sneakActive = false;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        clearHypixel();
    }

    private void clearHypixel() {
        hypWaitPlace = false;
        hypPending   = null;
        hypHitVec    = null;
        hypOrigSlot  = -1;
        hypBlockSlot = -1;
        hypDelay     = 0;
    }

    @Subscribe
    public void onGameLoop(GameLoopEvent e) {
        updateBlockCount();

        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null) {
            releaseSneak();
            return;
        }

        if (bypass.getMode() == BypassProfile.Hypixel) {
            tickHypixel();
            return;
        }

        if (keepY.isToggled() && mc.thePlayer.onGround) {
            keepYLevel = mc.thePlayer.posY;
        }

        if (mode.getMode() == ScaffoldMode.Tower
                && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
            mc.thePlayer.motionY = towerMotion.getInput();
        }

        if (mode.getMode() == ScaffoldMode.Eagle && eagleSneak.isToggled()) {
            boolean air = Utils.Player.playerOverAir();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), air);
            sneakActive = air;
        } else {
            releaseSneak();
        }

        if (safeWalk.isToggled()) tickSafeWalk();

        if (!ensureBlock()) {
            hasTarget = false;
            driftToCamera();
            return;
        }

        BlockPos target = getPlaceTarget();
        if (target == null) {
            hasTarget = false;
            driftToCamera();
            return;
        }

        PlaceInfo pi = findPlaceInfo(target);
        if (pi == null) {
            hasTarget = false;
            driftToCamera();
            return;
        }
        hasTarget = true;

        computeTargetRotation(pi.hitVec);

        tickRotation();

        if (delayTicks > 0) { delayTicks--; return; }

        if (!rotationReady()) return;

        if (doPlace(pi)) {
            int lo = (int) minDelay.getInput();
            int hi = Math.max(lo, (int) maxDelay.getInput());
            delayTicks = lo + (hi > lo ? ThreadLocalRandom.current().nextInt(hi - lo + 1) : 0);
        }
    }

    private void computeTargetRotation(Vec3 hitVec) {
        float[] raw = calcRotation(hitVec);
        float jit = (float) jitter.getInput();

        targetYaw = raw[0];
        if (jit > 0) {
            targetYaw += (float)(ThreadLocalRandom.current().nextGaussian() * jit * 0.7);
        }

        float geoPitch = raw[1];
        float cfgPitch = (float) pitchTarget.getInput();

        targetPitch = cfgPitch * 0.6F + geoPitch * 0.4F;
        targetPitch = MathHelper.clamp_float(targetPitch, 55.0F, 87.0F);
        if (jit > 0) {
            targetPitch += (float)(ThreadLocalRandom.current().nextGaussian() * jit * 0.4);
        }
    }

    private void tickRotation() {
        float speed = (float) rotSpeed.getInput();
        SilentAim.Request req = new SilentAim.Request();
        req.yaw = targetYaw;
        req.pitch = targetPitch;
        req.profile = SilentAim.Profile.PLACE;
        req.priority = 40;
        req.maxYawStepDeg = speed;
        req.maxPitchStepDeg = speed * 0.85f;
        req.fixMovement = true;
        req.claimant = this;
        SilentAim.aim(req);
    }

    private void driftToCamera() {
        // Glide silent yaw back toward the user's rotation when no place target.
        SilentAim.Request req = new SilentAim.Request();
        req.yaw = mc.thePlayer.rotationYaw;
        req.pitch = mc.thePlayer.rotationPitch;
        req.profile = SilentAim.Profile.PRECISE;
        req.priority = 30;
        req.maxYawStepDeg = 4.0f;
        req.maxPitchStepDeg = 4.0f;
        req.fixMovement = false;
        req.syncVisualHead = false;
        req.claimant = this;
        SilentAim.aim(req);
    }

    private boolean rotationReady() {
        float yOff = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - SilentAim.getServerYaw()));
        float pOff = Math.abs(targetPitch - SilentAim.getServerPitch());
        return yOff < 5.0F && pOff < 5.0F;
    }

    private float currentYaw() {
        return hasTarget ? SilentAim.getServerYaw() : mc.thePlayer.rotationYaw;
    }

    private float[] calcRotation(Vec3 target) {
        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeZ = mc.thePlayer.posZ;
        double dx = target.xCoord - eyeX;
        double dy = target.yCoord - eyeY;
        double dz = target.zCoord - eyeZ;
        double h  = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{ yaw, pitch };
    }

    private BlockPos getPlaceTarget() {
        double baseY = (keepY.isToggled() ? keepYLevel : mc.thePlayer.posY);
        Vec3 dir = movementDir();
        double ahead = 0.6;
        double sx = dir.xCoord * ahead;
        double sz = dir.zCoord * ahead;

        BlockPos[] candidates = {
            fp(mc.thePlayer.posX + sx,            baseY - 0.1, mc.thePlayer.posZ + sz),
            fp(mc.thePlayer.posX,                 baseY - 0.1, mc.thePlayer.posZ),
            fp(mc.thePlayer.posX + sx * 1.6,      baseY - 0.1, mc.thePlayer.posZ + sz * 1.6),
            fp(mc.thePlayer.posX + sx + sz * 0.4, baseY - 0.1, mc.thePlayer.posZ + sz - sx * 0.4),
            fp(mc.thePlayer.posX + sx - sz * 0.4, baseY - 0.1, mc.thePlayer.posZ + sz + sx * 0.4),
        };

        for (BlockPos c : candidates) {
            Block b = mc.theWorld.getBlockState(c).getBlock();
            if (b == Blocks.air || b instanceof BlockLiquid) return c;
        }
        return null;
    }

    private PlaceInfo findPlaceInfo(BlockPos target) {
        PlaceInfo pi = tryFaces(target);
        if (pi != null) return pi;

        for (int d = 1; d <= 2; d++) {
            pi = tryFaces(target.down(d));
            if (pi != null) return pi;
        }

        for (EnumFacing h : EnumFacing.HORIZONTALS) {
            pi = tryFaces(target.offset(h));
            if (pi != null) return pi;
            for (int d = 1; d <= 2; d++) {
                pi = tryFaces(target.offset(h).down(d));
                if (pi != null) return pi;
            }
        }
        return null;
    }

    private PlaceInfo tryFaces(BlockPos target) {
        EnumFacing[] faces = {
            EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
        };
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        PlaceInfo best = null;
        double bestDist = Double.MAX_VALUE;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (EnumFacing f : faces) {
            BlockPos nb = target.offset(f);
            Block nbBlock = mc.theWorld.getBlockState(nb).getBlock();
            if (nbBlock == Blocks.air || nbBlock instanceof BlockLiquid) continue;

            EnumFacing clickFace = f.getOpposite();

            double hx = nb.getX() + 0.5 + clickFace.getFrontOffsetX() * 0.5;
            double hy = nb.getY() + 0.5 + clickFace.getFrontOffsetY() * 0.5;
            double hz = nb.getZ() + 0.5 + clickFace.getFrontOffsetZ() * 0.5;

            if (clickFace.getFrontOffsetX() != 0) {
                hy += (rng.nextDouble() - 0.5) * 0.4;
                hz += (rng.nextDouble() - 0.5) * 0.4;
            } else if (clickFace.getFrontOffsetY() != 0) {
                hx += (rng.nextDouble() - 0.5) * 0.4;
                hz += (rng.nextDouble() - 0.5) * 0.4;
            } else {
                hx += (rng.nextDouble() - 0.5) * 0.4;
                hy += (rng.nextDouble() - 0.5) * 0.4;
            }

            Vec3 hit = new Vec3(hx, hy, hz);
            double dist = eyes.distanceTo(hit);
            if (dist <= placeRange.getInput() && dist < bestDist) {
                bestDist = dist;
                best = new PlaceInfo(nb, clickFace, hit);
            }
        }
        return best;
    }

    private boolean doPlace(PlaceInfo pi) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return false;
        boolean ok = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, pi.neighbor, pi.face, pi.hitVec);
        if (ok) mc.thePlayer.swingItem();
        return ok;
    }

    private boolean ensureBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && held.getItem() instanceof ItemBlock) return true;
        if (!autoSwitch.isToggled()) return false;
        int cur = mc.thePlayer.inventory.currentItem;
        int best = -1, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemBlock) {
                int d = Math.abs(i - cur);
                if (d < bestD) { bestD = d; best = i; }
            }
        }
        if (best != -1) { mc.thePlayer.inventory.currentItem = best; return true; }
        return false;
    }

    private void tickHypixel() {
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null) return;
        if (safeWalk.isToggled()) tickSafeWalk();
        if (keepY.isToggled() && mc.thePlayer.onGround) keepYLevel = mc.thePlayer.posY;

        if (hypDelay > 0) { hypDelay--; hasTarget = false; return; }

        if (!hypWaitPlace) {

            BlockPos target = getHypTarget();
            if (target == null) { hasTarget = false; driftToCamera(); return; }
            if (!ensureBlock()) { hasTarget = false; return; }

            PlaceInfo pi = findPlaceInfo(target);
            if (pi == null) { hasTarget = false; return; }
            hasTarget = true;

            computeTargetRotation(pi.hitVec);
            tickRotation();

            if (!rotationReady()) return;

            int blockSlot = closestBlockSlot();
            if (blockSlot == -1) return;

            hypPending   = pi;
            hypHitVec    = pi.hitVec;
            hypOrigSlot  = mc.thePlayer.inventory.currentItem;
            hypBlockSlot = blockSlot;
            hypWaitPlace = true;

        } else {

            hypWaitPlace = false;

            if (getHypTarget() == null || hypPending == null) return;

            ItemStack item = mc.thePlayer.inventory.getStackInSlot(hypBlockSlot);
            if (item == null || !(item.getItem() instanceof ItemBlock)) return;

            mc.thePlayer.inventory.currentItem = hypBlockSlot;
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock)) {
                mc.thePlayer.inventory.currentItem = hypOrigSlot;
                return;
            }

            boolean placed = mc.playerController.onPlayerRightClick(
                    mc.thePlayer, mc.theWorld, held,
                    hypPending.neighbor, hypPending.face, hypHitVec);
            if (placed) mc.thePlayer.swingItem();

            mc.thePlayer.inventory.currentItem = hypOrigSlot;
            hypPending = null;
            hypHitVec  = null;

            if (placed) {
                hypDelay = ThreadLocalRandom.current().nextBoolean() ? 1 : 0;
            }
        }
    }

    private BlockPos getHypTarget() {
        double px = mc.thePlayer.posX;
        double py = (keepY.isToggled() ? keepYLevel : mc.thePlayer.posY) - 0.1;
        double pz = mc.thePlayer.posZ;

        BlockPos c = fp(px, py, pz);
        Block bc = mc.theWorld.getBlockState(c).getBlock();
        if (bc == Blocks.air || bc instanceof BlockLiquid) return c;

        double[] off = { -0.3, 0.3 };
        for (double ox : off) {
            for (double oz : off) {
                BlockPos d = fp(px + ox, py, pz + oz);
                if (d.equals(c)) continue;
                Block db = mc.theWorld.getBlockState(d).getBlock();
                if (db == Blocks.air || db instanceof BlockLiquid) return d;
            }
        }
        return null;
    }

    private int closestBlockSlot() {
        int cur = mc.thePlayer.inventory.currentItem;
        int best = -1, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemBlock) {
                int d = Math.abs(i - cur);
                if (d < bestD) { bestD = d; best = i; }
            }
        }
        return best;
    }

    private Vec3 movementDir() {
        float yaw = currentYaw();
        if (silentForwardWalk.isToggled()) {
            float r = (float) Math.toRadians(yaw);
            return new Vec3(-Math.sin(r), 0, Math.cos(r));
        }
        float fwd = mc.thePlayer.moveForward;
        float str = mc.thePlayer.moveStrafing;
        float yawBase = (fwd == 0 && str == 0)
                ? yaw
                : strafeYaw(fwd, str, yaw);
        float r = (float) Math.toRadians(yawBase);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    private static float strafeYaw(float forward, float strafe, float yaw) {
        if (forward == 0 && strafe == 0) return yaw;
        boolean reversed = forward < 0.0f;
        float strafingYaw = 90.0f * (forward > 0.0f ? 0.5f : reversed ? -0.5f : 1.0f);
        if (reversed) yaw += 180.0f;
        if (strafe > 0.0f) yaw -= strafingYaw;
        else if (strafe < 0.0f) yaw += strafingYaw;
        return yaw;
    }

    private void tickSafeWalk() {
        if (!mc.thePlayer.onGround) return;
        float yawRef = currentYaw();
        double yr = Math.toRadians(yawRef);
        double fx = -Math.sin(yr) * 0.6;
        double fz =  Math.cos(yr) * 0.6;
        BlockPos ahead = new BlockPos(
                mc.thePlayer.posX + fx,
                mc.thePlayer.posY - 0.01,
                mc.thePlayer.posZ + fz);
        Block b = mc.theWorld.getBlockState(ahead).getBlock();
        if (b == Blocks.air || b instanceof BlockLiquid) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }
    }

    private void releaseSneak() {
        if (sneakActive) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            sneakActive = false;
        }
    }

    private BlockPos fp(double x, double y, double z) {
        return new BlockPos(
                MathHelper.floor_double(x),
                MathHelper.floor_double(y),
                MathHelper.floor_double(z));
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        SilentAim.applyToUpdate(e);
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent e) {
        if (!Utils.Player.isPlayerInGame() || !hasTarget) return;
        if (!silentForwardWalk.isToggled()) return;
        float f = e.getForward();
        float s = e.getStrafe();
        if (f == 0 && s == 0) return;
        float mag = MathHelper.sqrt_float(f * f + s * s);
        if (mag > 1.0F) mag = 1.0F;
        e.setForward(mag);
        e.setStrafe(0.0F);
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth()  / 2;
        int cy = sr.getScaledHeight() / 2;

        boolean hasFont = FontUtil.hasLoaded() && FontUtil.semiBold != null;
        String countStr = String.valueOf(blockCount);

        float textW = hasFont ? (float) FontUtil.semiBold.getStringWidth(countStr)
                              : mc.fontRendererObj.getStringWidth(countStr);
        float textH = hasFont ? FontUtil.semiBold.getHeight() : mc.fontRendererObj.FONT_HEIGHT;

        float iconSize = 16.0F;
        float gap = 3.0F;
        float padX = 6.0F;
        float padY = 4.0F;

        float totalW = padX + iconSize + gap + textW + padX;
        float totalH = padY + Math.max(iconSize, textH) + padY;
        float cornerR = totalH / 2.0F;

        float boxX = cx - totalW / 2.0F;
        float boxY = cy + 10.0F;

        if (HUD.enableBlur != null && HUD.enableBlur.isToggled()) {
            GUIBlurUtil.drawBlurredBackground(
                    (int) boxX, (int) boxY, (int) totalW, (int) totalH,
                    (int) HUD.blurRadius.getInput(), (int) cornerR, 0.85F);
            mc.entityRenderer.setupOverlayRendering();
        }

        RenderUtils.drawRoundedRectAA(boxX, boxY, boxX + totalW, boxY + totalH, cornerR, 0xCC101318);

        float iconX = boxX + padX;
        float iconY = boxY + padY + (Math.max(iconSize, textH) - iconSize) / 2.0F;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && held.getItem() instanceof ItemBlock) {
            GlStateManager.pushMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();
            mc.getRenderItem().renderItemIntoGUI(held, (int) iconX, (int) iconY);
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();

            GlStateManager.disableLighting();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int col = blockCount <= 16 ? 0xFFFF5555 : (blockCount <= 64 ? 0xFFFFAA00 : 0xFFFFFFFF);
        float textX = iconX + iconSize + gap;
        float textY = boxY + padY + (Math.max(iconSize, textH) - textH) / 2.0F;
        if (hasFont) {
            FontUtil.semiBold.drawSmoothString(countStr, textX, textY, col);
        } else {
            mc.fontRendererObj.drawStringWithShadow(countStr, textX, textY, col);
        }

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void updateBlockCount() {
        if (!Utils.Player.isPlayerInGame()) return;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemBlock) n += s.stackSize;
        }
        blockCount = n;
    }

    @Override
    public String getHudSuffix() {
        if (bypass.getMode() == BypassProfile.Hypixel) return "Hyp | " + blockCount;
        return String.valueOf(blockCount);
    }

    private static final class PlaceInfo {
        final BlockPos   neighbor;
        final EnumFacing face;
        final Vec3       hitVec;
        PlaceInfo(BlockPos n, EnumFacing f, Vec3 h) { neighbor = n; face = f; hitVec = h; }
    }
}
