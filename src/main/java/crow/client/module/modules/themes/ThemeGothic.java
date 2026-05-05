package crow.client.module.modules.themes;

public class ThemeGothic extends ThemeModule {
    public ThemeGothic() {
        super("Gothic");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.4F,
                0xFFBDBDBD, 0xFF9E9E9E, 0xFF757575,
                0xFFB0B0B0, 0xFF8A8A8A, 0xFFC4C4C4);
    }
}
