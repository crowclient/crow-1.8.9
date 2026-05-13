package crow.client.clickgui.compact;

import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

public class CompactToggle {

    // Square checkbox-style box. Theme-gradient fill when enabled, gray-ish
    // (palette.toggleOff) when disabled. The module-level on/off pill on the
    // card header keeps the switch form factor; per-setting ticks are boxes.
    private static final int BOX_SIZE = 16;
    private static final float BOX_RADIUS = 3.5F;

    private final TickSetting setting;
    private final Module mod;

    int x, y, w, h;

    private final Animation toggleAnim = new Animation(180, Animation::easeOutCubic);
    private final Animation hoverAnim = new Animation(140, Animation::easeOutCubic);

    public CompactToggle(TickSetting setting, Module mod) {
        this.setting = setting;
        this.mod = mod;
        toggleAnim.set(setting.isToggled() ? 1.0F : 0.0F);
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        toggleAnim.setTarget(setting.isToggled() ? 1.0F : 0.0F);
        toggleAnim.update();
        float toggleAnimation = toggleAnim.get();

        int boxX = x + w - BOX_SIZE;
        int boxY = y + (h - BOX_SIZE) / 2;
        boolean hovered = mouseX >= boxX && mouseX <= boxX + BOX_SIZE
                && mouseY >= boxY && mouseY <= boxY + BOX_SIZE;
        hoverAnim.setTarget(hovered ? 1.0F : 0.0F);
        hoverAnim.update();
        float hoverAnimation = hoverAnim.get();

        // Vertically align the label baseline with the box center. Use the
        // font's actual height instead of a hard-coded 9 so the optical
        // center matches the box for every loaded font size.
        float textH = FontUtil.semiBold.getHeight();
        float textY = y + (h - textH) / 2.0F;
        FontUtil.semiBold.drawSmoothString(setting.getName(), x, textY, palette.titleText);

        // Base fill: gray when off, theme-colored when on, with a small hover
        // tint blended in either direction.
        int themeColor = 0xFF000000 | (crow.client.module.modules.client.GuiModule.getThemeColor(0) & 0x00FFFFFF);
        int fillColor = CompactModuleCard.blendColor(palette.toggleOff, themeColor, toggleAnimation);
        if (hoverAnimation > 0.01F) {
            fillColor = CompactModuleCard.blendColor(fillColor, palette.hoverCard, hoverAnimation * 0.22F);
        }
        RenderUtils.drawRoundedRectAA(boxX, boxY, boxX + BOX_SIZE, boxY + BOX_SIZE, BOX_RADIUS, fillColor);

        // Flowing theme gradient layered on top while toggled — same effect
        // as the previous pill, just confined to the box footprint.
        if (toggleAnimation > 0.02F) {
            RenderUtils.drawFlowingGradientRoundedRect(
                    boxX + 1, boxY + 1, boxX + BOX_SIZE - 1, boxY + BOX_SIZE - 1,
                    BOX_RADIUS - 1.0F, (int) (185 * toggleAnimation), 0);
        }

        // Subtle outline that brightens on hover.
        int outlineAlpha = 36 + (int) (hoverAnimation * 70);
        RenderUtils.drawRoundedOutline(boxX, boxY, boxX + BOX_SIZE, boxY + BOX_SIZE, BOX_RADIUS, 1.0F,
                (outlineAlpha << 24) | 0xFFFFFF);

        // Lighter inner square as the "checked" indicator — centered inside
        // the box, fades in with the toggle animation.
        if (toggleAnimation > 0.05F) {
            int innerSize = 6;
            float innerX = boxX + (BOX_SIZE - innerSize) / 2.0F;
            float innerY = boxY + (BOX_SIZE - innerSize) / 2.0F;
            int innerAlpha = (int) (255 * Math.min(1.0F, toggleAnimation * 1.3F));
            int innerCol = (innerAlpha << 24) | 0xFFFFFF;
            RenderUtils.drawRoundedRectAA(innerX, innerY,
                    innerX + innerSize, innerY + innerSize, 1.5F, innerCol);
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        setting.toggle();
        mod.guiButtonToggled(setting);
    }
}
