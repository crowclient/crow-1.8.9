package crow.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

public class Parkour extends Module {

    private final CoolDown cd = new CoolDown(1);

    public Parkour() {
        super("Parkour", ModuleCategory.movement);
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame())
            return;

        KeyBinding jumpBinding = getJumpBinding();
        if (jumpBinding == null) return;

        if (!GameSettings.isKeyDown(jumpBinding) && cd.firstFinish())
            KeyBinding.setKeyBindState(jumpBinding.getKeyCode(), false);

        if (mc.thePlayer.onGround && Utils.Player.playerOverAir()
                && (mc.thePlayer.motionX != 0 || mc.thePlayer.motionZ != 0)) {
            KeyBinding.setKeyBindState(jumpBinding.getKeyCode(), true);
            cd.setCooldown(10);
            cd.start();
        }
    }

    @Override
    public void onDisable() {
        KeyBinding jumpBinding = getJumpBinding();
        if (jumpBinding != null) {
            KeyBinding.setKeyBindState(jumpBinding.getKeyCode(), GameSettings.isKeyDown(jumpBinding));
        }
    }

    private KeyBinding getJumpBinding() {
        return mc != null && mc.gameSettings != null ? mc.gameSettings.keyBindJump : null;
    }

}
