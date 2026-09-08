package crow.keystroke;

import crow.client.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class KeyStrokeRenderer {
    private static final int[] a = { 16777215, 16711680, 65280, 255, 16776960, 11141290 };
    private final Minecraft mc = Minecraft.getMinecraft();
    private final KeyStrokeKeyRenderer[] b = new KeyStrokeKeyRenderer[4];
    private final KeyStrokeMouse[] c = new KeyStrokeMouse[2];
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public KeyStrokeRenderer() {
        this.b[0] = new KeyStrokeKeyRenderer(this.mc.gameSettings.keyBindForward, 26, 2);
        this.b[1] = new KeyStrokeKeyRenderer(this.mc.gameSettings.keyBindBack, 26, 26);
        this.b[2] = new KeyStrokeKeyRenderer(this.mc.gameSettings.keyBindLeft, 2, 26);
        this.b[3] = new KeyStrokeKeyRenderer(this.mc.gameSettings.keyBindRight, 50, 26);
        this.c[0] = new KeyStrokeMouse(0, 2, 50);
        this.c[1] = new KeyStrokeMouse(1, 38, 50);
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent e) {

        if (this.mc.currentScreen == null) {
            if (this.mc.inGameHasFocus && !this.mc.gameSettings.showDebugInfo) {
                this.renderKeystrokes();
            } else {
                dragging = false;
            }
        } else if (this.mc.currentScreen instanceof GuiChat) {
            this.renderKeystrokes();
        } else {
            dragging = false;
        }
    }

    public void renderKeystrokes() {
        KeyStrokeMod.getKeyStroke();
        if (KeyStroke.enabled) {
            int x = KeyStroke.x;
            int y = KeyStroke.y;
            int g = this.getColor(KeyStroke.currentColorNumber);
            boolean h = KeyStroke.showMouseButtons;
            ScaledResolution res = new ScaledResolution(this.mc);
            float scale = Math.max(0.6F, KeyStroke.size);
            int width = Math.round(74 * scale);
            int height = Math.round((h ? 74 : 50) * scale);

            if (mc.currentScreen instanceof GuiChat) {
                handleChatDragging(res, width, height);
                x = KeyStroke.x;
                y = KeyStroke.y;
            } else {
                dragging = false;
            }

            if (x < 0) {
                KeyStroke.x = 0;
                x = KeyStroke.x;
            } else if (x > res.getScaledWidth() - width) {
                KeyStroke.x = res.getScaledWidth() - width;
                x = KeyStroke.x;
            }

            if (y < 0) {
                KeyStroke.y = 0;
                y = KeyStroke.y;
            } else if (y > res.getScaledHeight() - height) {
                KeyStroke.y = res.getScaledHeight() - height;
                y = KeyStroke.y;
            }

            this.drawMovementKeys(x, y, g, scale);
            if (h) {
                this.drawMouseButtons(x, y, g, scale);
            }

        }
    }

    private int getColor(int index) {
        return index == 6
                ? Color.getHSBColor((float) (System.currentTimeMillis() % 3750L) / 3750.0F, 1.0F, 1.0F).getRGB()
                : a[index];
    }

    private void drawMovementKeys(int x, int y, int textColor, float scale) {
        KeyStrokeKeyRenderer[] var4 = this.b;
        int var5 = var4.length;

        for (int var6 = 0; var6 < var5; ++var6) {
            KeyStrokeKeyRenderer key = var4[var6];
            key.renderKey(x, y, textColor, scale);
        }

    }

    private void drawMouseButtons(int x, int y, int textColor, float scale) {
        KeyStrokeMouse[] var4 = this.c;
        int var5 = var4.length;

        for (int var6 = 0; var6 < var5; ++var6) {
            KeyStrokeMouse button = var4[var6];
            button.n(x, y, textColor, scale);
        }

    }

    public static void drawBlurredButton(int x, int y, int width, int height, float alpha) {
        if (!KeyStroke.blurBackground) {
            return;
        }
        int blurAlpha = Math.max(18, Math.min(140, (int) (alpha * 110.0F)));
        RenderUtils.drawSoftBlurRect(x, y, x + width, y + height, 7, (blurAlpha << 24) | 0x111318);
    }

    public static void drawButtonShell(int x, int y, int width, int height, int fillColor) {
        drawBlurredButton(x, y, width, height, Math.min(1.0F, Math.max(0.2F, KeyStroke.backgroundOpacity / 255.0F)));
        int radius = Math.max(5, Math.min(width, height) / 3);
        RenderUtils.drawRoundedRectAA(x, y, x + width, y + height, radius, fillColor);
    }

    public static void drawPressSplash(int x, int y, int width, int height, int accentColor, long pressStart) {
        long elapsed = System.currentTimeMillis() - pressStart;
        if (pressStart <= 0L || elapsed < 0L || elapsed > 220L) {
            return;
        }

        float progress = elapsed / 220.0F;
        int radius = Math.max(4, (int) (Math.min(width, height) * (0.12F + progress * 0.48F)));
        int alpha = Math.max(0, (int) ((1.0F - progress) * 90.0F));
        int splash = (alpha << 24) | (accentColor & 0x00FFFFFF);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RenderUtils.glScissor(x, y, width, height);
        int cx = x + width / 2;
        int cy = y + height / 2;
        RenderUtils.drawRoundedRectAA(cx - radius, cy - radius, cx + radius, cy + radius, radius, splash);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void releaseBlur() {
    }

    private void handleChatDragging(ScaledResolution res, int width, int height) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }

        int mouseX = org.lwjgl.input.Mouse.getX() * res.getScaledWidth() / mc.displayWidth;
        int mouseY = res.getScaledHeight() - org.lwjgl.input.Mouse.getY() * res.getScaledHeight() / mc.displayHeight - 1;
        boolean hovering = mouseX >= KeyStroke.x && mouseX <= KeyStroke.x + width
                && mouseY >= KeyStroke.y && mouseY <= KeyStroke.y + height;

        if (org.lwjgl.input.Mouse.isButtonDown(0)) {
            if (!dragging && hovering) {
                dragging = true;
                dragOffsetX = KeyStroke.x - mouseX;
                dragOffsetY = KeyStroke.y - mouseY;
            }
            if (dragging) {
                KeyStroke.x = mouseX + dragOffsetX;
                KeyStroke.y = mouseY + dragOffsetY;
            }
        } else {
            dragging = false;
        }
    }
}
