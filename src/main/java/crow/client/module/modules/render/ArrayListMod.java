package crow.client.module.modules.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.Render2DEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.HUD;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.RGBSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

public class ArrayListMod extends Module {
    public static final int DEFAULT_POS_X = -1;
    public static final int DEFAULT_POS_Y = 2;

    public static TickSetting dropShadow, customFont;
    public static TickSetting background, sideLine, showSubtext, hideRenderModules;
    public static ComboSetting colorMode, lineColorMode, textMode, subtextDivider;
    public static SliderSetting rainbowSpeed, bgOpacity, lineWidth;
    public static RGBSetting customColor, bgColor;

    public enum ColorModes { THEME, RAINBOW, ASTOLFO, CUSTOM }
    public enum LineColorModes { SAME_AS_TEXT, THEME, RAINBOW, ASTOLFO, CUSTOM }
    public enum TextModes { NORMAL, LOWERCASE, UPPERCASE }
    public enum DividerStyle { DASH, BRACKETS, PIPE, PARENS }

    private static int posX = DEFAULT_POS_X;
    private static int posY = DEFAULT_POS_Y;
    private boolean dragging = false;
    private int dragOffX, dragOffY;
    private boolean alignRight = true;
    private final Map<Module, Float> moduleAnimations = new HashMap<>();

    private static final int TEXT_PADDING = 6;
    private static final int OUTER_PADDING = 3;
    private static final int VERT_PAD = 3;
    private static final int ROW_GAP = 0;
    private static final int CORNER_R = 4;

    public ArrayListMod() {
        super("ArrayList", ModuleCategory.render);
        this.registerSetting(dropShadow = new TickSetting("Shadow", true));
        this.registerSetting(customFont = new TickSetting("Custom font", true));
        this.registerSetting(colorMode = new ComboSetting("Text color", ColorModes.THEME));
        this.registerSetting(customColor = new RGBSetting("Custom color", 170, 68, 221));
        customColor.visibleWhen(() -> colorMode != null && colorMode.getMode() == ColorModes.CUSTOM);
        this.registerSetting(rainbowSpeed = new SliderSetting("Rainbow speed", 2, 1, 10, 1));
        rainbowSpeed.visibleWhen(() -> colorMode != null && (colorMode.getMode() == ColorModes.RAINBOW || colorMode.getMode() == ColorModes.ASTOLFO));
        this.registerSetting(background = new TickSetting("Background", true));
        this.registerSetting(hideRenderModules = new TickSetting("Hide render", false));
        this.registerSetting(bgColor = new RGBSetting("BG color", 13, 13, 26));
        bgColor.visibleWhen(() -> background != null && background.isToggled());
        this.registerSetting(bgOpacity = new SliderSetting("BG opacity", 180, 0, 255, 1));
        bgOpacity.visibleWhen(() -> background != null && background.isToggled());
        this.registerSetting(sideLine = new TickSetting("Side line", true));
        this.registerSetting(showSubtext = new TickSetting("Subtext", false));
        this.registerSetting(subtextDivider = new ComboSetting("Divider", DividerStyle.DASH));
        subtextDivider.visibleWhen(() -> showSubtext != null && showSubtext.isToggled());
        this.registerSetting(textMode = new ComboSetting("Text mode", TextModes.NORMAL));
        this.registerSetting(lineColorMode = new ComboSetting("Line color", LineColorModes.SAME_AS_TEXT));
        lineColorMode.visibleWhen(() -> sideLine != null && sideLine.isToggled());
        this.registerSetting(lineWidth = new SliderSetting("Line width", 1, 1, 5, 1));
        lineWidth.visibleWhen(() -> sideLine != null && sideLine.isToggled());
    }

    @Subscribe
    public void onRenderXD(Render2DEvent ev) {
        if (!Utils.Player.isPlayerInGame() || mc.gameSettings.showDebugInfo) {
            return;
        }

        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
            return;
        }

