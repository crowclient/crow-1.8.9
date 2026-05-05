package crow.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.config.ConfigModuleManager;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ConfigManager {

    public final ConfigModuleManager configModuleManager;
    public static boolean applyingConfig;

    private final File configDirectory = new File(crow.client.main.Crow.getDataDir(), "configs");
    private Config config;
    private final ArrayList<Config> configs = new ArrayList<>();

    public ConfigManager() {
        if (!configDirectory.isDirectory()) {
            configDirectory.mkdirs();
        }

        installFeaturedConfigs();
        File defaultFile = new File(configDirectory, "default" + Config.CROW_EXTENSION);
        this.config = new Config(defaultFile);
        discoverConfigs();
        configModuleManager = new ConfigModuleManager();
        if (!defaultFile.exists()) {
            save();
        }
    }

    private static final String[] FEATURED_CONFIGS = {
            "pvp.crow"
    };

    private void installFeaturedConfigs() {
        for (String resourceName : FEATURED_CONFIGS) {
            File target = new File(configDirectory, resourceName);
            if (target.exists()) continue;
            java.io.InputStream in = ConfigManager.class.getResourceAsStream(
                    "/assets/crow/config/" + resourceName);
            if (in == null) continue;
            try (java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                }
                out.flush();
            } catch (IOException ignored) {

            } finally {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }

    @SuppressWarnings("unused")
    public static boolean isOutdated(File file) {
        JsonParser jsonParser = new JsonParser();
        try (FileReader reader = new FileReader(file)) {
            Object obj = jsonParser.parse(reader);
            JsonObject data = (JsonObject) obj;
            return false;
        } catch (JsonSyntaxException | ClassCastException | IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public void discoverConfigs() {
        configs.clear();
        if (configDirectory.listFiles() == null || !(Objects.requireNonNull(configDirectory.listFiles()).length > 0))
            return;

        boolean hasCrowDefault = new File(configDirectory, "default" + Config.CROW_EXTENSION).isFile();

        for (File file : Objects.requireNonNull(configDirectory.listFiles())) {
            if (isSupportedConfigFile(file)) {
                String lowerName = file.getName().toLowerCase();
                if (hasCrowDefault && lowerName.equals("default" + Config.LEGACY_EXTENSION)) {
                    continue;
                }
                if (!isOutdated(file)) {
                    configs.add(new Config(new File(file.getPath())));
                }
            }
        }
        if (configModuleManager != null)
            configModuleManager.updater(configs);
    }

    public Config getConfig() {
        return config;
    }

    public void save() {
        JsonObject data = new JsonObject();
        data.addProperty("version", Crow.versionManager.getClientVersion().getVersion());
        data.addProperty("author", "Unknown");
        data.addProperty("notes", "");
        data.addProperty("intendedServer", "");
        data.addProperty("usedFor", 0);
        data.addProperty("lastEditTime", System.currentTimeMillis());

        JsonObject modules = new JsonObject();
        for (Module module : Crow.moduleManager.getConfigModules()) {
            modules.add(module.getName(), module.getConfigAsJson());
        }
        data.add("modules", modules);

        config.save(data);
    }

    public void setConfig(Config config) {
        applyingConfig = true;
        this.config = config;
        JsonObject data = config.getData().get("modules").getAsJsonObject();
        List<Module> knownModules = new ArrayList<>(Crow.moduleManager.getConfigModules());
        for (Module module : knownModules) {
            if (!module.isClientConfig()) {
                if (data.has(module.getName())) {
                    module.applyConfigFromJson(data.get(module.getName()).getAsJsonObject());
                } else {
                    module.resetToDefaults();
                }
            }
        }
        applyingConfig = false;

    }

    public void loadConfigByName(String replace) {
        discoverConfigs();
        for (Config config : configs) {
            if (config.getName().equals(replace))
                setConfig(config);
        }
    }

    public ArrayList<Config> getConfigs() {
        discoverConfigs();

        return new ArrayList<>(configs);
    }

    public void copyConfig(Config config, String s) {
        File file = new File(configDirectory, ensureConfigExtension(s));
        Config newConfig = new Config(file);
        newConfig.save(config.getData());
    }

    public void resetConfig() {
        for (Module module : Crow.moduleManager.getConfigModules())
            if (!module.isClientConfig())
                module.resetToDefaults();
        save();
    }

    public void deleteConfig(Config config) {
        config.file.delete();
        if (config.getName().equals(this.config.getName())) {
            discoverConfigs();
            if (this.configs.size() < 2) {
                this.resetConfig();
                File defaultFile = new File(configDirectory, "default" + Config.CROW_EXTENSION);
                this.config = new Config(defaultFile);
                save();
            } else {
                this.config = this.configs.get(0);
            }

            this.save();
        }
    }

    public File getConfigDirectory() {
        return configDirectory;
    }

    public File createConfigFile(String name) {
        return new File(configDirectory, ensureConfigExtension(name));
    }

    public String ensureConfigExtension(String name) {
        if (name.endsWith(Config.CROW_EXTENSION) || name.endsWith(Config.LEGACY_EXTENSION)) {
            return name;
        }
        return name + Config.CROW_EXTENSION;
    }

    private boolean isSupportedConfigFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(Config.CROW_EXTENSION) || name.endsWith(Config.LEGACY_EXTENSION);
    }
}
