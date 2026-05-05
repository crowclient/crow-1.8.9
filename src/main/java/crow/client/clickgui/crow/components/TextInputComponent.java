package crow.client.clickgui.crow.components;

import crow.client.clickgui.crow.ClickGui;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.TextSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

public class TextInputComponent extends SettingComponent {

    private static final int HEADER_H = 24;
    private static final int FIELD_H = 18;
    private static final int COLLAPSED_H = HEADER_H;
    private static final int EXPANDED_H = HEADER_H + FIELD_H + 4;

    private final TextSetting textSetting;
    private boolean focused;

    private static TextInputComponent activeInput;

    public TextInputComponent(Setting setting, ModuleComponent parent) {
        super(setting, parent);
        this.textSetting = (TextSetting) setting;
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, EXPANDED_H);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, EXPANDED_H);
        int sx = this.x + 5;

        FontUtil.normal.drawSmoothString(textSetting.getName(), sx, y + 4, 0xFFFFFFFF);

        int fieldX = sx;
        int fieldY = y + HEADER_H;
        int fieldW = CategoryComponent.PANEL_WIDTH - 20;
        int borderColor = focused ? ClickGui.getRainbowAtX(sx) : 0xFF444448;
        RenderUtils.drawRoundedRect(fieldX - 1, fieldY - 1, fieldX + fieldW + 1, fieldY + FIELD_H + 1, 4, borderColor);
        RenderUtils.drawRoundedRect(fieldX, fieldY, fieldX + fieldW, fieldY + FIELD_H, 3, 0xFF1A1A1E);

        String displayText = textSetting.getValue();
        boolean showPlaceholder = displayText.isEmpty() && !focused;
        if (showPlaceholder) {
            FontUtil.small.drawSmoothString(textSetting.getPlaceholder(),
                    fieldX + 4, fieldY + (FIELD_H - 8) / 2, 0xFF666666);
        } else {

            String trimmed = trimToFit(displayText, fieldW - 12);
            FontUtil.small.drawSmoothString(trimmed,
                    fieldX + 4, fieldY + (FIELD_H - 8) / 2, 0xFFEEEEEE);

            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cursorX = fieldX + 4 + (int) FontUtil.small.getStringWidth(trimmed);
                Gui.drawRect(cursorX, fieldY + 3, cursorX + 1, fieldY + FIELD_H - 3, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public void clicked(int mouseX, int mouseY, int button) {
        int fieldX = this.x + 5;
        int fieldY = y + HEADER_H;
        int fieldW = CategoryComponent.PANEL_WIDTH - 20;

        if (mouseX >= fieldX && mouseX <= fieldX + fieldW
                && mouseY >= fieldY && mouseY <= fieldY + FIELD_H) {
            setFocused(true);
        } else {
            setFocused(false);
        }
    }

    @Override
    public void keyTyped(char t, int k) {
        if (!focused) return;

        if (k == Keyboard.KEY_ESCAPE || k == Keyboard.KEY_RETURN) {
            setFocused(false);
            return;
        }
        if (k == Keyboard.KEY_BACK) {
            String current = textSetting.getValue();
            if (!current.isEmpty()) {
                textSetting.setValue(current.substring(0, current.length() - 1));
            }
            return;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(t)) {
            textSetting.setValue(textSetting.getValue() + t);
        }
    }

    private void setFocused(boolean f) {
        if (f) {
            if (activeInput != null && activeInput != this) {
                activeInput.focused = false;
            }
            activeInput = this;
        } else if (activeInput == this) {
            activeInput = null;
        }
        this.focused = f;
    }

    public static boolean isAnyFocused() {
        return activeInput != null && activeInput.focused;
    }

    public static boolean handleGlobalKeyTyped(char t, int k) {
        if (activeInput != null && activeInput.focused) {
            activeInput.keyTyped(t, k);
            return true;
        }
        return false;
    }

    private String trimToFit(String text, int maxWidth) {
        if (FontUtil.small.getStringWidth(text) <= maxWidth) return text;
        while (text.length() > 1 && FontUtil.small.getStringWidth("..." + text) > maxWidth) {
            text = text.substring(1);
        }
        return "..." + text;
    }
}