        ScaledResolution sr = crow.client.utils.RenderUtils.scaled();
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        if (posX == DEFAULT_POS_X) {
            posX = screenW - 2;
            alignRight = true;
        }
        posX = Math.max(2, Math.min(screenW - 2, posX));
        posY = Math.max(2, Math.min(screenH - 20, posY));
        alignRight = posX > (screenW / 2);

        List<Module> renderModules = buildRenderList();

        if (renderModules.isEmpty()) {
            dragging = false;
            return;
        }

        boolean lineOn = sideLine.isToggled();
        int lw = (int) lineWidth.getInput();
        int lineGap = lineOn ? lw + TEXT_PADDING : TEXT_PADDING;
        int rowH = getTextHeight() + VERT_PAD * 2;
        int bgRGB = ((int) bgOpacity.getInput() << 24)
                | (bgColor.getRed() << 16)
                | (bgColor.getGreen() << 8)
                | bgColor.getBlue();

        int maxWidth = 0;
        for (Module module : renderModules) {
            maxWidth = Math.max(maxWidth, frameWidth(module));
        }

        float animatedHeight = 0.0F;
        for (Module module : renderModules) {
            float progress = moduleAnimations.getOrDefault(module, 0.0F);
            animatedHeight += (rowH + ROW_GAP) * progress;
        }

        int railTop = -1;
        int railBottom = -1;
        int railColorTop = 0;
        int railColorBottom = 0;
        if (lineOn) {
            float scanY = posY;
            int scanDelay = 0;
            for (Module module : renderModules) {
                float progress = moduleAnimations.getOrDefault(module, 0.0F);
                if (progress <= 0.03F) {
                    continue;
                }
                int rowY = Math.round(scanY);
                if (railTop < 0) {
                    railTop = rowY;
                    railColorTop = withAlpha(getLineColor(scanDelay), Math.max(0, Math.min(255, (int) (255 * progress))));
                }
                railBottom = rowY + rowH;
                railColorBottom = withAlpha(getLineColor(scanDelay), Math.max(0, Math.min(255, (int) (255 * progress))));
                scanY += (rowH + ROW_GAP) * progress;
                scanDelay -= 50;
            }
        }

        final int rowCount = renderModules.size();
        final int[] rowBgX1 = new int[rowCount];
        final int[] rowBgX2 = new int[rowCount];
        final int[] rowYArr = new int[rowCount];
        final int[] rowTextX = new int[rowCount];
        final int[] rowTextW = new int[rowCount];
        final int[] rowAlpha = new int[rowCount];
        final float[] rowProgress = new float[rowCount];
        final String[] rowText = new String[rowCount];

        {
            float preY = posY;
            int idx = 0;
            for (Module module : renderModules) {
                float progress = moduleAnimations.getOrDefault(module, 0.0F);
                rowProgress[idx] = progress;
                if (progress <= 0.03F) { idx++; continue; }

                String displayText = frameText(module);
                int textWidth = frameWidth(module);
                int fullBgX1 = alignRight ? posX - textWidth - lineGap - OUTER_PADDING : posX;
                int fullBgX2 = alignRight ? posX : posX + textWidth + lineGap + OUTER_PADDING;
                int slide = (int) ((1.0F - progress) * (textWidth + 10));

                rowBgX1[idx] = alignRight ? fullBgX1 + slide : fullBgX1 - slide;
                rowBgX2[idx] = alignRight ? fullBgX2 + slide : fullBgX2 - slide;
                rowYArr[idx] = Math.round(preY);
                rowTextX[idx] = alignRight ? posX - textWidth - lineGap + slide : posX + lineGap - slide;
                rowTextW[idx] = textWidth;
                rowAlpha[idx] = Math.max(0, Math.min(255, (int) (255 * progress)));
                rowText[idx] = displayText;

                preY += (rowH + ROW_GAP) * progress;
                idx++;
            }
        }

