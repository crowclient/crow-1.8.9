package crow.client.module.modules.client;

import crow.client.module.Module;
import crow.client.utils.Utils;

public class Developer extends Module {

    public Developer() {
        super("Developer", ModuleCategory.client);
        this.withDescription("Reveals modules hidden from the ClickGUI. Disable to restore the normal listing.");
    }

    @Override
    public void onEnable() {
        Module.revealHiddenModules = true;
        Utils.Player.sendMessageToSelf("§e[Developer] §aHidden modules are now visible in the ClickGUI.");
    }

    @Override
    public void onDisable() {
        Module.revealHiddenModules = false;
        Utils.Player.sendMessageToSelf("§e[Developer] §7Hidden modules are now back to hidden.");
    }
}
