package crow.client.clickgui.spacious;

import crow.client.clickgui.compact.CompactModuleCard;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

/**
 * Spacious-mode double slider. Same layout language as {@link
 * SpaciousSlider}: title left, value pair right ("12.4  15.7"), a thin
 * full-width track at the bottom. Two small dot indicators mark the
 * min/max positions on the track, with a brighter segment between them.
 */
public class SpaciousDoubleSlider {

    public static final int ROW_HEIGHT = 22;
    private static final int TRACK_HEIGHT = 3;
    private static final int TRACK_GAP_TOP = 14;

    private final DoubleSliderSetting setting;
    private boolean dragging;
    private Handle activeHandle = Handle.MIN;

    int x, y, w, h;
    private float smoothMin = -1.0F;
    private float smoothMax = -1.0F;
    private float activeAnimation;
    private long lastFrameNanos;

    /** Seconds since the previous draw, clamped so a stall doesn't teleport
     *  the handles. */
    private float deltaSeconds() {
        long now = System.nanoTime();
        long prev = lastFrameNanos;
        lastFrameNanos = now;
        if (prev == 0L) return 1.0F / 60.0F;
        return Math.min(0.1F, (now - prev) / 1.0E9F);
    }

    public SpaciousDoubleSlider(DoubleSliderSetting setting) {
        this.setting = setting;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        int trackWidth = w;

        if (dragging) {
            double value = screenToValue(mouseX, trackWidth);
            double currentMin = setting.getInputMin();
            double currentMax = setting.getInputMax();
            double overlapEps = Math.max(setting.getInterval() * 1.5, 1e-6);
            if (Math.abs(currentMax - currentMin) <= overlapEps) {
                if (value > currentMax) activeHandle = Handle.MAX;
                else if (value < currentMin) activeHandle = Handle.MIN;
            }
            if (activeHandle == Handle.MIN) setting.setValueMin(value);
            else setting.setValueMax(value);
        }

        float targetMin = (float) ((setting.getInputMin() - setting.getMin())
                / (setting.getMax() - setting.getMin()));
        float targetMax = (float) ((setting.getInputMax() - setting.getMin())
                / (setting.getMax() - setting.getMin()));
        // Frame-rate independent: a fixed per-frame fraction moved the
        // handles more than twice as fast at 240fps as at 60.
        float dt = deltaSeconds();
        float speed = 1.0F - (float) Math.exp(-dt * (dragging ? 34.0 : 12.0));
        if (smoothMin < 0.0F) {
            smoothMin = targetMin;
            smoothMax = targetMax;
        } else {
            smoothMin += (targetMin - smoothMin) * speed;
            smoothMax += (targetMax - smoothMax) * speed;
            if (Math.abs(targetMin - smoothMin) < 0.002F) smoothMin = targetMin;
            if (Math.abs(targetMax - smoothMax) < 0.002F) smoothMax = targetMax;
        }
        activeAnimation += ((dragging ? 1.0F : 0.0F) - activeAnimation)
                * (1.0F - (float) Math.exp(-dt * 13.0));

        FontUtil.semiBold.drawSmoothString(setting.getName(), x, y + 2, palette.titleText);
        String value = formatValue(setting.getInputMin()) + "  " + formatValue(setting.getInputMax());
        int valueW = (int) FontUtil.small.getStringWidth(value);
        FontUtil.small.drawSmoothString(value, x + w - valueW, y + 3, palette.mutedText);

        int barY = y + TRACK_GAP_TOP;
        int barRight = x + w;
        int trackBg = (0x44 << 24) | 0xFFFFFF;
        RenderUtils.drawRoundedRectAA(x, barY, barRight, barY + TRACK_HEIGHT, TRACK_HEIGHT / 2.0F, trackBg);

        int minX = x + (int) (trackWidth * smoothMin);
        int maxX = x + (int) (trackWidth * smoothMax);
        if (maxX > minX) {
            int themeColor = 0xFF000000 | (GuiModule.getThemeColor(0) & 0x00FFFFFF);
            int fillColor = CompactModuleCard.blendColor(palette.toggleOff, themeColor, 0.85F);
            RenderUtils.drawRoundedRectAA(minX, barY, maxX, barY + TRACK_HEIGHT,
                    TRACK_HEIGHT / 2.0F, fillColor);
        }

        // Dot indicators on each handle.
        int dotR = 3;
        int dotCy = barY + TRACK_HEIGHT / 2;
        int dotAlpha = (int) (200 + 55 * activeAnimation);
        int dotColor = (dotAlpha << 24) | 0xFFFFFF;
        RenderUtils.drawRoundedRectAA(minX - dotR, dotCy - dotR, minX + dotR, dotCy + dotR, dotR, dotColor);
        RenderUtils.drawRoundedRectAA(maxX - dotR, dotCy - dotR, maxX + dotR, dotCy + dotR, dotR, dotColor);
    }

    private String formatValue(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        return String.valueOf(v);
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        dragging = true;
        int trackWidth = w;
        float targetMin = (float) ((setting.getInputMin() - setting.getMin())
                / (setting.getMax() - setting.getMin()));
        float targetMax = (float) ((setting.getInputMax() - setting.getMin())
                / (setting.getMax() - setting.getMin()));
        int minX = x + (int) (trackWidth * targetMin);
        int maxX = x + (int) (trackWidth * targetMax);
        activeHandle = Math.abs(mouseX - maxX) < Math.abs(mouseX - minX) ? Handle.MAX : Handle.MIN;
    }

    public void mouseReleased() {
        dragging = false;
    }

    private double screenToValue(int mouseX, int trackWidth) {
        double percent = Math.max(0.0D, Math.min(1.0D, (mouseX - x) / (double) Math.max(1, trackWidth)));
        return setting.getMin() + percent * (setting.getMax() - setting.getMin());
    }

    private enum Handle { MIN, MAX }
}
