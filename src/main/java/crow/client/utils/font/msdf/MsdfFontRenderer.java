package crow.client.utils.font.msdf;

import java.awt.Font;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import net.minecraft.client.renderer.GlStateManager;

import crow.client.utils.font.FontRenderer;

public class MsdfFontRenderer extends FontRenderer {

    private final MsdfAtlas atlas;

    private final float emToPx;

    public MsdfFontRenderer(Font fallbackFont, MsdfAtlas atlas) {
        super(fallbackFont, true, true);
        this.atlas = atlas;

        this.emToPx = fallbackFont.getSize2D() * 0.5F;
    }

    private boolean canRender() {
        return atlas != null && atlas.isLoaded() && MsdfShader.isReady();
    }

    @Override
    public int drawString(String text, double x, float y, int color) {
        if (!canRender()) return super.drawString(text, x, y, color);
        return (int) drawMsdf(text, x, y, color, false);
    }

    @Override
    public int drawStringWithShadow(String text, double x, float y, int color) {
        if (!canRender()) return super.drawStringWithShadow(text, x, y, color);

        int shadowAlpha = (color >>> 24) & 0xFF;
        int shadowColor = ((shadowAlpha / 4) << 24);
        drawMsdf(text, x + 0.9, y + 0.5F, shadowColor, false);
        return (int) drawMsdf(text, x, y, color, false);
    }

    @Override
    public int drawSmoothString(String text, double x, float y, int color) {
        if (!canRender()) return super.drawSmoothString(text, x, y, color);
        return (int) drawMsdf(text, x, y, color, false);
    }

    @Override
    public float drawSmoothString(String text, double x, double y, int color, boolean shadow) {
        if (!canRender()) return super.drawSmoothString(text, x, y, color, shadow);
        if (shadow) {
            int shadowAlpha = (color >>> 24) & 0xFF;
            int shadowColor = ((shadowAlpha / 4) << 24);
            drawMsdf(text, x + 0.9, y + 0.5, shadowColor, false);
        }
        return drawMsdf(text, x, y, color, false);
    }

    @Override
    public float drawString(String text, double x, double y, int color, boolean shadow, float kerning) {
        if (!canRender()) return super.drawString(text, x, y, color, shadow, kerning);
        if (shadow) {
            int shadowAlpha = (color >>> 24) & 0xFF;
            int shadowColor = ((shadowAlpha / 4) << 24);
            drawMsdf(text, x + 0.9, y + 0.5, shadowColor, false);
        }
        return drawMsdf(text, x, y, color, false);
    }

    @Override
    public double getStringWidth(String text) {
        if (!canRender()) return super.getStringWidth(text);
        return computeWidth(text) - 4;
    }

    @Override
    public double getStringWidth(String text, float kerning) {
        if (!canRender()) return super.getStringWidth(text, kerning);
        return computeWidth(text) - 4;
    }

    @Override
    public int getHeight() {
        if (!canRender()) return super.getHeight();
        return Math.round(atlas.getEmLineHeight() * emToPx);
    }

    private float computeWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            MsdfAtlas.Glyph g = atlas.getGlyph(text.charAt(i));
            if (g == null) g = atlas.getGlyph('?');
            if (g != null) {
                width += g.advance * emToPx;
            }
        }
        return width;
    }

    private float drawMsdf(String text, double x, double y, int color, boolean smoothing) {
        if (text == null || text.isEmpty()) return 0;

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean prevTex = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.getTextureId());

        GL20.glUseProgram(MsdfShader.program());
        GL20.glUniform1i(MsdfShader.uAtlas(), 0);
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>>  8) & 0xFF) / 255.0F;
        float b = ( color         & 0xFF) / 255.0F;
        if (a == 0) a = 1.0F;
        GL20.glUniform4f(MsdfShader.uColor(), r, g, b, a);

        float screenPxRange = atlas.getPxRange() * (emToPx / Math.max(1, atlas.getAtlasGlyphSize()));
        GL20.glUniform1f(MsdfShader.uPxRange(), Math.max(1.0F, screenPxRange));

        float baselineY = (float) y + atlas.getEmAscender() * emToPx;
        float cursorX = (float) x;

        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            MsdfAtlas.Glyph glyph = atlas.getGlyph(ch);
            if (glyph == null) glyph = atlas.getGlyph('?');
            if (glyph == null) continue;

            float x0 = cursorX + glyph.planeLeft  * emToPx;
            float x1 = cursorX + glyph.planeRight * emToPx;
            float y0 = baselineY - glyph.planeTop    * emToPx;
            float y1 = baselineY - glyph.planeBottom * emToPx;

            float aw = atlas.getAtlasWidth();
            float ah = atlas.getAtlasHeight();
            float u0 = glyph.atlasLeft   / aw;
            float u1 = glyph.atlasRight  / aw;
            float v0 = 1.0F - glyph.atlasTop    / ah;
            float v1 = 1.0F - glyph.atlasBottom / ah;

            GL11.glTexCoord2f(u0, v0); GL11.glVertex2f(x0, y0);
            GL11.glTexCoord2f(u0, v1); GL11.glVertex2f(x0, y1);
            GL11.glTexCoord2f(u1, v1); GL11.glVertex2f(x1, y1);
            GL11.glTexCoord2f(u1, v0); GL11.glVertex2f(x1, y0);

            cursorX += glyph.advance * emToPx;
        }
        GL11.glEnd();

        GL20.glUseProgram(prevProgram > 0 ? prevProgram : 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        if (!prevTex) GlStateManager.disableTexture2D();

        return cursorX - (float) x;
    }
}
