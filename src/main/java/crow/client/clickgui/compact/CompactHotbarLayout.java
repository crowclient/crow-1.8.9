package crow.client.clickgui.compact;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.HotbarLayoutSetting;
import crow.client.module.setting.impl.HotbarLayoutSetting.HotbarSlotConfig;
import crow.client.module.setting.impl.HotbarLayoutSetting.SlotType;
import crow.client.module.setting.impl.HotbarLayoutSetting.SmartPreset;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class CompactHotbarLayout {

    private static final int CELL_SIZE = 22;
    private static final int CELL_GAP = 3;
    private static final int LABEL_H = 14;
    private static final int PICKER_MAX_W = 252;
    private static final int PICKER_H = 292;
    private static final int PRESET_BTN_H = 22;
    private static final int ITEM_CELL = 20;
    private static final int ITEMS_PER_ROW = 9;
    private static final int PICKER_MARGIN = 6;
    private static final ResourceLocation SWORD_ICON = RenderUtils.getResourcePath("/assets/crow/invmanager/sword.png");
    private static final ResourceLocation AXE_ICON = RenderUtils.getResourcePath("/assets/crow/invmanager/ax.png");
    private static final ResourceLocation PICKAXE_ICON = RenderUtils.getResourcePath("/assets/crow/invmanager/pickaxe.png");
    private static final ResourceLocation SHOVEL_ICON = RenderUtils.getResourcePath("/assets/crow/invmanager/shovel.png");
    private static final ResourceLocation BOW_ICON = RenderUtils.getResourcePath("/assets/crow/invmanager/bow.png");
    private static final ResourceLocation BLOCK_ICON = RenderUtils.getResourcePath("/assets/crow/invmanager/block.png");

    private final HotbarLayoutSetting setting;
    int x, y, w, h;
    private int pickerSlot = -1;
    private String searchText = "";

    public CompactHotbarLayout(HotbarLayoutSetting setting) {
        this.setting = setting;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public int getHeight() {
        return getBaseHeight() + (pickerSlot >= 0 ? PICKER_H + PICKER_MARGIN : 0);
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {

        FontUtil.semiBold.drawSmoothString(setting.getName(), x, y + 2, palette.titleText);

        int cellsY = y + LABEL_H;
        int totalW = 9 * CELL_SIZE + 8 * CELL_GAP;
        int cellsX = x;

        for (int i = 0; i < 9; i++) {
            int cx = cellsX + i * (CELL_SIZE + CELL_GAP);
            int cy = cellsY;
            boolean hovered = mouseX >= cx && mouseX < cx + CELL_SIZE
                    && mouseY >= cy && mouseY < cy + CELL_SIZE;
            boolean selected = pickerSlot == i;

            int bgColor = selected ? GuiModule.accent() : (hovered ? palette.hoverCard : palette.toggleOff);
            RenderUtils.drawRoundedRectAA(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, 5, bgColor);

            if (selected) {
                RenderUtils.drawRoundedOutline(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, 5, 1.0F, GuiModule.accent());
            }

            HotbarSlotConfig config = setting.getSlot(i);
            drawSlotContent(cx, cy, config, palette);

            FontUtil.small.drawSmoothString(String.valueOf(i + 1), cx + 2, cy + CELL_SIZE - 8, 0x55FFFFFF);
        }

        if (pickerSlot >= 0) {
            drawPicker(mouseX, mouseY, cellsX, cellsY, palette);
        }
    }

    private void drawSlotContent(int cx, int cy, HotbarSlotConfig config, CompactPalette palette) {
        if (config.type == SlotType.EMPTY) {
            FontUtil.small.drawSmoothString("-", cx + 8, cy + 6, 0x44FFFFFF);
            return;
        }

        if (config.type == SlotType.SMART_PRESET) {
            if (drawPresetIcon(config.preset, cx + 4, cy + 4, 14, 14)) {
                return;
            }
            String label = getPresetShortLabel(config.preset);
            float labelW = (float) FontUtil.small.getStringWidth(label);
            FontUtil.small.drawSmoothString(label, cx + (CELL_SIZE - labelW) / 2.0F, cy + 6, getPresetAccent(config.preset));
            return;
        }

        if (config.type == SlotType.SPECIFIC_ITEM) {
            Item item = config.getResolvedItem();
            if (item != null) {
                renderItemIcon(cx + 3, cy + 3, new ItemStack(item));
            } else {
                FontUtil.small.drawSmoothString("?", cx + 8, cy + 6, 0xFFFF6666);
            }
        }
    }

    private String getPresetShortLabel(SmartPreset preset) {
        if (preset == null) return "?";
        switch (preset) {
            case BEST_SWORD:   return "Sw";
            case BEST_AXE:     return "Ax";
            case BEST_PICKAXE: return "Pk";
            case BEST_SHOVEL:  return "Sh";
            case BEST_BOW:     return "Bw";
            case BEST_ROD:     return "Rd";
            case BEST_FOOD:    return "Fd";
            case GAPPLE:       return "Gp";
            case BLOCK:        return "Bl";
            case PEARLS:       return "Pr";
            case PROJECTILES:  return "Pt";
            default:           return "?";
        }
    }

    private void drawPicker(int mouseX, int mouseY, int cellsX, int cellsY, CompactPalette palette) {
        int px = cellsX;
        int py = y + getBaseHeight() + PICKER_MARGIN;
        int panelW = getPickerWidth();
        int presetButtonW = (panelW - 8 - 8 - 4) / 2;

        RenderUtils.drawGlassPanel(px, py, px + panelW, py + PICKER_H, 10,
                palette.content, RenderUtils.GLASS_SHADOW_RAISED);
        RenderUtils.drawFlowingGradientRoundedRect(px + 1, py + 1, px + panelW - 1, py + 3, 9, 24, 0);

        int curY = py + 8;

        FontUtil.small.drawSmoothString("Slot " + (pickerSlot + 1) + " setup", px + 10, curY, palette.titleText);
        FontUtil.small.drawSmoothString("Pick a best-item rule or specific item", px + panelW - 10 - (int) FontUtil.small.getStringWidth("Pick a best-item rule or specific item"),
                curY, palette.mutedText);
        curY += 16;

        RenderUtils.drawRoundedRectAA(px + 8, curY, px + panelW - 8, curY + 20, 7, palette.card);
        String displaySearch = searchText.isEmpty() ? "Search items..." : searchText;
        int searchColor = searchText.isEmpty() ? palette.mutedText : palette.titleText;
        FontUtil.small.drawSmoothString(displaySearch, px + 14, curY + 6, searchColor);
        curY += 26;

        FontUtil.small.drawSmoothString("Best Item Rules", px + 8, curY, palette.mutedText);
        curY += 14;

        SmartPreset[] presets = getVisiblePresets();
        for (int i = 0; i < presets.length; i += 2) {
            for (int j = 0; j < 2 && i + j < presets.length; j++) {
                int bx = px + 8 + j * (presetButtonW + 4);
                int by = curY;
                SmartPreset p = presets[i + j];
                boolean hovered = mouseX >= bx && mouseX < bx + presetButtonW
                        && mouseY >= by && mouseY < by + PRESET_BTN_H;

                int btnColor = hovered
                        ? CompactModuleCard.blendColor(palette.toggleOff, palette.hoverCard, 0.72F)
                        : CompactModuleCard.blendColor(palette.toggleOff, palette.card, 0.38F);
                RenderUtils.drawRoundedRectAA(bx, by, bx + presetButtonW, by + PRESET_BTN_H, 8, btnColor);
                int accent = getPresetAccent(p);
                Gui.drawRect(bx + 1, by + 5, bx + 3, by + PRESET_BTN_H - 5, accent);
                boolean drewIcon = drawPresetIcon(p, bx + 8, by + 4, 14, 14);
                int textX = bx + (drewIcon ? 26 : 8);
                FontUtil.small.drawSmoothString(p.getDisplayName(), textX, by + 7, palette.titleText);
            }
            curY += PRESET_BTN_H + 3;
        }

        boolean emptyHovered = mouseX >= px + 8 && mouseX < px + 8 + presetButtonW
                && mouseY >= curY && mouseY < curY + PRESET_BTN_H;
        int emptyColor = emptyHovered
                ? CompactModuleCard.blendColor(palette.toggleOff, palette.hoverCard, 0.6F)
                : CompactModuleCard.blendColor(palette.toggleOff, palette.card, 0.38F);
        RenderUtils.drawRoundedRectAA(px + 8, curY, px + 8 + presetButtonW, curY + PRESET_BTN_H, 8, emptyColor);
        FontUtil.small.drawSmoothString("Empty", px + 14, curY + 5, palette.titleText);
        curY += PRESET_BTN_H + 8;

        Gui.drawRect(px + 8, curY, px + panelW - 8, curY + 1, palette.separator);
        curY += 6;

        String lowerSearch = searchText.toLowerCase();
        Map<String, List<Item>> categories = HotbarLayoutSetting.getItemCategories();

        for (Map.Entry<String, List<Item>> entry : categories.entrySet()) {
            List<Item> filtered = filterItems(entry.getValue(), lowerSearch);
            if (filtered.isEmpty()) continue;
            if (curY >= py + PICKER_H - 14) break;

            FontUtil.small.drawSmoothString(entry.getKey(), px + 8, curY, palette.mutedText);
            String countText = filtered.size() + " items";
            FontUtil.small.drawSmoothString(countText,
                    px + panelW - 8 - (int) FontUtil.small.getStringWidth(countText), curY, 0x88FFFFFF);
            curY += 12;

            int col = 0;
            for (Item item : filtered) {
                if (curY >= py + PICKER_H - ITEM_CELL) break;

                int ix = px + 8 + col * (ITEM_CELL + 2);
                int iy = curY;
                boolean itemHovered = mouseX >= ix && mouseX < ix + ITEM_CELL
                        && mouseY >= iy && mouseY < iy + ITEM_CELL;

                if (itemHovered) {
                    RenderUtils.drawRoundedRectAA(ix, iy, ix + ITEM_CELL, iy + ITEM_CELL, 3,
                            CompactModuleCard.blendColor(palette.card, GuiModule.accent(), 0.25F));

                    ItemStack stack = new ItemStack(item);
                    String name = stack.getDisplayName();
                    int tw = (int) FontUtil.small.getStringWidth(name) + 10;
                    RenderUtils.drawGlassPanel(mouseX + 10, mouseY - 14, mouseX + 10 + tw, mouseY - 1, 5,
                            palette.card, RenderUtils.GLASS_SHADOW_RAISED);
                    FontUtil.small.drawSmoothString(name, mouseX + 15, mouseY - 12, 0xFFFFFFFF);
                }

                renderItemIcon(ix + 2, iy + 2, new ItemStack(item));
                col++;
                if (col >= ITEMS_PER_ROW) {
                    col = 0;
                    curY += ITEM_CELL + 2;
                }
            }
            if (col > 0) curY += ITEM_CELL + 3;
            curY += 2;
        }
    }

    private int getBaseHeight() {
        return LABEL_H + CELL_SIZE + 8;
    }

    private int getPickerWidth() {
        return Math.min(PICKER_MAX_W, Math.max(212, w));
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        int cellsY = y + LABEL_H;
        int cellsX = x;

        if (pickerSlot >= 0) {
            if (handlePickerClick(mouseX, mouseY, cellsX, cellsY)) {
                return true;
            }
        }

        for (int i = 0; i < 9; i++) {
            int cx = cellsX + i * (CELL_SIZE + CELL_GAP);
            int cy = cellsY;
            if (mouseX >= cx && mouseX < cx + CELL_SIZE && mouseY >= cy && mouseY < cy + CELL_SIZE) {
                if (pickerSlot == i) {
                    pickerSlot = -1;
                } else {
                    pickerSlot = i;
                    searchText = "";
                }
                return true;
            }
        }

        if (pickerSlot >= 0) {
            pickerSlot = -1;
            return true;
        }

        return false;
    }

    private boolean handlePickerClick(int mouseX, int mouseY, int cellsX, int cellsY) {
        int px = cellsX;
        int py = y + getBaseHeight() + PICKER_MARGIN;
        int panelW = getPickerWidth();
        int presetButtonW = (panelW - 8 - 8 - 4) / 2;

        if (mouseX < px || mouseX > px + panelW || mouseY < py || mouseY > py + PICKER_H) {
            return false;
        }

        int curY = py + 8;
        curY += 16;

        if (mouseY < curY + 20) return true;
        curY += 26;

        curY += 14;

        SmartPreset[] presets = getVisiblePresets();
        for (int i = 0; i < presets.length; i += 2) {
            for (int j = 0; j < 2 && i + j < presets.length; j++) {
                int bx = px + 8 + j * (presetButtonW + 4);
                int by = curY;
                if (mouseX >= bx && mouseX < bx + presetButtonW
                        && mouseY >= by && mouseY < by + PRESET_BTN_H) {
                    setting.setSlot(pickerSlot, HotbarSlotConfig.ofPreset(presets[i + j]));
                    pickerSlot = -1;
                    return true;
                }
            }
            curY += PRESET_BTN_H + 3;
        }

        if (mouseX >= px + 8 && mouseX < px + 8 + presetButtonW
                && mouseY >= curY && mouseY < curY + PRESET_BTN_H) {
            setting.setSlot(pickerSlot, HotbarSlotConfig.empty());
            pickerSlot = -1;
            return true;
        }
        curY += PRESET_BTN_H + 8;
        curY += 7;

        String lowerSearch = searchText.toLowerCase();
        Map<String, List<Item>> categories = HotbarLayoutSetting.getItemCategories();
        for (Map.Entry<String, List<Item>> entry : categories.entrySet()) {
            List<Item> filtered = filterItems(entry.getValue(), lowerSearch);
            if (filtered.isEmpty()) continue;
            if (curY >= py + PICKER_H - 14) break;

            curY += 12;

            int col = 0;
            for (Item item : filtered) {
                if (curY >= py + PICKER_H - ITEM_CELL) break;

                int ix = px + 8 + col * (ITEM_CELL + 2);
                int iy = curY;
                if (mouseX >= ix && mouseX < ix + ITEM_CELL
                        && mouseY >= iy && mouseY < iy + ITEM_CELL) {
                    String regName = HotbarLayoutSetting.getItemRegistryName(item);
                    if (regName != null) {
                        setting.setSlot(pickerSlot, HotbarSlotConfig.ofItem(regName));
                        pickerSlot = -1;
                        return true;
                    }
                }

                col++;
                if (col >= ITEMS_PER_ROW) {
                    col = 0;
                    curY += ITEM_CELL + 2;
                }
            }
            if (col > 0) curY += ITEM_CELL + 3;
            curY += 2;
        }

        return true;
    }

    public void keyTyped(char t, int k) {
        if (pickerSlot < 0) return;

        if (k == 14) {
            if (!searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
            }
        } else if (k == 1) {
            pickerSlot = -1;
        } else if (t >= 32 && t < 127) {
            searchText += t;
        }
    }

    public boolean isPickerOpen() {
        return pickerSlot >= 0;
    }

    private SmartPreset[] getVisiblePresets() {
        return new SmartPreset[] {
                SmartPreset.BEST_SWORD,
                SmartPreset.BEST_AXE,
                SmartPreset.BEST_PICKAXE,
                SmartPreset.BEST_SHOVEL,
                SmartPreset.BEST_BOW,
                SmartPreset.BEST_ROD,
                SmartPreset.BEST_FOOD,
                SmartPreset.GAPPLE,
                SmartPreset.BLOCK,
                SmartPreset.PEARLS,
                SmartPreset.PROJECTILES
        };
    }

    private int getPresetAccent(SmartPreset preset) {
        if (preset == null) {
            return 0xFFFFFFFF;
        }
        switch (preset) {
            case BEST_SWORD:   return 0xFFFF8CA8;
            case BEST_AXE:     return 0xFFFFB469;
            case BEST_PICKAXE: return 0xFF6FC4FF;
            case BEST_SHOVEL:  return 0xFF8FD1A1;
            case BEST_BOW:     return 0xFFCDA2FF;
            case BEST_ROD:     return 0xFF6EE0D0;
            case BEST_FOOD:    return 0xFFFFD66E;
            case GAPPLE:       return 0xFFFFD54A;
            case BLOCK:        return 0xFFB7BEC9;
            case PEARLS:       return 0xFF73FFD1;
            case PROJECTILES:  return 0xFFFF9288;
            default:           return 0xFFFFFFFF;
        }
    }

    private ResourceLocation getPresetIcon(SmartPreset preset) {
        if (preset == null) {
            return null;
        }
        switch (preset) {
            case BEST_SWORD:   return SWORD_ICON;
            case BEST_AXE:     return AXE_ICON;
            case BEST_PICKAXE: return PICKAXE_ICON;
            case BEST_SHOVEL:  return SHOVEL_ICON;
            case BEST_BOW:     return BOW_ICON;
            case BLOCK:        return BLOCK_ICON;
            default:           return null;
        }
    }

    private boolean drawPresetIcon(SmartPreset preset, int x, int y, int width, int height) {
        ResourceLocation icon = getPresetIcon(preset);
        if (icon == null) {
            return false;
        }
        crow.client.utils.RenderUtils.bindSmoothIcon(icon);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.98F);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, width, height, width, height);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    private List<Item> filterItems(List<Item> items, String search) {
        if (search.isEmpty()) return items;
        List<Item> result = new ArrayList<>();
        for (Item item : items) {
            String name = new ItemStack(item).getDisplayName().toLowerCase();
            if (name.contains(search)) {
                result.add(item);
            }
        }
        return result;
    }

    private void renderItemIcon(int ix, int iy, ItemStack stack) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 32);
        mc.getRenderItem().renderItemIntoGUI(stack, ix, iy);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
    }
}
