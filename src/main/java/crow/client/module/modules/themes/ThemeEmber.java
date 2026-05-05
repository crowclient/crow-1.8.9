package crow.client.module.modules.themes;

public class ThemeEmber extends ThemeModule {
    public ThemeEmber() {
        super("Ember");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.7F,
                0xFFFF8A3D, 0xFFFF6B55, 0xFFFF4D6D,
                0xFFFF8B62, 0xFFFFC857, 0xFFFFA94A);
    }
}
