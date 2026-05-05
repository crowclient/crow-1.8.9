package crow.client.clickgui.crow.components;

import crow.client.clickgui.crow.ClickGui;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

public class DoubleSliderComponent extends SettingComponent {

    private static final int TRACK = 0xFF2A2A2A;

    private DoubleSliderSetting setting;
    private boolean mouseDown;
    private Helping helping;

    public DoubleSliderComponent(Setting setting, ModuleComponent category) {
        super(setting, category);
        this.setting = (DoubleSliderSetting) setting;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        int x = this.x + 5;
        setDimensions(moduleComponent.getWidth() - 10, 30);
        int trackWidth = width - 10;

        if (mouseDown) {
            float value = (mouseX - x) / (float) trackWidth;
            value = (float) ((value * (setting.getMax())) + (setting.getMin() * (1 - value)));
            if (helping == Helping.Max)
                setting.setValueMax(value);
            else if (helping == Helping.Min)
                setting.setValueMin(value);
        }

        FontUtil.normal.drawSmoothString(setting.getName(), x, y + 4, 0xFFFFFFFF);
        String values = setting.getInputMin() + " - " + setting.getInputMax();
        FontUtil.small.drawSmoothString(values,
                x2 - 8 - (int) FontUtil.small.getStringWidth(values), y + 6, 0xFFBEBEC9);

        int boxY = y + 19;
        int boxHeight = 6;
        int percentMinOff = (int) (trackWidth * ((setting.getInputMin() - setting.getMin()) / (setting.getMax() - setting.getMin())));
        int percentMaxOff = (int) (trackWidth * ((setting.getInputMax() - setting.getMin()) / (setting.getMax() - setting.getMin())));

        RenderUtils.drawRoundedRect(x, boxY, x + trackWidth, boxY + boxHeight, 3, TRACK);

        if (percentMaxOff > percentMinOff) {
            int rainbow = ClickGui.getRainbowAtX(x + (percentMinOff + percentMaxOff) / 2);
            RenderUtils.drawRoundedRect(x + percentMinOff, boxY, x + percentMaxOff, boxY + boxHeight, 3, rainbow);
        }

        RenderUtils.drawRoundedRect(x + percentMinOff - 2, boxY - 2, x + percentMinOff + 2, boxY + boxHeight + 2, 2, 0xFFF3F3F5);
        RenderUtils.drawRoundedRect(x + percentMaxOff - 2, boxY - 2, x + percentMaxOff + 2, boxY + boxHeight + 2, 2, 0xFFF3F3F5);
    }

    @Override
    public void clicked(int mouseX, int mouseY, int button) {
        mouseDown = true;
        float percentageAcross = (mouseX - x) / (float) width;
        float percentangeMax = (float) ((setting.getInputMax() - setting.getMin()) / (setting.getMax() - setting.getMin())) + 0.01f;
        float percentangeMin = (float) ((setting.getInputMin() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        helping = Math.abs(percentageAcross - percentangeMax) < Math.abs(percentageAcross - percentangeMin) ? Helping.Max : Helping.Min;
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
        mouseDown = false;
    }

    public enum Helping {
        Min, Max;
    }
}
