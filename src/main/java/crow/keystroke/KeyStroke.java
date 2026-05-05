package crow.keystroke;

public class KeyStroke {
    public static int x;
    public static int y;
    public static int currentColorNumber;
    public static boolean showMouseButtons;
    public static boolean enabled;
    public static boolean outline;
    public static boolean blurBackground;
    public static boolean glow;
    public static float size;
    public static float glowRadius;
    public static float glowIntensity;
    public static int glowColor;
    public static int backgroundOpacity;

    public KeyStroke() {
        x = 0;
        y = 0;
        currentColorNumber = 0;
        showMouseButtons = true;
        enabled = true;
        outline = false;
        blurBackground = true;
        glow = true;
        size = 1.0F;
        glowRadius = 10.0F;
        glowIntensity = 0.8F;
        glowColor = 0xFFAA44DD;
        backgroundOpacity = 155;
    }
}