        // Per-corner rounding: outer-edge corners round only where the stepped
        // column profile leaves them exposed; the rail edge stays flush unless
        // the side line is off, where the first/last rows close the column.
        final boolean[][] rowCorners = new boolean[rowCount][];
        int firstVisible = -1;
        {
            int prevVis = -1;
            for (int i = 0; i < rowCount; i++) {
                if (rowProgress[i] <= 0.03F) continue;
                if (firstVisible < 0) firstVisible = i;
                int nextVis = -1;
                for (int j = i + 1; j < rowCount; j++) {
                    if (rowProgress[j] > 0.03F) { nextVis = j; break; }
                }
                boolean outerTop, outerBottom;
                if (alignRight) {
                    outerTop = prevVis < 0 || rowBgX1[prevVis] > rowBgX1[i];
                    outerBottom = nextVis < 0 || rowBgX1[nextVis] > rowBgX1[i];
                } else {
                    outerTop = prevVis < 0 || rowBgX2[prevVis] < rowBgX2[i];
                    outerBottom = nextVis < 0 || rowBgX2[nextVis] < rowBgX2[i];
                }
                boolean innerTop = prevVis < 0 && !lineOn;
                boolean innerBottom = nextVis < 0 && !lineOn;
                rowCorners[i] = alignRight
                        ? new boolean[]{outerTop, outerBottom, innerBottom, innerTop}
                        : new boolean[]{innerTop, innerBottom, outerBottom, outerTop};
                prevVis = i;
            }
        }

        boolean lineOnRight = alignRight;
        if (lineOn && railTop >= 0 && railBottom > railTop) {
            int railX1, railX2;
            if (lineOnRight) {
                railX1 = posX;
                railX2 = posX + lw;
            } else {
                railX1 = posX - lw;
                railX2 = posX;
            }
            float railR = lw / 2.0F;
            if (isThemeLine()) {
                RenderUtils.drawFlowingGradientRoundedRectVertical(railX1, railTop, railX2, railBottom,
                        railR, (railColorTop >>> 24) & 0xFF, 0);
            } else {
                RenderUtils.drawRoundedRectAA(railX1, railTop, railX2, railBottom, railR, railColorTop);
            }
        }

        if (HUD.enableBlur != null && HUD.enableBlur.isToggled()) {

            int bbLeft = Integer.MAX_VALUE, bbRight = Integer.MIN_VALUE;
            int bbTop = Integer.MAX_VALUE, bbBottom = Integer.MIN_VALUE;
            for (int i = 0; i < rowCount; i++) {
                if (rowProgress[i] <= 0.03F) continue;
                bbLeft = Math.min(bbLeft, rowBgX1[i]);
                bbRight = Math.max(bbRight, rowBgX2[i]);
                bbTop = Math.min(bbTop, rowYArr[i]);
                bbBottom = Math.max(bbBottom, rowYArr[i] + rowH);
            }
            if (bbLeft < bbRight && bbTop < bbBottom) {
                final int cr = 4;

                GUIBlurUtil.drawBlurredBackgroundCustom(() -> {
                    for (int i = 0; i < rowCount; i++) {
                        if (rowCorners[i] == null) continue;
                        RenderUtils.drawRoundedRectAA(rowBgX1[i], rowYArr[i],
                                rowBgX2[i], rowYArr[i] + rowH, cr, 0xFFFFFFFF, rowCorners[i]);
                    }
                }, bbLeft, bbTop, bbRight - bbLeft, bbBottom - bbTop,
                        (int) HUD.blurRadius.getInput(), 0.6f);
                mc.entityRenderer.setupOverlayRendering();
            }
        }

