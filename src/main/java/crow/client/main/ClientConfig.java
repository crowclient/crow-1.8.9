package crow.client.main;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import crow.client.clickgui.crow.components.CategoryComponent;
import crow.client.module.GuiModule;
import crow.client.module.Module;
import crow.client.module.Module.ModuleCategory;
import crow.client.module.modules.HUD;
import crow.client.module.modules.render.ArrayListMod;
import crow.client.module.modules.render.ScoreboardMod;
import crow.client.utils.Utils;
import crow.keystroke.KeyStroke;
import net.minecraft.client.Minecraft;

public class ClientConfig {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static boolean applyingConfig;

    private final File cfgDir = Crow.getDataDir();
    private final File cfgFile;
    private final String fileName = "clientconfig.kv";
    private JsonObject config;

    public ClientConfig() {
        if (!cfgDir.exists())
            cfgDir.mkdirs();
        cfgFile = new File(cfgDir, fileName);
        if (!cfgFile.isFile() || cfgFile.length() == 0L) {
            return;
        }

        final JsonParser jsonParser = new JsonParser();
        try (FileReader reader = new FileReader(cfgFile)) {
            final JsonElement obj = jsonParser.parse(reader);
            if (obj == null || obj.isJsonNull()) {
                return;
            }
            if (obj.isJsonObject()) {
                config = obj.getAsJsonObject();
            } else {
                reportInvalidConfig();
            }
        } catch (JsonSyntaxException | IOException e) {
            reportInvalidConfig();
        }
    }

    public void applyConfig() {
        if (config == null) {
            return;
        }

        applyingConfig = true;
        try {
            Utils.URLS.hypixelApiKey = config.get("apikey").getAsString();
            // Pass the parent clickgui object — loadClickGuiCoords pulls
            // catPos out itself, and ALSO reads sibling fields like
            // "compactX/Y" and "spacious" that live on clickgui directly,
            // not inside catPos. Previously this passed catPos and those
            // sibling lookups silently no-op'd, so SpaciousGui layout and
            // CompactGui position never restored across restarts.
            loadClickGuiCoords(config.get("clickgui").getAsJsonObject());
            Crow.configManager.loadConfigByName(config.get("currentconfig").getAsString());
            loadHudCoords(config.get("hud").getAsJsonObject());
            loadTerminalCoords(config.get("clickgui").getAsJsonObject());
            loadModules(config.get("modules").getAsJsonObject());
        } catch (final Exception e) {
            config = null;
            reportInvalidConfig();
        } finally {
            applyingConfig = false;
        }
    }

    /**
     * Captures the fully initialized runtime defaults without writing them yet.
     * This keeps a missing, empty, or invalid on-disk file untouched at startup;
     * the next ordinary save persists the normal client state.
     */
    public void initializeDefaultsIfNeeded() {
        if (config == null) {
            config = getConfigAsJson();
        }
    }

    private void reportInvalidConfig() {
        System.err.println("[Crow] Ignoring invalid client config at " + cfgFile.getAbsolutePath()
                + "; the existing file was left unchanged.");
    }

    public void applyKeyStrokeSettingsFromConfigFile() {
        if (config == null || !config.has("keystrokes") || !config.get("keystrokes").isJsonObject()) {
            return;
        }

        try {
            final JsonObject data = config.get("keystrokes").getAsJsonObject();
            KeyStroke.x = data.get("x").getAsInt();
            KeyStroke.y = data.get("y").getAsInt();
            KeyStroke.enabled = data.get("enabled").getAsBoolean();
            KeyStroke.showMouseButtons = data.get("mbEnabled").getAsBoolean();
            KeyStroke.currentColorNumber = data.get("color").getAsInt();
            KeyStroke.outline = data.get("outline").getAsBoolean();
            KeyStroke.blurBackground = !data.has("blurBackground") || data.get("blurBackground").getAsBoolean();
            KeyStroke.size = data.has("size") ? data.get("size").getAsFloat() : 1.0F;
            KeyStroke.backgroundOpacity = data.has("backgroundOpacity") ? data.get("backgroundOpacity").getAsInt() : 155;
        } catch (final RuntimeException e) {
            System.err.println("[Crow] Ignoring invalid keystroke settings in " + cfgFile.getAbsolutePath() + ".");
        }
    }

