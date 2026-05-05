package crow.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.google.gson.JsonObject;

import crow.client.clickgui.crow.components.ModuleComponent;
import crow.client.main.Crow;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.notifications.NotificationRenderer;
import crow.client.utils.Utils;
import net.minecraft.client.Minecraft;

public class Module {
    public static final int MOUSE_BIND_OFFSET = 256;

    public static boolean revealHiddenModules = false;

    protected ArrayList<Setting> settings;
    private final String moduleName;
    private final ModuleCategory moduleCategory;
    protected boolean hasBind = true, showInHud = true, clientConfig, enabled;
    protected boolean defaultEnabled = enabled;
    protected boolean hidden = false;
    protected int keycode;
    protected int defualtKeyCode = keycode;

    protected ModuleComponent component;

    protected static Minecraft mc;
    private boolean isToggled;

    private String description = "";

    protected boolean registered;

    public void guiUpdate() {

    }

    public Module(String name, ModuleCategory moduleCategory) {
        this.moduleName = name;
        this.moduleCategory = moduleCategory;
        this.settings = new ArrayList<>();
        mc = Minecraft.getMinecraft();
    }

    protected <E extends Module> E withKeycode(int i) {
        this.keycode = i;
        this.defualtKeyCode = i;
        return (E) this;
    }

    protected <E extends Module> E withEnabled(boolean i) {
        this.enabled = i;
        this.defaultEnabled = i;
        try {
            setToggled(i);
        } catch (Exception e) {
        }
        return (E) this;
    }

    public <E extends Module> E withDescription(String i) {
        this.description = i;
        return (E) this;
    }

