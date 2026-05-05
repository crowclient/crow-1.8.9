package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.other.NameHider;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

public class TargetHUD extends Module {

    public static SliderSetting posXOffset, posYOffset, timeout, opacity, scale;
    public static TickSetting customFont;
    public static TickSetting followPlayer;
    public static ComboSetting<FollowPosition> followPosition;
    public static ComboSetting barColor, hudStyle;

    public enum BarColors { Theme, Cyan, Rainbow, Health, White }
    public enum HudStyle { Normal, Compact, Minimal, Exhibit, Modern }
    public enum FollowPosition { Left, Right, Above, Chest }

    private static final int   PANEL_W   = 174;
    private static final int   PANEL_H   = 54;
    private static final int   HEAD_SIZE = 38;
    private static final int   HEAD_PAD  = 8;
    private static final float CARD_R    = 10.0F;
    private static final int   BAR_H     = 4;
    private static final int   BAR_GAP   = 4;

    private float fadeRaw;

    private float fadeAlpha;
    private float fadeTarget;
    private long  lastTickMs;

    private float displayedHealth;

    private float smoothedHurt;

    private long lastHurtTickMs;
    private static final float HURT_SHRINK = 0.18F;
    private static final float HURT_TINT   = 0.85F;

    private AbstractClientPlayer target;
    private long  lastTargetTime;
    private boolean dragging;
    private int     dragOffsetX, dragOffsetY;

