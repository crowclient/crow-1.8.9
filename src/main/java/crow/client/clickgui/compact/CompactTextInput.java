package crow.client.clickgui.compact;

import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.TextSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

public class CompactTextInput {

    private static final int HEADER_HEIGHT = 14;
    private static final int FIELD_HEIGHT = 20;
    public static final int TOTAL_HEIGHT = HEADER_HEIGHT + FIELD_HEIGHT + 4;

    private final TextSetting setting;
    private boolean focused;

    private static CompactTextInput activeInput;

    int x, y, w, h;

    public CompactTextInput(TextSetting setting) {
        this.setting = setting;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {

        FontUtil.small.drawSmoothString(setting.getName(), x, y + 2, palette.mutedText);

        int fieldY = y + HEADER_HEIGHT;
        int borderColor = focused ? GuiModule.accent() : palette.outline;
        int bgColor = CompactModuleCard.blendColor(palette.background, palette.card, 0.4F);

        RenderUtils.drawRoundedRectAA(x, fieldY, x + w, fieldY + FIELD_HEIGHT, 6, borderColor);
        RenderUtils.drawRoundedRectAA(x + 1, fieldY + 1, x + w - 1, fieldY + FIELD_HEIGHT - 1, 5, bgColor);

        String displayText = setting.getValue();
        boolean showPlaceholder = displayText.isEmpty() && !focused;
        int textPad = 8;
        int maxTextWidth = w - textPad * 2;

        if (showPlaceholder) {
            FontUtil.small.drawSmoothString(setting.getPlaceholder(),
                    x + textPad, fieldY + (FIELD_HEIGHT - 8) / 2, palette.mutedText);
        } else {
            String trimmed = trimToFit(displayText, maxTextWidth);
            FontUtil.small.drawSmoothString(trimmed,
                    x + textPad, fieldY + (FIELD_HEIGHT - 8) / 2, palette.titleText);

            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cursorX = x + textPad + (int) FontUtil.small.getStringWidth(trimmed);
                Gui.drawRect(cursorX, fieldY + 4, cursorX + 1, fieldY + FIELD_HEIGHT - 4, palette.titleText);
            }
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        int fieldY = y + HEADER_HEIGHT;
        boolean inField = mouseX >= x && mouseX <= x + w
                && mouseY >= fieldY && mouseY <= fieldY + FIELD_HEIGHT;
        setFocused(inField);
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!focused) return false;

        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
            setFocused(false);
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            String current = setting.getValue();
            if (!current.isEmpty()) {
                setting.setValue(current.substring(0, current.length() - 1));
            }
            return true;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
            setting.setValue(setting.getValue() + typedChar);
            return true;
        }
        return true;
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

    public static boolean handleGlobalKeyTyped(char typedChar, int keyCode) {
        if (activeInput != null && activeInput.focused) {
            return activeInput.keyTyped(typedChar, keyCode);
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
