package crow.client.module.modules.themes;

public class ThemePink extends ThemeModule {
    public ThemePink() {
        super("Pink");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.7F,
                0xFFFF85C0, 0xFFFA7BBB, 0xFFF472B6,
                0xFFF78DC5, 0xFFF9A8D4, 0xFFFCA6CA);
    }
}
