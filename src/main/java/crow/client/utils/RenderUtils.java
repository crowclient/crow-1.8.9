package crow.client.utils;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;

import crow.client.main.Crow;
import crow.client.module.modules.HUD;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.RecordingMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

public class RenderUtils {
    private static final Map<String, ResourceLocation> RESOURCE_CACHE = new HashMap<>();

    // ponytail: shared scratch for GL matrix/viewport readback. The AA draw
    // primitives below run 100s of times per frame (123 drawRoundedRectAA
    // callsites, many in loops); allocating three direct ByteBuffers per call
    // meant ~40k direct allocations/sec, each hitting Bits.reserveMemory's
    // global accounting lock plus a Cleaner phantom-ref registration. GL here
    // is single-threaded, so one shared set is safe.
    private static final java.nio.FloatBuffer SCRATCH_MV = BufferUtils.createFloatBuffer(16);
    private static final java.nio.FloatBuffer SCRATCH_PR = BufferUtils.createFloatBuffer(16);
    private static final java.nio.IntBuffer   SCRATCH_VP = BufferUtils.createIntBuffer(16);

    public static void syncGlStateBlend() {
        if (GL11.glIsEnabled(GL11.GL_BLEND)) {
            GlStateManager.disableBlend();
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.enableBlend();
        } else {
            GlStateManager.enableBlend();
            GL11.glDisable(GL11.GL_BLEND);
            GlStateManager.disableBlend();
        }
    }

    public static void syncGlStateTexture2D() {
        if (GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
            GlStateManager.disableTexture2D();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GlStateManager.enableTexture2D();
        } else {
            GlStateManager.enableTexture2D();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GlStateManager.disableTexture2D();
        }
    }

    public static void syncGlStateBlendFunc() {
        int srcRGB = GL11.glGetInteger(0x80C9);
        int dstRGB = GL11.glGetInteger(0x80C8);
        int srcA   = GL11.glGetInteger(0x80CB);
        int dstA   = GL11.glGetInteger(0x80CA);
        int wrongSrc = (srcRGB == 770) ? 1 : 770;
        GlStateManager.tryBlendFuncSeparate(wrongSrc, dstRGB, srcA, dstA);
        GlStateManager.tryBlendFuncSeparate(srcRGB, dstRGB, srcA, dstA);
    }

    public static void syncGlStateShadeModel() {
        int real = GL11.glGetInteger(GL11.GL_SHADE_MODEL);

        int wrong = (real == GL11.GL_SMOOTH) ? GL11.GL_FLAT : GL11.GL_SMOOTH;
        GlStateManager.shadeModel(wrong);
        GlStateManager.shadeModel(real);
    }

