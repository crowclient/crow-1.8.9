package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.GameLoopEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

public class DelayRemover extends Module {
    public static DescriptionSetting desc;

    private final Field leftClickCounterField;

    public DelayRemover() {
        super("Delay Remover", ModuleCategory.combat);
        withEnabled(true);

        this.registerSetting(desc = new DescriptionSetting("Gives you 1.7 hitreg."));

        Field f = null;
        try {
            f = ReflectionHelper.findField(Minecraft.class, "field_71429_W", "leftClickCounter");
            if (f != null) f.setAccessible(true);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        this.leftClickCounterField = f;
    }

    @Override
    public boolean canBeEnabled() {
        return this.leftClickCounterField != null;
    }

    @Subscribe
    public void onGameLoop(GameLoopEvent event) {
        if (Utils.Player.isPlayerInGame() && this.leftClickCounterField != null) {
            if (!mc.inGameHasFocus || mc.thePlayer == null || mc.thePlayer.capabilities == null
                    || mc.thePlayer.capabilities.isCreativeMode) {
                return;
            }

            try {
                this.leftClickCounterField.set(mc, 0);
            } catch (Throwable ex) {
                ex.printStackTrace();
                this.disable();
            }
        }
    }
}
