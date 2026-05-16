package crow.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.TickEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.movement.Fly;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;

public class FallSpeed extends Module {
    public static DescriptionSetting dc;
    public static SliderSetting a;
    public static TickSetting b;

    public FallSpeed() {
        super("FallSpeed", ModuleCategory.movement);
        this.registerSetting(dc = new DescriptionSetting("Vanilla max: 3.92"));
        this.registerSetting(a = new SliderSetting("Motion", 5.0D, 0.0D, 8.0D, 0.1D));
        this.registerSetting(b = new TickSetting("No XZ", true));
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if ((double) mc.thePlayer.fallDistance >= 2.5D) {
            Module fly = Crow.moduleManager.getModuleByClazz(Fly.class);
            Module noFall = Crow.moduleManager.getModuleByClazz(NoFall.class);

            if ((fly != null && fly.isEnabled()) || (noFall != null && noFall.isEnabled())) {
                return;
            }

            if (mc.thePlayer.capabilities.isCreativeMode || mc.thePlayer.capabilities.isFlying) {
                return;
            }

            if (mc.thePlayer.isOnLadder() || mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) {
                return;
            }

            mc.thePlayer.motionY = -a.getInput();
            if (b.isToggled()) {
                mc.thePlayer.motionX = mc.thePlayer.motionZ = 0.0D;
            }
        }

    }
}
