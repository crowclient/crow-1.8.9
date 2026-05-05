package crow.client.clickgui.crow.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.lwjgl.opengl.GL11;

import crow.client.clickgui.crow.ClickGui;
import crow.client.clickgui.crow.Component;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.RecordingMode;
import crow.client.utils.Animation;
import crow.client.utils.CoolDown;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

public class CategoryComponent extends Component {

    public static final int PANEL_WIDTH = 178;
    public static final int HEADER_HEIGHT = 30;

    private static final int PANEL_BG = 0xF226262A;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int ICON_SIZE = 16;
    private static final int HEADER_TEXT_X = 38;
    private static final int SHADOW_WIDTH_INSET = 2;
    private static final int SHADOW_HEIGHT = 4;

    public boolean visable = true;
    public boolean categoryOpened = false;
    public boolean dragging = false;
    public int dragX, dragY;
    public Module.ModuleCategory categoryName;
    public ArrayList<ModuleComponent> modulesInCategory = new ArrayList<>();
    private ModuleComponent openComponent;
    private final ResourceLocation categoryIcon;

    private int prevHeight, deltaHeight, heightCheck;
    private final CoolDown timer = new CoolDown(500);
    public float tPercent;
    private final Animation toggleAnim = new Animation(250, Animation::easeOutCubic);

    public CategoryComponent(Module.ModuleCategory category) {
        categoryName = category;
        String iconName;
        if (category == Module.ModuleCategory.themes) {
            iconName = "themes";
        } else if (category == Module.ModuleCategory.config) {
            iconName = "configs";
        } else if (category == Module.ModuleCategory.other) {
            iconName = "other";
        } else {
            iconName = category.name().toLowerCase();
        }
        categoryIcon = RenderUtils.getResourcePath("/assets/crow/crowclickgui/" + iconName + ".png");
        setDimensions(PANEL_WIDTH, HEADER_HEIGHT);
        Crow.moduleManager.getModulesInCategory(category)
                .forEach(module -> modulesInCategory.add(new ModuleComponent(module, this)));
        modulesInCategory.sort(Comparator.comparingDouble(m -> FontUtil.normal.getStringWidth(m.mod.getName())));
        Collections.reverse(modulesInCategory);
    }

    public void initGui() {
    }

    @Override
    public void guiClosed() {
        heightCheck = 0;
    }

    @Override
    public void setCoords(int x, int y) {
        super.setCoords(x, y);
    }

    public void setOpened(boolean on) {
        categoryOpened = on;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visable) {
            return;
        }

        int targetHeight = 0;
        if (categoryOpened) {
            if (openComponent != null) {
                targetHeight = openComponent.getHeight();
            } else {
                for (ModuleComponent mc : modulesInCategory) {
                    targetHeight += mc.getHeight();
                }
            }
        }
        if (heightCheck != targetHeight) {
            prevHeight = heightCheck;
            deltaHeight = targetHeight - heightCheck;
            heightCheck = targetHeight;
            timer.setCooldown(500);
            timer.start();
        }
        tPercent = Utils.Client.smoothPercent(timer.getElapsedTime() / (float) timer.getCooldownTime());
        setDimensions(PANEL_WIDTH, HEADER_HEIGHT + prevHeight + (int) (deltaHeight * tPercent));

        if (dragging) {
            setCoords(mouseX + dragX, mouseY + dragY);
        }

        RenderUtils.drawRoundedRect(x, y, x2, y2, 8, PANEL_BG);

        if (categoryOpened || tPercent < 1) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderUtils.glScissor(x, y + HEADER_HEIGHT, width, y2 - (y + HEADER_HEIGHT) + 1);

            if (openComponent != null) {
                openComponent.setCoords(x, y + HEADER_HEIGHT);
                openComponent.draw(mouseX, mouseY);
            } else {
                int yOff = 0;
                for (ModuleComponent module : modulesInCategory) {
                    module.setCoords(x, y + HEADER_HEIGHT + yOff);
                    module.draw(mouseX, mouseY);
                    yOff += module.getHeight();
                }
            }

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        drawHeaderShadow();

        if (RecordingMode.shouldSkipStencil()) {

            RenderUtils.drawFlowingGradientRect(x, y, x2, y + HEADER_HEIGHT, 255, 0);
        } else {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_KEEP, GL11.GL_KEEP);

