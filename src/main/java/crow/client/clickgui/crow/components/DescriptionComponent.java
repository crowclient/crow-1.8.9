package crow.client.clickgui.crow.components;

import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.utils.font.FontUtil;

public class DescriptionComponent extends SettingComponent {

    private DescriptionSetting setting;

    public DescriptionComponent(Setting setting, ModuleComponent category) {
        super(setting, category);
        this.setting = (DescriptionSetting) setting;
    }

    @Override
    public void draw(int mouseX, int mouseY) {

        int x = this.x + 5;
        setDimensions(moduleComponent.getWidth(), 14);
        FontUtil.small.drawSmoothString(setting.getDesc(), x, y + 4, 0xFFB08BE0);
    }

}
