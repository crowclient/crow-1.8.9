package crow.client.module.modules.combat;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.input.Mouse;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.LookEvent;
import crow.client.event.impl.MoveInputEvent;
import crow.client.event.impl.PacketEvent;
import crow.client.event.impl.UpdateEvent;
import crow.client.module.Module;
import crow.client.module.modules.world.AntiBot;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import crow.client.utils.Utils.Player;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class LegitAura2 extends Module {

    private EntityPlayer target;

    private SliderSetting rotationDistance, fov, reach;
    private SliderSetting rotationSpeed, jitter;
    private DoubleSliderSetting cps;
    private TickSetting disableOnTp, disableWhenFlying, mouseDown, onlySurvival, fixMovement;
    private ComboSetting blockMode;

    private List<EntityPlayer> pTargets;
    private ComboSetting sortMode;
    private CoolDown coolDown = new CoolDown(1);
    private boolean leftDown, leftn;
    private long leftDownTime, leftUpTime, leftk, leftl;
    private float yaw, pitch, prevYaw, prevPitch;

    private float lerpYaw, lerpPitch;
    private double leftm;

    public LegitAura2() {
        super("Aura", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting(EnumChatFormatting.RED + "" + EnumChatFormatting.BOLD + "Does not work with patcher"));
        this.registerSetting(reach = new SliderSetting("Reach (Blocks)", 3.3, 3, 6, 0.05));
        this.registerSetting(rotationDistance = new SliderSetting("Rotation Range", 3.5, 3, 6, 0.05));
        this.registerSetting(rotationSpeed = new SliderSetting("Rotation Speed", 150, 20, 360, 5));
        this.registerSetting(jitter = new SliderSetting("Jitter", 1.5, 0.0, 5.0, 0.1));
        this.registerSetting(cps = new DoubleSliderSetting("Left CPS", 9, 13, 1, 60, 0.5));
        this.registerSetting(fov = new SliderSetting("Fov", 30, 0, 360, 1));
        this.registerSetting(onlySurvival = new TickSetting("Only Survival", true));
        this.registerSetting(disableOnTp = new TickSetting("Disable after tp", true));
        this.registerSetting(disableWhenFlying = new TickSetting("Disable when flying", true));
        this.registerSetting(mouseDown = new TickSetting("Mouse Down", true));
        this.registerSetting(fixMovement = new TickSetting("Movement Fix", true));
        this.registerSetting(sortMode = new ComboSetting("Sort mode", SortMode.Distance));
        this.registerSetting(blockMode = new ComboSetting("Block mode", BlockMode.NONE));
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            lerpYaw   = mc.thePlayer.rotationYaw;
            lerpPitch = mc.thePlayer.rotationPitch;
            yaw       = lerpYaw;
            pitch     = lerpPitch;
            prevYaw   = lerpYaw;
            prevPitch = lerpPitch;
        }
    }

    @Override
    public void onDisable() {
        target = null;
        pTargets = null;
        leftDown = false;
        leftDownTime = 0L;
        leftUpTime = 0L;
        leftk = 0L;
        leftl = 0L;
        leftn = false;

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        Utils.Client.setMouseButtonState(0, false);
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) {
            if (mc.thePlayer != null) {
                yaw       = mc.thePlayer.rotationYaw;
                lerpYaw   = yaw;
                lerpPitch = mc.thePlayer.rotationPitch;
            }
            return;
        }
        Mouse.poll();
        pTargets = Utils.Player.getClosePlayers((float) rotationDistance.getInput());
        pTargets.removeIf(player -> !(isValidTarget(player)));
        SortMode sm = (SortMode) sortMode.getMode();
        EntityPlayer pTarget = pTargets.isEmpty() ? null
                : pTargets.stream().min(Comparator.comparingDouble(t -> sm.sv.value(t))).get();
        pTargets.remove(pTarget);

        boolean blocked =
                (pTarget == null)
                || (mc.currentScreen != null)
                || !(!onlySurvival.isToggled() || mc.playerController.getCurrentGameType() == GameType.SURVIVAL)
                || !coolDown.hasFinished()
                || !(!mouseDown.isToggled() || Mouse.isButtonDown(0))
                || !(!disableWhenFlying.isToggled() || !mc.thePlayer.capabilities.isFlying);

        if (blocked) {
            target = null;
            prevYaw   = yaw;
            prevPitch = pitch;

            if (mc.currentScreen != null) {

                lerpYaw   = mc.thePlayer.rotationYaw;
                lerpPitch = mc.thePlayer.rotationPitch;
            } else {

                float maxExit   = (float) rotationSpeed.getInput() * 0.5f;
                float exitYaw   = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - lerpYaw);
                float exitPitch = mc.thePlayer.rotationPitch - lerpPitch;
                if (Math.abs(exitYaw) < 1.5f && Math.abs(exitPitch) < 1.5f) {
                    lerpYaw   = mc.thePlayer.rotationYaw;
                    lerpPitch = mc.thePlayer.rotationPitch;
                } else {
                    lerpYaw  += MathHelper.clamp_float(exitYaw, -maxExit, maxExit);
                    lerpPitch = MathHelper.clamp_float(
                            lerpPitch + MathHelper.clamp_float(exitPitch, -maxExit * 0.6f, maxExit * 0.6f),
                            -90f, 90f);
                }
            }
            yaw   = lerpYaw;
            pitch = lerpPitch;
            return;
        }

        target = pTarget;
        crowClick();

        float[] rot = Utils.Player.getTargetRotations(target, 0);
        float desiredYaw   = rot[0];

        float desiredPitch = rot[1] + 3.2f + (float)(Utils.Java.rand().nextDouble() * 1.6);

        float maxTurn = (float) rotationSpeed.getInput();

        float yawDiff = MathHelper.wrapAngleTo180_float(desiredYaw - lerpYaw);
        lerpYaw += MathHelper.clamp_float(yawDiff, -maxTurn, maxTurn);

        float pitchDiff = desiredPitch - lerpPitch;
        lerpPitch = MathHelper.clamp_float(
                lerpPitch + MathHelper.clamp_float(pitchDiff, -maxTurn * 0.55f, maxTurn * 0.55f),
                -90f, 90f);

        double jAmt = jitter.getInput();
        if (jAmt > 0) {
            lerpYaw  += (float)((Utils.Java.rand().nextDouble() - 0.5) * jAmt * 0.10);
            lerpPitch = MathHelper.clamp_float(
                    lerpPitch + (float)((Utils.Java.rand().nextDouble() - 0.5) * jAmt * 0.07),
                    -90f, 90f);
        }

        prevYaw   = yaw;
        prevPitch = pitch;

        float rawDeltaYaw   = MathHelper.wrapAngleTo180_float(lerpYaw - prevYaw);
        float rawDeltaPitch = lerpPitch - prevPitch;
        float patchedYaw    = Utils.Player.patchGCD(rawDeltaYaw);
        float patchedPitch  = Utils.Player.patchGCD(rawDeltaPitch);

        float gcd = Utils.Player.getGcd();
        if (Math.abs(patchedYaw)   < gcd) patchedYaw   = 0;
        if (Math.abs(patchedPitch) < gcd) patchedPitch = 0;

        yaw   = prevYaw + patchedYaw;
        pitch = MathHelper.clamp_float(prevPitch + patchedPitch, -90f, 90f);

        lerpYaw   = yaw;
        lerpPitch = pitch;

        e.setYaw(yaw);
        e.setPitch(pitch);
    }

    @Subscribe
    public void onTick(crow.client.event.impl.TickEvent e) {
        BlockMode m = (BlockMode) blockMode.getMode();
        if((m == BlockMode.FUCKY) && (mc.thePlayer.prevSwingProgress < mc.thePlayer.swingProgress))
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
    }

    @Subscribe
    public void renderWorldLast(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof RenderWorldLastEvent)) return;
        EntityPlayer t = target;
        List<EntityPlayer> others = pTargets;
        if (t == null) return;

        int red = Math.min(255, (int) ((20 - t.getHealth()) * 13));
        int green = 255 - red;
        int rgb = new Color(Math.max(0, red), Math.max(0, green), 0).getRGB();
        Utils.HUD.drawBoxAroundEntity(t, 2, 0, 0, rgb, false);

        if (others != null) {
            try {
                for (EntityPlayer p : others)
                    Utils.HUD.drawBoxAroundEntity(p, 2, 0, 0, 0x800000FF, false);
            } catch (java.util.ConcurrentModificationException ignored) {}
        }
    }

    @Subscribe
    public void packetEvent(PacketEvent e) {
        if((e.getPacket() instanceof S08PacketPlayerPosLook) && disableOnTp.isToggled() && (coolDown.getTimeLeft() < 2000)) {
            coolDown.setCooldown(2000);
            coolDown.start();
        }
    }

    @Subscribe
    public void move(MoveInputEvent e) {
        if(!fixMovement.isToggled()) return;
    	e.setYaw(yaw);
    }

    @Subscribe
    public void lookEvent(LookEvent e) {
    	e.setPrevYaw(prevYaw);
    	e.setPrevPitch(prevPitch);
    	e.setYaw(yaw);
    	e.setPitch(pitch);
    }

    private void crowClick() {
        if (!Mouse.isButtonDown(0)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            Utils.Client.setMouseButtonState(0, false);
        }
        if (Mouse.isButtonDown(0))
            this.leftClickExecute(mc.gameSettings.keyBindAttack.getKeyCode());
    }

    public void leftClickExecute(int key) {
        if ((this.leftUpTime > 0L) && (this.leftDownTime > 0L)) {
            if ((System.currentTimeMillis() > this.leftUpTime) && leftDown) {
                KeyBinding.setKeyBindState(key, true);
                KeyBinding.onTick(key);
                this.genLeftTimings();
                Utils.Client.setMouseButtonState(0, true);
                leftDown = false;
            } else if (System.currentTimeMillis() > this.leftDownTime) {
                KeyBinding.setKeyBindState(key, false);
                leftDown = true;
                Utils.Client.setMouseButtonState(0, false);
            }
        } else
            this.genLeftTimings();

    }

    public void genLeftTimings() {
        double clickSpeed = Utils.Client.sampleClickCps(cps, Utils.Java.rand());
        long delay = Math.max(1L, Math.round(1000.0D / clickSpeed));
        if (System.currentTimeMillis() > this.leftk) {
            if (!this.leftn && (Utils.Java.rand().nextInt(100) >= 85)) {
                this.leftn = true;
                this.leftm = 1.1D + (Utils.Java.rand().nextDouble() * 0.15D);
            } else
                this.leftn = false;

            this.leftk = System.currentTimeMillis() + 500L + Utils.Java.rand().nextInt(1500);
        }

        if (this.leftn)
            delay = (long) (delay * this.leftm);

        if (System.currentTimeMillis() > this.leftl) {
            if (Utils.Java.rand().nextInt(100) >= 80)
                delay += 50L + Utils.Java.rand().nextInt(100);

            this.leftl = System.currentTimeMillis() + 500L + Utils.Java.rand().nextInt(1500);
        }

        this.leftUpTime = System.currentTimeMillis() + delay;
        this.leftDownTime = (System.currentTimeMillis() + (delay / 2L)) - Utils.Java.rand().nextInt(10);
    }

    public double getReach() {
    	return reach.getInput();
    }

    private boolean isValidTarget(EntityPlayer ep) {
    	return (
                (ep != null)
                && !AntiBot.bot(ep)
                && (ep != mc.thePlayer)
                && Utils.Player.fov(ep, (float) fov.getInput()));
    }

    public enum SortMode {
    	Distance(player -> mc.thePlayer.getDistanceToEntity(player)),
    	Health(player -> player.getHealth()),
    	Hurttime(player -> (float) player.hurtTime),
    	Fov(Player::fovToEntity);

    	private final SortValue sv;

    	private SortMode(SortValue sv) {
    		this.sv = sv;
    	}

    	public SortValue getSortValue() {
    		return sv;
    	}
    }

    @FunctionalInterface
    private interface SortValue {
        Float value(EntityPlayer player);
    }

    public enum BlockMode {
        NONE,
        FUCKY;
    }

}
