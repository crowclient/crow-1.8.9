package crow.client.module.modules.themes;

public class ThemeMint extends ThemeModule {
    public ThemeMint() {
        super("Mint");
    }

    @Override
    public int getColor(int delay) {
        return flowingGradient(delay, 0.7F,
                0xFF9EF7D1, 0xFF7EF1D3, 0xFF5EEAD4,
                0xFF91F3E2, 0xFFC4FCEF, 0xFFB1FAE0);
    }
}
