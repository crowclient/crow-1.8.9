package crow.keystroke;

public class KeyStroke {
    public static int x;
    public static int y;
    public static int currentColorNumber;
    public static boolean showMouseButtons;
    public static boolean enabled;
    public static boolean outline;
    public static boolean blurBackground;
    public static float size;
    public static int backgroundOpacity;

    public KeyStroke() {
        x = 0;
        y = 0;
        currentColorNumber = 0;
        showMouseButtons = true;
        enabled = true;
        outline = false;
        blurBackground = true;
        size = 1.0F;
        backgroundOpacity = 155;
    }
}
