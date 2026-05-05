package crow.client.clickgui.crow.components;

import crow.client.clickgui.crow.ClickGui;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;

public class ComboComponent extends SettingComponent {

    private static final int HEADER_H = 24;
    private static final int OPTION_H = 18;
    private static final int DROP_PAD = 3;

    private final ComboSetting setting;
    private boolean expanded;

    public ComboComponent(Setting setting, ModuleComponent parent) {
        super(setting, parent);
        this.setting = (ComboSetting) setting;
        updateDimensions();
    }

    private void updateDimensions() {
        if (expanded) {
            Object[] opts = setting.getOptions();
            int optCount = opts != null ? opts.length : 0;
            setDimensions(CategoryComponent.PANEL_WIDTH - 10, HEADER_H + DROP_PAD + (optCount * OPTION_H) + DROP_PAD);
        } else {
            setDimensions(CategoryComponent.PANEL_WIDTH - 10, HEADER_H);
        }
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        updateDimensions();
        int sx = this.x + 5;
        int valColor = ClickGui.getRainbowAtX(sx);
        String modeName = setting.getMode().name().replace('_', ' ');

        FontUtil.normal.drawSmoothString(setting.getName(), sx, y + 7, 0xFFFFFFFF);
        FontUtil.small.drawSmoothString(modeName,
                x2 - 18 - (int) FontUtil.small.getStringWidth(modeName), y + 8, valColor);

        String arrow = expanded ? "\u25B2" : "\u25BC";
        FontUtil.small.drawSmoothString(arrow, x2 - 12, y + 8, 0xFFAAAAAA);

        if (expanded) {
            Object[] opts = setting.getOptions();
            if (opts == null) return;

            int dropY = y + HEADER_H + DROP_PAD;

            RenderUtils.drawRoundedRect(sx - 2, dropY - 2, x2 - 3, dropY + (opts.length * OPTION_H) + 2, 5, 0xF0202024);

            for (int i = 0; i < opts.length; i++) {
                String optName = opts[i].toString().replace('_', ' ');
                int optY = dropY + i * OPTION_H;
                boolean hovered = mouseX >= sx && mouseX <= x2 - 3
                        && mouseY >= optY && mouseY <= optY + OPTION_H;
                boolean selected = opts[i].equals(setting.getMode());

                if (selected) {
                    int themeColor = ClickGui.getRainbowAtX(sx);
                    RenderUtils.drawRoundedRect(sx - 1, optY, x2 - 4, optY + OPTION_H, 3,
                            (themeColor & 0x00FFFFFF) | 0x44000000);
                } else if (hovered) {
                    RenderUtils.drawRoundedRect(sx - 1, optY, x2 - 4, optY + OPTION_H, 3, 0x22FFFFFF);
                }

                int textColor = selected ? 0xFFFFFFFF : 0xFFAAAAAA;
                FontUtil.small.drawSmoothString(optName, sx + 4, optY + (OPTION_H - 8) / 2, textColor);
            }
        }
    }

    @Override
    public void clicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            expanded = false;
            updateDimensions();
            return;
        }

        if (expanded) {

            Object[] opts = setting.getOptions();
            if (opts != null) {
                int sx = this.x + 5;
                int dropY = y + HEADER_H + DROP_PAD;

                for (int i = 0; i < opts.length; i++) {
                    int optY = dropY + i * OPTION_H;
                    if (mouseX >= sx && mouseX <= x2 - 3
                            && mouseY >= optY && mouseY <= optY + OPTION_H) {
                        setting.setMode((Enum) opts[i]);
                        moduleComponent.mod.guiButtonToggled(setting);
                        expanded = false;
                        updateDimensions();
                        return;
                    }
                }
            }

            expanded = false;
        } else {

            expanded = true;
        }
        updateDimensions();
    }
}
