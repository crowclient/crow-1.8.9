package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.clickgui.crow.ClickGui;
import crow.client.event.EventDirection;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.PacketEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.RGBSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;
import java.awt.Color;

public class ServerPos extends Module {
    private final RGBSetting rgb;
    private final TickSetting rainbow;

    private double serverX;
    private double serverY;
    private double serverZ;

    public ServerPos() {
        super("Server Pos", ModuleCategory.render);
        this.registerSetting(rgb = new RGBSetting("RGB", 0, 255, 0));
        this.registerSetting(rainbow = new TickSetting("Rainbow", false));
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            serverX = mc.thePlayer.posX;
            serverY = mc.thePlayer.posY;
            serverZ = mc.thePlayer.posZ;
        }
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (e.getDirection() == EventDirection.OUTGOING && !e.isCancelled()) {
            Packet<?> packet = e.getPacket();
            if (packet instanceof C03PacketPlayer) {
                C03PacketPlayer c03 = (C03PacketPlayer) packet;
                if (c03.isMoving()) {
                    serverX = c03.getPositionX();
                    serverY = c03.getPositionY();
                    serverZ = c03.getPositionZ();
                }
            }
        }
    }

    @Subscribe
    public void onRenderWorld(ForgeEvent fe) {
        if (fe.getEvent() instanceof RenderWorldLastEvent) {
            if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return;

            int color = rainbow.isToggled() ? ClickGui.getRainbowAtX(0) : new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue()).getRGB();
            float a = 0.25F;
            float r = (float) ((color >> 16) & 255) / 255.0F;
            float g = (float) ((color >> 8) & 255) / 255.0F;
            float b = (float) (color & 255) / 255.0F;

            double x = serverX - mc.getRenderManager().viewerPosX;
            double y = serverY - mc.getRenderManager().viewerPosY;
            double z = serverZ - mc.getRenderManager().viewerPosZ;

            AxisAlignedBB bbox = new AxisAlignedBB(
                    x - 0.3, y, z - 0.3,
                    x + 0.3, y + 1.8, z + 0.3
            );

            GlStateManager.pushMatrix();
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);

            Utils.HUD.dbb(bbox, r, g, b);
            GL11.glColor4f(r, g, b, 1.0F);
            net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(bbox);

            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }
}
