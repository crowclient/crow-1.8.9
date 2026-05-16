package crow.client.module.modules.config;

import com.google.gson.JsonObject;
import crow.client.config.Config;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.setting.impl.ButtonSetting;
import crow.client.utils.Utils;
import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class ConfigSettings extends Module {
    private final File exportDirectory = new File(
            crow.client.main.Crow.getDataDir(), "exports");

    public ConfigSettings() {
        super("Config Actions", ModuleCategory.config);
        this.clientConfig = true;
        this.showInHud = false;
        this.withDescription("Save, export, refresh, and open your config folder.");
        this.registerSettings(
                new ButtonSetting("Save", this::saveCurrentConfig),
                new ButtonSetting("Refresh", this::refreshConfigList),
                new ButtonSetting("Export", this::exportCurrentConfig),
                new ButtonSetting("Open Folder", this::openConfigFolder)
        );
    }

    private void saveCurrentConfig() {
        Crow.configManager.save();
        if (Crow.clientConfig != null) {
            Crow.clientConfig.saveConfig();
        }
        Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX + "&aSaved current config.");
    }

    private void refreshConfigList() {
        Crow.configManager.discoverConfigs();
        Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX + "&aRefreshed config list.");
    }

    private void exportCurrentConfig() {
        try {
            saveCurrentConfig();
            if (!exportDirectory.isDirectory()) {
                exportDirectory.mkdirs();
            }

            JsonObject data = Crow.configManager.getConfig().getData();
            if (data == null) {
                Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX + "&cNo config data was available to export.");
                return;
            }

            String configName = Crow.configManager.getConfig() == null ? "config" : Crow.configManager.getConfig().getName();

            File exportFile = new File(exportDirectory, configName + Config.CROW_EXTENSION);
            int collision = 1;
            while (exportFile.exists()) {
                exportFile = new File(exportDirectory,
                        configName + " (" + collision + ")" + Config.CROW_EXTENSION);
                collision++;
            }
            File parent = exportFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            try (PrintWriter out = new PrintWriter(new FileWriter(exportFile))) {
                out.write(data.toString());
            }

            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(data.toString()), null);
            Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX + "&aExported config to &f" + exportFile.getAbsolutePath() + "&a and copied it to your clipboard.");
        } catch (Throwable throwable) {
            Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX + "&cFailed to export the current config.");
            throwable.printStackTrace();
        }
    }

    private void openConfigFolder() {
        try {
            File configDirectory = Crow.configManager.getConfigDirectory();
            if (!configDirectory.isDirectory()) {
                configDirectory.mkdirs();
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(configDirectory);
                Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX + "&aOpened your config folder.");
            } else {
                Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX
                        + "&cYour environment doesn't support opening folders. The config is at: &f"
                        + configDirectory.getAbsolutePath());
            }
        } catch (Throwable throwable) {
            Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX + "&cCould not open the config folder.");
            throwable.printStackTrace();
        }
    }
}
