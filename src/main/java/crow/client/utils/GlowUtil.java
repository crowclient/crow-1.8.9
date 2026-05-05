package crow.client.utils;

import crow.client.module.modules.client.RecordingMode;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class GlowUtil {

    private GlowUtil() {}

    public static void drawGlow(float x, float y, float width, float height,
                                float radius, float roundness, int color, float intensity) {
        if (width <= 0 || height <= 0 || radius <= 0 || intensity <= 0) return;
        if (RecordingMode.shouldSkipGlow()) return;

        int alpha = (color >> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        if (alpha <= 0) return;

        float x1 = x;
        float y1 = y;
        float x2 = x + width;
        float y2 = y + height;

        int layers = Math.max(3, Math.min(12, Math.round(radius * 1.5F)));

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        for (int i = layers; i >= 1; i--) {
            float progress = i / (float) layers;
            float spread = progress * radius * 2.0F;

            float alphaScale = (1.0F - progress * progress) * 0.14F * intensity;
            int layerAlpha = Math.max(1, Math.min(255, (int) (alpha * alphaScale)));
            float layerRound = roundness + spread * 0.5F;

            RenderUtils.drawRoundedRect(
                x1 - spread, y1 - spread,
                x2 + spread, y2 + spread,
                layerRound, (layerAlpha << 24) | rgb
            );
        }

        GL11.glPopAttrib();
        RenderUtils.syncAllGlState();
    }

    public static void destroy() {}

    public static void onResize() {}
}
