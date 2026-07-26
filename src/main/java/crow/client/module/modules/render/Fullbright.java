package crow.client.module.modules.render;

import crow.client.module.Module;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;

public class Fullbright extends Module {

    private float originalGamma;
    private boolean gammaCaptured;
    private Mode activeMode;
    private ComboSetting mode;
    public static boolean nightVision;

    public Fullbright() {
        super("Fullbright", ModuleCategory.render);
        this.registerSetting(mode = new ComboSetting("Mode", Mode.Gamma));
        this.registerSetting(new DescriptionSetting("No more darkness!"));
    }

    @Override
    public void postApplyConfig() {
        if (isEnabled()) {
            applyMode((Mode) mode.getMode());
        }
    }

    @Override
    public void onEnable() {
        applyMode((Mode) mode.getMode());
    }

    @Override
    public void onDisable() {
        revertActiveMode();
    }

    public void revertChanges(Mode mode) {
        if (activeMode == mode) {
            revertActiveMode();
        }
    }

    private void applyMode(Mode nextMode) {
        if (!isEnabled() || nextMode == null || activeMode == nextMode)
            return;

        revertActiveMode();
        activeMode = nextMode;
        switch (nextMode) {
        case Gamma:
            originalGamma = mc.gameSettings.gammaSetting;
            gammaCaptured = true;
            mc.gameSettings.gammaSetting = 100;
            break;
        case NightVision:
            nightVision = true;
            break;
        }
    }

    private void revertActiveMode() {
        Mode previousMode = activeMode;
        activeMode = null;

        if (previousMode == Mode.Gamma) {
            if (gammaCaptured) {
                mc.gameSettings.gammaSetting = originalGamma;
                gammaCaptured = false;
            }
        } else if (previousMode == Mode.NightVision) {
            nightVision = false;
        }
    }

    @Override
    public void guiButtonToggled(Setting b) {
        if (b == mode && isEnabled()) {
            applyMode((Mode) mode.getMode());
        }
    }

    public enum Mode {
        Gamma, NightVision
    }
}
