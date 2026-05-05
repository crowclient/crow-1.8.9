package crow.client.module.modules.themes;

public class ThemeSundae extends ThemeModule {
    public ThemeSundae() {
        super("Sundae");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.6F,
                0xFFFF9AA2, 0xFFFFB7B2, 0xFFFFDAB9,
                0xFFE2B0D6, 0xFFC7A0DC, 0xFFFFB3C6);
    }
}
