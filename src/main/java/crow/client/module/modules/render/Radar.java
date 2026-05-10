package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.modules.HUD;
import crow.client.module.modules.client.Targets;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import crow.client.utils.font.FontUtil;

public class Radar extends Module {
    private final SliderSetting distance;
    private final SliderSetting posX;
    private final SliderSetting posY;
    private final SliderSetting size;
    private final SliderSetting arrowScale;
    private final TickSetting showProjectiles;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    private static final ResourceLocation PLAYER_ARROW = new ResourceLocation("crow", "icons/playerarrow.png");

    public Radar() {
        super("Radar", ModuleCategory.render);
        this.registerSetting(distance = new SliderSetting("Distance", 32, 8, 96, 1));
        this.registerSetting(posX = new SliderSetting("X", 10, 0, 4000, 1));
        this.registerSetting(posY = new SliderSetting("Y", 40, 0, 2000, 1));
        this.registerSetting(size = new SliderSetting("Size", 74, 50, 120, 1));
        this.registerSetting(arrowScale = new SliderSetting("Arrow Scale", 8, 2, 16, 0.5));
        this.registerSetting(showProjectiles = new TickSetting("Show Projectiles", true));
    }

    @Subscribe
    public void render2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;

        int x = (int) posX.getInput();
        int y = (int) posY.getInput();
        int panelSize = (int) size.getInput();
        float corner = 6.0F;
        float centerX = x + panelSize / 2.0F;
        float centerY = y + panelSize / 2.0F;
        float radarRadius = panelSize / 2.0F - 6.0F;

        handleDragging(x, y, x + panelSize, y + panelSize);

        if (HUD.enableBlur != null && HUD.enableBlur.isToggled()) {
            GUIBlurUtil.drawBlurredBackground(x, y, panelSize, panelSize,
                    (int) HUD.blurRadius.getInput(), 6, 0.6f);
            mc.entityRenderer.setupOverlayRendering();
        }

        RenderUtils.drawRoundedRectAA(x, y, x + panelSize, y + panelSize, corner, 0xD814171D);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.08F);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(centerX, y + 4);
        GL11.glVertex2f(centerX, y + panelSize - 4);
        GL11.glVertex2f(x + 4, centerY);
        GL11.glVertex2f(x + panelSize - 4, centerY);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();

        float playerYaw = mc.thePlayer.rotationYaw;
        float compassRadius = radarRadius - 2.0F;

        String[] directions = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        for (int i = 0; i < 8; i++) {
            double dirAngle = i * 45.0;
            double relAngle = Math.toRadians(dirAngle - playerYaw);

            boolean isCardinal = (i % 2 == 0);
            float labelRadius = isCardinal ? compassRadius : compassRadius - 1.0F;
            float lx = centerX + (float) Math.sin(relAngle) * labelRadius;

            float ly = centerY - (float) Math.cos(relAngle) * labelRadius;

            String label = directions[i];
            if (isCardinal) {
                float strW = (float) FontUtil.bold.getStringWidth(label);
                float drawX = lx - strW / 2.0F;
                float drawY = ly - 3.0F;
                FontUtil.bold.drawSmoothString(label, drawX, drawY, 0xAAFFFFFF);
            } else {
                float strW = (float) FontUtil.small.getStringWidth(label);
                FontUtil.small.drawSmoothString(label, lx - strW / 2.0F, ly - 3.0F, 0x66FFFFFF);
            }
        }

        drawPlayerArrow(centerX, centerY, (float) arrowScale.getInput());

        for (Entity en : mc.theWorld.loadedEntityList) {
            if (en == mc.thePlayer) continue;

            double dist = mc.thePlayer.getDistanceToEntity(en);
            if (dist > distance.getInput()) continue;

            int dotColor;
            boolean isProjectile = false;

            if (en instanceof EntityPlayer) {
                EntityPlayer ep = (EntityPlayer) en;
                if (!Targets.isTargetingPlayers()) continue;
                if (crow.client.module.modules.world.AntiBot.renderBot(ep)) continue;
                if (Targets.isAFriend(ep)) {
                    dotColor = 0xFF44FF44;
                } else if (Targets.isATeamMate(ep)) {
                    dotColor = 0xFF55A0FF;
                } else {
                    dotColor = 0xFFFF5A5A;
                }
            } else if (en instanceof IMob) {
                if (!Targets.isTargetingMobs()) continue;
                dotColor = 0xFFFF9944;
            } else if (showProjectiles.isToggled() && (en instanceof EntityFireball || en instanceof EntityWitherSkull)) {
                dotColor = 0xFFFF4444;
                isProjectile = true;
            } else if (showProjectiles.isToggled() && en instanceof EntityArrow) {
                dotColor = 0xFFFFFF44;
                isProjectile = true;
            } else if (showProjectiles.isToggled() && en instanceof EntityTNTPrimed) {
                dotColor = 0xFFFF6622;
                isProjectile = true;
            } else {
                continue;
            }

            double dx = en.posX - mc.thePlayer.posX;
            double dz = en.posZ - mc.thePlayer.posZ;

            double yawRad = Math.toRadians(playerYaw);
            double rotX = -(dx * Math.cos(yawRad) + dz * Math.sin(yawRad));
            double rotZ = -dx * Math.sin(yawRad) + dz * Math.cos(yawRad);

            float dotX = centerX + (float) rotX / (float) distance.getInput() * radarRadius;
            float dotY = centerY - (float) rotZ / (float) distance.getInput() * radarRadius;

            dotX = Math.max(x + 4, Math.min(x + panelSize - 4, dotX));
            dotY = Math.max(y + 4, Math.min(y + panelSize - 4, dotY));

            GL11.glDisable(GL11.GL_DEPTH_TEST);

            if (isProjectile) {

                float s = 2.0F;
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                float r = ((dotColor >> 16) & 0xFF) / 255.0F;
                float g = ((dotColor >> 8) & 0xFF) / 255.0F;
                float b = (dotColor & 0xFF) / 255.0F;
                float a = ((dotColor >> 24) & 0xFF) / 255.0F;
                GL11.glColor4f(r, g, b, a);
                GL11.glBegin(GL11.GL_POLYGON);
                GL11.glVertex2f(dotX, dotY - s);
                GL11.glVertex2f(dotX + s, dotY);
                GL11.glVertex2f(dotX, dotY + s);
                GL11.glVertex2f(dotX - s, dotY);
                GL11.glEnd();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } else {
                RenderUtils.drawRoundedRectAA(dotX - 1.5F, dotY - 1.5F, dotX + 1.5F, dotY + 1.5F, 1.5F, dotColor);
            }

            String distLabel = String.valueOf((int) dist);
            float distLabelW = (float) FontUtil.small.getStringWidth(distLabel);
            FontUtil.small.drawSmoothString(distLabel, dotX - distLabelW / 2.0F, dotY + 3.0F, 0x99FFFFFF);

            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
    }

    private void drawPlayerArrow(float cx, float cy, float size) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0);

        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        mc.getTextureManager().bindTexture(PLAYER_ARROW);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        int drawX = (int) -size;
        int drawY = (int) -size;
        int drawSize = (int) (size * 2);

        net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(drawX, drawY, 0, 0, drawSize, drawSize, drawSize, drawSize);

        GL11.glPopMatrix();
    }

    private void handleDragging(int left, int top, int right, int bottom) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }

        int mx = Mouse.getX() * mc.currentScreen.width / mc.displayWidth;
        int my = mc.currentScreen.height - Mouse.getY() * mc.currentScreen.height / mc.displayHeight - 1;
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
