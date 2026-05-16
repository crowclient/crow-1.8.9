package crow.client.module.modules.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.HotbarLayoutSetting;
import crow.client.module.setting.impl.HotbarLayoutSetting.HotbarSlotConfig;
import crow.client.module.setting.impl.HotbarLayoutSetting.SlotType;
import crow.client.module.setting.impl.HotbarLayoutSetting.SmartPreset;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

public class InvManager extends Module {

    private final DoubleSliderSetting startDelay;
    private final DoubleSliderSetting actionDelay;
    private final TickSetting autoSort;
    private final TickSetting dropTrash;
    private final TickSetting combineDuplicates;
    private final TickSetting keepBestTool;
    private final HotbarLayoutSetting hotbarLayout;

    private boolean inInv;
    private final CoolDown timer = new CoolDown(0);
    private final List<InvAction> actionQueue = new ArrayList<>();
    private boolean sorted;

    private static final int HOTBAR_START = 36;
    private static final int INV_START = 9;
    private static final int INV_END = 45;
    private static final int DEFAULT_MAX_BLOCK_STACKS = 3;
    private static final int DEFAULT_MAX_ARROW_STACKS = 1;

    private static final List<Item> TRASH = new ArrayList<>();
    static {
        TRASH.add(Items.rotten_flesh);
        TRASH.add(Items.poisonous_potato);
        TRASH.add(Items.spider_eye);
        TRASH.add(Items.bone);
        TRASH.add(Items.string);
        TRASH.add(Items.feather);
        TRASH.add(Items.wheat_seeds);
        TRASH.add(Items.flint);
        TRASH.add(Items.clay_ball);
        TRASH.add(Items.leather);
        TRASH.add(Items.stick);
        TRASH.add(Items.bowl);
    }

    public InvManager() {
        super("InvManager", ModuleCategory.player);
        this.registerSetting(startDelay = new DoubleSliderSetting("Start delay", 100, 250, 0, 500, 1));
        this.registerSetting(actionDelay = new DoubleSliderSetting("Action delay", 50, 150, 0, 500, 1));
        this.registerSetting(autoSort = new TickSetting("Auto sort", true));
        this.registerSetting(dropTrash = new TickSetting("Drop trash", true));
        this.registerSetting(combineDuplicates = new TickSetting("Stack", true));
        this.registerSetting(keepBestTool = new TickSetting("Best only", true));
        this.registerSetting(hotbarLayout = new HotbarLayoutSetting("Hotbar layout"));
    }

    @Override
    public void onDisable() {
        inInv = false;
        actionQueue.clear();
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        if (!Utils.Player.isPlayerInInventory()) {
            if (inInv) {
                inInv = false;
                actionQueue.clear();
                sorted = false;
            }
            return;
        }

        if (!(mc.thePlayer.openContainer instanceof ContainerPlayer)) return;
        ContainerPlayer inv = (ContainerPlayer) mc.thePlayer.openContainer;

        if (!inInv) {
            inInv = true;
            sorted = false;
            buildActionQueue(inv);
            timer.setCooldown(randomBetween(startDelay));
            timer.start();
        }

        if (!actionQueue.isEmpty() && timer.hasFinished()) {
            InvAction action = actionQueue.remove(0);
            executeAction(inv, action);
            timer.setCooldown(randomBetween(actionDelay));
            timer.start();
        }
    }

    private void buildActionQueue(ContainerPlayer inv) {
        actionQueue.clear();

        if (dropTrash.isToggled()) {
            for (int slot = INV_START; slot < INV_END; slot++) {
                ItemStack stack = inv.getSlot(slot).getStack();
                if (stack == null) continue;
                if (isTrash(stack)) {
                    actionQueue.add(new InvAction(ActionType.DROP, slot, -1));
                }
            }
        }

        if (keepBestTool.isToggled()) {
            dropDuplicateTools(inv, ItemSword.class);
            dropDuplicateTools(inv, ItemPickaxe.class);
            dropDuplicateTools(inv, ItemAxe.class);
            dropDuplicateBows(inv);
            dropExcessBlocks(inv);
            dropExcessArrows(inv);
        }

        if (autoSort.isToggled()) {
            sortHotbar(inv);
        }
    }

