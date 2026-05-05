package crow.client.notifications;

import crow.client.module.modules.HUD;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.render.Notifications;
import crow.client.module.modules.render.Notifications.Position;
import crow.client.module.modules.render.Notifications.Style;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.GlowUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class Notification {

    static final int GAP = 4;
    private static final int ICON_SIZE = 12;
    private static final int BG = 0x101115;
    private static final ResourceLocation ICON =
            RenderUtils.getResourcePath("/assets/crow/notification.png");

    private final long fadeIn;
    private final long hold;
    private final long fadeOut;
    private final long total;

    final String title;
    final String message;
    final NotificationType type;

    private long startTime;

    public Notification(NotificationType type, String title, String message, int length) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.fadeIn = 180L;
        double durationMul = Notifications.getDuration();
        this.hold = (long) (800L * length * durationMul);
        this.fadeOut = 180L;
        this.total = fadeIn + hold + fadeOut;
    }

    public void show() {
        startTime = System.currentTimeMillis();
    }

    public boolean isShown() {
        return elapsed() < total;
    }

    private long elapsed() {
        return System.currentTimeMillis() - startTime;
    }

    int getHeight() {
        Style s = Notifications.getStyle();
        switch (s) {
            case Minimal: return 18;
            case Compact: return 22;
            case Classic: return 36;
            default:      return 34;
        }
    }

    public void render(int stackIndex) {
        Style style = Notifications.getStyle();
        switch (style) {
            case Minimal: renderMinimal(stackIndex); break;
            case Classic: renderClassic(stackIndex); break;
            case Compact: renderCompact(stackIndex); break;
            default:      renderModern(stackIndex);  break;
        }
    }

    private float calcAnim() {
        long t = elapsed();
        if (t < fadeIn) {
            return (float) Math.tanh(t / (double) fadeIn * 2.4);
        } else if (t < fadeIn + hold) {
            return 1.0F;
        } else {
            long fo = t - (fadeIn + hold);
            return (float) Math.tanh(2.4 - fo / (double) fadeOut * 2.4);
        }
    }

    private float calcProgress() {
        long t = elapsed();
        if (t <= fadeIn) return 1.0F;
        if (t >= fadeIn + hold) return 0.0F;
        return 1.0F - (float) (t - fadeIn) / (float) hold;
    }

    private int accentColor() {
        switch (type) {
            case WARNING: return 0xFFFFAA33;
            case ERROR:   return 0xFFFF4444;
            default:      return GuiModule.getThemeColor(0);
        }
    }

    private int stackY(int stackIndex, int height, float anim) {
        Position pos = Notifications.getPosition();
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        switch (pos) {
            case BottomRight: {
                int restY = sr.getScaledHeight() - 6 - (stackIndex + 1) * (height + GAP);
                return Math.round(restY + (height + 8) * (1.0F - anim));
            }
            case TopRight: {
                int restY = 6 + stackIndex * (height + GAP);
                return Math.round(restY - (height + 8) * (1.0F - anim));
            }
            default: {
                int restY = 6 + stackIndex * (height + GAP);
                return Math.round(restY - (height + 8) * (1.0F - anim));
            }
        }
    }

    private int stackX(int width) {
        Position pos = Notifications.getPosition();
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        switch (pos) {
            case BottomRight:
            case TopRight:
                return sr.getScaledWidth() - width - 6;
            default:
                return (sr.getScaledWidth() - width) / 2;
        }
    }

    private void renderModern(int stackIndex) {
        float anim = calcAnim();
        if (anim <= 0.01F) return;

        boolean hasFont = FontUtil.hasLoaded() && FontUtil.normal != null && FontUtil.small != null;
        int textLeft = 10 + ICON_SIZE + 8;
        int titleW = hasFont ? (int) FontUtil.normal.getStringWidth(title)
                             : Minecraft.getMinecraft().fontRendererObj.getStringWidth(title);
        int msgW = hasFont ? (int) FontUtil.small.getStringWidth(message)
                           : Minecraft.getMinecraft().fontRendererObj.getStringWidth(message);
        int width = Math.min(250, Math.max(160, textLeft + Math.max(titleW, msgW) + 12));
        int height = 34;

        int x = stackX(width);
        int y = stackY(stackIndex, height, anim);
        int radius = 10;

        int outerAlpha = (int) (0x60 * anim);
        int innerAlpha = (int) (0xE6 * anim);

        if (Notifications.useBlur() && HUD.enableBlur != null && HUD.enableBlur.isToggled()) {
            int br = HUD.blurRadius != null ? (int) HUD.blurRadius.getInput() : 5;
            GUIBlurUtil.drawBlurredBackground(x, y, width, height, br, radius, 0.5F);
            net.minecraft.client.Minecraft.getMinecraft().entityRenderer.setupOverlayRendering();
        }

        RenderUtils.drawRoundedRectAA(x, y + 1, x + width, y + height + 1, radius, (outerAlpha << 24));
        RenderUtils.drawRoundedRectAA(x, y, x + width, y + height, radius, (innerAlpha << 24) | BG);

        if (Notifications.useGlow() && HUD.enableGlow != null && HUD.enableGlow.isToggled()) {
            float gr = HUD.glowRadius != null ? (float) HUD.glowRadius.getInput() : 10F;
            float gi = HUD.glowIntensity != null ? (float) HUD.glowIntensity.getInput() : 0.8F;
            int glowColor = (((int)(0x40 * anim)) << 24) | (accentColor() & 0x00FFFFFF);
            RenderUtils.drawRoundedGlow(x, y, x + width, y + height, radius, glowColor, (int)(gr * gi));
        }

        if (ICON != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(ICON);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, anim);
            Gui.drawModalRectWithCustomSizedTexture(x + 10, y + (height - ICON_SIZE) / 2,
                    0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }

        int titleAlpha = (int) (0xFF * anim);
        int messageAlpha = (int) (0xD8 * anim);
        int textX = x + textLeft;
        if (hasFont) {
            FontUtil.normal.drawSmoothString(title, textX, y + 7, (titleAlpha << 24) | 0xFFFFFF);
            FontUtil.small.drawSmoothString(message, textX, y + 18, (messageAlpha << 24) | 0xB8BCC9);
        } else {
            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(title, textX, y + 7, (titleAlpha << 24) | 0xFFFFFF);
            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(message, textX, y + 19, (messageAlpha << 24) | 0xB8BCC9);
        }

        float progress = calcProgress();
        if (progress > 0.0F && progress < 1.0F) {
            int barColor = accentColor();
            int barA = (int) (0xCC * anim);
            float barW = (width - 20) * progress;
            RenderUtils.drawRoundedRectAA(x + 10, y + height - 4, x + 10 + barW, y + height - 2,
                    1, (barA << 24) | (barColor & 0x00FFFFFF));
        }

        GlStateManager.color(1F, 1F, 1F, 1F);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private void renderMinimal(int stackIndex) {
        float anim = calcAnim();
        if (anim <= 0.01F) return;

        boolean hasFont = FontUtil.hasLoaded() && FontUtil.small != null;
        String text = title + " — " + message;
        int textW = hasFont ? (int) FontUtil.small.getStringWidth(text)
                            : Minecraft.getMinecraft().fontRendererObj.getStringWidth(text);
        int padding = 10;
        int width = textW + padding * 2;
        int height = 18;

        int x = stackX(width);
        int y = stackY(stackIndex, height, anim);

        int bgAlpha = (int) (0xDD * anim);
        RenderUtils.drawRoundedRectAA(x, y, x + width, y + height, 4, (bgAlpha << 24) | 0x0D0F14);

        int accent = accentColor();
        int barA = (int) (0xFF * anim);
        float progress = calcProgress();
        float barW = (width - 4) * progress;
        RenderUtils.drawRoundedRectAA(x + 2, y + height - 2, x + 2 + barW, y + height, 1,
                (barA << 24) | (accent & 0x00FFFFFF));

        int textAlpha = (int) (0xFF * anim);
        int textY = y + (height - (hasFont ? FontUtil.small.getHeight() : 9)) / 2;
        if (hasFont) {
            FontUtil.small.drawSmoothString(text, x + padding, textY, (textAlpha << 24) | 0xDDDDDD);
        } else {
            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, x + padding, textY, (textAlpha << 24) | 0xDDDDDD);
        }

        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private void renderClassic(int stackIndex) {
        float anim = calcAnim();
        if (anim <= 0.01F) return;

        boolean hasFont = FontUtil.hasLoaded() && FontUtil.normal != null && FontUtil.small != null;
        int barW = 4;
        int textPad = barW + 8;
        int titleW = hasFont ? (int) FontUtil.normal.getStringWidth(title)
                             : Minecraft.getMinecraft().fontRendererObj.getStringWidth(title);
        int msgW = hasFont ? (int) FontUtil.small.getStringWidth(message)
                           : Minecraft.getMinecraft().fontRendererObj.getStringWidth(message);
        int width = Math.min(260, Math.max(150, textPad + Math.max(titleW, msgW) + 12));
        int height = 36;

        int x = stackX(width);
        int y = stackY(stackIndex, height, anim);

        int bgAlpha = (int) (0xEE * anim);

        if (Notifications.useBlur() && HUD.enableBlur != null && HUD.enableBlur.isToggled()) {
            int br = HUD.blurRadius != null ? (int) HUD.blurRadius.getInput() : 5;
            GUIBlurUtil.drawBlurredBackground(x, y, width, height, br, 3, 0.5F);
            net.minecraft.client.Minecraft.getMinecraft().entityRenderer.setupOverlayRendering();
        }

        RenderUtils.drawRoundedRectAA(x, y, x + width, y + height, 3, (bgAlpha << 24) | 0x111217);

        if (Notifications.useAccentBar()) {
            int accent = accentColor();
            int accentA = (int) (0xFF * anim);
            RenderUtils.drawRoundedRectAA(x, y, x + barW, y + height, 2,
                    (accentA << 24) | (accent & 0x00FFFFFF));
        }

        int titleAlpha = (int) (0xFF * anim);
        int messageAlpha = (int) (0xCC * anim);
        int textX = x + textPad;
        if (hasFont) {
            FontUtil.normal.drawSmoothString(title, textX, y + 6, (titleAlpha << 24) | 0xFFFFFF);
            FontUtil.small.drawSmoothString(message, textX, y + 19, (messageAlpha << 24) | 0xAAAAAA);
        } else {
            Minecraft mc = Minecraft.getMinecraft();
            mc.fontRendererObj.drawStringWithShadow(title, textX, y + 7, (titleAlpha << 24) | 0xFFFFFF);
            mc.fontRendererObj.drawStringWithShadow(message, textX, y + 20, (messageAlpha << 24) | 0xAAAAAA);
        }

        float progress = calcProgress();
        if (progress > 0.0F && progress < 1.0F) {
            int accent = accentColor();
            int barA = (int) (0x99 * anim);
            float pBarW = (width - 8) * progress;
            RenderUtils.drawRoundedRectAA(x + 4, y + height - 3, x + 4 + pBarW, y + height - 1,
                    1, (barA << 24) | (accent & 0x00FFFFFF));
        }

        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private void renderCompact(int stackIndex) {
        float anim = calcAnim();
        if (anim <= 0.01F) return;

        boolean hasFont = FontUtil.hasLoaded() && FontUtil.small != null;

        String text = message;
        int textW = hasFont ? (int) FontUtil.small.getStringWidth(text)
                            : Minecraft.getMinecraft().fontRendererObj.getStringWidth(text);

        int dotSize = 6;
        int padding = 8;
        int width = padding + dotSize + 5 + textW + padding;
        int height = 22;

        int x = stackX(width);
        int y = stackY(stackIndex, height, anim);
        int radius = height / 2;

        int bgAlpha = (int) (0xDD * anim);
        RenderUtils.drawRoundedRectAA(x, y, x + width, y + height, radius, (bgAlpha << 24) | 0x111217);
        RenderUtils.drawRoundedOutline(x, y, x + width, y + height, radius, 0.5F,
                ((int) (0x30 * anim) << 24) | 0xFFFFFF);

        int accent = accentColor();
        int dotA = (int) (0xFF * anim);
        int dotX = x + padding;
        int dotY = y + (height - dotSize) / 2;
        RenderUtils.drawRoundedRectAA(dotX, dotY, dotX + dotSize, dotY + dotSize,
                dotSize / 2, (dotA << 24) | (accent & 0x00FFFFFF));

        int textAlpha = (int) (0xFF * anim);
        int textX = dotX + dotSize + 5;
        int textY = y + (height - (hasFont ? FontUtil.small.getHeight() : 9)) / 2;
        if (hasFont) {
            FontUtil.small.drawSmoothString(text, textX, textY, (textAlpha << 24) | 0xDDDDDD);
        } else {
            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, textX, textY, (textAlpha << 24) | 0xDDDDDD);
        }

        GlStateManager.color(1F, 1F, 1F, 1F);
    }
}
