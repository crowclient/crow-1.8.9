package crow.client.module.modules.themes;

import crow.client.module.setting.impl.RGBSetting;

public class ThemeCustom extends ThemeModule {
    private final RGBSetting color;

    public ThemeCustom() {
        super("Custom");
        this.registerSetting(color = new RGBSetting("Color", 120, 80, 255));
    }

    @Override
    public int getColor(int delay) {
        return 0xFF000000 | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }
}
