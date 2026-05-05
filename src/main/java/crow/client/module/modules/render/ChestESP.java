package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import java.awt.*;
import java.util.Iterator;

public class ChestESP extends Module {
    public static SliderSetting red;
    public static SliderSetting green;
    public static SliderSetting blue;
    public static TickSetting rainbow;

    public ChestESP() {
        super("ChestESP", ModuleCategory.render);
        this.registerSetting(red = new SliderSetting("Red", 0.0D, 0.0D, 255.0D, 1.0D));
        this.registerSetting(green = new SliderSetting("Green", 0.0D, 0.0D, 255.0D, 1.0D));
        this.registerSetting(blue = new SliderSetting("Blue", 255.0D, 0.0D, 255.0D, 1.0D));
        this.registerSetting(rainbow = new TickSetting("Rainbow", false));
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (mc.currentScreen != null) {
            return;
        }
        if (fe.getEvent() instanceof RenderWorldLastEvent) {
            if (Utils.Player.isPlayerInGame()) {
                int rgb = rainbow.isToggled() ? Utils.Client.rainbowDraw(2L, 0L)
                        : (new Color((int) red.getInput(), (int) green.getInput(), (int) blue.getInput())).getRGB();
                Iterator var3 = mc.theWorld.loadedTileEntityList.iterator();

                while (true) {
                    TileEntity te;
                    do {
                        if (!var3.hasNext()) {
                            return;
                        }

                        te = (TileEntity) var3.next();
                    } while (!(te instanceof TileEntityChest) && !(te instanceof TileEntityEnderChest));

                    Utils.HUD.re(te.getPos(), rgb, true);
                }
            }
        }
    }
}
