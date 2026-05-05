package crow.client.module.modules.themes;

public class ThemeNord extends ThemeModule {
    public ThemeNord() {
        super("Nord");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.5F,
                0xFF88C0D0, 0xFF81A1C1, 0xFF5E81AC,
                0xFF8FBCBB, 0xFFA3BE8C, 0xFF7EB8B0);
    }
}
