package crow.client.clickgui.crow.components;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import crow.client.clickgui.crow.ClickGui;
import crow.client.module.setting.Setting;
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

public class HotbarLayoutComponent extends SettingComponent {

    private static final int CELL_SIZE   = 18;
    private static final int CELL_GAP    = 2;
    private static final int HEADER_H    = 16;
    private static final int ROW_H       = CELL_SIZE + 6;
    private static final int PICKER_GAP  = 4;

    private static final int PICKER_W    = 170;
    private static final int PICKER_H    = 240;
    private static final int PRESET_BTN_W = 78;
    private static final int PRESET_BTN_H = 14;
    private static final int ITEM_CELL   = 18;
    private static final int ITEMS_PER_ROW = 8;

    private static final int FIXED_CONTENT_H = 4 + 18 + 12 + 4 * (PRESET_BTN_H + 2) + (PRESET_BTN_H + 6) + 6;

    private final HotbarLayoutSetting setting;
    private int pickerSlot   = -1;
    private String searchText = "";
    private int pickerScrollY   = 0;
    private int maxPickerScroll = 0;

    private static final IntBuffer SCISSOR_BUF = BufferUtils.createIntBuffer(4);

    public HotbarLayoutComponent(Setting setting, ModuleComponent parent) {
        super(setting, parent);
        this.setting = (HotbarLayoutSetting) setting;
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, HEADER_H + ROW_H);
    }

    public boolean isPickerOpen() {
        return pickerSlot >= 0;
    }

    @Override
    public boolean handleScroll(int delta) {
        if (pickerSlot < 0) return false;
        pickerScrollY = Math.max(-maxPickerScroll, Math.min(0, pickerScrollY + delta));
        return true;
    }

    @Override
    public void draw(int mouseX, int mouseY) {

        int componentH = (pickerSlot >= 0)
                ? HEADER_H + ROW_H + PICKER_GAP + PICKER_H
                : HEADER_H + ROW_H;
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, componentH);

        int sx      = this.x + 5;
        int cellsY  = y + HEADER_H;
        int totalCellsW = 9 * CELL_SIZE + 8 * CELL_GAP;
        int cellsX  = sx + (width - 10 - totalCellsW) / 2;

        FontUtil.small.drawSmoothString(setting.getName(), sx, y + 3, 0xFFBEBEC9);

        for (int i = 0; i < 9; i++) {
            int cx = cellsX + i * (CELL_SIZE + CELL_GAP);
            int cy = cellsY;
            boolean hovered  = mouseX >= cx && mouseX < cx + CELL_SIZE
                             && mouseY >= cy && mouseY < cy + CELL_SIZE;
            boolean selected = pickerSlot == i;

            int bgColor = selected ? 0xFF3A3A5E : (hovered ? 0xFF3A3A3E : 0xFF2A2A2E);
            RenderUtils.drawRoundedRect(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, 3, bgColor);

            if (selected) {
                RenderUtils.drawRoundedOutline(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, 3, 1.0F,
                        ClickGui.getRainbowAtX(cx));
            }

            drawSlotContent(cx, cy, setting.getSlot(i));
            FontUtil.small.drawSmoothString(String.valueOf(i + 1), cx + 1, cy + CELL_SIZE - 6, 0x66FFFFFF);
        }

    }

    public void drawPickerOverlay(int mouseX, int mouseY) {
        if (pickerSlot >= 0) {
            drawPicker(mouseX, mouseY);
        }
    }

    private void drawPicker(int mouseX, int mouseY) {
        int px = this.x + 4;
        int py = this.y + HEADER_H + ROW_H + PICKER_GAP;

        RenderUtils.drawRoundedRect(px - 2, py - 2, px + PICKER_W + 2, py + PICKER_H + 2, 6, 0xEE1A1A1E);
        RenderUtils.drawRoundedRect(px, py, px + PICKER_W, py + PICKER_H, 5, 0xF0222228);

        int curY = py + 4;

        int searchFieldX2 = px + PICKER_W - 4;
        RenderUtils.drawRoundedRect(px + 4, curY, searchFieldX2, curY + 14, 3, 0xFF1A1A1E);
        String displaySearch = searchText.isEmpty() ? "Search items..." : searchText;
        int searchColor = searchText.isEmpty() ? 0xFF666666 : 0xFFDDDDDD;
        FontUtil.small.drawSmoothString(displaySearch, px + 8, curY + 3, searchColor);
        if (!searchText.isEmpty() && System.currentTimeMillis() % 1000 > 500) {
            int cursorX = px + 8 + (int) FontUtil.small.getStringWidth(searchText);
            Gui.drawRect(cursorX + 1, curY + 2, cursorX + 2, curY + 12, 0xFFCCCCCC);
        }
        curY += 18;

        FontUtil.small.drawSmoothString("Smart Presets", px + 4, curY, 0xFF888899);
        curY += 12;

        SmartPreset[] presets = SmartPreset.values();
        for (int i = 0; i < presets.length; i += 2) {
            for (int j = 0; j < 2 && i + j < presets.length; j++) {
                int bx = px + 4 + j * (PRESET_BTN_W + 4);
                int by = curY;
                SmartPreset p = presets[i + j];
                boolean hov = mouseX >= bx && mouseX < bx + PRESET_BTN_W
                           && mouseY >= by && mouseY < by + PRESET_BTN_H;
                RenderUtils.drawRoundedRect(bx, by, bx + PRESET_BTN_W, by + PRESET_BTN_H, 4,
                        hov ? 0xFF3A3A5E : 0xFF2A2A38);
                FontUtil.small.drawSmoothString(p.getDisplayName(), bx + 4, by + 3, 0xFFCCCCDD);
            }
            curY += PRESET_BTN_H + 2;
        }

        boolean emptyHov = mouseX >= px + 4 && mouseX < px + 4 + PRESET_BTN_W
                        && mouseY >= curY && mouseY < curY + PRESET_BTN_H;
        RenderUtils.drawRoundedRect(px + 4, curY, px + 4 + PRESET_BTN_W, curY + PRESET_BTN_H, 4,
                emptyHov ? 0xFF3A3A5E : 0xFF2A2A38);
        FontUtil.small.drawSmoothString("Empty", px + 8, curY + 3, 0xFFCCCCDD);
        curY += PRESET_BTN_H + 6;

        Gui.drawRect(px + 4, curY, px + PICKER_W - 4, curY + 1, 0x44FFFFFF);
        curY += 5;

        int itemAreaTop = curY;
        int itemAreaH   = py + PICKER_H - itemAreaTop;

        SCISSOR_BUF.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BUF);
        int savedSX = SCISSOR_BUF.get(0);
        int savedSY = SCISSOR_BUF.get(1);
        int savedSW = SCISSOR_BUF.get(2);
        int savedSH = SCISSOR_BUF.get(3);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RenderUtils.glScissor(px, itemAreaTop, PICKER_W, itemAreaH);

        String lowerSearch = searchText.toLowerCase();
        Map<String, List<Item>> categories = HotbarLayoutSetting.getItemCategories();

        int drawY        = itemAreaTop + pickerScrollY;
        int totalItemH   = 0;
        Item hoveredItem = null;

        for (Map.Entry<String, List<Item>> entry : categories.entrySet()) {
            List<Item> filtered = filterItems(entry.getValue(), lowerSearch);
            if (filtered.isEmpty()) continue;

            if (drawY + 11 > itemAreaTop && drawY < itemAreaTop + itemAreaH) {
                FontUtil.small.drawSmoothString(entry.getKey(), px + 4, drawY, 0xFF888899);
            }
            drawY     += 11;
            totalItemH += 11;

            int col = 0;
            for (Item item : filtered) {
                int ix = px + 4 + col * (ITEM_CELL + 1);
                int iy = drawY;

                if (iy + ITEM_CELL > itemAreaTop && iy < itemAreaTop + itemAreaH) {
                    boolean itemHov = mouseX >= ix && mouseX < ix + ITEM_CELL
                                   && mouseY >= iy && mouseY < iy + ITEM_CELL
                                   && mouseY >= itemAreaTop && mouseY < itemAreaTop + itemAreaH;
                    if (itemHov) {
                        RenderUtils.drawRoundedRect(ix, iy, ix + ITEM_CELL, iy + ITEM_CELL, 3, 0x44FFFFFF);
                        hoveredItem = item;
                    }
                    renderItemIcon(ix + 1, iy + 1, new ItemStack(item));
                }

                col++;
                if (col >= ITEMS_PER_ROW) {
                    col = 0;
                    drawY      += ITEM_CELL + 1;
                    totalItemH += ITEM_CELL + 1;
                }
            }
            if (col > 0) {
                drawY      += ITEM_CELL + 2;
                totalItemH += ITEM_CELL + 2;
            }
            drawY      += 2;
            totalItemH += 2;
        }

        maxPickerScroll = Math.max(0, totalItemH - itemAreaH);

        GL11.glScissor(savedSX, savedSY, savedSW, savedSH);

        if (hoveredItem != null) {
            String name = new ItemStack(hoveredItem).getDisplayName();
            int tw = (int) FontUtil.small.getStringWidth(name) + 8;
            RenderUtils.drawRoundedRect(mouseX + 8, mouseY - 14, mouseX + 8 + tw, mouseY - 1, 4, 0xEE1A1A1A);
            FontUtil.small.drawSmoothString(name, mouseX + 12, mouseY - 12, 0xFFFFFFFF);
        }

        if (maxPickerScroll > 0) {
            int trackX   = px + PICKER_W - 4;
            int trackTop = itemAreaTop;
            int trackH   = itemAreaH;
            float ratio  = itemAreaH / (float)(totalItemH);
            int thumbH   = Math.max(12, (int)(trackH * ratio));
            int thumbTop = trackTop + (int)((trackH - thumbH) * (-pickerScrollY) / (float) maxPickerScroll);

            RenderUtils.drawRoundedRect(trackX, trackTop, trackX + 3, trackTop + trackH, 1.5F, 0x22FFFFFF);
            RenderUtils.drawRoundedRect(trackX, thumbTop, trackX + 3, thumbTop + thumbH, 1.5F, 0x88FFFFFF);
        }
    }

    private void drawSlotContent(int cx, int cy, HotbarSlotConfig config) {
        if (config.type == SlotType.EMPTY) {
            FontUtil.small.drawSmoothString("-", cx + 6, cy + 4, 0x44FFFFFF);
            return;
        }
        if (config.type == SlotType.SMART_PRESET) {
            FontUtil.small.drawSmoothString(getPresetShortLabel(config.preset), cx + 2, cy + 5, 0xFFAABBFF);
            return;
        }
        if (config.type == SlotType.SPECIFIC_ITEM) {
            Item item = config.getResolvedItem();
            if (item != null) {
                renderItemIcon(cx + 1, cy + 1, new ItemStack(item));
            } else {
                FontUtil.small.drawSmoothString("?", cx + 6, cy + 4, 0xFFFF6666);
            }
        }
    }

    private String getPresetShortLabel(SmartPreset preset) {
        if (preset == null) return "?";
        switch (preset) {
            case BEST_SWORD:   return "\u2694";
            case BEST_AXE:     return "Ax";
            case BEST_PICKAXE: return "\u26CF";
            case BEST_SHOVEL:  return "Sh";
            case BEST_BOW:     return "\u2192";
            case BEST_ROD:     return "Rd";
            case BEST_FOOD:    return "Fd";
            case GAPPLE:       return "Gp";
            case BLOCK:        return "\u25A0";
            case PEARLS:       return "Ep";
            case PROJECTILES:  return "Pj";
            default:           return "?";
        }
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

    @Override
    public void clicked(int mouseX, int mouseY, int button) {
        int sx = this.x + 5;
        int cellsY = y + HEADER_H;
        int totalCellsW = 9 * CELL_SIZE + 8 * CELL_GAP;
        int cellsX = sx + (width - 10 - totalCellsW) / 2;

        if (pickerSlot >= 0) {
            if (handlePickerClick(mouseX, mouseY)) {
                return;
            }
        }

        for (int i = 0; i < 9; i++) {
            int cx = cellsX + i * (CELL_SIZE + CELL_GAP);
            int cy = cellsY;
            if (mouseX >= cx && mouseX < cx + CELL_SIZE && mouseY >= cy && mouseY < cy + CELL_SIZE) {
                if (pickerSlot == i) {
                    pickerSlot = -1;
                } else {
                    pickerSlot    = i;
                    searchText    = "";
                    pickerScrollY = 0;
                }
                return;
            }
        }

        if (pickerSlot >= 0) {
            pickerSlot = -1;
        }
    }

    private boolean handlePickerClick(int mouseX, int mouseY) {
        int px = this.x + 4;
        int py = this.y + HEADER_H + ROW_H + PICKER_GAP;

        if (mouseX < px - 2 || mouseX > px + PICKER_W + 2
         || mouseY < py - 2 || mouseY > py + PICKER_H + 2) {
            pickerSlot = -1;
            return true;
        }

        int curY = py + 4;

        if (mouseY >= curY && mouseY < curY + 14) return true;
        curY += 18;

        curY += 12;

        SmartPreset[] presets = SmartPreset.values();
        for (int i = 0; i < presets.length; i += 2) {
            for (int j = 0; j < 2 && i + j < presets.length; j++) {
                int bx = px + 4 + j * (PRESET_BTN_W + 4);
                if (mouseX >= bx && mouseX < bx + PRESET_BTN_W
                 && mouseY >= curY && mouseY < curY + PRESET_BTN_H) {
                    setting.setSlot(pickerSlot, HotbarSlotConfig.ofPreset(presets[i + j]));
                    pickerSlot = -1;
                    return true;
                }
            }
            curY += PRESET_BTN_H + 2;
        }

        if (mouseX >= px + 4 && mouseX < px + 4 + PRESET_BTN_W
         && mouseY >= curY && mouseY < curY + PRESET_BTN_H) {
            setting.setSlot(pickerSlot, HotbarSlotConfig.empty());
            pickerSlot = -1;
            return true;
        }
        curY += PRESET_BTN_H + 6;

        curY += 6;

        int itemAreaTop = curY;
        int itemAreaH   = py + PICKER_H - itemAreaTop;

        if (mouseY < itemAreaTop || mouseY >= itemAreaTop + itemAreaH) return true;

        String lowerSearch = searchText.toLowerCase();
        Map<String, List<Item>> categories = HotbarLayoutSetting.getItemCategories();
        int drawY = itemAreaTop + pickerScrollY;

        for (Map.Entry<String, List<Item>> entry : categories.entrySet()) {
            List<Item> filtered = filterItems(entry.getValue(), lowerSearch);
            if (filtered.isEmpty()) continue;
            drawY += 11;

            int col = 0;
            for (Item item : filtered) {
                int ix = px + 4 + col * (ITEM_CELL + 1);
                int iy = drawY;

                if (mouseX >= ix && mouseX < ix + ITEM_CELL
                 && mouseY >= iy && mouseY < iy + ITEM_CELL) {
                    String regName = HotbarLayoutSetting.getItemRegistryName(item);
                    if (regName != null) {
                        setting.setSlot(pickerSlot, HotbarSlotConfig.ofItem(regName));
                        pickerSlot = -1;
                    }
                    return true;
                }

                col++;
                if (col >= ITEMS_PER_ROW) {
                    col = 0;
                    drawY += ITEM_CELL + 1;
                }
            }
            if (col > 0) drawY += ITEM_CELL + 2;
            drawY += 2;
        }

        return true;
    }

    @Override
    public void keyTyped(char t, int k) {
        if (pickerSlot < 0) return;

        if (k == 14) {
            if (!searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                pickerScrollY = 0;
            }
        } else if (k == 1) {
            pickerSlot = -1;
        } else if (t >= 32 && t < 127) {
            searchText += t;
            pickerScrollY = 0;
        }
    }

    private List<Item> filterItems(List<Item> items, String search) {
        if (search.isEmpty()) return items;
        List<Item> result = new ArrayList<>();
        for (Item item : items) {
            if (new ItemStack(item).getDisplayName().toLowerCase().contains(search)) {
                result.add(item);
            }
        }
        return result;
    }
}
