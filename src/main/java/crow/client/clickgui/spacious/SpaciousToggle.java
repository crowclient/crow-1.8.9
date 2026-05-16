package crow.client.clickgui.spacious;

import crow.client.clickgui.compact.CompactModuleCard;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

/**
 * Spacious-mode toggle. Name on the left, a small rounded square
 * indicator on the right. Filled in the theme color when on, hollow
 * (subtle outline) when off. Click anywhere on the row to flip.
 */
public class SpaciousToggle {

    public static final int ROW_HEIGHT = 17;
    private static final int BOX_SIZE = 11;

    private final TickSetting setting;
    private final Module mod;

    int x, y, w, h;

    private final Animation toggleAnim = new Animation(180, Animation::easeOutCubic);

    public SpaciousToggle(TickSetting setting, Module mod) {
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
        float t = toggleAnim.get();

        int boxX = x + w - BOX_SIZE;
        int boxY = y + (h - BOX_SIZE) / 2;

        float textY = y + (h - FontUtil.semiBold.getHeight()) / 2.0F;
        int textColor = CompactModuleCard.blendColor(palette.mutedText, palette.titleText, 0.4F + 0.6F * t);
        FontUtil.semiBold.drawSmoothString(setting.getName(), x, textY, textColor);

        int themeColor = 0xFF000000 | (GuiModule.getThemeColor(0) & 0x00FFFFFF);
        int offColor = (0x55 << 24) | 0xFFFFFF;       // hollow outline tint
        int fillColor = CompactModuleCard.blendColor(offColor, themeColor, t);
        RenderUtils.drawRoundedRectAA(boxX, boxY, boxX + BOX_SIZE, boxY + BOX_SIZE, 2.5F, fillColor);

        // Center dot indicator when on — keeps the box feeling like a
        // checkbox rather than just a colored square.
        if (t > 0.05F) {
            int dotSize = 4;
            float dx = boxX + (BOX_SIZE - dotSize) / 2.0F;
            float dy = boxY + (BOX_SIZE - dotSize) / 2.0F;
            int innerAlpha = (int) (255 * Math.min(1.0F, t * 1.3F));
            RenderUtils.drawRoundedRectAA(dx, dy, dx + dotSize, dy + dotSize, 1.0F,
                    (innerAlpha << 24) | 0xFFFFFF);
        }
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        setting.toggle();
        mod.guiButtonToggled(setting);
    }
}
