package crow.client.utils;

import crow.client.module.modules.client.RecordingMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

public final class GUIBlurUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static int captureTexture = -1;
    private static int captureTexW = -1;
    private static int captureTexH = -1;

    // ponytail: exactly the attribute groups the blur touches, instead of
    // GL_ALL_ATTRIB_BITS. This runs twice per blurred HUD element per frame,
    // and GL_ALL also snapshots lighting (all 8 lights) and the pixel-transfer
    // tables, which the blur never modifies. If anything ever looks
    // state-corrupted after a blur, widening this back to GL_ALL_ATTRIB_BITS
    // is the one-line revert.
    private static final int BLUR_ATTRIB_MASK =
              GL11.GL_ENABLE_BIT          // every glEnable/glDisable below
            | GL11.GL_COLOR_BUFFER_BIT    // color mask, blend func, alpha func
            | GL11.GL_DEPTH_BUFFER_BIT    // depth mask + depth test
            | GL11.GL_STENCIL_BUFFER_BIT  // stencil func/op/mask, clear value
            | GL11.GL_VIEWPORT_BIT        // glViewport for the FBO passes
            | GL11.GL_TEXTURE_BIT         // bindings + min/mag/wrap params
            | GL11.GL_TRANSFORM_BIT       // glMatrixMode
            | GL11.GL_CURRENT_BIT         // glColor4f
            | GL11.GL_SCISSOR_BIT;        // callers may have scissor active

    // ponytail: see RenderUtils.SCRATCH_* — same per-call direct-alloc problem.
    private static final java.nio.FloatBuffer SCRATCH_MV =
            org.lwjgl.BufferUtils.createFloatBuffer(16);

    private static final java.nio.IntBuffer SCRATCH_SCISSOR =
            org.lwjgl.BufferUtils.createIntBuffer(16);

    /**
     * Clears the stencil buffer inside {@code (px, py, pw, ph)} only.
     *
     * <p>ponytail: {@code glClear} is bounded by the scissor box and nothing
     * else, so the previous unscissored clear wiped the stencil for the entire
     * framebuffer. A server HUD lights up ~8 blurred panels — arraylist,
     * scoreboard, targethud, radar, logo, stats, keystrokes — and each one paid
     * a full-screen stencil clear to mask a small rectangle. The caller's
     * scissor state is saved and restored exactly, so callers that rely on
     * their own scissor to clip the blur are unaffected.
     */
    private static void clearStencilIn(int px, int py, int pw, int ph) {
        boolean hadScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        SCRATCH_SCISSOR.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCRATCH_SCISSOR);
        int sx = SCRATCH_SCISSOR.get(0), sy = SCRATCH_SCISSOR.get(1);
        int sw = SCRATCH_SCISSOR.get(2), sh = SCRATCH_SCISSOR.get(3);

        // 2px margin: px/py/pw/ph are truncated from float GUI coords, and a
        // sliver of stale stencil at the edge shows up as a hard seam.
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(px - 2, py - 2, pw + 4, ph + 4);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

        GL11.glScissor(sx, sy, sw, sh);
        if (!hadScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static final Map<Long, Framebuffer> fboCache = new HashMap<>();

    private static int lastDisplayW = -1;
    private static int lastDisplayH = -1;

    private static int blurShader = -1;
    private static int blurUniformStride = -1;
    private static int blurUniformDirection = -1;
    private static int blurUniformTexSize = -1;
    private static int blurUniformTint = -1;

    private GUIBlurUtil() {}

    private static void ensureMainFramebufferStencil() {
        Framebuffer framebuffer = mc.getFramebuffer();
        if (framebuffer == null) {
            return;
        }

        try {
            if (framebuffer.depthBuffer > -1) {
                setupMainFramebufferStencil(framebuffer);
                framebuffer.depthBuffer = -1;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setupMainFramebufferStencil(Framebuffer framebuffer) {
        EXTFramebufferObject.glDeleteRenderbuffersEXT(framebuffer.depthBuffer);
        int stencilDepthBufferId = EXTFramebufferObject.glGenRenderbuffersEXT();
        EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, stencilDepthBufferId);
        EXTFramebufferObject.glRenderbufferStorageEXT(
                EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                EXTPackedDepthStencil.GL_DEPTH24_STENCIL8_EXT,
                mc.displayWidth,
                mc.displayHeight);
        EXTFramebufferObject.glFramebufferRenderbufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,
                EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                stencilDepthBufferId);
        EXTFramebufferObject.glFramebufferRenderbufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT,
                EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                stencilDepthBufferId);
    }

    /**
     * Copies the back buffer region into {@link #captureTexture}.
     *
     * <p>ponytail: glCopyTexImage2D reallocates the texture's storage on every
     * call — a driver-side memory allocation per blurred HUD element per frame.
     * Storage only actually needs reallocating when the region's size changes,
     * which is on resize/layout, not every frame. The steady-state path is now
     * glCopyTexSubImage2D, a pure GPU-side blit.
     */
    private static void captureBackbuffer(int px, int py, int pw, int ph) {
        if (pw != captureTexW || ph != captureTexH) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, pw, ph, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            captureTexW = pw;
            captureTexH = ph;
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, px, py, pw, ph);
    }

    private static void setupShader() {
        if (blurShader != -1) return;
        try {
            int program = GL20.glCreateProgram();

            String vert =
                "#version 120\n" +
                "void main() {\n" +
                "  gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
                "  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
                "}\n";

            String frag =
                "#version 120\n" +
                "uniform sampler2D texture;\n" +
                "uniform float stride;\n" +
                "uniform vec2 direction;\n" +
                "uniform vec2 texSize;\n" +
                "uniform vec4 uTint;\n" +
                "void main() {\n" +
                "  vec2 uv = gl_TexCoord[0].st;\n" +
                "  vec2 step1 = direction * stride / texSize;\n" +
                "  vec3 c = vec3(0.0);\n" +
                "  c += texture2D(texture, uv - 6.0 * step1).rgb * 0.01851;\n" +
                "  c += texture2D(texture, uv - 5.0 * step1).rgb * 0.03413;\n" +
                "  c += texture2D(texture, uv - 4.0 * step1).rgb * 0.05625;\n" +
                "  c += texture2D(texture, uv - 3.0 * step1).rgb * 0.08299;\n" +
                "  c += texture2D(texture, uv - 2.0 * step1).rgb * 0.10954;\n" +
                "  c += texture2D(texture, uv - 1.0 * step1).rgb * 0.12944;\n" +
                "  c += texture2D(texture, uv             ).rgb * 0.13684;\n" +
                "  c += texture2D(texture, uv + 1.0 * step1).rgb * 0.12944;\n" +
                "  c += texture2D(texture, uv + 2.0 * step1).rgb * 0.10954;\n" +
                "  c += texture2D(texture, uv + 3.0 * step1).rgb * 0.08299;\n" +
                "  c += texture2D(texture, uv + 4.0 * step1).rgb * 0.05625;\n" +
                "  c += texture2D(texture, uv + 5.0 * step1).rgb * 0.03413;\n" +
                "  c += texture2D(texture, uv + 6.0 * step1).rgb * 0.01851;\n" +

                "  vec3 result = mix(c, uTint.rgb, uTint.a);\n" +

                "  gl_FragColor = vec4(result, gl_Color.a);\n" +
                "}\n";

            int vId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vId, vert);
            GL20.glCompileShader(vId);
            GL20.glAttachShader(program, vId);

            int fId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fId, frag);
            GL20.glCompileShader(fId);
            GL20.glAttachShader(program, fId);

            GL20.glLinkProgram(program);
            blurShader = program;
            blurUniformStride    = GL20.glGetUniformLocation(program, "stride");
            blurUniformDirection = GL20.glGetUniformLocation(program, "direction");
            blurUniformTexSize   = GL20.glGetUniformLocation(program, "texSize");
            blurUniformTint      = GL20.glGetUniformLocation(program, "uTint");
        } catch (Exception e) {
            blurShader = -1;
        }
    }

    public static void drawBlurredBackground(int x, int y, int w, int h,
                                              int blurRadius, int roundness, float opacity) {
        if (w <= 0 || h <= 0 || opacity <= 0.001f) return;
        if (RecordingMode.shouldSkipBlur()) {

            int alpha = Math.min(255, (int) (opacity * 200));
            int color = (alpha << 24) | 0x0D0F14;
            RenderUtils.drawRoundedRect(x, y, x + w, y + h, roundness, color);
            return;
        }

        setupShader();

        try {
            GL11.glPushAttrib(BLUR_ATTRIB_MASK);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();

            if (mc.displayWidth != lastDisplayW || mc.displayHeight != lastDisplayH) {
                clearCache();
                lastDisplayW = mc.displayWidth;
                lastDisplayH = mc.displayHeight;
            }
            ensureMainFramebufferStencil();

            ScaledResolution sr = new ScaledResolution(mc);
            int sf = sr.getScaleFactor();

            java.nio.FloatBuffer modelView = SCRATCH_MV; modelView.rewind();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
            float scaleX = modelView.get(0);
            float scaleY = modelView.get(5);
            float transX = modelView.get(12);
            float transY = modelView.get(13);

            int absX = (int) (x * scaleX + transX);
            int absY = (int) (y * scaleY + transY);
            int absW = (int) (w * scaleX);
            int absH = (int) (h * scaleY);

            int pw = absW * sf;
            int ph = absH * sf;
            int px = absX * sf;
            int py = mc.displayHeight - (absY + absH) * sf;

            if (pw < 1 || ph < 1) return;

            blurRadius = Math.max(1, Math.min(20, blurRadius));
            int divisor = 4;
            int bw = Math.max(1, pw / divisor);
            int bh = Math.max(1, ph / divisor);

            float blurStride = Math.max(0.5F, blurRadius * 0.3F);

            long key = ((long) bw << 32) | (bh & 0xFFFFFFFFL);
            Framebuffer blurFbo = fboCache.get(key);
            if (blurFbo == null) {
                blurFbo = new Framebuffer(bw, bh, false);
                blurFbo.setFramebufferFilter(GL11.GL_LINEAR);
                fboCache.put(key, blurFbo);

                EXTFramebufferObject.glBindFramebufferEXT(
                        EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        MSAAFramebuffer.currentTargetFBO());
            }

            if (captureTexture == -1) {
                captureTexture = GL11.glGenTextures();
                captureTexW = -1;
                captureTexH = -1;
            }

            float cornerR = Math.max(0f, roundness);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            clearStencilIn(px, py, pw, ph);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            GL11.glColorMask(false, false, false, false);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);

            GL11.glEnable(GL11.GL_ALPHA_TEST);

            GL11.glAlphaFunc(GL11.GL_GREATER, 0.01f);

            RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, cornerR, 0xFFFFFFFF);
            GL11.glDisable(GL11.GL_ALPHA_TEST);

            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilMask(0x00);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);

            GlStateManager.bindTexture(captureTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            captureBackbuffer(px, py, pw, ph);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT, blurFbo.framebufferObject);
            GL11.glViewport(0, 0, bw, bh);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            if (blurShader != -1) {
                GL20.glUseProgram(blurShader);
                GL20.glUniform1f(blurUniformStride, blurStride);
                GL20.glUniform2f(blurUniformDirection, 1.0f, 0.0f);
                GL20.glUniform2f(blurUniformTexSize, pw, ph);
                GL20.glUniform4f(blurUniformTint, 0.0f, 0.0f, 0.0f, 0.0f);
            }

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, 0);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 1);
            GL11.glEnd();

            if (blurShader != -1) GL20.glUseProgram(0);

            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    MSAAFramebuffer.currentTargetFBO());
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilMask(0x00);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);

            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GlStateManager.bindTexture(blurFbo.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glColor4f(1f, 1f, 1f, Math.max(0f, Math.min(1f, opacity)));

            if (blurShader != -1) {
                GL20.glUseProgram(blurShader);
                GL20.glUniform1f(blurUniformStride, blurStride);
                GL20.glUniform2f(blurUniformDirection, 0.0f, 1.0f);
                GL20.glUniform2f(blurUniformTexSize, bw, bh);
                GL20.glUniform4f(blurUniformTint,
                        14.0f / 255.0f, 15.0f / 255.0f, 18.0f / 255.0f, 0.38f);
            }

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x,     y);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x,     y + h);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + w, y + h);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + w, y);
            GL11.glEnd();

            if (blurShader != -1) GL20.glUseProgram(0);

            GlStateManager.bindTexture(0);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GlStateManager.color(1f, 1f, 1f, 1f);

        } catch (Exception ignored) {

            int a = Math.max(20, Math.min(180, (int) (opacity * 180f)));
            RenderUtils.drawSoftBlurRect(x, y, x + w, y + h, roundness,
                    (a << 24) | 0x111118);
        } finally {
            try {
                if (blurShader != -1) {
                    GL20.glUseProgram(0);
                }
            } catch (Exception ignored) {
            }
            try {
                GlStateManager.bindTexture(0);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glDepthMask(true);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GlStateManager.color(1f, 1f, 1f, 1f);
                EXTFramebufferObject.glBindFramebufferEXT(
                        EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        MSAAFramebuffer.currentTargetFBO());
                GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
            } catch (Exception ignored) {
            }
            try {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            } catch (Exception ignored) {
            }
            try {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            } catch (Exception ignored) {
            }
            try {
                GL11.glPopAttrib();
            } catch (Exception ignored) {
            }

            RenderUtils.syncAllGlState();
            try {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
            } catch (Exception ignored) {
            }
        }
    }

    public static void drawBlurredBackgroundCustom(Runnable stencilDrawer, int x, int y, int w, int h,
                                                    int blurRadius, float opacity) {
        if (w <= 0 || h <= 0 || opacity <= 0.001f) return;
        if (RecordingMode.shouldSkipBlur()) {

            int alpha = Math.min(255, (int) (opacity * 200));
            int color = (alpha << 24) | 0x0D0F14;
            net.minecraft.client.gui.Gui.drawRect(x, y, x + w, y + h, color);
            return;
        }

        setupShader();

        try {
            GL11.glPushAttrib(BLUR_ATTRIB_MASK);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();

            if (mc.displayWidth != lastDisplayW || mc.displayHeight != lastDisplayH) {
                clearCache();
                lastDisplayW = mc.displayWidth;
                lastDisplayH = mc.displayHeight;
            }
            ensureMainFramebufferStencil();

            ScaledResolution sr = new ScaledResolution(mc);
            int sf = sr.getScaleFactor();

            java.nio.FloatBuffer modelView = SCRATCH_MV; modelView.rewind();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
            float scaleX = modelView.get(0);
            float scaleY = modelView.get(5);
            float transX = modelView.get(12);
            float transY = modelView.get(13);

            int absX = (int) (x * scaleX + transX);
            int absY = (int) (y * scaleY + transY);
            int absW = (int) (w * scaleX);
            int absH = (int) (h * scaleY);

            int pw = absW * sf;
            int ph = absH * sf;
            int px = absX * sf;
            int py = mc.displayHeight - (absY + absH) * sf;

            if (pw < 1 || ph < 1) return;

            blurRadius = Math.max(1, Math.min(20, blurRadius));
            int divisor = 4;
            int bw = Math.max(1, pw / divisor);
            int bh = Math.max(1, ph / divisor);
            float blurStride = Math.max(0.5F, blurRadius * 0.3F);

            long key = ((long) bw << 32) | (bh & 0xFFFFFFFFL);
            Framebuffer blurFbo = fboCache.get(key);
            if (blurFbo == null) {
                blurFbo = new Framebuffer(bw, bh, false);
                blurFbo.setFramebufferFilter(GL11.GL_LINEAR);
                fboCache.put(key, blurFbo);
                EXTFramebufferObject.glBindFramebufferEXT(
                        EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        MSAAFramebuffer.currentTargetFBO());
            }

            if (captureTexture == -1) {
                captureTexture = GL11.glGenTextures();
                captureTexW = -1;
                captureTexH = -1;
            }

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            clearStencilIn(px, py, pw, ph);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            GL11.glColorMask(false, false, false, false);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);

            GL11.glAlphaFunc(GL11.GL_GREATER, 0.01f);
            stencilDrawer.run();
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilMask(0x00);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);

            GlStateManager.bindTexture(captureTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            captureBackbuffer(px, py, pw, ph);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT, blurFbo.framebufferObject);
            GL11.glViewport(0, 0, bw, bh);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            if (blurShader != -1) {
                GL20.glUseProgram(blurShader);
                GL20.glUniform1f(blurUniformStride, blurStride);
                GL20.glUniform2f(blurUniformDirection, 1.0f, 0.0f);
                GL20.glUniform2f(blurUniformTexSize, pw, ph);
                GL20.glUniform4f(blurUniformTint, 0.0f, 0.0f, 0.0f, 0.0f);
            }

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, 0);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 1);
            GL11.glEnd();

            if (blurShader != -1) GL20.glUseProgram(0);

            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    MSAAFramebuffer.currentTargetFBO());
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilMask(0x00);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);

            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GlStateManager.bindTexture(blurFbo.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glColor4f(1f, 1f, 1f, Math.max(0f, Math.min(1f, opacity)));

            if (blurShader != -1) {
                GL20.glUseProgram(blurShader);
                GL20.glUniform1f(blurUniformStride, blurStride);
                GL20.glUniform2f(blurUniformDirection, 0.0f, 1.0f);
                GL20.glUniform2f(blurUniformTexSize, bw, bh);
                GL20.glUniform4f(blurUniformTint,
                        14.0f / 255.0f, 15.0f / 255.0f, 18.0f / 255.0f, 0.38f);
            }

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x,     y);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x,     y + h);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + w, y + h);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + w, y);
            GL11.glEnd();

            if (blurShader != -1) GL20.glUseProgram(0);

            GlStateManager.bindTexture(0);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GlStateManager.color(1f, 1f, 1f, 1f);

        } catch (Exception ignored) {
            int a = Math.max(20, Math.min(180, (int) (opacity * 180f)));
            RenderUtils.drawSoftBlurRect(x, y, x + w, y + h, 0,
                    (a << 24) | 0x111118);
        } finally {
            try { if (blurShader != -1) GL20.glUseProgram(0); } catch (Exception ignored) {}
            try {
                GlStateManager.bindTexture(0);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glDepthMask(true);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GlStateManager.color(1f, 1f, 1f, 1f);
                EXTFramebufferObject.glBindFramebufferEXT(
                        EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        MSAAFramebuffer.currentTargetFBO());
                GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
            } catch (Exception ignored) {}
            try { GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); } catch (Exception ignored) {}
            try { GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); } catch (Exception ignored) {}
            try { GL11.glPopAttrib(); } catch (Exception ignored) {}

            RenderUtils.syncAllGlState();
            try { GL11.glMatrixMode(GL11.GL_MODELVIEW); } catch (Exception ignored) {}
        }
    }

    public static void clearCache() {
        for (Framebuffer fb : fboCache.values()) {
            try { fb.deleteFramebuffer(); } catch (Exception ignored) {}
        }
        fboCache.clear();
        if (captureTexture != -1) {
            try { GL11.glDeleteTextures(captureTexture); } catch (Exception ignored) {}
            captureTexture = -1;
        }
        captureTexW = -1;
        captureTexH = -1;
    }
}
