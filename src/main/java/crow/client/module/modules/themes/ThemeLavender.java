package crow.client.module.modules.themes;

public class ThemeLavender extends ThemeModule {
    public ThemeLavender() {
        super("Lavender");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.65F,
                0xFFD6BCFA, 0xFFBEA4FA, 0xFFA78BFA,
                0xFFD099E7, 0xFFF9A8D4, 0xFFE8B2E7);
    }
}