        int delay = 0;
        for (int i = 0; i < rowCount; i++) {
            if (rowProgress[i] <= 0.03F) { delay -= 50; continue; }

            int bgX1 = rowBgX1[i];
            int bgX2 = rowBgX2[i];
            int rowY = rowYArr[i];
            int textX = rowTextX[i];
            int textWidth = rowTextW[i];
            int alphaScale = rowAlpha[i];
            String displayText = rowText[i];
            int textColor = withAlpha(getTextColor(delay), alphaScale);

            if (background.isToggled()) {
                int rowBg = (Math.min((bgRGB >> 24) & 0xFF, alphaScale) << 24) | (bgRGB & 0x00FFFFFF);
                RenderUtils.drawRoundedRectAA(bgX1, rowY, bgX2, rowY + rowH, CORNER_R, rowBg, rowCorners[i]);

                // 1-px lit rim on the top row — the glass-material cue, one rect.
                if (i == firstVisible) {
                    int hlA = (0x26 * alphaScale) / 255;
                    if (hlA > 0) {
                        RenderUtils.drawRoundedRectAA(bgX1 + 2, rowY + 0.6F, bgX2 - 2, rowY + 1.6F,
                                0.5F, (hlA << 24) | 0xFFFFFF);
                    }
                }
            }

            if (customFont.isToggled()) {
                FontUtil.semiBold.drawSmoothString(displayText, textX, rowY + VERT_PAD, textColor);
            } else {
                mc.fontRendererObj.drawString(displayText, textX, rowY + VERT_PAD, textColor, dropShadow.isToggled());
            }

            delay -= 50;
        }

