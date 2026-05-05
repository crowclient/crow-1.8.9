package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.utils.Utils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

public class Crosshair extends Module {
    public Crosshair() {
        super("Crosshair", ModuleCategory.render);
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent event) {
        if (!(event.getEvent() instanceof RenderGameOverlayEvent.Pre) || !Utils.Player.isPlayerInGame()) {
            return;
        }

        RenderGameOverlayEvent.Pre pre = (RenderGameOverlayEvent.Pre) event.getEvent();
        if (pre.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            pre.setCanceled(true);
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        if ((mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) || mc.gameSettings.showDebugInfo) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth() / 2;
        int cy = sr.getScaledHeight() / 2;
        int length = 6;
        int color = 0xFFF4F6FA;

        Gui.drawRect(cx - 1, cy - length, cx + 1, cy + length + 1, color);
        Gui.drawRect(cx - length, cy - 1, cx + length + 1, cy + 1, color);
    }
}
