package crow.client.render.aa;

import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.IntBuffer;

/**
 * Single entry point for routing UI passes through the supersampling FBO.
 *
 * <p>Architecture (third attempt — alpha-correct compositing):
 *
 * <ol>
 *   <li><b>{@link #beginUI}</b>: bind a transparent-cleared FBO at SSAA scale,
 *       set the viewport to match. The world is left intact on the back
 *       buffer; we never copy it into the FBO.</li>
 *   <li>UI draws into the FBO. The {@code MixinGlStateManager} +
 *       {@link AABlend} pair force every blend func to apply
 *       {@code (GL_ONE, GL_ONE_MINUS_SRC_ALPHA)} on the alpha channel, so
 *       the FBO accumulates correct pre-multiplied "over" composition
 *       instead of squashed alpha.</li>
 *   <li><b>{@link #endUI}</b>: composite the FBO onto the back buffer as a
 *       textured fullscreen quad with {@code glBlendFunc(GL_ONE,
 *       GL_ONE_MINUS_SRC_ALPHA)} — standard premultiplied "over". Bilinear
 *       texture filter does the SSAA downsample. World pixels under
 *       transparent FBO regions are preserved verbatim because
 *       {@code (1 - 0) = 1}.</li>
 * </ol>
 *
 * <p>GL state save/restore is explicit and minimal — no {@code glPushAttrib}
 * because that's been unreliable in MC 1.8.9's environment.
 */
public final class AAPipeline {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final AAFramebuffer fbo = new AAFramebuffer();
    private static final IntBuffer SCRATCH = BufferUtils.createIntBuffer(16);

    private static int depth = 0;
    private static int prevFbo = 0;
    private static int prevViewportX, prevViewportY, prevViewportW, prevViewportH;
    private static int prevDisplayW, prevDisplayH;
    private static boolean prevScissor;
    private static boolean prevBlend;
    private static int prevBlendSrcRGB, prevBlendDstRGB;
    private static int prevTex2D0;
    private static boolean prevTex2DEnabled;
    private static int prevActiveTexture;
    private static int prevProgram;
    private static boolean active = false;

    private AAPipeline() {}

    public static boolean isActive() { return active; }
    public static int resolvedColorTexture() { return fbo.colorTexture(); }

    public static void destroy() {
        fbo.destroy();
        active = false;
        depth = 0;
    }

    public static void beginUI() {
        depth++;
        if (depth > 1) return;

        // Hot-reload the mode from the user-facing setting.
        try {
            if (crow.client.module.modules.client.GuiModule.aaMode != null) {
                AAConfig.current = crow.client.module.modules.client.GuiModule.aaMode.getMode();
            }
        } catch (Throwable ignored) {}

        AAConfig.Mode mode = AAConfig.current;
        if (mode.ssaa <= 1) {
            active = false;
            return;
        }

        int dw, dh;
        if (mc.getFramebuffer() != null
                && mc.getFramebuffer().framebufferWidth > 0
                && mc.getFramebuffer().framebufferHeight > 0) {
            dw = mc.getFramebuffer().framebufferWidth;
            dh = mc.getFramebuffer().framebufferHeight;
        } else {
            dw = mc.displayWidth;
            dh = mc.displayHeight;
        }
        if (dw <= 0 || dh <= 0) {
            active = false;
            return;
        }

        if (!fbo.ensure(dw, dh, mode.ssaa)) {
            active = false;
            return;
        }

        // ---- Save the GL state we'll modify ----
        SCRATCH.clear();
        GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT, SCRATCH);
        prevFbo = SCRATCH.get(0);

        SCRATCH.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, SCRATCH);
        prevViewportX = SCRATCH.get(0);
        prevViewportY = SCRATCH.get(1);
        prevViewportW = SCRATCH.get(2);
        prevViewportH = SCRATCH.get(3);

        prevDisplayW = dw;
        prevDisplayH = dh;
        prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (prevScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // ---- Bind our FBO and clear it transparent ----
        // Mark active BEFORE the clear so the AABlend mixin would intercept any
        // blend-state side-effects of glClear (none in practice but safer).
        active = true;
        fbo.bindAsDrawTarget();
        GL11.glViewport(0, 0, fbo.width() * fbo.ssaa(), fbo.height() * fbo.ssaa());
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT
                   | GL11.GL_DEPTH_BUFFER_BIT
                   | GL11.GL_STENCIL_BUFFER_BIT);
    }

    public static void endUI() {
        if (depth <= 0) return;
        depth--;
        if (depth > 0) return;
        if (!active) return;

        // Critical: flip 'active' off BEFORE compositing so the composite's
        // own blend-func calls aren't intercepted by the AA mixin (which
        // would force separate alpha and break the premultiplied "over").
        active = false;

        // ---- Save the additional state the composite touches ----
        SCRATCH.clear();
        GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE, SCRATCH);
        prevActiveTexture = SCRATCH.get(0);

        // Switch to TEXTURE0 BEFORE querying its enable/bind state.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        prevTex2DEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        SCRATCH.clear();
        GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D, SCRATCH);
        prevTex2D0 = SCRATCH.get(0);

        prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        SCRATCH.clear();
        GL11.glGetInteger(GL11.GL_BLEND_SRC, SCRATCH);
        prevBlendSrcRGB = SCRATCH.get(0);
        SCRATCH.clear();
        GL11.glGetInteger(GL11.GL_BLEND_DST, SCRATCH);
        prevBlendDstRGB = SCRATCH.get(0);

        SCRATCH.clear();
        GL11.glGetInteger(0x8B8D /* GL_CURRENT_PROGRAM */, SCRATCH);
        prevProgram = SCRATCH.get(0);

        // ---- Composite ----
        // Bind dest, restore viewport so the quad covers the full display.
        EXTFramebufferObject.glBindFramebufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT, prevFbo);
        GL11.glViewport(prevViewportX, prevViewportY, prevViewportW, prevViewportH);

        compositeOntoCurrentTarget();

        // ---- Restore state ----
        if (prevProgram != 0) org.lwjgl.opengl.GL20.glUseProgram(prevProgram);
        else                  org.lwjgl.opengl.GL20.glUseProgram(0);

        // Texture 2D enable + binding on TEXTURE0
        if (prevTex2DEnabled) GL11.glEnable(GL11.GL_TEXTURE_2D);
        else                  GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2D0);

        // Active texture unit
        GL13.glActiveTexture(prevActiveTexture);

        // Blend
        GL11.glBlendFunc(prevBlendSrcRGB, prevBlendDstRGB);
        if (prevBlend) GL11.glEnable(GL11.GL_BLEND);
        else           GL11.glDisable(GL11.GL_BLEND);

        if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    /**
     * Draws the FBO's color texture as a fullscreen quad onto the current
     * draw target with premultiplied "over" composition.
     */
    private static void compositeOntoCurrentTarget() {
        int tex = fbo.colorTexture();
        if (tex == 0) return;

        // Save matrices we'll change.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_STENCIL_TEST);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA); // premult over

        // Bind our texture on TEXTURE0 with TEXTURE_2D enabled.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        // Make sure we're using the fixed-function pipeline (unbind any
        // shader program from previous UI rendering).
        org.lwjgl.opengl.GL20.glUseProgram(0);

        // GL_REPLACE — vertex color isn't multiplied in.
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(0f, 0f);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(1f, 0f);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(1f, 1f);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(0f, 1f);
        GL11.glEnd();

        // Restore matrices.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        // Restore TEXTURE_ENV_MODE to MC's default (MODULATE).
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
    }
}
