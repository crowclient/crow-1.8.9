package crow.client.render.aa;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * AA-aware blend-function dispatcher.
 *
 * <p>The fundamental problem with putting MC's UI through an alpha FBO is
 * that {@code glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)} applies its
 * factors to the alpha channel as well as RGB. Inside the FBO this squashes
 * accumulated alpha to {@code src.a² + dst.a*(1-src.a)} instead of the
 * correct "over" composition {@code src.a + dst.a*(1-src.a)}, and the
 * downstream composite back to the back buffer can't recover from that.
 *
 * <p>The fix is to use {@link GL14#glBlendFuncSeparate} so the alpha
 * channel uses {@code (ONE, ONE_MINUS_SRC_ALPHA)} regardless of what RGB is
 * doing. This helper centralises that decision: if the AA pipeline is
 * active, alpha factors are forced; otherwise the call passes through to
 * standard {@code glBlendFunc}.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@link crow.client.mixin.mixins.MixinGlStateManager} — redirects
 *       {@code GL11.glBlendFunc} invocations inside MC's
 *       {@code GlStateManager.blendFunc} body via this helper.</li>
 *   <li>Render code paths that bypass {@code GlStateManager} entirely (e.g.
 *       {@code RenderUtils.drawSoftBlurRect}, {@code Projectiles},
 *       {@code PenisESP}, {@code ServerPos}).</li>
 * </ul>
 */
public final class AABlend {

    /**
     * Like {@link GL11#glBlendFunc} but applies the AA-aware override.
     * Color factors are passed through as requested; alpha factors are
     * forced to {@code (ONE, ONE_MINUS_SRC_ALPHA)} while AA is active so
     * the FBO accumulates correct alpha.
     */
    public static void blendFunc(int srcColor, int dstColor) {
        if (AAPipeline.isActive()) {
            GL14.glBlendFuncSeparate(srcColor, dstColor,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL11.glBlendFunc(srcColor, dstColor);
        }
    }

    /**
     * Like {@link GL14#glBlendFuncSeparate} but applies the AA-aware
     * override on the alpha channel.
     */
    public static void blendFuncSeparate(int srcColor, int dstColor,
                                         int srcAlpha, int dstAlpha) {
        if (AAPipeline.isActive()) {
            GL14.glBlendFuncSeparate(srcColor, dstColor,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL14.glBlendFuncSeparate(srcColor, dstColor, srcAlpha, dstAlpha);
        }
    }

    private AABlend() {}
}
