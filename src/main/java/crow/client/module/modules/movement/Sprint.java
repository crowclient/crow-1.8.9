package crow.client.module.modules.movement;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;

public class Sprint extends Module {
    public static TickSetting multiDir;

    public Sprint() {
        super("Sprint", ModuleCategory.movement);
        this.registerSetting(multiDir = new TickSetting("All dirs", false));
    }

    @Subscribe
    public void p(TickEvent e) {
        if (Utils.Player.isPlayerInGame() && mc.inGameHasFocus) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
    }

}
