package crow.client.module.modules.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Backtrack — hit players at their previous positions inside the
 * server's natural lag-compensation window.
 *
 * <p>This module never modifies outgoing packets. Holding C03 against
 * modern anti-cheats (Grim, Vulcan) flags BadPacketsT because those
 * systems run their own physics simulation on the server and notice
 * the divergence the moment we delay anything. Instead we just
 * surface the entity's recent history visually, expand the mouseover
 * raycast, and let the server's own lag-comp resolve the hit at the
 * matching past position.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>Manual</b> — renders N translucent shadows of the entity's
 *       past positions and lets you click any of them. Aim modules
 *       target the shadow at the configured "Aim depth" so silent aim
 *       lines up with what's drawn.</li>
 *   <li><b>LagBased</b> — renders the entity's <i>model</i> at the
 *       past position the lag-comp window will accept a hit at (the
 *       "hittable" position) and a wireframe outline at the entity's
 *       <i>current</i> position (where the server sees them right now).
 *       Aim modules target the hittable position. Functionally the
 *       same lag-comp hit, but with one cleaner shadow plus an explicit
 *       "they're really here" indicator.</li>
 * </ul>
 */
public class Backtrack extends Module {

    public enum Mode { Manual, LagBased }
    public enum ShadowColor { Theme, Red, Green, Blue, Yellow, Purple, White }

    public static ComboSetting  modeSetting;
    public static SliderSetting renderTicks;
    public static SliderSetting aimDepth;
    public static SliderSetting maxAgeMs;
    public static TickSetting   expandHitbox;
    public static TickSetting   renderShadows;
    public static SliderSetting lagMs;
    public static TickSetting   renderServerPos;
    public static ComboSetting  colorSetting;
    public static SliderSetting maxDistance;
    public static TickSetting   playersOnly;
    public static TickSetting   weaponOnly;

    /** Per-entity ring buffer of historical tick snapshots, newest-first. */
    private final Map<Integer, Deque<TickSnapshot>> history = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY_PER_ENTITY = 20;

    public Backtrack() {
        super("Backtrack", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting(
                "Aim at past positions within the server's lag-comp window. No packet manipulation."));
        this.registerSetting(modeSetting    = new ComboSetting<>("Mode", Mode.Manual));

        // Manual-mode knobs ------------------------------------------------
        this.registerSetting(renderShadows  = new TickSetting("Render shadows", true));
        this.registerSetting(renderTicks    = new SliderSetting("Render ticks", 3, 1, 6, 1));
        this.registerSetting(aimDepth       = new SliderSetting("Aim depth",    2, 1, 6, 1));
        this.registerSetting(maxAgeMs       = new SliderSetting("Max age (ms)", 180, 50, 350, 10));
        this.registerSetting(expandHitbox   = new TickSetting("Click any shadow", true));

        // LagBased-mode knobs ----------------------------------------------
        this.registerSetting(lagMs           = new SliderSetting("Lag window (ms)", 150, 50, 300, 10));
        this.registerSetting(renderServerPos = new TickSetting("Server-pos box", true));

        // Shared knobs -----------------------------------------------------
        this.registerSetting(colorSetting   = new ComboSetting<>("Color", ShadowColor.Theme));
        this.registerSetting(maxDistance    = new SliderSetting("Max dist",  5.0D, 1.0D, 6.0D, 0.1D));
        this.registerSetting(playersOnly    = new TickSetting("Players only", true));
        this.registerSetting(weaponOnly     = new TickSetting("Weapon only",  false));

        // Visibility — Manual hides LagBased knobs and vice versa.
        renderShadows.visibleWhen(   () -> getMode() == Mode.Manual);
        renderTicks.visibleWhen(     () -> getMode() == Mode.Manual);
        aimDepth.visibleWhen(        () -> getMode() == Mode.Manual);
        maxAgeMs.visibleWhen(        () -> getMode() == Mode.Manual);
        expandHitbox.visibleWhen(    () -> getMode() == Mode.Manual);
        lagMs.visibleWhen(           () -> getMode() == Mode.LagBased);
        renderServerPos.visibleWhen( () -> getMode() == Mode.LagBased);
    }

