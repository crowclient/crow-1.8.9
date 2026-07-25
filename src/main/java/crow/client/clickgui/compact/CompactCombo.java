package crow.client.clickgui.compact;

import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class CompactCombo {

    private static final int HEADER_HEIGHT = 22;
    private static final int OPTION_HEIGHT = 20;
    private static final int DROPDOWN_PAD = 4;
    private static final int CORNER = 10;
    private static final ResourceLocation DROPDOWN_ARROW =
            RenderUtils.getResourcePath("/assets/crow/crowclickgui/arrow_down.png");

    private final ComboSetting<?> setting;
    private final Module mod;
    private boolean expanded;

    private final Animation expandAnim = new Animation(220, Animation::easeOutCubic);
    private final Animation hoverAnim = new Animation(160, Animation::easeOutCubic);

    private Animation[] optionHovers;

    int x, y, w, h;

    /**
     * Convert enum names like {@code PULSE_PINK} or {@code STRAFE_BOOST} to
     * Title Case ({@code Pulse Pink}, {@code Strafe Boost}) for display.
     * Falls back to the raw value for any non-enum option whose toString()
     * happens to already be mixed-case.
     */
    private static String titleCase(Object value) {
        if (value == null) return "";
        String raw = value.toString();
        if (raw.isEmpty()) return raw;
        // Heuristic: if the string is mostly already mixed-case (contains a
        // lowercase letter after the first character), leave it alone so
        // user-facing names like "Auto Tool" or "Right Clicker" aren't
        // mangled. Only retitle screaming-snake style names.
        boolean allUpperOrUnderscore = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLowerCase(c)) { allUpperOrUnderscore = false; break; }
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

    public CompactCombo(ComboSetting<?> setting, Module mod) {
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
        return HEADER_HEIGHT + DROPDOWN_PAD + (optionCount * OPTION_HEIGHT) + DROPDOWN_PAD;
    }

    private int pillWidth(String formattedValue) {
        return Math.max(108, (int) FontUtil.small.getStringWidth(formattedValue) + 32);
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        expandAnim.setTarget(expanded ? 1.0F : 0.0F);
        expandAnim.update();
        float expandAnimation = expandAnim.get();

        Object mode = setting.getMode();
        String value = mode == null ? "Unavailable" : titleCase(mode);
        int pillW = pillWidth(value);
        int pillX = x + w - pillW;
        int pillY = y;
        boolean hovered = mouseX >= pillX && mouseX <= pillX + pillW
                && mouseY >= pillY && mouseY <= pillY + HEADER_HEIGHT;
        hoverAnim.setTarget(hovered ? 1.0F : 0.0F);
        hoverAnim.update();
        float hoverAnimation = hoverAnim.get();

        FontUtil.small.drawSmoothString(setting.getName(), x, y + 6, palette.mutedText);

        int pillBase = CompactModuleCard.blendColor(palette.toggleOff, palette.card, 0.72F);
        int pillHover = CompactModuleCard.blendColor(palette.hoverCard, palette.card, 0.52F);
        int pillColor = CompactModuleCard.blendColor(pillBase, pillHover, hoverAnimation);
        RenderUtils.drawRoundedRectAA(pillX, pillY, pillX + pillW, pillY + HEADER_HEIGHT, CORNER, pillColor);

        // Flowing accent layer — visible on hover and while expanded.
        float gradientFactor = Math.max(expandAnimation, hoverAnimation);
        if (gradientFactor > 0.02F) {
            int highlightAlpha = Math.max(22, (int) (75 * gradientFactor));
            RenderUtils.drawFlowingGradientRoundedRect(
                    pillX + 1, pillY + 1, pillX + pillW - 1, pillY + HEADER_HEIGHT - 1,
                    CORNER - 1, highlightAlpha, 0
            );
        }

        // Selected value text — left-aligned with consistent left padding so
        // long names don't get clipped against the dropdown arrow.
        FontUtil.small.drawSmoothString(value, pillX + 10, pillY + 7, palette.titleText);
        drawAnimatedArrow(pillX + pillW - 14, pillY + 6, palette, expandAnimation);

        if (expandAnimation <= 0.02F) {
            return;
        }

        Object[] options = setting.getOptions();
        if (options == null || options.length == 0) {
            return;
        }

        int dropX = pillX;
        int dropY = y + HEADER_HEIGHT + DROPDOWN_PAD;
        int dropW = pillW;
        int rawDropH = options.length * OPTION_HEIGHT;
        int drawDropH = Math.max(1, (int) (rawDropH * expandAnimation));

        // Dropdown body — clearly distinct from the header pill via a darker
        // tint so the user sees where options start.
        int bodyColor = CompactModuleCard.blendColor(palette.background, palette.card, 0.55F);
        RenderUtils.drawGlassPanel(dropX, dropY, dropX + dropW, dropY + drawDropH + DROPDOWN_PAD * 2,
                CORNER, bodyColor,
                (int) (RenderUtils.GLASS_SHADOW_RAISED * expandAnimation));

        if (optionHovers == null || optionHovers.length != options.length) {
            optionHovers = new Animation[options.length];
            for (int i = 0; i < options.length; i++) {
                optionHovers[i] = new Animation(140, Animation::easeOutCubic);
            }
        }

        for (int i = 0; i < options.length; i++) {
            int optY = dropY + DROPDOWN_PAD + i * OPTION_HEIGHT;
            if (optY + OPTION_HEIGHT > dropY + drawDropH + DROPDOWN_PAD * 2) {
                break;
            }

            String optName = titleCase(options[i]);
            boolean selected = options[i].equals(setting.getMode());
            boolean optionHovered = mouseX >= dropX + 4 && mouseX <= dropX + dropW - 4
                    && mouseY >= optY && mouseY <= optY + OPTION_HEIGHT;
            optionHovers[i].setTarget(optionHovered ? 1.0F : 0.0F);
            optionHovers[i].update();
            float oHover = optionHovers[i].get();

            if (selected) {
                int themeRgb = crow.client.module.modules.client.GuiModule.getThemeColor(0) & 0x00FFFFFF;
                RenderUtils.drawRoundedRectAA(dropX + 4, optY + 1, dropX + dropW - 4, optY + OPTION_HEIGHT - 1,
                        7, 0x33000000 | themeRgb);
                RenderUtils.drawFlowingGradientRoundedRect(
                        dropX + 5, optY + 2, dropX + dropW - 5, optY + OPTION_HEIGHT - 2,
                        6, 95, 0);
            } else if (oHover > 0.02F) {
                int hoverAlpha = (int) (160 * oHover);
                int optHoverColor = CompactModuleCard.blendColor(palette.card, palette.hoverCard, 0.7F);
                RenderUtils.drawRoundedRectAA(dropX + 4, optY + 1, dropX + dropW - 4, optY + OPTION_HEIGHT - 1,
                        7, (hoverAlpha << 24) | (optHoverColor & 0x00FFFFFF));
            }

            int textColor = selected
                    ? palette.titleText
                    : CompactModuleCard.blendColor(palette.mutedText, palette.titleText,
                            Math.max(hoverAnimation * 0.2F, oHover * 0.6F));
            // Centered text within the option row — same alignment regardless
            // of selection state, so the row doesn't jump on click.
            FontUtil.small.drawCenteredSmoothString(optName, dropX + dropW / 2.0F, optY + 6, textColor);
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        Object mode = setting.getMode();
        String value = mode == null ? "Unavailable" : titleCase(mode);
        int pillW = pillWidth(value);
        int pillX = x + w - pillW;
        int pillY = y;

        if (button != 0) {
            expanded = false;
            return;
        }

        if (mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + HEADER_HEIGHT) {
            expanded = !expanded;
            return;
        }

        if (!expanded) {
            return;
        }

        Object[] options = setting.getOptions();
        if (options != null) {
            int dropX = pillX;
            // Match the option Y layout in draw(): dropY + DROPDOWN_PAD + i*OPTION_HEIGHT
            int dropY = y + HEADER_HEIGHT + DROPDOWN_PAD + DROPDOWN_PAD;
            for (int i = 0; i < options.length; i++) {
                int optY = dropY + i * OPTION_HEIGHT;
                if (mouseX >= dropX && mouseX <= dropX + pillW
                        && mouseY >= optY && mouseY <= optY + OPTION_HEIGHT) {
                    ((ComboSetting) setting).setMode((Enum) options[i]);
                    mod.guiButtonToggled(setting);
                    expanded = false;
                    return;
                }
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

    private void drawAnimatedArrow(int x, int y, CompactPalette palette, float animation) {
        if (DROPDOWN_ARROW == null) {
            FontUtil.small.drawSmoothString(animation > 0.5F ? "\u25B2" : "\u25BC", x, y + 1, palette.mutedText);
            return;
        }

        int size = 9;
        crow.client.utils.RenderUtils.bindSmoothIcon(DROPDOWN_ARROW);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + size / 2.0F, y + size / 2.0F, 0.0F);
        GlStateManager.rotate(180.0F * animation, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(-size / 2.0F, -size / 2.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.72F + (animation * 0.28F));
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, size, size, size, size);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