        handleDrag(screenW, screenH, renderModules);

    }

    // ponytail: per-frame caches. The old comparator called
    // getTextWidth(getArrayListText(m)) inside the sort, so each of the ~150
    // comparisons rebuilt two strings and measured two strings glyph-by-glyph.
    // Now each visible module is formatted and measured exactly once per frame.
    private final Map<Module, String> frameText = new HashMap<>();
    private final Map<Module, Integer> frameWidth = new HashMap<>();
    private final List<Module> renderListBuf = new ArrayList<>();

    private List<Module> buildRenderList() {
        renderListBuf.clear();
        frameText.clear();
        frameWidth.clear();

        List<Module> all = Crow.moduleManager.getModules();
        for (int i = 0; i < all.size(); i++) {
            Module module = all.get(i);
            if (!module.showInHud() || module.moduleCategory() == ModuleCategory.themes) {
                continue;
            }
            if (hideRenderModules.isToggled() && module.moduleCategory() == ModuleCategory.render) {
                continue;
            }
            Float cached = moduleAnimations.get(module);
            float progress = cached != null ? cached : (module.isEnabled() ? 1.0F : 0.0F);
            float target = module.isEnabled() ? 1.0F : 0.0F;
            progress += (target - progress) * 0.16F;
            if (Math.abs(target - progress) < 0.008F) {
                progress = target;
            }
            moduleAnimations.put(module, progress);
            if (progress > 0.02F) {
                String text = getArrayListText(module);
                frameText.put(module, text);
                frameWidth.put(module, getTextWidth(text));
                renderListBuf.add(module);
            }
        }
        renderListBuf.sort((a, b) -> Integer.compare(frameWidth.get(b), frameWidth.get(a)));
        return renderListBuf;
    }

    /** Formatted arraylist text for this frame; falls back for off-frame callers. */
    private String frameText(Module module) {
        String cached = frameText.get(module);
        return cached != null ? cached : getArrayListText(module);
    }

    /** Measured width for this frame; falls back for off-frame callers. */
    private int frameWidth(Module module) {
        Integer cached = frameWidth.get(module);
        return cached != null ? cached : getTextWidth(getArrayListText(module));
    }

    private void handleDrag(int screenW, int screenH, List<Module> active) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }

        int mx = Mouse.getX() * screenW / mc.displayWidth;
        int my = screenH - Mouse.getY() * screenH / mc.displayHeight - 1;
        boolean mouseDown = Mouse.isButtonDown(0);
        boolean lineOn = sideLine.isToggled();
        int lw = (int) lineWidth.getInput();
        int lineGap = lineOn ? lw + TEXT_PADDING : TEXT_PADDING;

        if (mouseDown) {
            if (!dragging) {
                if (active.isEmpty()) {
                    return;
                }

                int widest = 0;
                for (Module m : active) widest = Math.max(widest, frameWidth(m));
                int rowH = getTextHeight() + VERT_PAD * 2;
                int totalH = 0;
                for (Module module : active) {
                    totalH += Math.max(1, (int) ((rowH + ROW_GAP) * moduleAnimations.getOrDefault(module, 1.0F)));
                }
                int bx1 = alignRight ? posX - widest - lineGap - OUTER_PADDING - 2 - (lineOn ? lw : 0) : posX - 2;
                int bx2 = alignRight ? posX + 2 : posX + widest + lineGap + OUTER_PADDING + 2 + (lineOn ? lw : 0);

                if (mx >= bx1 && mx <= bx2 && my >= posY - 2 && my <= posY + totalH + 2) {
                    dragging = true;
                    dragOffX = posX - mx;
                    dragOffY = posY - my;
                }
            } else {
                posX = Math.max(2, Math.min(screenW - 2, mx + dragOffX));
                posY = Math.max(2, Math.min(screenH - 20, my + dragOffY));
            }
        } else {
            dragging = false;
        }
    }
    private int getTextWidth(String s) {
        return customFont.isToggled()
                ? (int) FontUtil.semiBold.getStringWidth(s)
                : mc.fontRendererObj.getStringWidth(s);
    }

    private String getArrayListText(Module module) {
        String base = module.getName();
        if (showSubtext == null || !showSubtext.isToggled()) {
            return applyTextMode(base);
        }
        String suffix = module.getHudSuffix();
        if (suffix == null || suffix.trim().isEmpty()) {
            return applyTextMode(base);
        }
        DividerStyle style = subtextDivider == null ? DividerStyle.DASH : (DividerStyle) subtextDivider.getMode();
        String formatted;
        switch (style) {
            case BRACKETS:
                formatted = base + " \u00a77[" + suffix + "]";
                break;
            case PIPE:
                formatted = base + " \u00a77| " + suffix;
                break;
            case PARENS:
                formatted = base + " \u00a77(" + suffix + ")";
                break;
            case DASH:
            default:
                formatted = base + " \u00a77- " + suffix;
                break;
        }
        return applyTextMode(formatted);
    }

    private String applyTextMode(String text) {
        TextModes mode = textMode == null ? TextModes.NORMAL : (TextModes) textMode.getMode();
        switch (mode) {
            case LOWERCASE:
                return text.toLowerCase();
            case UPPERCASE:
                return text.toUpperCase();
            default:
                return text;
        }
    }

    private int getTextHeight() {
        return customFont.isToggled()
                ? (int) FontUtil.semiBold.getHeight()
                : mc.fontRendererObj.FONT_HEIGHT;
    }

    private int getTextColor(int delay) {
        ColorModes mode = (ColorModes) colorMode.getMode();
        switch (mode) {
            case THEME:
                return GuiModule.getThemeColor(delay);
            case RAINBOW:
                return Utils.Client.rainbowDraw((long) rainbowSpeed.getInput(), delay);
            case ASTOLFO:
                return Utils.Client.astolfoColorsDraw((int) rainbowSpeed.getInput(), delay);
            case CUSTOM:
                return customColor.getRGB();
            default:
                return -1;
        }
    }

    private boolean isThemeLine() {
        LineColorModes mode = (LineColorModes) lineColorMode.getMode();
        return mode == LineColorModes.THEME
                || (mode == LineColorModes.SAME_AS_TEXT && colorMode.getMode() == ColorModes.THEME);
    }

    private int getLineColor(int delay) {
        LineColorModes mode = (LineColorModes) lineColorMode.getMode();
        switch (mode) {
            case SAME_AS_TEXT:
                return getTextColor(delay);
            case THEME:
                return GuiModule.getThemeColor(delay);
            case RAINBOW:
                return Utils.Client.rainbowDraw((long) rainbowSpeed.getInput(), delay);
            case ASTOLFO:
                return Utils.Client.astolfoColorsDraw((int) rainbowSpeed.getInput(), delay);
            case CUSTOM:
                return customColor.getRGB();
            default:
                return -1;
        }
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public static int getPosX() {
        return posX;
    }

    public static int getPosY() {
        return posY;
    }

    public static void setPosX(int x) {
        posX = x;
    }

    public static void setPosY(int y) {
        posY = y;
    }
}
