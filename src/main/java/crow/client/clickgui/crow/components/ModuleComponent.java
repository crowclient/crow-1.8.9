package crow.client.clickgui.crow.components;

import java.util.ArrayList;

import org.lwjgl.opengl.GL11;

import crow.client.clickgui.crow.ClickGui;
import crow.client.clickgui.crow.Component;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.setting.Setting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class ModuleComponent extends Component {

    private static final int ROW_OFF = 0xFF343436;
    private static final int SETTING_BG = 0xF226262A;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    private static final int TEXT_MUTED = 0xFF9A9AA4;
    private static final int TOOLTIP_BG = 0xEE1A1A1A;
    private static final int HOVER_OVERLAY = 0x22FFFFFF;
    private static final ResourceLocation DROPDOWN_ARROW =
            RenderUtils.getResourcePath("/assets/crow/crowclickgui/arrow_down.png");

    public static final int MODULE_HEIGHT = 34;

    public Module mod;
    public CategoryComponent category;
    public ArrayList<SettingComponent> settings = new ArrayList<>();
    public BindComponent bind;
    private final Animation hoverAnim = new Animation(180, Animation::easeOutCubic);
    private final Animation enableAnim = new Animation(220, Animation::easeOutCubic);
    private final Animation arrowAnim = new Animation(250, Animation::easeOutCubic);
    private float textAlpha = 1.0F;

    public ModuleComponent(Module mod, CategoryComponent parent) {
        this.mod = mod;
        this.category = parent;
        mod.setModuleComponent(this);
        mod.getSettings().forEach(setting -> {
            Class<? extends SettingComponent> clazz = setting.getCrowComponentType();
            try {
                settings.add(clazz.getDeclaredConstructor(Setting.class, this.getClass())
                        .newInstance(setting, this));
            } catch (Exception e) {
                System.out.println("Failed to create component: " + clazz);
            }
        });
        bind = new BindComponent(this);
        setDimensions(CategoryComponent.PANEL_WIDTH, MODULE_HEIGHT);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();

        boolean hovered = insideNameArea(mouseX, mouseY);
        boolean settingsOpen = category.getOpenModule() == this;
        boolean hasSettings = !settings.isEmpty() || mod.isBindable();

        hoverAnim.setTarget(hovered ? 1.0F : 0.0F);
        hoverAnim.update();
        float hoverAnimation = hoverAnim.get();

        enableAnim.setTarget(mod.isEnabled() ? 1.0F : 0.0F);
        enableAnim.update();
        float enableAnimation = enableAnim.get();

        int themeColor = GuiModule.getThemeColor(x + CategoryComponent.PANEL_WIDTH / 2);
        int bgColor;
        if (enableAnimation > 0.01F) {
            bgColor = blendColor(ROW_OFF, themeColor, enableAnimation * 0.7F);
        } else if (settingsOpen) {
            bgColor = (themeColor & 0x00FFFFFF) | 0x55000000;
        } else {
            bgColor = ROW_OFF;
        }
        RenderUtils.drawRoundedRect(x, y, x2, y + MODULE_HEIGHT, 5, bgColor);

        if (hoverAnimation > 0.01F) {
            int alpha = (int) (0x20 * hoverAnimation);
            RenderUtils.drawRoundedRect(x, y, x2, y + MODULE_HEIGHT, 5, (alpha << 24) | 0x00FFFFFF);
        }

        String moduleDescription = trimDescription(mod.getShortDescription());
        float textBrightness = Math.max(enableAnimation, hoverAnimation * 0.5F);
        int textColor = blendColor(TEXT_GRAY, TEXT_WHITE, Math.min(1.0F, textBrightness + 0.3F));
        int descColor = blendColor(TEXT_MUTED, TEXT_WHITE, Math.min(1.0F, hoverAnimation * 0.35F));
        FontUtil.normal.drawSmoothString(mod.getName(), x + 8, y + 5, textColor);
        FontUtil.small.drawSmoothString(moduleDescription, x + 8, y + 19, descColor);

        drawPill(enableAnimation);

        if (hasSettings) {
            drawArrow(settingsOpen);
        }

        if (settingsOpen) {

            for (SettingComponent sc : settings) {
                sc.visable = sc.setting.shouldBeVisible();
            }

            int yOff = 0;
            for (SettingComponent sc : settings) {
                if (!sc.visable) {
                    continue;
                }
                sc.setCoords(x, y + MODULE_HEIGHT + yOff);
                Gui.drawRect(x, y + MODULE_HEIGHT + yOff, x2,
                        y + MODULE_HEIGHT + yOff + sc.getHeight() + 3, SETTING_BG);
                sc.draw(mouseX, mouseY);
                yOff += sc.getHeight() + 3;
            }
            if (mod.isBindable()) {
                bind.setCoords(x, y + MODULE_HEIGHT + yOff);
                Gui.drawRect(x, y + MODULE_HEIGHT + yOff, x2,
                        y + MODULE_HEIGHT + yOff + bind.getHeight() + 3, SETTING_BG);
                bind.draw(mouseX, mouseY);
                yOff += bind.getHeight();
            }
            setDimensions(CategoryComponent.PANEL_WIDTH, MODULE_HEIGHT + yOff + 3);
        } else {
            setDimensions(CategoryComponent.PANEL_WIDTH, MODULE_HEIGHT);
        }

        String desc = mod.getDescription();
        if (hovered && desc != null && !desc.isEmpty()) {
            drawTooltip(mouseX, mouseY, desc);
        } else {
            textAlpha = 0.0F;
        }

        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private static final int PILL_W = 24;
    private static final int PILL_H = 12;
    private static final int PILL_OFF_BG = 0xFF2C2C30;

    private void drawPill(float enableAnim) {
        int px = x2 - PILL_W - 28;
        int py = y + (MODULE_HEIGHT - PILL_H) / 2;
        int themeCol = GuiModule.getThemeColor(x + CategoryComponent.PANEL_WIDTH / 2);
        int pillBg   = blendColor(PILL_OFF_BG, themeCol, enableAnim);
        RenderUtils.drawRoundedRect(px, py, px + PILL_W, py + PILL_H, PILL_H / 2.0f, pillBg);

        float ballOffset = enableAnim * (PILL_W - PILL_H + 2);
        int bx = (int)(px + 2 + ballOffset);
        RenderUtils.drawRoundedRect(bx, py + 2, bx + PILL_H - 4, py + PILL_H - 2,
                (PILL_H - 4) / 2.0f, 0xFFFFFFFF);
    }

    private void drawTooltip(int mx, int my, String text) {

        textAlpha += (1.0F - textAlpha) * 0.3F;
        int alpha = (int) (0xEE * textAlpha);

        int tw = (int) (FontUtil.normal.getStringWidth(text) + 8);
        int th = 14;
        int tx = mx + 8;
        int ty = my - th - 4;

        int bgColor = (alpha << 24) | (0x1A1A1A);
        RenderUtils.drawRoundedRect(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, 4, (alpha / 3 << 24) | 0x444444);
        RenderUtils.drawRoundedRect(tx, ty, tx + tw, ty + th, 4, bgColor);

        int textAlphaInt = (int) (0xFF * textAlpha);
        int textCol = (textAlphaInt << 24) | 0xFFFFFF;
        FontUtil.normal.drawSmoothString(text, tx + 4, ty + 3, textCol);
    }

    private void drawArrow(boolean settingsOpen) {

        arrowAnim.setTarget(settingsOpen ? 180.0F : 0.0F);
        arrowAnim.update();
        float arrowRotation = arrowAnim.get();

        int size = 9;
        int centerX = x2 - 12;
        int centerY = y + (MODULE_HEIGHT / 2) - 1;
        crow.client.utils.RenderUtils.drawChevronRotated(
                centerX, centerY, size, arrowRotation, 0xFFFFFFFF, 1.3F);
    }

    @Override
    public void clicked(int x, int y, int b) {
        if (insideNameArea(x, y)) {
            if (b == 0) {
                mod.toggle();
                return;
            } else if (b == 1 && (!mod.getSettings().isEmpty() || mod.isBindable())) {
                category.setOpenModule(category.getOpenModule() == this ? null : this);
                if (category.getOpenModule() == this) {
                    int yOff = 0;
                    for (SettingComponent sc : settings) {
                        sc.setCoords(x, y + MODULE_HEIGHT + yOff);
                        yOff += sc.getHeight() + 3;
                    }
                    if (mod.isBindable()) {
                        bind.setCoords(x, y + MODULE_HEIGHT + yOff);
                        yOff += bind.getHeight();
                    }
                    setDimensions(CategoryComponent.PANEL_WIDTH, MODULE_HEIGHT + yOff + 3);
                } else {
                    setDimensions(CategoryComponent.PANEL_WIDTH, MODULE_HEIGHT);
                }
                return;
            }
        }
        if (category.getOpenModule() == this) {
            settings.forEach(sc -> {
                if (sc.visable) {
                    sc.mouseDown(x, y, b);
                }
            });
        }
        bind.mouseDown(x, y, b);
    }

    @Override
    public void mouseReleased(int x, int y, int b) {
        if (category.getOpenModule() == this) {
            settings.forEach(sc -> sc.mouseReleased(x, y, b));
        }
    }

    @Override
    public void keyTyped(char t, int k) {

        if (category.getOpenModule() == this) {
            for (SettingComponent sc : settings) {
                if (sc.visable) sc.keyTyped(t, k);
            }
        }
        bind.keyTyped(t, k);
    }

    @Override
    public void scroll(float ss) {

        if (category.getOpenModule() == this) {
            for (SettingComponent sc : settings) {
                if (sc.visable && sc.handleScroll((int) ss)) {
                    return;
                }
            }
        }
        super.scroll(ss);
    }

    public boolean insideNameArea(int mx, int my) {
        return mx > x && mx < x2 && my > y && my < (y + MODULE_HEIGHT);
    }

    private static int blendColor(int c1, int c2, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String trimDescription(String description) {
        if (description == null) {
            return "";
        }

        String singleLine = description.replace('\n', ' ').trim();
        int maxWidth = CategoryComponent.PANEL_WIDTH - 24;
        if (FontUtil.small.getStringWidth(singleLine) <= maxWidth) {
            return singleLine;
        }

        while (singleLine.length() > 4 && FontUtil.small.getStringWidth(singleLine + "...") > maxWidth) {
            singleLine = singleLine.substring(0, singleLine.length() - 1).trim();
        }
        return singleLine + "...";
    }
}
