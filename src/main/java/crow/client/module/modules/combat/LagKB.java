package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.PacketEvent;
import crow.client.module.Module;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.concurrent.ThreadLocalRandom;

public class LagKB extends Module {

    private final ComboSetting<Mode> modeSetting;
    private final SliderSetting horizontal;
    private final SliderSetting vertical;
    private final SliderSetting chance;
    private final SliderSetting reactionDelay;
    private final SliderSetting reactionJitter;
    private final SliderSetting reversePower;
    private final SliderSetting reverseTicksDuration;
    private final TickSetting onlyPlayers;
    private final TickSetting onlyWhileTargeting;
    private final TickSetting onlySprinting;
    private final TickSetting groundOnly;
    private final TickSetting sprintReset;

    private Entity lastAttackedEntity = null;

    private long lastAttackTime = 0;

    private boolean sprintResetActive = false;

    private int sprintResetTicks = 0;

    private boolean velocityPending = false;

    private long velocityApplyTime = 0;

    private double pendingMotionX, pendingMotionY, pendingMotionZ;

    private int reverseTicksRemaining = 0;

    private double reverseX, reverseZ;

    private boolean wasSprintKeyPressed = false;

    public LagKB() {
        super("LagKB", ModuleCategory.combat);
        this.withDescription("Reduces knockback using client-side motion manipulation. "
                + "Uses legit-looking techniques safe for most anti-cheats.");

        this.registerSetting(new DescriptionSetting("§aClient-side KB reduction — no fake packets."));

        this.registerSetting(modeSetting = new ComboSetting<>("Mode", Mode.Reduce));
        this.registerSetting(horizontal = new SliderSetting("H %", 85.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(vertical = new SliderSetting("V %", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(chance = new SliderSetting("Chance %", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(reactionDelay = new SliderSetting("React delay", 0.0D, 0.0D, 5.0D, 1.0D));
        this.registerSetting(reactionJitter = new SliderSetting("React jitter", 0.0D, 0.0D, 3.0D, 1.0D));
        this.registerSetting(reversePower = new SliderSetting("Rev power", 0.3D, 0.05D, 1.0D, 0.05D));
        this.registerSetting(reverseTicksDuration = new SliderSetting("Rev ticks", 1.0D, 1.0D, 3.0D, 1.0D));
        this.registerSetting(onlyPlayers = new TickSetting("Players only", true));
        this.registerSetting(onlyWhileTargeting = new TickSetting("Targeting", false));
        this.registerSetting(onlySprinting = new TickSetting("Sprint only", false));
        this.registerSetting(groundOnly = new TickSetting("Ground only", false));
        this.registerSetting(sprintReset = new TickSetting("Sprint reset", true));
    }

    public enum Mode {

        Reduce,

        Sprint_Reset,

        Reverse,
    }

    @Override
    public String getHudSuffix() {
        String modeStr = modeSetting.getMode().name().replace('_', ' ');
        if (modeSetting.getMode() == Mode.Reduce) {
            return modeStr + " " + (int) horizontal.getInput() + "%";
        }
        return modeStr;
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {

        if (sprintResetActive) {
            finishSprintReset();
        }
        resetState();
    }

    private void resetState() {
        lastAttackedEntity = null;
        lastAttackTime = 0;
        sprintResetActive = false;
        sprintResetTicks = 0;
        velocityPending = false;
        reverseTicksRemaining = 0;
    }

    @Override
    public void postApplyConfig() {
        syncVisibility();
    }

    @Override
    public void guiButtonToggled(Setting setting) {
        if (setting == modeSetting) {
            syncVisibility();
        }
    }

    private void syncVisibility() {
        Mode mode = modeSetting.getMode();
        try {

            horizontal.hideComponent(mode == Mode.Sprint_Reset);
            vertical.hideComponent(mode == Mode.Sprint_Reset);
            reversePower.hideComponent(mode != Mode.Reverse);
            reverseTicksDuration.hideComponent(mode != Mode.Reverse);
            sprintReset.hideComponent(mode == Mode.Sprint_Reset);
        } catch (NullPointerException ignored) {}
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        if (!e.isIncoming()) return;

        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = e.getPacket();

            if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;

            if (!passesChance()) return;

            if (onlyWhileTargeting.isToggled()) {
                if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) return;
            }

            if (onlyPlayers.isToggled() && !(lastAttackedEntity instanceof EntityPlayer)) {

                Entity attacker = mc.thePlayer.getLastAttacker();
                if (!(attacker instanceof EntityPlayer)) return;
            }

            if (onlySprinting.isToggled() && !mc.thePlayer.isSprinting()) return;

            if (groundOnly.isToggled() && !mc.thePlayer.onGround) return;

            double motionX = packet.getMotionX() / 8000.0D;
            double motionY = packet.getMotionY() / 8000.0D;
            double motionZ = packet.getMotionZ() / 8000.0D;

            int baseDelay = (int) reactionDelay.getInput();
            int jitter = (int) reactionJitter.getInput();
            int totalDelayTicks = baseDelay;
            if (jitter > 0) {
                totalDelayTicks += ThreadLocalRandom.current().nextInt(0, jitter + 1);
            }

            velocityPending = true;
            pendingMotionX = motionX;
            pendingMotionY = motionY;
            pendingMotionZ = motionZ;

            velocityApplyTime = System.currentTimeMillis() + (totalDelayTicks * 50L);
        }
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {

        if (fe.getEvent() instanceof AttackEntityEvent) {
            AttackEntityEvent event = (AttackEntityEvent) fe.getEvent();
            if (event.entityPlayer == mc.thePlayer) {
                lastAttackedEntity = event.target;
                lastAttackTime = System.currentTimeMillis();
            }
        }

        if (fe.getEvent() instanceof TickEvent.ClientTickEvent) {
            TickEvent.ClientTickEvent tick = (TickEvent.ClientTickEvent) fe.getEvent();
            if (tick.phase != TickEvent.Phase.END) return;
            if (!Utils.Player.isPlayerInGame()) {
                resetState();
                return;
            }

            processVelocityReduction();
            processSprintReset();
            processReverseMomentum();
        }
    }

    private void processVelocityReduction() {
        if (!velocityPending) return;
        if (System.currentTimeMillis() < velocityApplyTime) return;

        velocityPending = false;

        Mode mode = modeSetting.getMode();

        switch (mode) {
            case Reduce:
                applyMotionReduction();
                if (sprintReset.isToggled()) {
                    initiateSprintReset();
                }
                break;

            case Sprint_Reset:

                initiateSprintReset();
                break;

            case Reverse:
                applyMotionReduction();
                initiateReverseMomentum();
                if (sprintReset.isToggled()) {
                    initiateSprintReset();
                }
                break;
        }
    }

    private void applyMotionReduction() {
        double hPercent = horizontal.getInput();
        double vPercent = vertical.getInput();

        double hJitter = ThreadLocalRandom.current().nextDouble(-2.0D, 2.01D);
        double vJitter = ThreadLocalRandom.current().nextDouble(-1.0D, 1.01D);

        double hFactor = Math.min(1.0D, Math.max(0.0D, (hPercent + hJitter) / 100.0D));
        double vFactor = Math.min(1.0D, Math.max(0.0D, (vPercent + vJitter) / 100.0D));

        if (hFactor < 1.0D) {
            mc.thePlayer.motionX *= hFactor;
            mc.thePlayer.motionZ *= hFactor;
        }

        if (vFactor < 1.0D) {
            mc.thePlayer.motionY *= vFactor;
        }
    }

    private void initiateSprintReset() {
        if (sprintResetActive) return;
        if (!mc.thePlayer.isSprinting()) return;

        wasSprintKeyPressed = mc.gameSettings.keyBindSprint.isKeyDown();
        sprintResetActive = true;
        sprintResetTicks = 0;

        mc.thePlayer.setSprinting(false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
    }

    private void processSprintReset() {
        if (!sprintResetActive) return;

        sprintResetTicks++;

        if (sprintResetTicks >= 1) {
            finishSprintReset();
        }
    }

    private void finishSprintReset() {

        if (wasSprintKeyPressed || mc.thePlayer.moveForward > 0) {
            mc.thePlayer.setSprinting(true);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        sprintResetActive = false;
        sprintResetTicks = 0;
    }

    private void initiateReverseMomentum() {
        double power = reversePower.getInput();

        double motX = mc.thePlayer.motionX;
        double motZ = mc.thePlayer.motionZ;
        double mag = Math.sqrt(motX * motX + motZ * motZ);

        if (mag < 0.01D) return;

        reverseX = -(motX / mag) * power;
        reverseZ = -(motZ / mag) * power;
        reverseTicksRemaining = (int) reverseTicksDuration.getInput();
    }

    private void processReverseMomentum() {
        if (reverseTicksRemaining <= 0) return;

        double decay = (double) reverseTicksRemaining / reverseTicksDuration.getInput();

        mc.thePlayer.motionX += reverseX * decay;
        mc.thePlayer.motionZ += reverseZ * decay;

        reverseTicksRemaining--;
    }

    private boolean passesChance() {
        double ch = chance.getInput();
        return ch >= 100.0D || Math.random() < (ch / 100.0D);
    }
}
