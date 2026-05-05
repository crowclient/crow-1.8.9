package crow.client.clickgui.crow.components;

import crow.client.clickgui.crow.ClickGui;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;

public class SliderComponent extends SettingComponent {

    private static final int TRACK = 0xFF2A2A2A;
    private static final int KNOB = 8;

    private final SliderSetting setting;
    private boolean dragging;

    public SliderComponent(Setting setting, ModuleComponent parent) {
        super(setting, parent);
        this.setting = (SliderSetting) setting;
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, 28);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, 28);
        int sx = this.x + 5;
        int trackWidth = width - 10;

        if (dragging) {
            float p = Math.max(0, Math.min(1, (mouseX - sx) / (float) trackWidth));
            setting.setValue((float) (setting.getMin() + p * (setting.getMax() - setting.getMin())));
        }

        FontUtil.normal.drawSmoothString(setting.getName(), sx, y + 4, 0xFFFFFFFF);
        String value = String.valueOf(setting.getInput());
        FontUtil.small.drawSmoothString(value,
                x2 - 8 - (int) FontUtil.small.getStringWidth(value), y + 6, 0xFFBEBEC9);

        int barY = y + 18;
        int barH = 6;
        int fill = (int) (trackWidth * ((setting.getInput() - setting.getMin())
                / (setting.getMax() - setting.getMin())));

        RenderUtils.drawRoundedRect(sx, barY, sx + trackWidth, barY + barH, 3, TRACK);

        if (fill > 0) {
            int rainbow = ClickGui.getRainbowAtX(sx + fill / 2);
            RenderUtils.drawRoundedRect(sx, barY, sx + Math.min(fill, trackWidth), barY + barH, 3, rainbow);
        }

        int knobCenter = sx + Math.min(fill, trackWidth);
        RenderUtils.drawRoundedRect(knobCenter - KNOB / 2, barY - 3, knobCenter + KNOB / 2, barY + barH + 3, 4, 0xFFF3F3F5);
    }

    @Override
    public void clicked(int button, int x, int y) {
        dragging = true;
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
        dragging = false;
    }
}
