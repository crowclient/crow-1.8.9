package crow.client.module.modules.combat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import crow.client.event.EventDirection;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.PacketEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
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
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class Backtrack extends Module {

    public static SliderSetting delayMs, maxDistance, opacity, combatWindow;
    public static TickSetting playersOnly, weaponOnly, renderBox, onlyMoving, combatOnly;

    private final ConcurrentLinkedQueue<DelayedEntityPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean replaying = false;
    private static final int MAX_QUEUED_PACKETS = 200;
    private static final long HARD_MAX_DELAY_MS = 1000;

    private final Map<Integer, Long> lastIncomingMs = new ConcurrentHashMap<>();

    private static final int MAX_QUEUED_PER_ENTITY = 6;

    private volatile long lastCombatTime = 0L;
    private boolean wasInCombat = false;

    public Backtrack() {
        super("Backtrack", ModuleCategory.combat);
        this.withDescription("Delays incoming entity position packets so opponents render at where they were ~delay ms ago. Aim there — server's lag-comp accepts the hit.");
        this.registerSetting(delayMs = new SliderSetting("Delay (ms)", 100, 0, 500, 10));
        this.registerSetting(maxDistance = new SliderSetting("Max Distance", 6.0D, 1.0D, 10.0D, 0.1D));
        this.registerSetting(opacity = new SliderSetting("Box Opacity", 0.35D, 0.05D, 1.0D, 0.05D));
        this.registerSetting(playersOnly = new TickSetting("Players only", true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon only", false));
        this.registerSetting(onlyMoving = new TickSetting("Only when moving", true));
        this.registerSetting(combatOnly = new TickSetting("Combat only", true));
        this.registerSetting(combatWindow = new SliderSetting("Combat window (s)", 3.0D, 1.0D, 10.0D, 0.5D));
        combatWindow.visibleWhen(() -> combatOnly.isToggled());
        this.registerSetting(renderBox = new TickSetting("Render server-pos box", true));
    }

    @Override
    public void onDisable() {
        flushAllPackets();
        lastIncomingMs.clear();
        wasInCombat = false;
        lastCombatTime = 0L;
    }

    @Override
    public void onEnable() {
        packetQueue.clear();
        lastIncomingMs.clear();
        wasInCombat = false;
        lastCombatTime = 0L;
    }

    private boolean isCombatActive() {
        if (!combatOnly.isToggled()) return true;
        long age = System.currentTimeMillis() - lastCombatTime;
        return age <= (long) (combatWindow.getInput() * 1000);
    }

    @Override
    public String getHudSuffix() {
        int d = (int) delayMs.getInput();
        if (d > 0) {
            return getLocalPing() + "+" + d + "ms";
        }
        return getLocalPing() + "ms";
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        Packet<?> packet = e.getPacket();

        if (e.getDirection() == EventDirection.OUTGOING && packet instanceof C02PacketUseEntity) {
            if (((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
                lastCombatTime = System.currentTimeMillis();
            }
            return;
        }

        if (delayMs.getInput() <= 0) return;
        if (e.getDirection() != EventDirection.INCOMING) return;
        if (replaying) return;
        if (!Utils.Player.isPlayerInGame() || mc.theWorld == null) return;
        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return;
        if (!isCombatActive()) return;

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity s14 = (S14PacketEntity) packet;

            if (packet.getClass() == S14PacketEntity.S16PacketEntityLook.class) return;

            Entity entity = s14.getEntity(mc.theWorld);
            if (entity != null && shouldBacktrack(entity)) {
                tryQueue(entity.getEntityId(), packet, e);
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
            Entity entity = mc.theWorld.getEntityByID(s18.getEntityId());
            if (entity != null && shouldBacktrack(entity)) {
                tryQueue(s18.getEntityId(), packet, e);
            }
        }
    }

    private void tryQueue(int entityId, Packet<?> packet, PacketEvent e) {
        long now = System.currentTimeMillis();
        lastIncomingMs.put(entityId, now);

        if (packetQueue.size() >= MAX_QUEUED_PACKETS) return;

        int countForEntity = 0;
        for (DelayedEntityPacket dp : packetQueue) {
            if (dp.entityId == entityId && ++countForEntity >= MAX_QUEUED_PER_ENTITY) {
                return;
            }
        }

        packetQueue.add(new DelayedEntityPacket(packet, entityId, now));
        e.setCancelled(true);
    }

    private boolean shouldBacktrack(Entity entity) {
        if (entity == mc.thePlayer) return false;
        if (!(entity instanceof EntityLivingBase)) return false;
        if (playersOnly.isToggled() && !(entity instanceof EntityPlayer)) return false;
        if (!((EntityLivingBase) entity).isEntityAlive()) return false;
        double dist = mc.thePlayer.getDistanceToEntity(entity);
        return dist <= maxDistance.getInput() + 3.0;
    }

    @Subscribe
    public void onTick(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof TickEvent.ClientTickEvent)) return;
        TickEvent.ClientTickEvent cte = (TickEvent.ClientTickEvent) fe.getEvent();
        if (cte.phase != TickEvent.Phase.END) return;
        if (!Utils.Player.isPlayerInGame()) {
            flushAllPackets();
            wasInCombat = false;
            return;
        }

        if (combatOnly.isToggled() && mc.theWorld != null && mc.thePlayer != null) {
            double range = maxDistance.getInput();
            double rangeSq = range * range;
            for (Entity ent : new java.util.ArrayList<>(mc.theWorld.loadedEntityList)) {
                if (ent == null || ent == mc.thePlayer || !(ent instanceof EntityPlayer)) continue;
                if (!((EntityPlayer) ent).isEntityAlive()) continue;
                if (mc.thePlayer.getDistanceSqToEntity(ent) <= rangeSq) {
                    lastCombatTime = System.currentTimeMillis();
                    break;
                }
            }
        }

        boolean active = isCombatActive();
        if (wasInCombat && !active) {
            flushAllPackets();
        }
        wasInCombat = active;

        flushExpiredPackets();
    }

    private void flushExpiredPackets() {
        long now = System.currentTimeMillis();
        long delay = (long) delayMs.getInput();

        replaying = true;
        try {
            while (!packetQueue.isEmpty()) {
                DelayedEntityPacket dp = packetQueue.peek();
                long age = now - dp.timestamp;
                if (age >= delay || age >= HARD_MAX_DELAY_MS) {
                    packetQueue.poll();
                    replayPacket(dp.packet);
                } else {
                    break;
                }
            }
        } finally {
            replaying = false;
        }
    }

    private void replayPacket(Packet<?> packet) {
        if (mc.getNetHandler() == null) return;
        try {
            if (packet instanceof S14PacketEntity) {
                mc.getNetHandler().handleEntityMovement((S14PacketEntity) packet);
            } else if (packet instanceof S18PacketEntityTeleport) {
                mc.getNetHandler().handleEntityTeleport((S18PacketEntityTeleport) packet);
            }
        } catch (Exception ignored) {

        }
    }

    private void flushAllPackets() {
        replaying = true;
        try {
            while (!packetQueue.isEmpty()) {
                DelayedEntityPacket dp = packetQueue.poll();
                replayPacket(dp.packet);
            }
        } finally {
            replaying = false;
        }
    }

    private static final double GRAVITY        = 0.08D;
    private static final double VERTICAL_DRAG  = 0.98D;
    private static final double MAX_PING_TICKS = 20.0D;

    private final Predicted scratch = new Predicted();

    private boolean isPacketDelayActive() {
        if (delayMs.getInput() <= 0) return false;
        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return false;
        if (!isCombatActive()) return false;
        return true;
    }

    private double getPredictionTicks(int ping) {
        double extraDelay = isPacketDelayActive() ? delayMs.getInput() : 0;
        double tickMs = (ping / 2.0) + extraDelay;
        return Math.min(tickMs / 50.0, MAX_PING_TICKS);
    }

    @Subscribe
    public void onRenderWorld(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof RenderWorldLastEvent)) return;
        if (!renderBox.isToggled() || !Utils.Player.isPlayerInGame()) return;
        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return;

        double pingTicks = getPredictionTicks(getLocalPing());
        if (pingTicks <= 0.0) return;

        final RenderWorldLastEvent rwle = (RenderWorldLastEvent) fe.getEvent();
        final float partialTicks = rwle.partialTicks;

        final double viewX = mc.getRenderManager().viewerPosX;
        final double viewY = mc.getRenderManager().viewerPosY;
        final double viewZ = mc.getRenderManager().viewerPosZ;

        final int themeColor = GuiModule.getThemeColor(0);
        final float r = ((themeColor >> 16) & 0xFF) / 255.0F;
        final float g = ((themeColor >> 8) & 0xFF) / 255.0F;
        final float b = (themeColor & 0xFF) / 255.0F;
        final float baseAlpha = (float) opacity.getInput();

        final float confidence = (float) Math.max(0.4D, 1.0D - pingTicks / MAX_PING_TICKS);

        final double maxDist = maxDistance.getInput();
        final double maxDistSq = (maxDist + 3.0D) * (maxDist + 3.0D);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.5F);

            for (Entity entity : new java.util.ArrayList<>(mc.theWorld.loadedEntityList)) {
                if (entity == null || entity == mc.thePlayer || !(entity instanceof EntityLivingBase)) continue;
                if (!((EntityLivingBase) entity).isEntityAlive()) continue;
                if (playersOnly.isToggled() && !(entity instanceof EntityPlayer)) continue;
                if (mc.thePlayer.getDistanceSqToEntity(entity) > maxDistSq) continue;

                final double vxTick = entity.posX - entity.prevPosX;
                final double vzTick = entity.posZ - entity.prevPosZ;
                if (onlyMoving.isToggled() && vxTick * vxTick + vzTick * vzTick < 0.001D) continue;

                predictPosition(entity, pingTicks, partialTicks, scratch);

                final float halfW = entity.width / 2.0F;
                final double bx = scratch.x - viewX;
                final double by = scratch.y - viewY;
                final double bz = scratch.z - viewZ;

                final AxisAlignedBB box = new AxisAlignedBB(
                        bx - halfW, by, bz - halfW,
                        bx + halfW, by + entity.height, bz + halfW);

                final float outlineA = Math.min(1.0F, baseAlpha + 0.2F) * confidence;
                final float fillA    = baseAlpha * 0.5F * confidence;
                GlStateManager.color(r, g, b, outlineA);
                RenderGlobal.drawSelectionBoundingBox(box);
                drawFilledBox(box, r, g, b, fillA);
            }
        } finally {
            GL11.glPopAttrib();

            crow.client.utils.RenderUtils.syncAllGlState();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void predictPosition(Entity entity, double pingTicks, float partialTicks, Predicted out) {
        final double baseX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        final double baseY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        final double baseZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        final double vx = entity.posX - entity.prevPosX;
        final double vy = entity.posY - entity.prevPosY;
        final double vz = entity.posZ - entity.prevPosZ;

        out.x = baseX + vx * pingTicks;
        out.z = baseZ + vz * pingTicks;

        if (entity.onGround) {
            out.y = baseY;
            return;
        }

        double py = baseY;
        double cy = vy;
        final int fullTicks = (int) pingTicks;
        for (int i = 0; i < fullTicks; i++) {
            py += cy;
            cy -= GRAVITY;
            cy *= VERTICAL_DRAG;
        }
        py += cy * (pingTicks - fullTicks);
        out.y = py;
    }

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

    public static AxisAlignedBB getPredictedHitbox(Entity entity, float partialTicks) {
        if (entity == null || entity == mc.thePlayer) return null;
        if (!(entity instanceof EntityLivingBase)) return null;

        Backtrack instance = (Backtrack) Crow.moduleManager.getModuleByClazz(Backtrack.class);
        if (instance == null || !instance.isEnabled()) return null;
        if (!renderBox.isToggled()) return null;
        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return null;
        if (!((EntityLivingBase) entity).isEntityAlive()) return null;
        if (playersOnly.isToggled() && !(entity instanceof EntityPlayer)) return null;

        if (mc.thePlayer == null) return null;
        double maxDist = maxDistance.getInput();
        double maxDistSq = (maxDist + 3.0D) * (maxDist + 3.0D);
        if (mc.thePlayer.getDistanceSqToEntity(entity) > maxDistSq) return null;

        double pingTicks = instance.getPredictionTicks(instance.getLocalPing());
        if (pingTicks <= 0.0) return null;

        double vxTick = entity.posX - entity.prevPosX;
        double vzTick = entity.posZ - entity.prevPosZ;
        if (onlyMoving.isToggled() && vxTick * vxTick + vzTick * vzTick < 0.001D) return null;

        Predicted p = new Predicted();
        predictPosition(entity, pingTicks, partialTicks, p);

        float halfW = entity.width / 2.0F;
        return new AxisAlignedBB(
                p.x - halfW, p.y, p.z - halfW,
                p.x + halfW, p.y + entity.height, p.z + halfW);
    }

    public static AxisAlignedBB unionWithPredicted(Entity entity, AxisAlignedBB current, float partialTicks) {
        AxisAlignedBB predicted = getPredictedHitbox(entity, partialTicks);
        if (predicted == null) return current;
        return current.union(predicted);
    }

    private static final class Predicted {
        double x, y, z;
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

    private static class DelayedEntityPacket {
        final Packet<?> packet;
        final int entityId;
        final long timestamp;

        DelayedEntityPacket(Packet<?> packet, int entityId, long timestamp) {
            this.packet = packet;
            this.entityId = entityId;
            this.timestamp = timestamp;
        }
    }
}
