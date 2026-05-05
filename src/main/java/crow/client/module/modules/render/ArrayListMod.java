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
import crow.client.utils.GlowUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
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
        this.registerSetting(dropShadow = new TickSetting("Drop shadow", true));
        this.registerSetting(customFont = new TickSetting("Custom Font", true));
        this.registerSetting(colorMode = new ComboSetting("Text Color", ColorModes.THEME));
        this.registerSetting(customColor = new RGBSetting("Custom Color", 170, 68, 221));
        customColor.visibleWhen(() -> colorMode != null && colorMode.getMode() == ColorModes.CUSTOM);
        this.registerSetting(rainbowSpeed = new SliderSetting("Rainbow Speed", 2, 1, 10, 1));
        rainbowSpeed.visibleWhen(() -> colorMode != null && (colorMode.getMode() == ColorModes.RAINBOW || colorMode.getMode() == ColorModes.ASTOLFO));
        this.registerSetting(background = new TickSetting("Background", true));
        this.registerSetting(hideRenderModules = new TickSetting("Hide Render Modules", false));
        this.registerSetting(bgColor = new RGBSetting("BG Color", 13, 13, 26));
        bgColor.visibleWhen(() -> background != null && background.isToggled());
        this.registerSetting(bgOpacity = new SliderSetting("BG Opacity", 180, 0, 255, 1));
        bgOpacity.visibleWhen(() -> background != null && background.isToggled());
        this.registerSetting(sideLine = new TickSetting("Side Line", true));
        this.registerSetting(showSubtext = new TickSetting("Show Subtext", false));
        this.registerSetting(subtextDivider = new ComboSetting("Subtext Divider", DividerStyle.DASH));
        subtextDivider.visibleWhen(() -> showSubtext != null && showSubtext.isToggled());
        this.registerSetting(textMode = new ComboSetting("Text Mode", TextModes.NORMAL));
        this.registerSetting(lineColorMode = new ComboSetting("Line Color", LineColorModes.SAME_AS_TEXT));
        lineColorMode.visibleWhen(() -> sideLine != null && sideLine.isToggled());
        this.registerSetting(lineWidth = new SliderSetting("Line Width", 1, 1, 5, 1));
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

        ScaledResolution sr = new ScaledResolution(mc);
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
            maxWidth = Math.max(maxWidth, getTextWidth(getArrayListText(module)));
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

                String displayText = getArrayListText(module);
                int textWidth = getTextWidth(displayText);
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
            int railGlowColor = 0xFF000000 | (railColorTop & 0x00FFFFFF);
            if (HUD.enableGlow != null && HUD.enableGlow.isToggled()) {
                GlowUtil.drawGlow(railX1, railTop, railX2 - railX1, railBottom - railTop,
                        Math.max(4.0F, (float) HUD.glowRadius.getInput() * 0.45F), 1,
                        railGlowColor, (float) HUD.glowIntensity.getInput() * 0.65F);
            }
            Gui.drawRect(railX1, railTop, railX2, railBottom, railColorTop);
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

                final boolean[] blurRound;
                if (lineOnRight) {
                    blurRound = new boolean[]{true, true, false, false};
                } else {
                    blurRound = new boolean[]{false, false, true, true};
                }
                GUIBlurUtil.drawBlurredBackgroundCustom(() -> {
                    for (int i = 0; i < rowCount; i++) {
                        if (rowProgress[i] <= 0.03F) continue;
                        RenderUtils.drawRoundedRectAA(rowBgX1[i], rowYArr[i],
                                rowBgX2[i], rowYArr[i] + rowH, cr, 0xFFFFFFFF, blurRound);
                    }
                }, bbLeft, bbTop, bbRight - bbLeft, bbBottom - bbTop,
                        (int) HUD.blurRadius.getInput(), 0.6f);
                mc.entityRenderer.setupOverlayRendering();
            }
        }

        if (dropShadow.isToggled()) {
            for (int i = 0; i < rowCount; i++) {
                if (rowProgress[i] <= 0.03F) continue;
                int shadowAlpha = Math.max(6, Math.min(22, rowAlpha[i] / 14));
                GlowUtil.drawGlow(rowBgX1[i], rowYArr[i] + 1, rowBgX2[i] - rowBgX1[i], rowH,
                        4.0F, CORNER_R, (shadowAlpha << 24), 0.16F);
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

                boolean tl, tr, bl, br;
                if (lineOnRight) {

                    tl = true; bl = true; tr = false; br = false;
                } else {

                    tl = false; bl = false; tr = true; br = true;
                }
                RenderUtils.drawRoundedRectAA(bgX1, rowY, bgX2, rowY + rowH, CORNER_R, rowBg,
                        new boolean[]{tl, bl, br, tr});
            }

            if (customFont.isToggled()) {
                if (HUD.enableGlow != null && HUD.enableGlow.isToggled()) {
                    int glowCol = 0xFF000000 | (getTextColor(delay) & 0x00FFFFFF);
                    float glowR = Math.max(2.0F, (float) HUD.glowRadius.getInput() * 0.5F);
                    float glowI = (float) HUD.glowIntensity.getInput();
                    FontUtil.semiBold.drawGlowString(displayText, textX, rowY + VERT_PAD,
                            textColor, glowCol, glowR, glowI);
                } else {
                    FontUtil.semiBold.drawSmoothString(displayText, textX, rowY + VERT_PAD, textColor);
                }
            } else {
                if (HUD.enableGlow != null && HUD.enableGlow.isToggled()) {
                    int glowCol = 0xFF000000 | (getTextColor(delay) & 0x00FFFFFF);
                    float glowR = Math.max(2.0F, (float) HUD.glowRadius.getInput() * 0.5F);
                    float glowI = (float) HUD.glowIntensity.getInput();
                    RenderUtils.drawVanillaTextGlow(mc.fontRendererObj, displayText,
                            textX, rowY + VERT_PAD, glowCol, glowR, glowI);
                }
                mc.fontRendererObj.drawString(displayText, textX, rowY + VERT_PAD, textColor, dropShadow.isToggled());
            }

            delay -= 50;
        }

        handleDrag(screenW, screenH, renderModules);

    }

    private List<Module> buildRenderList() {
        List<Module> result = new ArrayList<>();
        for (Module module : Crow.moduleManager.getModules()) {
            if (!module.showInHud() || module.moduleCategory() == ModuleCategory.themes) {
                continue;
            }
            if (hideRenderModules.isToggled() && module.moduleCategory() == ModuleCategory.render) {
                continue;
            }
            float progress = moduleAnimations.containsKey(module) ? moduleAnimations.get(module) : (module.isEnabled() ? 1.0F : 0.0F);
            float target = module.isEnabled() ? 1.0F : 0.0F;
            progress += (target - progress) * 0.16F;
            if (Math.abs(target - progress) < 0.008F) {
                progress = target;
            }
            moduleAnimations.put(module, progress);
            if (progress > 0.02F) {
                result.add(module);
            }
        }
        result.sort((a, b) -> Integer.compare(getTextWidth(getArrayListText(b)), getTextWidth(getArrayListText(a))));
        return result;
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

                int widest = active.stream().mapToInt(m -> getTextWidth(getArrayListText(m))).max().orElse(0);
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
