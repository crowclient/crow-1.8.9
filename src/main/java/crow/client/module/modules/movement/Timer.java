package crow.client.module.modules.movement;

import com.google.common.eventbus.Subscribe;
import crow.client.clickgui.crow.ClickGui;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;

public class Timer extends Module {
    public static SliderSetting speed;
    public static TickSetting strafeOnly;

    public Timer() {
        super("Timer", ModuleCategory.movement);
        speed = new SliderSetting("Speed", 1.0D, 0.0D, 5.0D, 0.01D);
        strafeOnly = new TickSetting("Strafe only", false);
        this.registerSetting(speed);
        this.registerSetting(strafeOnly);
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!(mc.currentScreen instanceof ClickGui)) {
            if (strafeOnly.isToggled() && mc.thePlayer.moveStrafing == 0.0F) {
                Utils.Client.resetTimer();
                return;
            }

            Utils.Client.getTimer().timerSpeed = (float) speed.getInput();
        } else {
            Utils.Client.resetTimer();
        }

    }

    public void onDisable() {
        Utils.Client.resetTimer();
    }
}
