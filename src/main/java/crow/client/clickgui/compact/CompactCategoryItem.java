package crow.client.clickgui.compact;

import org.lwjgl.opengl.GL11;

import crow.client.module.Module.ModuleCategory;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

public class CompactCategoryItem {

    private static final int ICON_SIZE = 11;
    private static final int ICON_PADDING = 8;
    private static final int TEXT_PADDING = 24;

    private final ModuleCategory category;
    private final ResourceLocation icon;

    private int x, y, w, h;
    private float hoverAnimation = 0.0F;
    private float selectAnimation = 0.0F;

    private float selectPulse = 0.0F;
    private boolean wasSelected = false;

    public CompactCategoryItem(ModuleCategory category) {
        this.category = category;
        String iconName;
        if (category == ModuleCategory.search) {
            iconName = "search";
        } else if (category == ModuleCategory.themes) {
            iconName = "themes";
        } else if (category == ModuleCategory.config) {
            iconName = "configs";
        } else if (category == ModuleCategory.other) {
            iconName = "other";
        } else {
            iconName = category.name().toLowerCase();
        }
        this.icon = iconName == null ? null : RenderUtils.getResourcePath("/assets/crow/crowclickgui/" + iconName + ".png");
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, boolean selected, CompactPalette palette) {
        boolean hovered = isMouseOver(mouseX, mouseY);

        hoverAnimation += ((hovered ? 1.0F : 0.0F) - hoverAnimation) * 0.22F;
        selectAnimation += ((selected ? 1.0F : 0.0F) - selectAnimation) * 0.22F;

        if (selected && !wasSelected) {
            selectPulse = 1.0F;
        }
        wasSelected = selected;
        selectPulse *= 0.88F;
        if (selectPulse < 0.01F) selectPulse = 0.0F;

        int drawX = x + Math.round(hoverAnimation * 1.0F + selectAnimation * 2.0F);

        if (selected || hoverAnimation > 0.02F) {

            float tint = Math.max(selectAnimation * 0.06F, hoverAnimation * 0.04F);
            int alpha = Math.round(255 * tint);
            if (alpha > 0) {
                int surface = (alpha << 24) | 0xFFFFFF;
                RenderUtils.drawRoundedRectAA(drawX, y, drawX + w, y + h, 8, surface);
            }
        }

        if (selectAnimation > 0.01F) {
            int accentRGB = crow.client.module.modules.client.GuiModule.getThemeColor(0)
                            & 0x00FFFFFF;
            int barAlpha = Math.round(255 * selectAnimation);
            int accent = (barAlpha << 24) | accentRGB;

            int barX = drawX;
            int barTop = y + 4;
            int barBottom = y + h - 4;
            RenderUtils.drawRoundedRectAA(barX, barTop, barX + 2, barBottom, 1, accent);
        }

        int textPadding = icon != null ? TEXT_PADDING : 12;
        if (icon != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(icon);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            float alpha = 0.78F + 0.22F * Math.max(hoverAnimation, selectAnimation);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
            int iconDrawX = drawX + ICON_PADDING + (selected ? 1 : 0);
            Gui.drawModalRectWithCustomSizedTexture(
                    iconDrawX, y + (h - ICON_SIZE) / 2,
                    0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int textColor = selected
                ? palette.titleText
                : CompactModuleCard.blendColor(palette.mutedText, palette.titleText, hoverAnimation * 0.40F);
        FontUtil.semiBold.drawSmoothString(category.getName(), drawX + textPadding, y + (h - 9) / 2, textColor);
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public ModuleCategory getCategory() {
        return category;
    }
}
