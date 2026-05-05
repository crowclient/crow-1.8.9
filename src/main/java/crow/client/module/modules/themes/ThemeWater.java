package crow.client.module.modules.themes;

public class ThemeWater extends ThemeModule {
    public ThemeWater() {
        super("Water");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.55F,
                0xFF00BCD4, 0xFF009688, 0xFF26C6DA,
                0xFF4DD0E1, 0xFF00ACC1, 0xFF0097A7);
    }
}