    private void sortHotbar(ContainerPlayer inv) {
        for (int i = 0; i < 9; i++) {
            HotbarSlotConfig config = hotbarLayout.getSlot(i);
            if (config.type == SlotType.EMPTY) continue;

            int targetSlot = HOTBAR_START + i;

            int sourceSlot = resolveSlotForConfig(inv, config,  -1);
            if (sourceSlot == -1) continue;
            if (sourceSlot == targetSlot) continue;

            actionQueue.add(new InvAction(ActionType.SWAP, sourceSlot, i));
        }
    }

    private boolean isCorrectItem(ItemStack stack, HotbarSlotConfig config) {
        if (stack == null) return false;

        switch (config.type) {
            case SMART_PRESET:
                return matchesPreset(stack, config.preset);
            case SPECIFIC_ITEM:
                if (config.itemRegistryName != null) {
                    Item target = Item.getByNameOrId(config.itemRegistryName);
                    return target != null && stack.getItem() == target;
                }
                return false;
            default:
                return true;
        }
    }

    private boolean matchesPreset(ItemStack stack, SmartPreset preset) {
        if (preset == null) return false;
        Item item = stack.getItem();
        switch (preset) {
            case BEST_SWORD:    return item instanceof ItemSword;
            case BEST_AXE:      return item instanceof ItemAxe;
            case BEST_PICKAXE:  return item instanceof ItemPickaxe;
            case BEST_SHOVEL:   return item instanceof ItemSpade;
            case BEST_BOW:      return item instanceof ItemBow;
            case BEST_ROD:      return item instanceof ItemFishingRod;
            case BEST_FOOD:     return item instanceof ItemFood;
            case GAPPLE:        return item == Items.golden_apple;
            case BLOCK:         return item instanceof ItemBlock;
            case PEARLS:        return item == Items.ender_pearl;
            case PROJECTILES:   return item == Items.snowball || item == Items.egg || item == Items.arrow || item == Items.fire_charge;
            default:            return false;
        }
    }

    private int resolveSlotForConfig(ContainerPlayer inv, HotbarSlotConfig config, int hotbarIdx) {
        if (config.type == SlotType.SMART_PRESET) {
            return findBestForPreset(inv, config.preset, hotbarIdx);
        } else if (config.type == SlotType.SPECIFIC_ITEM) {
            return findSpecificItem(inv, config.itemRegistryName, hotbarIdx);
        }
        return -1;
    }

