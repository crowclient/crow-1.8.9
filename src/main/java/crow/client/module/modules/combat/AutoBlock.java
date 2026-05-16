package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Mouse;

public class AutoBlock extends Module {
    public static DoubleSliderSetting duration, distance;
    public static SliderSetting chance;
    private boolean engaged;
    private final CoolDown engagedTime = new CoolDown(0);

    public AutoBlock() {
        super("AutoBlock", ModuleCategory.combat);

        this.registerSetting(duration = new DoubleSliderSetting("Hold ms", 20, 100, 1, 500, 1));
        this.registerSetting(distance = new DoubleSliderSetting("Distance", 0, 3, 0, 6, 0.01));
        this.registerSetting(chance = new SliderSetting("Chance %", 100, 0, 100, 1));
    }

    @Override
    public void onEnable() {
        engaged = false;
    }

    @Override
    public void onDisable() {
        if (engaged) {
            release();
        }
        engaged = false;
    }

    @Subscribe
    public void onRender(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame() || !Utils.Player.isPlayerHoldingSword())
            return;

        if (engaged) {

            pressHold();

            if ((engagedTime.hasFinished() || !Mouse.isButtonDown(0))
                    && duration.getInputMin() <= engagedTime.getElapsedTime()) {
                engaged = false;
                release();
            }
            return;
        }

        if (Mouse.isButtonDown(0) && mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null
                && mc.thePlayer.getDistanceToEntity(mc.objectMouseOver.entityHit) >= distance.getInputMin()
                && mc.thePlayer.getDistanceToEntity(mc.objectMouseOver.entityHit) <= distance.getInputMax()
                && (chance.getInput() == 100 || Math.random() <= chance.getInput() / 100)) {
            engaged = true;
            engagedTime.setCooldown((long) duration.getInputMax());
            engagedTime.start();
            press();
        }
    }

    private static void release() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, false);
        Utils.Client.setMouseButtonState(1, false);
    }

    private static void press() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        KeyBinding.onTick(key);
        Utils.Client.setMouseButtonState(1, true);
    }

    private static void pressHold() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        KeyBinding.onTick(key);
    }
}
