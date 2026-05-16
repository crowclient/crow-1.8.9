package crow.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import crow.client.event.EventDirection;
import crow.client.event.impl.PacketEvent;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.util.ArrayDeque;
import java.util.Deque;

public class BlinkInfinite extends Module {

    private final SliderSetting ticksPerRelease;
    private final SliderSetting maxQueueSize;
    private final TickSetting showClone;
    private final TickSetting autoReleaseOnTeleport;
    private final DescriptionSetting queuedPacketsDesc;

    private final Deque<Packet<?>> packets = new ArrayDeque<>();
    private EntityOtherPlayerMP blinkClone;
    private boolean releasingPackets;
    private int ticksSinceLastRelease;
    private int ticksActive;

    public BlinkInfinite() {
        super("BlinkInfinite", ModuleCategory.movement);
        this.withDescription("Holds outgoing packets and drains them slowly (1 every N ticks). Queue grows unbounded — effectively infinite blink.");
        this.registerSetting(ticksPerRelease = new SliderSetting("Release ticks", 4, 1, 40, 1));
        this.registerSetting(maxQueueSize = new SliderSetting("Max queue", 2000, 100, 10000, 100));
        this.registerSetting(showClone = new TickSetting("Show clone", true));
        this.registerSetting(autoReleaseOnTeleport = new TickSetting("Off on TP", true));
        this.registerSetting(queuedPacketsDesc = new DescriptionSetting("Queued: 0"));
    }

    @Override
    public String getHudSuffix() {
        return String.valueOf(packets.size());
    }

    @Override
    public void onEnable() {
        if (isSingleplayerSession()) {
            Utils.Player.sendMessageToSelf("§e[BlinkInfinite] §cDisabled in singleplayer to prevent world desync.");
            disable();
            return;
        }

        packets.clear();
        releasingPackets = false;
        ticksSinceLastRelease = 0;
        ticksActive = 0;
        if (showClone.isToggled()) {
            spawnBlinkClone();
        }
        updateQueuedLabel();
    }

    @Override
    public void onDisable() {
        flushAllPackets();
        removeBlinkClone();
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame()) {
            packets.clear();
            removeBlinkClone();
            return;
        }

        if (isSingleplayerSession()) {
            packets.clear();
            removeBlinkClone();
            if (isEnabled()) {
                Utils.Player.sendMessageToSelf("§e[BlinkInfinite] §cDisabled in singleplayer to prevent world desync.");
                disable();
            }
            return;
        }

        ticksActive++;
        ticksSinceLastRelease++;

        int maxQueue = (int) maxQueueSize.getInput();
        if (packets.size() >= maxQueue) {
            Utils.Player.sendMessageToSelf("§e[BlinkInfinite] §cAuto-released — queue size limit reached (" + maxQueue + ").");
            disable();
            return;
        }

        int interval = Math.max(1, (int) ticksPerRelease.getInput());
        if (ticksSinceLastRelease >= interval && !packets.isEmpty()) {
            releaseOnePacket();
            ticksSinceLastRelease = 0;
        }

        if (blinkClone != null) {
            blinkClone.rotationYawHead = mc.thePlayer.rotationYawHead;
            blinkClone.renderYawOffset = mc.thePlayer.renderYawOffset;
        }

        updateQueuedLabel();
    }

    @Subscribe
    public void packetEvent(PacketEvent event) {
        if (!Utils.Player.isPlayerInGame()) return;
        if (isSingleplayerSession()) return;

        if (event.getDirection() == EventDirection.OUTGOING && !releasingPackets) {
            Packet<?> packet = event.getPacket();
            if (!shouldIntercept(packet)) return;

            packets.addLast(packet);
            event.setCancelled(true);
            updateQueuedLabel();
            return;
        }

        if (event.getDirection() == EventDirection.INCOMING && autoReleaseOnTeleport.isToggled()) {
            if (event.getPacket() instanceof S08PacketPlayerPosLook) {
                if (!packets.isEmpty()) {
                    Utils.Player.sendMessageToSelf("§e[BlinkInfinite] §cServer sent S08 position correction — releasing.");

                    packets.clear();
                    disable();
                }
            }
        }
    }

    private boolean shouldIntercept(Packet<?> packet) {

        if (packet instanceof C00Handshake
                || packet instanceof C00PacketLoginStart
                || packet instanceof C16PacketClientStatus) {
            return false;
        }
        return true;
    }

    private void releaseOnePacket() {
        if (mc.getNetHandler() == null) {
            packets.clear();
            return;
        }
        Packet<?> packet = packets.pollFirst();
        if (packet == null) return;

        releasingPackets = true;
        try {
            mc.getNetHandler().addToSendQueue(packet);
        } finally {
            releasingPackets = false;
        }
    }

    private void flushAllPackets() {
        if (mc.getNetHandler() == null) {
            packets.clear();
            releasingPackets = false;
            return;
        }
        releasingPackets = true;
        try {
            Packet<?> packet;
            while ((packet = packets.pollFirst()) != null) {
                mc.getNetHandler().addToSendQueue(packet);
            }
        } finally {
            releasingPackets = false;
        }
        ticksSinceLastRelease = 0;
        ticksActive = 0;
        updateQueuedLabel();
    }

    private void updateQueuedLabel() {
        int count = packets.size();
        String timeStr = String.format("%.1fs", ticksActive / 20.0);
        queuedPacketsDesc.setDesc("Queued: " + count + " (" + timeStr + ")");
    }

    private void spawnBlinkClone() {
        if (!Utils.Player.isPlayerInGame()) return;

        removeBlinkClone();
        blinkClone = new EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.getGameProfile());
        blinkClone.copyLocationAndAnglesFrom(mc.thePlayer);
        blinkClone.rotationYawHead = mc.thePlayer.rotationYawHead;
        blinkClone.renderYawOffset = mc.thePlayer.renderYawOffset;
        blinkClone.inventory.copyInventory(mc.thePlayer.inventory);
        mc.theWorld.addEntityToWorld(-1338, blinkClone);
    }

    private void removeBlinkClone() {
        if (blinkClone != null && mc.theWorld != null) {
            mc.theWorld.removeEntityFromWorld(-1338);
            blinkClone = null;
        }
    }

    private boolean isSingleplayerSession() {
        return mc != null && mc.isSingleplayer();
    }
}
