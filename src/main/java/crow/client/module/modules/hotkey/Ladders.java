package crow.client.module.modules.hotkey;

import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.item.ItemStack;

public class Ladders extends Module {
    private final TickSetting preferSlot;
    private final SliderSetting hotbarSlotPreference;

    public Ladders() {
        super("Ladders", ModuleCategory.hotkey);

        this.registerSetting(preferSlot = new TickSetting("Prefer slot", false));
        this.registerSetting(hotbarSlotPreference = new SliderSetting("Slot", 8, 1, 9, 1));
    }

    @Override
    public void onEnable() {
        if (!Utils.Player.isPlayerInGame())
            return;

        if (preferSlot.isToggled()) {
            int preferedSlot = (int) hotbarSlotPreference.getInput() - 1;

            if (checkSlot(preferedSlot)) {
                mc.thePlayer.inventory.currentItem = preferedSlot;
                this.disable();
                return;
            }
        }

        for (int slot = 0; slot <= 8; slot++) {
            if (checkSlot(slot)) {
                if (mc.thePlayer.inventory.currentItem != slot) {
                    mc.thePlayer.inventory.currentItem = slot;
                } else {
                    return;
                }
                this.disable();
                return;
            }
        }
        this.disable();
    }

    public static boolean checkSlot(int slot) {
        ItemStack itemInSlot = mc.thePlayer.inventory.getStackInSlot(slot);
        if (itemInSlot == null)
            return false;

        return itemInSlot != null && itemInSlot.getDisplayName().equalsIgnoreCase("ladder");
    }
}
