package crow.client.mixin.mixins;

import crow.client.module.modules.render.CustomHotbar;
import crow.client.module.modules.render.ScoreboardMod;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.ScoreObjective;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public class MixinGuiIngame {

    @Shadow
    private int remainingHighlightTicks;

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void crow$replaceHotbar(ScaledResolution scaledResolution, float partialTicks, CallbackInfo ci) {
        if (CustomHotbar.shouldReplaceVanilla()) {
            this.remainingHighlightTicks = 0;
            ci.cancel();
            return;
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.enableBlend();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.enableAlpha();
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "renderGameOverlay", at = @At("HEAD"))
    private void crow$suppressHighlight(float partialTicks, CallbackInfo ci) {
        if (CustomHotbar.shouldReplaceVanilla()) {
            this.remainingHighlightTicks = 0;
        }
    }

    @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
    private void crow$replaceScoreboard(ScoreObjective objective, ScaledResolution scaledResolution, CallbackInfo ci) {
        if (objective != null && ScoreboardMod.shouldReplaceVanilla()) {
            ci.cancel();
        }
    }
}
