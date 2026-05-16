package crow.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemTool;
import net.minecraft.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ChestStealer extends Module {

    private final DoubleSliderSetting startDelay;
    private final DoubleSliderSetting stealDelay;
    private final DoubleSliderSetting closeDelay;
    private final TickSetting autoClose;
    private final TickSetting smartStealer;
    private final TickSetting ignoreTrash;
    private final ComboSetting stealMode;
    private final SliderSetting maxItems;
    private final TickSetting nameFilter;
    private final TickSetting onlyHotbar;

    public enum StealMode { Normal, Smart, Random, Column }

    private boolean inChest;
    private boolean skipContainer;
    private final CoolDown actionTimer = new CoolDown(0);
    private final CoolDown closeTimer = new CoolDown(0);

    private boolean closeTimerArmed;
    private List<Integer> stealQueue = new ArrayList<>();
    private ContainerChest currentChest;
    private int itemsStolen;
    private long chestOpenedAt;

    private static final List<Item> TRASH_ITEMS = Arrays.asList(
            Items.rotten_flesh, Items.poisonous_potato, Items.spider_eye,
            Items.bone, Items.string, Items.feather, Items.wheat_seeds,
            Items.flint, Items.clay_ball, Items.leather
    );

    public ChestStealer() {
        super("ChestStealer", ModuleCategory.player);
        this.registerSetting(stealMode = new ComboSetting("Steal mode", StealMode.Smart));
        this.registerSetting(startDelay = new DoubleSliderSetting("Start delay", 80, 200, 0, 500, 1));
        this.registerSetting(stealDelay = new DoubleSliderSetting("Steal delay", 50, 150, 0, 500, 1));
        this.registerSetting(closeDelay = new DoubleSliderSetting("Close delay", 50, 200, 0, 500, 1));
        this.registerSetting(autoClose = new TickSetting("Auto close", true));
        this.registerSetting(smartStealer = new TickSetting("Smart steal", true));
        this.registerSetting(ignoreTrash = new TickSetting("No trash", true));
        this.registerSetting(nameFilter = new TickSetting("Skip named", false));
        this.registerSetting(onlyHotbar = new TickSetting("Hotbar only", false));
        this.registerSetting(maxItems = new SliderSetting("Max items", 0, 0, 54, 1));
    }

    @Override
    public String getHudSuffix() {
        return toTitleCase(((StealMode) stealMode.getMode()).name());
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;

        if (Utils.Player.isPlayerInChest()) {
            ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;

            if (!inChest) {

                currentChest = chest;
                inChest = true;
                skipContainer = false;
                itemsStolen = 0;
                chestOpenedAt = System.currentTimeMillis();
                closeTimerArmed = false;

                String chestName = chest.getLowerChestInventory().getDisplayName().getUnformattedText();

                if (isGameMenu(chest, chestName)) {
                    skipContainer = true;
                    return;
                }

                if (nameFilter.isToggled()) {
                    if (!chestName.equals("Chest") && !chestName.equals("Large Chest")) {
                        skipContainer = true;
                        return;
                    }
                }

                buildStealQueue(chest);
                actionTimer.setCooldown(randomBetween(startDelay));
                actionTimer.start();
            }

            if (skipContainer) return;

            if (!stealQueue.isEmpty() && actionTimer.hasFinished()) {

                if ((int) maxItems.getInput() > 0 && itemsStolen >= (int) maxItems.getInput()) {
                    stealQueue.clear();
                } else {
                    int slotIndex = stealQueue.remove(0);

                    if (slotIndex < chest.getLowerChestInventory().getSizeInventory()
                            && chest.getLowerChestInventory().getStackInSlot(slotIndex) != null) {
                        if (onlyHotbar.isToggled()) {

                            int hotbarSlot = findEmptyHotbar(chest);
                            if (hotbarSlot != -1) {
                                mc.playerController.windowClick(chest.windowId, slotIndex, hotbarSlot, 2, mc.thePlayer);
                            }
                        } else {
                            mc.playerController.windowClick(chest.windowId, slotIndex, 0, 1, mc.thePlayer);
                        }
                        itemsStolen++;
                    }
                    actionTimer.setCooldown(randomBetween(stealDelay));
                    actionTimer.start();
                }
            }

            if (stealQueue.isEmpty() && autoClose.isToggled() && inChest) {
                if (!closeTimerArmed) {
                    closeTimer.setCooldown(randomBetween(closeDelay));
                    closeTimer.start();
                    closeTimerArmed = true;
                } else if (closeTimer.hasFinished()) {
                    mc.thePlayer.closeScreen();
                    inChest = false;
                    closeTimerArmed = false;
                }
            }
        } else {
            inChest = false;
            skipContainer = false;
            stealQueue.clear();
            closeTimerArmed = false;
        }
    }

    private static final String[] MENU_KEYWORDS = {
        "menu", "selector", "select", "lobby", "shop", "store", "warp",
        "teleport", "kit", "cosmetic", "profile", "stat", "setting",
        "play", "game", "reward", "quest", "mission", "daily", "auction",
        "bazaar", "trade", "upgrade", "ability", "skill", "level", "rank",
        "booster", "event", "challenge", "tournament", "party", "friend",
        "guild", "clan", "team", "leaderboard", "scoreboard", "help",
        "info", "portal", "navigator", "compass", "hub"
    };

    private boolean isGameMenu(ContainerChest chest, String displayName) {
        String lower = displayName.toLowerCase();

        for (String keyword : MENU_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }

        int chestSize = chest.getLowerChestInventory().getSizeInventory();
        Item glassPaneItem = Item.getItemFromBlock(Blocks.stained_glass_pane);
        Item plainGlassPaneItem = Item.getItemFromBlock(Blocks.glass_pane);
        int totalItems = 0;
        int glassPanes = 0;
        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = chest.getLowerChestInventory().getStackInSlot(i);
            if (stack == null) continue;
            totalItems++;
            Item item = stack.getItem();
            if (item == glassPaneItem || item == plainGlassPaneItem) glassPanes++;
        }

        if (totalItems > 0 && glassPanes * 100 / totalItems > 30) return true;

        if (totalItems >= 3) {
            int namedItems = 0;
            for (int i = 0; i < chestSize; i++) {
                ItemStack stack = chest.getLowerChestInventory().getStackInSlot(i);
                if (stack != null && stack.hasDisplayName()) namedItems++;
            }
            if (namedItems == totalItems) return true;
        }

        return false;
    }

    private void buildStealQueue(ContainerChest chest) {
        int chestSize = chest.getLowerChestInventory().getSizeInventory();
        List<SlotEntry> candidates = new ArrayList<>();

        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = chest.getLowerChestInventory().getStackInSlot(i);
            if (stack == null) continue;

            if (smartStealer.isToggled() && !isUsefulItem(stack)) continue;
            if (ignoreTrash.isToggled() && TRASH_ITEMS.contains(stack.getItem())) continue;

            candidates.add(new SlotEntry(i, chestSize));
        }

        StealMode mode = (StealMode) stealMode.getMode();
        switch (mode) {
            case Smart:
                candidates = sortByNearestNeighbor(candidates);
                break;
            case Random:
                Collections.shuffle(candidates);
                break;
            case Column:
                candidates.sort(Comparator.comparingInt(a -> a.col));
                break;
            case Normal:
            default:

                break;
        }

        stealQueue.clear();
        for (SlotEntry entry : candidates) {
            stealQueue.add(entry.slotIndex);
        }
    }

    private boolean isUsefulItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemSword)      return true;
        if (item instanceof ItemBow)        return true;
        if (item instanceof ItemTool)       return true;
        if (item instanceof ItemArmor)      return true;
        if (item instanceof ItemEnderPearl) return true;
        if (item instanceof ItemFood)       return true;
        if (item instanceof ItemBlock)      return true;
        if (item instanceof ItemPotion) {

            List<PotionEffect> effects = Items.potionitem.getEffects(stack);
            if (effects != null) {
                for (PotionEffect effect : effects) {
                    int id = effect.getPotionID();
                    if (id == 6 || id == 1 || id == 5 || id == 10 || id == 11 || id == 8) {
                        return true;
                    }
                }
            }
            return false;
        }

        if (item == Items.golden_apple)      return true;
        if (item == Items.arrow)             return true;
        if (item == Items.water_bucket)      return true;
        if (item == Items.lava_bucket)       return true;
        if (item == Items.milk_bucket)       return true;
        if (item == Items.flint_and_steel)   return true;
        if (item == Items.fishing_rod)       return true;
        if (item == Items.snowball)          return true;
        if (item == Items.egg)               return true;
        if (item == Items.fire_charge)       return true;
        if (item == Items.experience_bottle) return true;
        if (item == Items.ender_eye)         return true;
        if (item == Items.iron_ingot)        return true;
        if (item == Items.gold_ingot)        return true;
        if (item == Items.diamond)           return true;
        if (item == Items.emerald)           return true;
        if (item == Items.redstone)          return true;
        if (item == Items.dye)               return true;
        if (item == Items.coal)              return true;
        if (item == Items.glowstone_dust)    return true;
        if (item == Items.gunpowder)         return true;
        if (item == Items.blaze_powder)      return true;
        if (item == Items.blaze_rod)         return true;
        if (item == Items.sugar)             return true;
        if (item == Items.slime_ball)        return true;
        if (item == Items.stick)             return true;
        if (item == Items.string)            return true;
        if (item == Items.gold_nugget)       return true;
        if (item == Items.spider_eye)        return true;
        if (item == Items.fermented_spider_eye) return true;
        if (item == Items.glass_bottle)      return true;
        if (item == Items.bone)              return true;
        if (item == Items.feather)           return true;
        if (item == Items.leather)           return true;
        if (item == Items.book)              return true;
        if (item == Items.enchanted_book)    return true;
        if (item == Items.name_tag)          return true;
        if (item == Items.nether_star)       return true;
        if (item == Items.ghast_tear)        return true;
        if (item == Items.magma_cream)       return true;
        if (item == Items.iron_horse_armor)  return true;
        if (item == Items.golden_horse_armor)return true;
        if (item == Items.diamond_horse_armor) return true;
        if (item == Items.saddle)            return true;
        return false;
    }

    private List<SlotEntry> sortByNearestNeighbor(List<SlotEntry> entries) {
        if (entries.isEmpty()) return entries;

        List<SlotEntry> result = new ArrayList<>();
        List<SlotEntry> remaining = new ArrayList<>(entries);

        SlotEntry current = remaining.remove(ThreadLocalRandom.current().nextInt(remaining.size()));
        result.add(current);

        while (!remaining.isEmpty()) {
            final SlotEntry cur = current;
            SlotEntry nearest = remaining.stream()
                    .min(Comparator.comparingDouble(s -> s.distanceTo(cur)))
                    .get();
            remaining.remove(nearest);
            result.add(nearest);
            current = nearest;
        }

        return result;
    }

    private int findEmptyHotbar(ContainerChest chest) {
        int chestSize = chest.getLowerChestInventory().getSizeInventory();

        for (int i = 0; i < 9; i++) {
            int containerSlot = chestSize + 27 + i;
            if (chest.getSlot(containerSlot).getStack() == null) {
                return i;
            }
        }
        return -1;
    }

    private long randomBetween(DoubleSliderSetting setting) {
        double min = setting.getInputMin();
        double max = setting.getInputMax();
        if (min >= max) return (long) min;
        return (long) ThreadLocalRandom.current().nextDouble(min, max + 0.01);
    }

    private static class SlotEntry {
        final int slotIndex;
        final int row;
        final int col;

        SlotEntry(int slotIndex, int chestSize) {
            this.slotIndex = slotIndex;
            int cols = 9;
            this.row = slotIndex / cols;
            this.col = slotIndex % cols;
        }

        double distanceTo(SlotEntry other) {
            return Math.abs(this.row - other.row) + Math.abs(this.col - other.col);
        }
    }
}
