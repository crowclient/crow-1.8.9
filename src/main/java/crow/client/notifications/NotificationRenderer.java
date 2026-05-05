package crow.client.notifications;

import com.google.common.eventbus.Subscribe;

import crow.client.config.ConfigManager;
import crow.client.event.impl.Render2DEvent;
import crow.client.main.ClientConfig;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.render.Notifications;
import crow.client.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public class NotificationRenderer {
    public static final NotificationRenderer notificationRenderer = new NotificationRenderer();
    private static Minecraft mc = Minecraft.getMinecraft();

    private static boolean isEnabled() {
        if (!GuiModule.notifications()) return false;

        if (Crow.moduleManager != null) {
            Module m = Crow.moduleManager.getModuleByClazz(Notifications.class);
            if (m != null && !m.isEnabled()) return false;
        }
        return true;
    }

    @Subscribe
    public void onRender(Render2DEvent e) {
        if (isEnabled())
            NotificationManager.render();
    }

    public static void moduleStateChanged(Module m) {
        if (!isEnabled() || mc.currentScreen != null || ConfigManager.applyingConfig
                || ClientConfig.applyingConfig)
            return;

        if (!m.getClass().equals(Gui.class)) {
            String s = m.isEnabled() ? "enabled" : "disabled";
            NotificationManager
                    .show(new Notification(NotificationType.INFO, "Module " + s, m.getName() + " has been " + s, 1));
        }
    }
}