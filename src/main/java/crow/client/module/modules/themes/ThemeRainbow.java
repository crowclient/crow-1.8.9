package crow.client.module.modules.themes;

import crow.client.utils.Utils;

public class ThemeRainbow extends ThemeModule {
    public ThemeRainbow() {
        super("Rainbow");
        enable();
    }

    @Override
    public int getColor(int delay) {
        return Utils.Client.rainbowDraw(2L, delay);
    }
}
