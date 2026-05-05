package crow.client.clickgui.compact;

import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class CompactCombo {

    private static final int HEADER_HEIGHT = 22;
    private static final int OPTION_HEIGHT = 20;
    private static final int DROPDOWN_PAD = 4;
    private static final int CORNER = 10;
    private static final ResourceLocation DROPDOWN_ARROW =
            RenderUtils.getResourcePath("/assets/crow/crowclickgui/arrow_down.png");

    private final ComboSetting<?> setting;
    private final Module mod;
    private boolean expanded;
    private float expandAnimation;
    private float hoverAnimation;

    private float[] optionHovers;

    int x, y, w, h;

    public CompactCombo(ComboSetting<?> setting, Module mod) {
        this.setting = setting;
        this.mod = mod;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public int getCurrentHeight() {
        if (!expanded) return HEADER_HEIGHT;
        Object[] options = setting.getOptions();
        int optionCount = options != null ? options.length : 0;
        return HEADER_HEIGHT + DROPDOWN_PAD + (optionCount * OPTION_HEIGHT) + DROPDOWN_PAD;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        expandAnimation += ((expanded ? 1.0F : 0.0F) - expandAnimation) * 0.24F;

        Object mode = setting.getMode();
        String value = mode == null ? "Unavailable" : mode.toString().replace('_', ' ');
        int pillW = Math.max(108, (int) FontUtil.small.getStringWidth(value) + 28);
        int pillX = x + w - pillW;
        int pillY = y;
        boolean hovered = mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + HEADER_HEIGHT;
        hoverAnimation += ((hovered ? 1.0F : 0.0F) - hoverAnimation) * 0.22F;

        FontUtil.small.drawSmoothString(setting.getName(), x, y + 6, palette.mutedText);

        int pillBase = CompactModuleCard.blendColor(palette.toggleOff, palette.card, 0.72F);
        int pillHover = CompactModuleCard.blendColor(palette.hoverCard, palette.card, 0.52F);
        int pillColor = CompactModuleCard.blendColor(pillBase, pillHover, hoverAnimation);
        RenderUtils.drawRoundedRectAA(pillX, pillY, pillX + pillW, pillY + HEADER_HEIGHT, CORNER, pillColor);

        if (expandAnimation > 0.02F || hovered) {
            int highlightAlpha = Math.max(22, (int) (70 * Math.max(expandAnimation, hoverAnimation)));
            RenderUtils.drawFlowingGradientRoundedRect(
                    pillX + 1, pillY + 1, pillX + pillW - 1, pillY + HEADER_HEIGHT - 1,
                    CORNER - 1, highlightAlpha, 0
            );
        }

        FontUtil.small.drawCenteredSmoothString(value, pillX + pillW / 2.0F - 6.0F, pillY + 7, palette.titleText);
        drawAnimatedArrow(pillX + pillW - 14, pillY + 6, palette, expandAnimation);

        if (expandAnimation <= 0.02F) {
            return;
        }

        Object[] options = setting.getOptions();
        if (options == null || options.length == 0) {
            return;
        }

        int dropX = pillX;
        int dropY = y + HEADER_HEIGHT + DROPDOWN_PAD;
        int dropW = pillW;
        int rawDropH = options.length * OPTION_HEIGHT;
        int drawDropH = Math.max(1, (int) (rawDropH * expandAnimation));

        RenderUtils.drawRoundedRectAA(dropX, dropY, dropX + dropW, dropY + drawDropH + DROPDOWN_PAD * 2,
                CORNER, CompactModuleCard.blendColor(palette.card, palette.background, 0.78F));

        int glowAlpha = Math.max(14, (int) (90 * expandAnimation));
        RenderUtils.drawFlowingGradientRoundedRect(
                dropX + 1, dropY + 1, dropX + dropW - 1, dropY + drawDropH + DROPDOWN_PAD * 2 - 1,
                CORNER - 1, glowAlpha, 0
        );

        if (optionHovers == null || optionHovers.length != options.length) {
            optionHovers = new float[options.length];
        }

        for (int i = 0; i < options.length; i++) {
            int optY = dropY + DROPDOWN_PAD + i * OPTION_HEIGHT;
            if (optY + OPTION_HEIGHT > dropY + drawDropH + DROPDOWN_PAD * 2) {
                break;
            }

            String optName = options[i].toString().replace('_', ' ');
            boolean selected = options[i].equals(setting.getMode());
            boolean optionHovered = mouseX >= dropX + 3 && mouseX <= dropX + dropW - 3
                    && mouseY >= optY && mouseY <= optY + OPTION_HEIGHT;

            optionHovers[i] += ((optionHovered ? 1.0F : 0.0F) - optionHovers[i]) * 0.24F;

            if (selected) {
                RenderUtils.drawRoundedRectAA(dropX + 3, optY, dropX + dropW - 3, optY + OPTION_HEIGHT,
                        8, CompactModuleCard.blendColor(palette.accent, palette.card, 0.22F));

                RenderUtils.drawFlowingGradientRoundedRect(dropX + 4, optY + 1, dropX + dropW - 4, optY + OPTION_HEIGHT - 1,
                        7, 40, 0);
            } else if (optionHovers[i] > 0.02F) {
                int hoverAlpha = (int) (255 * optionHovers[i]);
                int optHoverColor = CompactModuleCard.blendColor(palette.card, palette.hoverCard, optionHovers[i] * 0.75F);
                RenderUtils.drawRoundedRectAA(dropX + 3, optY, dropX + dropW - 3, optY + OPTION_HEIGHT,
                        8, (hoverAlpha << 24) | (optHoverColor & 0x00FFFFFF));
            }

            int textColor = selected ? palette.titleText
                    : CompactModuleCard.blendColor(palette.mutedText, palette.titleText,
                            Math.max(hoverAnimation * 0.2F, optionHovers[i] * 0.6F));
            FontUtil.small.drawCenteredSmoothString(optName, dropX + dropW / 2.0F, optY + 6, textColor);
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        int pillW = Math.max(108, (int) FontUtil.small.getStringWidth(
                String.valueOf(setting.getMode() == null ? "Unavailable" : setting.getMode().toString().replace('_', ' '))) + 28);
        int pillX = x + w - pillW;
        int pillY = y;

        if (button != 0) {
            expanded = false;
            return;
        }

        if (mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + HEADER_HEIGHT) {
            expanded = !expanded;
            return;
        }

        if (!expanded) {
            return;
        }

        Object[] options = setting.getOptions();
        if (options != null) {
            int dropX = pillX;
            int dropY = y + HEADER_HEIGHT + DROPDOWN_PAD + DROPDOWN_PAD;
            for (int i = 0; i < options.length; i++) {
                int optY = dropY + i * OPTION_HEIGHT;
                if (mouseX >= dropX && mouseX <= dropX + pillW
                        && mouseY >= optY && mouseY <= optY + OPTION_HEIGHT) {
                    ((ComboSetting) setting).setMode((Enum) options[i]);
                    mod.guiButtonToggled(setting);
                    expanded = false;
                    return;
                }
            }
        }

        expanded = false;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void closeDropdown() {
        expanded = false;
    }

    private void drawAnimatedArrow(int x, int y, CompactPalette palette, float animation) {
        if (DROPDOWN_ARROW == null) {
            FontUtil.small.drawSmoothString(animation > 0.5F ? "\u25B2" : "\u25BC", x, y + 1, palette.mutedText);
            return;
        }

        int size = 9;
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(DROPDOWN_ARROW);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + size / 2.0F, y + size / 2.0F, 0.0F);
        GlStateManager.rotate(180.0F * animation, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(-size / 2.0F, -size / 2.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.72F + (animation * 0.28F));
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, size, size, size, size);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