    private int findBestForPreset(ContainerPlayer inv, SmartPreset preset, int excludeHotbarIdx) {
        int bestSlot = -1;
        float bestScore = -1;
        int excludeSlot = excludeHotbarIdx >= 0 ? HOTBAR_START + excludeHotbarIdx : -1;

        for (int slot = INV_START; slot < INV_END; slot++) {
            if (excludeSlot >= 0 && slot == excludeSlot) continue;
            ItemStack stack = inv.getSlot(slot).getStack();
            if (stack == null) continue;

            boolean matches = false;
            float score = 0;

            switch (preset) {
                case BEST_SWORD:
                    if (stack.getItem() instanceof ItemSword) {
                        matches = true;
                        score = getToolScore(stack);
                    }
                    break;
                case BEST_AXE:
                    if (stack.getItem() instanceof ItemAxe) {
                        matches = true;
                        score = getToolScore(stack);
                    }
                    break;
                case BEST_PICKAXE:
                    if (stack.getItem() instanceof ItemPickaxe) {
                        matches = true;
                        score = getToolScore(stack);
                    }
                    break;
                case BEST_SHOVEL:
                    if (stack.getItem() instanceof ItemSpade) {
                        matches = true;
                        score = getToolScore(stack);
                    }
                    break;
                case BEST_BOW:
                    if (stack.getItem() instanceof ItemBow) {
                        matches = true;
                        score = getToolScore(stack);
                    }
                    break;
                case BEST_ROD:
                    if (stack.getItem() instanceof ItemFishingRod) {
                        matches = true;
                        score = getToolScore(stack);
                    }
                    break;
                case BEST_FOOD:
                    if (stack.getItem() instanceof ItemFood) {
                        matches = true;
                        ItemFood food = (ItemFood) stack.getItem();
                        score = food.getHealAmount(stack);

                        if (stack.getItem() == Items.golden_apple) {
                            score += 100;
                            if (stack.getMetadata() == 1) score += 100;
                        }
                    }
                    break;
                case GAPPLE:
                    if (stack.getItem() == Items.golden_apple) {
                        matches = true;
                        score = stack.stackSize + (stack.getMetadata() == 1 ? 1000 : 0);
                    }
                    break;
                case BLOCK:
                    if (stack.getItem() instanceof ItemBlock) {
                        matches = true;
                        score = stack.stackSize;
                    }
                    break;
                case PEARLS:
                    if (stack.getItem() == Items.ender_pearl) {
                        matches = true;
                        score = stack.stackSize;
                    }
                    break;
                case PROJECTILES:
                    if (stack.getItem() == Items.snowball || stack.getItem() == Items.egg
                            || stack.getItem() == Items.arrow || stack.getItem() == Items.fire_charge) {
                        matches = true;
                        score = stack.stackSize;
                    }
                    break;
            }

            if (matches && score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private int findSpecificItem(ContainerPlayer inv, String registryName, int excludeHotbarIdx) {
        if (registryName == null) return -1;
        Item target = Item.getByNameOrId(registryName);
        if (target == null) return -1;

        int excludeSlot = excludeHotbarIdx >= 0 ? HOTBAR_START + excludeHotbarIdx : -1;

        if (excludeHotbarIdx < 0) {

            for (int slot = INV_START; slot < INV_END; slot++) {
                ItemStack stack = inv.getSlot(slot).getStack();
                if (stack != null && stack.getItem() == target) {
                    return slot;
                }
            }
            return -1;
        }
        for (int slot = INV_START; slot < INV_END; slot++) {
            if (slot == excludeSlot) continue;
            ItemStack stack = inv.getSlot(slot).getStack();
            if (stack != null && stack.getItem() == target) {
                return slot;
            }
        }
        return -1;
    }

    private void dropDuplicateTools(ContainerPlayer inv, Class<? extends Item> toolClass) {
        List<SlotScore> tools = new ArrayList<>();
        for (int slot = INV_START; slot < INV_END; slot++) {
            ItemStack stack = inv.getSlot(slot).getStack();
            if (stack == null) continue;
            if (toolClass.isInstance(stack.getItem())) {
                tools.add(new SlotScore(slot, getToolScore(stack)));
            }
        }

        if (tools.size() <= 1) return;
        tools.sort(Comparator.comparingDouble(s -> -s.score));

        for (int i = 1; i < tools.size(); i++) {
            actionQueue.add(new InvAction(ActionType.DROP, tools.get(i).slot, -1));
        }
    }

    private void dropDuplicateBows(ContainerPlayer inv) {
        List<SlotScore> bows = new ArrayList<>();
        for (int slot = INV_START; slot < INV_END; slot++) {
            ItemStack stack = inv.getSlot(slot).getStack();
            if (stack == null) continue;
            if (stack.getItem() instanceof ItemBow) {
                bows.add(new SlotScore(slot, getToolScore(stack)));
            }
        }
        if (bows.size() <= 1) return;
        bows.sort(Comparator.comparingDouble(s -> -s.score));
        for (int i = 1; i < bows.size(); i++) {
            actionQueue.add(new InvAction(ActionType.DROP, bows.get(i).slot, -1));
        }
    }

    private void dropExcessBlocks(ContainerPlayer inv) {
        int maxStacks = DEFAULT_MAX_BLOCK_STACKS;
        if (maxStacks <= 0) return;

        List<Integer> blockSlots = new ArrayList<>();
        for (int slot = INV_START; slot < INV_END; slot++) {
            ItemStack stack = inv.getSlot(slot).getStack();
            if (stack == null) continue;
            if (stack.getItem() instanceof ItemBlock) {
                blockSlots.add(slot);
            }
        }

        blockSlots.sort((a, b) -> {
            ItemStack sa = inv.getSlot(b).getStack();
            ItemStack sb = inv.getSlot(a).getStack();
            return (sa != null ? sa.stackSize : 0) - (sb != null ? sb.stackSize : 0);
        });

        for (int i = maxStacks; i < blockSlots.size(); i++) {
            actionQueue.add(new InvAction(ActionType.DROP, blockSlots.get(i), -1));
        }
    }

    private void dropExcessArrows(ContainerPlayer inv) {
        int maxStacks = DEFAULT_MAX_ARROW_STACKS;
        if (maxStacks <= 0) return;

        List<Integer> arrowSlots = new ArrayList<>();
        for (int slot = INV_START; slot < INV_END; slot++) {
            ItemStack stack = inv.getSlot(slot).getStack();
            if (stack == null) continue;
            if (stack.getItem() == Items.arrow) {
                arrowSlots.add(slot);
            }
        }

        arrowSlots.sort((a, b) -> {
            ItemStack sa = inv.getSlot(b).getStack();
            ItemStack sb = inv.getSlot(a).getStack();
            return (sa != null ? sa.stackSize : 0) - (sb != null ? sb.stackSize : 0);
        });

        for (int i = maxStacks; i < arrowSlots.size(); i++) {
            actionQueue.add(new InvAction(ActionType.DROP, arrowSlots.get(i), -1));
        }
    }

    private void executeAction(ContainerPlayer inv, InvAction action) {
        switch (action.type) {
            case DROP:

                mc.playerController.windowClick(inv.windowId, action.slot, 1, 4, mc.thePlayer);
                break;
            case SHIFT_CLICK:
                mc.playerController.windowClick(inv.windowId, action.slot, 0, 1, mc.thePlayer);
                break;
            case SWAP:

                mc.playerController.windowClick(inv.windowId, action.slot, action.targetHotbarIdx, 2, mc.thePlayer);
                break;
        }
    }

    private boolean isTrash(ItemStack stack) {
        Item item = stack.getItem();
        if (TRASH.contains(item)) return true;

        if (item instanceof ItemTool && stack.getItemDamage() >= stack.getMaxDamage() - 5) return true;
        return false;
    }

    private float getToolScore(ItemStack stack) {
        Item item = stack.getItem();
        float score = 0;

        if (item instanceof ItemSword) {
            ItemSword sword = (ItemSword) item;
            score = 5.0F + sword.getDamageVsEntity();
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) * 1.25F;
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack) * 0.5F;
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack) * 0.3F;
        } else if (item instanceof ItemTool) {
            ItemTool tool = (ItemTool) item;
            score = tool.getToolMaterial().getEfficiencyOnProperMaterial();
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack) * 0.8F;
        } else if (item instanceof ItemBow) {
            score = 5.0F;
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack) * 1.5F;
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack) * 0.3F;
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack) * 0.5F;
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, stack) * 2.0F;
        } else if (item instanceof ItemFishingRod) {
            score = 4.0F;
            score += EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack) * 0.35F;
        } else if (item instanceof ItemBlock) {
            score = stack.stackSize;
        } else if (item == Items.golden_apple) {
            score = stack.stackSize + (stack.getMetadata() == 1 ? 100 : 0);
        } else if (item instanceof ItemEnderPearl) {
            score = stack.stackSize;
        } else {
            score = 1;
        }

        if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
            float durability = 1.0F - ((float) stack.getItemDamage() / (float) stack.getMaxDamage());
            score += durability * 0.5F;
        }

        return score;
    }

    private long randomBetween(DoubleSliderSetting setting) {
        double min = setting.getInputMin();
        double max = setting.getInputMax();
        if (min >= max) return (long) min;
        return (long) ThreadLocalRandom.current().nextDouble(min, max + 0.01);
    }

    private static class SlotScore {
        final int slot;
        final float score;
        SlotScore(int slot, float score) { this.slot = slot; this.score = score; }
    }

    private enum ActionType { DROP, SHIFT_CLICK, SWAP }

    private static class InvAction {
        final ActionType type;
        final int slot;
        final int targetHotbarIdx;

        InvAction(ActionType type, int slot, int targetHotbarIdx) {
            this.type = type;
            this.slot = slot;
            this.targetHotbarIdx = targetHotbarIdx;
        }
    }
}
