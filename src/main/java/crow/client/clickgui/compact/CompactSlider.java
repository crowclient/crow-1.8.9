package crow.client.clickgui.compact;

import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

public class CompactSlider {

    private final SliderSetting setting;
    private boolean dragging = false;

    int x, y, w, h;

    private final Animation fillAnim = new Animation(180, Animation::easeOutCubic);
    private boolean fillInitialized = false;

    private final Animation activeAnim = new Animation(200, Animation::easeOutCubic);

    public CompactSlider(SliderSetting setting) {
        this.setting = setting;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        int valueWidth = (int) FontUtil.normal.getStringWidth(String.valueOf(setting.getInput()));
        int trackWidth = Math.max(80, w - valueWidth - 16);

        if (dragging) {
            float p = Math.max(0.0F, Math.min(1.0F, (mouseX - x) / (float) trackWidth));
            setting.setValue((float) (setting.getMin() + p * (setting.getMax() - setting.getMin())));
        }

        float targetPercent = (float) ((setting.getInput() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        if (!fillInitialized) {
            fillAnim.set(targetPercent);
            fillInitialized = true;
        } else if (dragging) {
            fillAnim.set(targetPercent);
        } else {
            fillAnim.setTarget(targetPercent);
        }
        fillAnim.update();
        float smoothPercent = fillAnim.get();

        activeAnim.setTarget(dragging ? 1.0F : 0.0F);
        activeAnim.update();
        float activeAnimation = activeAnim.get();

        FontUtil.semiBold.drawSmoothString(setting.getName(), x, y + 1, palette.titleText);
        String value = String.valueOf(setting.getInput());
        FontUtil.normal.drawSmoothString(value, x + w - valueWidth, y + 1, palette.titleText);

        int barY = y + 15;
        int barH = 6;
        RenderUtils.drawRoundedRectAA(x, barY, x + trackWidth, barY + barH, 3, palette.toggleOff);

        int fillW = Math.max(0, Math.min(trackWidth, (int) (trackWidth * smoothPercent)));
        if (fillW > 0) {
            int fillColor = CompactModuleCard.blendColor(palette.toggleOff, palette.accent, 0.65F);
            RenderUtils.drawRoundedRectAA(x, barY, x + fillW, barY + barH, 3, fillColor);
            int gradientAlpha = (int) (165 + activeAnimation * 50);
            RenderUtils.drawFlowingGradientRoundedRect(x, barY, x + fillW, barY + barH, 3,
                    Math.min(220, gradientAlpha), 0);
        }

        int knobCenter = x + fillW;
        int knobHalfW = 4;

        if (activeAnimation > 0.02F) {
            int glowAlpha = (int) (35 * activeAnimation);
            RenderUtils.drawRoundedRectAA(
                    knobCenter - knobHalfW - 3, barY - 6,
                    knobCenter + knobHalfW + 3, barY + barH + 5,
                    7, (glowAlpha << 24) | (palette.accent & 0x00FFFFFF));
        }

        RenderUtils.drawRoundedRectAA(knobCenter - knobHalfW + 1, barY - 2, knobCenter + knobHalfW + 1, barY + 9, 4, 0x18000000);

        RenderUtils.drawRoundedRectAA(knobCenter - knobHalfW, barY - 3, knobCenter + knobHalfW, barY + 8, 4, 0xFFFFFFFF);
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        dragging = true;
    }

    public void mouseReleased() {
        dragging = false;
    }
}
