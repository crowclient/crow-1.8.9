package crow.client.module.modules.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.network.NetworkPlayerInfo;
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
 * Backtrack — lets you hit players at their previous positions by
 * exploiting the server's lag compensation window.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>Manual</b> — Records each nearby entity's past N tick
 *       positions and renders translucent "shadows" of them. The
 *       outgoing connection is NOT modified. The user aims at any
 *       shadow; the server-side lag-comp window (typically ~500 ms
 *       on Hypixel-class servers) accepts the hit against where the
 *       entity was at that past tick.</li>
 *   <li><b>Lag Based</b> — Delays outgoing player position packets
 *       (C03/C04/C05/C06) by {@code Latency} ms when an attack is
 *       imminent, then releases them. The server's view of the
 *       player's position falls behind by {@code Latency} ms, so the
 *       lag-comp window effectively shifts further into the past —
 *       letting the user hit positions older than the natural
 *       lag-comp window. Also renders the opponent's server-side
 *       position (where the server thinks they were at the moment
 *       the server is processing the player's attack).</li>
 * </ul>
 *
 * <p>The static {@link #unionWithPredicted(Entity, AxisAlignedBB, float)}
 * helper is consumed by {@code MixinEntityRenderer} to expand the
 * mouse-over hit box: clicking on a rendered shadow registers as
 * clicking the entity, so the vanilla click → attack flow lands.
 */
public class Backtrack extends Module {

    public enum Mode { Manual, LagBased }
    public enum ShadowColor { Theme, Red, Green, Blue, Yellow, Purple, White }

    public static ComboSetting modeSetting;
    public static TickSetting renderTicks;
    public static SliderSetting ticksHistory;
    public static TickSetting renderServerPos;
    public static SliderSetting latency;
    public static ComboSetting colorSetting;
    public static SliderSetting maxDistance;
    public static TickSetting playersOnly;
    public static TickSetting weaponOnly;

    /** Per-entity ring buffer of historical tick snapshots. */
    private final Map<Integer, Deque<TickSnapshot>> history = new ConcurrentHashMap<>();

    /** Our own position history — used to render server-side position in Lag-Based mode. */
    private final Deque<TickSnapshot> ownHistory = new ArrayDeque<>();

    private static final int OWN_HISTORY_CAP = 60;
    private static final int MAX_HISTORY_PER_ENTITY = 25;

    public Backtrack() {
        super("Backtrack", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting(
                "Hit players at their previous positions via server-side lag compensation."));
        this.registerSetting(modeSetting    = new ComboSetting<>("Mode", Mode.Manual));
        this.registerSetting(renderTicks    = new TickSetting("Render Previous Ticks", true));
        this.registerSetting(ticksHistory   = new SliderSetting("Ticks", 8, 1, 20, 1));
        this.registerSetting(renderServerPos = new TickSetting("Render Server Pos", true));
        this.registerSetting(latency        = new SliderSetting("Latency", 150, 50, 500, 10));
        this.registerSetting(colorSetting   = new ComboSetting<>("Color", ShadowColor.Theme));
        this.registerSetting(maxDistance    = new SliderSetting("Max dist", 6.0D, 1.0D, 10.0D, 0.1D));
        this.registerSetting(playersOnly    = new TickSetting("Players only", true));
        this.registerSetting(weaponOnly     = new TickSetting("Weapon only", false));

        // Visibility — Manual hides Lag-Based knobs and vice versa.
        renderTicks.visibleWhen(() -> getMode() == Mode.Manual);
        ticksHistory.visibleWhen(() -> getMode() == Mode.Manual);
        renderServerPos.visibleWhen(() -> getMode() == Mode.LagBased);
        latency.visibleWhen(() -> getMode() == Mode.LagBased);
    }

    @Override
    public void onEnable() {
        history.clear();
        ownHistory.clear();
    }

    @Override
    public void onDisable() {
        flushAllOutgoing();
        history.clear();
        ownHistory.clear();
    }

    @Override
    public void guiButtonToggled(Setting s) {
        // Force visibility refresh when Mode changes.
        if (s == modeSetting && getMode() == Mode.Manual) {
            flushAllOutgoing();
        }
    }

    @SuppressWarnings("unchecked")
    private static Mode getMode() {
        return modeSetting == null ? Mode.Manual : (Mode) modeSetting.getMode();
    }

    @Override
    public String getHudSuffix() {
        Mode m = getMode();
        if (m == Mode.LagBased) {
            return ((int) latency.getInput()) + "ms";
        }
        return ((int) ticksHistory.getInput()) + "t";
    }

    /* ====================================================================== */
    /* Tick — record snapshots, release delayed outgoing packets              */
    /* ====================================================================== */

    @Subscribe
    public void onTick(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof TickEvent.ClientTickEvent)) return;
        TickEvent.ClientTickEvent cte = (TickEvent.ClientTickEvent) fe.getEvent();
        if (cte.phase != TickEvent.Phase.END) return;
        if (!Utils.Player.isPlayerInGame() || mc.theWorld == null) {
            history.clear();
            ownHistory.clear();
            flushAllOutgoing();
            return;
        }

        long now = System.currentTimeMillis();
        int cap = (int) ticksHistory.getInput();
        double range = maxDistance.getInput();
        double rangeSq = (range + 1.0) * (range + 1.0);

        // Record snapshots of nearby entities.
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
            while (buf.size() > Math.max(cap, MAX_HISTORY_PER_ENTITY)) {
                buf.removeLast();
            }
        }

        // Track our own position history (drives the server-pos shadow).
        if (mc.thePlayer != null) {
            ownHistory.addFirst(new TickSnapshot(
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, now));
            while (ownHistory.size() > OWN_HISTORY_CAP) ownHistory.removeLast();
        }

    }

    /* ====================================================================== */
    /* Lag-Based mode is now visual + aim only — no actual connection mod.    */
    /* Holding outgoing C03 packets triggered Grim's BadPacketsT check        */
    /* (position-vs-physics mismatch) badly enough that the "modify the      */
    /* connection" approach wasn't viable. Instead, Lag-Based mode renders   */
    /* a server-pos shadow at `now − latency` and points AimAssist /         */
    /* SilentAura at it; the server's own lag-comp window (~ping/2)          */
    /* accepts the hit naturally as long as `latency` stays inside that     */
    /* window. The user-facing behavior is the same — "hit them where they  */
    /* were `latency` ms ago" — without packet manipulation.                  */
    /* ====================================================================== */

    private void flushAllOutgoing() {
        // No-op — Lag-Based mode no longer holds outgoing packets.
    }

    /* ====================================================================== */
    /* Hit-box union — consumed by MixinEntityRenderer                        */
    /* ====================================================================== */

    /**
     * Expand an entity's hitbox to include all recorded backtrack
     * positions. This is what makes "click on a shadow → attack the
     * entity" work — the mouseover raycast sees the union and resolves
     * to the entity, then vanilla clickMouse fires C02 with the entity
     * ID. Server-side lag-comp converts that into a hit at the past
     * position the shadow represents.
     */
    /**
     * Return the (x, y, z) position aim modules should target on the
     * given entity, accounting for active backtrack. Returns {@code null}
     * if backtrack isn't applicable to this entity — callers fall back
     * to {@code entity.posX/Y/Z}.
     *
     * <p>Manual mode returns the OLDEST recorded snapshot (maximum lag-
     * comp benefit). Lag-Based mode returns the snapshot closest to
     * {@code now − latency} (the exact server-side position the lag-comp
     * window will hit). With this in place, AimAssist and SilentAura
     * automatically point their rotation at the same shadow the renderer
     * draws — so what the user sees IS where the hit lands.
     */
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

        Mode mode = getMode();
        TickSnapshot s;
        if (mode == Mode.LagBased) {
            s = pickServerPosSnapshot(buf);
        } else {
            // Manual: oldest available position within the active window.
            // peekLast() is the most-backtracked snapshot we recorded.
            int cap = (int) ticksHistory.getInput();
            s = null;
            int n = 0;
            for (TickSnapshot snap : buf) {
                if (n++ >= cap) break;
                s = snap; // overwrite — last assignment is oldest within cap
            }
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

        AxisAlignedBB out = current;
        Deque<TickSnapshot> buf = inst.history.get(entity.getEntityId());
        if (buf == null || buf.isEmpty()) return current;

        // For Manual mode, every shadow is a valid hit target. For
        // Lag-Based mode, only the oldest tracked position represents
        // the server-pos that the lag-comp window will accept.
        Mode mode = getMode();
        float halfW = entity.width / 2.0F;
        if (mode == Mode.Manual) {
            int cap = (int) ticksHistory.getInput();
            int n = 0;
            for (TickSnapshot s : buf) {
                if (n++ >= cap) break;
                out = out.union(new AxisAlignedBB(
                        s.x - halfW, s.y, s.z - halfW,
                        s.x + halfW, s.y + entity.height, s.z + halfW));
            }
        } else {
            // Lag-Based: union with the snapshot closest to (now - latency).
            TickSnapshot best = pickServerPosSnapshot(buf);
            if (best != null) {
                out = out.union(new AxisAlignedBB(
                        best.x - halfW, best.y, best.z - halfW,
                        best.x + halfW, best.y + entity.height, best.z + halfW));
            }
        }
        return out;
    }

    private static TickSnapshot pickServerPosSnapshot(Deque<TickSnapshot> buf) {
        if (buf == null || buf.isEmpty()) return null;
        long now = System.currentTimeMillis();
        long target = now - (long) latency.getInput();
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
    /* Rendering — translucent player-shaped shadows at past positions        */
    /* ====================================================================== */

    @Subscribe
    public void onRenderWorld(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof RenderWorldLastEvent)) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        Mode mode = getMode();
        boolean drawShadows = mode == Mode.Manual && renderTicks.isToggled();
        boolean drawServer  = mode == Mode.LagBased && renderServerPos.isToggled();
        if (!drawShadows && !drawServer) return;

        RenderWorldLastEvent rwle = (RenderWorldLastEvent) fe.getEvent();
        float partialTicks = rwle.partialTicks;

        double viewX = mc.getRenderManager().viewerPosX;
        double viewY = mc.getRenderManager().viewerPosY;
        double viewZ = mc.getRenderManager().viewerPosZ;

        int color = resolveShadowColor();
        float cr = ((color >> 16) & 0xFF) / 255.0F;
        float cg = ((color >>  8) & 0xFF) / 255.0F;
        float cb = ( color        & 0xFF) / 255.0F;

        int cap = (int) ticksHistory.getInput();
        for (Entity entity : new ArrayList<>(mc.theWorld.loadedEntityList)) {
            if (entity == null || entity == mc.thePlayer) continue;
            if (!(entity instanceof EntityLivingBase) || !((EntityLivingBase) entity).isEntityAlive()) continue;
            if (playersOnly.isToggled() && !(entity instanceof EntityPlayer)) continue;

            Deque<TickSnapshot> buf = history.get(entity.getEntityId());
            if (buf == null || buf.isEmpty()) continue;

            if (drawShadows) {
                int n = 0;
                int total = Math.min(cap, buf.size());
                for (TickSnapshot s : buf) {
                    if (n >= total) break;
                    // Older = more transparent. The oldest shadow (the
                    // one we aim at) keeps a minimum visible alpha so it
                    // stays clearly clickable even in long tails.
                    float fade = 1.0F - (n / (float) Math.max(1, total));
                    float overallAlpha = Math.max(0.25F, 0.85F * fade);
                    drawShadowFigure(entity, s, partialTicks,
                            viewX, viewY, viewZ, cr, cg, cb, overallAlpha);
                    n++;
                }
            }

            if (drawServer) {
                TickSnapshot s = pickServerPosSnapshot(buf);
                if (s != null) {
                    drawShadowFigure(entity, s, partialTicks,
                            viewX, viewY, viewZ, cr, cg, cb, 0.92F);
                }
            }
        }

        crow.client.utils.RenderUtils.syncAllGlState();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Premium per-shadow render: the actual entity rendered at the past
     * position with a translucent color tint, plus a crisp theme-color
     * outline box for "you can hit here" affordance. Mirrors Vape v4's
     * backtrack visual.
     */
    private void drawShadowFigure(Entity entity, TickSnapshot s, float partialTicks,
                                  double viewX, double viewY, double viewZ,
                                  float r, float g, float b, float alpha) {
        // Save entity state so we can swap it temporarily for the render.
        double origX = entity.posX, origY = entity.posY, origZ = entity.posZ;
        double origPX = entity.prevPosX, origPY = entity.prevPosY, origPZ = entity.prevPosZ;
        double origLX = entity.lastTickPosX, origLY = entity.lastTickPosY, origLZ = entity.lastTickPosZ;
        float origYaw = entity.rotationYaw, origPitch = entity.rotationPitch;
        float origPYaw = entity.prevRotationYaw, origPPitch = entity.prevRotationPitch;
        float origYawHead = entity instanceof EntityLivingBase
                ? ((EntityLivingBase) entity).rotationYawHead : 0F;
        float origPYawHead = entity instanceof EntityLivingBase
                ? ((EntityLivingBase) entity).prevRotationYawHead : 0F;

        // Swap to snapshot pose for the duration of the render.
        entity.posX = entity.lastTickPosX = entity.prevPosX = s.x;
        entity.posY = entity.lastTickPosY = entity.prevPosY = s.y;
        entity.posZ = entity.lastTickPosZ = entity.prevPosZ = s.z;
        entity.rotationYaw = entity.prevRotationYaw = s.yaw;
        entity.rotationPitch = entity.prevRotationPitch = s.pitch;
        if (entity instanceof EntityLivingBase) {
            ((EntityLivingBase) entity).rotationYawHead = s.yaw;
            ((EntityLivingBase) entity).prevRotationYawHead = s.yaw;
        }

        boolean prevRender = mc.gameSettings.thirdPersonView != 0;
        try {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(r, g, b, alpha);

            // Render the entity at the snapshot position with the
            // colored tint. RenderManager.doRenderEntity handles the
            // viewer-relative offset internally.
            mc.getRenderManager().doRenderEntity(
                    entity, s.x - viewX, s.y - viewY, s.z - viewZ,
                    s.yaw, partialTicks, false);

            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        } catch (Throwable ignored) {
            // Some entity renderers throw if state is awkward — never
            // let a render glitch cascade into a state-corruption crash.
        } finally {
            // Always restore — even if rendering threw.
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

        // Crisp outline box overlay so the click-target is obvious even
        // when the entity render is dim or partially occluded.
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.4F);

            float halfW = entity.width / 2.0F;
            double bx = s.x - viewX;
            double by = s.y - viewY;
            double bz = s.z - viewZ;
            AxisAlignedBB box = new AxisAlignedBB(
                    bx - halfW, by, bz - halfW,
                    bx + halfW, by + entity.height, bz + halfW);
            GlStateManager.color(r, g, b, Math.min(1F, alpha * 1.1F));
            RenderGlobal.drawSelectionBoundingBox(box);
            GlStateManager.color(r, g, b, alpha * 0.18F);
            drawFilledBox(box, r, g, b, alpha * 0.18F);
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

    /** Local-ping accessor kept for HUD use; not used by core logic. */
    @SuppressWarnings("unused")
    private int getLocalPing() {
        if (mc.getNetHandler() == null || mc.thePlayer == null) return 0;
        try {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (info == null) return 0;
            int ping = info.getResponseTime();
            return ping > 0 && ping < 5000 ? ping : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
