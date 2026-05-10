package crow.client.module.modules.combat;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.input.Mouse;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.Module;
import crow.client.module.modules.client.Targets;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class AimAssist extends Module {

    public static SliderSetting horizontalSpeed;
    public static SliderSetting verticalSpeed;
    public static SliderSetting fov;
    public static SliderSetting distance;
    public static SliderSetting pitchOffset;
    public static SliderSetting randomization;
    public static TickSetting clickAim;
    public static TickSetting aimPitch;
    public static TickSetting weaponOnly;
    public static TickSetting breakBlocks;
    public static TickSetting visibilityCheck;
    public static TickSetting showFovCircle;
    public static ComboSetting targetMode;
    public static ArrayList<Entity> friends = new ArrayList<>();

    private EntityLivingBase currentTarget;
    private long targetAcquiredTime;

    private float aimDriftYaw;
    private float aimDriftPitch;
    private int aimDriftTicks;

    private float lerpYaw;
    private float lerpPitch;
    private boolean lerpInitialised;

    private int trackingTicks;

    private float lastFrameYawVel;

    private long lastVisibleAtMs;

    private int overshootRecoveryTicks;

    private long lastAttackMs;

    private float postAttackYawOffset;
    private float postAttackPitchOffset;

    public enum TargetMode {
        Head, Neck, Center, Feet
    }

    public AimAssist() {
        super("AimAssist", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Set targets in Client->Targets"));

        this.registerSetting(horizontalSpeed = new SliderSetting("Horizontal Speed", 0.5D, 0.01D, 1.0D, 0.01D));
        this.registerSetting(verticalSpeed   = new SliderSetting("Vertical Speed",   0.5D, 0.01D, 1.0D, 0.01D));
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 15.0D, 360.0D, 1.0D));
        this.registerSetting(distance = new SliderSetting("Distance", 4.5D, 1.0D, 8.0D, 0.1D));
        this.registerSetting(pitchOffset = new SliderSetting("Pitch offset", 0.0D, -2.0D, 2.0D, 0.05D));
        this.registerSetting(randomization = new SliderSetting("Randomization", 2.0D, 0.0D, 10.0D, 0.5D));
        this.registerSetting(clickAim = new TickSetting("Click aim", true));
        this.registerSetting(breakBlocks = new TickSetting("Break blocks", true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon only", false));
        this.registerSetting(aimPitch = new TickSetting("Aim pitch", false));
        this.registerSetting(visibilityCheck = new TickSetting("Visibility check", true));
        this.registerSetting(showFovCircle = new TickSetting("Show FOV Circle", true));
        this.registerSetting(targetMode = new ComboSetting("Target Area", TargetMode.Head));
    }

    @Override
    public String getHudSuffix() {
        return ((TargetMode) targetMode.getMode()).name();
    }

    @Override
    public void onEnable() {
        aimDriftYaw = aimDriftPitch = 0;
        aimDriftTicks = 0;
        lerpInitialised = false;
        trackingTicks = 0;
        lastFrameYawVel = 0;
        lastVisibleAtMs = 0L;
        overshootRecoveryTicks = 0;
    }

    @Override
    public void onDisable() {
        aimDriftYaw = aimDriftPitch = 0;
        aimDriftTicks = 0;
        lerpInitialised = false;
        trackingTicks = 0;
        lastFrameYawVel = 0;
        lastVisibleAtMs = 0L;
        overshootRecoveryTicks = 0;
        currentTarget = null;
    }

    @Subscribe
    public void onAttackEntityForge(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof AttackEntityEvent)) return;
        lastAttackMs = System.currentTimeMillis();

        ThreadLocalRandom r = ThreadLocalRandom.current();
        float yawDir = r.nextBoolean() ? 1.0F : -1.0F;
        float pitchDir = r.nextBoolean() ? 1.0F : -1.0F;
        postAttackYawOffset   = yawDir   * (0.4F + r.nextFloat() * 1.2F);
        postAttackPitchOffset = pitchDir * (0.2F + r.nextFloat() * 0.5F);
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null) return;
        if (showFovCircle != null && showFovCircle.isToggled()) {
            ScaledResolution sr = new ScaledResolution(mc);
            float fovRad = (float) Math.toRadians(fov.getInput() / 2.0);
            float camFovRad = (float) Math.toRadians(mc.gameSettings.fovSetting / 2.0);
            float radius = (float) ((sr.getScaledWidth() / 2.0f) * Math.tan(fovRad) / Math.tan(camFovRad));
            RenderUtils.drawCircleOutline(sr.getScaledWidth() / 2.0f, sr.getScaledHeight() / 2.0f, radius, 1.5f, 0x88FFFFFF);
        }
    }

    @Subscribe
    public void onRenderWorldLast(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof RenderWorldLastEvent)) return;
        if (currentTarget != null && currentTarget.isEntityAlive()
                && mc.thePlayer != null
                && mc.thePlayer.getDistanceToEntity(currentTarget) <= distance.getInput()) {
            int color = GuiModule.getThemeColor(0);
            Utils.HUD.drawRingAroundEntity(currentTarget, color, 0.6D, 0.05D, 2.0F);
        }
    }

    @Subscribe
    public void onRender(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof TickEvent.RenderTickEvent)) return;
        TickEvent.RenderTickEvent rte = (TickEvent.RenderTickEvent) fe.getEvent();
        if (rte.phase != TickEvent.Phase.END) return;

        if (!Utils.Client.currentScreenMinecraft() || !Utils.Player.isPlayerInGame()) {

            lerpInitialised = false;
            return;
        }

        if (breakBlocks.isToggled() && mc.objectMouseOver != null) {
            BlockPos p = mc.objectMouseOver.getBlockPos();
            if (p != null) {
                Block bl = mc.theWorld.getBlockState(p).getBlock();
                if (bl != Blocks.air && !(bl instanceof BlockLiquid)) {
                    return;
                }
            }
        }

        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) {
            return;
        }

        if (clickAim.isToggled() && !Mouse.isButtonDown(0) && !Utils.Client.autoClickerClicking()) {
            return;
        }

        boolean isClicking = Mouse.isButtonDown(0) || Utils.Client.autoClickerClicking();

        EntityLivingBase target = getTarget(isClicking);
        if (target == null) {
            currentTarget = null;
            aimDriftYaw = aimDriftPitch = 0;
            aimDriftTicks = 0;
            trackingTicks = 0;
            lerpInitialised = false;
            return;
        }

        if (!lerpInitialised) {
            lerpYaw = mc.thePlayer.rotationYaw;
            lerpPitch = mc.thePlayer.rotationPitch;
            lerpInitialised = true;
        }

        if (target != currentTarget) {
            trackingTicks = 0;
        }
        currentTarget = target;
        targetAcquiredTime = System.currentTimeMillis();
        trackingTicks++;

        if (visibilityCheck.isToggled()) {
            long nowMs = System.currentTimeMillis();
            if (mc.thePlayer.canEntityBeSeen(target)) {
                lastVisibleAtMs = nowMs;
            } else if (nowMs - lastVisibleAtMs > 350L) {
                return;
            }
        }

        if (mc.thePlayer.getDistanceToEntity(target) > distance.getInput()) {
            return;
        }

        float mouseDeltaYaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - lerpYaw);
        float mouseDeltaPitch = mc.thePlayer.rotationPitch - lerpPitch;
        lerpYaw += mouseDeltaYaw;
        lerpPitch += mouseDeltaPitch;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        tickAimDrift(r);

        double targetY = target.posY;
        switch ((TargetMode) targetMode.getMode()) {
            case Head:
                targetY += target.getEyeHeight() * (0.976D + r.nextDouble() * 0.028D);
                break;
            case Neck:
                targetY += target.getEyeHeight() * (0.83D + r.nextDouble() * 0.04D);
                break;
            case Center:
                targetY += target.height * (0.48D + r.nextDouble() * 0.04D);
                break;
            case Feet:
                targetY += target.height * (0.13D + r.nextDouble() * 0.04D);
                break;
        }
        targetY += pitchOffset.getInput() + aimDriftPitch * 0.07D;

        double diffX = target.posX - mc.thePlayer.posX;
        double diffY = targetY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double diffZ = target.posZ - mc.thePlayer.posZ;
        double dist = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);

        float desiredYaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F + aimDriftYaw * 0.35F;
        float desiredPitch = (float) -(Math.atan2(diffY, dist) * 180.0 / Math.PI);

        float yawDelta = MathHelper.wrapAngleTo180_float(desiredYaw - lerpYaw);
        float pitchDelta = desiredPitch - lerpPitch;
        float absYaw = Math.abs(yawDelta);
        float absPitch = Math.abs(pitchDelta);
        float distAng = (float) Math.sqrt(absYaw * absYaw + absPitch * absPitch);

        float yawSpeed   = (float) horizontalSpeed.getInput();
        float pitchSpeed = (float) verticalSpeed.getInput();

        float gainHiYaw   = 0.020F + yawSpeed   * 0.160F;
        float gainLoYaw   = 0.008F + yawSpeed   * 0.052F;
        float gainHiPitch = 0.020F + pitchSpeed * 0.160F;
        float gainLoPitch = 0.008F + pitchSpeed * 0.052F;

        float pivot = 11.0F;
        float yawBlend   = absYaw   / (absYaw   + pivot);
        float pitchBlend = absPitch / (absPitch + pivot);
        float yawGain   = gainLoYaw   + (gainHiYaw   - gainLoYaw)   * yawBlend;
        float pitchGain = gainLoPitch + (gainHiPitch - gainLoPitch) * pitchBlend;

        float rampMultiplier = 1.0F;
        if (trackingTicks == 1) {
            rampMultiplier = 0.30F + r.nextFloat() * 0.12F;
        } else if (trackingTicks == 2) {
            rampMultiplier = 0.55F + r.nextFloat() * 0.12F;
        } else if (trackingTicks == 3) {
            rampMultiplier = 0.78F + r.nextFloat() * 0.10F;
        } else if (trackingTicks == 4) {
            rampMultiplier = 0.92F + r.nextFloat() * 0.06F;
        }
        yawGain   *= rampMultiplier;
        pitchGain *= rampMultiplier;

        // Settle factor: 0 when far from target (>5°), 1 when locked on (<1°).
        // All high-frequency noise is gated by this so a locked-on target
        // doesn't shake — noise only happens during the approach.
        float settle = MathHelper.clamp_float((distAng - 1.0F) / 4.0F, 0.0F, 1.0F);

        if (overshootRecoveryTicks > 0) {
            overshootRecoveryTicks--;
            yawGain *= 0.55F;
            pitchGain *= 0.55F;
        }

        float yawStep = yawDelta * yawGain;
        float pitchStep = pitchDelta * pitchGain;

        float randAmount = (float) randomization.getInput();
        if (randAmount > 0 && settle > 0F) {
            float yawNoise = (r.nextFloat() - 0.5F) * randAmount * 0.04F * settle;
            float pitchNoise = (r.nextFloat() - 0.5F) * randAmount * 0.03F * settle;
            yawStep += yawNoise;
            pitchStep += pitchNoise;
        }

        // Tiny step-magnitude jitter, only while still approaching.
        if (settle > 0F) {
            float wobble = 1.0F - 0.04F * settle + 0.08F * settle * r.nextFloat();
            yawStep *= wobble;
            pitchStep *= wobble;
        }

        long nowMs = System.currentTimeMillis();
        long sinceAttack = nowMs - lastAttackMs;
        if (lastAttackMs > 0L && sinceAttack >= 0L && sinceAttack < 280L) {
            float decay = 1.0F - (sinceAttack / 280.0F);
            float driftEase = decay * decay;
            yawStep   += postAttackYawOffset   * driftEase * 0.06F;
            pitchStep += postAttackPitchOffset * driftEase * 0.06F;
        }

        lerpYaw += yawStep;
        lerpPitch = MathHelper.clamp_float(lerpPitch + pitchStep, -90.0F, 90.0F);

        float prevYaw = mc.thePlayer.rotationYaw;
        float prevPitch = mc.thePlayer.rotationPitch;

        float rawDeltaYaw = MathHelper.wrapAngleTo180_float(lerpYaw - prevYaw);
        float rawDeltaPitch = lerpPitch - prevPitch;

        float patchedYaw = Utils.Player.patchGCD(rawDeltaYaw);
        float patchedPitch = Utils.Player.patchGCD(rawDeltaPitch);

        mc.thePlayer.rotationYaw += patchedYaw;
        lastFrameYawVel = patchedYaw;

        lerpYaw = mc.thePlayer.rotationYaw;

        mc.thePlayer.prevRotationYawHead = mc.thePlayer.rotationYawHead;
        mc.thePlayer.rotationYawHead = mc.thePlayer.rotationYaw;
        mc.thePlayer.prevRenderYawOffset = mc.thePlayer.renderYawOffset;
        float yawSpeedAbs = Math.abs(lastFrameYawVel);

        float bodyBlend = 0.42F - Math.min(0.24F, yawSpeedAbs * 0.04F);
        mc.thePlayer.renderYawOffset += MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - mc.thePlayer.renderYawOffset) * bodyBlend;

        if (aimPitch.isToggled()) {
            mc.thePlayer.rotationPitch += patchedPitch;
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch, -90.0F, 90.0F);
            lerpPitch = mc.thePlayer.rotationPitch;
        } else {

            lerpPitch = mc.thePlayer.rotationPitch;
        }
    }

    private EntityLivingBase getTarget(boolean clicking) {

        if (clicking && currentTarget != null && currentTarget.isEntityAlive()
                && mc.thePlayer.getDistanceToEntity(currentTarget) <= distance.getInput()) {
            return currentTarget;
        }

        EntityLivingBase best = Targets.getTargetEntityNoFov(distance.getInput());
        if (best != null && mc.thePlayer.getDistanceToEntity(best) <= distance.getInput()) {

            if (!clicking) {
                double fovToTarget = Math.abs(getFovToEntity(best));
                if (fovToTarget > fov.getInput() / 2.0D) {
                    return null;
                }
            }
            return best;
        }
        return null;
    }

    private double getFovToEntity(Entity entity) {
        double diffX = entity.posX - mc.thePlayer.posX;
        double diffZ = entity.posZ - mc.thePlayer.posZ;
        double yaw = Math.atan2(diffZ, diffX) * 180.0 / Math.PI - 90.0;
        return MathHelper.wrapAngleTo180_float((float)(mc.thePlayer.rotationYaw - yaw));
    }

    private void tickAimDrift(ThreadLocalRandom r) {
        aimDriftTicks++;
        if (aimDriftTicks < 4 + r.nextInt(6)) {
            return;
        }
        aimDriftTicks = 0;
        aimDriftYaw += (r.nextFloat() - 0.5F) * 0.15F;
        aimDriftPitch += (r.nextFloat() - 0.5F) * 0.10F;
        aimDriftYaw = MathHelper.clamp_float(aimDriftYaw, -0.4F, 0.4F);
        aimDriftPitch = MathHelper.clamp_float(aimDriftPitch, -0.3F, 0.3F);
    }

    public static void addFriend(Entity entityPlayer) { friends.add(entityPlayer); }

    public static boolean addFriend(String name) {
        if (mc.theWorld == null || name == null || name.isEmpty())
            return false;
        boolean found = false;
        for (Entity entity : mc.theWorld.getLoadedEntityList()) {
            if (entityNameMatches(entity, name) && !Targets.isAFriend(entity)) {
                addFriend(entity);
                found = true;
            }
        }
        return found;
    }

    public static boolean removeFriend(String name) {
        if (mc.theWorld == null || mc.getNetHandler() == null || name == null || name.isEmpty())
            return false;
        boolean removed = false, found = false;
        for (NetworkPlayerInfo npi : new ArrayList<>(mc.getNetHandler().getPlayerInfoMap())) {
            if (npi.getDisplayName() == null)
                continue;
            Entity entity = mc.theWorld.getPlayerEntityByName(npi.getDisplayName().getUnformattedText());
            if (entity != null && entityNameMatches(entity, name)) {
                removed = removeFriend(entity);
                found = true;
            }
        }
        return found && removed;
    }

    private static boolean entityNameMatches(Entity entity, String name) {
        if (entity == null || name == null)
            return false;
        if (entity.getName().equalsIgnoreCase(name))
            return true;
        String tag = entity.getCustomNameTag();
        return tag != null && tag.equalsIgnoreCase(name);
    }

    public static boolean removeFriend(Entity entityPlayer) {
        try { friends.remove(entityPlayer); } catch (Exception e) { return false; }
        return true;
    }

    public static ArrayList<Entity> getFriends() { return friends; }
}