    private JsonObject getClickGuiAsJson() {
        final JsonObject data = new JsonObject();
        data.add("catPos", getClickGuiPosAsJson());
        data.addProperty("terminalX", Crow.clickGui.terminal.getX());
        data.addProperty("terminalY", Crow.clickGui.terminal.getY());
        data.addProperty("width", Crow.clickGui.terminal.getWidth());
        data.addProperty("height", Crow.clickGui.terminal.getHeight());
        data.addProperty("hidden", Crow.clickGui.terminal.hidden);

        data.addProperty("opened", Crow.clickGui.terminal.opened);
        if (Crow.compactGui != null) {
            data.addProperty("compactX", Crow.compactGui.getSavedContainerX());
            data.addProperty("compactY", Crow.compactGui.getSavedContainerY());
        }
        if (Crow.spaciousGui != null) {
            data.add("spacious", Crow.spaciousGui.getStateAsJson());
        }
        return data;
    }

    public JsonObject getClickGuiPosAsJson() {
        final JsonObject data = new JsonObject();
        for (final CategoryComponent cat : Crow.clickGui.getCategoryList()) {
            final JsonObject catData = new JsonObject();
            catData.addProperty("X", cat.getX());
            catData.addProperty("Y", cat.getY());
            catData.addProperty("visable", cat.visable);
            catData.addProperty("opened", cat.categoryOpened);
            data.add(cat.categoryName.name(), catData);
        }
        return data;
    }

    public JsonObject getConfigAsJson() {
        final JsonObject data = new JsonObject();

        data.addProperty("apikey", Utils.URLS.hypixelApiKey);
        data.addProperty("currentconfig", Crow.configManager.getConfig().getName());
        data.add("keystrokes", getKeystrokeAsJson());
        data.add("hud", getHudAsJson());
        data.add("clickgui", getClickGuiAsJson());
        data.add("modules", getModulesAsJson());

        return data;
    }

    private JsonObject getHudAsJson() {
        final JsonObject data = new JsonObject();
        data.addProperty("hudX", HUD.getHudX());
        data.addProperty("hudY", HUD.getHudY());
        data.addProperty("arrayListX", ArrayListMod.getPosX());
        data.addProperty("arrayListY", ArrayListMod.getPosY());
        if (ScoreboardMod.posX != null && ScoreboardMod.posY != null) {
            data.addProperty("scoreboardX", (int) ScoreboardMod.posX.getInput());
            data.addProperty("scoreboardY", (int) ScoreboardMod.posY.getInput());
        }
        return data;
    }

    private JsonObject getKeystrokeAsJson() {
        final JsonObject data = new JsonObject();
        data.addProperty("x", KeyStroke.x);
        data.addProperty("y", KeyStroke.y);
        data.addProperty("enabled", KeyStroke.enabled);
        data.addProperty("mbEnabled", KeyStroke.showMouseButtons);
        data.addProperty("color", KeyStroke.currentColorNumber);
        data.addProperty("outline", KeyStroke.outline);
        data.addProperty("blurBackground", KeyStroke.blurBackground);
        data.addProperty("size", KeyStroke.size);
        data.addProperty("backgroundOpacity", KeyStroke.backgroundOpacity);
        return data;
    }

    private JsonObject getModulesAsJson() {
        final JsonObject data = new JsonObject();
        for (final Module m : Crow.moduleManager.getClientConfigModules())
            if (!(m instanceof GuiModule))
                data.add(m.getName(), m.getConfigAsJson());
        return data;
    }

