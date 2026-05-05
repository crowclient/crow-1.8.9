package crow.client.clickgui.compact;

import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

public class CompactDoubleSlider {

    private final DoubleSliderSetting setting;
    private boolean dragging;
    private Handle activeHandle = Handle.MIN;

    int x, y, w, h;

    private float smoothMin = -1.0F;
    private float smoothMax = -1.0F;

    private float activeAnimation;

    public CompactDoubleSlider(DoubleSliderSetting setting) {
        this.setting = setting;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        String valueText = setting.getInputMin() + " - " + setting.getInputMax();
        int valueWidth = (int) FontUtil.small.getStringWidth(valueText);
        int trackWidth = Math.max(80, w - valueWidth - 16);

        if (dragging) {
            double value = screenToValue(mouseX, trackWidth);

            double currentMin = setting.getInputMin();
            double currentMax = setting.getInputMax();
            double overlapEps = Math.max(setting.getInterval() * 1.5, 1e-6);
            if (Math.abs(currentMax - currentMin) <= overlapEps) {
                if (value > currentMax) {
                    activeHandle = Handle.MAX;
                } else if (value < currentMin) {
                    activeHandle = Handle.MIN;
                }

            }

            if (activeHandle == Handle.MIN) {
                setting.setValueMin(value);
            } else {
                setting.setValueMax(value);
            }
        }

        float targetMin = (float) ((setting.getInputMin() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        float targetMax = (float) ((setting.getInputMax() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        float speed = dragging ? 0.45F : 0.18F;

        if (smoothMin < 0.0F) {
            smoothMin = targetMin;
            smoothMax = targetMax;
        } else {
            smoothMin += (targetMin - smoothMin) * speed;
            smoothMax += (targetMax - smoothMax) * speed;
            if (Math.abs(targetMin - smoothMin) < 0.002F) smoothMin = targetMin;
            if (Math.abs(targetMax - smoothMax) < 0.002F) smoothMax = targetMax;
        }

        activeAnimation += ((dragging ? 1.0F : 0.0F) - activeAnimation) * 0.20F;

        FontUtil.semiBold.drawSmoothString(setting.getName(), x, y + 1, palette.titleText);
        FontUtil.small.drawSmoothString(valueText, x + w - valueWidth, y + 2, palette.mutedText);

        int barY = y + 15;
        int barH = 6;
        RenderUtils.drawRoundedRectAA(x, barY, x + trackWidth, barY + barH, 3, palette.toggleOff);

        int minX = x + (int) (trackWidth * smoothMin);
        int maxX = x + (int) (trackWidth * smoothMax);
        if (maxX > minX) {
            int fillColor = CompactModuleCard.blendColor(palette.toggleOff, palette.accent, 0.65F);
            RenderUtils.drawRoundedRectAA(minX, barY, maxX, barY + barH, 3, fillColor);
            int gradientAlpha = (int) (165 + activeAnimation * 50);
            RenderUtils.drawFlowingGradientRoundedRect(minX, barY, maxX, barY + barH, 3,
                    Math.min(220, gradientAlpha), 0);
        }

        if (activeAnimation > 0.02F) {
            int glowAlpha = (int) (30 * activeAnimation);
            int glowColor = (glowAlpha << 24) | (palette.accent & 0x00FFFFFF);
            if (activeHandle == Handle.MIN || !dragging) {
                RenderUtils.drawRoundedRectAA(minX - 7, barY - 6, minX + 7, barY + barH + 5, 7, glowColor);
            }
            if (activeHandle == Handle.MAX || !dragging) {
                RenderUtils.drawRoundedRectAA(maxX - 7, barY - 6, maxX + 7, barY + barH + 5, 7, glowColor);
            }
        }

        RenderUtils.drawRoundedRectAA(minX - 3, barY - 2, minX + 5, barY + 9, 4, 0x18000000);
        RenderUtils.drawRoundedRectAA(maxX - 3, barY - 2, maxX + 5, barY + 9, 4, 0x18000000);

        RenderUtils.drawRoundedRectAA(minX - 4, barY - 3, minX + 4, barY + 8, 4, 0xFFFFFFFF);
        RenderUtils.drawRoundedRectAA(maxX - 4, barY - 3, maxX + 4, barY + 8, 4, 0xFFFFFFFF);
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        dragging = true;
        int valueWidth = (int) FontUtil.small.getStringWidth(setting.getInputMin() + " - " + setting.getInputMax());
        int trackWidth = Math.max(80, w - valueWidth - 16);
        float targetMin = (float) ((setting.getInputMin() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        float targetMax = (float) ((setting.getInputMax() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        int minX = x + (int) (trackWidth * targetMin);
        int maxX = x + (int) (trackWidth * targetMax);
        activeHandle = Math.abs(mouseX - maxX) < Math.abs(mouseX - minX) ? Handle.MAX : Handle.MIN;
    }

    public void mouseReleased() {
        dragging = false;
    }

    private int valueToScreen(double value, int trackWidth) {
        double percent = (value - setting.getMin()) / (setting.getMax() - setting.getMin());
        return x + (int) Math.round(percent * trackWidth);
    }

    private double screenToValue(int mouseX, int trackWidth) {
        double percent = Math.max(0.0D, Math.min(1.0D, (mouseX - x) / (double) trackWidth));
        return setting.getMin() + percent * (setting.getMax() - setting.getMin());
    }

    private enum Handle {
        MIN, MAX
    }
}
