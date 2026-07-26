package crow.client.clickgui.crow;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import crow.client.clickgui.crow.components.CategoryComponent;
import crow.client.clickgui.crow.components.HotbarLayoutComponent;
import crow.client.clickgui.crow.components.SettingComponent;
import crow.client.clickgui.crow.components.TextInputComponent;
import crow.client.main.Crow;
import crow.client.module.Module.ModuleCategory;
import crow.client.module.modules.client.GuiModule;
import crow.client.utils.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class ClickGui extends GuiScreen {

    private final ArrayList<CategoryComponent> categoryList;
    private CategoryComponent lastCategory;
    public static int mouseX, mouseY;
    public static int binding;
    public final Terminal terminal;
    private long openAnimationStart;
    private final List<CategoryComponent> visibleCategories = new ArrayList<>();
    private static boolean firstOpenDone = false;
    private long loadingStart;
    private static final long LOADING_DURATION = 600L;
    private static ResourceLocation CROW_ICON;

    public static int screenWidth = 1;
    public static int screenHeight = 1;

    private static ResourceLocation BLUR_SHADER;

    static {
        try {
            CROW_ICON = RenderUtils.getResourcePath("/assets/crow/crow.png");
        } catch (Throwable ignored) {
        }
        try {
            BLUR_SHADER = new ResourceLocation("shaders/post/blur.json");
        } catch (Throwable ignored) {
        }
    }

    public static int getRainbowAtX(int x) {
        return getRainbowAt(x, 0.0F, 1.0F, 1.0F);
    }

    public static int getRainbowAtXBright(int x) {
        return getRainbowAt(x, 0.0F, 0.65F, 1.0F);
    }

    public static int getRainbowAt(int x, float offset, float saturation, float brightness) {
        float base = screenWidth <= 0 ? 0.0F : (x / (float) screenWidth);
        float time = (System.currentTimeMillis() % 6000L) / 6000.0F;
        return Color.HSBtoRGB((base + time + offset) % 1.0F, saturation, brightness);
    }

    public ClickGui() {
        this.terminal = new Terminal();
        this.categoryList = new ArrayList<>();
        ModuleCategory[] values = ModuleCategory.values();
        int xOffset = 5;
        int yOffset = 5;
        for (ModuleCategory cat : values) {
            if (cat == ModuleCategory.category) {
                continue;
            }
            String name = cat.name().toLowerCase();
            if (name.equals("hotkey")) {
                continue;
            }
            CategoryComponent comp = new CategoryComponent(cat);
            comp.visable = true;
            categoryList.add(comp);
            comp.setCoords(xOffset, yOffset);
            xOffset += CategoryComponent.PANEL_WIDTH + 5;
            if (xOffset > 700) {
                xOffset = 5;
                yOffset += 150;
            }
        }
    }

    public void initMain() {
    }

    @Override
    public void initGui() {
        super.initGui();
        openAnimationStart = System.currentTimeMillis();
        if (!firstOpenDone) {
            loadingStart = System.currentTimeMillis();
        }
        refreshVisibleCategories();
        try {
            if (mc != null && mc.entityRenderer != null
                    && !mc.entityRenderer.isShaderActive()
                    && mc.theWorld != null && mc.thePlayer != null) {
                InputStream stream = null;
                try {
                    stream = mc.getResourceManager().getResource(BLUR_SHADER).getInputStream();
                } catch (Exception ignored) {
                }
                if (stream != null) {
                    mc.entityRenderer.loadShader(BLUR_SHADER);
                    stream.close();
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void drawScreen(int x, int y, float p) {
        if (!Display.isActive()) {
            mc.displayGuiScreen(null);
            return;
        }

        crow.client.utils.MSAAFramebuffer.begin();
        try {
            drawScreenMSAA(x, y, p);
        } finally {
            crow.client.utils.MSAAFramebuffer.end();
        }
    }

    private void drawScreenMSAA(int x, int y, float p) {
        super.drawScreen(x, y, p);
        mouseX = x;
        mouseY = y;

        ScaledResolution scaledResolution = new ScaledResolution(mc);
        screenWidth = scaledResolution.getScaledWidth();
        screenHeight = scaledResolution.getScaledHeight();

        if (!firstOpenDone) {
            long elapsed = System.currentTimeMillis() - loadingStart;
            if (elapsed < LOADING_DURATION) {
                float loadProgress = Math.min(1.0F, elapsed / (float) LOADING_DURATION);

                drawRect(0, 0, width, height, 0xFF101014);

                if (CROW_ICON != null) {
                    int logoSize = 48;
                    int logoX = (width - logoSize) / 2;
                    int logoY = (height - logoSize) / 2 - 10;
                    float logoAlpha = loadProgress < 0.7F
                            ? Math.min(1.0F, loadProgress / 0.3F)
                            : 1.0F - (loadProgress - 0.7F) / 0.3F;
                    GlStateManager.enableBlend();
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    GlStateManager.color(1.0F, 1.0F, 1.0F, Math.max(0.0F, logoAlpha));
                    crow.client.utils.RenderUtils.bindSmoothIcon(CROW_ICON);
                    Gui.drawModalRectWithCustomSizedTexture(logoX, logoY, 0, 0, logoSize, logoSize, logoSize, logoSize);
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                }
                return;
            } else {
                firstOpenDone = true;
                openAnimationStart = System.currentTimeMillis();
            }
        }

        drawRect(0, 0, width, height, 0x3C101014);
        drawRainbowBackdrop();

        String ver = Crow.CLIENT_NAME;
        mc.fontRendererObj.drawStringWithShadow(ver, 4,
                this.height - 4 - mc.fontRendererObj.FONT_HEIGHT, 0xFFFFFFFF);

        float progress = Math.min(1.0F, (System.currentTimeMillis() - openAnimationStart) / 700.0F);
        for (CategoryComponent cat : visibleCategories) {
            float delay = Math.min(0.35F, cat.getX() / (float) Math.max(1, screenWidth) * 0.3F);
            float local = progress <= delay ? 0.0F : Math.min(1.0F, (progress - delay) / (1.0F - delay));
            float eased = 1.0F - (float) Math.pow(1.0F - local, 3.0F);
            float slide = -42.0F * (1.0F - eased);
            float bounce = (float) Math.sin(local * Math.PI) * 18.0F * (1.0F - local);
            int offsetX = Math.round(slide + bounce);

            GL11.glPushMatrix();
            GL11.glTranslatef(offsetX, 0.0F, 0.0F);
            cat.draw(x, y);
            GL11.glPopMatrix();
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        for (CategoryComponent cat : visibleCategories) {
            if (cat.getOpenModule() != null) {
                for (SettingComponent sc : cat.getOpenModule().settings) {
                    if (sc instanceof HotbarLayoutComponent) {
                        HotbarLayoutComponent hlc = (HotbarLayoutComponent) sc;
                        if (hlc.isPickerOpen()) {
                            hlc.drawPickerOverlay(x, y);
                        }
                    }
                }
            }
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void mouseClicked(int x, int y, int mouseButton) throws IOException {
        for (CategoryComponent cat : visibleCategories) {
            if (cat.mouseDown(x, y, mouseButton)) {
                lastCategory = cat;
                return;
            }
        }
    }

    @Override
    public void mouseReleased(int x, int y, int mouseButton) {
        for (CategoryComponent cat : visibleCategories) {
            cat.mouseReleased(x, y, mouseButton);
        }
        if (Crow.clientConfig != null) {
            Crow.clientConfig.saveConfig();
        }
    }

    @Override
    public void keyTyped(char t, int k) {

        if (TextInputComponent.handleGlobalKeyTyped(t, k)) {

            if (k != 1) return;
        }
        if (lastCategory != null) {
            lastCategory.keyTyped(t, k);
        }
        if (k == 1) {
            Crow.mc.displayGuiScreen(null);
            Crow.configManager.save();
            if (Crow.clientConfig != null) {
                Crow.clientConfig.saveConfig();
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel() * 5;
        visibleCategories.forEach(cat -> {
            if (cat.isMouseOver(mouseX, mouseY)) {
                cat.scroll(scroll);
            }
        });
    }

    @Override
    public void onGuiClosed() {
        try {
            if (mc.entityRenderer != null && mc.entityRenderer.isShaderActive()) {
                mc.entityRenderer.stopUseShader();
            }
        } catch (Exception ignored) {
        }
        for (CategoryComponent cat : visibleCategories) {
            cat.guiClosed();
        }
        Crow.configManager.save();
        if (Crow.clientConfig != null) {
            Crow.clientConfig.saveConfig();
        }
        GuiModule.handleGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public ArrayList<CategoryComponent> getCategoryList() {
        return categoryList;
    }

    public CategoryComponent getCategoryComponent(ModuleCategory mCat) {
        for (CategoryComponent cc : categoryList) {
            if (cc.categoryName == mCat) {
                return cc;
            }
        }
        return null;
    }

    public ArrayList<CategoryComponent> visableCategoryList() {
        refreshVisibleCategories();
        return new ArrayList<>(visibleCategories);
    }

    public void resetSort() {
        int xOffset = 5;
        int yOffset = 5;
        for (CategoryComponent cat : categoryList) {
            cat.setCoords(xOffset, yOffset);
            xOffset += CategoryComponent.PANEL_WIDTH + 5;
            if (xOffset > 700) {
                xOffset = 5;
                yOffset += 150;
            }
        }
        refreshVisibleCategories();
    }

    private void refreshVisibleCategories() {
        visibleCategories.clear();
        for (CategoryComponent categoryComponent : categoryList) {
            if (categoryComponent.visable) {
                visibleCategories.add(categoryComponent);
            }
        }
    }

    private void drawRainbowBackdrop() {
        drawGradientRect(0, 0, width, height,
                withAlpha(getRainbowAt(0, 0.02F, 0.78F, 1.0F), 34),
                withAlpha(getRainbowAt(width, 0.18F, 0.82F, 1.0F), 34));

        drawGradientRect(0, height / 2, width, height,
                withAlpha(getRainbowAt(width / 4, 0.10F, 0.72F, 1.0F), 18),
                withAlpha(getRainbowAt((width * 3) / 4, 0.26F, 0.72F, 1.0F), 4));
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
