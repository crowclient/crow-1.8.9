package crow.client.mixin.mixins;

import crow.client.module.modules.render.Viewmodel;
import crow.client.utils.RenderUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    @Inject(method = "renderItemInFirstPerson(F)V", at = @At("HEAD"))
    private void crow$resetItemState(float partialTicks, CallbackInfo ci) {

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.enableBlend();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.enableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.depthMask(true);
        GlStateManager.disableCull();
        GlStateManager.enableCull();
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "renderOverlays(F)V", at = @At("HEAD"))
    private void crow$resetOverlayState(float partialTicks, CallbackInfo ci) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderUtils.syncAllGlState();
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "transformFirstPersonItem", at = @At("RETURN"))
    private void crow$applyViewmodel(float equipProgress, float swingProgress, CallbackInfo ci) {
        if (!Viewmodel.isActive()) {
            return;
        }

        float scale = Viewmodel.getScale();
        GlStateManager.translate(Viewmodel.getTranslateX(), Viewmodel.getTranslateY(), Viewmodel.getTranslateZ());
        GlStateManager.rotate(Viewmodel.getRotateX(), 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(Viewmodel.getRotateY(), 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(Viewmodel.getRotateZ(), 0.0F, 0.0F, 1.0F);
        GlStateManager.scale(scale, scale, scale);
    }
}
