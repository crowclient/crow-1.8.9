package crow.client.clickgui.crow.components;

import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ButtonSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

public class ButtonComponent extends SettingComponent {
    private final ButtonSetting setting;

    public ButtonComponent(Setting setting, ModuleComponent category) {
        super(setting, category);
        this.setting = (ButtonSetting) setting;
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, 24);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, 24);
        RenderUtils.drawRoundedRect(x + 4, y + 4, x2 - 4, y + height - 4, 4, 0x44404A58);
        FontUtil.normal.drawCenteredSmoothString(setting.getName(), (x + x2) / 2.0F, y + 8, 0xFFFFFFFF);
    }

    @Override
    public void clicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            setting.press();
        }
    }

}
