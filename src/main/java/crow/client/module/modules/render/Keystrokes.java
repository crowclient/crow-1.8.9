package crow.client.module.modules.render;

import crow.client.module.Module;
import crow.client.module.modules.HUD;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.keystroke.KeyStroke;

public class Keystrokes extends Module {
    public static TickSetting showMouseButtons;
    public static TickSetting outline;
    public static ComboSetting colorMode;
    public static SliderSetting size;
    public static SliderSetting backgroundOpacity;

    private enum KeystrokeColor {
        WHITE,
        RED,
        GREEN,
        BLUE,
        YELLOW,
        PURPLE,
        RAINBOW
    }

    public Keystrokes() {
        super("Keystrokes", ModuleCategory.render);
        this.registerSetting(new DescriptionSetting("Shows your movement keys on screen."));
        this.registerSetting(new DescriptionSetting("Open chat to drag the overlay."));
        this.registerSetting(showMouseButtons = new TickSetting("Show mouse buttons", KeyStroke.showMouseButtons));
        this.registerSetting(outline = new TickSetting("Outline", KeyStroke.outline));
        this.registerSetting(size = new SliderSetting("Size", KeyStroke.size, 0.6D, 1.8D, 0.05D));
        this.registerSetting(backgroundOpacity = new SliderSetting("Background opacity", KeyStroke.backgroundOpacity, 40.0D, 255.0D, 1.0D));
        this.registerSetting(colorMode = new ComboSetting("Text color", getCurrentColor()));
        this.clientConfig = true;
    }

    @Override
    public void onEnable() {
        KeyStroke.enabled = true;
        syncSettingsToOverlay();
    }

    @Override
    public void onDisable() {
        KeyStroke.enabled = false;
    }

    @Override
    public void guiUpdate() {
        syncSettingsToOverlay();
    }

    @Override
    public void guiButtonToggled(crow.client.module.setting.Setting setting) {
        syncSettingsToOverlay();
    }

    @Override
    public void postApplyConfig() {
        syncSettingsToOverlay();
    }

    private void syncSettingsToOverlay() {
        KeyStroke.showMouseButtons = showMouseButtons.isToggled();
        KeyStroke.outline = outline.isToggled();
        KeyStroke.blurBackground = HUD.enableBlur != null && HUD.enableBlur.isToggled();
        KeyStroke.size = (float) size.getInput();
        KeyStroke.backgroundOpacity = (int) backgroundOpacity.getInput();
        KeyStroke.currentColorNumber = ((KeystrokeColor) colorMode.getMode()).ordinal();
    }

    private KeystrokeColor getCurrentColor() {
        int index = Math.max(0, Math.min(KeystrokeColor.values().length - 1, KeyStroke.currentColorNumber));
        return KeystrokeColor.values()[index];
    }
}
