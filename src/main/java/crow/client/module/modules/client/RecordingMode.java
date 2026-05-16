package crow.client.module.modules.client;

import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.TickSetting;

public class RecordingMode extends Module {

    public static TickSetting disableBlur;
    public static TickSetting disableStencil;

    private static boolean active = false;

    public RecordingMode() {
        super("Recording Mode", ModuleCategory.client);
        this.registerSetting(new DescriptionSetting("Replaces GPU effects with"));
        this.registerSetting(new DescriptionSetting("lightweight fallbacks for OBS."));
        this.registerSetting(new DescriptionSetting("Use Window Capture in OBS."));
        this.registerSetting(disableBlur = new TickSetting("No blur", true));
        this.registerSetting(disableStencil = new TickSetting("No stencil", true));
    }

    @Override
    public void onEnable() {
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean shouldSkipBlur() {
        return active && (disableBlur == null || disableBlur.isToggled());
    }

    public static boolean shouldSkipStencil() {
        return active && (disableStencil == null || disableStencil.isToggled());
    }
}
