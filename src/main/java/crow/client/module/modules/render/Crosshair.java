package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

public class Crosshair extends Module {

    public enum CrosshairColor { White, Theme }

    public static ComboSetting<CrosshairColor> color;

    public Crosshair() {
        super("Crosshair", ModuleCategory.render);
        this.registerSetting(color = new ComboSetting("Color", CrosshairColor.White));
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

        ScaledResolution sr = crow.client.utils.RenderUtils.scaled();
        float cx = sr.getScaledWidth() / 2.0F;
        float cy = sr.getScaledHeight() / 2.0F;
        float length = 6.5F;
        float half = 1.0F;

        int rgb = color.getMode() == CrosshairColor.Theme
                ? (GuiModule.getThemeColor(0) & 0x00FFFFFF)
                : 0xF4F6FA;
        int col = 0xFF000000 | rgb;

        // Antialiased bars with rounded caps — same footprint as the old drawRect cross.
        RenderUtils.drawRoundedRectAA(cx - half, cy - length, cx + half, cy + length, half, col);
        RenderUtils.drawRoundedRectAA(cx - length, cy - half, cx + length, cy + half, half, col);
    }
}
