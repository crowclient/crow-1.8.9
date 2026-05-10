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
import crow.client.utils.SilentAim;
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

    private int lastTargetId = -1;
    private long lastTargetSeenMs;
    private long targetAcquiredMs;
    private long reactionDelayMs;

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
            targetYaw = mc.thePlayer.rotationYaw;
            targetPitch = mc.thePlayer.rotationPitch;
        }
        locked = true;
        leftDownTime = 0;
        leftUpTime = 0;
        ticksSinceAttack = 0;
        leftDown = false;
        aimDriftYaw = aimDriftPitch = 0;
        aimDriftTicks = 0;
        lastTargetId = -1;
        lastTargetSeenMs = 0L;
        targetAcquiredMs = 0L;
        reactionDelayMs = 0L;
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

            targetYaw = mc.thePlayer.rotationYaw;
            targetPitch = mc.thePlayer.rotationPitch;

            if (!isUsingConsumable()) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            }
            return;
        }

        target = entityTarget;
        locked = false;

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
                SilentAim.getServerYaw(), SilentAim.getServerPitch(), heightFactor);
        targetYaw = rot[0] + aimDriftYaw;
        targetPitch = rot[1];

        if (randomizeRotation.isToggled()) {

            targetYaw += (float) (r.nextGaussian() * 0.42);
            targetPitch += (float) (r.nextGaussian() * 0.22);
            targetPitch = MathHelper.clamp_float(targetPitch, -90.0F, 90.0F);
        }

        SilentAim.Request req = new SilentAim.Request();
        req.yaw = targetYaw;
        req.pitch = targetPitch;
        req.profile = SilentAim.Profile.COMBAT;
        req.priority = 100;
        if (!smoothRotation.isToggled()) {
            // Snap mode: large per-tick caps short-circuit the spring smoothing.
            req.maxYawStepDeg = 180f;
            req.maxPitchStepDeg = 180f;
        } else {
            req.maxYawStepDeg = (float) rotationSpeed.getInput();
            req.maxPitchStepDeg = (float) rotationSpeed.getInput() * 0.8f;
        }
        req.claimant = this;
        SilentAim.aim(req);

        ticksSinceAttack++;
        boolean reacted = System.currentTimeMillis() - targetAcquiredMs >= reactionDelayMs;
        float lastYawApplied = SilentAim.getServerYaw() - SilentAim.getPrevServerYaw();
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
        if (locked) return;
        SilentAim.applyToUpdate(e);
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
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - SilentAim.getServerYaw()));
        float pitchDiff = Math.abs(targetPitch - SilentAim.getServerPitch());
        return yawDiff < thrYaw && pitchDiff < thrPitch;
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

    @Override
    public String getHudSuffix() { return target != null ? target.getName() : ""; }

    public enum BlockMode { NONE, VANILLA, BLOCK_HIT }
}
