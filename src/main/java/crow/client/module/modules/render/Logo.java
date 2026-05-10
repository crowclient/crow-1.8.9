package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.modules.HUD;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class Logo extends Module {

    public static TickSetting showIcon;
    public static TickSetting showText;
    public static SliderSetting posX, posY;
    public static SliderSetting logoScale;
    public static TickSetting accentLine;
    public static TickSetting showVersion;
    public static TickSetting background;

    private boolean dragging;
    private int dragOffsetX, dragOffsetY;

    private static final ResourceLocation CROW_ICON = new ResourceLocation("crow", "icons/crow.png");
    private static final String CLIENT_NAME = "Crow";
    private static final String CLIENT_VERSION = "v1.0";

    public Logo() {
        super("Logo", ModuleCategory.render);
        this.registerSetting(showIcon = new TickSetting("Show Icon", true));
        this.registerSetting(showText = new TickSetting("Show Text", true));
        this.registerSetting(posX = new SliderSetting("X", 6, 0, 4000, 1));
        this.registerSetting(posY = new SliderSetting("Y", 6, 0, 2000, 1));
        this.registerSetting(logoScale = new SliderSetting("Scale", 1.0D, 0.5D, 2.0D, 0.05D));
        this.registerSetting(accentLine = new TickSetting("Accent Line", true));
        this.registerSetting(showVersion = new TickSetting("Show Version", true));
        this.registerSetting(background = new TickSetting("Background", true));
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;

        boolean icon = showIcon.isToggled();
        boolean text = showText.isToggled();
        if (!icon && !text) return;

        float s = (float) logoScale.getInput();
        int x = (int) posX.getInput();
        int y = (int) posY.getInput();

        int totalW = computeWidth(icon, text, s);
        int totalH = computeHeight(icon, text, s);

        handleDragging(x, y, x + totalW, y + totalH);

        GlStateManager.pushMatrix();

        boolean useBlur = HUD.enableBlur != null && HUD.enableBlur.isToggled();
        int accent = GuiModule.getThemeColor(0);
        int accentRGB = accent & 0x00FFFFFF;

        if (useBlur) {
            GUIBlurUtil.drawBlurredBackground(x, y, totalW, totalH,
                    (int) HUD.blurRadius.getInput(), 8, 0.5f);
            mc.entityRenderer.setupOverlayRendering();
        }

        if (background.isToggled()) {
            RenderUtils.drawRoundedRectAA(x, y, x + totalW, y + totalH, 8, 0xD014171D);
        }

        if (accentLine.isToggled()) {
            RenderUtils.drawRoundedRectAA(x + 6, y + 2, x + totalW - 6, y + 3, 1,
                    0x80000000 | accentRGB);
        }

        float contentY = y + 5;
        float contentX = x + 8;

        if (icon) {
            int imgSize = Math.round(16 * s);
            drawIcon(contentX, contentY + (computeHeight(icon, text, s) - 10 - imgSize) / 2.0F, imgSize);
            contentX += imgSize + 5;
        }

        if (text) {
            float textY = contentY + (computeHeight(icon, text, s) - 10 - FontUtil.bold.getHeight() * s) / 2.0F;

            GlStateManager.pushMatrix();
            GlStateManager.translate(contentX, textY, 0);
            GlStateManager.scale(s, s, 1);

            FontUtil.bold.drawSmoothString(CLIENT_NAME, 0, 0, 0xFFF2F4F7);

            if (showVersion.isToggled()) {
                float nameW = (float) FontUtil.bold.getStringWidth(CLIENT_NAME);
                FontUtil.small.drawSmoothString(CLIENT_VERSION, nameW + 4, 2, 0x80FFFFFF);
            }

            GlStateManager.popMatrix();
        }

        GlStateManager.popMatrix();
    }

    private int computeWidth(boolean icon, boolean text, float s) {
        int w = 16;
        if (icon) {
            w += Math.round(16 * s) + 5;
        }
        if (text) {
            float textW = (float) FontUtil.bold.getStringWidth(CLIENT_NAME) * s;
            if (showVersion != null && showVersion.isToggled()) {
                textW += 4 + (float) FontUtil.small.getStringWidth(CLIENT_VERSION) * s;
            }
            w += Math.round(textW);
        }
        return w;
    }

    private int computeHeight(boolean icon, boolean text, float s) {
        int textH = Math.round(FontUtil.bold.getHeight() * s);
        int imgH = Math.round(16 * s);
        int contentH;
        if (icon && text) {
            contentH = Math.max(textH, imgH);
        } else if (icon) {
            contentH = imgH;
        } else {
            contentH = textH;
        }
        return contentH + 10;
    }

    private void drawIcon(float x, float y, int size) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.getTextureManager().bindTexture(CROW_ICON);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(
                (int) x, (int) y, 0, 0, size, size, size, size);
    }

    private void handleDragging(int left, int top, int right, int bottom) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }

        int screenW = mc.currentScreen.width;
        int screenH = mc.currentScreen.height;
        int mx = Mouse.getX() * screenW / mc.displayWidth;
        int my = screenH - Mouse.getY() * screenH / mc.displayHeight - 1;
        boolean hovering = mx >= left && mx <= right && my >= top && my <= bottom;

        if (Mouse.isButtonDown(0)) {
            if (!dragging && hovering) {
                dragging = true;
                dragOffsetX = (int) posX.getInput() - mx;
                dragOffsetY = (int) posY.getInput() - my;
            }
            if (dragging) {
                posX.setValue(mx + dragOffsetX);
                posY.setValue(my + dragOffsetY);
            }
        } else {
            dragging = false;
        }
    }
}
