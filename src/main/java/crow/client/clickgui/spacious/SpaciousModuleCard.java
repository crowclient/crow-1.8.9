package crow.client.clickgui.spacious;

import java.util.ArrayList;
import java.util.List;

import crow.client.clickgui.compact.CompactBind;
import crow.client.clickgui.compact.CompactHotbarLayout;
import crow.client.clickgui.compact.CompactModuleCard;
import crow.client.clickgui.compact.CompactTextInput;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ButtonSetting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.HotbarLayoutSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TextSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Animation;
import crow.client.utils.Icons;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

/**
 * Module card for the Spacious GUI. The whole card header is the click
 * target: left-click toggles the module, right-click expands the inline
 * settings panel.
 *
 * <p>Inside the expanded panel, settings render with the spacious-only
 * row components ({@link SpaciousSlider}, {@link SpaciousToggle},
 * {@link SpaciousCombo}, etc.) which use a flat, narrow row aesthetic
 * — title left, value/control right, thin progress tracks at the bottom
 * of slider rows. Rare types (text input, hotbar layout, keybind) fall
 * back to the Compact subcomponents for now.
 */
public class SpaciousModuleCard {

    public static final int CARD_HEIGHT = 22;
    /** Square: rows tile flush against each other and against the column
     *  edges, so there is no seam between a row and the panel behind it. */
    private static final int CARD_RADIUS = 0;
    private static final int SETTINGS_INDENT = 6;
    private static final int SETTING_GAP = 2;
    private static final int SETTING_TOP_PAD = 5;
    private static final int SETTING_BOT_PAD = 5;

    public final Module mod;
    private final SpaciousCategoryTab tab;

    private int x, y, w, h;
    private final Animation hoverAnim = new Animation(180, Animation::easeOutCubic);
    private final Animation enableAnim = new Animation(220, Animation::easeOutCubic);
    private final Animation expandAnim = new Animation(220, Animation::easeOutCubic);

    private final List<Object> settingComponents = new ArrayList<>();
    private CompactBind bindComponent;
    private boolean expanded;

