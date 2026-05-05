package crow.client.mixin.mixins;

import io.netty.channel.ChannelHandlerContext;
import crow.client.event.EventDirection;
import crow.client.event.impl.PacketEvent;
import crow.client.main.Crow;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(priority = 995, value = NetworkManager.class)
public class MixinNetworkManager {

    private static long crow$lastSwingTick = -1;

    private static boolean crow$injectingSwing = false;

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void sendPacket(Packet p_sendPacket_1_, CallbackInfo ci) {

        if (!crow$injectingSwing) {
            if (p_sendPacket_1_ instanceof C02PacketUseEntity) {
                C02PacketUseEntity usePacket = (C02PacketUseEntity) p_sendPacket_1_;
                if (usePacket.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    Minecraft mc = Minecraft.getMinecraft();
                    long currentTick = (mc.thePlayer != null) ? mc.thePlayer.ticksExisted : -1;
                    if (currentTick != crow$lastSwingTick) {

                        crow$injectingSwing = true;
                        try {
                            ((NetworkManager) (Object) this).sendPacket(new C0APacketAnimation());
                        } finally {
                            crow$injectingSwing = false;
                        }
                    }
                }
            }

            if (p_sendPacket_1_ instanceof C0APacketAnimation) {
                Minecraft mc = Minecraft.getMinecraft();
                crow$lastSwingTick = (mc.thePlayer != null) ? mc.thePlayer.ticksExisted : -1;
            }
        }

        PacketEvent e = new PacketEvent(p_sendPacket_1_, EventDirection.OUTGOING);

        Crow.eventBus.post(e);

        p_sendPacket_1_ = e.getPacket();
        if (e.isCancelled())
            ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void receivePacket(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_, CallbackInfo ci) {
        PacketEvent e = new PacketEvent(p_channelRead0_2_, EventDirection.INCOMING);

        Crow.eventBus.post(e);

        p_channelRead0_2_ = e.getPacket();
        if (e.isCancelled())
            ci.cancel();
    }

}
