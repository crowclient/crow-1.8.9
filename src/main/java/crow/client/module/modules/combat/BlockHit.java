package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class BlockHit extends Module {
    public static SliderSetting range, chance;
    public static TickSetting onlyPlayers, onlyForward;
    public static DoubleSliderSetting waitMs;
    public static DoubleSliderSetting actionMs;
    public static DoubleSliderSetting hitPer;

    private boolean executingAction;
    private int hits;
    private int rHit = 1;
    private boolean call;
    private boolean tryStartCombo;
    private final CoolDown actionTimer = new CoolDown(0);
    private final CoolDown waitTimer = new CoolDown(0);

    public BlockHit() {
        super("BlockHit", ModuleCategory.combat);

        this.registerSetting(onlyPlayers = new TickSetting("Only combo players", true));
        this.registerSetting(onlyForward = new TickSetting("Only blockhit when walking forward", false));
        this.registerSetting(waitMs = new DoubleSliderSetting("Action Time (MS)", 30, 40, 1, 300, 1));
        this.registerSetting(actionMs = new DoubleSliderSetting("Block after ... ms", 20, 30, 1, 300, 1));
        this.registerSetting(hitPer = new DoubleSliderSetting("Once every ... hits", 1, 1, 1, 10, 1));
        this.registerSetting(chance = new SliderSetting("Chance %", 100, 0, 100, 1));
        this.registerSetting(range = new SliderSetting("Range: ", 3, 1, 6, 0.05));
    }

    @Override
    public void onDisable() {

        if (executingAction) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            Utils.Client.setMouseButtonState(1, false);
        }
        executingAction = false;
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
    }

    @Subscribe
    public void onHit(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof AttackEntityEvent)) return;
        AttackEntityEvent e = (AttackEntityEvent) fe.getEvent();

        if (isSecondCall() || executingAction) return;

        hits++;

        if (hits > rHit) {
            hits = 1;
            rerollHitTarget();
        }

        if (onlyPlayers.isToggled() && !(e.target instanceof EntityPlayer)) return;
        if (chance.getInput() < 100 && Math.random() * 100 >= chance.getInput()) return;
        if (!Utils.Player.isPlayerHoldingSword()) return;
        if (mc.thePlayer.getDistanceToEntity(e.target) > range.getInput()) return;
        if (rHit != hits) return;

        tryStartCombo();
    }

    private void finishCombo() {
        executingAction = false;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        Utils.Client.setMouseButtonState(1, false);
    }

    private void startCombo() {
        if (onlyForward.isToggled()
                && !Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()))
            return;

        executingAction = true;
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        KeyBinding.onTick(key);
        Utils.Client.setMouseButtonState(1, true);
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

    private void rerollHitTarget() {
        int range = (int) (hitPer.getInputMax() - hitPer.getInputMin()) + 1;
        rHit = ThreadLocalRandom.current().nextInt(range) + (int) hitPer.getInputMin();
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
