package crow.client.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class MSAAFramebuffer {

    private static final int SCALE = 2;

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static int fbo            = -1;
    private static int colorTex       = -1;
    private static int depthStencilRb = -1;

    private static int width  = 0;
    private static int height = 0;

    private static int prevFbo = 0;
    private static int prevViewportX, prevViewportY, prevViewportW, prevViewportH;
    private static boolean active = false;

    private static final boolean SSAA_DISABLED = true;

    private MSAAFramebuffer() {}

    @Deprecated
    public static void setEnabled(boolean enabled) {

    }

    public static void begin() {

    }

    public static void end() {

    }

    public static boolean isActive() {
        return active;
    }

    public static int currentTargetFBO() {
        if (active) return fbo;
        Framebuffer mainFb = mc.getFramebuffer();
        return mainFb != null ? mainFb.framebufferObject : 0;
    }

    private static void blitTextureFullscreen(int textureId, float uMax, float vMax, int filter) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDepthMask(false);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0f,    0f);    GL11.glVertex2f(0f, 0f);
            GL11.glTexCoord2f(uMax,  0f);    GL11.glVertex2f(1f, 0f);
            GL11.glTexCoord2f(uMax,  vMax);  GL11.glVertex2f(1f, 1f);
            GL11.glTexCoord2f(0f,    vMax);  GL11.glVertex2f(0f, 1f);
            GL11.glEnd();

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void ensureFBO(int w, int h) {
        if (fbo != -1 && width == w && height == h) return;
        cleanup();

        int sw = w * SCALE;
        int sh = h * SCALE;

        fbo            = EXTFramebufferObject.glGenFramebuffersEXT();
        colorTex       = GL11.glGenTextures();
        depthStencilRb = EXTFramebufferObject.glGenRenderbuffersEXT();

        EXTFramebufferObject.glBindFramebufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT, fbo);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, sw, sh, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
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
            cleanup();
            throw new RuntimeException("SSAA FBO incomplete: 0x" + Integer.toHexString(status));
        }

        width  = w;
        height = h;
    }

    public static void cleanup() {
        if (fbo != -1) {
            try { EXTFramebufferObject.glDeleteFramebuffersEXT(fbo); } catch (Throwable ignored) {}
            fbo = -1;
        }
        if (colorTex != -1) {
            try { GL11.glDeleteTextures(colorTex); } catch (Throwable ignored) {}
            colorTex = -1;
        }
        if (depthStencilRb != -1) {
            try { EXTFramebufferObject.glDeleteRenderbuffersEXT(depthStencilRb); } catch (Throwable ignored) {}
            depthStencilRb = -1;
        }
        width  = 0;
        height = 0;
    }
}
