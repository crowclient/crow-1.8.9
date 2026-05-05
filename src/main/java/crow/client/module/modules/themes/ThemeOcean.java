package crow.client.module.modules.themes;

public class ThemeOcean extends ThemeModule {
    public ThemeOcean() {
        super("Ocean");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.7F,
                0xFF38BDF8, 0xFF22B1F0, 0xFF0EA5E9,
                0xFF10AEC8, 0xFF14B8A6, 0xFF26BBCF);
    }
}