    public SpaciousModuleCard(Module mod, SpaciousCategoryTab tab) {
        this.mod = mod;
        this.tab = tab;

        for (Setting setting : mod.getSettings()) {
            if (setting instanceof SliderSetting) {
                settingComponents.add(new SpaciousSlider((SliderSetting) setting));
            } else if (setting instanceof DoubleSliderSetting) {
                settingComponents.add(new SpaciousDoubleSlider((DoubleSliderSetting) setting));
            } else if (setting instanceof TickSetting) {
                settingComponents.add(new SpaciousToggle((TickSetting) setting, mod));
            } else if (setting instanceof ComboSetting) {
                settingComponents.add(new SpaciousCombo((ComboSetting<?>) setting, mod));
            } else if (setting instanceof ButtonSetting) {
                settingComponents.add(new SpaciousButton((ButtonSetting) setting));
            } else if (setting instanceof TextSetting) {
                settingComponents.add(new CompactTextInput((TextSetting) setting));
            } else if (setting instanceof HotbarLayoutSetting) {
                settingComponents.add(new CompactHotbarLayout((HotbarLayoutSetting) setting));
            }
        }
        if (mod.isBindable()) {
            bindComponent = new CompactBind(mod, null);
        }
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public boolean isExpanded() { return expanded; }

    public int getTotalHeight() {
        float anim = expandAnim.get();
        if (anim < 0.005F) return h;
        return h + (int) Math.round(getSettingsHeight() * anim);
    }

    private int getSettingsHeight() {
        int total = SETTING_TOP_PAD;
        for (Object sc : settingComponents) {
            total += getComponentHeight(sc) + SETTING_GAP;
        }
        if (bindComponent != null) total += 24 + SETTING_GAP;
        return total + SETTING_BOT_PAD;
    }

    private int getComponentHeight(Object sc) {
        if (sc instanceof SpaciousSlider) return SpaciousSlider.ROW_HEIGHT;
        if (sc instanceof SpaciousDoubleSlider) return SpaciousDoubleSlider.ROW_HEIGHT;
        if (sc instanceof SpaciousToggle) return SpaciousToggle.ROW_HEIGHT;
        if (sc instanceof SpaciousCombo) return ((SpaciousCombo) sc).getCurrentHeight();
        if (sc instanceof SpaciousButton) return SpaciousButton.ROW_HEIGHT;
        if (sc instanceof CompactTextInput) return CompactTextInput.TOTAL_HEIGHT;
        if (sc instanceof CompactHotbarLayout) return ((CompactHotbarLayout) sc).getHeight();
        return 18;
    }

    /** Short key label for the bind badge, or null when unbound. */
    private String bindLabel() {
        if (!mod.isBindable() || mod.getKeycode() == 0) return null;
        try {
            String name = org.lwjgl.input.Keyboard.getKeyName(mod.getKeycode());
            return name == null || name.isEmpty() ? null : name;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        boolean hovered = isMouseOverHeader(mouseX, mouseY);

        hoverAnim.setTarget(hovered ? 1.0F : 0.0F);
        hoverAnim.update();
        float hoverAnimation = hoverAnim.get();

        enableAnim.setTarget(mod.isEnabled() ? 1.0F : 0.0F);
        enableAnim.update();
        float enableAnimation = enableAnim.get();

        expandAnim.setTarget(expanded ? 1.0F : 0.0F);
        expandAnim.update();

        int totalHeight = getTotalHeight();
        int themeColor = 0xFF000000 | (GuiModule.getThemeColor(0) & 0x00FFFFFF);

        // State is carried entirely by row fill: enabled floods with the
        // accent, disabled stays dark. No checkbox, no switch. Because enabled
        // rows are scattered through the list rather than grouped, a flood is
        // what makes the active set parseable in one glance.
        int neutral = CompactModuleCard.blendColor(palette.card, palette.hoverCard, hoverAnimation);
        int cardColor = CompactModuleCard.blendColor(neutral, themeColor, enableAnimation);

        // Rows stay flat — they sit inside the tab's chrome shadow already,
        // and a shadow per row is what turned the list into mush.
        //
        // The accent flood stops at the header. The expanded body keeps the
        // neutral card colour: setting rows draw palette.titleText (white)
        // and several of them are shared Compact components, so a light theme
        // accent behind them is text nobody can read and contrast can't be
        // fixed from here. Drawn as two non-overlapping rects — the palette
        // colours are translucent, so stacking them would double-composite.
        RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, CARD_RADIUS, cardColor);
        if (totalHeight > h) {
            RenderUtils.drawRoundedRectAA(x, y + h, x + w, y + totalHeight, CARD_RADIUS, palette.card);
        }

        // Hint of dimension on the flooded rows: lit at the left, deeper at the
        // right. Drawn as a neutral sheen rather than a second accent colour so
        // it holds up whichever theme is active. Strictly left-to-right — a
        // top-to-bottom sheen shades the shared edge of two stacked enabled
        // rows differently and draws a seam between them, which is exactly the
        // grouping the flood is supposed to make readable.
        if (enableAnimation > 0.01F) {
            int lift = (int) (0x1C * enableAnimation);
            int sink = (int) (0x18 * enableAnimation);
            RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, CARD_RADIUS,
                    (lift << 24) | 0xFFFFFF, (sink << 24) | 0x000000, null);
        }

        // Near-white in BOTH states — the label should read identically
        // whether the row is on or off, so the fill is the only thing carrying
        // state. contrastText only kicks in for the light themes, where white
        // on the accent would be unreadable.
        // Centre on the row's true middle using the renderer's own height
        // rather than a hardcoded 8, so the label and the icons beside it
        // share one baseline instead of drifting apart with the font.
        float rowMid = y + h / 2.0F;
        float titleY = rowMid - FontUtil.semiBold.getHeight() / 2.0F;
        int titleColor = enableAnimation > 0.5F
                ? RenderUtils.contrastText(cardColor)
                : palette.titleText;
        FontUtil.semiBold.drawSmoothString(mod.getName(), x + 9, titleY, titleColor);

        int lowEmphasis = (titleColor & 0x00FFFFFF);
        int rightEdge = x + w - 8;

        // Chevron on every row, always visible but low-emphasis: each module
        // expands, so the affordance should be a constant part of the row
        // rather than something that appears when you happen to hover.
        RenderUtils.drawChevronRotated(rightEdge - 2, y + h / 2.0F, 5,
                180.0F * expandAnim.get(),
                ((int) (0x8A + 0x50 * Math.max(hoverAnimation, expandAnim.get())) << 24) | lowEmphasis,
                1.1F);
        rightEdge -= 13;

        // Keybind: the key letter followed by a small keyboard glyph, so a
        // bound module says so without being expanded. Same low-emphasis white
        // as the chevron.
        String bind = bindLabel();
        if (bind != null) {
            float glyphSize = 9.0F;
            float gap = 3.0F;
            float labelW = (float) FontUtil.small.getStringWidth(bind);
            float kbW = Icons.width(Icons.KEYBOARD, glyphSize);
            float cy = y + h / 2.0F;

            Icons.drawLeft(Icons.KEYBOARD, rightEdge - kbW, cy, glyphSize,
                    (0xB0 << 24) | lowEmphasis);
            FontUtil.small.drawSmoothString(bind,
                    rightEdge - kbW - gap - labelW,
                    cy - FontUtil.small.getHeight() / 2.0F,
                    (0xC8 << 24) | lowEmphasis);
        }

        if (expandAnim.get() > 0.01F) {
            drawExpandedSettings(mouseX, mouseY, palette);
        }
    }

