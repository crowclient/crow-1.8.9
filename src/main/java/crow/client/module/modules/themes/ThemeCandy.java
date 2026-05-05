package crow.client.module.modules.themes;

public class ThemeCandy extends ThemeModule {
    public ThemeCandy() {
        super("Candy");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.8F,
                0xFFFF73B3, 0xFFFF968E, 0xFFFFB86B,
                0xFFFFD468, 0xFFFFF06A, 0xFFFFB28E);
    }
}
