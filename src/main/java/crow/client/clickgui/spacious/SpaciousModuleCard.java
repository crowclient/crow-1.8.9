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

    public static final int CARD_HEIGHT = 18;
    private static final int CARD_RADIUS = 5;
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

        // Card background blends from neutral toward theme color when enabled.
        int neutral = CompactModuleCard.blendColor(palette.card, palette.hoverCard, hoverAnimation * 0.45F);
        int cardColor = CompactModuleCard.blendColor(neutral, themeColor, enableAnimation * 0.32F);

        RenderUtils.drawRoundedRectAA(x, y, x + w, y + totalHeight, CARD_RADIUS, cardColor);

        // Module name — centered vertically on the header row. Just a
        // gentle brighten on toggle; the background tint carries the
        // toggled-state signal, the text stays neutral.
        int titleY = y + (h - 8) / 2;
        int titleColor = CompactModuleCard.blendColor(palette.titleText, 0xFFFFFFFF, enableAnimation * 0.15F);
        FontUtil.semiBold.drawSmoothString(mod.getName(), x + 7, titleY, titleColor);

        // Expand-chevron on the right when the module has settings.
        if (!settingComponents.isEmpty() || mod.isBindable()) {
            int arrowX = x + w - 8;
            int arrowY = y + h / 2;
            int arrowColor = enableAnimation > 0.4F
                    ? ((int)(200 * enableAnimation) << 24) | 0xFFFFFF
                    : ((int)(140 * (0.4F + hoverAnimation * 0.6F)) << 24) | 0xFFFFFF;
            RenderUtils.drawChevronRotated(arrowX, arrowY, 5,
                    180.0F * expandAnim.get(), arrowColor, 1.1F);
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
