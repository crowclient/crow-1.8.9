package crow.client.event.forge;

import crow.client.clickgui.crow.ClickGui;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.EarlyRender2DEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.utils.RenderUtils;
import crow.client.utils.SilentAim;
import crow.client.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public class ForgeEventListener {

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        try {
            if (e.phase == TickEvent.Phase.END) {
                if (Utils.Player.isPlayerInGame())
                    for (Module module : Crow.moduleManager.getModules())
                        if (Minecraft.getMinecraft().currentScreen instanceof ClickGui)
                            try { module.guiUpdate(); } catch (Throwable t) { logHandlerError("guiUpdate", module, t); }
            }
        } catch (Throwable t) {
            logHandlerError("onTick", null, t);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        try {
        if (e.phase == TickEvent.Phase.END) {
            if (Utils.Player.isPlayerInGame())
                for (Module module : Crow.moduleManager.getModules())
                    if (Minecraft.getMinecraft().currentScreen == null)
                        try { module.keybind(); } catch (Throwable t) { logHandlerError("keybind", module, t); }

            prepareHudRenderState();

            crow.client.utils.MSAAFramebuffer.begin();
            try {

                Crow.eventBus.post(new EarlyRender2DEvent());
                Crow.eventBus.post(new Render2DEvent());
            } finally {
                crow.client.utils.MSAAFramebuffer.end();

                GL11.glDisable(GL11.GL_BLEND);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                GL11.glEnable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableBlend();
                GlStateManager.disableBlend();
                GlStateManager.disableAlpha();
                GlStateManager.enableAlpha();
                GlStateManager.disableDepth();
                GlStateManager.enableDepth();
                GlStateManager.depthMask(false);
                GlStateManager.depthMask(true);
                GlStateManager.disableCull();
                GlStateManager.enableCull();
                GlStateManager.disableTexture2D();
                GlStateManager.enableTexture2D();
                GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0);
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        Crow.eventBus.post(new ForgeEvent(e));
        } catch (Throwable t) {
            logHandlerError("onRenderTick", null, t);
        }
    }

    private void prepareHudRenderState() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.entityRenderer.setupOverlayRendering();

        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        RenderUtils.syncAllGlState();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        try { Crow.eventBus.post(new ForgeEvent(e)); } catch (Throwable t) { logHandlerError("onClientTick", null, t); }
    }

    @SubscribeEvent
    public void onHit(AttackEntityEvent e) {
        try { Crow.eventBus.post(new ForgeEvent(e)); } catch (Throwable t) { logHandlerError("onHit", null, t); }
    }

    @SubscribeEvent
    public void onMouseUpdate(MouseEvent e) {
        try { Crow.eventBus.post(new ForgeEvent(e)); } catch (Throwable t) { logHandlerError("onMouseUpdate", null, t); }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        try { Crow.eventBus.post(new ForgeEvent(e)); } catch (Throwable t) { logHandlerError("onRenderWorldLast", null, t); }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent e) {
        try { Crow.eventBus.post(new ForgeEvent(e)); } catch (Throwable t) { logHandlerError("onLivingUpdate", null, t); }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent e) {
        try { Crow.eventBus.post(new ForgeEvent(e)); } catch (Throwable t) { logHandlerError("onEntityJoinWorld", null, t); }
    }

    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent e) {
        try { Crow.eventBus.post(new ForgeEvent(e)); } catch (Throwable t) { logHandlerError("onClientChatReceived", null, t); }
    }

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre e) {
        try { SilentAim.beginPlayerRender(e.entityPlayer); } catch (Throwable t) { logHandlerError("onRenderPlayerPre", null, t); }
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post e) {
        try { SilentAim.endPlayerRender(e.entityPlayer); } catch (Throwable t) { logHandlerError("onRenderPlayerPost", null, t); }
    }

    @SubscribeEvent
    public void onDrawBlockHighlight(DrawBlockHighlightEvent e) {
        try { Crow.eventBus.post(new ForgeEvent(e)); } catch (Throwable t) { logHandlerError("onDrawBlockHighlight", null, t); }
    }

    private static void logHandlerError(String where, Module module, Throwable t) {
        try {
            System.err.println("[Crow] ForgeEventListener." + where
                    + (module != null ? " (" + module.getClass().getSimpleName() + ")" : "")
                    + ": " + t);
            t.printStackTrace();
        } catch (Throwable ignored) {
        }
    }

}
