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
import crow.client.utils.Utils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
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
    public static SliderSetting expand;
    public static SliderSetting cornerLength;

    public enum ESPMode { Corner, Box, Health }

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
        this.registerSetting(outline = new TickSetting("Black outline", true));
        this.registerSetting(cornerLength = new SliderSetting("Corner length", 8, 3, 20, 1));
        cornerLength.visibleWhen(() -> espMode != null && espMode.getMode() == ESPMode.Corner);
        this.registerSetting(expand = new SliderSetting("Expand", 0.0D, -0.3D, 2.0D, 0.1D));
        this.registerSetting(showInvis = new TickSetting("Show invis", true));
        this.registerSetting(filterNPCs = new TickSetting("Filter NPCs", true));
        this.registerSetting(redOnDamage = new TickSetting("Red on damage", true));
    }

    @Override
    public void guiUpdate() {
        espColor = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue()).getRGB();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (mc.currentScreen != null) {
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
        if (mc.currentScreen != null || pendingBoxes.isEmpty()) return;
        if (!Utils.Player.isPlayerInGame()) return;

        GlStateManager.pushMatrix();
        mc.entityRenderer.setupOverlayRendering();

        int cLen = (int) cornerLength.getInput();

        for (ScreenBox box : pendingBoxes) {
            int left   = (int) box.minX;
            int top    = (int) box.minY;
            int right  = (int) box.maxX;
            int bottom = (int) box.maxY;
            int boxWidth = Math.max(1, right - left);
            int boxHeight = Math.max(1, bottom - top);
            int shortestSide = Math.min(boxWidth, boxHeight);
            int dynamicLen = Math.max(2, Math.min(cLen, shortestSide / 4));
            int outlineThickness = shortestSide < 18 ? 0 : (shortestSide < 34 ? 1 : 2);

            if (outline.isToggled() && outlineThickness > 0) {
                drawCorners(left, top, right, bottom, dynamicLen, 0xFF000000, outlineThickness);
            }
            drawCornersFill(left, top, right, bottom, dynamicLen, box.color, shortestSide < 16 ? 1 : 2);

            if (box.drawHealth) {
                drawHealthBarSide(left, top, right, bottom, box.healthRatio);
            }
        }

        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
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

        if (outline.isToggled()) {
            Gui.drawRect(barX - 1, top - 1, barX + barWidth + 1, bottom + 1, 0xFF000000);
        }
        Gui.drawRect(barX, top, barX + barWidth, bottom, 0xFF222222);

        int red       = (int) (255 * (1.0F - healthRatio));
        int green     = (int) (255 * healthRatio);
        int fillColor = 0xFF000000 | (red << 16) | (green << 8);
        Gui.drawRect(barX, bottom - filled, barX + barWidth, bottom, fillColor);
    }
}
