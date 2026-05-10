package crow.client.module.modules;

import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;

public class HUD extends Module {
    public static TickSetting editPosition, dropShadow, logo;
    public static TickSetting enableBlur, customChat;
    public static SliderSetting blurRadius;

    private static int hudX = 5;
    private static int hudY = 70;

    public static Utils.HUD.PositionMode positionMode;
    public static boolean showedError;
    public static boolean e;
    public static final String HUDX_prefix = "HUDX~ ";
    public static final String HUDY_prefix = "HUDY~ ";

    public HUD() {
        super("HUD", ModuleCategory.render);
        this.withDescription("Legacy HUD shell — settings only. The old arraylist renderer was removed; use ArrayListMod / Logo / Statistics instead.");

        this.registerSetting(editPosition = new TickSetting("Edit position", false));
        this.registerSetting(dropShadow = new TickSetting("Drop shadow", true));
        this.registerSetting(logo = new TickSetting("Logo", true));
        this.registerSetting(enableBlur = new TickSetting("Enable Blur", false));
        this.registerSetting(blurRadius = new SliderSetting("Blur Radius", 5, 1, 20, 1));
        blurRadius.visibleWhen(() -> enableBlur != null && enableBlur.isToggled());
        this.registerSetting(customChat = new TickSetting("Custom Chat", true));

        showedError = false;
        showInHud = false;
    }

    public static int getHudX() {
        return hudX;
    }

    public static int getHudY() {
        return hudY;
    }

    public static void setHudX(int hudX) {
        HUD.hudX = hudX;
    }

    public static void setHudY(int hudY) {
        HUD.hudY = hudY;
    }
}
