package crow.client.module.modules.themes;

public class ThemeWinter extends ThemeModule {
    public ThemeWinter() {
        super("Winter");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.45F,
                0xFFE3F2FD, 0xFFBBDEFB, 0xFFB3E5FC,
                0xFFE1F5FE, 0xFFD1E8FF, 0xFFC5CAE9);
    }
}
