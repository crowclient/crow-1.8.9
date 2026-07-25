package crow.client.module.modules.themes;

/**
 * A single muted periwinkle, deliberately static.
 *
 * <p>Every other theme here animates through a palette. This one does not:
 * the GUI uses the accent as a full-row flood for enabled modules, and a
 * colour that drifts while you read the list makes the "which of these is on"
 * scan harder, not easier. The tonal shift within a row comes from the
 * renderer's vertical sheen instead, so the dimension is there without the
 * colour moving.
 *
 * <p>Slightly desaturated on purpose — it is the only saturated colour in the
 * panel, so a neon violet would shout.
 */
public class ThemePeriwinkle extends ThemeModule {

    public ThemePeriwinkle() {
        super("Periwinkle");
    }

    @Override
    public int getColor(int delay) {
        return 0xFF7F7CEA;
    }
}