    public <E extends Module> E withHidden(boolean h) {
        this.hidden = h;
        return (E) this;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public String getDescription() {
        if (description != null && !description.trim().isEmpty()) {
            return description.trim();
        }
        return generateFallbackDescription();
    }

    public String getShortDescription() {
        return getDescription();
    }

    private void playToggleSound() {
        if (crow.client.module.modules.client.GuiModule.toggleSounds()) {
            crow.client.utils.SoundUtils.playSound("click");
        }
    }

    public JsonObject getConfigAsJson() {
        JsonObject settings = new JsonObject();

        for (Setting setting : this.settings)
            if (setting != null) {
                JsonObject settingData = setting.getConfigAsJson();
                settings.add(setting.settingName, settingData);
            }

        JsonObject data = new JsonObject();
        data.addProperty("enabled", enabled);
        if (hasBind)
            data.addProperty("keycode", keycode);
        data.addProperty("showInHud", showInHud);
        data.add("settings", settings);

        return data;
    }

    public void applyConfigFromJson(JsonObject data) {
        try {
            if (hasBind)
                this.keycode = data.get("keycode").getAsInt();
            setToggled(data.get("enabled").getAsBoolean());
            JsonObject settingsData = data.get("settings").getAsJsonObject();
            for (Setting setting : getSettings())
                if (settingsData.has(setting.getName()))
                    setting.applyConfigFromJson(settingsData.get(setting.getName()).getAsJsonObject());
            this.showInHud = data.get("showInHud").getAsBoolean();
        } catch (NullPointerException ignored) {

        }
        postApplyConfig();
    }

    public void postApplyConfig() {

    }

    public void keybind() {
        if ((this.keycode != 0) && this.canBeEnabled())
            if (!this.isToggled && isBindPressed()) {
                this.toggle();
                this.isToggled = true;
            } else if (!isBindPressed())
                this.isToggled = false;
    }

    public boolean canBeEnabled() {
        return true;
    }

    public boolean showInHud() {
        return showInHud;
    }

    public void enable() {
        if(!canBeEnabled() || enabled)
            return;

        try {
            this.onEnable();
            this.enabled = true;
            if (!registered) {
                Crow.eventBus.register(this);
                registered = true;
            }
            playToggleSound();
            NotificationRenderer.moduleStateChanged(this);
        } catch (Throwable throwable) {
            this.enabled = false;
            if (registered) {
                Crow.eventBus.unregister(this);
                registered = false;
            }
            handleToggleFailure("enable", throwable);
        }
    }

    public void disable() {
        if(!canBeEnabled() || !enabled)
            return;
        boolean wasRegistered = registered;
        try {
            if (registered) {
                Crow.eventBus.unregister(this);
                registered = false;
            }
            this.onDisable();
            this.enabled = false;
            playToggleSound();
            NotificationRenderer.moduleStateChanged(this);
        } catch (Throwable throwable) {
            this.enabled = false;
            registered = false;
            handleToggleFailure("disable", throwable);
            if (wasRegistered) {
                try {
                    Crow.eventBus.unregister(this);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void setToggled(boolean enabled) {
        if(!canBeEnabled())
            return;
        if (enabled)
            enable();
        else
            disable();
    }

    public boolean isBindable() {
        return hasBind;
    }

    public String getName() {
        return this.moduleName;
    }

    public String getHudSuffix() {
        for (Setting setting : this.settings) {
            if (setting instanceof ComboSetting) {
                Object mode = ((ComboSetting<?>) setting).getMode();
                if (mode != null) {
                    return toTitleCase(mode.toString());
                }
            }
        }
        return "";
    }

    protected static String toTitleCase(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        String spaced = raw.replace('_', ' ').trim();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(spaced.charAt(i - 1))) {
                sb.append(' ');
            }
            sb.append(c);
        }

        String[] words = sb.toString().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(' ');
            String w = words[i];
            if (w.length() <= 1) {
                result.append(w.toUpperCase());
            } else {
                result.append(Character.toUpperCase(w.charAt(0)))
                      .append(w.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    public ArrayList<Setting> getSettings() {
        return this.settings;
    }

    public Setting getSettingByName(String name) {
        for (Setting setting : this.settings)
            if (setting.getName().equalsIgnoreCase(name))
                return setting;
        return null;
    }

    public void registerSetting(Setting Setting) {
        this.settings.add(Setting);
    }

    public void registerSettings(Setting... settings) {
        Collections.addAll(this.settings, settings);
    }

    public void setVisableInHud(boolean vis) {
        this.showInHud = vis;
    }

    public ModuleCategory moduleCategory() {
        return this.moduleCategory;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void toggle() {
        if (this.enabled)
            this.disable();
        else
            this.enable();
    }

    public void guiButtonToggled(Setting b) {

    }

    public int getKeycode() {
        return this.keycode;
    }

    public void setbind(int keybind) {
        this.keycode = keybind;
    }

    public void resetToDefaults() {
        this.keycode = defualtKeyCode;
        this.setToggled(defaultEnabled);

        for (Setting setting : this.settings)
            setting.resetToDefaults();
    }

    public void setModuleComponent(ModuleComponent component) {
        this.component = component;
    }

    public void onGuiClose() {

    }

    public String getBindAsString() {
        if (keycode == 0) {
            return "None";
        }
        if (isMouseBind()) {
            return "M" + (keycode - MOUSE_BIND_OFFSET + 1);
        }
        return Keyboard.getKeyName(keycode);
    }

    public void clearBinds() {
        this.keycode = 0;
    }

    public boolean isClientConfig() {
        return clientConfig;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void unRegister() {
        if(registered) {
            registered = false;
            Crow.eventBus.unregister(this);
            onDisable();
        }
    }

    private void handleToggleFailure(String action, Throwable throwable) {
        try { System.err.println("Failed to " + action + " module '" + getName() + "'"); } catch (Throwable ignored) {}
        try { throwable.printStackTrace(); } catch (Throwable ignored) {}

        try {
            Utils.Player.sendMessageToSelf("&c" + getName() + " failed to " + action + ". Check the log for details.");
        } catch (Throwable ignored) {
        }
    }

    private boolean isBindPressed() {
        if (isMouseBind()) {
            int mouseButton = keycode - MOUSE_BIND_OFFSET;
            return mouseButton >= 0 && Mouse.isButtonDown(mouseButton);
        }
        return Keyboard.isKeyDown(this.keycode);
    }

    private boolean isMouseBind() {
        return keycode >= MOUSE_BIND_OFFSET;
    }

    private String generateFallbackDescription() {
        String lowerName = getName().toLowerCase();
        if (lowerName.contains("aim")) return "Smoothly helps line up your aim on valid targets.";
        if (lowerName.contains("aura")) return "Automatically attacks nearby targets with your configured rules.";
        if (lowerName.contains("reach")) return "Randomizes your hit distance within a safe min and max range.";
        if (lowerName.contains("velocity")) return "Tweaks incoming knockback so fights feel more controllable.";
        if (lowerName.contains("click")) return "Automates clicks while keeping timing configurable.";
        if (lowerName.contains("esp")) return "Highlights entities or blocks so they are easier to track.";
        if (lowerName.contains("hud")) return "Draws extra information directly onto your screen.";
        if (lowerName.contains("speed")) return "Changes your movement speed with adjustable behavior.";
        if (lowerName.contains("sprint")) return "Keeps sprinting behavior consistent while you move.";
        if (lowerName.contains("fly")) return "Lets you move through the air using the selected fly mode.";
        if (lowerName.contains("blink")) return "Queues packets so your movement is released in bursts later.";
        if (lowerName.contains("scaffold")) return "Places blocks under you automatically while you bridge.";
        if (lowerName.contains("armor") || lowerName.contains("armour")) return "Equips stronger armor pieces without manual inventory work.";
        if (lowerName.contains("config")) return "Imports, exports, and manages your saved client setups.";

        return moduleCategory == null
                ? "Configure " + getName() + " behavior."
                : moduleCategory.formatFallbackDescription(getName());
    }

    public enum ModuleCategory {
        category(true, null, "Crow", "Configure %s behavior."),
        search(false, category, "Search", "Configure %s behavior."),
        combat(false, category, "Combat", "Improves %s combat behavior with configurable checks."),
        movement(false, category, "Movement", "Adjusts %s movement handling to fit your playstyle."),
        player(false, category, "Player", "Automates %s player actions when conditions are met."),
        world(false, category, "World", "Changes how %s interacts with the world around you."),
        render(false, category, "Render", "Customizes how %s appears on your screen."),
        other(false, category, "Other", "Adds a utility feature for %s."),
        client(false, category, "Client", "Applies %s client-side quality-of-life behavior."),
        hotkey(false, category, "Hotkey", "Configure %s behavior."),
        config(false, category, "Configs", "Manages %s configuration data and presets."),
        themes(false, category, "Themes", "Configure %s behavior.");

        private final boolean defaultShown;
        private final ModuleCategory topCategory;
        private final String name;
        private final String fallbackDescriptionTemplate;
        private List<ModuleCategory> childCategories = new ArrayList<ModuleCategory>();

        ModuleCategory(boolean defaultShown, ModuleCategory topCategory, String name, String fallbackDescriptionTemplate) {
            if(topCategory != null)
                topCategory.addChildCategory(this);
            this.defaultShown = defaultShown;
            this.topCategory = topCategory;
            this.name = name;
            this.fallbackDescriptionTemplate = fallbackDescriptionTemplate;
        }

        public String formatFallbackDescription(String moduleName) {
            return String.format(fallbackDescriptionTemplate, moduleName);
        }

        public void addChildCategory(ModuleCategory moduleCategory) {
            childCategories.add(moduleCategory);
        }

        public List<ModuleCategory> getChildCategories() {
            return childCategories;
        }

        public String getName() {
            return name;
        }

        public boolean isShownByDefault() {
            return defaultShown;
        }

        public ModuleCategory getParentCategory() {
            return topCategory;
        }
    }
}
