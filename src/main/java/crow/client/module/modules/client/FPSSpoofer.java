package crow.client.module.modules.client;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.GameLoopEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;

public class FPSSpoofer extends Module {
    public static DescriptionSetting desc;
    public static DoubleSliderSetting fps;

    public int ticksPassed;

    private final Field fpsField;

    public FPSSpoofer() {
        super("FPSSpoof", ModuleCategory.other);
        this.registerSetting(desc = new DescriptionSetting("Spoofs your fps"));
        this.registerSetting(fps = new DoubleSliderSetting("FPS", 99860, 100000, 0, 100000, 100));

        Field f = null;
        try {
            f = ReflectionHelper.findField(Minecraft.class, "field_71420_M", "fpsCounter");
            if (f != null) f.setAccessible(true);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        fpsField = f;
    }

    @Override
    public boolean canBeEnabled() {
        return fpsField != null;
    }

    public void onEnable() {
        ticksPassed = 0;
    }

    @Subscribe
    public void onGameLoop(GameLoopEvent e) {
        if (fpsField == null) return;
        try {
            int min = (int) fps.getInputMin();
            int max = (int) fps.getInputMax() + 1;
            if (max <= min) max = min + 1;
            int fpsN = ThreadLocalRandom.current().nextInt(min, max);
            fpsField.set(mc, fpsN);
        } catch (Throwable ex) {
            ex.printStackTrace();
            this.disable();
        }
    }

}
