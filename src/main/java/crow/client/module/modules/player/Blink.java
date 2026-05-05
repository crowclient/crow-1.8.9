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
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.util.ArrayList;
import java.util.List;

public class Blink extends Module {

    private final TickSetting pulse;
    private final SliderSetting pulseDelay;
    private final SliderSetting maxDuration;
    private final TickSetting showClone;
    private final TickSetting autoReleaseOnTeleport;
    private final DescriptionSetting queuedPacketsDesc;

    private final List<Packet<?>> packets = new ArrayList<>();
    private long lastFlushTime;
    private EntityOtherPlayerMP blinkClone;
    private boolean releasingPackets;
    private int ticksActive;

    public Blink() {
        super("Blink", ModuleCategory.player);
        this.withDescription("Queues outgoing packets to simulate lag. Server sees you frozen in place.");
        this.registerSetting(pulse = new TickSetting("Pulse", false));
        this.registerSetting(pulseDelay = new SliderSetting("Pulse delay (ms)", 500, 100, 2000, 50));
        this.registerSetting(maxDuration = new SliderSetting("Max duration (s)", 10.0, 2.0, 30.0, 0.5));
        this.registerSetting(showClone = new TickSetting("Show clone", true));
        this.registerSetting(autoReleaseOnTeleport = new TickSetting("Release on S08 teleport", true));
        this.registerSetting(queuedPacketsDesc = new DescriptionSetting("Queued: 0"));
    }

    @Override
    public String getHudSuffix() {
        return String.valueOf(packets.size());
    }

    @Override
    public void onEnable() {
        if (isSingleplayerSession()) {
            Utils.Player.sendMessageToSelf("§e[Blink] §cDisabled in singleplayer to prevent world disconnects.");
            disable();
            return;
        }

        packets.clear();
        lastFlushTime = System.currentTimeMillis();
        releasingPackets = false;
        ticksActive = 0;
        if (showClone.isToggled()) {
            spawnBlinkClone();
        }
        updateQueuedLabel();
    }

    @Override
    public void onDisable() {
        flushPackets();
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
                Utils.Player.sendMessageToSelf("§e[Blink] §cDisabled in singleplayer to prevent world disconnects.");
                disable();
            }
            return;
        }

        ticksActive++;

        int maxTicks = (int) (maxDuration.getInput() * 20);
        if (ticksActive >= maxTicks && !packets.isEmpty()) {
            Utils.Player.sendMessageToSelf("§e[Blink] §cAuto-released — max duration reached.");
            disable();
            return;
        }

        if (pulse.isToggled() && !packets.isEmpty()
                && System.currentTimeMillis() - lastFlushTime >= pulseDelay.getInput()) {
            flushPackets();

            if (showClone.isToggled()) {
                removeBlinkClone();
                spawnBlinkClone();
            }
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

            packets.add(packet);
            event.setCancelled(true);
            updateQueuedLabel();
            return;
        }

        if (event.getDirection() == EventDirection.INCOMING && autoReleaseOnTeleport.isToggled()) {
            if (event.getPacket() instanceof S08PacketPlayerPosLook) {
                if (!packets.isEmpty()) {
                    Utils.Player.sendMessageToSelf("§e[Blink] §cServer sent S08 position correction — releasing.");

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

    private void flushPackets() {
        if (mc.getNetHandler() == null) {
            packets.clear();
            releasingPackets = false;
            return;
        }

        releasingPackets = true;
        try {
            for (Packet<?> packet : packets) {
                mc.getNetHandler().addToSendQueue(packet);
            }
        } finally {
            releasingPackets = false;
        }

        packets.clear();
        lastFlushTime = System.currentTimeMillis();
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
        mc.theWorld.addEntityToWorld(-1337, blinkClone);
    }

    private void removeBlinkClone() {
        if (blinkClone != null && mc.theWorld != null) {
            mc.theWorld.removeEntityFromWorld(-1337);
            blinkClone = null;
        }
    }

    private boolean isSingleplayerSession() {
        return mc != null && mc.isSingleplayer();
    }
}
