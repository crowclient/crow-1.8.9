package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.HUD;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.other.NameHider;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

public class TargetHUD extends Module {

    /** Opaque-equivalent base for HUD panel fills. Everything multiplies this
     *  by the opacity setting and the fade, so lowering it here makes every
     *  layout translucent at once. Blur is off by default on the HUD, so this
     *  show-through plus the rim and shadow is what reads as glass. */
    private static final float GLASS_ALPHA = 196.0F;

    /* Drop shadow. Wide and weak on purpose: the HUD floats over the world
     * with nothing behind it, so a tight shadow just traces a dark outline of
     * the panel instead of reading as depth. These are paired values — raising
     * the alpha without raising the blur brings the hard rim straight back. */
    private static final float SHADOW_ALPHA = 0x4A;
    private static final float SHADOW_Y     = 5.0F;
    private static final float SHADOW_BLUR  = 12.0F;

    /**
     * The one panel material every layout draws its background with.
     *
     * <p>Both the fill and the shadow are scaled by the reveal fade and the
     * opacity slider. That coupling is the point: a fixed-alpha shadow leaves
     * a dark blob hanging in the air under a panel that has already faded out,
     * and at low opacity it shows through the glass as a grey smear.
     *
     * @param rgb      0xRRGGBB fill, alpha supplied here
     * @param alphaMul per-style fill multiplier (Glass runs thinner)
     */
    private void panelBg(float x, float y, float x1, float y1,
                         float radius, int rgb, float alphaMul) {
        float reveal = fadeAlpha * (float) opacity.getInput();
        int fillA = (int) (GLASS_ALPHA * reveal * alphaMul);
        if (fillA <= 0) return;
        boolean shadow = HUD.dropShadow == null || HUD.dropShadow.isToggled();
        RenderUtils.drawGlassPanel(x, y, x1, y1, radius, (fillA << 24) | rgb,
                shadow ? (int) (SHADOW_ALPHA * reveal) : 0, SHADOW_Y, SHADOW_BLUR);
    }

    public static SliderSetting posXOffset, posYOffset, timeout, opacity, scale;
    public static TickSetting customFont;
    public static TickSetting followPlayer;
    public static ComboSetting<FollowPosition> followPosition;
    public static ComboSetting barColor, hudStyle;

    public enum BarColors { Theme, Cyan, Rainbow, Health, White }
    public enum HudStyle { Normal, Compact, Minimal, Exhibit, Modern, Glass, Pill, Bar, Bracket, Card }
    public enum FollowPosition { Left, Right, Above, Chest }

    /** Default style parameters — see {@link StyleParams} for per-style overrides. */
    private static final int   PANEL_W   = 174;
    private static final int   PANEL_H   = 54;
    private static final int   HEAD_SIZE = 38;
    private static final int   HEAD_PAD  = 8;
    private static final float CARD_R    = 10.0F;
    private static final int   BAR_H     = 4;
    private static final int   BAR_GAP   = 4;

    /** Visual parameters that vary by {@link HudStyle}. */
    private static final class StyleParams {
        int panelW = PANEL_W, panelH = PANEL_H;
        int headSize = HEAD_SIZE, headPad = HEAD_PAD;
        int barH = BAR_H, barGap = BAR_GAP;
        float cardR = CARD_R;
        float bgAlphaMul = 1.0F;
        boolean showBg = true;
        boolean showHead = true;
        boolean showBar = true;
        boolean accent = false;
        boolean brackets = false;
    }

    private StyleParams styleParams() {
        StyleParams p = new StyleParams();
        switch ((HudStyle) hudStyle.getMode()) {
            case Compact:
                p.panelW = 140; p.panelH = 40;
                p.headSize = 28; p.headPad = 6;
                p.cardR = 8.0F;
                break;
            case Minimal:
                p.showBg = false; p.showHead = false; p.showBar = false;
                p.panelW = 150; p.panelH = 24;
                break;
            case Exhibit:
                p.panelW = 220; p.panelH = 70;
                p.headSize = 50; p.headPad = 10;
                p.cardR = 14.0F;
                p.barH = 5; p.barGap = 5;
                break;
            case Modern:
                p.accent = true;
                p.cardR = 12.0F;
                break;
            case Glass:
                p.bgAlphaMul = 0.45F;
                p.cardR = 14.0F;
                break;
            case Pill:
                p.cardR = 22.0F;
                p.panelH = 44;
                p.headSize = 32;
                break;
            case Bracket:
                p.showBg = false; p.showHead = false; p.showBar = false;
                p.brackets = true;
                p.panelW = 200; p.panelH = 26;
                break;
            // Card and Bar use dedicated draw paths — params unused below.
            case Card:
            case Bar:
            case Normal:
            default:
                break;
        }
        return p;
    }

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

