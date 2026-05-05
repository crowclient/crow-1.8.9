package crow.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

public class FastPlace extends Module {
    public static SliderSetting delaySlider, projSlider;
    public static TickSetting blockOnly, projSeparate;

    public static final Field rightClickDelayTimerField;

    static {
        rightClickDelayTimerField = ReflectionHelper.findField(Minecraft.class, "field_71467_ac",
                "rightClickDelayTimer");

        if (rightClickDelayTimerField != null) {
            rightClickDelayTimerField.setAccessible(true);
        }
    }

    public FastPlace() {
        super("FastPlace", ModuleCategory.player);

        this.registerSetting(delaySlider = new SliderSetting("Delay", 0.0D, 0.0D, 4.0D, 1.0D));
        this.registerSetting(blockOnly = new TickSetting("Blocks only", true));
        this.registerSetting(projSeparate = new TickSetting("Separate Projectile Delay", true));
        this.registerSetting(projSlider = new SliderSetting("Projectile Delay", 2.0D, 0.0D, 4.0D, 1.0D));

    }

    @Override
    public boolean canBeEnabled() {
        return rightClickDelayTimerField != null;
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (Utils.Player.isPlayerInGame() && mc.inGameHasFocus && rightClickDelayTimerField != null) {
            if (blockOnly.isToggled()) {
                ItemStack item = mc.thePlayer.getHeldItem();
                if (item != null && (item.getItem() instanceof ItemBlock)) {
                    try {
                        int c = (int) delaySlider.getInput();
                        if (c == 0) {
                            rightClickDelayTimerField.set(mc, 0);
                        } else {
                            if (c == 4) {
                                return;
                            }

                            int d = rightClickDelayTimerField.getInt(mc);
                            if (d == 4) {
                                rightClickDelayTimerField.set(mc, c);
                            }
                        }
                    } catch (IllegalAccessException | IndexOutOfBoundsException ignored) {
                    }
                } else if (item != null && (item.getItem() instanceof ItemSnowball || item.getItem() instanceof ItemEgg)
                        && projSeparate.isToggled()) {
                    try {
                        int c = (int) projSlider.getInput();
                        if (c == 0) {
                            rightClickDelayTimerField.set(mc, 0);
                        } else {
                            if (c == 4) {
                                return;
                            }

                            int d = rightClickDelayTimerField.getInt(mc);
                            if (d == 4) {
                                rightClickDelayTimerField.set(mc, c);
                            }
                        }
                    } catch (IllegalAccessException | IndexOutOfBoundsException ignored) {
                    }
                }
            } else {
                try {
                    int c = (int) delaySlider.getInput();
                    if (c == 0) {
                        rightClickDelayTimerField.set(mc, 0);
                    } else {
                        if (c == 4) {
                            return;
                        }

                        int d = rightClickDelayTimerField.getInt(mc);
                        if (d == 4) {
                            rightClickDelayTimerField.set(mc, c);
                        }
                    }
                } catch (IllegalAccessException | IndexOutOfBoundsException ignored) {
                }
            }
        }
    }
}
