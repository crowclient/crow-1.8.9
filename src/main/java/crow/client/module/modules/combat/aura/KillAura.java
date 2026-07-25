package crow.client.module.modules.combat.aura;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
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
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class KillAura extends Module {

    private EntityLivingBase target;

    public static SliderSetting reach, rotationSpeed;
    private DoubleSliderSetting cps;
    private TickSetting disableOnTp, disableWhenFlying, mouseDown, onlySurvival;
    private TickSetting randomizeRotation, smoothRotation, autoBlock, targetRing;
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
    private boolean auraUseItemDown;
    private boolean auraUseItemQueued;
    private volatile boolean teleportResetPending;

    private float aimDriftYaw, aimDriftPitch;
    private int aimDriftTicks;

    private int lastTargetId = -1;
    private long lastTargetSeenMs;
    private long targetAcquiredMs;
    private long reactionDelayMs;

    private static final long TARGET_PERSIST_MS = 600L;

    public KillAura() {
        super("SilentAura", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Set targets in Client->Targets"));
        this.registerSetting(reach = new SliderSetting("Reach", 4.0, 3.0, 6.0, 0.05));
        this.registerSetting(rotationSpeed = new SliderSetting("Rot speed", 80, 10, 300, 1));
        this.registerSetting(cps = new DoubleSliderSetting("CPS", 10, 14, 1, 20, 0.5));
        this.registerSetting(attackTickDelay = new SliderSetting("Min ticks", 1, 0, 5, 1));
        this.registerSetting(onlySurvival = new TickSetting("Survival only", true));
        this.registerSetting(disableOnTp = new TickSetting("Off on TP", true));
        this.registerSetting(disableWhenFlying = new TickSetting("Off flying", true));
        this.registerSetting(mouseDown = new TickSetting("Hold LMB", false));
        // No "Move fix" toggle: movement always follows the reported yaw. Walking
        // on the camera yaw while the packet carries the aim yaw is a guaranteed
        // simulation mismatch, so there is no correct way to turn it off.
        this.registerSetting(smoothRotation = new TickSetting("Smooth rot", true));
        this.registerSetting(randomizeRotation = new TickSetting("Rand rot", true));
        this.registerSetting(autoBlock = new TickSetting("Auto block", false));
        this.registerSetting(blockMode = new ComboSetting("Block", BlockMode.NONE));
        this.registerSetting(targetRing = new TickSetting("Target ring", true));
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
        auraUseItemDown = false;
        auraUseItemQueued = false;
        teleportResetPending = false;
        SilentAim.release(this);
    }

    @Override
    public void onDisable() {
        teleportResetPending = false;
        target = null;
        locked = true;
        releaseAttackState();
        SilentAim.release(this);
    }

    @Subscribe
    public void gameLoopCleanup(GameLoopEvent e) {
        if (consumeTeleportReset()) return;
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null
                || mc.thePlayer == null || mc.playerController == null
                || (target != null && (!target.isEntityAlive()
                    || mc.thePlayer.getDistanceToEntity(target) > reach.getInput()))) {
            clearTargetState();
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (consumeTeleportReset()) return;

        if (!e.isPre()) return;

        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null || mc.thePlayer == null || mc.playerController == null) {
            clearTargetState();
            return;
        }

        if (onlySurvival.isToggled() && mc.playerController.getCurrentGameType() != GameType.SURVIVAL) {
            clearTargetState(); return;
        }
        if (!tpCooldown.hasFinished()) { clearTargetState(); return; }
        if (mouseDown.isToggled() && !Mouse.isButtonDown(0)) { clearTargetState(); return; }
        if (disableWhenFlying.isToggled() && mc.thePlayer.capabilities.isFlying) { clearTargetState(); return; }

        EntityLivingBase candidate = Targets.getTargetEntityNoFov(reach.getInput());
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
            if (lastTargetId != -1
                    && System.currentTimeMillis() - lastTargetSeenMs > TARGET_PERSIST_MS) {
                lastTargetId = -1;
            }
            clearTargetState();
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
            req.instant = true;
            req.disableTremor = true;
        } else {
            float speed = (float) rotationSpeed.getInput();
            req.maxYawStepDeg = speed;
            req.maxPitchStepDeg = speed * 0.8f;
            // Drive stiffness off the slider too, the way BlockIn does. Mapping
            // the slider to the per-tick cap alone made everything above ~40 a
            // no-op — the spring never asks for more than stiffness*error — so
            // the whole upper range behaved identically and acquisition always
            // ran at the fixed 0.55, which lands a 60 degree sweep in one 33
            // degree step. Now the slider actually controls settle time.
            float t = MathHelper.clamp_float((speed - 10f) / (300f - 10f), 0f, 1f);
            req.stiffness = 0.15f + t * (0.80f - 0.15f);
        }
        req.claimant = this;
        SilentAim.aim(req);

        runAttack();
    }

    /**
     * Attack, still inside UpdateEvent.PRE — i.e. <b>before</b> the C03 flying
     * packet is written. Vanilla's clickMouse runs in {@code runTick} ahead of
     * {@code theWorld.updateEntities()}, so a real client's C02 always precedes
     * that tick's C03. Firing this on POST put the attack after the flying
     * packet, which is exactly what Grim's Post check looks for.
     *
     * <p>Nothing about the rotation changes between PRE and POST — serverYaw was
     * settled at {@code runTick} HEAD by {@link SilentAim#beginCycle()} — so the
     * readiness and aim-stability tests below read the same values they did
     * before the move.
     */
    private void runAttack() {
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null
                || mc.thePlayer == null || mc.playerController == null
                || target == null || locked || !target.isEntityAlive()
                || mc.thePlayer.getDistanceToEntity(target) > reach.getInput()) {
            clearTargetState();
            return;
        }
        if (!SilentAim.isClaimedBy(this) || isUsingConsumable()) {
            releaseAttackState();
            return;
        }

        ticksSinceAttack++;
        boolean reacted = System.currentTimeMillis() - targetAcquiredMs >= reactionDelayMs;
        float lastYawApplied = MathHelper.wrapAngleTo180_float(
                SilentAim.getServerYaw() - SilentAim.getPrevServerYaw());
        boolean stableAim = Math.abs(lastYawApplied) < 6.0F;
        if (reacted && stableAim
                && ticksSinceAttack >= (int) attackTickDelay.getInput()
                && isAimedAtTarget()) {
            crowClick();
        }

        BlockMode bm = autoBlock.isToggled() ? (BlockMode) blockMode.getMode() : BlockMode.NONE;
        boolean holdingSword = Utils.Player.isPlayerHoldingSword();
        if (bm == BlockMode.VANILLA && holdingSword
                && mc.thePlayer.prevSwingProgress < mc.thePlayer.swingProgress) {
            releaseAuraUseItemState();
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            auraUseItemQueued = true;
        } else if (bm == BlockMode.BLOCK_HIT && holdingSword) {

            long now = System.currentTimeMillis();
            boolean releaseWindow = leftUpTime > 0
                    && now >= leftUpTime - 90L
                    && now <= leftUpTime + 60L;
            setAuraUseItemState(!releaseWindow);
        } else {
            releaseAuraUseItemState();
        }
    }

    @Subscribe
    public void packetEvent(PacketEvent e) {
        if (e.isIncoming() && e.getPacket() instanceof S08PacketPlayerPosLook) {
            teleportResetPending = true;
        }
    }

    @Subscribe
    public void renderWorldLast(ForgeEvent fe) {
        if (fe.getEvent() instanceof RenderWorldLastEvent && target != null
                && targetRing.isToggled()) {
            int color = GuiModule.getThemeColor(0);
            Utils.HUD.drawRingAroundEntity(target, color, 0.6D, 0.05D, 2.5F);
        }
    }

    private void crowClick() {
        this.leftClickExecute();
    }

    public void leftClickExecute() {
        if (!SilentAim.isClaimedBy(this) || target == null || mc.thePlayer == null
                || mc.playerController == null || !target.isEntityAlive()) {
            releaseAttackState();
            return;
        }

        if (isUsingConsumable()) {
            releaseAttackState();
            return;
        }

        if (leftUpTime > 0L && leftDownTime > 0L) {
            if (System.currentTimeMillis() > leftUpTime && leftDown) {
                if (mc.thePlayer.isUsingItem()) mc.thePlayer.stopUsingItem();

                if (ThreadLocalRandom.current().nextInt(100) >= 3
                        && mc.thePlayer.getDistanceToEntity(target) <= reach.getInput()) {
                    // Swing FIRST. Minecraft.clickMouse calls swingItem() before
                    // attackEntity(), so the wire order is C0A then C02. Sending
                    // C02 first is Grim's PacketOrderB — a vanilla client can
                    // never produce it.
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, target);
                }
                ticksSinceAttack = 0;
                genLeftTimings();
                leftDown = false;
            } else if (System.currentTimeMillis() > leftDownTime) {
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

    private boolean consumeTeleportReset() {
        if (!teleportResetPending) return false;
        teleportResetPending = false;
        SilentAim.release(this);
        if (!disableOnTp.isToggled()) return false;

        tpCooldown.setCooldown(2000);
        tpCooldown.start();
        clearTargetState();
        return true;
    }

    private void clearTargetState() {
        target = null;
        locked = true;
        aimDriftYaw = aimDriftPitch = 0f;
        aimDriftTicks = 0;
        if (mc.thePlayer != null) {
            targetYaw = mc.thePlayer.rotationYaw;
            targetPitch = mc.thePlayer.rotationPitch;
        }
        SilentAim.release(this);
        releaseAttackState();
    }

    private void releaseAttackState() {
        leftDown = false;
        leftDownTime = 0L;
        leftUpTime = 0L;
        ticksSinceAttack = 0;
        releaseAuraUseItemState();
    }

    private void setAuraUseItemState(boolean pressed) {
        if (!pressed) {
            releaseAuraUseItemState();
            return;
        }
        if (mc.gameSettings == null || mc.gameSettings.keyBindUseItem == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        auraUseItemDown = true;
    }

    private void releaseAuraUseItemState() {
        if (mc.gameSettings != null && mc.gameSettings.keyBindUseItem != null) {
            KeyBinding useItem = mc.gameSettings.keyBindUseItem;
            if (auraUseItemQueued) {
                useItem.isPressed();
            }
            if (auraUseItemDown) {
                boolean physicallyDown = GameSettings.isKeyDown(useItem);
                KeyBinding.setKeyBindState(useItem.getKeyCode(), physicallyDown);
            }
        }
        auraUseItemQueued = false;
        auraUseItemDown = false;
    }

    private boolean isUsingConsumable() {
        if (mc.thePlayer == null || !mc.thePlayer.isUsingItem()) return false;
        ItemStack inUse = mc.thePlayer.getItemInUse();
        if (inUse == null) return false;
        return inUse.getItem() instanceof ItemFood
                || inUse.getItem() instanceof ItemBow
                || inUse.getItem() instanceof ItemPotion
                || inUse.getItem() instanceof ItemBucketMilk;
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
