package crow.client.module.modules.themes;

public class ThemePeony extends ThemeModule {
    public ThemePeony() {
        super("Peony");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.6F,
                0xFFCE93D8, 0xFFBA68C8, 0xFFE1BEE7,
                0xFFEA80FC, 0xFFD1A3E3, 0xFFF3C1F7);
    }
}
