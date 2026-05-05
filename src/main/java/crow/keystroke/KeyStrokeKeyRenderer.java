

package crow.keystroke;

import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.awt.*;

public class KeyStrokeKeyRenderer {
    private final Minecraft a = Minecraft.getMinecraft();
    private final KeyBinding keyBinding;
    private final int c;
    private final int d;
    private boolean e = true;
    private long f;

    public KeyStrokeKeyRenderer(KeyBinding i, int j, int k) {
        this.keyBinding = i;
        this.c = j;
        this.d = k;
    }

    public void renderKey(int l, int m, int color, float scale) {
        boolean o = this.keyBinding.isKeyDown();
        String p = Keyboard.getKeyName(this.keyBinding.getKeyCode());
        if (o != this.e) {
            this.e = o;
            this.f = System.currentTimeMillis();
        }

        double h = 1.0D;
        int g = 255;
        if (o) {
            g = Math.min(255, (int) (2L * (System.currentTimeMillis() - this.f)));
            h = Math.max(0.0D, 1.0D - (double) (System.currentTimeMillis() - this.f) / 20.0D);
        } else {
            g = Math.max(0, 255 - (int) (2L * (System.currentTimeMillis() - this.f)));
            h = Math.min(1.0D, (double) (System.currentTimeMillis() - this.f) / 20.0D);
        }

        int q = color >> 16 & 255;
        int r = color >> 8 & 255;
        int s = color & 255;
        int c = (new Color(q, r, s)).getRGB();
        int x = l + Math.round(this.c * scale);
        int y = m + Math.round(this.d * scale);
        int width = Math.round(22 * scale);
        int height = Math.round(22 * scale);
        int baseAlpha = Math.max(25, Math.min(255, KeyStroke.backgroundOpacity + (o ? 26 : 0)));
        int shellColor = (baseAlpha << 24) | (18 << 16) | (18 << 8) | 24;

        int radius = Math.max(5, Math.round(7 * scale));
        KeyStrokeRenderer.drawPressSplash(x, y, width, height, c, this.f);
        KeyStrokeRenderer.drawButtonShell(x, y, width, height, shellColor);
        if (o) {
            RenderUtils.drawRoundedRectAA(x, y, x + width, y + height, radius, (0x22 << 24) | (c & 0x00FFFFFF));
        }
        if (KeyStroke.outline) {
            RenderUtils.drawRoundedOutline(x, y, x + width, y + height, radius, 1.0F, c);
        }

        int textColor = -16777216 + ((int) ((double) q * h) << 16) + ((int) ((double) r * h) << 8) + (int) ((double) s * h);
        float textWidth = (float) FontUtil.semiBold.getStringWidth(p);
        float textScale = Math.max(0.7F, scale * 0.9F);
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glTranslatef(x + width / 2.0F, y + height / 2.0F - 1.0F, 0.0F);
        org.lwjgl.opengl.GL11.glScalef(textScale, textScale, 1.0F);
        FontUtil.semiBold.drawSmoothString(p, -textWidth / 2.0F, -4.5F, textColor);
        org.lwjgl.opengl.GL11.glPopMatrix();
    }
}
