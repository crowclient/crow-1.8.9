package crow.client.clickgui.crow.components;

import org.lwjgl.opengl.GL11;

import crow.client.clickgui.crow.ClickGui;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public class TickComponent extends SettingComponent {

    private static final int TRACK_OFF = 0xFF34343A;
    private static final int KNOB = 12;

    private final TickSetting setting;

    public TickComponent(Setting setting, ModuleComponent parent) {
        super(setting, parent);
        this.setting = (TickSetting) setting;
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, 22);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, 22);
        int sx = this.x + 5;
        int toggleWidth = 28;
        int toggleHeight = 14;
        int toggleX = x2 - toggleWidth - 8;
        int toggleY = y + (height - toggleHeight) / 2;
        int knobX = setting.isToggled() ? toggleX + toggleWidth - KNOB - 2 : toggleX + 2;
        int fillColor = setting.isToggled() ? ClickGui.getRainbowAtX(toggleX + toggleWidth / 2) : TRACK_OFF;

        FontUtil.normal.drawSmoothString(setting.getName(), sx, y + 7, 0xFFFFFFFF);
        RenderUtils.drawRoundedRect(toggleX, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, 7, fillColor);
        RenderUtils.drawRoundedRect(knobX, toggleY + 1, knobX + KNOB, toggleY + 13, 6, 0xFFF2F2F2);
    }

    @Override
    public void clicked(int x, int y, int button) {
        setting.toggle();
        moduleComponent.mod.guiButtonToggled(setting);
    }
}