    private void loadClickGuiCoords(JsonObject clickGuiData) {
        // The legacy click-GUI's per-category positions live under a
        // "catPos" sub-object; the sibling fields "compactX", "compactY",
        // and "spacious" live directly on clickGuiData.
        JsonObject catPos = clickGuiData.has("catPos")
                ? clickGuiData.get("catPos").getAsJsonObject()
                : clickGuiData; // legacy fallback for old files
        for (final CategoryComponent cat : Crow.clickGui.getCategoryList()) {
            if (!catPos.has(cat.categoryName.name())) continue;
            final JsonObject catData = catPos.get(cat.categoryName.name()).getAsJsonObject();
            cat.setCoords(catData.get("X").getAsInt(), catData.get("Y").getAsInt());
            cat.setOpened(catData.get("opened").getAsBoolean());
            if (cat.categoryName != ModuleCategory.category) {
                final boolean visable = (cat.categoryName == ModuleCategory.category)
                        || catData.get("visable").getAsBoolean();
                cat.visable = visable;
                Crow.moduleManager.guiModuleManager.getModuleByModuleCategory(cat.categoryName).setToggled(visable);
            }
        }
        if (Crow.compactGui != null && clickGuiData.has("compactX") && clickGuiData.has("compactY")) {
            Crow.compactGui.setSavedPosition(clickGuiData.get("compactX").getAsInt(), clickGuiData.get("compactY").getAsInt());
        }
        if (Crow.spaciousGui != null && clickGuiData.has("spacious")) {
            Crow.spaciousGui.applyState(clickGuiData.get("spacious").getAsJsonObject());
        }
    }

    private void loadHudCoords(JsonObject data) {
        HUD.setHudX(data.get("hudX").getAsInt());
        HUD.setHudY(data.get("hudY").getAsInt());
        ArrayListMod.setPosX(data.has("arrayListX") ? data.get("arrayListX").getAsInt() : ArrayListMod.DEFAULT_POS_X);
        ArrayListMod.setPosY(data.has("arrayListY") ? data.get("arrayListY").getAsInt() : ArrayListMod.DEFAULT_POS_Y);
        if (ScoreboardMod.posX != null && ScoreboardMod.posY != null) {
            if (data.has("scoreboardX")) {
                ScoreboardMod.posX.setValue(data.get("scoreboardX").getAsInt());
            }
            if (data.has("scoreboardY")) {
                ScoreboardMod.posY.setValue(data.get("scoreboardY").getAsInt());
            }
        }
    }

    private void loadModules(JsonObject data) {
        final List<Module> knownModules = new ArrayList<>(Crow.moduleManager.getClientConfigModules());
        for (final Module module : knownModules)
            if (data.has(module.getName()))
                module.applyConfigFromJson(data.get(module.getName()).getAsJsonObject());
            else
                module.resetToDefaults();
    }

    private void loadTerminalCoords(JsonObject data) {
        Crow.clickGui.terminal.setLocation(data.get("terminalX").getAsInt(), data.get("terminalY").getAsInt());
        Crow.clickGui.terminal.setSize(data.get("width").getAsInt(), data.get("height").getAsInt());
        Crow.clickGui.terminal.opened = data.get("opened").getAsBoolean();
        Crow.clickGui.terminal.hidden = data.get("hidden").getAsBoolean();
    }

    public void saveConfig() {
        if (applyingConfig)
            return;
        this.config = getConfigAsJson();

        try (PrintWriter out = new PrintWriter(new FileWriter(cfgFile))) {
            out.write(config.toString());
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public void updateKeyStrokesSettings() {
        config.add("keystrokes", getKeystrokeAsJson());
        saveConfig();
    }

    public void updateCompactGuiPosition(int x, int y) {
        if (applyingConfig) {
            return;
        }
        if (config == null) {
            config = getConfigAsJson();
        }
        JsonObject clickGui = config.has("clickgui") ? config.get("clickgui").getAsJsonObject() : new JsonObject();
        clickGui.addProperty("compactX", x);
        clickGui.addProperty("compactY", y);
        config.add("clickgui", clickGui);
        saveConfig();
    }

    public void updateScoreboardPosition(int x, int y) {
        if (applyingConfig) {
            return;
        }
        if (config == null) {
            config = getConfigAsJson();
        }
        JsonObject hud = config.has("hud") ? config.get("hud").getAsJsonObject() : new JsonObject();
        hud.addProperty("scoreboardX", x);
        hud.addProperty("scoreboardY", y);
        config.add("hud", hud);
        saveConfig();
    }
}
