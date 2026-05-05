package crow.client.module.setting.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import crow.client.clickgui.kv.KvComponent;
import crow.client.clickgui.kv.components.KvDescriptionComponent;
import crow.client.clickgui.crow.Component;
import crow.client.clickgui.crow.components.HotbarLayoutComponent;
import crow.client.clickgui.crow.components.ModuleComponent;
import crow.client.clickgui.crow.components.SettingComponent;
import crow.client.module.setting.Setting;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

public class HotbarLayoutSetting extends Setting {

    private final HotbarSlotConfig[] slots = new HotbarSlotConfig[9];

    public enum SlotType {
        EMPTY, SMART_PRESET, SPECIFIC_ITEM
    }

    public enum SmartPreset {
        BEST_SWORD("Best Sword"),
        BEST_AXE("Best Axe"),
        BEST_PICKAXE("Best Pickaxe"),
        BEST_SHOVEL("Best Shovel"),
        BEST_BOW("Best Bow"),
        BEST_ROD("Best Rod"),
        BEST_FOOD("Best Food"),
        GAPPLE("Gapple"),
        BLOCK("Block"),
        PEARLS("Pearls"),
        PROJECTILES("Projectiles");

        private final String displayName;

        SmartPreset(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class HotbarSlotConfig {
        public SlotType type;
        public SmartPreset preset;
        public String itemRegistryName;

        private HotbarSlotConfig() {
            this.type = SlotType.EMPTY;
        }

        public static HotbarSlotConfig empty() {
            return new HotbarSlotConfig();
        }

        public static HotbarSlotConfig ofPreset(SmartPreset preset) {
            HotbarSlotConfig c = new HotbarSlotConfig();
            c.type = SlotType.SMART_PRESET;
            c.preset = preset;
            return c;
        }

        public static HotbarSlotConfig ofItem(String registryName) {
            HotbarSlotConfig c = new HotbarSlotConfig();
            c.type = SlotType.SPECIFIC_ITEM;
            c.itemRegistryName = registryName;
            return c;
        }

        public String getDisplayLabel() {
            switch (type) {
                case SMART_PRESET:
                    return preset != null ? preset.getDisplayName() : "?";
                case SPECIFIC_ITEM:
                    if (itemRegistryName != null) {
                        Item item = Item.getByNameOrId(itemRegistryName);
                        if (item != null) {
                            return new ItemStack(item).getDisplayName();
                        }
                        return itemRegistryName;
                    }
                    return "?";
                default:
                    return "Empty";
            }
        }

        public Item getResolvedItem() {
            if (type == SlotType.SPECIFIC_ITEM && itemRegistryName != null) {
                return Item.getByNameOrId(itemRegistryName);
            }
            return null;
        }
    }

    public HotbarLayoutSetting(String name) {
        super(name);

        slots[0] = HotbarSlotConfig.ofPreset(SmartPreset.BEST_SWORD);
        slots[1] = HotbarSlotConfig.ofPreset(SmartPreset.BLOCK);
        slots[2] = HotbarSlotConfig.ofPreset(SmartPreset.GAPPLE);
        slots[3] = HotbarSlotConfig.empty();
        slots[4] = HotbarSlotConfig.ofPreset(SmartPreset.BEST_BOW);
        for (int i = 5; i < 9; i++) {
            slots[i] = HotbarSlotConfig.empty();
        }
    }

    public HotbarSlotConfig getSlot(int index) {
        if (index < 0 || index >= 9) return HotbarSlotConfig.empty();
        return slots[index];
    }

    public void setSlot(int index, HotbarSlotConfig config) {
        if (index >= 0 && index < 9) {
            slots[index] = config;
        }
    }

    @Override
    public void resetToDefaults() {
        slots[0] = HotbarSlotConfig.ofPreset(SmartPreset.BEST_SWORD);
        slots[1] = HotbarSlotConfig.ofPreset(SmartPreset.BLOCK);
        slots[2] = HotbarSlotConfig.ofPreset(SmartPreset.GAPPLE);
        slots[3] = HotbarSlotConfig.empty();
        slots[4] = HotbarSlotConfig.ofPreset(SmartPreset.BEST_BOW);
        for (int i = 5; i < 9; i++) {
            slots[i] = HotbarSlotConfig.empty();
        }
    }

    @Override
    public JsonObject getConfigAsJson() {
        JsonObject data = new JsonObject();
        data.addProperty("type", getSettingType());
        JsonArray arr = new JsonArray();
        for (int i = 0; i < 9; i++) {
            JsonObject slotObj = new JsonObject();
            HotbarSlotConfig cfg = slots[i];
            slotObj.addProperty("slotType", cfg.type.name());
            if (cfg.type == SlotType.SMART_PRESET && cfg.preset != null) {
                slotObj.addProperty("preset", cfg.preset.name());
            }
            if (cfg.type == SlotType.SPECIFIC_ITEM && cfg.itemRegistryName != null) {
                slotObj.addProperty("item", cfg.itemRegistryName);
            }
            arr.add(slotObj);
        }
        data.add("slots", arr);
        return data;
    }

    @Override
    public String getSettingType() {
        return "hotbar_layout";
    }

    @Override
    public void applyConfigFromJson(JsonObject data) {
        if (!data.has("type") || !data.get("type").getAsString().equals(getSettingType()))
            return;
        if (!data.has("slots")) return;

        JsonArray arr = data.getAsJsonArray("slots");
        for (int i = 0; i < Math.min(9, arr.size()); i++) {
            JsonObject slotObj = arr.get(i).getAsJsonObject();
            String slotTypeStr = slotObj.has("slotType") ? slotObj.get("slotType").getAsString() : "EMPTY";
            try {
                SlotType slotType = SlotType.valueOf(slotTypeStr);
                switch (slotType) {
                    case SMART_PRESET:
                        if (slotObj.has("preset")) {
                            try {
                                SmartPreset preset = SmartPreset.valueOf(slotObj.get("preset").getAsString());
                                slots[i] = HotbarSlotConfig.ofPreset(preset);
                            } catch (IllegalArgumentException e) {
                                slots[i] = HotbarSlotConfig.empty();
                            }
                        } else {
                            slots[i] = HotbarSlotConfig.empty();
                        }
                        break;
                    case SPECIFIC_ITEM:
                        if (slotObj.has("item")) {
                            slots[i] = HotbarSlotConfig.ofItem(slotObj.get("item").getAsString());
                        } else {
                            slots[i] = HotbarSlotConfig.empty();
                        }
                        break;
                    default:
                        slots[i] = HotbarSlotConfig.empty();
                        break;
                }
            } catch (IllegalArgumentException e) {
                slots[i] = HotbarSlotConfig.empty();
            }
        }
    }

    @Override
    public Component createComponent(ModuleComponent moduleComponent) {
        return null;
    }

    @Override
    public Class<? extends SettingComponent> getCrowComponentType() {
        return HotbarLayoutComponent.class;
    }

    @Override
    public Class<? extends KvComponent> getComponentType() {
        return KvDescriptionComponent.class;
    }

    private static List<Item> allItems;
    private static Map<String, List<Item>> categorizedItems;

    public static List<Item> getAllVanillaItems() {
        if (allItems == null) {
            allItems = new ArrayList<>();
            for (Object obj : Item.itemRegistry) {
                if (obj instanceof Item) {
                    allItems.add((Item) obj);
                }
            }
        }
        return allItems;
    }

    public static Map<String, List<Item>> getItemCategories() {
        if (categorizedItems == null) {
            categorizedItems = new LinkedHashMap<>();
            categorizedItems.put("Swords", new ArrayList<>());
            categorizedItems.put("Tools", new ArrayList<>());
            categorizedItems.put("Bows", new ArrayList<>());
            categorizedItems.put("Rods", new ArrayList<>());
            categorizedItems.put("Projectiles", new ArrayList<>());
            categorizedItems.put("Armor", new ArrayList<>());
            categorizedItems.put("Food", new ArrayList<>());
            categorizedItems.put("Blocks", new ArrayList<>());
            categorizedItems.put("Utility", new ArrayList<>());
            categorizedItems.put("Misc", new ArrayList<>());

            for (Item item : getAllVanillaItems()) {
                if (item instanceof ItemSword) {
                    categorizedItems.get("Swords").add(item);
                } else if (item instanceof ItemPickaxe || item instanceof ItemAxe
                        || item instanceof ItemSpade || item instanceof ItemHoe) {
                    categorizedItems.get("Tools").add(item);
                } else if (item instanceof ItemBow) {
                    categorizedItems.get("Bows").add(item);
                } else if (item instanceof ItemFishingRod) {
                    categorizedItems.get("Rods").add(item);
                } else if (item == Items.egg || item == Items.snowball || item == Items.fire_charge
                        || item == Items.arrow || item == Items.ender_pearl) {
                    categorizedItems.get("Projectiles").add(item);
                } else if (item instanceof ItemArmor) {
                    categorizedItems.get("Armor").add(item);
                } else if (item instanceof ItemFood) {
                    categorizedItems.get("Food").add(item);
                } else if (item instanceof ItemBlock) {
                    categorizedItems.get("Blocks").add(item);
                } else if (item == Items.golden_apple || item == Items.potionitem || item == Items.milk_bucket
                        || item == Items.water_bucket || item == Items.lava_bucket || item == Items.flint_and_steel
                        || item == Items.bucket || item == Items.compass || item == Items.clock) {
                    categorizedItems.get("Utility").add(item);
                } else {
                    categorizedItems.get("Misc").add(item);
                }
            }
        }
        return categorizedItems;
    }

    public static String getItemRegistryName(Item item) {
        Object name = Item.itemRegistry.getNameForObject(item);
        return name != null ? name.toString() : null;
    }
}
