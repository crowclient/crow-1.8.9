package crow.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;

public class SafeWalk extends Module {
    public static TickSetting shiftAtEdge;

    private boolean wasShifting;

    public SafeWalk() {
        super("SafeWalk", ModuleCategory.player);
        this.registerSetting(shiftAtEdge = new TickSetting("Shift at edge", false));
    }

    @Override
    public void onDisable() {
        if (wasShifting) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            wasShifting = false;
        }
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return;

        if (shiftAtEdge.isToggled() && mc.thePlayer.onGround && Utils.Player.playerOverAir()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            wasShifting = true;
        } else if (wasShifting) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            wasShifting = false;
        }
    }
}
