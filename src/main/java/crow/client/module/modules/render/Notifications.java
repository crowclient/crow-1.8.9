package crow.client.module.modules.render;

import crow.client.module.Module;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;

public class Notifications extends Module {

    public enum Style { Modern, Minimal, Classic, Compact }
    public enum Position { TopCenter, TopRight, BottomRight }

    public static ComboSetting<Style> style;
    public static ComboSetting<Position> position;
    public static SliderSetting duration;
    public static TickSetting blur;
    public static TickSetting glow;
    public static TickSetting accentBar;

    public Notifications() {
        super("Notifications", ModuleCategory.render);
        this.registerSetting(style = new ComboSetting<>("Style", Style.Modern));
        this.registerSetting(position = new ComboSetting<>("Position", Position.TopCenter));
        this.registerSetting(duration = new SliderSetting("Duration", 1.0D, 0.5D, 5.0D, 0.5D));
        this.registerSetting(blur = new TickSetting("Blur", false));
        this.registerSetting(glow = new TickSetting("Glow", false));
        this.registerSetting(accentBar = new TickSetting("Accent Bar", true));
        accentBar.visibleWhen(() -> {
            Style s = style != null ? (Style) style.getMode() : Style.Modern;
            return s == Style.Classic;
        });
    }

    public static Style getStyle() {
        return style != null ? (Style) style.getMode() : Style.Modern;
    }

    public static Position getPosition() {
        return position != null ? (Position) position.getMode() : Position.TopCenter;
    }

    public static double getDuration() {
        return duration != null ? duration.getInput() : 1.0;
    }

    public static boolean useBlur() {
        return blur != null && blur.isToggled();
    }

    public static boolean useGlow() {
        return glow != null && glow.isToggled();
    }

    public static boolean useAccentBar() {
        return accentBar != null && accentBar.isToggled();
    }
}