            RenderUtils.drawRoundedRect(x, y, x2, y + HEADER_HEIGHT, 8, 0xFFFFFFFF,
                    new boolean[]{true, true, false, false});

            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            RenderUtils.drawFlowingGradientRect(x, y, x2, y + HEADER_HEIGHT, 255, 0);

            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        Gui.drawRect(x, y + HEADER_HEIGHT, x2, y + HEADER_HEIGHT + 1, 0x25FFFFFF);

        float shimmerCycle = (System.currentTimeMillis() % 3000L) / 3000.0F;
        int shimmerX = x + (int) ((PANEL_WIDTH + 20) * shimmerCycle) - 10;
        int shimmerWidth = 12;
        int sLeft = Math.max(x, shimmerX);
        int sRight = Math.min(x2, shimmerX + shimmerWidth);
        if (sRight > sLeft) {
            Gui.drawRect(sLeft, y, sRight, y + HEADER_HEIGHT, 0x20FFFFFF);
        }

        if (categoryIcon != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(categoryIcon);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            Gui.drawModalRectWithCustomSizedTexture(x + 9, y + 6, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }

        FontUtil.normal.drawSmoothString(categoryName.getName(), x + HEADER_TEXT_X, y + 9, TEXT_WHITE);

        GL11.glPushMatrix();
        toggleAnim.setTarget(categoryOpened ? 1.0F : 0.0F);
        toggleAnim.update();
        GL11.glTranslatef((x + PANEL_WIDTH - 8), (y + 13), 0);
        GL11.glRotatef(toggleAnim.get() * 180.0F, 0, 0, 1);
        GL11.glTranslatef(-(x + PANEL_WIDTH - 8), -(y + 13), 0);
        GL11.glScaled(0.5, 0.5, 0.5);
        Minecraft.getMinecraft().fontRendererObj.drawString("+",
                (x + PANEL_WIDTH - 11) * 2, (y + 9) * 2, TEXT_WHITE, false);
        GL11.glPopMatrix();
    }

    @Override
    public void scroll(float ss) {
        if (openComponent != null) {
            openComponent.scroll(ss);
        }
    }

    @Override
    public void clicked(int x, int y, int button) {
        if (overExpandButton(x, y)) {
            setOpened(!categoryOpened);
            return;
        }
        if (overHeader(x, y)) {
            dragX = this.x - x;
            dragY = this.y - y;
            dragging = true;
            return;
        }
        if (openComponent == null) {
            for (ModuleComponent module : modulesInCategory) {
                if (module.mouseDown(x, y, button)) {
                    return;
                }
            }
        } else {
            openComponent.mouseDown(x, y, button);
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
        dragging = false;
        modulesInCategory.forEach(module -> module.mouseReleased(x, y, button));
    }

    @Override
    public void keyTyped(char t, int k) {
        if (openComponent != null) {
            openComponent.keyTyped(t, k);
        }
    }

    public void updateModules() {
        modulesInCategory.clear();
        Crow.moduleManager.getModulesInCategory(categoryName)
                .forEach(module -> modulesInCategory.add(new ModuleComponent(module, this)));
        modulesInCategory.sort(Comparator.comparingDouble(m -> FontUtil.normal.getStringWidth(m.mod.getName())));
        Collections.reverse(modulesInCategory);
    }

    public boolean overExpandButton(int mx, int my) {
        return mx > (x + PANEL_WIDTH - 14) && mx < x2 && my > y && my < (y + HEADER_HEIGHT);
    }

    public boolean overHeader(int mx, int my) {
        return mx > x && mx < x2 && my > y && my < (y + HEADER_HEIGHT);
    }

    public void setOpenModule(ModuleComponent component) {
        openComponent = component;
    }

    public ModuleComponent getOpenModule() {
        return openComponent;
    }

    private void drawHeaderShadow() {
        int left = x + SHADOW_WIDTH_INSET;
        int right = x2 - SHADOW_WIDTH_INSET;
        int shadowTop = y + HEADER_HEIGHT;
        Gui.drawRect(left, shadowTop, right, shadowTop + 1, 0x5A000000);
        Gui.drawRect(left + 1, shadowTop + 1, right - 1, shadowTop + 2, 0x32000000);
        Gui.drawRect(left + 2, shadowTop + 2, right - 2, shadowTop + SHADOW_HEIGHT, 0x18000000);
    }
}
