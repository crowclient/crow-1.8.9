package crow.client.render.aa;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.IntBuffer;

/**
 * Signed-distance-field 2D shape primitives. Anti-aliases on the GPU via
 * {@code smoothstep} on the SDF, so shapes look identical at any scale and
 * never expose polygon facets, even at large corner radii or tiny circles.
 *
 * <p>If the SDF shader fails to compile (very old GL context), falls back to
 * the legacy polygon rasterizer, so callers don't need to handle that.
 *
 * <p>Output is non-premultiplied alpha — composes with MC's standard alpha
 * blend ({@code GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA}). All GL state
 * touched is saved/restored so calls don't leak side effects (this was the
 * source of "everything is broken" in the first attempt).
 */
public final class SDFRenderer {

    /**
     * Edge softness in shape-local units. 0.5 px gives sub-pixel AA on curves
     * while keeping axis-aligned-rect coverage identical to {@code Gui.drawRect}
     * — i.e. pixel (X, Y) is full coverage iff its center is inside the rect's
     * mathematical bounds. Larger values look smoother on curves but make
     * axis-aligned rects appear ~0.5 px wider per side.
     */
    private static final float AA_PX = 0.5f;

    /** Padding around the bounding quad so the smoothstep tail is included. */
    private static final float QUAD_PAD = AA_PX * 2.0f + 1.0f;

    // LWJGL's glGetInteger requires the IntBuffer to have at least 16
    // remaining elements regardless of how many ints actually get returned —
    // it's a precondition check on the buffer, not a write count.
    private static final IntBuffer SCRATCH = BufferUtils.createIntBuffer(16);

    public static void fillRoundedRect(float x, float y, float w, float h, float radius, int rgba) {
        drawShape(x, y, w, h, radius, /*outline*/ 0f, rgba);
    }

    public static void outlineRoundedRect(float x, float y, float w, float h,
                                          float radius, float thickness, int rgba) {
        drawShape(x, y, w, h, radius, thickness, rgba);
    }

    public static void fillCircle(float cx, float cy, float radius, int rgba) {
        drawShape(cx - radius, cy - radius, radius * 2f, radius * 2f, radius, 0f, rgba);
    }

    public static void outlineCircle(float cx, float cy, float radius, float thickness, int rgba) {
        drawShape(cx - radius, cy - radius, radius * 2f, radius * 2f, radius, thickness, rgba);
    }

    private static void drawShape(float x, float y, float w, float h,
                                  float radius, float outline, int rgba) {
        int prog = SDFShader.program();
        if (prog == 0) {
            // Shader unavailable — punt to legacy rasterizer so calls still draw.
            crow.client.utils.RenderUtils.drawRoundedRect(x, y, x + w, y + h, radius, rgba);
            return;
        }

        radius = Math.max(0f, Math.min(radius, Math.min(w, h) * 0.5f));
        float halfW = w * 0.5f;
        float halfH = h * 0.5f;
        float cx = x + halfW;
        float cy = y + halfH;

        float a = ((rgba >> 24) & 0xFF) / 255f;
        float r = ((rgba >> 16) & 0xFF) / 255f;
        float g = ((rgba >>  8) & 0xFF) / 255f;
        float b = ( rgba        & 0xFF) / 255f;

        float padHalfW = halfW + QUAD_PAD;
        float padHalfH = halfH + QUAD_PAD;

        // ---- Save state we will modify so we don't leak it. ----
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasTex   = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        SCRATCH.clear();
        GL11.glGetInteger(GL11.GL_BLEND_SRC, SCRATCH);
        int prevBlendSrc = SCRATCH.get(0);
        SCRATCH.clear();
        GL11.glGetInteger(GL11.GL_BLEND_DST, SCRATCH);
        int prevBlendDst = SCRATCH.get(0);

        // ---- Draw. ----
        GL20.glUseProgram(prog);
        SDFShader.setUniforms(halfW, halfH, radius, outline, r, g, b, a, AA_PX);

        if (!wasBlend) GL11.glEnable(GL11.GL_BLEND);
        AABlend.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (wasTex) GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(-padHalfW, -padHalfH); GL11.glVertex2f(cx - padHalfW, cy - padHalfH);
        GL11.glTexCoord2f( padHalfW, -padHalfH); GL11.glVertex2f(cx + padHalfW, cy - padHalfH);
        GL11.glTexCoord2f( padHalfW,  padHalfH); GL11.glVertex2f(cx + padHalfW, cy + padHalfH);
        GL11.glTexCoord2f(-padHalfW,  padHalfH); GL11.glVertex2f(cx - padHalfW, cy + padHalfH);
        GL11.glEnd();

        // ---- Restore. ----
        GL20.glUseProgram(prevProgram);
        if (wasTex) GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(prevBlendSrc, prevBlendDst);
        if (!wasBlend) GL11.glDisable(GL11.GL_BLEND);
    }

    private SDFRenderer() {}
}
