package crow.client.module.modules.client;

import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.notifications.NotificationRenderer;
import net.minecraftforge.common.MinecraftForge;

public class SelfDestruct extends Module {

    public static boolean selfDestructed;

    public SelfDestruct() {
        super("Self Destruct", ModuleCategory.client);
    }

    public void onEnable() {
        this.disable();
        selfDestructed = true;
        mc.displayGuiScreen(null);

        for (Module module : Crow.moduleManager.getModules()) {
            module.unRegister();
        }
        for(Object obj : Crow.registered) {
            MinecraftForge.EVENT_BUS.unregister(obj);
        }

        Crow.eventBus.unregister(NotificationRenderer.notificationRenderer);

    }
}
