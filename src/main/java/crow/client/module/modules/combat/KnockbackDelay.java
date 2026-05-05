package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.PacketEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class KnockbackDelay extends Module {

    private final SliderSetting delaySetting;
    private final Queue<DelayedVelocity> pendingVelocities = new ConcurrentLinkedQueue<>();

    public KnockbackDelay() {
        super("KnockbackDelay", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Delays incoming knockback to manipulate distancing."));
        this.registerSetting(delaySetting = new SliderSetting("Delay", 100, 0, 500, 1));
    }

    @Override
    public void onDisable() {

        pendingVelocities.clear();
    }

    @Override
    public void onEnable() {
        pendingVelocities.clear();
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (!e.isIncoming()) {
            return;
        }

        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = e.getPacket();

            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {

                double motionX = packet.getMotionX() / 8000.0D;
                double motionY = packet.getMotionY() / 8000.0D;
                double motionZ = packet.getMotionZ() / 8000.0D;

                int delayMs = (int) delaySetting.getInput();

                if (delayMs > 0) {

                    e.setCanceled(true);

                    pendingVelocities.add(new DelayedVelocity(motionX, motionY, motionZ, System.currentTimeMillis() + delayMs));
                }
            }
        }
    }

    @Subscribe
    public void onTick(ForgeEvent fe) {

        if (fe.getEvent() instanceof TickEvent.RenderTickEvent) {
            if (mc.thePlayer == null) {
                pendingVelocities.clear();
                return;
            }

            long currentTime = System.currentTimeMillis();
            Iterator<DelayedVelocity> iterator = pendingVelocities.iterator();

            while (iterator.hasNext()) {
                DelayedVelocity dv = iterator.next();

                if (currentTime >= dv.applyTime) {

                    mc.thePlayer.motionX = dv.motionX;
                    mc.thePlayer.motionY = dv.motionY;
                    mc.thePlayer.motionZ = dv.motionZ;

                    iterator.remove();
                }
            }
        }
    }

    private static class DelayedVelocity {
        public final double motionX;
        public final double motionY;
        public final double motionZ;
        public final long applyTime;

        public DelayedVelocity(double motionX, double motionY, double motionZ, long applyTime) {
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.applyTime = applyTime;
        }
    }
}
