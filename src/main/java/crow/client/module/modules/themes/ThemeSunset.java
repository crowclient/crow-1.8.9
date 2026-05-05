package crow.client.module.modules.themes;

public class ThemeSunset extends ThemeModule {
    public ThemeSunset() {
        super("Sunset");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.7F,
                0xFFFF9A3C, 0xFFFF7D52, 0xFFFF5F6D,
                0xFFFF8969, 0xFFFFB76A, 0xFFFFD166, 0xFFFFB951);
    }
}
