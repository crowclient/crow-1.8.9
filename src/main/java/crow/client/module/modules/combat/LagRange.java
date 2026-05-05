package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.EventDirection;
import crow.client.event.impl.PacketEvent;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import java.util.concurrent.ConcurrentLinkedQueue;

public class LagRange extends Module {

    private static final int MAX_QUEUED_POSITION = 40;
    private static final long HARD_MAX_DELAY_MS = 800;
    private static final int MAX_FLUSH_PER_TICK = 10;
    private static final int MAX_VELOCITY_QUEUE = 20;
    private static final int MAX_VELOCITY_FLUSH = 4;

    private final SliderSetting lagMs;
    private final TickSetting delayPosition;
    private final TickSetting delayedVelocity;
    private final SliderSetting velocityDelayMs;

    private final ConcurrentLinkedQueue<DelayedPacket> positionQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<DelayedVelocity> velocityQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean flushing = false;

    public LagRange() {
        super("LagRange", ModuleCategory.combat);
        this.withDescription("Delays position packets to extend effective reach. Does not delay KeepAlive.");
        this.registerSetting(new DescriptionSetting("Delays C03 position packets."));
        this.registerSetting(lagMs = new SliderSetting("Lag (ms)", 80.0, 0.0, 300.0, 10.0));
        this.registerSetting(delayPosition = new TickSetting("Delay Position", true));
        this.registerSetting(delayedVelocity = new TickSetting("Delayed Velocity", true));
        this.registerSetting(velocityDelayMs = new SliderSetting("Velocity Delay (ms)", 100.0, 0.0, 400.0, 10.0));
    }

    @Override
    public void onEnable() {
        positionQueue.clear();
        velocityQueue.clear();
    }

    @Override
    public void onDisable() {
        flushAllPosition();
        applyAllVelocities();
    }

    @Override
    public String getHudSuffix() {
        return (int) lagMs.getInput() + "ms";
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.isSingleplayer()) {
            positionQueue.clear();
            velocityQueue.clear();
            return;
        }

        long now = System.currentTimeMillis();
        long delay = (long) lagMs.getInput();

        flushing = true;
        try {
            int flushed = 0;
            while (!positionQueue.isEmpty() && flushed < MAX_FLUSH_PER_TICK) {
                DelayedPacket dp = positionQueue.peek();
                long age = now - dp.timestamp;

                if (age >= delay || age >= HARD_MAX_DELAY_MS) {
                    positionQueue.poll();
                    sendPacket(dp.packet);
                    flushed++;
                } else {
                    break;
                }
            }
        } finally {
            flushing = false;
        }

        int vFlushed = 0;
        while (!velocityQueue.isEmpty() && vFlushed < MAX_VELOCITY_FLUSH) {
            DelayedVelocity dv = velocityQueue.peek();
            if (now - dv.timestamp >= velocityDelayMs.getInput()) {
                velocityQueue.poll();
                dv.apply(mc.thePlayer);
                vFlushed++;
            } else {
                break;
            }
        }
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.isSingleplayer()) return;

        if (e.getDirection() == EventDirection.OUTGOING) {
            if (flushing) return;

            Packet<?> packet = e.getPacket();

            if (delayPosition.isToggled() && lagMs.getInput() > 0 && packet instanceof C03PacketPlayer) {

                if (positionQueue.size() >= MAX_QUEUED_POSITION) {

                    DelayedPacket oldest = positionQueue.poll();
                    if (oldest != null) {
                        flushing = true;
                        try { sendPacket(oldest.packet); } finally { flushing = false; }
                    }
                }
                positionQueue.add(new DelayedPacket(packet, System.currentTimeMillis()));
                e.setCancelled(true);
            }
        }

        if (e.getDirection() == EventDirection.INCOMING) {
            if (delayedVelocity.isToggled()) {
                Packet<?> packet = e.getPacket();
                if (packet instanceof S12PacketEntityVelocity) {
                    S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) packet;
                    if (mc.thePlayer != null && s12.getEntityID() == mc.thePlayer.getEntityId()) {
                        if (velocityQueue.size() >= MAX_VELOCITY_QUEUE) {

                            DelayedVelocity oldest = velocityQueue.poll();
                            if (oldest != null) oldest.apply(mc.thePlayer);
                        }
                        velocityQueue.add(new DelayedVelocity(
                                s12.getMotionX(), s12.getMotionY(), s12.getMotionZ(),
                                System.currentTimeMillis()));
                        e.setCancelled(true);
                    }
                }
            }
        }
    }

    private void sendPacket(Packet<?> packet) {
        if (mc.getNetHandler() != null && packet != null) {
            try {
                mc.getNetHandler().addToSendQueue(packet);
            } catch (Exception ignored) {}
        }
    }

    private void flushAllPosition() {
        flushing = true;
        try {
            while (!positionQueue.isEmpty()) {
                DelayedPacket dp = positionQueue.poll();
                sendPacket(dp.packet);
            }
        } finally {
            flushing = false;
        }
    }

    private void applyAllVelocities() {
        if (mc.thePlayer != null) {
            while (!velocityQueue.isEmpty()) {
                DelayedVelocity dv = velocityQueue.poll();
                dv.apply(mc.thePlayer);
            }
        }
        velocityQueue.clear();
    }

    private static class DelayedPacket {
        final Packet<?> packet;
        final long timestamp;

        DelayedPacket(Packet<?> packet, long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }
    }

    private static class DelayedVelocity {
        final int motionX, motionY, motionZ;
        final long timestamp;

        DelayedVelocity(int motionX, int motionY, int motionZ, long timestamp) {
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.timestamp = timestamp;
        }

        void apply(net.minecraft.entity.player.EntityPlayer player) {
            if (player != null) {
                player.motionX = motionX / 8000.0D;
                player.motionY = motionY / 8000.0D;
                player.motionZ = motionZ / 8000.0D;
            }
        }
    }
}
