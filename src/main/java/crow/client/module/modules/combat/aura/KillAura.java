package crow.client.module.modules.combat.aura;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Mouse;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.GameLoopEvent;
import crow.client.event.impl.LookEvent;
import crow.client.event.impl.MoveInputEvent;
import crow.client.event.impl.PacketEvent;
import crow.client.event.impl.UpdateEvent;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.Targets;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class KillAura extends Module {

    private EntityLivingBase target;

    public static SliderSetting reach, rotationSpeed;
    private DoubleSliderSetting cps;
    private TickSetting disableOnTp, disableWhenFlying, mouseDown, onlySurvival, fixMovement;
    private TickSetting randomizeRotation, smoothRotation, autoBlock;
    private SliderSetting attackTickDelay;
    private ComboSetting blockMode;

    private float serverYaw, serverPitch;
    private float prevServerYaw, prevServerPitch;
    private float targetYaw, targetPitch;
    private boolean locked;

    private final CoolDown tpCooldown = new CoolDown(1);
    private boolean leftDown;
    private long leftDownTime, leftUpTime, leftNextBurst;
    private double burstMultiplier;
    private boolean inBurst;
    private int ticksSinceAttack;

    private float aimDriftYaw, aimDriftPitch;
    private int aimDriftTicks;

    private float yawVelocity, pitchVelocity;
    private int aimTick;

    private enum AimMode { GLIDE, REST, BURST }
    private AimMode aimMode = AimMode.GLIDE;
    private int aimModeTicksRemaining;
    private float yawWobblePhase;

    private int lastTargetId = -1;
    private long lastTargetSeenMs;
    private long targetAcquiredMs;
    private long reactionDelayMs;

    private float lastYawApplied;
    private static final long TARGET_PERSIST_MS = 600L;

    public KillAura() {
        super("KillAura", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Set targets in Client->Targets"));
        this.registerSetting(reach = new SliderSetting("Reach", 4.0, 3.0, 6.0, 0.05));
        this.registerSetting(rotationSpeed = new SliderSetting("Rotation speed", 80, 10, 300, 1));
        this.registerSetting(cps = new DoubleSliderSetting("CPS", 10, 14, 1, 20, 0.5));
        this.registerSetting(attackTickDelay = new SliderSetting("Min ticks between hits", 1, 0, 5, 1));
        this.registerSetting(onlySurvival = new TickSetting("Only survival", true));
        this.registerSetting(disableOnTp = new TickSetting("Disable after TP", true));
        this.registerSetting(disableWhenFlying = new TickSetting("Disable when flying", true));
        this.registerSetting(mouseDown = new TickSetting("Mouse down", false));
        this.registerSetting(fixMovement = new TickSetting("Movement fix", true));
        this.registerSetting(smoothRotation = new TickSetting("Smooth rotation", true));
        this.registerSetting(randomizeRotation = new TickSetting("Randomize rotation", true));
        this.registerSetting(autoBlock = new TickSetting("Auto block", false));
        this.registerSetting(blockMode = new ComboSetting("Block mode", BlockMode.NONE));
    }

    @Override
    public void onEnable() {
        if (Utils.Player.isPlayerInGame()) {
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
            prevServerYaw = serverYaw;
            prevServerPitch = serverPitch;
            targetYaw = serverYaw;
            targetPitch = serverPitch;
        }
        locked = true;
        leftDownTime = 0;
        leftUpTime = 0;
        ticksSinceAttack = 0;
        leftDown = false;
        aimDriftYaw = aimDriftPitch = 0;
        aimDriftTicks = 0;
        yawVelocity = pitchVelocity = 0;
        aimTick = 0;
        aimMode = AimMode.GLIDE;
        aimModeTicksRemaining = 0;
        yawWobblePhase = ThreadLocalRandom.current().nextFloat() * (float) Math.PI * 2.0F;
        lastTargetId = -1;
        lastTargetSeenMs = 0L;
        targetAcquiredMs = 0L;
        reactionDelayMs = 0L;
        lastYawApplied = 0F;
    }

    @Override
    public void onDisable() {
        target = null;
        locked = true;
        leftDown = false;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
    }

    @Subscribe
    public void gameLoopEvent(GameLoopEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null || mc.thePlayer == null || mc.playerController == null) {
            target = null;
            locked = true;
            return;
        }

        if (onlySurvival.isToggled() && mc.playerController.getCurrentGameType() != GameType.SURVIVAL) {
            target = null; locked = true; return;
        }
        if (!tpCooldown.hasFinished()) { target = null; locked = true; return; }
        if (mouseDown.isToggled() && !Mouse.isButtonDown(0)) { target = null; locked = true; return; }
        if (disableWhenFlying.isToggled() && mc.thePlayer.capabilities.isFlying) { target = null; locked = true; return; }

        double targetRange = Math.max(reach.getInput(), Targets.getDistanceSetting());
        EntityLivingBase candidate = Targets.getTargetEntityNoFov(targetRange);
        EntityLivingBase entityTarget = candidate;
        if (target != null && target.isEntityAlive()
                && mc.thePlayer.getDistanceToEntity(target) <= reach.getInput()
                && candidate != null && candidate != target) {
            double curDist = mc.thePlayer.getDistanceToEntity(target);
            double newDist = mc.thePlayer.getDistanceToEntity(candidate);
            if (newDist >= curDist * 0.85) {
                entityTarget = target;
            }
        }
        if (entityTarget == null || !entityTarget.isEntityAlive() || mc.thePlayer.getDistanceToEntity(entityTarget) > reach.getInput()) {
            target = null;
            locked = true;

            if (lastTargetId != -1
                    && System.currentTimeMillis() - lastTargetSeenMs > TARGET_PERSIST_MS) {
                lastTargetId = -1;
            }
            aimDriftYaw = aimDriftPitch = 0;
            aimDriftTicks = 0;
            yawVelocity = pitchVelocity = 0;

            targetYaw = mc.thePlayer.rotationYaw;
            targetPitch = mc.thePlayer.rotationPitch;

            if (!isUsingConsumable()) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            }
            return;
        }

        target = entityTarget;
        locked = false;
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;

        ThreadLocalRandom r = ThreadLocalRandom.current();

        int currentId = target.getEntityId();
        if (currentId != lastTargetId) {
            targetAcquiredMs = System.currentTimeMillis();
            reactionDelayMs = 150L + r.nextInt(140);
            lastTargetId = currentId;
        }
        lastTargetSeenMs = System.currentTimeMillis();

        double heightFactor = 0.975D + r.nextDouble() * 0.018D;
        tickAimDrift(r);

        float[] rot = Utils.Player.getTargetRotations(target, 0.03F + aimDriftPitch * 0.02F,
                serverYaw, serverPitch, heightFactor);
        targetYaw = rot[0] + aimDriftYaw;
        targetPitch = rot[1];

        if (randomizeRotation.isToggled()) {

            targetYaw += (float) (r.nextGaussian() * 0.42);
            targetPitch += (float) (r.nextGaussian() * 0.22);
            targetPitch = MathHelper.clamp_float(targetPitch, -90.0F, 90.0F);
        }

        if (smoothRotation.isToggled()) {
            applySmoothRotation(r);
        } else {
            serverYaw = targetYaw;
            serverPitch = targetPitch;
            yawVelocity = pitchVelocity = 0;
        }

        serverPitch = MathHelper.clamp_float(serverPitch, -90.0F, 90.0F);
        syncVisualRotations();

        ticksSinceAttack++;
        boolean reacted = System.currentTimeMillis() - targetAcquiredMs >= reactionDelayMs;
        boolean stableAim = Math.abs(lastYawApplied) < 6.0F;
        if (reacted && stableAim
                && ticksSinceAttack >= (int) attackTickDelay.getInput()
                && isAimedAtTarget()) {
            crowClick();
        }

        BlockMode bm = (BlockMode) blockMode.getMode();
        boolean holdingSword = Utils.Player.isPlayerHoldingSword();
        if (bm == BlockMode.VANILLA && holdingSword
                && mc.thePlayer.prevSwingProgress < mc.thePlayer.swingProgress) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
        } else if (bm == BlockMode.BLOCK_HIT && holdingSword) {

            long now = System.currentTimeMillis();
            boolean releaseWindow = leftUpTime > 0
                    && now >= leftUpTime - 90L
                    && now <= leftUpTime + 60L;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), !releaseWindow);
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        if (locked) {
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
            prevServerYaw = serverYaw;
            prevServerPitch = serverPitch;
            return;
        }
        e.setYaw(serverYaw);
        e.setPitch(serverPitch);
    }

    @Subscribe
    public void lookEvent(LookEvent e) {
        if (locked || target == null) return;

        if (mc.gameSettings.thirdPersonView != 0) {
            e.setYaw(serverYaw);
            e.setPitch(serverPitch);
            e.setPrevYaw(prevServerYaw);
            e.setPrevPitch(prevServerPitch);
            syncVisualRotations();
        }

    }

    @Subscribe
    public void move(MoveInputEvent e) {
        if (!fixMovement.isToggled() || locked) return;
        e.setYaw(serverYaw);
    }

    @Subscribe
    public void packetEvent(PacketEvent e) {
        if (e.getPacket() instanceof S08PacketPlayerPosLook && disableOnTp.isToggled()) {
            tpCooldown.setCooldown(2000);
            tpCooldown.start();
        }
    }

    @Subscribe
    public void renderWorldLast(ForgeEvent fe) {
        if (fe.getEvent() instanceof RenderWorldLastEvent && target != null) {
            int color = GuiModule.getThemeColor(0);
            Utils.HUD.drawRingAroundEntity(target, color, 0.6D, 0.05D, 2.5F);
        }
    }

    private void crowClick() {
        this.leftClickExecute();
    }

    public void leftClickExecute() {
        if (target == null || mc.thePlayer == null || mc.playerController == null || !target.isEntityAlive()) {
            leftDown = false;
            return;
        }

        if (isUsingConsumable()) {
            leftDown = false;
            return;
        }

        if (leftUpTime > 0L && leftDownTime > 0L) {
            if (System.currentTimeMillis() > leftUpTime && leftDown) {
                if (mc.thePlayer.isUsingItem()) mc.thePlayer.stopUsingItem();
                mc.thePlayer.swingItem();

                if (ThreadLocalRandom.current().nextInt(100) >= 3
                        && mc.thePlayer.getDistanceToEntity(target) <= reach.getInput()) {
                    mc.playerController.attackEntity(mc.thePlayer, target);
                }
                ticksSinceAttack = 0;
                genLeftTimings();
                leftDown = false;
            } else if (System.currentTimeMillis() > leftDownTime) {
                if (Mouse.isButtonDown(1))
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                leftDown = true;
            }
        } else {
            genLeftTimings();
        }
    }

    public void genLeftTimings() {
        double clickSpeed = Utils.Client.sampleClickCps(cps, Utils.Java.rand());
        long delay = Math.max(1L, Math.round(1000.0D / clickSpeed));

        if (System.currentTimeMillis() > leftNextBurst) {
            if (!inBurst && Utils.Java.rand().nextInt(100) >= 85) {
                inBurst = true;
                burstMultiplier = 1.1D + Utils.Java.rand().nextDouble() * 0.15D;
            } else {
                inBurst = false;
            }
            leftNextBurst = System.currentTimeMillis() + 500L + Utils.Java.rand().nextInt(1500);
        }
        if (inBurst) delay = (long) (delay / burstMultiplier);

        if (Utils.Java.rand().nextInt(100) >= 80) {
            delay += 30L + Utils.Java.rand().nextInt(80);
        }

        leftUpTime = System.currentTimeMillis() + delay;
        leftDownTime = System.currentTimeMillis() + (delay / 2L) - Utils.Java.rand().nextInt(10);
    }

    private boolean isUsingConsumable() {
        if (mc.thePlayer == null || !mc.thePlayer.isUsingItem()) return false;
        ItemStack inUse = mc.thePlayer.getItemInUse();
        if (inUse == null) return false;
        return inUse.getItem() instanceof ItemFood || inUse.getItem() instanceof ItemBow;
    }

    public double getReach() { return reach.getInput(); }

    private boolean isAimedAtTarget() {
        if (target == null) {
            return false;
        }
        float thrYaw = 12.5F + ThreadLocalRandom.current().nextFloat() * 2.5F;
        float thrPitch = 10.5F + ThreadLocalRandom.current().nextFloat() * 2.0F;
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - serverYaw));
        float pitchDiff = Math.abs(targetPitch - serverPitch);
        return yawDiff < thrYaw && pitchDiff < thrPitch;
    }

    private void applySmoothRotation(ThreadLocalRandom r) {
        aimTick++;
        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float pitchDelta = targetPitch - serverPitch;

        if (aimModeTicksRemaining <= 0) {
            int roll = r.nextInt(100);
            if (roll < 60) {
                aimMode = AimMode.GLIDE;
                aimModeTicksRemaining = 4 + r.nextInt(8);
            } else if (roll < 88) {
                aimMode = AimMode.REST;
                aimModeTicksRemaining = 1 + r.nextInt(3);
            } else {
                aimMode = AimMode.BURST;
                aimModeTicksRemaining = 1 + r.nextInt(2);
            }
        }
        aimModeTicksRemaining--;

        float yawFraction, pitchFraction, yawDecay, pitchDecay, tremorYawSd, tremorPitchSd;
        switch (aimMode) {
            case REST:
                yawFraction   = 0.04F + r.nextFloat() * 0.06F;
                pitchFraction = 0.03F + r.nextFloat() * 0.05F;
                yawDecay      = 0.78F + r.nextFloat() * 0.10F;
                pitchDecay    = 0.80F + r.nextFloat() * 0.10F;
                tremorYawSd   = 0.10F + r.nextFloat() * 0.10F;
                tremorPitchSd = 0.06F + r.nextFloat() * 0.06F;
                break;
            case BURST:
                yawFraction   = 0.50F + r.nextFloat() * 0.40F;
                pitchFraction = 0.40F + r.nextFloat() * 0.30F;
                yawDecay      = 0.20F + r.nextFloat() * 0.20F;
                pitchDecay    = 0.25F + r.nextFloat() * 0.20F;
                tremorYawSd   = 0.30F + r.nextFloat() * 0.40F;
                tremorPitchSd = 0.15F + r.nextFloat() * 0.20F;
                break;
            case GLIDE:
            default:
                yawFraction   = 0.18F + r.nextFloat() * 0.22F;
                pitchFraction = 0.16F + r.nextFloat() * 0.20F;
                yawDecay      = 0.50F + r.nextFloat() * 0.18F;
                pitchDecay    = 0.48F + r.nextFloat() * 0.20F;
                tremorYawSd   = 0.20F + r.nextFloat() * 0.30F;
                tremorPitchSd = 0.10F + r.nextFloat() * 0.15F;
                break;
        }

        float baseMax = Math.max(2.0F, (float) rotationSpeed.getInput() / 9.0F);
        float maxTurn = baseMax * (0.45F + 0.85F * r.nextFloat());

        float yawTargetVel   = yawDelta   * yawFraction;
        float pitchTargetVel = pitchDelta * pitchFraction;
        yawVelocity   = yawVelocity   * yawDecay   + yawTargetVel   * (1.0F - yawDecay);
        pitchVelocity = pitchVelocity * pitchDecay + pitchTargetVel * (1.0F - pitchDecay);

        yawWobblePhase += 0.05F + r.nextFloat() * 0.10F;
        if (yawWobblePhase > Math.PI * 4.0) yawWobblePhase -= (float) (Math.PI * 4.0);
        float wobble = (float) Math.sin(yawWobblePhase) * 0.28F;

        float yawApply   = yawVelocity   + (float) (r.nextGaussian() * tremorYawSd)   + wobble;
        float pitchApply = pitchVelocity + (float) (r.nextGaussian() * tremorPitchSd);

        int dice = r.nextInt(100);
        if (dice < 5) {
            yawApply   = wobble * 0.4F;
            pitchApply = (float) (r.nextGaussian() * 0.05);
            yawVelocity   *= 0.35F;
            pitchVelocity *= 0.35F;
        } else if (dice < 12 && Math.abs(yawDelta) > 3.0F) {
            float boost = 1.30F + r.nextFloat() * 0.40F;
            yawApply   *= boost;
            pitchApply *= 0.85F + r.nextFloat() * 0.30F;
            yawVelocity   *= -0.20F + r.nextFloat() * 0.40F;
            pitchVelocity *=  0.30F + r.nextFloat() * 0.30F;
        } else if (dice >= 95) {
            yawApply   += (float) (r.nextGaussian() * 0.85);
            pitchApply += (float) (r.nextGaussian() * 0.45);
        }

        yawApply   = MathHelper.clamp_float(yawApply,   -maxTurn,        maxTurn);
        pitchApply = MathHelper.clamp_float(pitchApply, -maxTurn * 0.55F, maxTurn * 0.55F);

        serverYaw   += yawApply;
        serverPitch += pitchApply;
        lastYawApplied = yawApply;
    }

    private void tickAimDrift(ThreadLocalRandom r) {
        aimDriftTicks++;
        if (aimDriftTicks < 2 + r.nextInt(4)) {
            return;
        }
        aimDriftTicks = 0;
        aimDriftYaw += (r.nextFloat() - 0.5F) * 0.35F;
        aimDriftPitch += (r.nextFloat() - 0.5F) * 0.22F;
        aimDriftYaw = MathHelper.clamp_float(aimDriftYaw, -1.1F, 1.1F);
        aimDriftPitch = MathHelper.clamp_float(aimDriftPitch, -0.75F, 0.75F);
    }

    private void syncVisualRotations() {
        if (mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.prevRotationYawHead = mc.thePlayer.rotationYawHead;
        mc.thePlayer.rotationYawHead = serverYaw;
        mc.thePlayer.prevRenderYawOffset = mc.thePlayer.renderYawOffset;
        mc.thePlayer.renderYawOffset += MathHelper.wrapAngleTo180_float(serverYaw - mc.thePlayer.renderYawOffset) * 0.45F;
    }

    @Override
    public String getHudSuffix() { return target != null ? target.getName() : ""; }

    public enum BlockMode { NONE, VANILLA, BLOCK_HIT }
}
