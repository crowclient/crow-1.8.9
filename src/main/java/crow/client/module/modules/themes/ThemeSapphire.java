package crow.client.module.modules.themes;

public class ThemeSapphire extends ThemeModule {
    public ThemeSapphire() {
        super("Sapphire");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.7F,
                0xFF60A5FA, 0xFF4384F3, 0xFF2563EB,
                0xFF2E90F2, 0xFF38BDF8, 0xFF4CB1FA);
    }
}
