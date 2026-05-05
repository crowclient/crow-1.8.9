package crow.client.module.modules.render;

import crow.client.module.Module;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;

public class Fullbright extends Module {

    private float originalGamma;
    private ComboSetting mode;
    public static boolean nightVision;

    public Fullbright() {
        super("Fullbright", ModuleCategory.render);
        this.registerSetting(mode = new ComboSetting("Mode", Mode.Gamma));
        this.registerSetting(new DescriptionSetting("No more darkness!"));
    }

    @Override
    public void postApplyConfig() {
        onEnable();
    }

    @Override
    public void onEnable() {
        switch ((Mode) mode.getMode()) {
        case Gamma:
            originalGamma = mc.gameSettings.gammaSetting;
            mc.gameSettings.gammaSetting = 100;
            break;
        case NightVision:
            nightVision = true;
            break;
        }
    }

    @Override
    public void onDisable() {
        revertChanges((Mode) mode.getMode());
    }

    public void revertChanges(Mode mode) {
        switch (mode) {
        case Gamma:
            mc.gameSettings.gammaSetting = originalGamma > 10 ? 1 : originalGamma;
            break;
        case NightVision:
            nightVision = false;
            break;
        }
    }

    @Override
    public void guiButtonToggled(Setting b) {
        if (b == mode) {
            revertChanges((Mode) mode.getPrevMode());
            onEnable();
        }
    }

    public enum Mode {
        Gamma, NightVision
    }
}
