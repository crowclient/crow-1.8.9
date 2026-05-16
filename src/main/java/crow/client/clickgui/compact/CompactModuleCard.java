package crow.client.clickgui.compact;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import crow.client.module.Module;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ButtonSetting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.HotbarLayoutSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TextSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class CompactModuleCard {

    private static final int PILL_WIDTH = 34;
    private static final int PILL_HEIGHT = 18;
    private static final int PILL_MARGIN = 12;
    private static final int SETTINGS_INDENT = 12;
    private static final int SETTING_GAP = 4;
    private static final int SETTING_TOP_PAD = 8;
    private static final int SETTING_BOT_PAD = 8;
    private static final int CARD_RADIUS = 10;
    private static final ResourceLocation DROPDOWN_ARROW =
            RenderUtils.getResourcePath("/assets/crow/crowclickgui/arrow_down.png");

    public final Module mod;
    private final CompactGui gui;

    private int x, y, w, h;
    private final Animation hoverAnim = new Animation(180, Animation::easeOutCubic);
    private final Animation enableAnim = new Animation(220, Animation::easeOutCubic);
    private final Animation expandAnim = new Animation(200, Animation::easeOutCubic);
    private final Animation revealAnim = new Animation(250, Animation::easeOutCubic);
    private int listIndex;

    private final List<Object> settingComponents = new ArrayList<>();
    private CompactBind bindComponent;

    public CompactModuleCard(Module mod, CompactGui gui) {
        this.mod = mod;
        this.gui = gui;

        for (Setting setting : mod.getSettings()) {
            if (setting instanceof SliderSetting) {
                settingComponents.add(new CompactSlider((SliderSetting) setting));
            } else if (setting instanceof DoubleSliderSetting) {
                settingComponents.add(new CompactDoubleSlider((DoubleSliderSetting) setting));
            } else if (setting instanceof TickSetting) {
                settingComponents.add(new CompactToggle((TickSetting) setting, mod));
            } else if (setting instanceof ComboSetting) {
                settingComponents.add(new CompactCombo((ComboSetting<?>) setting, mod));
            } else if (setting instanceof ButtonSetting) {
                settingComponents.add(new CompactButton((ButtonSetting) setting));
            } else if (setting instanceof TextSetting) {
                settingComponents.add(new CompactTextInput((TextSetting) setting));
            } else if (setting instanceof HotbarLayoutSetting) {
                settingComponents.add(new CompactHotbarLayout((HotbarLayoutSetting) setting));
            }
        }

        if (mod.isBindable()) {
            bindComponent = new CompactBind(mod, gui);
        }
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void setListIndex(int listIndex) {
        this.listIndex = listIndex;
    }

    public int getTotalHeight() {
        if (usesDetachedSettingsPanel()) {
            return h;
        }
        // Interpolate the card height by expandAnim so cards below cascade
        // smoothly while this one opens, and so that closing also animates
        // (expandAnim drifts back to 0 even after expandedCard is reset).
        float anim = expandAnim.get();
        if (anim < 0.005F) {
            return h;
        }
        return h + (int) Math.round(getSettingsHeight() * anim);
    }

    private int getSettingsHeight() {
        int total = SETTING_TOP_PAD;
        for (Object sc : settingComponents) {
            total += getComponentHeight(sc) + SETTING_GAP;
        }
        if (bindComponent != null) {
            total += 24 + SETTING_GAP;
        }
        return total + SETTING_BOT_PAD;
    }

    private int getComponentHeight(Object sc) {
        if (sc instanceof CompactSlider) return 24;
        if (sc instanceof CompactDoubleSlider) return 24;
        if (sc instanceof CompactToggle) return 20;
        if (sc instanceof CompactCombo) return ((CompactCombo) sc).getCurrentHeight();
        if (sc instanceof CompactButton) return 22;
        if (sc instanceof CompactTextInput) return CompactTextInput.TOTAL_HEIGHT;
        if (sc instanceof CompactHotbarLayout) return ((CompactHotbarLayout) sc).getHeight();
        return 20;
    }

    public void drawHeader(int mouseX, int mouseY, CompactPalette palette) {
        boolean expanded = gui.getExpandedCard() == this;
        boolean hovered = isMouseOverHeader(mouseX, mouseY);
        boolean hasSettings = !settingComponents.isEmpty() || mod.isBindable();
        boolean configCard = mod.moduleCategory() == Module.ModuleCategory.config;
        float revealTarget = gui.getRevealTargetForIndex(listIndex);

        hoverAnim.setTarget(hovered ? 1.0F : 0.0F);
        hoverAnim.update();
        float hoverAnimation = hoverAnim.get();

        enableAnim.setTarget(mod.isEnabled() ? 1.0F : 0.0F);
        enableAnim.update();
        float enableAnimation = enableAnim.get();

        expandAnim.setTarget(expanded ? 1.0F : 0.0F);
        expandAnim.update();
        float expandAnimation = expandAnim.get();

        revealAnim.setTarget(revealTarget);
        revealAnim.update();
        float revealAnimation = revealAnim.get();

        int totalHeight = getTotalHeight();
        int drawY = y;
        int alpha = Math.max(0, Math.min(255, (int) (255 * revealAnimation)));
        int cardColor = blendColor(palette.card, palette.hoverCard, hoverAnimation * 0.55F);
        if (configCard) {
            cardColor = blendColor(cardColor, palette.sidebarSelected, 0.18F);
        }

        float shadowIntensity = Math.max(hoverAnimation * 0.6F, expandAnimation * 0.8F);
        if (shadowIntensity > 0.02F && alpha > 30) {
            int shadowAlpha = (int) (12 * shadowIntensity * (alpha / 255.0F));
            RenderUtils.drawRoundedRectAA(x + 2, drawY + 3, x + w + 2, drawY + totalHeight + 3,
                    CARD_RADIUS + 2, (shadowAlpha << 24));
        }

        RenderUtils.drawRoundedRectAA(x, drawY, x + w, drawY + totalHeight, CARD_RADIUS, withAlpha(cardColor, alpha));

        if (hoverAnimation > 0.05F) {
            int outlineAlpha = (int) (22 * hoverAnimation * (alpha / 255.0F));
            RenderUtils.drawRoundedOutline(x, drawY, x + w, drawY + totalHeight, CARD_RADIUS, 1.0F,
                    (outlineAlpha << 24) | 0x00FFFFFF);
        }

        float innerGlow = Math.max(expandAnimation * 0.10F, enableAnimation * 0.05F);
        if (innerGlow > 0.01F) {
            RenderUtils.drawFlowingGradientRoundedRect(x + 1, drawY + 1, x + w - 1, drawY + totalHeight - 1,
                    CARD_RADIUS - 1, Math.min(42, (int) (innerGlow * 255.0F)), 0);
        }

        int leftPad = 12;
        int badgeWidth = gui.isSearching() ? getCategoryBadgeWidth() + 8 : 0;
        int textRightEdge = x + w - PILL_MARGIN - PILL_WIDTH - (hasSettings ? 18 : 0) - badgeWidth - 8;
        String moduleName = trimToWidth(mod.getName(), FontUtil.normal, textRightEdge - (x + leftPad));
        String description = getSingleLineDescription(textRightEdge - (x + leftPad));

        boolean hasDesc = description != null && !description.isEmpty();
        int titleH = 9;
        int descH = 8;
        int gap = 3;
        int blockH = hasDesc ? (titleH + gap + descH) : titleH;
        int blockTop = drawY + (h - blockH) / 2;
        int titleY = blockTop;
        int descriptionY = hasDesc ? (blockTop + titleH + gap) : (blockTop + titleH);
        FontUtil.semiBold.drawSmoothString(moduleName, x + leftPad, titleY, withAlpha(palette.titleText, alpha));
        if (hasDesc) {
            FontUtil.small.drawSmoothString(description, x + leftPad, descriptionY, withAlpha(palette.mutedText, alpha));
        }

        if (gui.isSearching()) {
            drawCategoryBadge(textRightEdge + 4, drawY + 10, palette, alpha);
        }

        int pillX = x + w - PILL_MARGIN - PILL_WIDTH;
        int pillY = drawY + (h - PILL_HEIGHT) / 2;
        drawTogglePill(pillX, pillY, palette, alpha);

        if (hasSettings) {
            drawArrow(x + w - PILL_MARGIN - PILL_WIDTH - 13, drawY + (h - 10) / 2, alpha);
        }
    }

    public void drawExpandedSettings(int mouseX, int mouseY, CompactPalette palette) {
        if (usesDetachedSettingsPanel()) {
            return;
        }
        float anim = expandAnim.get();
        if (anim < 0.01F) {
            return;
        }

        // Clip drawing to the card's animated bounding box. As expandAnim
        // grows 0 → 1, the card height grows from h → h + getSettingsHeight,
        // so the scissor reveals the settings progressively from the top of
        // the panel downward. Settings below the current animated bottom edge
        // are masked, so they neatly slide into view instead of popping in.
        int animatedTotal = h + (int) Math.round(getSettingsHeight() * anim);
        gui.applyCardClipScissor(x, y, w, animatedTotal);

        // Small parallax: while opening, the settings start a few pixels above
        // their final resting Y and ease down. The offset disappears by the
        // time anim crosses ~0.75 so click bounds (gated at anim > 0.3) stay
        // close enough to the visual position.
        float openProgress = Math.min(1.0F, anim / 0.75F);
        int yOffset = -(int) Math.round((1.0F - openProgress) * 6.0F);

        int settingsX = x + SETTINGS_INDENT;
        int settingsW = w - SETTINGS_INDENT * 2;
        int drawY = y + h + SETTING_TOP_PAD + yOffset;

        for (Object sc : settingComponents) {
            int compH = getComponentHeight(sc);
            if (sc instanceof CompactSlider) {
                ((CompactSlider) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactSlider) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactDoubleSlider) {
                ((CompactDoubleSlider) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactDoubleSlider) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactToggle) {
                ((CompactToggle) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactToggle) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactCombo) {
                ((CompactCombo) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactCombo) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactButton) {
                ((CompactButton) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactButton) sc).draw(mouseX, mouseY, palette);
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

        gui.restoreContentScissor();
    }

    public boolean usesDetachedSettingsPanel() {
        return false;
    }

    public int getDetachedSettingsHeight() {
        return getSettingsHeight();
    }

    public void drawDetachedSettings(int mouseX, int mouseY, CompactPalette palette, int panelX, int panelY, int panelW, int scrollOffset) {
        drawSettingsContents(mouseX, mouseY, palette, panelX, panelY - scrollOffset, panelW);
    }

    public boolean mouseClickedDetached(int mouseX, int mouseY, int button, int panelX, int panelY, int panelW, int scrollOffset) {
        int settingsX = panelX;
        int settingsW = panelW;
        int drawY = panelY - scrollOffset + SETTING_TOP_PAD;

        for (Object sc : settingComponents) {
            if (sc instanceof CompactHotbarLayout && ((CompactHotbarLayout) sc).isPickerOpen()) {
                if (((CompactHotbarLayout) sc).mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        for (Object sc : settingComponents) {
            int compH = getComponentHeight(sc);
            if (isOver(settingsX, drawY, settingsW, compH, mouseX, mouseY)) {
                if (sc instanceof CompactSlider) {
                    ((CompactSlider) sc).mouseClicked(mouseX, mouseY, button);
                } else if (sc instanceof CompactDoubleSlider) {
                    ((CompactDoubleSlider) sc).mouseClicked(mouseX, mouseY, button);
                } else if (sc instanceof CompactToggle) {
                    ((CompactToggle) sc).mouseClicked(mouseX, mouseY, button);
                } else if (sc instanceof CompactCombo) {
                    ((CompactCombo) sc).mouseClicked(mouseX, mouseY, button);
                } else if (sc instanceof CompactButton) {
                    ((CompactButton) sc).mouseClicked(mouseX, mouseY, button);
                } else if (sc instanceof CompactTextInput) {
                    ((CompactTextInput) sc).mouseClicked(mouseX, mouseY, button);
                } else if (sc instanceof CompactHotbarLayout) {
                    ((CompactHotbarLayout) sc).mouseClicked(mouseX, mouseY, button);
                }
                return true;
            }
            drawY += compH + SETTING_GAP;
        }

        if (bindComponent != null && bindComponent.isMouseOver(mouseX, mouseY)) {
            bindComponent.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        return false;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        boolean expanded = gui.getExpandedCard() == this;

        if (expanded && expandAnim.get() > 0.3F) {
            int settingsX = x + SETTINGS_INDENT;
            int settingsW = w - SETTINGS_INDENT * 2;
            int drawY = y + h + SETTING_TOP_PAD;

            for (Object sc : settingComponents) {
                if (sc instanceof CompactHotbarLayout && ((CompactHotbarLayout) sc).isPickerOpen()) {
                    if (((CompactHotbarLayout) sc).mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }

            for (Object sc : settingComponents) {
                int compH = getComponentHeight(sc);
                if (isOver(settingsX, drawY, settingsW, compH, mouseX, mouseY)) {
                    if (sc instanceof CompactSlider) {
                        ((CompactSlider) sc).mouseClicked(mouseX, mouseY, button);
                    } else if (sc instanceof CompactDoubleSlider) {
                        ((CompactDoubleSlider) sc).mouseClicked(mouseX, mouseY, button);
                    } else if (sc instanceof CompactToggle) {
                        ((CompactToggle) sc).mouseClicked(mouseX, mouseY, button);
                    } else if (sc instanceof CompactCombo) {
                        ((CompactCombo) sc).mouseClicked(mouseX, mouseY, button);
                    } else if (sc instanceof CompactButton) {
                        ((CompactButton) sc).mouseClicked(mouseX, mouseY, button);
                    } else if (sc instanceof CompactTextInput) {
                        ((CompactTextInput) sc).mouseClicked(mouseX, mouseY, button);
                    } else if (sc instanceof CompactHotbarLayout) {
                        ((CompactHotbarLayout) sc).mouseClicked(mouseX, mouseY, button);
                    }
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
            if (button == 0 && isOverToggle(mouseX, mouseY)) {
                mod.toggle();
                return true;
            }
            if (button == 1 && (!settingComponents.isEmpty() || mod.isBindable())) {
                gui.setExpandedCard(this);
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
            if (sc instanceof CompactSlider) {
                ((CompactSlider) sc).mouseReleased();
            } else if (sc instanceof CompactDoubleSlider) {
                ((CompactDoubleSlider) sc).mouseReleased();
            }
        }
    }

    private void drawSettingsContents(int mouseX, int mouseY, CompactPalette palette, int settingsX, int settingsY, int settingsW) {
        int drawY = settingsY + SETTING_TOP_PAD;

        for (Object sc : settingComponents) {
            int compH = getComponentHeight(sc);
            if (sc instanceof CompactSlider) {
                ((CompactSlider) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactSlider) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactDoubleSlider) {
                ((CompactDoubleSlider) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactDoubleSlider) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactToggle) {
                ((CompactToggle) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactToggle) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactCombo) {
                ((CompactCombo) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactCombo) sc).draw(mouseX, mouseY, palette);
            } else if (sc instanceof CompactButton) {
                ((CompactButton) sc).setPosition(settingsX, drawY, settingsW, compH);
                ((CompactButton) sc).draw(mouseX, mouseY, palette);
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
    }

    public void keyTypedSettings(char typedChar, int keyCode) {
        if (CompactTextInput.handleGlobalKeyTyped(typedChar, keyCode)) {
            return;
        }
        for (Object sc : settingComponents) {
            if (sc instanceof CompactHotbarLayout) {
                ((CompactHotbarLayout) sc).keyTyped(typedChar, keyCode);
            }
        }
    }

    private void drawTogglePill(int px, int py, CompactPalette palette, int alpha) {
        float ea = enableAnim.get();

        int themeColor = 0xFF000000 | (crow.client.module.modules.client.GuiModule.getThemeColor(0) & 0x00FFFFFF);
        int pillColor = blendColor(palette.toggleOff, themeColor, ea);
        RenderUtils.drawRoundedRectAA(px, py, px + PILL_WIDTH, py + PILL_HEIGHT,
                PILL_HEIGHT / 2.0F, withAlpha(pillColor, alpha));
        if (ea > 0.01F) {
            RenderUtils.drawFlowingGradientRoundedRectVertical(
                    px + 1, py + 1, px + PILL_WIDTH - 1, py + PILL_HEIGHT - 1,
                    (PILL_HEIGHT - 2) / 2.0F, (int) (120 * ea), 0);
        }

        int knobSize = 12;
        float offset = ea * (PILL_WIDTH - knobSize - 4);
        int knobX = (int) (px + 2 + offset);
        int knobY = py + (PILL_HEIGHT - knobSize) / 2;
        RenderUtils.drawRoundedRectAA(knobX, knobY, knobX + knobSize, knobY + knobSize, knobSize / 2.0F, withAlpha(0xFFFFFFFF, alpha));
    }

    private void drawCategoryBadge(int badgeX, int badgeY, CompactPalette palette, int alpha) {
        String label = mod.moduleCategory().getName();
        int badgeW = getCategoryBadgeWidth();
        RenderUtils.drawRoundedRectAA(badgeX, badgeY, badgeX + badgeW, badgeY + 14, 7,
                withAlpha(blendColor(palette.toggleOff, palette.card, 0.55F), alpha));
        FontUtil.small.drawCenteredSmoothString(label, badgeX + badgeW / 2.0F, badgeY + 4, withAlpha(palette.mutedText, alpha));
    }

    private int getCategoryBadgeWidth() {
        return Math.max(34, (int) FontUtil.small.getStringWidth(mod.moduleCategory().getName()) + 14);
    }

    private void drawArrow(int arrowX, int arrowY, int alpha) {

        int size = 10;
        int color = (Math.max(0, Math.min(255, alpha)) << 24) | 0xFFFFFF;
        crow.client.utils.RenderUtils.drawChevronRotated(
                arrowX + size / 2.0F,
                arrowY + size / 2.0F,
                size,
                180.0F * expandAnim.get(),
                color,
                1.4F);
    }

    private boolean isMouseOverHeader(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private boolean isOverToggle(int mouseX, int mouseY) {
        int pillX = x + w - PILL_MARGIN - PILL_WIDTH;
        int pillY = y + (h - PILL_HEIGHT) / 2;
        return isOver(pillX, pillY, PILL_WIDTH, PILL_HEIGHT, mouseX, mouseY);
    }

    private boolean isOver(int cx, int cy, int cw, int ch, int mx, int my) {
        return mx >= cx && mx <= cx + cw && my >= cy && my <= cy + ch;
    }

    public static int blendColor(int c1, int c2, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private String getSingleLineDescription(int maxWidth) {
        String description = mod.getShortDescription();
        if (description == null || description.isEmpty()) {
            return "";
        }
        if (FontUtil.small.getStringWidth(description) <= maxWidth) {
            return description;
        }
        return trimToWidth(description, FontUtil.small, maxWidth);
    }

    private String trimToWidth(String text, Object font, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (getStringWidth(font, text) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (trimmed.length() > 4 && getStringWidth(font, trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed + "...";
    }

    private double getStringWidth(Object font, String text) {
        if (font == FontUtil.small) {
            return FontUtil.small.getStringWidth(text);
        }
        return FontUtil.normal.getStringWidth(text);
    }
}
