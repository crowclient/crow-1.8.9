package crow.client.module.modules.movement;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.PacketEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import net.minecraft.network.play.server.S30PacketWindowItems;

public class NoSlow extends Module {
    public static SliderSetting slowPercent;
    public static TickSetting noReset;

    public NoSlow() {
        super("NoSlow", ModuleCategory.movement);
        this.registerSetting(new DescriptionSetting("Default is 80% motion reduction."));
        this.registerSetting(new DescriptionSetting("Use 'No Reset' on Hypixel."));
        this.registerSetting(slowPercent = new SliderSetting("Slow %", 80.0D, 0.0D, 80.0D, 1.0D));
        this.registerSetting(noReset = new TickSetting("No Reset", false));
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (noReset.isToggled()) {
            if (e.getPacket() instanceof S30PacketWindowItems) {
                if (mc.thePlayer.isUsingItem()) {
                    e.cancel();
                }
            }
        }
    }

}
