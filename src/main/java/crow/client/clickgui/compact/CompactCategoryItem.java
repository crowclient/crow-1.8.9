package crow.client.clickgui.compact;

import org.lwjgl.opengl.GL11;

import crow.client.module.Module.ModuleCategory;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.utils.Animation;
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
    // Duration-based, not per-frame lerps: a `+= delta * 0.22F` step runs
    // more than twice as fast at 240fps as at 60, so the whole sidebar
    // changed feel with the framerate.
    private final Animation hoverAnim = new Animation(180, Animation::easeOutCubic);
    private final Animation selectAnim = new Animation(180, Animation::easeOutCubic);

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

        hoverAnim.setTarget(hovered ? 1.0F : 0.0F);
        hoverAnim.update();
        selectAnim.setTarget(selected ? 1.0F : 0.0F);
        selectAnim.update();
        float hoverAnimation = hoverAnim.get();
        float selectAnimation = selectAnim.get();

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

        int textColor = selected
                ? palette.titleText
                : CompactModuleCard.blendColor(palette.mutedText, palette.titleText, hoverAnimation * 0.40F);

        // Phosphor glyph rather than a PNG: it inherits textColor, so the
        // icon brightens with the label on hover for free.
        String glyph = crow.client.utils.Icons.forCategory(category);
        float iconSize = ICON_SIZE;
        float iconDrawX = drawX + ICON_PADDING + (selected ? 1 : 0);
        crow.client.utils.Icons.drawLeft(glyph, iconDrawX, y + h / 2.0F, iconSize, textColor);

        FontUtil.semiBold.drawSmoothString(category.getName(),
                drawX + TEXT_PADDING, y + (h - 9) / 2, textColor);
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public ModuleCategory getCategory() {
        return category;
    }
}
