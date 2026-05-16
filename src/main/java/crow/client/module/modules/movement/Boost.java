package crow.client.module.modules.movement;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.TickEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.utils.Utils;

public class Boost extends Module {
    public static DescriptionSetting c;
    public static SliderSetting a;
    public static SliderSetting b;
    private int i;
    private boolean t;

    public Boost() {
        super("Boost", ModuleCategory.movement);
        this.registerSetting(c = new DescriptionSetting("20 ticks are in 1 second"));
        this.registerSetting(a = new SliderSetting("Multiplier", 2.0D, 1.0D, 3.0D, 0.05D));
        this.registerSetting(b = new SliderSetting("Ticks", 15.0D, 1.0D, 80.0D, 1.0D));
    }

    public void onEnable() {
        Module timer = Crow.moduleManager.getModuleByClazz(Timer.class);
        if (timer != null && timer.isEnabled()) {
            this.t = true;
            timer.disable();
        }

    }

    public void onDisable() {
        this.i = 0;
        if (Utils.Client.getTimer().timerSpeed != 1.0F) {
            Utils.Client.resetTimer();
        }

        if (this.t) {
            Module timer = Crow.moduleManager.getModuleByClazz(Timer.class);
            if (timer != null)
                timer.enable();
        }

        this.t = false;
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (this.i == 0) {
            this.i = mc.thePlayer.ticksExisted;
        }

        Utils.Client.getTimer().timerSpeed = (float) a.getInput();
        if ((double) this.i == (double) mc.thePlayer.ticksExisted - b.getInput()) {
            Utils.Client.resetTimer();
            this.disable();
        }

    }
}
