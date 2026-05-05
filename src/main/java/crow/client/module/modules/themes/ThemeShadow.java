package crow.client.module.modules.themes;

public class ThemeShadow extends ThemeModule {
    public ThemeShadow() {
        super("Shadow");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.5F,
                0xFF7C4DFF, 0xFF651FFF, 0xFF536DFE,
                0xFF8C6FFF, 0xFF6A3DE8, 0xFF7E57C2);
    }
}
