package crow.client.clickgui.spacious;

import crow.client.clickgui.compact.CompactModuleCard;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

/**
 * Spacious-mode slider. Renders as a flat row with the name at the
 * top-left, current value at the top-right, and a thin full-width
 * progress track at the bottom of the row. No floating knob — the fill
 * end is the indicator. Matches the cleaner row aesthetic of the
 * spacious GUI.
 */
public class SpaciousSlider {

    public static final int ROW_HEIGHT = 22;
    private static final int TRACK_HEIGHT = 3;
    private static final int TRACK_GAP_TOP = 14;

    private final SliderSetting setting;
    private boolean dragging;

    int x, y, w, h;

    private final Animation fillAnim = new Animation(180, Animation::easeOutCubic);
    private boolean fillInitialized;
    private final Animation activeAnim = new Animation(200, Animation::easeOutCubic);

    public SpaciousSlider(SliderSetting setting) {
        this.setting = setting;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        // Drag → set value from mouse X across the full row width. Using
        // the row width (not a reserved sub-area like compact mode) makes
        // the track the full width of the card, matching the screenshot.
        if (dragging) {
            float p = Math.max(0.0F, Math.min(1.0F, (mouseX - x) / (float) Math.max(1, w)));
            setting.setValue((float) (setting.getMin() + p * (setting.getMax() - setting.getMin())));
        }

        float targetPercent = (float) ((setting.getInput() - setting.getMin())
                / (setting.getMax() - setting.getMin()));
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

        // Title (left) + value (right) on the upper line.
        FontUtil.semiBold.drawSmoothString(setting.getName(), x, y + 2, palette.titleText);
        String value = formatValue(setting.getInput());
        int valueW = (int) FontUtil.small.getStringWidth(value);
        FontUtil.small.drawSmoothString(value, x + w - valueW, y + 3, palette.mutedText);

        // Full-row track at the bottom.
        int barY = y + TRACK_GAP_TOP;
        int barRight = x + w;
        int trackBg = (0x44 << 24) | 0xFFFFFF;
        RenderUtils.drawRoundedRectAA(x, barY, barRight, barY + TRACK_HEIGHT, TRACK_HEIGHT / 2.0F, trackBg);

        int trackWidth = w;
        int fillW = Math.max(0, Math.min(trackWidth, (int) (trackWidth * smoothPercent)));
        if (fillW > 0) {
            int themeColor = 0xFF000000 | (GuiModule.getThemeColor(0) & 0x00FFFFFF);
            int fillColor = CompactModuleCard.blendColor(palette.toggleOff, themeColor, 0.85F);
            RenderUtils.drawRoundedRectAA(x, barY, x + fillW, barY + TRACK_HEIGHT, TRACK_HEIGHT / 2.0F, fillColor);
            if (activeAnimation > 0.01F) {
                // Subtle thumb dot at the fill end while interacting.
                int dotR = 3;
                int dotCx = x + fillW;
                int dotCy = barY + TRACK_HEIGHT / 2;
                int dotAlpha = (int) (200 + 55 * activeAnimation);
                RenderUtils.drawRoundedRectAA(dotCx - dotR, dotCy - dotR,
                        dotCx + dotR, dotCy + dotR, dotR, (dotAlpha << 24) | 0xFFFFFF);
            }
        }
    }

    private String formatValue(double v) {
        // Drop the trailing ".0" when the value is integral so labels
        // read as compact as possible ("6" not "6.0").
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return String.valueOf(v);
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        dragging = true;
    }

    public void mouseReleased() {
        dragging = false;
    }
}