    @Override
    public void onEnable() {
        history.clear();
    }

    @Override
    public void onDisable() {
        history.clear();
    }

    @SuppressWarnings("unchecked")
    private static Mode getMode() {
        return modeSetting == null ? Mode.Manual : (Mode) modeSetting.getMode();
    }

    @Override
    public String getHudSuffix() {
        if (getMode() == Mode.LagBased) {
            return ((int) lagMs.getInput()) + "ms";
        }
        return ((int) renderTicks.getInput()) + "t";
    }

    /* ====================================================================== */
    /* Tick — record snapshots                                                */
    /* ====================================================================== */

    @Subscribe
    public void onTick(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof TickEvent.ClientTickEvent)) return;
        TickEvent.ClientTickEvent cte = (TickEvent.ClientTickEvent) fe.getEvent();
        if (cte.phase != TickEvent.Phase.END) return;
        if (!Utils.Player.isPlayerInGame() || mc.theWorld == null) {
            history.clear();
            return;
        }

        long now = System.currentTimeMillis();
        double range = maxDistance.getInput();
        double rangeSq = (range + 1.0) * (range + 1.0);

        for (Entity ent : new ArrayList<>(mc.theWorld.loadedEntityList)) {
            if (ent == null || ent == mc.thePlayer) continue;
            if (!(ent instanceof EntityLivingBase) || !((EntityLivingBase) ent).isEntityAlive()) continue;
            if (playersOnly.isToggled() && !(ent instanceof EntityPlayer)) continue;
            if (mc.thePlayer.getDistanceSqToEntity(ent) > rangeSq) {
                history.remove(ent.getEntityId());
                continue;
            }
            Deque<TickSnapshot> buf = history.computeIfAbsent(ent.getEntityId(),
                    id -> new ArrayDeque<>());
            buf.addFirst(new TickSnapshot(ent.posX, ent.posY, ent.posZ,
                    ent.rotationYaw, ent.rotationPitch, now));
            while (buf.size() > MAX_HISTORY_PER_ENTITY) {
                buf.removeLast();
            }
        }
    }

    /* ====================================================================== */
    /* Aim / hitbox surface                                                   */
    /* ====================================================================== */

    public static double[] getAimTarget(Entity entity) {
        if (entity == null || entity == mc.thePlayer) return null;
        if (!(entity instanceof EntityLivingBase) || !((EntityLivingBase) entity).isEntityAlive()) return null;

        Backtrack inst = (Backtrack) Crow.moduleManager.getModuleByClazz(Backtrack.class);
        if (inst == null || !inst.isEnabled()) return null;
        if (playersOnly.isToggled() && !(entity instanceof EntityPlayer)) return null;
        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return null;
        if (mc.thePlayer == null) return null;

        double range = maxDistance.getInput();
        if (mc.thePlayer.getDistanceSqToEntity(entity) > (range + 1.0) * (range + 1.0)) return null;

        Deque<TickSnapshot> buf = inst.history.get(entity.getEntityId());
        if (buf == null || buf.isEmpty()) return null;

        TickSnapshot s;
        if (getMode() == Mode.LagBased) {
            s = pickHittableSnapshot(buf);
        } else {
            // Only aim as far back as the click surface actually reaches.
            // unionWithPredicted expands the hitbox over `renderTicks`
            // snapshots and only when "Click any shadow" is on; aiming past
            // that points the rotation at a shadow the mouseover raycast
            // never covers, so the click resolves to nothing at all.
            if (!expandHitbox.isToggled()) return null;
            int depth = Math.min((int) aimDepth.getInput(), (int) renderTicks.getInput());
            s = pickManualSnapshot(buf, depth, (long) maxAgeMs.getInput());
        }
        if (s == null) return null;
        return new double[] { s.x, s.y, s.z };
    }

    public static AxisAlignedBB unionWithPredicted(Entity entity, AxisAlignedBB current, float partialTicks) {
        if (entity == null || entity == mc.thePlayer) return current;
        if (!(entity instanceof EntityLivingBase) || !((EntityLivingBase) entity).isEntityAlive()) return current;

        Backtrack inst = (Backtrack) Crow.moduleManager.getModuleByClazz(Backtrack.class);
        if (inst == null || !inst.isEnabled()) return current;
        if (playersOnly.isToggled() && !(entity instanceof EntityPlayer)) return current;
        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return current;
        if (mc.thePlayer == null) return current;

        double range = maxDistance.getInput();
        if (mc.thePlayer.getDistanceSqToEntity(entity) > (range + 1.0) * (range + 1.0)) return current;

        Deque<TickSnapshot> buf = inst.history.get(entity.getEntityId());
        if (buf == null || buf.isEmpty()) return current;

        float halfW = entity.width / 2.0F;

        if (getMode() == Mode.LagBased) {
            // LagBased unions the hittable (past) position so a click
            // there counts as a click on the entity. The server-pos box
            // is purely visual — never part of the hit surface.
            TickSnapshot s = pickHittableSnapshot(buf);
            if (s == null) return current;
            return current.union(new AxisAlignedBB(
                    s.x - halfW, s.y, s.z - halfW,
                    s.x + halfW, s.y + entity.height, s.z + halfW));
        }

        // Manual mode — gated by the explicit toggle.
        if (!expandHitbox.isToggled()) return current;
        long now = System.currentTimeMillis();
        long maxAge = (long) maxAgeMs.getInput();
        int cap = (int) renderTicks.getInput();

        AxisAlignedBB out = current;
        int n = 0;
        for (TickSnapshot s : buf) {
            if (n++ >= cap) break;
            if (now - s.timestamp > maxAge) break;
            out = out.union(new AxisAlignedBB(
                    s.x - halfW, s.y, s.z - halfW,
                    s.x + halfW, s.y + entity.height, s.z + halfW));
        }
        return out;
    }

    private static TickSnapshot pickManualSnapshot(Deque<TickSnapshot> buf, int depth, long maxAge) {
        long now = System.currentTimeMillis();
        int n = 0;
        TickSnapshot best = null;
        for (TickSnapshot s : buf) {
            if (now - s.timestamp > maxAge) break;
            n++;
            best = s;
            if (n >= depth) break;
        }
        return best;
    }

    /** Snapshot closest to (now − lagMs) — where lag-comp will accept the hit. */
    private static TickSnapshot pickHittableSnapshot(Deque<TickSnapshot> buf) {
        if (buf == null || buf.isEmpty()) return null;
        long now = System.currentTimeMillis();
        long target = now - (long) lagMs.getInput();
        TickSnapshot best = null;
        long bestDiff = Long.MAX_VALUE;
        for (TickSnapshot s : buf) {
            long d = Math.abs(s.timestamp - target);
            if (d < bestDiff) {
                bestDiff = d;
                best = s;
            }
        }
        return best;
    }

    /* ====================================================================== */
    /* Rendering                                                              */
    /* ====================================================================== */

    @Subscribe
    public void onRenderWorld(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof RenderWorldLastEvent)) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        Mode mode = getMode();
        boolean drawManual = mode == Mode.Manual && renderShadows.isToggled();
        boolean drawLag    = mode == Mode.LagBased;
        if (!drawManual && !drawLag) return;
        // Match the aim/hitbox gate — getAimTarget and unionWithPredicted both
        // bail on weaponOnly, so drawing here anyway would show shadows that
        // can't be clicked or aimed at.
        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return;

        RenderWorldLastEvent rwle = (RenderWorldLastEvent) fe.getEvent();
        float partialTicks = rwle.partialTicks;

        double viewX = mc.getRenderManager().viewerPosX;
        double viewY = mc.getRenderManager().viewerPosY;
        double viewZ = mc.getRenderManager().viewerPosZ;

        int color = resolveShadowColor();
        float cr = ((color >> 16) & 0xFF) / 255.0F;
        float cg = ((color >>  8) & 0xFF) / 255.0F;
        float cb = ( color        & 0xFF) / 255.0F;

        long now = System.currentTimeMillis();
        long maxAge = (long) maxAgeMs.getInput();
        int cap = (int) renderTicks.getInput();

        for (Entity entity : new ArrayList<>(mc.theWorld.loadedEntityList)) {
            if (entity == null || entity == mc.thePlayer) continue;
            if (!(entity instanceof EntityLivingBase) || !((EntityLivingBase) entity).isEntityAlive()) continue;
            if (playersOnly.isToggled() && !(entity instanceof EntityPlayer)) continue;

            Deque<TickSnapshot> buf = history.get(entity.getEntityId());
            if (buf == null || buf.isEmpty()) continue;

            if (drawManual) {
                int n = 0;
                int total = Math.min(cap, buf.size());
                for (TickSnapshot s : buf) {
                    if (n >= total) break;
                    if (now - s.timestamp > maxAge) break;
                    float fade = 1.0F - (n / (float) Math.max(1, total));
                    float overallAlpha = Math.max(0.30F, 0.85F * fade);
                    drawShadowFigure(entity, s, partialTicks,
                            viewX, viewY, viewZ, cr, cg, cb, overallAlpha, true);
                    n++;
                }
            }

            if (drawLag) {
                // Solid model at the hittable (past) position — this is
                // where lag-comp will register the hit, so this is where
                // we want the user's cursor pointed.
                TickSnapshot hittable = pickHittableSnapshot(buf);
                if (hittable != null) {
                    drawShadowFigure(entity, hittable, partialTicks,
                            viewX, viewY, viewZ, cr, cg, cb, 0.92F, true);
                }
                // Wireframe-only box at the entity's current (server)
                // position — visual "they're actually here right now",
                // not part of the click surface.
                if (renderServerPos.isToggled()) {
                    drawCurrentPositionBox(entity, partialTicks,
                            viewX, viewY, viewZ, cr, cg, cb);
                }
            }
        }

        crow.client.utils.RenderUtils.syncAllGlState();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Per-shadow render — entity rendered at a past pose, plus an
     *  optional outline / fill box for click-target affordance. */
    private void drawShadowFigure(Entity entity, TickSnapshot s, float partialTicks,
                                  double viewX, double viewY, double viewZ,
                                  float r, float g, float b, float alpha,
                                  boolean drawBox) {
        double origX = entity.posX, origY = entity.posY, origZ = entity.posZ;
        double origPX = entity.prevPosX, origPY = entity.prevPosY, origPZ = entity.prevPosZ;
        double origLX = entity.lastTickPosX, origLY = entity.lastTickPosY, origLZ = entity.lastTickPosZ;
        float origYaw = entity.rotationYaw, origPitch = entity.rotationPitch;
        float origPYaw = entity.prevRotationYaw, origPPitch = entity.prevRotationPitch;
        float origYawHead = entity instanceof EntityLivingBase
                ? ((EntityLivingBase) entity).rotationYawHead : 0F;
        float origPYawHead = entity instanceof EntityLivingBase
                ? ((EntityLivingBase) entity).prevRotationYawHead : 0F;

        entity.posX = entity.lastTickPosX = entity.prevPosX = s.x;
        entity.posY = entity.lastTickPosY = entity.prevPosY = s.y;
        entity.posZ = entity.lastTickPosZ = entity.prevPosZ = s.z;
        entity.rotationYaw = entity.prevRotationYaw = s.yaw;
        entity.rotationPitch = entity.prevRotationPitch = s.pitch;
        if (entity instanceof EntityLivingBase) {
            ((EntityLivingBase) entity).rotationYawHead = s.yaw;
            ((EntityLivingBase) entity).prevRotationYawHead = s.yaw;
        }

        try {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(r, g, b, alpha);

            mc.getRenderManager().doRenderEntity(
                    entity, s.x - viewX, s.y - viewY, s.z - viewZ,
                    s.yaw, partialTicks, false);

            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        } catch (Throwable ignored) {
        } finally {
            entity.posX = origX; entity.posY = origY; entity.posZ = origZ;
            entity.prevPosX = origPX; entity.prevPosY = origPY; entity.prevPosZ = origPZ;
            entity.lastTickPosX = origLX; entity.lastTickPosY = origLY; entity.lastTickPosZ = origLZ;
            entity.rotationYaw = origYaw; entity.rotationPitch = origPitch;
            entity.prevRotationYaw = origPYaw; entity.prevRotationPitch = origPPitch;
            if (entity instanceof EntityLivingBase) {
                ((EntityLivingBase) entity).rotationYawHead = origYawHead;
                ((EntityLivingBase) entity).prevRotationYawHead = origPYawHead;
            }
        }

        if (drawBox) {
            drawBoundingBoxAt(entity, s.x, s.y, s.z,
                    viewX, viewY, viewZ, r, g, b, alpha, true);
        }
    }

    /** Wireframe-only outline at the entity's current position — used in
     *  LagBased mode to show where the server actually sees them. */
    private void drawCurrentPositionBox(Entity entity, float partialTicks,
                                        double viewX, double viewY, double viewZ,
                                        float r, float g, float b) {
        // Use the interpolated position (what we'd actually see if we
        // weren't drawing the shadow at the past pose).
        double ex = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double ey = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double ez = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        drawBoundingBoxAt(entity, ex, ey, ez, viewX, viewY, viewZ, r, g, b, 0.9F, false);
    }

    private void drawBoundingBoxAt(Entity entity, double posX, double posY, double posZ,
                                   double viewX, double viewY, double viewZ,
                                   float r, float g, float b, float alpha, boolean filled) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.6F);

            float halfW = entity.width / 2.0F;
            double bx = posX - viewX;
            double by = posY - viewY;
            double bz = posZ - viewZ;
            AxisAlignedBB box = new AxisAlignedBB(
                    bx - halfW, by, bz - halfW,
                    bx + halfW, by + entity.height, bz + halfW);
            GlStateManager.color(r, g, b, Math.min(1F, alpha * 1.1F));
            RenderGlobal.drawSelectionBoundingBox(box);
            if (filled) {
                GlStateManager.color(r, g, b, alpha * 0.18F);
                drawFilledBox(box, r, g, b, alpha * 0.18F);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private int resolveShadowColor() {
        ShadowColor sc = (ShadowColor) colorSetting.getMode();
        if (sc == null) sc = ShadowColor.Theme;
        switch (sc) {
            case Red:    return 0xFFFF4F4F;
            case Green:  return 0xFF4FFF6F;
            case Blue:   return 0xFF5FA8FF;
            case Yellow: return 0xFFFFE96B;
            case Purple: return 0xFFB87BFF;
            case White:  return 0xFFFFFFFF;
            case Theme:
            default:     return GuiModule.getThemeColor(0) | 0xFF000000;
        }
    }

    private void drawFilledBox(AxisAlignedBB bb, float r, float g, float b, float a) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        tessellator.draw();
    }

    private static final class TickSnapshot {
        final double x, y, z;
        final float yaw, pitch;
        final long timestamp;

        TickSnapshot(double x, double y, double z, float yaw, float pitch, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.timestamp = timestamp;
        }
    }
}
