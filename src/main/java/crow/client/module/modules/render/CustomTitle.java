package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

public class CustomTitle extends Module {

    private static CustomTitle instance;

    public static SliderSetting titleScale;
    public static SliderSetting subtitleScale;
    public static SliderSetting yOffset;
    public static TickSetting dropShadow;

    private static volatile String capturedTitle = "";
    private static volatile String capturedSubtitle = "";
    private static volatile int capturedTimer = 0;
    private static volatile int capturedFadeIn = 0;
    private static volatile int capturedDisplay = 0;
    private static volatile int capturedFadeOut = 0;
    private static volatile float capturedPartial = 0.0F;
    private static volatile long lastCaptureNanos = 0L;

    public CustomTitle() {
        super("CustomTitle", ModuleCategory.render);
        instance = this;
        this.registerSetting(titleScale     = new SliderSetting("Title scale", 1.0D, 0.5D, 2.0D, 0.05D));
        this.registerSetting(subtitleScale  = new SliderSetting("Sub scale", 1.0D, 0.5D, 2.0D, 0.05D));
        this.registerSetting(yOffset        = new SliderSetting("Y offset", 0, -100, 100, 1));
        this.registerSetting(dropShadow     = new TickSetting("Shadow", true));
    }

    public static boolean shouldReplaceVanilla() {
        return instance != null && instance.isEnabled() && Utils.Player.isPlayerInGame();
    }

    public static void captureTitle(String title, String subtitle,
                                     int timer, int fadeIn, int display, int fadeOut,
                                     float partialTicks) {
        capturedTitle = title != null ? title : "";
        capturedSubtitle = subtitle != null ? subtitle : "";
        capturedTimer = timer;
        capturedFadeIn = fadeIn;
        capturedDisplay = display;
        capturedFadeOut = fadeOut;
        capturedPartial = partialTicks;
        lastCaptureNanos = System.nanoTime();
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (!shouldReplaceVanilla() || mc.thePlayer == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;
        if (capturedTimer <= 0) return;

        if (System.nanoTime() - lastCaptureNanos > 200_000_000L) return;
        if (!FontUtil.hasLoaded() || FontUtil.title == null) return;

        float age = (float) capturedTimer - capturedPartial;
        int alpha255 = 255;

        if (capturedTimer > capturedFadeOut + capturedDisplay) {

            float f = (float) (capturedFadeIn + capturedDisplay + capturedFadeOut) - age;
            alpha255 = (int) (f * 255.0F / (float) capturedFadeIn);
        }
        if (capturedTimer <= capturedFadeOut) {

            alpha255 = (int) (age * 255.0F / (float) capturedFadeOut);
        }

        alpha255 = MathHelper.clamp_int(alpha255, 0, 255);
        if (alpha255 <= 8) return;

        ScaledResolution sr = crow.client.utils.RenderUtils.scaled();
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        float cx = sw / 2.0F;
        float cy = sh / 2.0F + (float) yOffset.getInput();

        if (!capturedTitle.isEmpty()) {
            drawCenteredLine(capturedTitle, cx, cy - 10.0F, (float) titleScale.getInput() * 2.0F, alpha255);
        }

        if (!capturedSubtitle.isEmpty()) {
            drawCenteredLine(capturedSubtitle, cx, cy + 5.0F, (float) subtitleScale.getInput() * 1.0F, alpha255);
        }
    }

    private void drawCenteredLine(String text, float centerX, float y, float scale, int alpha255) {
        int color = (alpha255 << 24) | 0xFFFFFF;
        int shadowColor = ((alpha255 * 130 / 255) << 24) & 0xFF000000;

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);

        float w = (float) FontUtil.title.getStringWidth(text);
        float x = -w / 2.0F;

        if (dropShadow.isToggled()) {
            FontUtil.title.drawSmoothString(text, x + 1.2F, 1.2F, shadowColor);
        }
        FontUtil.title.drawSmoothString(text, x, 0.0F, color);

        GlStateManager.popMatrix();
    }
}
