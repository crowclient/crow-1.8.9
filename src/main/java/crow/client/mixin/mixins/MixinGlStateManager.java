package crow.client.mixin.mixins;

import crow.client.render.aa.AABlend;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects GL blend-function calls inside {@link GlStateManager} to the
 * AA-aware dispatcher in {@link AABlend}.
 *
 * <p>When the AA pipeline is active, every UI draw inside the supersampling
 * FBO needs to use {@code glBlendFuncSeparate} with alpha factors
 * {@code (GL_ONE, GL_ONE_MINUS_SRC_ALPHA)} so the FBO's alpha channel
 * accumulates proper "over" composition rather than getting squashed by
 * standard {@code glBlendFunc}'s same-factor-for-RGB-and-A behaviour.
 *
 * <p>This is the single critical piece that makes alpha-FBO compositing of
 * MC's UI pipeline tractable. Without it, every UI alpha blend leaks alpha
 * into the squashed-form trap and the composite back to the back buffer
 * can't recover.
 *
 * <p>{@link AABlend} no-ops to plain {@code glBlendFunc} when AA is off, so
 * the Mixin has zero effect during normal world rendering or when the AA
 * setting is off.
 */
@Mixin(GlStateManager.class)
public class MixinGlStateManager {

    @Redirect(
        method = "blendFunc(II)V",
        at = @At(value = "INVOKE",
                 target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V"),
        require = 0
    )
    private static void crow$blendFunc(int srcFactor, int dstFactor) {
        AABlend.blendFunc(srcFactor, dstFactor);
    }

    @Redirect(
        method = "tryBlendFuncSeparate(IIII)V",
        at = @At(value = "INVOKE",
                 target = "Lorg/lwjgl/opengl/GL14;glBlendFuncSeparate(IIII)V"),
        require = 0
    )
    private static void crow$tryBlendFuncSeparate(int srcFactor, int dstFactor,
                                                   int srcAlpha, int dstAlpha) {
        AABlend.blendFuncSeparate(srcFactor, dstFactor, srcAlpha, dstAlpha);
    }
}
