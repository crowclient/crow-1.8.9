package crow.client.render.aa;

import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/**
 * Single non-multisampled FBO at SSAA-scale, used as the off-screen target
 * for the UI pass.
 *
 * <p>The compositing strategy this time around:
 * <ol>
 *   <li>FBO is cleared to fully transparent {@code (0,0,0,0)} at begin.</li>
 *   <li>UI draws into it with standard alpha blend, but the alpha channel
 *       gets {@code (GL_ONE, GL_ONE_MINUS_SRC_ALPHA)} via the GlStateManager
 *       mixin + {@link AABlend} helper. So the FBO accumulates correct
 *       "over" alpha rather than the squashed {@code src.a²} form.</li>
 *   <li>FBO RGB ends up in pre-multiplied form (because std blend produces
 *       {@code src.rgb * src.a + dst.rgb * (1-src.a)} on a transparent
 *       background = {@code src.rgb * src.a}).</li>
 *   <li>Composite onto the back buffer via a textured quad with
 *       {@code glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)} — the standard
 *       "premultiplied over" formula. World stays intact where UI is
 *       transparent.</li>
 * </ol>
 *
 * <p>SSAA downsample: bilinear texture filter on the FBO during the
 * composite. For 2× downsample, bilinear gives the 2×2 box average — a
 * proper SSAA resolve.
 */
final class AAFramebuffer {

    private int fbo = 0;
    private int colorTex = 0;
    private int depthStencilRb = 0;

    private int width = 0;
    private int height = 0;
    private int ssaa = 1;

    boolean ensure(int logicalW, int logicalH, int wantSsaa) {
        wantSsaa = Math.max(1, wantSsaa);
        if (fbo != 0 && width == logicalW && height == logicalH && ssaa == wantSsaa) {
            return true;
        }
        destroy();

        width = logicalW;
        height = logicalH;
        ssaa = wantSsaa;
        int sw = logicalW * ssaa;
        int sh = logicalH * ssaa;
        if (sw <= 0 || sh <= 0) return false;

        try {
            fbo = EXTFramebufferObject.glGenFramebuffersEXT();
            colorTex = GL11.glGenTextures();
            depthStencilRb = EXTFramebufferObject.glGenRenderbuffersEXT();

            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT, fbo);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                    sw, sh, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            EXTFramebufferObject.glFramebufferTexture2DEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT,
                    GL11.GL_TEXTURE_2D, colorTex, 0);

            EXTFramebufferObject.glBindRenderbufferEXT(
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT, depthStencilRb);
            EXTFramebufferObject.glRenderbufferStorageEXT(
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                    EXTPackedDepthStencil.GL_DEPTH24_STENCIL8_EXT, sw, sh);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT,
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT, depthStencilRb);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT, depthStencilRb);

            int status = EXTFramebufferObject.glCheckFramebufferStatusEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT);
            if (status != EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT) {
                destroy();
                return false;
            }
            return true;
        } catch (Throwable t) {
            destroy();
            return false;
        } finally {
            EXTFramebufferObject.glBindRenderbufferEXT(
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT, 0);
        }
    }

    void bindAsDrawTarget() {
        EXTFramebufferObject.glBindFramebufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT, fbo);
    }

    int width()  { return width; }
    int height() { return height; }
    int ssaa()   { return ssaa; }
    int colorTexture() { return colorTex; }
    boolean isReady() { return fbo != 0; }

    void destroy() {
        if (fbo != 0)            { EXTFramebufferObject.glDeleteFramebuffersEXT(fbo); fbo = 0; }
        if (colorTex != 0)       { GL11.glDeleteTextures(colorTex); colorTex = 0; }
        if (depthStencilRb != 0) { EXTFramebufferObject.glDeleteRenderbuffersEXT(depthStencilRb); depthStencilRb = 0; }
        width = height = 0;
        ssaa = 1;
    }
}
