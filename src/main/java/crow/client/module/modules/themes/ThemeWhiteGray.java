package crow.client.module.modules.themes;

public class ThemeWhiteGray extends ThemeModule {
    public ThemeWhiteGray() {
        super("White/Gray");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.5F,
                0xFFFFFFFF, 0xFFE4E6E9, 0xFFC9CDD3,
                0xFFABB0B8, 0xFF8C939D, 0xFFC6C9CE);
    }
}