    /* World-to-screen state for the Follow option. Populated from the
     * 3D render pass (RenderWorldLastEvent) so the matrices are still
     * the world's, not the 2D overlay's. Read in onRender2d. */
    private static final FloatBuffer MV  = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PR  = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer   VP  = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer OUT = BufferUtils.createFloatBuffer(3);
    private float   followScreenX, followScreenY;
    private boolean followValid;

    public TargetHUD() {
        super("Target HUD", ModuleCategory.render);

        this.registerSetting(hudStyle       = new ComboSetting("Style", HudStyle.Normal));
        this.registerSetting(posXOffset     = new SliderSetting("X offset", 42, -300, 300, 1));
        posXOffset.visibleWhen(() -> followPlayer == null || !followPlayer.isToggled());
        this.registerSetting(posYOffset     = new SliderSetting("Y offset", 20, -300, 300, 1));
        posYOffset.visibleWhen(() -> followPlayer == null || !followPlayer.isToggled());
        this.registerSetting(timeout        = new SliderSetting("Timeout", 3000, 1000, 8000, 100));
        this.registerSetting(opacity        = new SliderSetting("Opacity", 0.92D, 0.2D, 1.0D, 0.01D));
        this.registerSetting(scale          = new SliderSetting("Size", 1.0D, 0.5D, 2.0D, 0.05D));
        this.registerSetting(customFont     = new TickSetting("Custom font", true));
        this.registerSetting(followPlayer   = new TickSetting("Follow", false));
        this.registerSetting(followPosition = new ComboSetting("Follow pos", FollowPosition.Above));
        followPosition.visibleWhen(() -> followPlayer != null && followPlayer.isToggled());
        this.registerSetting(barColor       = new ComboSetting("Bar color", BarColors.Theme));
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof AttackEntityEvent) {
            AttackEntityEvent e = (AttackEntityEvent) fe.getEvent();
            if (e.target instanceof AbstractClientPlayer) {
                target = (AbstractClientPlayer) e.target;
                lastTargetTime = System.currentTimeMillis();
            }
            return;
        }

