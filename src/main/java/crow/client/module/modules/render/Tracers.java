package crow.client.module.modules.render;

import java.awt.Color;
import java.util.Iterator;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.modules.world.AntiBot;
import crow.client.module.setting.impl.RGBSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class Tracers extends Module {
    public static TickSetting showInvis;
    public static RGBSetting rgb;
    public static TickSetting rainbow;
    public static TickSetting redshift;
    public static SliderSetting lineWidth, distance;
    private boolean savedBobbing;
    private int rgb_c;

    public Tracers() {
        super("Tracers", ModuleCategory.render);
        this.registerSetting(showInvis = new TickSetting("Show invis", true));
        this.registerSetting(lineWidth = new SliderSetting("Line width", 1.0D, 1.0D, 5.0D, 1.0D));
        this.registerSetting(distance = new SliderSetting("Distance", 1.0D, 1.0D, 512.0D, 1.0D));
        this.registerSetting(rgb = new RGBSetting("Color", 0, 255, 0));
        this.registerSetting(rainbow = new TickSetting("Rainbow", false));
        this.registerSetting(redshift = new TickSetting("Redshift", false));
    }

    @Override
    public void onEnable() {
        this.savedBobbing = mc.gameSettings.viewBobbing;
        if (this.savedBobbing)
            mc.gameSettings.viewBobbing = false;
    }

    @Override
    public void onDisable() {
        mc.gameSettings.viewBobbing = this.savedBobbing;
    }

    @Subscribe
    public void onTick(TickEvent ev) {
        if (mc.gameSettings.viewBobbing)
            mc.gameSettings.viewBobbing = false;
    }

    @Override
    public void guiUpdate() {
        this.rgb_c = rgb.getRGB();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (mc.currentScreen != null) {
            return;
        }
        if (fe.getEvent() instanceof RenderWorldLastEvent)
            if (Utils.Player.isPlayerInGame()) {
                int rgb = rainbow.isToggled() ? Utils.Client.rainbowDraw(2L, 0L) : this.rgb_c;
                Iterator<EntityPlayer> var3 = mc.theWorld.playerEntities.iterator();

                while (true) {
                    EntityPlayer en;
                    do
                        do
                            do {
                                if (!var3.hasNext())
                                    return;

                                en = (EntityPlayer) var3.next();
                            } while (en == mc.thePlayer);
                        while (en.deathTime != 0);
                    while (!showInvis.isToggled() && en.isInvisible());

                    if (!AntiBot.renderBot(en))
                        if (redshift.isToggled() && (mc.thePlayer.getDistanceToEntity(en) < 25)) {
                            int r = (int) (Math.abs(mc.thePlayer.getDistanceToEntity(en) - 25) * 10);
                            int g = Math.abs(r - 255);
                            int rgbs = new Color(r, g, this.rgb.getBlue()).getRGB();
                            Utils.HUD.dtl(en, rgbs, (float) lineWidth.getInput());
                        } else
                            Utils.HUD.dtl(en, rgb, (float) lineWidth.getInput());
                }
            }
    }
}
