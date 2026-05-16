package crow.client.clickgui.spacious;

import crow.client.clickgui.compact.CompactModuleCard;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

/**
 * Spacious-mode combo. Renders as a section-divider strip: dimly tinted
 * background with the setting name and current value joined by a dash
 * ("Target Mode — Distance"), echoing the screenshot mock-up. Click to
 * open a dropdown below that lists all options. Click an option to pick
 * it; click the strip again or any option to close.
 */
public class SpaciousCombo {

    public static final int HEADER_HEIGHT = 18;
    public static final int OPTION_HEIGHT = 16;
    private static final int DROPDOWN_PAD = 3;

    private final ComboSetting<?> setting;
    private final Module mod;
    private boolean expanded;

    int x, y, w, h;

    private final Animation expandAnim = new Animation(220, Animation::easeOutCubic);
    private Animation[] optionHovers;

    public SpaciousCombo(ComboSetting<?> setting, Module mod) {
        this.setting = setting;
        this.mod = mod;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public int getCurrentHeight() {
        if (!expanded) return HEADER_HEIGHT;
        Object[] options = setting.getOptions();
        int optionCount = options != null ? options.length : 0;
        return HEADER_HEIGHT + DROPDOWN_PAD + optionCount * OPTION_HEIGHT + DROPDOWN_PAD;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        expandAnim.setTarget(expanded ? 1.0F : 0.0F);
        expandAnim.update();
        float expandT = expandAnim.get();

        Object mode = setting.getMode();
        String value = mode == null ? "Unavailable" : titleCase(mode);
        boolean hovered = mouseX >= x && mouseX <= x + w
                && mouseY >= y && mouseY <= y + HEADER_HEIGHT;

        // Strip background — subtly tinted card hover blend so it reads
        // as a clickable header rather than blending into the module card.
        int stripColor = CompactModuleCard.blendColor(palette.toggleOff, palette.card,
                hovered ? 0.40F : 0.70F);
        RenderUtils.drawRoundedRectAA(x, y, x + w, y + HEADER_HEIGHT, 4, stripColor);

        // "Name — Value" composed inline, centered.
        String label = setting.getName() + "  —  " + value;
        int labelW = (int) FontUtil.small.getStringWidth(label);
        int textX = x + (w - labelW) / 2;
        int textY = y + (HEADER_HEIGHT - 8) / 2;
        FontUtil.small.drawSmoothString(label, textX, textY, palette.titleText);

        if (expandT <= 0.02F) return;

        Object[] options = setting.getOptions();
        if (options == null || options.length == 0) return;

        int dropY = y + HEADER_HEIGHT + 1;
        int rawDropH = options.length * OPTION_HEIGHT + DROPDOWN_PAD * 2;
        int drawDropH = Math.max(1, (int) (rawDropH * expandT));

        int bodyColor = CompactModuleCard.blendColor(palette.background, palette.card, 0.45F);
        RenderUtils.drawRoundedRectAA(x, dropY, x + w, dropY + drawDropH, 4, bodyColor);

        if (optionHovers == null || optionHovers.length != options.length) {
            optionHovers = new Animation[options.length];
            for (int i = 0; i < options.length; i++) {
                optionHovers[i] = new Animation(140, Animation::easeOutCubic);
            }
        }

        int themeColor = 0xFF000000 | (GuiModule.getThemeColor(0) & 0x00FFFFFF);
        for (int i = 0; i < options.length; i++) {
            int optY = dropY + DROPDOWN_PAD + i * OPTION_HEIGHT;
            if (optY + OPTION_HEIGHT > dropY + drawDropH) break;

            boolean selected = options[i].equals(setting.getMode());
            boolean oHover = mouseX >= x + 4 && mouseX <= x + w - 4
                    && mouseY >= optY && mouseY <= optY + OPTION_HEIGHT;
            optionHovers[i].setTarget(oHover ? 1.0F : 0.0F);
            optionHovers[i].update();
            float oh = optionHovers[i].get();

            if (selected) {
                int selBg = CompactModuleCard.blendColor(palette.card, themeColor, 0.40F);
                RenderUtils.drawRoundedRectAA(x + 4, optY + 1, x + w - 4, optY + OPTION_HEIGHT - 1,
                        3, selBg);
            } else if (oh > 0.02F) {
                int hoverAlpha = (int) (120 * oh);
                int hoverCol = CompactModuleCard.blendColor(palette.card, palette.hoverCard, 0.65F);
                RenderUtils.drawRoundedRectAA(x + 4, optY + 1, x + w - 4, optY + OPTION_HEIGHT - 1,
                        3, (hoverAlpha << 24) | (hoverCol & 0x00FFFFFF));
            }

            int textColor = selected ? palette.titleText
                    : CompactModuleCard.blendColor(palette.mutedText, palette.titleText, oh * 0.6F);
            FontUtil.small.drawCenteredSmoothString(titleCase(options[i]),
                    x + w / 2.0F, optY + (OPTION_HEIGHT - 8) / 2.0F, textColor);
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            expanded = false;
            return;
        }

        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + HEADER_HEIGHT) {
            expanded = !expanded;
            return;
        }

        if (!expanded) return;

        Object[] options = setting.getOptions();
        if (options == null) {
            expanded = false;
            return;
        }
        int dropY = y + HEADER_HEIGHT + 1 + DROPDOWN_PAD;
        for (int i = 0; i < options.length; i++) {
            int optY = dropY + i * OPTION_HEIGHT;
            if (mouseX >= x && mouseX <= x + w
                    && mouseY >= optY && mouseY <= optY + OPTION_HEIGHT) {
                ((ComboSetting) setting).setMode((Enum) options[i]);
                mod.guiButtonToggled(setting);
                expanded = false;
                return;
            }
        }
        expanded = false;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void closeDropdown() {
        expanded = false;
    }

    private static String titleCase(Object value) {
        if (value == null) return "";
        String raw = value.toString();
        if (raw.isEmpty()) return raw;
        boolean allUpperOrUnderscore = true;
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isLowerCase(raw.charAt(i))) { allUpperOrUnderscore = false; break; }
        }
        String spaced = raw.replace('_', ' ');
        if (!allUpperOrUnderscore) return spaced;
        StringBuilder sb = new StringBuilder(spaced.length());
        boolean wordStart = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(c);
                wordStart = true;
            } else if (wordStart) {
                sb.append(Character.toUpperCase(c));
                wordStart = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