        // World-pass: project the current target's position into screen
        // space and stash it. Only does work if Follow is on and we have
        // a real target — saves a glGet call every frame otherwise.
        if (fe.getEvent() instanceof RenderWorldLastEvent
                && followPlayer != null && followPlayer.isToggled()
                && target != null && target != mc.thePlayer
                && Utils.Player.isPlayerInGame()) {
            updateFollowProjection(((RenderWorldLastEvent) fe.getEvent()).partialTicks);
        }
    }

    /** Capture the modelview/projection matrices from the live 3D pass
     *  and project the target's world position to virtual screen space.
     *  Stores the result in {@link #followScreenX}/{@link #followScreenY};
     *  sets {@link #followValid} to true if the projection landed inside
     *  the visible depth range (i.e. the target isn't behind the camera
     *  or clipped). */
    private void updateFollowProjection(float partialTicks) {
        followValid = false;
        if (target == null) return;

        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks - vx;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks - vy;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks - vz;

        // Pick the projection's Y origin based on which side the HUD
        // should hang off — above the head, on either flank, or chest-
        // height centered.
        FollowPosition fp = (FollowPosition) followPosition.getMode();
        double projY;
        switch (fp) {
            case Above: projY = y + target.height + 0.30; break;
            case Chest: projY = y + target.height * 0.55; break;
            case Left:
            case Right:
            default:    projY = y + target.height * 0.60; break;
        }

        MV.rewind(); PR.rewind(); VP.rewind(); OUT.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MV);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PR);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VP);

        if (!GLU.gluProject((float) x, (float) projY, (float) z,
                MV, PR, VP, OUT)) return;
        float depth = OUT.get(2);
        if (depth < 0.0F || depth > 1.0F) return;

        int sf = Math.max(1, new ScaledResolution(mc).getScaleFactor());
        followScreenX = OUT.get(0) / sf;
        followScreenY = (VP.get(3) - OUT.get(1)) / sf;
        followValid = true;
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

        StyleParams style = styleParams();
        HudStyle styleEnum = (HudStyle) hudStyle.getMode();
        int hitW = panelHitWidth(styleEnum, style);
        int hitH = panelHitHeight(styleEnum, style);

        // Pick the panel anchor. Follow mode pins the HUD to the target's
        // projected screen position with a per-side offset; otherwise we
        // use the user's draggable XY offset from screen center.
        int anchorX, anchorY;
        boolean usingFollow = followPlayer.isToggled()
                && target != mc.thePlayer
                && followValid;
        if (usingFollow) {
            FollowPosition fp = (FollowPosition) followPosition.getMode();
            int margin = 12;
            switch (fp) {
                case Left:
                    anchorX = Math.round(followScreenX) - hitW - margin;
                    anchorY = Math.round(followScreenY) - hitH / 2;
                    break;
                case Right:
                    anchorX = Math.round(followScreenX) + margin;
                    anchorY = Math.round(followScreenY) - hitH / 2;
                    break;
                case Chest:
                    anchorX = Math.round(followScreenX) - hitW / 2;
                    anchorY = Math.round(followScreenY) - hitH / 2;
                    break;
                case Above:
                default:
                    anchorX = Math.round(followScreenX) - hitW / 2;
                    anchorY = Math.round(followScreenY) - hitH - margin;
                    break;
            }
            // Keep the panel on-screen even when the target is near an edge.
            anchorX = MathHelper.clamp_int(anchorX, 2, screenW - hitW - 2);
            anchorY = MathHelper.clamp_int(anchorY, 2, screenH - hitH - 2);
        } else if (followPlayer.isToggled() && target != mc.thePlayer) {
            // Follow is on but the target isn't on-screen (behind camera,
            // clipped, etc). Don't draw a stale HUD floating at the center.
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        } else {
            anchorX = screenW / 2 + (int) posXOffset.getInput();
            anchorY = screenH / 2 + (int) posYOffset.getInput();
        }

        // Dragging only makes sense when the HUD is free-floating. When
        // following the target, the position is driven by projection.
        if (!usingFollow) {
            handleDragging(screenW, screenH, anchorX, anchorY, hitW, hitH);
        } else {
            dragging = false;
        }
        updateHealth();
        updateHurtState();

        // Reveal pops from 92% about the panel's centre. Scaling from zero
        // about the top-left corner (the old behaviour) read as the HUD being
        // sucked into its own anchor, and because the shadow shader derives its
        // blur radius from the live modelview it also crushed the blur to
        // sub-pixel on the way in — the hard black rim on every fade.
        float bounceScale = s * (0.92F + 0.08F * fadeAlpha);
        float pivotX = anchorX + hitW / 2.0F;
        float pivotY = anchorY + hitH / 2.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(pivotX, pivotY, 0);
        GlStateManager.scale(bounceScale, bounceScale, 1.0F);
        GlStateManager.translate(-pivotX, -pivotY, 0);

        switch (styleEnum) {
            case Glass:   drawGlassLayout(anchorX, anchorY);   break;
            case Modern:  drawModernLayout(anchorX, anchorY);  break;
            case Exhibit: drawExhibitLayout(anchorX, anchorY); break;
            case Pill:    drawPillLayout(anchorX, anchorY);    break;
            case Bracket: drawBracketLayout(anchorX, anchorY); break;
            case Bar:     drawBarLayout(anchorX, anchorY);     break;
            case Card:    drawCardLayout(anchorX, anchorY);    break;
            case Compact: drawCompactLayout(anchorX, anchorY); break;
            case Minimal: drawMinimalLayout(anchorX, anchorY); break;
            case Normal:
            default:      drawPanel(anchorX, anchorY, style);  break;
        }

        GlStateManager.popMatrix();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawPanel(int x, int y, StyleParams p) {
        if (p.showBg) {
            panelBg(x, y, x + p.panelW, y + p.panelH, p.cardR, 0x101013, p.bgAlphaMul);
            if (p.accent) {
                int accentA = (int) (fadeAlpha * 255.0F);
                int accentCol = (accentA << 24) | (GuiModule.getThemeColor(0) & 0x00FFFFFF);
                RenderUtils.drawRoundedRectAA(x, y + 3, x + 3, y + p.panelH - 3, 1.5F, accentCol);
            }
        }

        int textX;
        int textRight = x + p.panelW - 12;
        if (p.showHead) {
            int headX = x + p.headPad;
            int headY = y + (p.panelH - p.headSize) / 2;
            drawHead(headX, headY, p.headSize);
            textX = headX + p.headSize + 10;
        } else {
            textX = x + (p.accent ? 12 : 8);
        }
        int textW = Math.max(0, textRight - textX);

        boolean useCustomFont = customFont.isToggled() && FontUtil.hasLoaded();
        float nameH  = useCustomFont ? FontUtil.bold.getHeight()  : mc.fontRendererObj.FONT_HEIGHT;
        float smallH = useCustomFont ? FontUtil.small.getHeight() : mc.fontRendererObj.FONT_HEIGHT;
        float blockH = nameH + (p.showBar ? (p.barGap + p.barH + p.barGap + smallH) : (p.barGap + smallH));
        float blockTop = y + (p.panelH - blockH) / 2.0F;

        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        String displayName = trimToFit(getTargetDisplayName(), useCustomFont, textW);
        String shownName = p.brackets ? ("[ " + displayName + " ]") : displayName;
        if (useCustomFont) {
            FontUtil.bold.drawSmoothString(shownName, textX, blockTop, nameColor);
        } else {
            mc.fontRendererObj.drawString(shownName, textX, (int) blockTop, nameColor, false);
        }

        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        int barX = textX;
        int barY = (int) (blockTop + nameH + p.barGap);
        int barW = textW;
        float barR = p.barH / 2.0F;
        int trackColor = ((int) (fadeAlpha * 64.0F) << 24) | 0xF4F6FA;
        if (p.showBar && barW > 0) {
            RenderUtils.drawRoundedRectAA(barX, barY, barX + barW, barY + p.barH, barR, trackColor);
            // Sub-pixel width: the SDF fill antialiases its own edge, so a
            // float here makes the bar glide instead of stepping a pixel at a
            // time as health drains.
            float fillW = barW * MathHelper.clamp_float(displayedHealth / maxHealth, 0.0F, 1.0F);
            if (fillW > 1) {
                RenderUtils.drawRoundedRectAA(barX, barY, barX + fillW, barY + p.barH, barR,
                        getBarFillColor(fadeAlpha));
            }
        }

        int healthY = p.showBar ? (barY + p.barH + p.barGap) : (int) (blockTop + nameH + p.barGap);
        String healthText = String.format("%.0f / %.0f HP", displayedHealth, maxHealth);
        int healthColor = ((int) (fadeAlpha * 180.0F) << 24) | 0xC8CCD4;
        if (useCustomFont) {
            FontUtil.small.drawSmoothString(healthText, textX, healthY, healthColor);
        } else {
            mc.fontRendererObj.drawString(healthText, textX, healthY, healthColor, false);
        }
    }

    /** Vertical layout: big head on top, name + HP centered below. */
    private void drawCardLayout(int x, int y) {
        final int panelW = 110, panelH = 132, headSize = 64;
        panelBg(x, y, x + panelW, y + panelH, 12.0F, 0x101013, 1.0F);
        int headX = x + (panelW - headSize) / 2;
        int headY = y + 10;
        drawHead(headX, headY, headSize);

        boolean useCustomFont = customFont.isToggled() && FontUtil.hasLoaded();
        String displayName = trimToFit(getTargetDisplayName(), useCustomFont, panelW - 12);
        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        float nameW = useCustomFont ? (float) FontUtil.bold.getStringWidth(displayName)
                                    : mc.fontRendererObj.getStringWidth(displayName);
        float nameX = x + (panelW - nameW) / 2.0F;
        float nameY = y + 10 + headSize + 8;
        if (useCustomFont) {
            FontUtil.bold.drawSmoothString(displayName, nameX, nameY, nameColor);
        } else {
            mc.fontRendererObj.drawString(displayName, (int) nameX, (int) nameY, nameColor, false);
        }

        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        int barX = x + 10, barY = (int) (nameY + 14), barW = panelW - 20, barH = 4;
        int trackColor = ((int) (fadeAlpha * 64.0F) << 24) | 0xF4F6FA;
        RenderUtils.drawRoundedRectAA(barX, barY, barX + barW, barY + barH, 2.0F, trackColor);
        float fillW = barW * MathHelper.clamp_float(displayedHealth / maxHealth, 0.0F, 1.0F);
        if (fillW > 1) {
            RenderUtils.drawRoundedRectAA(barX, barY, barX + fillW, barY + barH, 2.0F,
                    getBarFillColor(fadeAlpha));
        }

        String healthText = String.format("%.0f / %.0f HP", displayedHealth, maxHealth);
        float hpW = useCustomFont ? (float) FontUtil.small.getStringWidth(healthText)
                                  : mc.fontRendererObj.getStringWidth(healthText);
        float hpX = x + (panelW - hpW) / 2.0F;
        float hpY = barY + barH + 4;
        int healthColor = ((int) (fadeAlpha * 180.0F) << 24) | 0xC8CCD4;
        if (useCustomFont) {
            FontUtil.small.drawSmoothString(healthText, hpX, hpY, healthColor);
        } else {
            mc.fontRendererObj.drawString(healthText, (int) hpX, (int) hpY, healthColor, false);
        }
    }

    /** Wide horizontal slim bar: tiny head, name + HP inline. */
    private void drawBarLayout(int x, int y) {
        final int panelW = 280, panelH = 26, headSize = 18;
        panelBg(x, y, x + panelW, y + panelH, 8.0F, 0x101013, 1.0F);
        int headX = x + 4;
        int headY = y + (panelH - headSize) / 2;
        drawHead(headX, headY, headSize);

        boolean useCustomFont = customFont.isToggled() && FontUtil.hasLoaded();
        float nameH = useCustomFont ? FontUtil.bold.getHeight() : mc.fontRendererObj.FONT_HEIGHT;
        int textX = headX + headSize + 6;
        float textY = y + (panelH - nameH) / 2.0F;

        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        String displayName = trimToFit(getTargetDisplayName(), useCustomFont, 110);
        float nameW = useCustomFont ? (float) FontUtil.bold.getStringWidth(displayName)
                                    : mc.fontRendererObj.getStringWidth(displayName);
        if (useCustomFont) {
            FontUtil.bold.drawSmoothString(displayName, textX, textY, nameColor);
        } else {
            mc.fontRendererObj.drawString(displayName, textX, (int) textY, nameColor, false);
        }

        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        int barX = textX + (int) nameW + 8;
        int barY = y + (panelH - 3) / 2;
        int barW = (x + panelW - 60) - barX;
        if (barW < 20) barW = 20;
        int trackColor = ((int) (fadeAlpha * 64.0F) << 24) | 0xF4F6FA;
        RenderUtils.drawRoundedRectAA(barX, barY, barX + barW, barY + 3, 1.5F, trackColor);
        float fillW = barW * MathHelper.clamp_float(displayedHealth / maxHealth, 0.0F, 1.0F);
        if (fillW > 1) {
            RenderUtils.drawRoundedRectAA(barX, barY, barX + fillW, barY + 3, 1.5F,
                    getBarFillColor(fadeAlpha));
        }

        String healthText = String.format("%.0f HP", displayedHealth);
        float hpW = useCustomFont ? (float) FontUtil.small.getStringWidth(healthText)
                                  : mc.fontRendererObj.getStringWidth(healthText);
        float hpX = x + panelW - hpW - 6;
        int healthColor = ((int) (fadeAlpha * 200.0F) << 24) | 0xC8CCD4;
        if (useCustomFont) {
            FontUtil.small.drawSmoothString(healthText, hpX, textY, healthColor);
        } else {
            mc.fontRendererObj.drawString(healthText, (int) hpX, (int) textY, healthColor, false);
        }
    }

    // ------------------------------------------------------------------
    // Per-style distinct layouts. Each method is responsible for its own
    // background, geometry, and contents so the styles read as genuinely
    // different designs rather than reskins of a shared panel.
    // ------------------------------------------------------------------

    /** Glass: frosted blur background, thin highlight outline, minimal chrome. */
    private void drawGlassLayout(int x, int y) {
        final int panelW = 200, panelH = 60;
        float aMul = (float) opacity.getInput() * fadeAlpha;

        // Live blurred backdrop — the visual signature of this style.
        GUIBlurUtil.drawBlurredBackground(x, y, panelW, panelH, 6, 14, 0.6F * aMul);
        mc.entityRenderer.setupOverlayRendering();

        // Same dark smoked tint as every other panel — this layout's point is
        // the real backdrop blur above, not a different colour. The rim and
        // hairline come from the shared material.
        panelBg(x, y, x + panelW, y + panelH, 14.0F, 0x0E1014, 0.72F);

        int headSize = 40;
        int headX = x + 10;
        int headY = y + (panelH - headSize) / 2;
        drawHead(headX, headY, headSize);

        boolean f = customFont.isToggled() && FontUtil.hasLoaded();
        int textX = headX + headSize + 10;
        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF6F8FC;
        String name = trimToFit(getTargetDisplayName(), f, x + panelW - textX - 12);
        if (f) FontUtil.bold.drawSmoothString(name, textX, y + 12, nameColor);
        else mc.fontRendererObj.drawString(name, textX, y + 12, nameColor, false);

        float maxHP = Math.max(1.0F, target.getMaxHealth());
        int barX = textX, barY = y + 32, barW = panelW - (textX - x) - 12, barH = 4;
        RenderUtils.drawRoundedRectAA(barX, barY, barX + barW, barY + barH, 2.0F,
                ((int) (fadeAlpha * 70.0F) << 24) | 0xF4F6FA);
        float fillW = barW * MathHelper.clamp_float(displayedHealth / maxHP, 0.0F, 1.0F);
        if (fillW > 1) {
            RenderUtils.drawRoundedRectAA(barX, barY, barX + fillW, barY + barH, 2.0F,
                    getBarFillColor(fadeAlpha));
        }

        String hp = String.format("%.0f / %.0f", displayedHealth, maxHP);
        int hpColor = ((int) (fadeAlpha * 200.0F) << 24) | 0xE8ECF4;
        if (f) FontUtil.small.drawSmoothString(hp, textX, y + 42, hpColor);
        else mc.fontRendererObj.drawString(hp, textX, y + 42, hpColor, false);
    }

    /** Modern: no head, theme-color stripe on the left, vertical info column. */
    private void drawModernLayout(int x, int y) {
        final int panelW = 156, panelH = 50;
        panelBg(x, y, x + panelW, y + panelH, 8.0F, 0x0B0C10, 1.0F);

        // Theme stripe fills the full height — bold left-side accent. One
        // antialiased two-stop fill, not a base rect plus a strip-tessellated
        // gradient on top: that path insets its corners geometrically, so it
        // stairsteps against the SDF panel underneath.
        int stripeA = (int) (fadeAlpha * 255.0F);
        int themeRgb = GuiModule.getThemeColor(0) & 0x00FFFFFF;
        RenderUtils.drawRoundedRectAA(x, y, x + 4, y + panelH, 2.0F,
                (stripeA << 24) | themeRgb,
                (stripeA << 24) | (GuiModule.getThemeColor(-140) & 0x00FFFFFF), null);

        boolean f = customFont.isToggled() && FontUtil.hasLoaded();
        int textX = x + 14;
        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        String name = trimToFit(getTargetDisplayName(), f, panelW - 24);
        if (f) FontUtil.bold.drawSmoothString(name, textX, y + 8, nameColor);
        else mc.fontRendererObj.drawString(name, textX, y + 8, nameColor, false);

        float maxHP = Math.max(1.0F, target.getMaxHealth());
        int barX = textX, barY = y + 28, barW = panelW - 24, barH = 3;
        RenderUtils.drawRoundedRectAA(barX, barY, barX + barW, barY + barH, 1.5F,
                ((int) (fadeAlpha * 60.0F) << 24) | 0xF4F6FA);
        float fillW = barW * MathHelper.clamp_float(displayedHealth / maxHP, 0.0F, 1.0F);
        if (fillW > 1) {
            RenderUtils.drawRoundedRectAA(barX, barY, barX + fillW, barY + barH, 1.5F,
                    getBarFillColor(fadeAlpha));
        }

        String hp = String.format("%.1f HP", displayedHealth);
        int hpColor = ((int) (fadeAlpha * 200.0F) << 24) | (themeRgb);
        if (f) FontUtil.small.drawSmoothString(hp, textX, y + 36, hpColor);
        else mc.fontRendererObj.drawString(hp, textX, y + 36, hpColor, false);
    }

    /** Exhibit: large, header strip with theme color, hearts row instead of bar. */
    private void drawExhibitLayout(int x, int y) {
        final int panelW = 240, panelH = 78;
        panelBg(x, y, x + panelW, y + panelH, 12.0F, 0x101013, 1.0F);

        // Theme-colored header strip across the top. Two-stop AA fill rather
        // than a flat rect plus a tessellated gradient — the strip path
        // stairsteps the header's rounded top corners against the panel.
        int headerA = (int) (fadeAlpha * 200.0F);
        int themeRgb = GuiModule.getThemeColor(0) & 0x00FFFFFF;
        RenderUtils.drawRoundedRectAA(x, y, x + panelW, y + 18, 12.0F,
                (headerA << 24) | themeRgb,
                (headerA << 24) | (GuiModule.getThemeColor(-110) & 0x00FFFFFF),
                new boolean[]{true, true, false, false});

        // Name centered in header.
        boolean f = customFont.isToggled() && FontUtil.hasLoaded();
        String name = trimToFit(getTargetDisplayName(), f, panelW - 24);
        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xFFFFFF;
        if (f) {
            float nw = (float) FontUtil.bold.getStringWidth(name);
            FontUtil.bold.drawSmoothString(name, x + (panelW - nw) / 2.0F, y + 4, nameColor);
        } else {
            int nw = mc.fontRendererObj.getStringWidth(name);
            mc.fontRendererObj.drawString(name, x + (panelW - nw) / 2, y + 5, nameColor, false);
        }

        int headSize = 48;
        int headX = x + 10;
        int headY = y + 22;
        drawHead(headX, headY, headSize);

        int infoX = headX + headSize + 12;
        float maxHP = Math.max(1.0F, target.getMaxHealth());

        // Big HP number.
        String hp = String.format("%.0f / %.0f", displayedHealth, maxHP);
        int hpColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        if (f) FontUtil.bold.drawSmoothString(hp, infoX, y + 26, hpColor);
        else mc.fontRendererObj.drawString(hp, infoX, y + 26, hpColor, false);

        // Hearts row — a dot per 2 HP, theme-tinted for filled, dim for missing.
        int dotsTotal = Math.min(10, (int) Math.ceil(maxHP / 2.0));
        int dotR = 3, dotGap = 3;
        int dotsY = y + 50;
        int filled = (int) Math.round(MathHelper.clamp_float(
                (displayedHealth / maxHP) * dotsTotal, 0.0F, dotsTotal));
        int filledCol = getBarFillColor(fadeAlpha);
        int dimCol = ((int) (fadeAlpha * 70.0F) << 24) | 0xF4F6FA;
        for (int i = 0; i < dotsTotal; i++) {
            int dx = infoX + i * (dotR * 2 + dotGap);
            int col = i < filled ? filledCol : dimCol;
            RenderUtils.drawRoundedRectAA(dx, dotsY, dx + dotR * 2, dotsY + dotR * 2, dotR, col);
        }

        // Bottom subtle subtitle: max health.
        String sub = String.format("max %.0f", maxHP);
        int subColor = ((int) (fadeAlpha * 150.0F) << 24) | 0x9AA0AB;
        if (f) FontUtil.small.drawSmoothString(sub, infoX, y + 62, subColor);
        else mc.fontRendererObj.drawString(sub, infoX, y + 62, subColor, false);
    }

    /** Pill: rounded pill silhouette, circular head on the left, name + HP inline. */
    private void drawPillLayout(int x, int y) {
        final int panelW = 200, panelH = 50;
        float cardR = panelH / 2.0F;
        panelBg(x, y, x + panelW, y + panelH, cardR, 0x101013, 1.0F);
        int outA = (int) (fadeAlpha * 70.0F);
        RenderUtils.drawRoundedOutline(x, y, x + panelW, y + panelH, cardR, 1.0F,
                (outA << 24) | 0xFFFFFF);

        // Head fits inside the left circular end.
        int headSize = panelH - 10;
        int headX = x + 5;
        int headY = y + 5;
        // Ring around the head to emphasise the circular cap.
        int ringA = (int) (fadeAlpha * 120.0F);
        int themeRgb = GuiModule.getThemeColor(0) & 0x00FFFFFF;
        RenderUtils.drawRoundedRectAA(headX - 2, headY - 2,
                headX + headSize + 2, headY + headSize + 2,
                (headSize + 4) / 2.0F, (ringA << 24) | themeRgb);
        drawHead(headX, headY, headSize);

        boolean f = customFont.isToggled() && FontUtil.hasLoaded();
        int textX = headX + headSize + 12;
        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        String name = trimToFit(getTargetDisplayName(), f, panelW - (textX - x) - 16);
        if (f) FontUtil.bold.drawSmoothString(name, textX, y + 14, nameColor);
        else mc.fontRendererObj.drawString(name, textX, y + 14, nameColor, false);

        float maxHP = Math.max(1.0F, target.getMaxHealth());
        String hp = String.format("%.0f / %.0f HP", displayedHealth, maxHP);
        int hpColor = ((int) (fadeAlpha * 180.0F) << 24) | (themeRgb);
        if (f) FontUtil.small.drawSmoothString(hp, textX, y + 28, hpColor);
        else mc.fontRendererObj.drawString(hp, textX, y + 28, hpColor, false);
    }

    /** Bracket: no panel; theme-colored bracket characters frame the name; HP underneath. */
    private void drawBracketLayout(int x, int y) {
        boolean f = customFont.isToggled() && FontUtil.hasLoaded();
        String name = getTargetDisplayName();
        float maxHP = Math.max(1.0F, target.getMaxHealth());
        String hp = String.format("%.0f / %.0f HP", displayedHealth, maxHP);

        int themeRgb = GuiModule.getThemeColor(0) & 0x00FFFFFF;
        int bracketColor = ((int) (fadeAlpha * 255.0F) << 24) | themeRgb;
        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        int hpColor = ((int) (fadeAlpha * 180.0F) << 24) | 0xC8CCD4;

        if (f) {
            float lw = (float) FontUtil.bold.getStringWidth("[ ");
            float nw = (float) FontUtil.bold.getStringWidth(name);
            FontUtil.bold.drawSmoothString("[",   x,                 y + 1, bracketColor);
            FontUtil.bold.drawSmoothString(name,  x + lw,            y + 1, nameColor);
            FontUtil.bold.drawSmoothString("]",   x + lw + nw + 2,   y + 1, bracketColor);
            FontUtil.small.drawSmoothString(hp,   x + 4,             y + 14, hpColor);
        } else {
            int lw = mc.fontRendererObj.getStringWidth("[ ");
            int nw = mc.fontRendererObj.getStringWidth(name);
            mc.fontRendererObj.drawString("[",  x,                y + 1, bracketColor, false);
            mc.fontRendererObj.drawString(name, x + lw,           y + 1, nameColor,    false);
            mc.fontRendererObj.drawString("]",  x + lw + nw + 2,  y + 1, bracketColor, false);
            mc.fontRendererObj.drawString(hp,   x + 4,            y + 14, hpColor,     false);
        }
    }

    /** Compact: tight panel, small head, name + HP only — no bar. */
    private void drawCompactLayout(int x, int y) {
        final int panelW = 140, panelH = 40;
        panelBg(x, y, x + panelW, y + panelH, 9.0F, 0x101013, 1.0F);
        int headSize = 26;
        int headX = x + 6;
        int headY = y + (panelH - headSize) / 2;
        drawHead(headX, headY, headSize);

        boolean f = customFont.isToggled() && FontUtil.hasLoaded();
        int textX = headX + headSize + 6;
        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        String name = trimToFit(getTargetDisplayName(), f, panelW - (textX - x) - 8);
        if (f) FontUtil.bold.drawSmoothString(name, textX, y + 8, nameColor);
        else mc.fontRendererObj.drawString(name, textX, y + 8, nameColor, false);

        float maxHP = Math.max(1.0F, target.getMaxHealth());
        String hp = String.format("%.0f HP", displayedHealth);
        int hpColor = ((int) (fadeAlpha * 200.0F) << 24) | (getBarFillColor(fadeAlpha) & 0x00FFFFFF);
        if (f) FontUtil.small.drawSmoothString(hp, textX, y + 22, hpColor);
        else mc.fontRendererObj.drawString(hp, textX, y + 22, hpColor, false);
    }

    /** Minimal: no panel, just floating name + HP text. */
    private void drawMinimalLayout(int x, int y) {
        boolean f = customFont.isToggled() && FontUtil.hasLoaded();
        String name = getTargetDisplayName();
        float maxHP = Math.max(1.0F, target.getMaxHealth());
        String hp = String.format("%.0f / %.0f HP", displayedHealth, maxHP);

        int nameColor = ((int) (fadeAlpha * 255.0F) << 24) | 0xF4F6FA;
        int hpColor   = ((int) (fadeAlpha * 180.0F) << 24) | (getBarFillColor(fadeAlpha) & 0x00FFFFFF);
        if (f) {
            FontUtil.bold.drawSmoothString(name, x, y + 1, nameColor);
            FontUtil.small.drawSmoothString(hp,  x, y + 14, hpColor);
        } else {
            mc.fontRendererObj.drawString(name, x, y + 1, nameColor, false);
            mc.fontRendererObj.drawString(hp,   x, y + 14, hpColor,  false);
        }
    }

    private int panelHitWidth(HudStyle s, StyleParams p) {
        switch (s) {
            case Card:    return 110;
            case Bar:     return 280;
            case Glass:   return 200;
            case Modern:  return 156;
            case Exhibit: return 240;
            case Pill:    return 200;
            case Bracket: return 200;
            case Minimal: return 150;
            case Compact: return 140;
            default:      return p.panelW;
        }
    }

    private int panelHitHeight(HudStyle s, StyleParams p) {
        switch (s) {
            case Card:    return 132;
            case Bar:     return 26;
            case Glass:   return 60;
            case Modern:  return 50;
            case Exhibit: return 78;
            case Pill:    return 50;
            case Bracket: return 26;
            case Minimal: return 24;
            case Compact: return 40;
            default:      return p.panelH;
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

        RenderUtils.drawRoundedTexturedRect(x, y, size, size, r,
                 8f / 64f,  8f / 64f, 16f / 64f, 16f / 64f, tint);

        RenderUtils.drawRoundedTexturedRect(x, y, size, size, r,
                40f / 64f,  8f / 64f, 48f / 64f, 16f / 64f, tint);

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void handleDragging(int screenW, int screenH, int hudX, int hudY, int hitW, int hitH) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }
        int mx = Mouse.getX() * screenW / mc.displayWidth;
        int my = screenH - Mouse.getY() * screenH / mc.displayHeight - 1;
        boolean hovering = mx >= hudX && mx <= hudX + hitW
                        && my >= hudY && my <= hudY + hitH;

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