    private void drawExpandedSettings(int mouseX, int mouseY, CompactPalette palette) {
        float anim = expandAnim.get();
        int animatedTotal = h + (int) Math.round(getSettingsHeight() * anim);
        tab.applyClipScissor(x, y, w, animatedTotal);

        float openProgress = Math.min(1.0F, anim / 0.75F);
        int yOffset = -(int) Math.round((1.0F - openProgress) * 6.0F);

        int settingsX = x + SETTINGS_INDENT;
        int settingsW = w - SETTINGS_INDENT * 2;
        int drawY = y + h + SETTING_TOP_PAD + yOffset;

        for (Object sc : settingComponents) {
            int compH = getComponentHeight(sc);
            if (sc instanceof SpaciousSlider) {
                ((SpaciousSlider) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((SpaciousSlider) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof SpaciousDoubleSlider) {
                ((SpaciousDoubleSlider) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((SpaciousDoubleSlider) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof SpaciousToggle) {
                ((SpaciousToggle) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((SpaciousToggle) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof SpaciousCombo) {
                ((SpaciousCombo) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((SpaciousCombo) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof SpaciousButton) {
                ((SpaciousButton) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((SpaciousButton) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactTextInput) {
                ((CompactTextInput) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactTextInput) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactHotbarLayout) {
                ((CompactHotbarLayout) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactHotbarLayout) sc).draw(mouseX, mouseY, palette);
            }
            drawY += compH + SETTING_GAP;
        }

        if (bindComponent != null) {
            bindComponent.setPosition(settingsX, drawY, settingsW, 24);
            bindComponent.draw(mouseX, mouseY, palette);
        }

        tab.restoreClipScissor();
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (expanded && expandAnim.get() > 0.3F) {
            int settingsX = x + SETTINGS_INDENT;
            int settingsW = w - SETTINGS_INDENT * 2;
            int drawY = y + h + SETTING_TOP_PAD;
            for (Object sc : settingComponents) {
                if (sc instanceof CompactHotbarLayout && ((CompactHotbarLayout) sc).isPickerOpen()) {
                    if (((CompactHotbarLayout) sc).mouseClicked(mouseX, mouseY, button)) return true;
                }
            }
            for (Object sc : settingComponents) {
                int compH = getComponentHeight(sc);
                if (isOver(settingsX, drawY, settingsW, compH, mouseX, mouseY)) {
                    if (sc instanceof SpaciousSlider) ((SpaciousSlider) sc).mouseClicked(mouseX, mouseY, button);
                    else if (sc instanceof SpaciousDoubleSlider) ((SpaciousDoubleSlider) sc).mouseClicked(mouseX, mouseY, button);
                    else if (sc instanceof SpaciousToggle) ((SpaciousToggle) sc).mouseClicked(mouseX, mouseY, button);
                    else if (sc instanceof SpaciousCombo) ((SpaciousCombo) sc).mouseClicked(mouseX, mouseY, button);
                    else if (sc instanceof SpaciousButton) ((SpaciousButton) sc).mouseClicked(mouseX, mouseY, button);
                    else if (sc instanceof CompactTextInput) ((CompactTextInput) sc).mouseClicked(mouseX, mouseY, button);
                    else if (sc instanceof CompactHotbarLayout) ((CompactHotbarLayout) sc).mouseClicked(mouseX, mouseY, button);
                    return true;
                }
                drawY += compH + SETTING_GAP;
            }
            if (bindComponent != null && bindComponent.isMouseOver(mouseX, mouseY)) {
                bindComponent.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }

        if (isMouseOverHeader(mouseX, mouseY)) {
            // Spacious binding: WHOLE CARD toggles on left click, right
            // click opens the settings panel. No separate toggle pill.
            if (button == 1 && (!settingComponents.isEmpty() || mod.isBindable())) {
                expanded = !expanded;
                return true;
            }
            if (button == 0) {
                mod.toggle();
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        for (Object sc : settingComponents) {
            if (sc instanceof SpaciousSlider) ((SpaciousSlider) sc).mouseReleased();
            else if (sc instanceof SpaciousDoubleSlider) ((SpaciousDoubleSlider) sc).mouseReleased();
        }
    }

    public void keyTyped(char c, int k) {
        if (CompactTextInput.handleGlobalKeyTyped(c, k)) return;
        for (Object sc : settingComponents) {
            if (sc instanceof CompactHotbarLayout) ((CompactHotbarLayout) sc).keyTyped(c, k);
        }
    }

    private boolean isMouseOverHeader(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private boolean isOver(int cx, int cy, int cw, int ch, int mx, int my) {
        return mx >= cx && mx <= cx + cw && my >= cy && my <= cy + ch;
    }
}
