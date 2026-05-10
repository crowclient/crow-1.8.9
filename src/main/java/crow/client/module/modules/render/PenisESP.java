package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.module.Module;
import crow.client.module.modules.combat.AimAssist;
import crow.client.module.modules.world.AntiBot;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.RGBSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class PenisESP extends Module {

    private final ComboSetting colorMode;
    private final RGBSetting   rgb;
    private final SliderSetting size;
    private final SliderSetting thickness;
    private final SliderSetting renderDist;
    private final TickSetting   targetPlayers;
    private final TickSetting   targetMobs;
    private final TickSetting   skipFriends;
    private final TickSetting   pulseFx;
    private final TickSetting   spinFx;

    public enum ColorMode { STATIC, RAINBOW, PULSE_PINK }

    private static final FloatBuffer MAT_BUF = BufferUtils.createFloatBuffer(16);

    private final List<EntityLivingBase> targets = new ArrayList<>();

    private static final int    SEG      = 12;
    private static final double TWO_PI   = Math.PI * 2.0;

    private float spinAngle    = 0f;
    private long  lastFrameMs  = 0L;

    public PenisESP() {
        super("PenisESP", ModuleCategory.render);
        this.registerSetting(colorMode   = new ComboSetting("Color",        ColorMode.PULSE_PINK));
        this.registerSetting(rgb         = new RGBSetting("Static RGB",     255, 105, 180));
        this.registerSetting(size        = new SliderSetting("Size",         0.5,  0.1,  2.0,  0.1));
        this.registerSetting(thickness   = new SliderSetting("Thickness",    1.5,  0.5,  4.0,  0.5));
        this.registerSetting(renderDist  = new SliderSetting("Render dist",  32,   8,    128,  4));
        this.registerSetting(targetPlayers = new TickSetting("Players",      true));
        this.registerSetting(targetMobs  = new TickSetting("Mobs",           false));
        this.registerSetting(skipFriends = new TickSetting("Skip friends",   true));
        this.registerSetting(pulseFx     = new TickSetting("Pulse",          true));
        this.registerSetting(spinFx      = new TickSetting("Spin",           false));
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (mc.currentScreen != null) return;
        if (!(fe.getEvent() instanceof RenderWorldLastEvent)) return;
        if (!Utils.Player.isPlayerInGame()) return;

        final RenderWorldLastEvent rwle = (RenderWorldLastEvent) fe.getEvent();
        final float pt  = rwle.partialTicks;
        final long  now = System.currentTimeMillis();

        if (lastFrameMs > 0L) {
            spinAngle = (spinAngle + 80f * ((now - lastFrameMs) / 1000f)) % 360f;
        }
        lastFrameMs = now;

        final float[] col = resolveColor((ColorMode) colorMode.getMode(), now);

        float scale = (float) size.getInput();
        if (pulseFx.isToggled()) {
            scale *= 1f + 0.08f * (float) Math.sin(now / 420.0);
        }

        final double maxDistSq = renderDist.getInput() * renderDist.getInput();
        final double camX = mc.getRenderManager().viewerPosX;
        final double camY = mc.getRenderManager().viewerPosY;
        final double camZ = mc.getRenderManager().viewerPosZ;

        targets.clear();

        if (targetPlayers.isToggled()) {
            for (EntityPlayer p : mc.theWorld.playerEntities) {
                if (p == mc.thePlayer || p.deathTime != 0) continue;
                if (AntiBot.bot(p)) continue;
                if (skipFriends.isToggled() && AimAssist.getFriends().contains(p)) continue;
                if (p.getDistanceSqToEntity(mc.thePlayer) > maxDistSq) continue;
                targets.add(p);
            }
        }
        if (targetMobs.isToggled()) {
            for (Entity e : mc.theWorld.loadedEntityList) {
                if (!(e instanceof EntityLivingBase) || e instanceof EntityPlayer) continue;
                final EntityLivingBase mob = (EntityLivingBase) e;
                if (mob.deathTime != 0) continue;
                if (mob.getDistanceSqToEntity(mc.thePlayer) > maxDistSq) continue;
                targets.add(mob);
            }
        }

        if (targets.isEmpty()) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        crow.client.render.aa.AABlend.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        final float lw = (float) thickness.getInput();

        for (EntityLivingBase target : targets) {

            final double ix = target.lastTickPosX + (target.posX - target.lastTickPosX) * pt - camX;
            final double iy = target.lastTickPosY + (target.posY - target.lastTickPosY) * pt - camY;
            final double iz = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * pt - camZ;

            final double torsoY = iy + target.height * 0.55;

            GL11.glPushMatrix();
            GL11.glTranslated(ix, torsoY, iz);

            MAT_BUF.rewind();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MAT_BUF);
            final float[] m = new float[16];
            MAT_BUF.rewind();
            MAT_BUF.get(m);
            m[0] = 1; m[1] = 0; m[2] = 0;
            m[4] = 0; m[5] = 1; m[6] = 0;
            m[8] = 0; m[9] = 0; m[10] = 1;
            MAT_BUF.rewind();
            MAT_BUF.put(m);
            MAT_BUF.rewind();
            GL11.glLoadMatrix(MAT_BUF);

            if (spinFx.isToggled()) {
                GL11.glRotatef(spinAngle, 0f, 0f, 1f);
            }

            GL11.glScalef(scale, scale, scale);

            GL11.glLineWidth(lw);
            GL11.glColor4f(col[0], col[1], col[2], 1.0f);
            drawShape();

            GL11.glPopMatrix();
        }

        GL11.glDepthMask(true);
        GL11.glPopAttrib();
    }

    private void drawShape() {

        drawBall(-0.32, -0.68, 0.0, 0.22);
        drawBall( 0.32, -0.68, 0.0, 0.22);

        final double shR  = 0.20;
        final double[] shY = { -0.45, -0.25, -0.05, 0.12, 0.25 };
        for (double y : shY) {
            ring(shR, y);
        }

        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < SEG; i++) {
            final double a = (i / (double) SEG) * TWO_PI;
            final double x = Math.cos(a) * shR;
            final double z = Math.sin(a) * shR;
            GL11.glVertex3d(x, shY[0],                z);
            GL11.glVertex3d(x, shY[shY.length - 1],   z);
        }
        GL11.glEnd();

        ring(0.28, 0.30);

        final double[] gR = { 0.28, 0.265, 0.235, 0.19, 0.130, 0.060, 0.015 };
        final double[] gY = { 0.30,  0.37,  0.45,  0.52,  0.59,  0.66,  0.74 };

        for (int i = 0; i < gR.length; i++) {
            if (gR[i] > 0.02) ring(gR[i], gY[i]);
        }

        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < SEG; i++) {
            final double a = (i / (double) SEG) * TWO_PI;
            final double cx = Math.cos(a);
            final double cz = Math.sin(a);
            GL11.glVertex3d(cx * gR[0], gY[0], cz * gR[0]);
            GL11.glVertex3d(cx * gR[5], gY[5], cz * gR[5]);
        }
        GL11.glEnd();
    }

    private void ring(double radius, double y) {
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < SEG; i++) {
            final double a = (i / (double) SEG) * TWO_PI;
            GL11.glVertex3d(Math.cos(a) * radius, y, Math.sin(a) * radius);
        }
        GL11.glEnd();
    }

    private void drawBall(double cx, double cy, double cz, double radius) {

        final int LAT = 4;
        for (int r = 0; r < LAT; r++) {
            final double lat    = Math.PI * (r / (double)(LAT - 1)) - Math.PI * 0.5;
            final double ringR  = Math.cos(lat) * radius;
            final double ringY  = cy + Math.sin(lat) * radius;
            if (ringR < 0.01) continue;
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < SEG; i++) {
                final double a = (i / (double) SEG) * TWO_PI;
                GL11.glVertex3d(cx + Math.cos(a) * ringR, ringY, cz + Math.sin(a) * ringR);
            }
            GL11.glEnd();
        }

        final int LONG = 6;
        for (int i = 0; i < LONG; i++) {
            final double longAngle = (i / (double) LONG) * Math.PI;
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int j = 0; j <= 10; j++) {
                final double lat = Math.PI * (j / 10.0) - Math.PI * 0.5;
                GL11.glVertex3d(
                    cx + Math.cos(lat) * radius * Math.cos(longAngle),
                    cy + Math.sin(lat) * radius,
                    cz + Math.cos(lat) * radius * Math.sin(longAngle)
                );
            }
            GL11.glEnd();
        }
    }

    private float[] resolveColor(ColorMode mode, long now) {
        switch (mode) {
            case RAINBOW: {
                final Color c = new Color(Utils.Client.rainbowDraw(2L, 0L));
                return new float[]{ c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f };
            }
            case PULSE_PINK: {

                final float t  = (float)((Math.sin(now / 700.0) + 1.0) * 0.5);
                final float r  = 0.90f + 0.10f * t;
                final float g  = 0.18f + 0.22f * t;
                final float b  = 0.45f + 0.30f * t;
                return new float[]{ r, g, b };
            }
            default: {
                return new float[]{
                    rgb.getRed()   / 255f,
                    rgb.getGreen() / 255f,
                    rgb.getBlue()  / 255f
                };
            }
        }
    }
}
