package crow.keystroke;

import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import crow.client.utils.MouseManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class KeyStrokeMouse {
    private static final String[] a = { "LMB", "RMB" };
    private final Minecraft b = Minecraft.getMinecraft();
    private final int c;
    private final int d;
    private final int e;
    private final List<Long> f = new ArrayList();
    private boolean g = true;
    private long h;

    public KeyStrokeMouse(int k, int l, int m) {
        this.c = k;
        this.d = l;
        this.e = m;
    }

    public void n(int o, int p, int color, float scale) {
        boolean r = Mouse.isButtonDown(this.c);
        String s = a[this.c];
        if (r != this.g) {
            this.g = r;
            this.h = System.currentTimeMillis();
            if (r) {
                this.f.add(this.h);
            }
        }

        double j = 1.0D;
        int i = 255;
        if (r) {
            i = Math.min(255, (int) (2L * (System.currentTimeMillis() - this.h)));
            j = Math.max(0.0D, 1.0D - (double) (System.currentTimeMillis() - this.h) / 20.0D);
        } else {
            i = Math.max(0, 255 - (int) (2L * (System.currentTimeMillis() - this.h)));
            j = Math.min(1.0D, (double) (System.currentTimeMillis() - this.h) / 20.0D);
        }

        int t = color >> 16 & 255;
        int u = color >> 8 & 255;
        int v = color & 255;
        int c = (new Color(t, u, v)).getRGB();
        int boxX = o + Math.round(this.d * scale);
        int boxY = p + Math.round(this.e * scale);
        int width = Math.round(34 * scale);
        int height = Math.round(22 * scale);
        int radius = Math.max(5, Math.round(7 * scale));
        int shellAlpha = Math.max(25, Math.min(255, KeyStroke.backgroundOpacity + (r ? 26 : 0)));
        int shellColor = (shellAlpha << 24) | (18 << 16) | (18 << 8) | 24;

        KeyStrokeRenderer.drawPressSplash(boxX, boxY, width, height, c, this.h);
        KeyStrokeRenderer.drawButtonShell(boxX, boxY, width, height, shellColor);
        if (KeyStroke.outline) {
            RenderUtils.drawRoundedOutline(boxX, boxY, boxX + width, boxY + height, radius, 1.0F, c);
        }

        int textColor = -16777216 + ((int) ((double) t * j) << 16) + ((int) ((double) u * j) << 8) + (int) ((double) v * j);
        float titleScale = Math.max(0.65F, scale * 0.85F);
        float titleWidth = (float) FontUtil.semiBold.getStringWidth(s);
        GL11.glPushMatrix();
        GL11.glTranslatef(boxX + width / 2.0F, boxY + Math.max(5.0F, 6.0F * scale), 0.0F);
        GL11.glScalef(titleScale, titleScale, 1.0F);
        FontUtil.semiBold.drawSmoothString(s, -titleWidth / 2.0F, 0.0F, textColor);
        GL11.glPopMatrix();
        String leftCps = MouseManager.getLeftClickCounter() + " CPS";
        String rightCps = MouseManager.getRightClickCounter() + " CPS";
        int leftWidth = this.b.fontRendererObj.getStringWidth(leftCps);
        int rightWidth = this.b.fontRendererObj.getStringWidth(rightCps);
        boolean a2 = this.c == 0;
        int cpsWidth = a2 ? leftWidth : rightWidth;
        float cpsScale = Math.max(0.4F, scale * 0.5F);
        GL11.glPushMatrix();
        GL11.glTranslatef(boxX + width / 2.0F, boxY + height - Math.max(6.0F, 7.0F * scale), 0.0F);
        GL11.glScalef(cpsScale, cpsScale, 1.0F);
        this.b.fontRendererObj.drawString(a2 ? leftCps : rightCps, -(cpsWidth / 2), 0,
                -16777216 + ((int) (255.0D * j) << 16) + ((int) (255.0D * j) << 8) + (int) (255.0D * j));
        GL11.glPopMatrix();
    }
}
