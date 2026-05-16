package crow.client.clickgui.spacious;

import crow.client.clickgui.compact.CompactModuleCard;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.ButtonSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

/**
 * Spacious-mode action button. Slim row, centered label, subtle hover
 * + press tint. No flowing gradient or accent outline — keeps the row
 * visually consistent with the other spacious settings.
 */
public class SpaciousButton {

    public static final int ROW_HEIGHT = 18;

    private final ButtonSetting setting;
    int x, y, w, h;

    private final Animation hoverAnim = new Animation(160, Animation::easeOutCubic);
    private float pressAnimation;

    public SpaciousButton(ButtonSetting setting) {
        this.setting = setting;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        hoverAnim.setTarget(hovered ? 1.0F : 0.0F);
        hoverAnim.update();
        float hover = hoverAnim.get();
        pressAnimation *= 0.82F;
        if (pressAnimation < 0.01F) pressAnimation = 0.0F;

        int themeColor = 0xFF000000
                | (crow.client.module.modules.client.GuiModule.getThemeColor(0) & 0x00FFFFFF);

        // Idle bg already sits firmly inside the card-hover range so the
        // button reads as a clickable element from the start. Hover blends
        // toward the theme color instead of toggleOff so it lights up
        // rather than muting further.
        int idleBg = CompactModuleCard.blendColor(palette.card, palette.hoverCard, 0.60F);
        int hoverBg = CompactModuleCard.blendColor(palette.hoverCard, themeColor, 0.30F);
        int bg = CompactModuleCard.blendColor(idleBg, hoverBg, Math.min(1F, hover + pressAnimation * 0.45F));
        RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, 4, bg);

        // Original muted→title blend — bg brightening lifts the button
        // without affecting the text contrast curve.
        int textColor = CompactModuleCard.blendColor(palette.mutedText, palette.titleText, 0.45F + hover * 0.55F);
        FontUtil.semiBold.drawCenteredSmoothString(setting.getName(),
                x + w / 2.0F, y + (h - 9) / 2.0F, textColor);
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            pressAnimation = 1.0F;
            setting.press();
        }
    }
}
