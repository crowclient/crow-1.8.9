package crow.client.module.modules.themes;

public class ThemeSunkist extends ThemeModule {
    public ThemeSunkist() {
        super("Sunkist");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.65F,
                0xFFFFA726, 0xFFFF8F00, 0xFFFFB300,
                0xFFFFCA28, 0xFFFFD54F, 0xFFFFAB40);
    }
}