    public static void syncAllGlState() {
        syncGlStateBlend();
        syncGlStateTexture2D();
        syncGlStateBlendFunc();
        syncGlStateShadeModel();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static Color getBreathingPastel() {
        float hue = (System.currentTimeMillis() % 5000) / 5000f;
        return Color.getHSBColor(hue, 0.48f, 0.80f);
    }

    public static Color blend(Color color, Color color1, double d0) {
        float f = (float) d0;
        float f1 = 1.0F - f;
        float[] afloat = new float[3];
        float[] afloat1 = new float[3];
        color.getColorComponents(afloat);
        color1.getColorComponents(afloat1);
        return new Color((afloat[0] * f) + (afloat1[0] * f1), (afloat[1] * f) + (afloat1[1] * f1),
                        (afloat[2] * f) + (afloat1[2] * f1));
    }

    public static void glScissor(int x, int y, int width, int height) {
        int scale = new ScaledResolution(Crow.mc).getScaleFactor();
        int scissorY = Crow.mc.displayHeight - (y + height) * scale;
        GL11.glScissor(x * scale, scissorY, width * scale, height * scale);
    }

    public static void drawBorderedRect(float f, float f1, float f2, float f3, float f4, int i, int j) {
        float f5 = (float) ((i >> 24) & 255) / 255.0F;
        float f6 = (float) ((i >> 16) & 255) / 255.0F;
        float f7 = (float) ((i >> 8) & 255) / 255.0F;
        float f8 = (float) (i & 255) / 255.0F;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glPushMatrix();
        GlStateManager.color(f6, f7, f8, f5);
        GL11.glLineWidth(f4);
        GL11.glBegin(1);
        GL11.glVertex2d(f, f1);
        GL11.glVertex2d(f, f3);
        GL11.glVertex2d(f2, f3);
        GL11.glVertex2d(f2, f1);
        GL11.glVertex2d(f, f1);
        GL11.glVertex2d(f2, f1);
        GL11.glVertex2d(f, f3);
        GL11.glVertex2d(f2, f3);
        GL11.glEnd();
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void setColor(final int color) {
        final float a = ((color >> 24) & 0xFF) / 255.0f;
        final float r = ((color >> 16) & 0xFF) / 255.0f;
        final float g = ((color >> 8) & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        GlStateManager.color(r, g, b, a);
    }

    private static final boolean[] ALL_CORNERS = { true, true, true, true };

    private static void drawAARect(float x, float y, float x1, float y1,
                                   float radius, int color, boolean[] round, float borderWidth) {
        if (x1 <= x || y1 <= y) return;
        if (((color >> 24) & 0xFF) <= 0) return;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        setColor(color);

        if (borderWidth > 5000.0f) {
            GL11.glBegin(GL11.GL_POLYGON);
            emitRoundedPerimeter(x, y, x1, y1, radius, round);
            GL11.glEnd();
        } else {
            GL11.glLineWidth(borderWidth);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            emitRoundedPerimeter(x, y, x1, y1, radius, round);
            GL11.glEnd();
            GL11.glLineWidth(1.0f);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawAAArc(float cx, float cy, float outerR, float innerR,
                                  float startDeg, float endDeg, int color) {
        if (outerR <= 0) return;
        if (((color >> 24) & 0xFF) <= 0) return;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        setColor(color);

        int segments = Math.max(64, (int) Math.abs(endDeg - startDeg));
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.toRadians(startDeg + (endDeg - startDeg) * i / segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            GL11.glVertex2f(cx + cos * outerR, cy + sin * outerR);
            GL11.glVertex2f(cx + cos * innerR, cy + sin * innerR);
        }
        GL11.glEnd();

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void emitRoundedPerimeter(float x, float y, float x1, float y1, float radius, final boolean[] round) {
        if (round[0]) emitCornerArc(x,  y,  radius, -1,  1,   0,  90); else GL11.glVertex2d(x,  y);
        if (round[1]) emitCornerArc(x,  y1, radius, -1, -1,  90, 180); else GL11.glVertex2d(x,  y1);
        if (round[2]) emitCornerArc(x1, y1, radius,  1, -1,   0,  90); else GL11.glVertex2d(x1, y1);
        if (round[3]) emitCornerArc(x1, y,  radius,  1,  1,  90, 180); else GL11.glVertex2d(x1, y);
    }

    private static void emitCornerArc(float x, float y, float radius, int pn, int pn2,
                                       int from, int to) {
        for (int i = from; i <= to; i++) {
            GL11.glVertex2d(
                x + (radius * -pn) + (Math.sin((i * Math.PI) / 180.0) * radius * pn),
                y + (radius * pn2) + (Math.cos((i * Math.PI) / 180.0) * radius * pn));
        }
    }

    public static void roundHelper(float x, float y, float radius, int pn, int pn2, int originalRotation, int finalRotation) {
        emitCornerArc(x, y, radius, pn, pn2, originalRotation, finalRotation);
    }
    public static void round(float x, float y, float x1, float y1, float radius, final boolean[] round) {
        emitRoundedPerimeter(x, y, x1, y1, radius, round);
    }

    public static void drawRoundedRect(float x, float y, float x1, float y1, final float radius, final int color) {
        if (crow.client.render.aa.AAPipeline.isActive()) {
            crow.client.render.aa.SDFRenderer.fillRoundedRect(x, y, x1 - x, y1 - y, radius, color);
            return;
        }
        drawAARect(x, y, x1, y1, radius, color, ALL_CORNERS, 9999.0f);
    }

    public static void drawRoundedRect(float x, float y, float x1, float y1, final float radius, final int color, boolean[] round) {
        // SDF doesn't support per-corner rounding yet — use legacy path.
        drawAARect(x, y, x1, y1, radius, color, round, 9999.0f);
    }

    public static void drawRoundedOutline(float x, float y, float x1, float y1, final float radius, final float borderSize, final int color) {
        if (crow.client.render.aa.AAPipeline.isActive()) {
            crow.client.render.aa.SDFRenderer.outlineRoundedRect(x, y, x1 - x, y1 - y, radius, borderSize, color);
            return;
        }
        drawAARect(x, y, x1, y1, radius, color, ALL_CORNERS, borderSize);
    }

    public static void drawRoundedOutline(float x, float y, float x1, float y1, final float radius, final float borderSize, final int color, boolean[] drawCorner) {
        drawAARect(x, y, x1, y1, radius, color, drawCorner, borderSize);
    }

    public static void drawBorderedRoundedRect(float x, float y, float d, float y1, float radius, float borderSize, int borderC, int insideC, boolean[] round) {
        drawAARect(x, y, d, y1, radius, insideC, round, 9999.0f);
        drawAARect(x, y, d, y1, radius, borderC, round, borderSize);
    }

    public static void drawBorderedRoundedRect(float x, float y, float x1, float y1, float radius, float borderSize, int borderC, int insideC) {
        drawAARect(x, y, x1, y1, radius, insideC, ALL_CORNERS, 9999.0f);
        drawAARect(x, y, x1, y1, radius, borderC, ALL_CORNERS, borderSize);
    }

    public static void drawCircleOutline(float cx, float cy, float radius, float lineWidth, int color) {
        if (crow.client.render.aa.AAPipeline.isActive()) {
            crow.client.render.aa.SDFRenderer.outlineCircle(cx, cy, radius, lineWidth, color);
            return;
        }
        drawAARect(cx - radius, cy - radius, cx + radius, cy + radius, radius, color, ALL_CORNERS, lineWidth);
    }

    public static void drawFilledCircle(float cx, float cy, float radius, int color) {
        if (crow.client.render.aa.AAPipeline.isActive()) {
            crow.client.render.aa.SDFRenderer.fillCircle(cx, cy, radius, color);
            return;
        }
        drawAARect(cx - radius, cy - radius, cx + radius, cy + radius, radius, color, ALL_CORNERS, 9999.0f);
    }

    public static void drawArc(float cx, float cy, float outerR, float innerR,
                                float startDeg, float endDeg, int color) {
        drawAAArc(cx, cy, outerR, innerR, startDeg, endDeg, color);
    }

    public static void drawFlowingGradientRect(int left, int top, int right, int bottom, int alpha, int delayBase) {
        if (right <= left || bottom <= top || alpha <= 0) return;

        int width = right - left;
        int step = 1;
        int spatialSpread = 200;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);

        for (int x = left; x < right; x += step) {
            int nextX = Math.min(right, x + step);
            int spatialDelayLeft = (x - left) * spatialSpread / Math.max(1, width);
            int spatialDelayRight = (nextX - left) * spatialSpread / Math.max(1, width);
            int colorLeft = GuiModule.getThemeColor(delayBase - spatialDelayLeft);
            int colorRight = GuiModule.getThemeColor(delayBase - spatialDelayRight);

            int cLa = alpha & 0xFF;
            int cLr = (colorLeft >> 16) & 0xFF;
            int cLg = (colorLeft >> 8) & 0xFF;
            int cLb = colorLeft & 0xFF;
            int cRa = alpha & 0xFF;
            int cRr = (colorRight >> 16) & 0xFF;
            int cRg = (colorRight >> 8) & 0xFF;
            int cRb = colorRight & 0xFF;

            worldrenderer.pos(nextX, top, 0.0D).color(cRr, cRg, cRb, cRa).endVertex();
            worldrenderer.pos(x, top, 0.0D).color(cLr, cLg, cLb, cLa).endVertex();
            worldrenderer.pos(x, bottom, 0.0D).color(cLr, cLg, cLb, cLa).endVertex();
            worldrenderer.pos(nextX, bottom, 0.0D).color(cRr, cRg, cRb, cRa).endVertex();
        }

        tessellator.draw();

        GlStateManager.shadeModel(7425);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawFlowingGradientRoundedRect(int left, int top, int right, int bottom, float radius, int alpha, int delayBase) {
        if (right <= left || bottom <= top || alpha <= 0) return;

        int width = right - left;
        int height = bottom - top;
        float r = Math.max(0.0F, Math.min(radius, Math.min(width / 2.0F, height / 2.0F)));
        int spatialSpread = 200;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);

        for (int x = left; x < right; x++) {
            int nextX = x + 1;
            float insetL = roundCornerInset(x     - left, width,  r);
            float insetR = roundCornerInset(nextX - left, width,  r);
            float colTop = top    + insetL;
            float colBot = bottom - insetL;
            float nxtTop = top    + insetR;
            float nxtBot = bottom - insetR;
            if (colTop >= colBot || nxtTop >= nxtBot) continue;

            int spatialDelayLeft  = (x     - left) * spatialSpread / Math.max(1, width);
            int spatialDelayRight = (nextX - left) * spatialSpread / Math.max(1, width);
            int colorLeft  = crow.client.module.modules.client.GuiModule.getThemeColor(delayBase - spatialDelayLeft);
            int colorRight = crow.client.module.modules.client.GuiModule.getThemeColor(delayBase - spatialDelayRight);
            int a = alpha & 0xFF;
            int cLr = (colorLeft >> 16) & 0xFF, cLg = (colorLeft >> 8) & 0xFF, cLb = colorLeft & 0xFF;
            int cRr = (colorRight >> 16) & 0xFF, cRg = (colorRight >> 8) & 0xFF, cRb = colorRight & 0xFF;

            worldrenderer.pos(nextX, nxtTop, 0.0D).color(cRr, cRg, cRb, a).endVertex();
            worldrenderer.pos(x,     colTop, 0.0D).color(cLr, cLg, cLb, a).endVertex();
            worldrenderer.pos(x,     colBot, 0.0D).color(cLr, cLg, cLb, a).endVertex();
            worldrenderer.pos(nextX, nxtBot, 0.0D).color(cRr, cRg, cRb, a).endVertex();
        }

        tessellator.draw();

        GlStateManager.shadeModel(7425);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static float roundCornerInset(float pos, float length, float radius) {
        if (radius <= 0.0F) return 0.0F;
        if (pos < radius) {
            float dy = radius - pos;
            return radius - (float) Math.sqrt(Math.max(0.0F, radius * radius - dy * dy));
        }
        if (pos > length - radius) {
            float dy = pos - (length - radius);
            return radius - (float) Math.sqrt(Math.max(0.0F, radius * radius - dy * dy));
        }
        return 0.0F;
    }

    public static void drawFlowingGradientRectVertical(int left, int top, int right, int bottom, int alpha, int delayBase) {
        if (right <= left || bottom <= top || alpha <= 0) return;

        int height = bottom - top;
        int step = 1;
        int spatialSpread = 200;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);

        for (int y = top; y < bottom; y += step) {
            int nextY = Math.min(bottom, y + step);
            int spatialDelayTop    = (y     - top) * spatialSpread / Math.max(1, height);
            int spatialDelayBottom = (nextY - top) * spatialSpread / Math.max(1, height);
            int colorTop    = crow.client.module.modules.client.GuiModule.getThemeColor(delayBase - spatialDelayTop);
            int colorBottom = crow.client.module.modules.client.GuiModule.getThemeColor(delayBase - spatialDelayBottom);

            int cTr = (colorTop    >> 16) & 0xFF;
            int cTg = (colorTop    >>  8) & 0xFF;
            int cTb =  colorTop            & 0xFF;
            int cBr = (colorBottom >> 16) & 0xFF;
            int cBg = (colorBottom >>  8) & 0xFF;
            int cBb =  colorBottom         & 0xFF;
            int a   = alpha & 0xFF;

            worldrenderer.pos(right, y,     0.0D).color(cTr, cTg, cTb, a).endVertex();
            worldrenderer.pos(left,  y,     0.0D).color(cTr, cTg, cTb, a).endVertex();
            worldrenderer.pos(left,  nextY, 0.0D).color(cBr, cBg, cBb, a).endVertex();
            worldrenderer.pos(right, nextY, 0.0D).color(cBr, cBg, cBb, a).endVertex();
        }

        tessellator.draw();

        GlStateManager.shadeModel(7425);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawFlowingGradientRoundedRectVertical(int left, int top, int right, int bottom, float radius, int alpha, int delayBase) {
        if (right <= left || bottom <= top || alpha <= 0) return;

        int width = right - left;
        int height = bottom - top;
        float r = Math.max(0.0F, Math.min(radius, Math.min(width / 2.0F, height / 2.0F)));
        int spatialSpread = 200;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);

        for (int y = top; y < bottom; y++) {
            int nextY = y + 1;
            float insetT = roundCornerInset(y     - top, height, r);
            float insetB = roundCornerInset(nextY - top, height, r);
            float lT = left  + insetT;
            float rT = right - insetT;
            float lB = left  + insetB;
            float rB = right - insetB;
            if (lT >= rT || lB >= rB) continue;

            int spatialDelayTop    = (y     - top) * spatialSpread / Math.max(1, height);
            int spatialDelayBottom = (nextY - top) * spatialSpread / Math.max(1, height);
            int colorTop    = crow.client.module.modules.client.GuiModule.getThemeColor(delayBase - spatialDelayTop);
            int colorBottom = crow.client.module.modules.client.GuiModule.getThemeColor(delayBase - spatialDelayBottom);
            int a = alpha & 0xFF;
            int cTr = (colorTop    >> 16) & 0xFF, cTg = (colorTop    >> 8) & 0xFF, cTb = colorTop    & 0xFF;
            int cBr = (colorBottom >> 16) & 0xFF, cBg = (colorBottom >> 8) & 0xFF, cBb = colorBottom & 0xFF;

            worldrenderer.pos(rT, y,     0.0D).color(cTr, cTg, cTb, a).endVertex();
            worldrenderer.pos(lT, y,     0.0D).color(cTr, cTg, cTb, a).endVertex();
            worldrenderer.pos(lB, nextY, 0.0D).color(cBr, cBg, cBb, a).endVertex();
            worldrenderer.pos(rB, nextY, 0.0D).color(cBr, cBg, cBb, a).endVertex();
        }

        tessellator.draw();

        GlStateManager.shadeModel(7425);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawSoftBlurRect(float x, float y, float x1, float y1, float radius, int baseColor) {
        int alpha = (baseColor >> 24) & 0xFF;
        int rgb = baseColor & 0x00FFFFFF;
        if (alpha <= 0) return;

        drawRoundedRect(x, y, x1, y1, radius, baseColor);
        drawRoundedRect(x - 1, y - 1, x1 + 1, y1 + 1, radius + 1, ((int) (alpha * 0.28F) << 24) | rgb);
        drawRoundedRect(x - 3, y - 2, x1 + 3, y1 + 2, radius + 2, ((int) (alpha * 0.18F) << 24) | rgb);
        drawRoundedRect(x - 5, y - 4, x1 + 5, y1 + 4, radius + 4, ((int) (alpha * 0.10F) << 24) | rgb);
    }

    public static float nativeScale() {
        ScaledResolution sr = new ScaledResolution(Crow.mc);
        return sr.getScaleFactor();
    }

    private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /* Elevation shadow presets. A shadow crosses an elevation boundary
     * exactly once — nothing nested inside a raised surface gets its own,
     * or the whole stack turns to mud. */

    /* These are GUI units, not pixels — the shader scales them by the live
     * modelview. Each preset renders as two layers (see drawSoftShadow):
     * a wide, downward-offset ambient penumbra that carries the "blurred"
     * read, and a tight contact shadow that keeps the edge defined. The
     * alpha here is the ambient layer's peak; with the gaussian-CDF falloff
     * only about half of it is visible at the silhouette, so these run
     * darker than the old exp-falloff numbers did. */

    /** Chrome: window/container level — the macOS-window read: a deep,
     *  diffuse pool below, soft everywhere else. */
    public static final int    GLASS_SHADOW_CHROME      = 0x6E;
    private static final float GLASS_SHADOW_CHROME_Y    = 4.0F;
    private static final float GLASS_SHADOW_CHROME_BLUR = 10.0F;

    /** Raised: cards, dropdowns, modals, tooltips sitting on chrome. */
    public static final int    GLASS_SHADOW_RAISED      = 0x58;
    private static final float GLASS_SHADOW_RAISED_Y    = 2.0F;
    private static final float GLASS_SHADOW_RAISED_BLUR = 5.0F;

    /**
     * Apple-style two-layer soft shadow: one wide, offset ambient layer
     * (the blur) plus one tight, near-centered contact layer (the edge
     * definition). Both go through the SDF shadow shader.
     */
    private static void drawSoftShadow(float x, float y, float w, float h,
                                       float radius, int alpha,
                                       float offsetY, float blur) {
        if (alpha <= 0) return;
        drawShadowedRoundedRect(x, y, w, h, radius,
                clamp255(alpha) << 24, 0.0F, offsetY, blur);
        drawShadowedRoundedRect(x, y, w, h, radius,
                clamp255((int) (alpha * 0.7F)) << 24, 0.0F,
                Math.min(1.0F, offsetY * 0.25F), Math.max(1.0F, blur * 0.3F));
    }

    /**
     * Vertical two-stop gradient clipped to a rounded rect.
     *
     * <p>Intended as a translucent sheen <i>over</i> an already-drawn
     * {@link #drawRoundedRectAA} fill: the strips below are geometrically
     * inset at the corners rather than analytically antialiased, so the base
     * fill underneath is what gives the shape its clean silhouette. Layering
     * it this way also means one sheen works over any accent colour instead of
     * needing a per-theme pair of stops.
     */
    public static void drawRoundedRectVGradient(float x, float y, float x1, float y1,
                                                float radius, int topColor, int bottomColor) {
        if (x1 <= x || y1 <= y) return;

        float w = x1 - x;
        float h = y1 - y;
        float r = Math.max(0.0F, Math.min(radius, Math.min(w / 2.0F, h / 2.0F)));

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        net.minecraft.client.renderer.Tessellator tess =
                net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);

        int tA = (topColor >>> 24) & 0xFF, tR = (topColor >> 16) & 0xFF,
            tG = (topColor >> 8) & 0xFF,   tB = topColor & 0xFF;
        int bA = (bottomColor >>> 24) & 0xFF, bR = (bottomColor >> 16) & 0xFF,
            bG = (bottomColor >> 8) & 0xFF,   bB = bottomColor & 0xFF;

        int steps = Math.max(1, (int) Math.ceil(h));
        for (int i = 0; i < steps; i++) {
            float yT = y + h * (i / (float) steps);
            float yB = y + h * ((i + 1) / (float) steps);
            float insetT = roundCornerInset((int) (yT - y), (int) h, r);
            float insetB = roundCornerInset((int) (yB - y), (int) h, r);
            float lT = x + insetT, rT = x1 - insetT;
            float lB = x + insetB, rB = x1 - insetB;
            if (lT >= rT || lB >= rB) continue;

            float fT = (yT - y) / h;
            float fB = (yB - y) / h;
            int aT = (int) (tA + (bA - tA) * fT), rr1 = (int) (tR + (bR - tR) * fT);
            int gg1 = (int) (tG + (bG - tG) * fT), bb1 = (int) (tB + (bB - tB) * fT);
            int aB = (int) (tA + (bA - tA) * fB), rr2 = (int) (tR + (bR - tR) * fB);
            int gg2 = (int) (tG + (bG - tG) * fB), bb2 = (int) (tB + (bB - tB) * fB);

            wr.pos(rT, yT, 0.0D).color(rr1, gg1, bb1, aT).endVertex();
            wr.pos(lT, yT, 0.0D).color(rr1, gg1, bb1, aT).endVertex();
            wr.pos(lB, yB, 0.0D).color(rr2, gg2, bb2, aB).endVertex();
            wr.pos(rB, yB, 0.0D).color(rr2, gg2, bb2, aB).endVertex();
        }

        tess.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Text colour that stays legible on {@code bgArgb}: near-black on light
     * fills, white on dark ones.
     *
     * <p>Needed wherever a surface is filled with the live theme colour, which
     * ranges from near-black to White/Gray — hardcoding white text there makes
     * the label vanish on the light themes.
     */
    public static int contrastText(int bgArgb) {
        int r = (bgArgb >> 16) & 0xFF;
        int g = (bgArgb >>  8) & 0xFF;
        int b =  bgArgb        & 0xFF;
        float lum = (0.299F * r + 0.587F * g + 0.114F * b) / 255F;
        return lum > 0.58F ? 0xFF10131A : 0xFFFFFFFF;
    }

    /**
     * Chrome-tier shadow on its own, for panels that paint their interior in
     * several pieces and so can't use {@link #drawGlassPanel}. Pair it with
     * {@link #drawGlassEdge} around the pieces.
     *
     * @param alphaScale 0–1 reveal fade
     */
    public static void drawGlassChromeShadow(float x, float y, float w, float h,
                                             float radius, float alphaScale) {
        if (alphaScale <= 0.0F) return;
        drawSoftShadow(x, y, w, h, radius,
                (int) (GLASS_SHADOW_CHROME * alphaScale),
                GLASS_SHADOW_CHROME_Y, GLASS_SHADOW_CHROME_BLUR);
    }

    /**
     * The client's single panel material, minus the backdrop blur: a soft
     * drop shadow, a translucent fill, a 1-px lit top rim, and a hairline
     * border. Every panel, card, dropdown and HUD background should go
     * through here so they read as one system by construction.
     *
     * <p>There is deliberately no {@link GUIBlurUtil} call. Inside a click
     * GUI the blurred backdrop comes free from Minecraft's own post-process
     * shader (see {@code GuiModule.applyBackdropBlurShader}); on the HUD the
     * rim plus the shadow carry the glass read on their own, which matters
     * because HUD blur is off by default. Use {@link #drawFrostedPanel} for
     * the rare surface that genuinely needs a real backdrop blur.
     *
     * @param fill        translucent ARGB fill — pass a palette color straight through
     * @param shadowAlpha 0 for no shadow, else {@link #GLASS_SHADOW_CHROME} /
     *                    {@link #GLASS_SHADOW_RAISED} scaled by any reveal fade
     */
    public static void drawGlassPanel(float x, float y, float x1, float y1,
                                      float radius, int fill, int shadowAlpha) {
        drawGlassPanel(x, y, x1, y1, radius, fill, shadowAlpha,
                GLASS_SHADOW_RAISED_Y, GLASS_SHADOW_RAISED_BLUR);
    }

    /**
     * {@link #drawGlassPanel} with explicit shadow geometry, for chrome-tier
     * surfaces that need a deeper, farther shadow than a raised card.
     */
    public static void drawGlassPanel(float x, float y, float x1, float y1,
                                      float radius, int fill, int shadowAlpha,
                                      float shadowOffsetY, float shadowBlur) {
        if (x1 <= x || y1 <= y) return;

        if (shadowAlpha > 0) {
            // drawSoftShadow draws the shadow only, never the rect, and
            // takes w/h — the conversion lives here so call sites keep
            // one coordinate convention.
            drawSoftShadow(x, y, x1 - x, y1 - y, radius,
                    shadowAlpha, shadowOffsetY, shadowBlur);
        }

        drawRoundedRectAA(x, y, x1, y1, radius, fill);

        // Alpha of the fill drives the edge so a panel fading in doesn't
        // leave a bright outline hanging in the air.
        drawGlassEdge(x, y, x1, y1, radius, (fill >>> 24) & 0xFF);
    }

    /**
     * The lit top rim plus hairline border of the glass material, without a
     * fill or shadow.
     *
     * <p>For panels whose interior is painted in more than one piece — a
     * sidebar beside a content pane, a header strip over a body — where
     * stacking translucent fills would double-darken the glass. Paint the
     * pieces, then close the panel with one call to this so the edge reads as
     * a single sheet.
     *
     * @param alpha 0–255, normally the fill alpha of the pieces
     */
    public static void drawGlassEdge(float x, float y, float x1, float y1,
                                     float radius, int alpha) {
        if (x1 <= x || y1 <= y || alpha <= 0) return;
        float a = clamp255(alpha) / 255.0F;

        // 1-px lit rim just inside the top edge — the glass catches light.
        // This is the single strongest "glass" cue, and it costs one rect.
        int hlA = clamp255((int) (0x26 * a));
        float inset = Math.min(radius * 0.4F, (x1 - x) * 0.2F);
        drawRoundedRectAA(x + inset, y + 0.6F, x1 - inset, y + 1.6F,
                0.5F, (hlA << 24) | 0xFFFFFF);

        // Hairline border.
        int bA = clamp255((int) (0x3A * a));
        drawRoundedOutline(x, y, x1, y1, radius, 1.0F, (bA << 24) | 0xFFFFFF);
    }

    /**
     * {@link #drawGlassPanel} plus a real stencil-clipped backdrop blur.
     *
     * <p>Reserve this for surfaces with no already-blurred backdrop behind
     * them — the main menu, and the HUD layouts that opt into it. Each call
     * costs a full attrib push, a {@code glCopyTexImage2D} reallocation, an
     * unscissored stencil clear and two shader passes, and overlapping calls
     * capture each other, so this is a handful-per-frame primitive.
     *
     * @param alpha master alpha (0–255) driving reveal fades.
     */
    public static void drawFrostedPanel(float x, float y, float x1, float y1,
                                        float radius, int alpha) {
        drawFrostedPanel(x, y, x1, y1, radius, alpha, 8);
    }

    /** {@link #drawFrostedPanel} with an explicit backdrop-blur radius. */
    public static void drawFrostedPanel(float x, float y, float x1, float y1,
                                        float radius, int alpha, int blurRadius) {
        if (x1 <= x || y1 <= y || alpha <= 0) return;
        float a = alpha / 255.0F;

        // Frosted glass: backdrop blur clipped to the rounded rect. Falls
        // back to a flat dark rect internally when FBO/blur is unavailable.
        // Drawn before the shadow so the shadow isn't captured into its own
        // blur and doubled.
        GUIBlurUtil.drawBlurredBackground(Math.round(x), Math.round(y),
                Math.round(x1 - x), Math.round(y1 - y),
                blurRadius, Math.round(radius), a);

        // Tint is lighter than the blur util's own baked 0.38 mix would
        // suggest — the two stack, and at 0xD2 the backdrop was only ~11%
        // visible, which is a dark rect with a rumour of blur rather than
        // glass.
        int tintA = clamp255((int) (0xB8 * a));
        drawGlassPanel(x, y, x1, y1, radius, (tintA << 24) | 0x0E1014,
                clamp255((int) (GLASS_SHADOW_CHROME * a)),
                GLASS_SHADOW_CHROME_Y, GLASS_SHADOW_CHROME_BLUR);
    }

    /** Pre-scale ceiling for non-icon assets (mostly backgrounds). Wide
     *  enough to let menubg.jpg etc. retain detail for fullscreen draw. */
    private static final int RESOURCE_MAX_EDGE = 1024;

    /** Pre-scale ceiling for icon-class assets loaded without a display-size
     *  hint. Callers that know their render size should use
     *  {@link #getResourcePathAtSize}. Icons here are drawn anywhere from 11
     *  to ~48 logical px, and GUI scale 3–4 turns that into up to ~190
     *  physical px, so 128 keeps a >= 1x source at every real draw size while
     *  still cutting the 794x763 crow.png to 64 KB of VRAM. 32 was small
     *  enough to visibly blur the logo on the main menu and click GUIs. */
    private static final int ICON_MAX_EDGE = 128;

    /** Per-size pre-scaled icon cache. Keyed by "{path}@{storeSize}" so
     *  the same source PNG can have separate smaller variants for, say,
     *  the 11-px launcher button vs the 48-px click-GUI splash. */
    private static final Map<String, ResourceLocation> SIZED_RESOURCE_CACHE = new HashMap<>();

    /** Round {@code v} up to the next power of two, with a 16-px floor. */
    private static int nextPowerOfTwoAtLeast16(int v) {
        int n = 16;
        while (n < v && n < 4096) n <<= 1;
        return n;
    }

    /** Load (and cache) an icon pre-scaled in Java with bicubic to a size
     *  picked from the requested render size. The chosen source size is
     *  the next power of two ≥ 2 × displaySize, so GPU GL_LINEAR sees a
     *  ≤ 2× downscale ratio — well within its sweet spot, no stairstep
     *  aliasing.
     *
     *  Example: displaySize=11 → store at 32×32. displaySize=22 → 64×64.
     *  displaySize=48 → 128×128. */
    public static ResourceLocation getResourcePathAtSize(String s, int displaySize) {
        if (s == null || s.isEmpty()) return null;
        int storeSize = nextPowerOfTwoAtLeast16(Math.max(8, displaySize) * 2);
        String key = s + "@" + storeSize;

        ResourceLocation cached = SIZED_RESOURCE_CACHE.get(key);
        if (cached != null) return cached;

        try (InputStream in = HUD.class.getResourceAsStream(s)) {
            if (in == null) return null;
            BufferedImage src = ImageIO.read(in);
            if (src == null) return null;
            BufferedImage scaled = downscaleIfLarge(src, storeSize);

            String textureKey = "crow_sized/" + s.replace('\\', '/')
                    .replace("/", "_")
                    .replace(".", "_") + "_" + storeSize;
            ResourceLocation location = Minecraft.getMinecraft().renderEngine
                    .getDynamicTextureLocation(textureKey, new DynamicTexture(scaled));

            try {
                Minecraft.getMinecraft().getTextureManager().bindTexture(location);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            } catch (Throwable ignored) {}

            SIZED_RESOURCE_CACHE.put(key, location);
            return location;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Bind an icon texture and configure smooth filtering. Use this
     *  instead of {@code bindTexture + glTexParameteri} pairs at every
     *  call site. */
    public static void bindSmoothIcon(ResourceLocation location) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(location);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
    }

    public static ResourceLocation getResourcePath(String s) {
        if (s == null || s.isEmpty()) return null;

        ResourceLocation cached = RESOURCE_CACHE.get(s);
        if (cached != null) return cached;

        try (InputStream resourceInputStream = HUD.class.getResourceAsStream(s)) {
            if (resourceInputStream == null) return null;
            BufferedImage bufferedImage = ImageIO.read(resourceInputStream);
            if (bufferedImage == null) return null;

            // Pre-scale heuristic. Icon-like sources (square-ish and not
            // huge) get clamped to a small max edge with bicubic so they
            // alias gracefully at the typical icon render sizes. Wider
            // assets (backgrounds, photos) keep their resolution up to
            // the larger cap.
            int maxEdge = looksLikeIcon(bufferedImage)
                    ? ICON_MAX_EDGE
                    : RESOURCE_MAX_EDGE;
            bufferedImage = downscaleIfLarge(bufferedImage, maxEdge);

            String textureKey = "crow/" + s.replace('\\', '/')
                    .replace("/", "_")
                    .replace(".", "_");
            ResourceLocation location = Minecraft.getMinecraft().renderEngine
                    .getDynamicTextureLocation(textureKey, new DynamicTexture(bufferedImage));

            // Force linear filter once at load — the filter state persists
            // on the texture object, but bindSmoothIcon also reapplies it
            // every bind so DynamicTexture's default NEAREST never sneaks
            // through.
            try {
                Minecraft.getMinecraft().getTextureManager().bindTexture(location);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            } catch (Throwable ignored) {
            }

            RESOURCE_CACHE.put(s, location);
            return location;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Heuristic: square-ish images at or below the icon cap (or up to
     *  about 1k, accommodating the high-res crow.png) read as icons.
     *  Non-square / very large images are treated as backgrounds. */
    private static boolean looksLikeIcon(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int longEdge = Math.max(w, h);
        int shortEdge = Math.min(w, h);
        // Bail on huge images.
        if (longEdge > 1024) return false;
        // Aspect ratio within 1.4× both ways → icon-shaped.
        return (longEdge * 4) <= (shortEdge * 6);
    }

    /** Bicubic downscale to a max edge, preserving aspect. No-op if the
     *  source is already within the limit. */
    private static BufferedImage downscaleIfLarge(BufferedImage src, int maxEdge) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxEdge && h <= maxEdge) return src;
        float scale = Math.min(maxEdge / (float) w, maxEdge / (float) h);
        int newW = Math.max(1, Math.round(w * scale));
        int newH = Math.max(1, Math.round(h * scale));
        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return out;
    }

    private static int sdfShader = -1;
    private static int sdfU_Color = -1;
    private static int sdfU_Color2 = -1;
    private static int sdfU_CenterPx = -1;
    private static int sdfU_HalfSizePx = -1;
    private static int sdfU_RadiiPx = -1;
    private static int sdfU_ClipCenter = -1;
    private static int sdfU_ClipHalfPx = -1;
    private static int sdfU_ClipRadii = -1;

    /* ---- Rounded clip region, applied to every SDF shape while set. ---- */
    private static boolean clipActive;
    private static float clipX, clipY, clipX1, clipY1, clipRadius;
    private static boolean[] clipCorners = ALL_CORNERS;

    /**
     * Clip every subsequent {@link #drawRoundedRectAA} to a rounded rect,
     * antialiased along the clip edge.
     *
     * <p>The companion to a scissor, not a replacement: a scissor is
     * axis-aligned, so content that runs to the edge of a rounded panel
     * squares off its corners. Doing it here rather than with a stencil mask
     * is what keeps the clipped corner smooth — a stencil is one bit per
     * sample, so masking with one stairsteps the curve.
     *
     * <p>Only shapes drawn through the SDF shader are clipped; text, textures
     * and raw {@code Gui.drawRect} still need the scissor. Always pair with
     * {@link #clearRoundedClip()}.
     *
     * @param round corner mask, {TL, BL, BR, TR}
     */
    public static void setRoundedClip(float x, float y, float x1, float y1,
                                      float radius, boolean[] round) {
        clipActive = x1 > x && y1 > y;
        clipX = x; clipY = y; clipX1 = x1; clipY1 = y1;
        clipRadius = radius;
        clipCorners = round == null ? ALL_CORNERS : round;
    }

    public static void clearRoundedClip() {
        clipActive = false;
    }

    private static int sdfArcShader = -1;
    private static int sdfArcU_Color = -1;
    private static int sdfArcU_CenterPx = -1;
    private static int sdfArcU_RingPx = -1;
    private static int sdfArcU_Angular = -1;

    private static void sdfDiagLog(String msg) {
        try {
            java.io.File dir = Minecraft.getMinecraft().mcDataDir;
            java.io.File f = new java.io.File(dir, "crow-sdf-diagnostic.log");
            try (java.io.FileWriter w = new java.io.FileWriter(f, true)) {
                w.write("[" + System.currentTimeMillis() + "] " + msg + "\n");
            }
        } catch (Throwable ignored) {}
    }

    private static void setupSdfShader() {
        if (sdfShader == -2 || sdfShader > 0) return;
        try {
            int program = GL20.glCreateProgram();
            if (program == 0) {
                sdfDiagLog("glCreateProgram returned 0 — shaders unavailable. Falling back.");
                sdfShader = -2;
                return;
            }

            String vert =
                "#version 120\n" +
                "void main() {\n" +
                "  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
                "}\n";

            String frag =
                "#version 120\n" +
                "uniform vec4 uColor;\n" +
                // Right-hand stop of the horizontal gradient. Equal to uColor
                // for a flat fill, so the mix below costs nothing there.
                "uniform vec4 uColor2;\n" +
                "uniform vec2 uCenterPx;\n" +
                "uniform vec2 uHalfSizePx;\n" +
                "uniform vec4 uRadiiPx;\n" +
                // Rounded clip region. Antialiased in the same pass as the
                // shape, which a stencil mask cannot be — stencil is one bit
                // per sample, so a clipped corner comes out stairstepped.
                // Effectively disabled by uploading a huge half-size.
                "uniform vec2 uClipCenterPx;\n" +
                "uniform vec2 uClipHalfPx;\n" +
                "uniform vec4 uClipRadiiPx;\n" +
                "void main() {\n" +
                "  vec2 p = gl_FragCoord.xy - uCenterPx;\n" +
                "  float r;\n" +
                "  if (p.y > 0.0) r = (p.x > 0.0) ? uRadiiPx.y : uRadiiPx.x;\n" +
                "  else            r = (p.x > 0.0) ? uRadiiPx.z : uRadiiPx.w;\n" +

                "  bool leftAA   = uRadiiPx.x > 0.0 || uRadiiPx.w > 0.0;\n" +
                "  bool rightAA  = uRadiiPx.y > 0.0 || uRadiiPx.z > 0.0;\n" +
                "  bool topAA    = uRadiiPx.x > 0.0 || uRadiiPx.y > 0.0;\n" +
                "  bool bottomAA = uRadiiPx.w > 0.0 || uRadiiPx.z > 0.0;\n" +

                "  vec2 halfSize = uHalfSizePx;\n" +
                "  if (p.x > 0.0 && !rightAA)  halfSize.x = uHalfSizePx.x + r + 4.0;\n" +
                "  if (p.x < 0.0 && !leftAA)   halfSize.x = uHalfSizePx.x + r + 4.0;\n" +
                "  if (p.y > 0.0 && !topAA)    halfSize.y = uHalfSizePx.y + r + 4.0;\n" +
                "  if (p.y < 0.0 && !bottomAA) halfSize.y = uHalfSizePx.y + r + 4.0;\n" +
                "  vec2 q = abs(p) - halfSize + vec2(r);\n" +
                "  float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;\n" +

                "  float aa = clamp(fwidth(d), 0.5, 1.0);\n" +

                "  float alpha = 1.0 - smoothstep(-aa, aa, d);\n" +

                "  float gx = clamp((p.x + uHalfSizePx.x)\n" +
                "                   / max(1.0, 2.0 * uHalfSizePx.x), 0.0, 1.0);\n" +
                "  vec4 col = mix(uColor, uColor2, gx);\n" +

                "  vec2 cp = gl_FragCoord.xy - uClipCenterPx;\n" +
                "  float cr;\n" +
                "  if (cp.y > 0.0) cr = (cp.x > 0.0) ? uClipRadiiPx.y : uClipRadiiPx.x;\n" +
                "  else            cr = (cp.x > 0.0) ? uClipRadiiPx.z : uClipRadiiPx.w;\n" +
                "  vec2 cq = abs(cp) - uClipHalfPx + vec2(cr);\n" +
                "  float cd = length(max(cq, vec2(0.0))) + min(max(cq.x, cq.y), 0.0) - cr;\n" +
                "  float caa = clamp(fwidth(cd), 0.5, 1.0);\n" +
                "  float clipA = 1.0 - smoothstep(-caa, caa, cd);\n" +

                "  gl_FragColor = vec4(col.rgb, col.a * alpha * clipA);\n" +
                "}\n";

            int vId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vId, vert);
            GL20.glCompileShader(vId);
            if (GL20.glGetShaderi(vId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("vertex shader compile failed:\n"
                        + GL20.glGetShaderInfoLog(vId, 4096));
                sdfShader = -2;
                return;
            }
            GL20.glAttachShader(program, vId);

            int fId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fId, frag);
            GL20.glCompileShader(fId);
            if (GL20.glGetShaderi(fId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("fragment shader compile failed:\n"
                        + GL20.glGetShaderInfoLog(fId, 4096));
                sdfShader = -2;
                return;
            }
            GL20.glAttachShader(program, fId);

            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                sdfDiagLog("program link failed:\n"
                        + GL20.glGetProgramInfoLog(program, 4096));
                sdfShader = -2;
                return;
            }

            sdfShader = program;
            sdfU_Color      = GL20.glGetUniformLocation(program, "uColor");
            sdfU_Color2     = GL20.glGetUniformLocation(program, "uColor2");
            sdfU_CenterPx   = GL20.glGetUniformLocation(program, "uCenterPx");
            sdfU_HalfSizePx = GL20.glGetUniformLocation(program, "uHalfSizePx");
            sdfU_RadiiPx    = GL20.glGetUniformLocation(program, "uRadiiPx");
            sdfU_ClipCenter = GL20.glGetUniformLocation(program, "uClipCenterPx");
            sdfU_ClipHalfPx = GL20.glGetUniformLocation(program, "uClipHalfPx");
            sdfU_ClipRadii  = GL20.glGetUniformLocation(program, "uClipRadiiPx");
            sdfDiagLog("shader compiled OK (program=" + program
                    + ", uniforms color=" + sdfU_Color + " center=" + sdfU_CenterPx
                    + " halfSize=" + sdfU_HalfSizePx + " radii=" + sdfU_RadiiPx + ")");
        } catch (Throwable t) {
            sdfDiagLog("setup threw: " + t);
            sdfShader = -2;
        }
    }

    private static boolean sdfFirstCallLogged = false;

    private static boolean drawSdfShape(float x, float y, float x1, float y1,
                                         float halfWGui, float halfHGui,
                                         float radiusTLGui, float radiusBLGui,
                                         float radiusBRGui, float radiusTRGui,
                                         float r, float g, float b, float a) {
        return drawSdfShape(x, y, x1, y1, halfWGui, halfHGui,
                radiusTLGui, radiusBLGui, radiusBRGui, radiusTRGui,
                r, g, b, a, r, g, b, a);
    }

    private static boolean drawSdfShape(float x, float y, float x1, float y1,
                                         float halfWGui, float halfHGui,
                                         float radiusTLGui, float radiusBLGui,
                                         float radiusBRGui, float radiusTRGui,
                                         float r, float g, float b, float a,
                                         float r2, float g2, float b2, float a2) {

        java.nio.FloatBuffer mv = SCRATCH_MV; mv.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        java.nio.FloatBuffer pr = SCRATCH_PR; pr.rewind();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, pr);
        java.nio.IntBuffer vp = SCRATCH_VP; vp.rewind();
        GL11.glGetInteger(GL11.GL_VIEWPORT, vp);

        float mvSx = mv.get(0),  mvSy = mv.get(5);
        float mvTx = mv.get(12), mvTy = mv.get(13);
        float pSx  = pr.get(0),  pSy  = pr.get(5);
        float pTx  = pr.get(12), pTy  = pr.get(13);
        int vpX = vp.get(0), vpY = vp.get(1);
        int vpW = vp.get(2), vpH = vp.get(3);
        if (vpW <= 0 || vpH <= 0) return false;

        float cxGui = (x + x1) * 0.5F;
        float cyGui = (y + y1) * 0.5F;

        float xClipC = pSx * (mvSx * cxGui + mvTx) + pTx;
        float yClipC = pSy * (mvSy * cyGui + mvTy) + pTy;
        float cxPx = (xClipC + 1.0F) * 0.5F * vpW + vpX;
        float cyPx = (yClipC + 1.0F) * 0.5F * vpH + vpY;

        float pxPerGuiX = Math.abs(mvSx * pSx) * vpW * 0.5F;
        float pxPerGuiY = Math.abs(mvSy * pSy) * vpH * 0.5F;
        if (pxPerGuiX <= 0.0F || pxPerGuiY <= 0.0F) return false;

        float halfWPx = halfWGui * pxPerGuiX;
        float halfHPx = halfHGui * pxPerGuiY;

        float scale = Math.min(pxPerGuiX, pxPerGuiY);
        float maxR  = Math.min(halfWPx, halfHPx);

        float rTLPx = Math.max(0.0F, Math.min(radiusTLGui * scale, maxR));
        float rBLPx = Math.max(0.0F, Math.min(radiusBLGui * scale, maxR));
        float rBRPx = Math.max(0.0F, Math.min(radiusBRGui * scale, maxR));
        float rTRPx = Math.max(0.0F, Math.min(radiusTRGui * scale, maxR));

        GL20.glUniform4f(sdfU_Color, r, g, b, a);
        GL20.glUniform4f(sdfU_Color2, r2, g2, b2, a2);
        GL20.glUniform2f(sdfU_CenterPx, cxPx, cyPx);
        GL20.glUniform2f(sdfU_HalfSizePx, halfWPx, halfHPx);

        GL20.glUniform4f(sdfU_RadiiPx, rTLPx, rTRPx, rBRPx, rBLPx);

        // Clip uniforms go up every call: the shader is shared, so leaving a
        // stale clip set would silently carve up whatever draws next.
        if (clipActive) {
            float ccxGui = (clipX + clipX1) * 0.5F;
            float ccyGui = (clipY + clipY1) * 0.5F;
            float ccxPx = ((pSx * (mvSx * ccxGui + mvTx) + pTx) + 1.0F) * 0.5F * vpW + vpX;
            float ccyPx = ((pSy * (mvSy * ccyGui + mvTy) + pTy) + 1.0F) * 0.5F * vpH + vpY;
            float chwPx = (clipX1 - clipX) * 0.5F * pxPerGuiX;
            float chhPx = (clipY1 - clipY) * 0.5F * pxPerGuiY;
            float cR = Math.max(0.0F, Math.min(clipRadius * scale, Math.min(chwPx, chhPx)));
            GL20.glUniform2f(sdfU_ClipCenter, ccxPx, ccyPx);
            GL20.glUniform2f(sdfU_ClipHalfPx, chwPx, chhPx);
            GL20.glUniform4f(sdfU_ClipRadii,
                    clipCorners[0] ? cR : 0.0F, clipCorners[3] ? cR : 0.0F,
                    clipCorners[2] ? cR : 0.0F, clipCorners[1] ? cR : 0.0F);
        } else {
            GL20.glUniform2f(sdfU_ClipCenter, 0.0F, 0.0F);
            GL20.glUniform2f(sdfU_ClipHalfPx, 1.0E7F, 1.0E7F);
            GL20.glUniform4f(sdfU_ClipRadii, 0.0F, 0.0F, 0.0F, 0.0F);
        }

        boolean leftAA   = radiusTLGui > 0.0F || radiusBLGui > 0.0F;
        boolean rightAA  = radiusTRGui > 0.0F || radiusBRGui > 0.0F;
        boolean topAA    = radiusTLGui > 0.0F || radiusTRGui > 0.0F;
        boolean bottomAA = radiusBLGui > 0.0F || radiusBRGui > 0.0F;

        float padGuiX = Math.max(0.5F, 1.0F / Math.max(1.0F, pxPerGuiX));
        float padGuiY = Math.max(0.5F, 1.0F / Math.max(1.0F, pxPerGuiY));
        float padLeft   = leftAA   ? padGuiX : 0.0F;
        float padRight  = rightAA  ? padGuiX : 0.0F;
        float padTop    = topAA    ? padGuiY : 0.0F;
        float padBottom = bottomAA ? padGuiY : 0.0F;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x  - padLeft,  y  - padTop);
        GL11.glVertex2f(x  - padLeft,  y1 + padBottom);
        GL11.glVertex2f(x1 + padRight, y1 + padBottom);
        GL11.glVertex2f(x1 + padRight, y  - padTop);
        GL11.glEnd();

        return true;
    }

    public static void drawRoundedRectAA(float x, float y, float x1, float y1, float radius, int color) {
        drawRoundedRectAA(x, y, x1, y1, radius, color, ALL_CORNERS);
    }

    public static void drawRoundedRectAA(float x, float y, float x1, float y1, float radius, int color, boolean[] round) {
        drawRoundedRectAA(x, y, x1, y1, radius, color, color, round);
    }

    /**
     * Rounded rect with a left-to-right two-stop gradient, antialiased and
     * honouring {@link #setRoundedClip}.
     *
     * <p>Horizontal only, deliberately: a vertical sheen shades the top and
     * bottom edges of a row differently, which draws a seam between two
     * stacked rows that are in the same state. Pass the same colour twice for
     * a flat fill.
     */
    public static void drawRoundedRectAA(float x, float y, float x1, float y1, float radius,
                                         int color, int color2, boolean[] round) {
        if (x1 <= x || y1 <= y) return;
        int alphaInt = (color >> 24) & 0xFF;
        int alphaInt2 = (color2 >> 24) & 0xFF;
        if (alphaInt <= 0 && alphaInt2 <= 0) return;
        if (round == null) round = ALL_CORNERS;

        setupSdfShader();
        if (sdfShader <= 0) {
            if (!sdfFirstCallLogged) {
                sdfDiagLog("FALLBACK path (shader unavailable) for rect "
                        + x + "," + y + " " + x1 + "," + y1 + " r=" + radius
                        + " (sdfShader state=" + sdfShader + ")");
                sdfFirstCallLogged = true;
            }

            drawRoundedRect(x, y, x1, y1, radius, color, round);
            return;
        }
        if (!sdfFirstCallLogged) {
            java.nio.IntBuffer dbgVp = SCRATCH_VP; dbgVp.rewind();
            GL11.glGetInteger(GL11.GL_VIEWPORT, dbgVp);
            sdfDiagLog("AA path active. rect=(" + x + "," + y + ")-("
                    + x1 + "," + y1 + ") r=" + radius
                    + " viewport=(" + dbgVp.get(0) + "," + dbgVp.get(1) + ","
                    + dbgVp.get(2) + "x" + dbgVp.get(3) + ")");
            sdfFirstCallLogged = true;
        }

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean prevTex2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();

        GlStateManager.tryBlendFuncSeparate(770, 771, 0, 1);

        GL20.glUseProgram(sdfShader);

        float halfW = (x1 - x) * 0.5F;
        float halfH = (y1 - y) * 0.5F;

        float r = Math.max(0.0F, Math.min(radius, Math.min(halfW, halfH)));

        float rTL = round[0] ? r : 0.0F;
        float rBL = round[1] ? r : 0.0F;
        float rBR = round[2] ? r : 0.0F;
        float rTR = round[3] ? r : 0.0F;

        float a = alphaInt / 255.0F;
        float cr = ((color >> 16) & 0xFF) / 255.0F;
        float cg = ((color >> 8)  & 0xFF) / 255.0F;
        float cb = ( color        & 0xFF) / 255.0F;

        float a2 = alphaInt2 / 255.0F;
        float cr2 = ((color2 >> 16) & 0xFF) / 255.0F;
        float cg2 = ((color2 >> 8)  & 0xFF) / 255.0F;
        float cb2 = ( color2        & 0xFF) / 255.0F;

        boolean ok = drawSdfShape(x, y, x1, y1, halfW, halfH,
                                   rTL, rBL, rBR, rTR,
                                   cr, cg, cb, a, cr2, cg2, cb2, a2);

        GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (prevTex2D) GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (!ok) {

            drawRoundedRect(x, y, x1, y1, radius, color, round);
        }
    }

    public static void drawCircleAA(float cx, float cy, float radius, int color) {
        if (radius <= 0.0F) return;
        int alphaInt = (color >> 24) & 0xFF;
        if (alphaInt <= 0) return;

        setupSdfShader();
        if (sdfShader <= 0) {
            drawFilledCircle(cx, cy, radius, color);
            return;
        }

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean prevTex2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 0, 1);

        GL20.glUseProgram(sdfShader);

        float a = alphaInt / 255.0F;
        float cr = ((color >> 16) & 0xFF) / 255.0F;
        float cg = ((color >> 8)  & 0xFF) / 255.0F;
        float cb = ( color        & 0xFF) / 255.0F;

        boolean ok = drawSdfShape(cx - radius, cy - radius, cx + radius, cy + radius,
                                   radius, radius,
                                   radius, radius, radius, radius, cr, cg, cb, a);

        GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (prevTex2D) GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (!ok) {
            drawFilledCircle(cx, cy, radius, color);
        }
    }

    private static void setupSdfArcShader() {
        if (sdfArcShader == -2 || sdfArcShader > 0) return;
        try {
            int program = GL20.glCreateProgram();
            if (program == 0) {
                sdfDiagLog("arc shader: glCreateProgram returned 0");
                sdfArcShader = -2;
                return;
            }
            String vert =
                "#version 120\n" +
                "void main() { gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex; }\n";

            String frag =
                "#version 120\n" +
                "uniform vec4 uColor;\n" +
                "uniform vec2 uCenterPx;\n" +
                "uniform vec2 uRingPx;\n" +
                "uniform vec3 uAngular;\n" +
                "void main() {\n" +
                "  vec2 p = gl_FragCoord.xy - uCenterPx;\n" +
                "  float pLen = length(p);\n" +
                "  float dRadial = abs(pLen - uRingPx.x) - uRingPx.y;\n" +
                "  float dAngular;\n" +
                "  if (uAngular.z > 3.13) {\n" +
                "    dAngular = -1e9;\n" +
                "  } else {\n" +
                "    vec2 bis = vec2(uAngular.x, uAngular.y);\n" +
                "    float dotB = dot(p, bis);\n" +
                "    float crsB = p.x * bis.y - p.y * bis.x;\n" +
                "    float ang = atan(abs(crsB), dotB);\n" +
                "    if (ang <= uAngular.z) {\n" +
                "      dAngular = -pLen * sin(uAngular.z - ang);\n" +
                "    } else {\n" +
                "      dAngular = pLen * sin(ang - uAngular.z);\n" +
                "    }\n" +
                "  }\n" +
                "  float dist = max(dRadial, dAngular);\n" +

                "  float aa = clamp(fwidth(dist), 0.5, 1.0);\n" +
                "  float alpha = 1.0 - smoothstep(-aa, aa, dist);\n" +
                "  gl_FragColor = vec4(uColor.rgb, uColor.a * alpha);\n" +
                "}\n";

            int vId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vId, vert);
            GL20.glCompileShader(vId);
            if (GL20.glGetShaderi(vId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("arc vertex shader compile failed:\n" + GL20.glGetShaderInfoLog(vId, 4096));
                sdfArcShader = -2;
                return;
            }
            GL20.glAttachShader(program, vId);

            int fId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fId, frag);
            GL20.glCompileShader(fId);
            if (GL20.glGetShaderi(fId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("arc fragment shader compile failed:\n" + GL20.glGetShaderInfoLog(fId, 4096));
                sdfArcShader = -2;
                return;
            }
            GL20.glAttachShader(program, fId);

            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                sdfDiagLog("arc program link failed:\n" + GL20.glGetProgramInfoLog(program, 4096));
                sdfArcShader = -2;
                return;
            }

            sdfArcShader = program;
            sdfArcU_Color    = GL20.glGetUniformLocation(program, "uColor");
            sdfArcU_CenterPx = GL20.glGetUniformLocation(program, "uCenterPx");
            sdfArcU_RingPx   = GL20.glGetUniformLocation(program, "uRingPx");
            sdfArcU_Angular  = GL20.glGetUniformLocation(program, "uAngular");
            sdfDiagLog("arc shader compiled OK (program=" + program + ")");
        } catch (Throwable t) {
            sdfDiagLog("arc shader setup threw: " + t);
            sdfArcShader = -2;
        }
    }

    public static void drawArcAA(float cx, float cy, float outerR, float innerR,
                                  float startDeg, float endDeg, int color) {
        if (outerR <= 0) return;
        int alphaInt = (color >> 24) & 0xFF;
        if (alphaInt <= 0) return;
        if (innerR < 0) innerR = 0;
        if (innerR > outerR) { float t = innerR; innerR = outerR; outerR = t; }

        setupSdfArcShader();
        if (sdfArcShader <= 0) {
            drawArc(cx, cy, outerR, innerR, startDeg, endDeg, color);
            return;
        }

        java.nio.FloatBuffer mv = SCRATCH_MV; mv.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        java.nio.FloatBuffer pr = SCRATCH_PR; pr.rewind();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, pr);
        java.nio.IntBuffer vp = SCRATCH_VP; vp.rewind();
        GL11.glGetInteger(GL11.GL_VIEWPORT, vp);

        float mvSx = mv.get(0),  mvSy = mv.get(5);
        float mvTx = mv.get(12), mvTy = mv.get(13);
        float pSx  = pr.get(0),  pSy  = pr.get(5);
        float pTx  = pr.get(12), pTy  = pr.get(13);
        int vpX = vp.get(0), vpY = vp.get(1);
        int vpW = vp.get(2), vpH = vp.get(3);
        if (vpW <= 0 || vpH <= 0) {
            drawArc(cx, cy, outerR, innerR, startDeg, endDeg, color);
            return;
        }

        float xClipC = pSx * (mvSx * cx + mvTx) + pTx;
        float yClipC = pSy * (mvSy * cy + mvTy) + pTy;
        float cxPx = (xClipC + 1.0F) * 0.5F * vpW + vpX;
        float cyPx = (yClipC + 1.0F) * 0.5F * vpH + vpY;

        float pxPerGuiX = Math.abs(mvSx * pSx) * vpW * 0.5F;
        float pxPerGuiY = Math.abs(mvSy * pSy) * vpH * 0.5F;
        if (pxPerGuiX <= 0.0F || pxPerGuiY <= 0.0F) {
            drawArc(cx, cy, outerR, innerR, startDeg, endDeg, color);
            return;
        }
        float scale = Math.min(pxPerGuiX, pxPerGuiY);

        float midRPx = (outerR + innerR) * 0.5F * scale;
        float halfThicknessPx = (outerR - innerR) * 0.5F * scale;

        float halfAngleRad;
        float bisectorCos, bisectorSin;
        float arcLength = Math.abs(endDeg - startDeg);
        if (arcLength >= 359.99F) {

            halfAngleRad = 4.0F;
            bisectorCos = 1.0F;
            bisectorSin = 0.0F;
        } else {
            halfAngleRad = (float) Math.toRadians(arcLength * 0.5F);
            float bisectorRadGui = (float) Math.toRadians((startDeg + endDeg) * 0.5);
            bisectorCos = (float) Math.cos(bisectorRadGui);

            bisectorSin = -(float) Math.sin(bisectorRadGui);
        }

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean prevTex2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 0, 1);

        GL20.glUseProgram(sdfArcShader);

        float a = alphaInt / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8)  & 0xFF) / 255.0F;
        float b = ( color        & 0xFF) / 255.0F;
        GL20.glUniform4f(sdfArcU_Color, r, g, b, a);
        GL20.glUniform2f(sdfArcU_CenterPx, cxPx, cyPx);
        GL20.glUniform2f(sdfArcU_RingPx, midRPx, halfThicknessPx);
        GL20.glUniform3f(sdfArcU_Angular, bisectorCos, bisectorSin, halfAngleRad);

        float padGui = Math.max(1.0F, 2.0F / scale);
        float bound = outerR + padGui;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(cx - bound, cy - bound);
        GL11.glVertex2f(cx - bound, cy + bound);
        GL11.glVertex2f(cx + bound, cy + bound);
        GL11.glVertex2f(cx + bound, cy - bound);
        GL11.glEnd();

        GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (prevTex2D) GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int shadowShader = -1;
    private static int shadowU_Color = -1;
    private static int shadowU_CenterPx = -1;
    private static int shadowU_HalfSizePx = -1;
    private static int shadowU_RadiusPx = -1;
    private static int shadowU_BlurPx = -1;

    private static void setupShadowShader() {
        if (shadowShader == -2 || shadowShader > 0) return;
        try {
            int program = GL20.glCreateProgram();
            if (program == 0) {
                sdfDiagLog("shadow shader: glCreateProgram returned 0");
                shadowShader = -2;
                return;
            }
            String vert =
                "#version 120\n" +
                "void main() { gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex; }\n";
            String frag =
                "#version 120\n" +
                "uniform vec4 uColor;\n" +
                "uniform vec2 uCenterPx;\n" +
                "uniform vec2 uHalfSizePx;\n" +
                "uniform float uRadiusPx;\n" +
                "uniform float uBlurPx;\n" +
                "void main() {\n" +

                "  vec2 p = gl_FragCoord.xy - uCenterPx;\n" +
                "  vec2 q = abs(p) - uHalfSizePx + vec2(uRadiusPx);\n" +
                "  float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - uRadiusPx;\n" +

                // Gaussian-CDF falloff centered on the silhouette, like a
                // real blurred silhouette (CSS box-shadow). The old
                // exp(-t*t) held ~full alpha for the first half-sigma past
                // the edge, which rendered as a hard dark rim — a stretched
                // copy of the panel — instead of a blur.
                "  float sigma = max(uBlurPx, 0.5);\n" +
                "  float alpha = uColor.a * (1.0 - smoothstep(-sigma, sigma, d));\n" +
                // Outer shadow only, like a non-inset CSS box-shadow. Without
                // this the interior is filled, which is invisible under an
                // opaque panel but shows straight through a translucent one
                // and muddies the glass.
                "  alpha *= smoothstep(-1.0, 0.5, d);\n" +
                "  gl_FragColor = vec4(uColor.rgb, alpha);\n" +
                "}\n";

            int vId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vId, vert);
            GL20.glCompileShader(vId);
            if (GL20.glGetShaderi(vId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("shadow vertex compile failed:\n" + GL20.glGetShaderInfoLog(vId, 4096));
                shadowShader = -2;
                return;
            }
            GL20.glAttachShader(program, vId);

            int fId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fId, frag);
            GL20.glCompileShader(fId);
            if (GL20.glGetShaderi(fId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("shadow fragment compile failed:\n" + GL20.glGetShaderInfoLog(fId, 4096));
                shadowShader = -2;
                return;
            }
            GL20.glAttachShader(program, fId);

            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                sdfDiagLog("shadow program link failed:\n" + GL20.glGetProgramInfoLog(program, 4096));
                shadowShader = -2;
                return;
            }

            shadowShader      = program;
            shadowU_Color     = GL20.glGetUniformLocation(program, "uColor");
            shadowU_CenterPx  = GL20.glGetUniformLocation(program, "uCenterPx");
            shadowU_HalfSizePx = GL20.glGetUniformLocation(program, "uHalfSizePx");
            shadowU_RadiusPx  = GL20.glGetUniformLocation(program, "uRadiusPx");
            shadowU_BlurPx    = GL20.glGetUniformLocation(program, "uBlurPx");
            sdfDiagLog("shadow shader compiled OK (program=" + program + ")");
        } catch (Throwable t) {
            sdfDiagLog("shadow shader setup threw: " + t);
            shadowShader = -2;
        }
    }

    public static void drawShadowedRoundedRect(float x, float y, float w, float h,
                                                float radius, int shadowColor,
                                                float shadowOffsetX, float shadowOffsetY,
                                                float shadowBlur) {
        if (w <= 0 || h <= 0 || shadowBlur <= 0) return;
        int aInt = (shadowColor >> 24) & 0xFF;
        if (aInt <= 0) return;

        setupShadowShader();
        if (shadowShader <= 0) {

            drawStackedShadowFallback(x, y, w, h, radius, shadowColor,
                                       shadowOffsetX, shadowOffsetY, shadowBlur);
            return;
        }

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean prevTex2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();

        GlStateManager.tryBlendFuncSeparate(770, 771, 0, 1);

        GL20.glUseProgram(shadowShader);

        java.nio.FloatBuffer mv = SCRATCH_MV; mv.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        java.nio.FloatBuffer pr = SCRATCH_PR; pr.rewind();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, pr);
        java.nio.IntBuffer vp = SCRATCH_VP; vp.rewind();
        GL11.glGetInteger(GL11.GL_VIEWPORT, vp);

        float mvSx = mv.get(0),  mvSy = mv.get(5);
        float mvTx = mv.get(12), mvTy = mv.get(13);
        float pSx  = pr.get(0),  pSy  = pr.get(5);
        float pTx  = pr.get(12), pTy  = pr.get(13);
        int vpX = vp.get(0), vpY = vp.get(1);
        int vpW = vp.get(2), vpH = vp.get(3);
        if (vpW <= 0 || vpH <= 0) {
            GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            if (prevTex2D) GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }

        float halfWGui = w * 0.5F;
        float halfHGui = h * 0.5F;
        float cxGui = x + halfWGui + shadowOffsetX;
        float cyGui = y + halfHGui + shadowOffsetY;

        float xClipC = pSx * (mvSx * cxGui + mvTx) + pTx;
        float yClipC = pSy * (mvSy * cyGui + mvTy) + pTy;
        float cxPx = (xClipC + 1.0F) * 0.5F * vpW + vpX;
        float cyPx = (yClipC + 1.0F) * 0.5F * vpH + vpY;

        float pxPerGuiX = Math.abs(mvSx * pSx) * vpW * 0.5F;
        float pxPerGuiY = Math.abs(mvSy * pSy) * vpH * 0.5F;
        if (pxPerGuiX <= 0.0F || pxPerGuiY <= 0.0F) {
            GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            if (prevTex2D) GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        float scale = Math.min(pxPerGuiX, pxPerGuiY);

        float halfWPx = halfWGui * pxPerGuiX;
        float halfHPx = halfHGui * pxPerGuiY;
        float radiusPx = Math.max(0.0F, Math.min(radius * scale, Math.min(halfWPx, halfHPx)));
        float blurPx = Math.max(1.0F, shadowBlur * scale);

        float aF = aInt / 255.0F;
        float rF = ((shadowColor >> 16) & 0xFF) / 255.0F;
        float gF = ((shadowColor >>  8) & 0xFF) / 255.0F;
        float bF = ( shadowColor        & 0xFF) / 255.0F;

        GL20.glUniform4f(shadowU_Color, rF, gF, bF, aF);
        GL20.glUniform2f(shadowU_CenterPx, cxPx, cyPx);
        GL20.glUniform2f(shadowU_HalfSizePx, halfWPx, halfHPx);
        GL20.glUniform1f(shadowU_RadiusPx, radiusPx);
        GL20.glUniform1f(shadowU_BlurPx, blurPx);

        // The CDF falloff hits zero at exactly one sigma past the edge, so
        // anything beyond blur + a pixel of AA margin is wasted fill.
        float padGui = shadowBlur * 1.25F + 2.0F / Math.max(1.0F, scale);
        float sx0 = x + shadowOffsetX - padGui;
        float sy0 = y + shadowOffsetY - padGui;
        float sx1 = x + shadowOffsetX + w + padGui;
        float sy1 = y + shadowOffsetY + h + padGui;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(sx0, sy0);
        GL11.glVertex2f(sx0, sy1);
        GL11.glVertex2f(sx1, sy1);
        GL11.glVertex2f(sx1, sy0);
        GL11.glEnd();

        GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (prevTex2D) GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int textRectShader = -1;
    private static int textRectU_Color      = -1;
    private static int textRectU_Tex        = -1;
    private static int textRectU_CenterPx   = -1;
    private static int textRectU_HalfSizePx = -1;
    private static int textRectU_RadiusPx   = -1;

    private static void setupTextRectShader() {
        if (textRectShader == -2 || textRectShader > 0) return;
        try {
            int program = GL20.glCreateProgram();
            if (program == 0) { textRectShader = -2; return; }
            String vert =
                "#version 120\n" +
                "void main() {\n" +
                "  gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
                "  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
                "}\n";
            String frag =
                "#version 120\n" +
                "uniform sampler2D uTex;\n" +
                "uniform vec4 uColor;\n" +
                "uniform vec2 uCenterPx;\n" +
                "uniform vec2 uHalfSizePx;\n" +
                "uniform float uRadiusPx;\n" +
                "void main() {\n" +
                "  vec2 p = gl_FragCoord.xy - uCenterPx;\n" +
                "  vec2 q = abs(p) - uHalfSizePx + vec2(uRadiusPx);\n" +
                "  float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - uRadiusPx;\n" +
                "  float aa = clamp(fwidth(d), 0.5, 1.0);\n" +
                "  float clipA = 1.0 - smoothstep(-aa, aa, d);\n" +
                "  vec4 tex = texture2D(uTex, gl_TexCoord[0].st);\n" +
                "  gl_FragColor = vec4(tex.rgb * uColor.rgb, tex.a * uColor.a * clipA);\n" +
                "}\n";

            int vId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vId, vert);
            GL20.glCompileShader(vId);
            if (GL20.glGetShaderi(vId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("textRect vertex compile failed:\n" + GL20.glGetShaderInfoLog(vId, 4096));
                textRectShader = -2; return;
            }
            GL20.glAttachShader(program, vId);

            int fId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fId, frag);
            GL20.glCompileShader(fId);
            if (GL20.glGetShaderi(fId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("textRect fragment compile failed:\n" + GL20.glGetShaderInfoLog(fId, 4096));
                textRectShader = -2; return;
            }
            GL20.glAttachShader(program, fId);

            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                sdfDiagLog("textRect link failed:\n" + GL20.glGetProgramInfoLog(program, 4096));
                textRectShader = -2; return;
            }

            textRectShader      = program;
            textRectU_Color     = GL20.glGetUniformLocation(program, "uColor");
            textRectU_Tex       = GL20.glGetUniformLocation(program, "uTex");
            textRectU_CenterPx  = GL20.glGetUniformLocation(program, "uCenterPx");
            textRectU_HalfSizePx= GL20.glGetUniformLocation(program, "uHalfSizePx");
            textRectU_RadiusPx  = GL20.glGetUniformLocation(program, "uRadiusPx");
        } catch (Throwable t) {
            textRectShader = -2;
        }
    }

    public static boolean isTexturedRectShaderAvailable() {
        setupTextRectShader();
        return textRectShader > 0;
    }

    public static void drawRoundedTexturedRect(float x, float y, float w, float h, float radius,
                                                 float u0, float v0, float u1, float v1,
                                                 int tintColor) {
        if (w <= 0 || h <= 0) return;
        int aInt = (tintColor >>> 24) & 0xFF;
        if (aInt == 0) aInt = 255;

        setupTextRectShader();
        if (textRectShader <= 0) {
            // Shaderless fallback: plain textured quad, no corner rounding.
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(((tintColor >> 16) & 0xFF) / 255.0F,
                    ((tintColor >> 8) & 0xFF) / 255.0F,
                    (tintColor & 0xFF) / 255.0F, aInt / 255.0F);
            net.minecraft.client.renderer.Tessellator tess =
                    net.minecraft.client.renderer.Tessellator.getInstance();
            net.minecraft.client.renderer.WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
            wr.pos(x,     y + h, 0.0D).tex(u0, v1).endVertex();
            wr.pos(x + w, y + h, 0.0D).tex(u1, v1).endVertex();
            wr.pos(x + w, y,     0.0D).tex(u1, v0).endVertex();
            wr.pos(x,     y,     0.0D).tex(u0, v0).endVertex();
            tess.draw();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        GL20.glUseProgram(textRectShader);
        GL20.glUniform1i(textRectU_Tex, 0);

        java.nio.FloatBuffer mv = SCRATCH_MV; mv.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        java.nio.FloatBuffer pr = SCRATCH_PR; pr.rewind();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, pr);
        java.nio.IntBuffer vp = SCRATCH_VP; vp.rewind();
        GL11.glGetInteger(GL11.GL_VIEWPORT, vp);

        float mvSx = mv.get(0),  mvSy = mv.get(5);
        float mvTx = mv.get(12), mvTy = mv.get(13);
        float pSx  = pr.get(0),  pSy  = pr.get(5);
        float pTx  = pr.get(12), pTy  = pr.get(13);
        int vpX = vp.get(0), vpY = vp.get(1);
        int vpW = vp.get(2), vpH = vp.get(3);
        if (vpW <= 0 || vpH <= 0) {
            GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
            return;
        }

        float cxGui = x + w * 0.5F;
        float cyGui = y + h * 0.5F;
        float xClipC = pSx * (mvSx * cxGui + mvTx) + pTx;
        float yClipC = pSy * (mvSy * cyGui + mvTy) + pTy;
        float cxPx = (xClipC + 1.0F) * 0.5F * vpW + vpX;
        float cyPx = (yClipC + 1.0F) * 0.5F * vpH + vpY;

        float pxPerGuiX = Math.abs(mvSx * pSx) * vpW * 0.5F;
        float pxPerGuiY = Math.abs(mvSy * pSy) * vpH * 0.5F;
        float scale = Math.min(pxPerGuiX, pxPerGuiY);

        float halfWPx  = (w * 0.5F) * pxPerGuiX;
        float halfHPx  = (h * 0.5F) * pxPerGuiY;
        float radiusPx = Math.max(0.0F, Math.min(radius * scale, Math.min(halfWPx, halfHPx)));

        float aF = aInt / 255.0F;
        float rF = ((tintColor >>> 16) & 0xFF) / 255.0F;
        float gF = ((tintColor >>>  8) & 0xFF) / 255.0F;
        float bF = ( tintColor          & 0xFF) / 255.0F;

        GL20.glUniform4f(textRectU_Color, rF, gF, bF, aF);
        GL20.glUniform2f(textRectU_CenterPx, cxPx, cyPx);
        GL20.glUniform2f(textRectU_HalfSizePx, halfWPx, halfHPx);
        GL20.glUniform1f(textRectU_RadiusPx, radiusPx);

        float padGui = 1.0F / Math.max(1.0F, scale);
        float qx0 = x - padGui;
        float qy0 = y - padGui;
        float qx1 = x + w + padGui;
        float qy1 = y + h + padGui;

        float uPad = (u1 - u0) * padGui / Math.max(1.0F, w);
        float vPad = (v1 - v0) * padGui / Math.max(1.0F, h);
        float u0p = u0 - uPad, u1p = u1 + uPad;
        float v0p = v0 - vPad, v1p = v1 + vPad;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0p, v0p); GL11.glVertex2f(qx0, qy0);
        GL11.glTexCoord2f(u0p, v1p); GL11.glVertex2f(qx0, qy1);
        GL11.glTexCoord2f(u1p, v1p); GL11.glVertex2f(qx1, qy1);
        GL11.glTexCoord2f(u1p, v0p); GL11.glVertex2f(qx1, qy0);
        GL11.glEnd();

        GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int chevronShader = -1;
    private static int chevronU_Color = -1;
    private static int chevronU_A1 = -1;
    private static int chevronU_A2 = -1;
    private static int chevronU_B1 = -1;
    private static int chevronU_B2 = -1;
    private static int chevronU_HalfThickness = -1;

    private static void setupChevronShader() {
        if (chevronShader == -2 || chevronShader > 0) return;
        try {
            int program = GL20.glCreateProgram();
            if (program == 0) { chevronShader = -2; return; }
            String vert =
                "#version 120\n" +
                "void main() { gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex; }\n";

            String frag =
                "#version 120\n" +
                "uniform vec4 uColor;\n" +
                "uniform vec2 uA1;\n" +
                "uniform vec2 uA2;\n" +
                "uniform vec2 uB1;\n" +
                "uniform vec2 uB2;\n" +
                "uniform float uHalfThickness;\n" +
                "float sdSegment(vec2 p, vec2 a, vec2 b) {\n" +
                "  vec2 pa = p - a;\n" +
                "  vec2 ba = b - a;\n" +
                "  float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);\n" +
                "  return length(pa - ba * h);\n" +
                "}\n" +
                "void main() {\n" +
                "  vec2 p = gl_FragCoord.xy;\n" +
                "  float dA = sdSegment(p, uA1, uA2) - uHalfThickness;\n" +
                "  float dB = sdSegment(p, uB1, uB2) - uHalfThickness;\n" +
                "  float d = min(dA, dB);\n" +
                "  float aa = clamp(fwidth(d), 0.5, 1.0);\n" +
                "  float alpha = 1.0 - smoothstep(-aa, aa, d);\n" +
                "  gl_FragColor = vec4(uColor.rgb, uColor.a * alpha);\n" +
                "}\n";

            int vId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vId, vert);
            GL20.glCompileShader(vId);
            if (GL20.glGetShaderi(vId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("chevron vertex compile failed:\n" + GL20.glGetShaderInfoLog(vId, 4096));
                chevronShader = -2; return;
            }
            GL20.glAttachShader(program, vId);

            int fId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fId, frag);
            GL20.glCompileShader(fId);
            if (GL20.glGetShaderi(fId, GL20.GL_COMPILE_STATUS) == 0) {
                sdfDiagLog("chevron fragment compile failed:\n" + GL20.glGetShaderInfoLog(fId, 4096));
                chevronShader = -2; return;
            }
            GL20.glAttachShader(program, fId);

            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                sdfDiagLog("chevron link failed:\n" + GL20.glGetProgramInfoLog(program, 4096));
                chevronShader = -2; return;
            }

            chevronShader = program;
            chevronU_Color         = GL20.glGetUniformLocation(program, "uColor");
            chevronU_A1            = GL20.glGetUniformLocation(program, "uA1");
            chevronU_A2            = GL20.glGetUniformLocation(program, "uA2");
            chevronU_B1            = GL20.glGetUniformLocation(program, "uB1");
            chevronU_B2            = GL20.glGetUniformLocation(program, "uB2");
            chevronU_HalfThickness = GL20.glGetUniformLocation(program, "uHalfThickness");
        } catch (Throwable t) {
            chevronShader = -2;
        }
    }

    public enum ChevronDirection {
        DOWN(0.0F),
        UP(180.0F),
        RIGHT(270.0F),
        LEFT(90.0F);

        public final float angleDeg;
        ChevronDirection(float angleDeg) { this.angleDeg = angleDeg; }
    }

    public static void drawChevron(float cx, float cy, float size, ChevronDirection dir,
                                    int color, float thickness) {
        drawChevronRotated(cx, cy, size, dir.angleDeg, color, thickness);
    }

    public static void drawChevronRotated(float cx, float cy, float size, float angleDeg,
                                           int color, float thickness) {
        if (size <= 0 || thickness <= 0) return;
        int aInt = (color >> 24) & 0xFF;
        if (aInt <= 0) return;

        setupChevronShader();
        if (chevronShader <= 0) {

            drawChevronFallback(cx, cy, size, angleDeg, color, thickness);
            return;
        }

        float half = size * 0.5F;

        float armX = half * 0.85F;
        float armY = half * 0.45F;

        float a1lx = -armX, a1ly = -armY;
        float a1rx =  0.0F, a1ry =  armY;
        float b1lx =  armX, b1ly = -armY;
        float b1rx =  0.0F, b1ry =  armY;

        double rad = Math.toRadians(angleDeg);
        float cosT = (float) Math.cos(rad);
        float sinT = (float) Math.sin(rad);

        float ax1Gui = cx + (a1lx * cosT - a1ly * sinT);
        float ay1Gui = cy + (a1lx * sinT + a1ly * cosT);
        float ax2Gui = cx + (a1rx * cosT - a1ry * sinT);
        float ay2Gui = cy + (a1rx * sinT + a1ry * cosT);
        float bx1Gui = cx + (b1lx * cosT - b1ly * sinT);
        float by1Gui = cy + (b1lx * sinT + b1ly * cosT);
        float bx2Gui = cx + (b1rx * cosT - b1ry * sinT);
        float by2Gui = cy + (b1rx * sinT + b1ry * cosT);

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean prevTex2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 0, 1);

        GL20.glUseProgram(chevronShader);

        java.nio.FloatBuffer mv = SCRATCH_MV; mv.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        java.nio.FloatBuffer pr = SCRATCH_PR; pr.rewind();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, pr);
        java.nio.IntBuffer vp = SCRATCH_VP; vp.rewind();
        GL11.glGetInteger(GL11.GL_VIEWPORT, vp);

        float mvSx = mv.get(0),  mvSy = mv.get(5);
        float mvTx = mv.get(12), mvTy = mv.get(13);
        float pSx  = pr.get(0),  pSy  = pr.get(5);
        float pTx  = pr.get(12), pTy  = pr.get(13);
        int vpX = vp.get(0), vpY = vp.get(1);
        int vpW = vp.get(2), vpH = vp.get(3);

        float pxPerGuiX = Math.abs(mvSx * pSx) * vpW * 0.5F;
        float pxPerGuiY = Math.abs(mvSy * pSy) * vpH * 0.5F;
        float scale = Math.min(pxPerGuiX, pxPerGuiY);
        if (vpW <= 0 || vpH <= 0 || pxPerGuiX <= 0 || pxPerGuiY <= 0) {
            GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            if (prevTex2D) GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }

        float ax1 = guiToFragX(ax1Gui, mvSx, mvTx, pSx, pTx, vpX, vpW);
        float ay1 = guiToFragY(ay1Gui, mvSy, mvTy, pSy, pTy, vpY, vpH);
        float ax2 = guiToFragX(ax2Gui, mvSx, mvTx, pSx, pTx, vpX, vpW);
        float ay2 = guiToFragY(ay2Gui, mvSy, mvTy, pSy, pTy, vpY, vpH);
        float bx1 = guiToFragX(bx1Gui, mvSx, mvTx, pSx, pTx, vpX, vpW);
        float by1 = guiToFragY(by1Gui, mvSy, mvTy, pSy, pTy, vpY, vpH);
        float bx2 = guiToFragX(bx2Gui, mvSx, mvTx, pSx, pTx, vpX, vpW);
        float by2 = guiToFragY(by2Gui, mvSy, mvTy, pSy, pTy, vpY, vpH);

        float halfThicknessPx = thickness * scale * 0.5F;

        float aF = aInt / 255.0F;
        float rF = ((color >> 16) & 0xFF) / 255.0F;
        float gF = ((color >>  8) & 0xFF) / 255.0F;
        float bF = ( color        & 0xFF) / 255.0F;

        GL20.glUniform4f(chevronU_Color, rF, gF, bF, aF);
        GL20.glUniform2f(chevronU_A1, ax1, ay1);
        GL20.glUniform2f(chevronU_A2, ax2, ay2);
        GL20.glUniform2f(chevronU_B1, bx1, by1);
        GL20.glUniform2f(chevronU_B2, bx2, by2);
        GL20.glUniform1f(chevronU_HalfThickness, halfThicknessPx);

        float padGui = thickness + 2.0F / Math.max(1.0F, scale);
        float qx0 = cx - half - padGui;
        float qy0 = cy - half - padGui;
        float qx1 = cx + half + padGui;
        float qy1 = cy + half + padGui;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(qx0, qy0);
        GL11.glVertex2f(qx0, qy1);
        GL11.glVertex2f(qx1, qy1);
        GL11.glVertex2f(qx1, qy0);
        GL11.glEnd();

        GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (prevTex2D) GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static float guiToFragX(float xGui, float mvSx, float mvTx,
                                     float pSx, float pTx, int vpX, int vpW) {
        float clip = pSx * (mvSx * xGui + mvTx) + pTx;
        return (clip + 1.0F) * 0.5F * vpW + vpX;
    }

    private static float guiToFragY(float yGui, float mvSy, float mvTy,
                                     float pSy, float pTy, int vpY, int vpH) {
        float clip = pSy * (mvSy * yGui + mvTy) + pTy;
        return (clip + 1.0F) * 0.5F * vpH + vpY;
    }

    private static void drawChevronFallback(float cx, float cy, float size, float angleDeg,
                                             int color, float thickness) {

        float half = size * 0.5F;
        float armX = half * 0.85F;
        float armY = half * 0.45F;

        float armLen = (float) Math.sqrt(armX * armX + (armY * 2) * (armY * 2));
        float t2 = thickness * 0.5F;

        float spread = (float) Math.toDegrees(Math.atan2(armY * 2, armX));
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0);
        GL11.glRotatef(angleDeg, 0, 0, 1);

        GL11.glPushMatrix();
        GL11.glRotatef(90 - spread, 0, 0, 1);
        drawRoundedRect(0, -t2, armLen, t2, t2, color);
        GL11.glPopMatrix();

        GL11.glPushMatrix();
        GL11.glRotatef(-(90 - spread), 0, 0, 1);
        drawRoundedRect(0, -t2, armLen, t2, t2, color);
        GL11.glPopMatrix();
        GL11.glPopMatrix();
    }

    private static void drawStackedShadowFallback(float x, float y, float w, float h,
                                                   float radius, int shadowColor,
                                                   float shadowOffsetX, float shadowOffsetY,
                                                   float shadowBlur) {
        int rgb = shadowColor & 0x00FFFFFF;
        int srcAlpha = (shadowColor >> 24) & 0xFF;
        if (srcAlpha <= 0 || shadowBlur <= 0) return;

        int layers = 8;

        float layerNorm = 0.0F;
        for (int i = 0; i < layers; i++) {
            float t = i / (float) layers;
            float w0 = (1.0F - t) * (1.0F - t);
            layerNorm += w0;
        }
        for (int i = layers - 1; i >= 0; i--) {
            float t = i / (float) layers;
            float spread = t * shadowBlur;
            float layerWeight = (1.0F - t) * (1.0F - t) / layerNorm;
            int la = Math.max(1, Math.min(255, (int) (srcAlpha * layerWeight)));
            int color = (la << 24) | rgb;
            float lr = radius + spread * 0.5F;
            drawRoundedRectAA(
                    x + shadowOffsetX - spread,
                    y + shadowOffsetY - spread,
                    x + shadowOffsetX + w + spread,
                    y + shadowOffsetY + h + spread,
                    lr,
                    color);
        }
    }
}
