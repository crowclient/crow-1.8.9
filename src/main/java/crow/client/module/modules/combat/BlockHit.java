package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.concurrent.ThreadLocalRandom;

public class BlockHit extends Module {

    public enum Mode { Manual, Auto, Predict }

    public static ComboSetting<Mode> mode;
    public static SliderSetting range, chance, predictRange;
    public static TickSetting onlyPlayers, onlyForward, weaponOnly;
    public static DoubleSliderSetting waitMs, actionMs, hitPer, predictHoldMs;

    private boolean executingAction;
    private int hits;
    private int rHit = 1;
    private boolean call;
    private boolean tryStartCombo;
    private boolean predictBlocking;
    private long predictReleaseAt;
    private final CoolDown actionTimer = new CoolDown(0);
    private final CoolDown waitTimer = new CoolDown(0);

    public BlockHit() {
        super("BlockHit", ModuleCategory.combat);

        this.registerSetting(mode = new ComboSetting<>("Mode", Mode.Auto));
        this.registerSetting(weaponOnly = new TickSetting("Sword only", true));
        this.registerSetting(onlyPlayers = new TickSetting("Players only", true));
        this.registerSetting(onlyForward = new TickSetting("Forward only", false));
        this.registerSetting(waitMs = new DoubleSliderSetting("Hold time", 30, 40, 1, 300, 1));
        this.registerSetting(actionMs = new DoubleSliderSetting("After ms", 20, 30, 1, 300, 1));
        this.registerSetting(hitPer = new DoubleSliderSetting("Every N hits", 1, 1, 1, 10, 1));
        this.registerSetting(chance = new SliderSetting("Chance %", 100, 0, 100, 1));
        this.registerSetting(range = new SliderSetting("Range", 3, 1, 6, 0.05));
        this.registerSetting(predictRange = new SliderSetting("Pred range", 3.5, 1, 6, 0.05));
        this.registerSetting(predictHoldMs = new DoubleSliderSetting("Pred hold", 80, 140, 30, 400, 1));
    }

    @Override
    public String getHudSuffix() {
        return mode.getMode().name();
    }

    @Override
    public void onDisable() {
        if (executingAction || predictBlocking) {
            releaseBlock();
        }
        executingAction = false;
        predictBlocking = false;
        predictReleaseAt = 0L;
        hits = 0;
        rHit = 1;
        call = false;
        tryStartCombo = false;
    }

    @Subscribe
    public void onRender(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;

        if (tryStartCombo && waitTimer.hasFinished()) {
            tryStartCombo = false;
            startCombo();
        }
        if (actionTimer.hasFinished() && executingAction) {
            finishCombo();
        }

        if (mode.getMode() == Mode.Predict) {
            tickPredict();
        } else if (predictBlocking) {
            releaseBlock();
            predictBlocking = false;
            predictReleaseAt = 0L;
        }
    }

    @Subscribe
    public void onHit(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof AttackEntityEvent)) return;

        Mode m = mode.getMode();
        if (m == Mode.Predict) return;
        if (m == Mode.Manual && !Mouse.isButtonDown(1)) return;

        AttackEntityEvent e = (AttackEntityEvent) fe.getEvent();

        if (isSecondCall() || executingAction) return;

        hits++;
        if (hits > rHit) {
            hits = 1;
            rerollHitTarget();
        }

        if (onlyPlayers.isToggled() && !(e.target instanceof EntityPlayer)) return;
        if (chance.getInput() < 100 && Math.random() * 100 >= chance.getInput()) return;
        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingSword()) return;
        if (mc.thePlayer.getDistanceToEntity(e.target) > range.getInput()) return;
        if (rHit != hits) return;

        tryStartCombo();
    }

    private void tickPredict() {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingSword()) return;

        long now = System.currentTimeMillis();
        if (predictBlocking) {
            if (now >= predictReleaseAt) {
                releaseBlock();
                predictBlocking = false;
                predictReleaseAt = 0L;
            }
            return;
        }

        double r = predictRange.getInput();
        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) o;
            if (p == mc.thePlayer) continue;
            if (p.isDead || p.getHealth() <= 0) continue;
            if (mc.thePlayer.getDistanceToEntity(p) > r) continue;
            if (!incomingSwing(p)) continue;

            holdBlock();
            predictBlocking = true;
            predictReleaseAt = now + (long) ThreadLocalRandom.current().nextDouble(
                    predictHoldMs.getInputMin(), predictHoldMs.getInputMax() + 0.01);
            break;
        }
    }

    private boolean incomingSwing(EntityLivingBase ent) {
        if (!ent.isSwingInProgress) return false;
        if (ent.prevSwingProgress >= ent.swingProgress) return false;
        if (ent.swingProgress > 0.45F) return false;
        double dx = mc.thePlayer.posX - ent.posX;
        double dz = mc.thePlayer.posZ - ent.posZ;
        double desiredYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double diff = Math.abs(MathHelper.wrapAngleTo180_float((float) (ent.rotationYaw - desiredYaw)));
        return diff < 50.0;
    }

    private void finishCombo() {
        executingAction = false;
        releaseBlock();
    }

    private void startCombo() {
        if (onlyForward.isToggled()
                && !Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()))
            return;
        if (mode.getMode() == Mode.Manual && !Mouse.isButtonDown(1)) return;

        executingAction = true;
        holdBlock();
        actionTimer.setCooldown(
                (long) ThreadLocalRandom.current().nextDouble(waitMs.getInputMin(), waitMs.getInputMax() + 0.01));
        actionTimer.start();
    }

    private void tryStartCombo() {
        tryStartCombo = true;
        waitTimer.setCooldown(
                (long) ThreadLocalRandom.current().nextDouble(actionMs.getInputMin(), actionMs.getInputMax() + 0.01));
        waitTimer.start();
    }

    private void holdBlock() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        KeyBinding.onTick(key);
        Utils.Client.setMouseButtonState(1, true);
    }

    private void releaseBlock() {
        if (mc.gameSettings == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        Utils.Client.setMouseButtonState(1, false);
    }

    private void rerollHitTarget() {
        int span = (int) (hitPer.getInputMax() - hitPer.getInputMin()) + 1;
        rHit = ThreadLocalRandom.current().nextInt(span) + (int) hitPer.getInputMin();
    }

    private boolean isSecondCall() {
        if (call) {
            call = false;
            return true;
        } else {
            call = true;
            return false;
        }
    }
}
