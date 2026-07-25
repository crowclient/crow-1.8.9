package crow.client.clickgui.compact;

import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.ButtonSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

public class CompactButton {
    private final ButtonSetting setting;

    int x, y, w, h;

    private final Animation hoverAnim = new Animation(160, Animation::easeOutCubic);

    private final Animation pressAnim = new Animation(220, Animation::easeOutCubic);

    public CompactButton(ButtonSetting setting) {
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
        float hoverAnimation = hoverAnim.get();

        // Press decays back to rest; setTarget(1) on click makes it spike.
        pressAnim.setTarget(0.0F);
        pressAnim.update();
        float pressAnimation = pressAnim.get();

        int bgBase = CompactModuleCard.blendColor(palette.toggleOff, palette.card, 0.35F);
        int bgHover = CompactModuleCard.blendColor(palette.hoverCard, palette.card, 0.50F);
        int bgColor = CompactModuleCard.blendColor(bgBase, bgHover, hoverAnimation);
        RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, 10, bgColor);

        int glowAlpha = (int) (28 + hoverAnimation * 40 + pressAnimation * 80);
        RenderUtils.drawFlowingGradientRoundedRect(x + 1, y + 1, x + w - 1, y + h - 1, 9,
                Math.min(180, glowAlpha), 0);

        if (hoverAnimation > 0.05F) {
            int outlineAlpha = (int) (45 * hoverAnimation);
            RenderUtils.drawRoundedOutline(x, y, x + w, y + h, 10, 1.0F,
                    (outlineAlpha << 24) | (GuiModule.accent() & 0x00FFFFFF));
        }

        FontUtil.semiBold.drawCenteredSmoothString(setting.getName(), x + w / 2.0F, y + 8, palette.titleText);
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            pressAnim.set(1.0F);
            setting.press();
        }
    }
}