    public TargetHUD() {
        super("Target HUD", ModuleCategory.render);

        this.registerSetting(hudStyle       = new ComboSetting("Style", HudStyle.Normal));
        this.registerSetting(posXOffset     = new SliderSetting("X Offset", 42, -300, 300, 1));
        posXOffset.visibleWhen(() -> followPlayer == null || !followPlayer.isToggled());
        this.registerSetting(posYOffset     = new SliderSetting("Y Offset", 20, -300, 300, 1));
        posYOffset.visibleWhen(() -> followPlayer == null || !followPlayer.isToggled());
        this.registerSetting(timeout        = new SliderSetting("Timeout (ms)", 3000, 1000, 8000, 100));
        this.registerSetting(opacity        = new SliderSetting("Opacity", 0.92D, 0.2D, 1.0D, 0.01D));
        this.registerSetting(scale          = new SliderSetting("Size", 1.0D, 0.5D, 2.0D, 0.05D));
        this.registerSetting(customFont     = new TickSetting("Custom Font", true));
        this.registerSetting(followPlayer   = new TickSetting("Follow Player", false));
        this.registerSetting(followPosition = new ComboSetting("Follow Position", FollowPosition.Above));
        followPosition.visibleWhen(() -> followPlayer != null && followPlayer.isToggled());
        this.registerSetting(barColor       = new ComboSetting("Bar Color", BarColors.Theme));
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof AttackEntityEvent) {
            AttackEntityEvent e = (AttackEntityEvent) fe.getEvent();
            if (e.target instanceof AbstractClientPlayer) {
                target = (AbstractClientPlayer) e.target;
                lastTargetTime = System.currentTimeMillis();
            }
        }
    }

    @Subscribe
    public void onRender2d(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;

        updatePreviewTarget();
        if (!updateFade()) return;
        if (target == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        float s = (float) scale.getInput();

        float bounceScale = s * fadeAlpha;
        int anchorX = screenW / 2 + (int) posXOffset.getInput();
        int anchorY = screenH / 2 + (int) posYOffset.getInput();

        handleDragging(screenW, screenH, anchorX, anchorY);
        updateHealth();
        updateHurtState();

        GlStateManager.pushMatrix();
        GlStateManager.translate(anchorX, anchorY, 0);
        GlStateManager.scale(bounceScale, bounceScale, 1.0F);
        GlStateManager.translate(-anchorX, -anchorY, 0);

        drawPanel(anchorX, anchorY);

        GlStateManager.popMatrix();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawPanel(int x, int y) {
        int alphaByte = (int) (255.0F * (float) opacity.getInput() * fadeAlpha);
        if (alphaByte <= 0) return;

        int bgColor = (alphaByte << 24) | 0x101013;
        RenderUtils.drawRoundedRectAA(x, y, x + PANEL_W, y + PANEL_H, CARD_R, bgColor);

        int headX = x + HEAD_PAD;
        int headY = y + (PANEL_H - HEAD_SIZE) / 2;
        drawHead(headX, headY, HEAD_SIZE);

        int textX     = headX + HEAD_SIZE + 10;
        int textRight = x + PANEL_W - 12;
        int textW     = Math.max(0, textRight - textX);

        boolean useCustomFont = customFont.isToggled() && FontUtil.hasLoaded();
        float nameH  = useCustomFont ? FontUtil.bold.getHeight()  : mc.fontRendererObj.FONT_HEIGHT;
        float smallH = useCustomFont ? FontUtil.small.getHeight() : mc.fontRendererObj.FONT_HEIGHT;
        float blockH = nameH + BAR_GAP + BAR_H + BAR_GAP + smallH;
        float blockTop = y + (PANEL_H - blockH) / 2.0F;

        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        String displayName = trimToFit(getTargetDisplayName(), useCustomFont, textW);
        if (useCustomFont) {
            FontUtil.bold.drawSmoothString(displayName, textX, blockTop, nameColor);
        } else {
            mc.fontRendererObj.drawString(displayName, textX, (int) blockTop, nameColor, false);
        }

        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        int   barX = textX;
        int   barY = (int) (blockTop + nameH + BAR_GAP);
        int   barW = textW;
        float barR = BAR_H / 2.0F;
        int   trackColor = ((int) (fadeAlpha * 64.0F) << 24) | 0xF4F6FA;
        if (barW > 0) {
            RenderUtils.drawRoundedRectAA(barX, barY, barX + barW, barY + BAR_H, barR, trackColor);
            int fillW = (int) (barW * MathHelper.clamp_float(displayedHealth / maxHealth, 0.0F, 1.0F));
            if (fillW > 1) {
                RenderUtils.drawRoundedRectAA(barX, barY, barX + fillW, barY + BAR_H, barR,
                        getBarFillColor(fadeAlpha));
            }
        }

        int    healthY     = barY + BAR_H + BAR_GAP;
        String healthText  = String.format("%.0f / %.0f HP", displayedHealth, maxHealth);
        int    healthColor = ((int) (fadeAlpha * 180.0F) << 24) | 0xC8CCD4;
        if (useCustomFont) {
            FontUtil.small.drawSmoothString(healthText, textX, healthY, healthColor);
        } else {
            mc.fontRendererObj.drawString(healthText, textX, healthY, healthColor, false);
        }
    }

    private void drawHead(int x, int y, int size) {
        if (target == null) return;
        ResourceLocation skin = target.getLocationSkin();
        if (skin == null) return;

        float cx        = x + size / 2.0F;
        float cy        = y + size / 2.0F;
        float headScale = 1.0F - HURT_SHRINK * smoothedHurt;
        float gb        = 1.0F - HURT_TINT * smoothedHurt;
        float r         = Math.min(size * 0.22F, size * 0.5F);

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.scale(headScale, headScale, 1.0F);
        GlStateManager.translate(-cx, -cy, 0);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.disableCull();

        mc.getTextureManager().bindTexture(skin);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

        float headAlpha = fadeAlpha * (float) opacity.getInput();
        int aByte = MathHelper.clamp_int(Math.round(headAlpha * 255.0F), 0, 255);
        int gByte = MathHelper.clamp_int(Math.round(gb * 255.0F), 0, 255);
        int tint  = (aByte << 24) | (0xFF << 16) | (gByte << 8) | gByte;

        if (RenderUtils.isTexturedRectShaderAvailable()) {

            RenderUtils.drawRoundedTexturedRect(x, y, size, size, r,
                     8f / 64f,  8f / 64f, 16f / 64f, 16f / 64f, tint);

            RenderUtils.drawRoundedTexturedRect(x, y, size, size, r,
                    40f / 64f,  8f / 64f, 48f / 64f, 16f / 64f, tint);
        } else {

            GlStateManager.color(1.0F, gb, gb, headAlpha);
            drawHeadFanFallback(x, y, size, r,  8f / 64f,  8f / 64f, 16f / 64f, 16f / 64f);
            drawHeadFanFallback(x, y, size, r, 40f / 64f,  8f / 64f, 48f / 64f, 16f / 64f);
        }

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void drawHeadFanFallback(int x, int y, int size, float r,
                                     float u0, float v0, float u1, float v1) {
        float xL = x, yT = y, xR = x + size, yB = y + size;
        float cx = x + size * 0.5F, cy = y + size * 0.5F;
        float uMid = (u0 + u1) * 0.5F, vMid = (v0 + v1) * 0.5F;
        float uRange = u1 - u0, vRange = v1 - v0;
        int seg = 16;

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glTexCoord2f(uMid, vMid);
        GL11.glVertex2f(cx, cy);
        emitArc(xR - r, yT + r, r, 270.0, 360.0, seg, xL, yT, size, size, u0, v0, uRange, vRange);
        emitArc(xR - r, yB - r, r,   0.0,  90.0, seg, xL, yT, size, size, u0, v0, uRange, vRange);
        emitArc(xL + r, yB - r, r,  90.0, 180.0, seg, xL, yT, size, size, u0, v0, uRange, vRange);
        emitArc(xL + r, yT + r, r, 180.0, 270.0, seg, xL, yT, size, size, u0, v0, uRange, vRange);
        emitVertex(xR - r, yT, xL, yT, size, size, u0, v0, uRange, vRange);
        GL11.glEnd();
    }

    private void emitArc(float arcCx, float arcCy, float r,
                         double startDeg, double endDeg, int segments,
                         float bboxX, float bboxY, int bboxW, int bboxH,
                         float u0, float v0, float uRange, float vRange) {
        for (int i = 0; i <= segments; i++) {
            double a = Math.toRadians(startDeg + (endDeg - startDeg) * i / segments);
            float px = arcCx + (float) Math.cos(a) * r;
            float py = arcCy + (float) Math.sin(a) * r;
            emitVertex(px, py, bboxX, bboxY, bboxW, bboxH, u0, v0, uRange, vRange);
        }
    }

    private void emitVertex(float px, float py,
                            float bboxX, float bboxY, int bboxW, int bboxH,
                            float u0, float v0, float uRange, float vRange) {
        float u = u0 + ((px - bboxX) / bboxW) * uRange;
        float v = v0 + ((py - bboxY) / bboxH) * vRange;
        GL11.glTexCoord2f(u, v);
        GL11.glVertex2f(px, py);
    }

    private void handleDragging(int screenW, int screenH, int hudX, int hudY) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }
        int mx = Mouse.getX() * screenW / mc.displayWidth;
        int my = screenH - Mouse.getY() * screenH / mc.displayHeight - 1;
        boolean hovering = mx >= hudX && mx <= hudX + PANEL_W
                        && my >= hudY && my <= hudY + PANEL_H;

        if (Mouse.isButtonDown(0)) {
            if (!dragging && hovering) {
                dragging = true;
                dragOffsetX = (int) posXOffset.getInput() - (mx - screenW / 2);
                dragOffsetY = (int) posYOffset.getInput() - (my - screenH / 2);
            }
            if (dragging) {
                posXOffset.setValue((mx - screenW / 2) + dragOffsetX);
                posYOffset.setValue((my - screenH / 2) + dragOffsetY);
            }
        } else {
            dragging = false;
        }
    }

    private String getTargetDisplayName() {
        String name = target != null ? target.getName() : "Steve";
        if (Crow.moduleManager != null) {
            Module nh = Crow.moduleManager.getModuleByClazz(NameHider.class);
            if (nh != null && nh.isEnabled()) {
                name = NameHider.format(name);
            }
        }
        return name;
    }

    private String trimToFit(String text, boolean customFontInUse, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        int width = customFontInUse
                ? (int) FontUtil.bold.getStringWidth(text)
                : mc.fontRendererObj.getStringWidth(text);
        if (width <= maxWidth) return text;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = sb.toString() + text.charAt(i) + "...";
            int cw = customFontInUse
                    ? (int) FontUtil.bold.getStringWidth(candidate)
                    : mc.fontRendererObj.getStringWidth(candidate);
            if (cw > maxWidth) break;
            sb.append(text.charAt(i));
        }
        return sb.toString() + "...";
    }

    private void updatePreviewTarget() {
        boolean previewMode = mc.currentScreen instanceof GuiChat;
        if (previewMode && target == null && mc.thePlayer instanceof AbstractClientPlayer) {
            target = (AbstractClientPlayer) mc.thePlayer;
            lastTargetTime = System.currentTimeMillis();
        }
    }

    private boolean updateFade() {

        if (target != null && target != mc.thePlayer
                && (!target.isEntityAlive() || target.getHealth() <= 0)) {
            target = null;
            fadeRaw = 0.0F;
            fadeAlpha = 0.0F;
            fadeTarget = 0.0F;
            displayedHealth = 0.0F;
            return false;
        }

        boolean previewMode = mc.currentScreen instanceof GuiChat;

        if (previewMode && target != null) {
            lastTargetTime = System.currentTimeMillis();
        }
        boolean hasTarget = target != null
                && System.currentTimeMillis() - lastTargetTime <= (long) timeout.getInput()
                && (target.getHealth() > 0 || previewMode);
        fadeTarget = hasTarget ? 1.0F : 0.0F;

        long now = System.currentTimeMillis();
        if (lastTickMs == 0L) lastTickMs = now;
        float dt = Math.min(0.1F, (now - lastTickMs) / 1000.0F);
        lastTickMs = now;

        float speed = fadeTarget > fadeRaw ? 6.0F : 9.0F;
        float k = 1.0F - (float) Math.exp(-speed * dt);
        fadeRaw += (fadeTarget - fadeRaw) * k;
        fadeRaw = MathHelper.clamp_float(fadeRaw, 0.0F, 1.0F);

        fadeAlpha = 1.0F - (float) Math.pow(1.0F - fadeRaw, 3.0F);

        if (fadeRaw < 0.005F) {
            fadeRaw = 0.0F;
            fadeAlpha = 0.0F;
            if (!hasTarget && !previewMode) target = null;
            displayedHealth = 0.0F;
            return false;
        }
        return true;
    }

    private void updateHealth() {
        float actual    = target.getHealth();
        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        displayedHealth += (actual - displayedHealth) * 0.15F;
        displayedHealth = MathHelper.clamp_float(displayedHealth, 0.0F, maxHealth);
    }

    private void updateHurtState() {
        long now = System.currentTimeMillis();
        if (target == null) {
            smoothedHurt = 0.0F;
            lastHurtTickMs = now;
            return;
        }
        int   maxHurt = Math.max(1, target.maxHurtTime);
        float raw     = MathHelper.clamp_float(target.hurtTime / (float) maxHurt, 0.0F, 1.0F);

        if (raw >= smoothedHurt) {

            smoothedHurt = raw;
        } else {

            float dt = lastHurtTickMs == 0L ? 0.016F
                                            : Math.min(0.1F, (now - lastHurtTickMs) / 1000.0F);
            float k  = 1.0F - (float) Math.exp(-7.0F * dt);
            smoothedHurt += (raw - smoothedHurt) * k;
        }
        lastHurtTickMs = now;
        if (smoothedHurt < 0.005F) smoothedHurt = 0.0F;
    }

    private int getBarFillColor(float alpha) {
        int a = (int) (255.0F * alpha);
        switch ((BarColors) barColor.getMode()) {
            case Cyan:    return (a << 24) | 0x6DE4FF;
            case Rainbow: return (a << 24) | (Utils.Client.rainbowDraw(2, 0) & 0x00FFFFFF);
            case Health: {
                float frac = displayedHealth / Math.max(1.0F, target.getMaxHealth());
                int rC = (int) (255 * (1.0F - frac));
                int gC = (int) (255 * frac);
                return (a << 24) | (rC << 16) | (gC << 8);
            }
            case White:   return (a << 24) | 0xF4F6FA;
            case Theme:
            default:      return (a << 24) | (GuiModule.getThemeColor(0) & 0x00FFFFFF);
        }
    }
}
