package crow.client.module.modules.themes;

public class ThemeLegacy extends ThemeModule {
    public ThemeLegacy() {
        super("Legacy");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.55F,
                0xFF64B5F6, 0xFF42A5F5, 0xFF90CAF9,
                0xFF7CB8F2, 0xFF5CA8E8, 0xFFBBDEFB);
    }
}
