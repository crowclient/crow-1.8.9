package crow.client.module.modules.themes;

public class ThemeAurora extends ThemeModule {
    public ThemeAurora() {
        super("Aurora");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.75F,
                0xFF5BFFB1, 0xFF60F4D5, 0xFF67E8F9,
                0xFF8DB8FC, 0xFFB388FF, 0xFF87C4D8);
    }
}
