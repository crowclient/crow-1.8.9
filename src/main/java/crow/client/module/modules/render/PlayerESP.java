package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.clickgui.crow.ClickGui;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.modules.world.AntiBot;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.RGBSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class PlayerESP extends Module {

    public static ComboSetting espMode;
    public static RGBSetting rgb;
    public static TickSetting rainbow;
    public static TickSetting showInvis;
    public static TickSetting filterNPCs;
    public static TickSetting redOnDamage;
    public static TickSetting healthBar;
    public static TickSetting outline;
    public static TickSetting hideInInventory;
    public static SliderSetting expand;
    public static SliderSetting cornerLength;
    public static SliderSetting outlineThickness;
    public static SliderSetting glowSpread;

    public enum ESPMode { Corner, Box, Health, Glow, Outline }

    private int espColor;

    private static final FloatBuffer MODEL_VIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer   VIEWPORT   = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer RESULT     = BufferUtils.createFloatBuffer(3);

    private static class ScreenBox {
        final float minX, minY, maxX, maxY;
        final int color;
        final float healthRatio;
        final boolean drawHealth;
        ScreenBox(float minX, float minY, float maxX, float maxY, int color, float healthRatio, boolean drawHealth) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
            this.color = color; this.healthRatio = healthRatio; this.drawHealth = drawHealth;
        }
    }
    private final List<ScreenBox> pendingBoxes = new ArrayList<>();

    public PlayerESP() {
        super("PlayerESP", ModuleCategory.render);
        this.registerSetting(espMode = new ComboSetting("Mode", ESPMode.Corner));
        this.registerSetting(rgb = new RGBSetting("RGB", 0, 255, 0));
        this.registerSetting(rainbow = new TickSetting("Rainbow", false));
        this.registerSetting(healthBar = new TickSetting("Health bar", true));
        this.registerSetting(outline = new TickSetting("Outline", true));
        this.registerSetting(cornerLength = new SliderSetting("Corner len", 8, 3, 20, 1));
        cornerLength.visibleWhen(() -> espMode != null && espMode.getMode() == ESPMode.Corner);
        this.registerSetting(outlineThickness = new SliderSetting("Line width", 2.0D, 1.0D, 6.0D, 0.5D));
        outlineThickness.visibleWhen(() -> espMode != null && espMode.getMode() == ESPMode.Outline);
        this.registerSetting(glowSpread = new SliderSetting("Glow spread", 4.0D, 1.0D, 12.0D, 0.5D));
        glowSpread.visibleWhen(() -> espMode != null && espMode.getMode() == ESPMode.Glow);
        this.registerSetting(expand = new SliderSetting("Expand", 0.0D, -0.3D, 2.0D, 0.1D));
        this.registerSetting(showInvis = new TickSetting("Show invis", true));
        this.registerSetting(filterNPCs = new TickSetting("No NPCs", true));
        this.registerSetting(redOnDamage = new TickSetting("Red on hurt", true));
        // Default off — ESP keeps rendering through the inventory unless the
        // user opts in to having it disappear there.
        this.registerSetting(hideInInventory = new TickSetting("Hide in inv", false));
    }

    @Override
    public void guiUpdate() {
        espColor = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue()).getRGB();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        // Only hide when the player explicitly opted in to "Hide in inventory"
        // AND the open screen is an inventory-like container. Chat, the
        // click GUI, and other floating screens leave the ESP visible.
        if (mc.currentScreen != null
                && hideInInventory != null && hideInInventory.isToggled()
                && mc.currentScreen instanceof GuiContainer) {
            pendingBoxes.clear();
            return;
        }
        if (!(fe.getEvent() instanceof RenderWorldLastEvent)) return;
        if (!Utils.Player.isPlayerInGame()) return;

        RenderWorldLastEvent rwle = (RenderWorldLastEvent) fe.getEvent();
        ESPMode mode = (ESPMode) espMode.getMode();

        pendingBoxes.clear();

        if (mode == ESPMode.Box || mode == ESPMode.Health) {
            int type = (mode == ESPMode.Box) ? 1 : 4;
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player == mc.thePlayer || player.deathTime != 0) continue;
                if (!showInvis.isToggled() && player.isInvisible()) continue;
                if (filterNPCs.isToggled() && player.getDisplayNameString().toLowerCase().startsWith("npc")) continue;
                if (AntiBot.renderBot(player)) continue;
                int color = rainbow.isToggled()
                        ? ClickGui.getRainbowAtX((int) player.posX * 50) : (espColor | 0xFF000000);
                if (redOnDamage.isToggled() && player.hurtTime > 0) color = 0xFFFF3333;
                Utils.HUD.drawBoxAroundEntity(player, type, expand.getInput(), 0, color, redOnDamage.isToggled());
            }
            return;
        }

        MODEL_VIEW.rewind(); PROJECTION.rewind(); VIEWPORT.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);

        ScaledResolution sr = new ScaledResolution(mc);
        int scaleFactor = sr.getScaleFactor();

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.deathTime != 0) continue;
            if (!showInvis.isToggled() && player.isInvisible()) continue;
            if (filterNPCs.isToggled() && player.getDisplayNameString().toLowerCase().startsWith("npc")) continue;
            if (AntiBot.renderBot(player)) continue;

            int color = rainbow.isToggled()
                    ? ClickGui.getRainbowAtX((int) player.posX * 50) : (espColor | 0xFF000000);
            if (redOnDamage.isToggled() && player.hurtTime > 0) color = 0xFFFF3333;

            float pt = rwle.partialTicks;
            double rx = player.lastTickPosX + (player.posX - player.lastTickPosX) * pt - mc.getRenderManager().viewerPosX;
            double ry = player.lastTickPosY + (player.posY - player.lastTickPosY) * pt - mc.getRenderManager().viewerPosY;
            double rz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * pt - mc.getRenderManager().viewerPosZ;

            double hw = player.width / 2.0 + expand.getInput() * 0.3;
            double ph = player.height + expand.getInput() * 0.2;

            double[][] corners = {
                    {rx - hw, ry,      rz - hw}, {rx - hw, ry,      rz + hw},
                    {rx + hw, ry,      rz - hw}, {rx + hw, ry,      rz + hw},
                    {rx - hw, ry + ph, rz - hw}, {rx - hw, ry + ph, rz + hw},
                    {rx + hw, ry + ph, rz - hw}, {rx + hw, ry + ph, rz + hw},
            };

            float minX = Float.MAX_VALUE,  minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            boolean anyVisible = false;

            for (double[] c : corners) {

                MODEL_VIEW.rewind(); PROJECTION.rewind(); VIEWPORT.rewind(); RESULT.rewind();
                boolean ok = GLU.gluProject((float) c[0], (float) c[1], (float) c[2],
                        MODEL_VIEW, PROJECTION, VIEWPORT, RESULT);
                if (!ok) continue;

                float sx = RESULT.get(0);
                float sy = VIEWPORT.get(3) - RESULT.get(1);
                float sz = RESULT.get(2);
                if (sz < 0.0f || sz > 1.0f) continue;

                anyVisible = true;
                if (sx < minX) minX = sx;
                if (sx > maxX) maxX = sx;
                if (sy < minY) minY = sy;
                if (sy > maxY) maxY = sy;
            }

            if (!anyVisible) continue;

            minX /= scaleFactor; maxX /= scaleFactor;
            minY /= scaleFactor; maxY /= scaleFactor;

            float hr = Math.max(0, Math.min(1, player.getHealth() / player.getMaxHealth()));
            pendingBoxes.add(new ScreenBox(minX, minY, maxX, maxY, color, hr, healthBar.isToggled()));
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        // Mirror the inventory gate in onForgeEvent — don't render 2D ESP
        // through inventory-like screens when the user opts in.
        if (mc.currentScreen != null
                && hideInInventory != null && hideInInventory.isToggled()
                && mc.currentScreen instanceof GuiContainer) {
            return;
        }
        if (pendingBoxes.isEmpty()) return;
        if (!Utils.Player.isPlayerInGame()) return;

        GlStateManager.pushMatrix();
        mc.entityRenderer.setupOverlayRendering();

        ESPMode mode = (ESPMode) espMode.getMode();
        int cLen = (int) cornerLength.getInput();
        float outlineThick = (float) outlineThickness.getInput();
        float glowSpreadVal = (float) glowSpread.getInput();

        for (ScreenBox box : pendingBoxes) {
            int left   = (int) box.minX;
            int top    = (int) box.minY;
            int right  = (int) box.maxX;
            int bottom = (int) box.maxY;
            int boxWidth = Math.max(1, right - left);
            int boxHeight = Math.max(1, bottom - top);
            int shortestSide = Math.min(boxWidth, boxHeight);

            switch (mode) {
                case Glow:
                    drawGlow(left, top, right, bottom, box.color, glowSpreadVal);
                    break;
                case Outline:
                    drawOutline(left, top, right, bottom, box.color, outlineThick,
                            outline.isToggled());
                    break;
                case Corner:
                default: {
                    int dynamicLen = Math.max(2, Math.min(cLen, shortestSide / 4));
                    int outerThick = shortestSide < 18 ? 0 : (shortestSide < 34 ? 1 : 2);
                    if (outline.isToggled() && outerThick > 0) {
                        drawCorners(left, top, right, bottom, dynamicLen, 0xFF000000, outerThick);
                    }
                    drawCornersFill(left, top, right, bottom, dynamicLen, box.color,
                            shortestSide < 16 ? 1 : 2);
                    break;
                }
            }

            if (box.drawHealth) {
                drawHealthBarSide(left, top, right, bottom, box.healthRatio);
            }
        }

        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Soft glow halo around the entity's screen rect — layered rounded
     * rectangles at expanding radii with falling alpha. Visually distinct
     * from Outline and Corner: no hard edge, just a haze of theme color.
     */
    private void drawGlow(int left, int top, int right, int bottom, int color, float spread) {
        int rgb = color & 0x00FFFFFF;
        // Soft halo: 6 concentric rounded rects spreading outward.
        int layers = 6;
        for (int i = layers; i >= 1; i--) {
            float pad = (i / (float) layers) * spread * 2.0F;
            float r = 4.0F + pad * 0.6F;
            // Quadratic alpha fall-off so the outer layers are faint and
            // the inner glow has more presence.
            float falloff = 1.0F - (i / (float) layers);
            int a = Math.max(0, Math.min(255, (int) (95 * falloff * falloff)));
            if (a <= 0) continue;
            RenderUtils.drawRoundedRectAA(left - pad, top - pad, right + pad, bottom + pad, r,
                    (a << 24) | rgb);
        }
        // Bright core outline so the glow has a defined center.
        RenderUtils.drawRoundedOutline(left, top, right, bottom, 2.5F, 1.0F,
                0xCC000000 | rgb);
    }

    /**
     * Clean solid-colored rectangular outline. Black backing stroke is
     * drawn when the "Black outline" tick is on so the line stays legible
     * against bright skins / terrain.
     */
    private void drawOutline(int left, int top, int right, int bottom, int color,
                              float thickness, boolean blackOutline) {
        if (blackOutline) {
            RenderUtils.drawRoundedOutline(left - 1, top - 1, right + 1, bottom + 1,
                    3.0F, thickness + 2.0F, 0xFF000000);
        }
        RenderUtils.drawRoundedOutline(left, top, right, bottom, 2.5F, thickness,
                0xFF000000 | (color & 0x00FFFFFF));
    }

    private void drawCornersFill(int left, int top, int right, int bottom, int len, int color, int thickness) {
        Gui.drawRect(left,             top,              left + len,         top + thickness,      color);
        Gui.drawRect(left,             top,              left + thickness,   top + len,            color);
        Gui.drawRect(right - len,      top,              right,              top + thickness,      color);
        Gui.drawRect(right - thickness, top,             right,              top + len,            color);
        Gui.drawRect(left,             bottom - thickness, left + len,       bottom,               color);
        Gui.drawRect(left,             bottom - len,     left + thickness,   bottom,               color);
        Gui.drawRect(right - len,      bottom - thickness, right,            bottom,               color);
        Gui.drawRect(right - thickness, bottom - len,    right,              bottom,               color);
    }

    private void drawCorners(int left, int top, int right, int bottom, int len, int color, int t) {
        Gui.drawRect(left - t,        top - t,        left + len + t,  top + 1 + t,       color);
        Gui.drawRect(left - t,        top - t,        left + 1 + t,    top + len + t,     color);
        Gui.drawRect(right - len - t, top - t,        right + t,       top + 1 + t,       color);
        Gui.drawRect(right - 1 - t,   top - t,        right + t,       top + len + t,     color);
        Gui.drawRect(left - t,        bottom - 1 - t, left + len + t,  bottom + t,        color);
        Gui.drawRect(left - t,        bottom - len - t, left + 1 + t,  bottom + t,        color);
        Gui.drawRect(right - len - t, bottom - 1 - t, right + t,       bottom + t,        color);
        Gui.drawRect(right - 1 - t,   bottom - len - t, right + t,     bottom + t,        color);
    }

    private void drawHealthBarSide(int left, int top, int right, int bottom, float healthRatio) {
        int barWidth  = 2;
        int barX      = left - barWidth - 3;
        int barHeight = bottom - top;
        int filled    = (int) (barHeight * healthRatio);

        float r = barWidth / 2.0F;
        if (outline.isToggled()) {
            RenderUtils.drawRoundedRectAA(barX - 1, top - 1, barX + barWidth + 1, bottom + 1, r + 1, 0xFF000000);
        }
        RenderUtils.drawRoundedRectAA(barX, top, barX + barWidth, bottom, r, 0xC0161920);

        int red       = (int) (255 * (1.0F - healthRatio));
        int green     = (int) (255 * healthRatio);
        int fillColor = 0xFF000000 | (red << 16) | (green << 8);
        if (filled > 0) {
            RenderUtils.drawRoundedRectAA(barX, bottom - Math.max(filled, barWidth), barX + barWidth, bottom, r, fillColor);
        }
    }
}
