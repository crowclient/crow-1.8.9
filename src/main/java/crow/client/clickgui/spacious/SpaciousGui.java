package crow.client.clickgui.spacious;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import crow.client.clickgui.compact.CompactModuleCard;
import crow.client.main.Crow;
import crow.client.module.Module.ModuleCategory;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.utils.Animation;
import crow.client.utils.Icons;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/**
 * Spacious click-GUI mode — central launcher + draggable category tabs.
 *
 * <p>Visual language matches the Compact GUI: same {@link CompactPalette}
 * background / card / accent / sidebar colors, same flowing-gradient
 * accent strips, same rounded-corner radii, same {@code MSAAFramebuffer}
 * pass. The difference is layout: instead of one big sidebar + scrolling
 * content panel, you get a small "home" card with category buttons, and
 * each category opens its own draggable tab.
 *
 * <p>Module cards inside tabs are {@link SpaciousModuleCard} — Compact-
 * styled rounded cards without the toggle pill or description text.
 * Click the card to toggle, right-click to expand the inline settings
 * panel. Settings reuse the Compact subcomponents (slider, toggle box,
 * combo, etc.) so configuration UI is identical across both modes.
 */
public class SpaciousGui extends GuiScreen {

    private static ResourceLocation CROW_ICON;
    static {
        try {
            CROW_ICON = RenderUtils.getResourcePath("/assets/crow/crow.png");
        } catch (Throwable ignored) {}
    }

    /** Outer margin around the whole grid of columns. */
    private static final int GRID_MARGIN = 12;
    /** Gap between adjacent columns, horizontally and vertically. */
    private static final int GRID_GAP = 8;

    private final List<SpaciousCategoryTab> tabs = new ArrayList<>();
    private SpaciousCategoryTab topTab;

    private long openTime;
    /** True until a layout has been applied, whether auto or restored. */
    private boolean firstInit = true;

    private final Animation introAnim = new Animation(300, Animation::easeOutCubic);

    public SpaciousGui() {
        for (ModuleCategory cat : ModuleCategory.values()) {
            // Skip meta categories that don't represent module groupings the
            // user would interact with directly.
            if (cat == ModuleCategory.category
                    || cat == ModuleCategory.hotkey
                    || cat == ModuleCategory.search) continue;
            tabs.add(new SpaciousCategoryTab(cat, this));
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        openTime = System.currentTimeMillis();
        introAnim.set(0F);
        introAnim.setTarget(1F);

        // Replay the zoom-in animation on every open. The SpaciousGui
        // instance lives for the whole game session, so without resetting
        // these animations a tab's openAnim would already be at 1 from
        // the last time the user had it open — the zoom would only play
        // on the very first open. Resetting every panel here means a
        // satisfying zoom-in fires every time the GUI is shown.
        for (SpaciousCategoryTab tab : tabs) {
            tab.resetOpenAnim();
        }

        // Auto-layout on first open, and any time the window size changed
        // out from under a saved layout — a grid restored from a 1080p
        // session would otherwise hang off the edge of a smaller window.
        if (firstInit || !layoutFitsScreen()) {
            layoutColumns();
            firstInit = false;
        }
    }

    int clampTabX(int v) { return Math.max(0, Math.min(width - SpaciousCategoryTab.TAB_WIDTH, v)); }
    int clampTabY(int v, int tabHeight) { return Math.max(0, Math.min(Math.max(0, height - tabHeight), v)); }

    /** True when every column is fully on screen horizontally. */
    private boolean layoutFitsScreen() {
        for (SpaciousCategoryTab tab : tabs) {
            if (tab.x < 0 || tab.x + SpaciousCategoryTab.TAB_WIDTH > width || tab.y < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Flow every category into columns left-to-right, wrapping to a new row
     * when the screen runs out. Every category is always on screen, so there
     * is no launcher and nothing to show or hide.
     */
    private void layoutColumns() {
        int colW = SpaciousCategoryTab.TAB_WIDTH;
        int usable = Math.max(colW, width - GRID_MARGIN * 2);
        int perRow = Math.max(1, (usable + GRID_GAP) / (colW + GRID_GAP));

        // Centre the grid horizontally so it doesn't hug the left edge on
        // wide displays.
        int rowW = perRow * colW + (perRow - 1) * GRID_GAP;
        int startX = Math.max(GRID_MARGIN, (width - rowW) / 2);

        int col = 0;
        int rowY = GRID_MARGIN;
        int tallestInRow = 0;
        for (SpaciousCategoryTab tab : tabs) {
            if (col == perRow) {
                col = 0;
                rowY += tallestInRow + GRID_GAP;
                tallestInRow = 0;
            }
            tab.setPosition(startX + col * (colW + GRID_GAP), rowY);
            tallestInRow = Math.max(tallestInRow, tab.getCurrentHeight());
            col++;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        if (!Display.isActive()) {
            mc.displayGuiScreen(null);
            return;
        }
        // Frost each column's own backdrop rather than shading the whole
        // screen, so the world stays sharp and only the glass is blurred.
        //
        // This has to run before the AA pass, not inside it: GUIBlurUtil
        // captures whatever framebuffer is bound, and the AA pass renders into
        // a UI-only FBO that begin() clears transparent — the world simply is
        // not in there to blur.
        for (SpaciousCategoryTab t : tabs) {
            t.drawBackdropBlur(mouseX, mouseY);
        }
        crow.client.utils.MSAAFramebuffer.begin();
        try {
            drawScreenInner(mouseX, mouseY, partial);
        } finally {
            crow.client.utils.MSAAFramebuffer.end();
        }
    }

    private void drawScreenInner(int mouseX, int mouseY, float partial) {
        super.drawScreen(mouseX, mouseY, partial);

        CompactPalette palette = GuiModule.getCompactPalette();
        introAnim.update();
        float intro = introAnim.get();

        // Scrim — fades in with the GUI.
        int scrim = palette.scrim;
        int scrimAlpha = (int) (((scrim >>> 24) & 0xFF) * intro);
        Gui.drawRect(0, 0, width, height,
                (scrimAlpha << 24) | (scrim & 0x00FFFFFF));

        // Most-recently-clicked column draws last so its dropdowns overlap
        // its neighbours rather than being clipped by them.
        for (SpaciousCategoryTab t : tabs) {
            if (t != topTab) t.draw(mouseX, mouseY, palette);
        }
        if (topTab != null) topTab.draw(mouseX, mouseY, palette);

        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    @Override
    public void mouseClicked(int x, int y, int mouseButton) throws IOException {
        // Topmost-first, so a dropdown overlapping a neighbour wins the click.
        List<SpaciousCategoryTab> order = new ArrayList<>();
        if (topTab != null) order.add(topTab);
        for (int i = tabs.size() - 1; i >= 0; i--) {
            SpaciousCategoryTab t = tabs.get(i);
            if (t != topTab) order.add(t);
        }
        for (SpaciousCategoryTab t : order) {
            if (t.mouseClicked(x, y, mouseButton)) {
                topTab = t;
                return;
            }
        }
    }

    @Override
    public void mouseReleased(int x, int y, int mouseButton) {
        for (SpaciousCategoryTab t : tabs) {
            t.mouseReleased(x, y, mouseButton);
        }
        // Persist BOTH layouts:
        //  - clientConfig.kv     → click-GUI layout + client-config modules
        //  - active .crow config → per-module settings like Reach, KillAura,
        //    etc. Without this, setting changes made in the Spacious GUI
        //    only live in memory; restarting the game or loading a config
        //    reverts them.
        if (Crow.clientConfig != null) {
            Crow.clientConfig.saveConfig();
        }
        if (Crow.configManager != null) {
            Crow.configManager.save();
        }
    }

    @Override
    public void keyTyped(char t, int k) {
        if (k == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        for (SpaciousCategoryTab tab : tabs) tab.keyTyped(t, k);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        // Route the wheel to the topmost column under the cursor.
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (topTab != null && topTab.containsPoint(mx, my)) {
            topTab.scroll(wheel);
            return;
        }
        for (int i = tabs.size() - 1; i >= 0; i--) {
            SpaciousCategoryTab t = tabs.get(i);
            if (t != topTab && t.containsPoint(mx, my)) {
                t.scroll(wheel);
                return;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        // Forward drag motion to whichever tab is scrollbar-dragging.
        for (SpaciousCategoryTab t : tabs) {
            if (t.isScrollbarDragging()) {
                t.scrollbarDrag(mouseY);
                return;
            }
        }
    }

    @Override
    public void onGuiClosed() {
        try {
            if (mc.entityRenderer != null && mc.entityRenderer.isShaderActive()) {
                mc.entityRenderer.stopUseShader();
            }
        } catch (Exception ignored) {}
        // Same dual-save as mouseReleased — module setting changes need
        // to land in the active .crow config too, not just clientconfig.kv.
        if (Crow.clientConfig != null) {
            Crow.clientConfig.saveConfig();
        }
        if (Crow.configManager != null) {
            Crow.configManager.save();
        }
        GuiModule.handleGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    /* ====================================================================== */
    /* Persistence — launcher position + per-tab x/y/visible state.           */
    /* Serialised into the client config so the user's layout (where they     */
    /* dragged each tab, which tabs they had open) survives game restarts.   */
    /* ====================================================================== */

    public com.google.gson.JsonObject getStateAsJson() {
        com.google.gson.JsonObject out = new com.google.gson.JsonObject();
        com.google.gson.JsonObject perTab = new com.google.gson.JsonObject();
        for (SpaciousCategoryTab tab : tabs) {
            com.google.gson.JsonObject td = new com.google.gson.JsonObject();
            td.addProperty("x", tab.x);
            td.addProperty("y", tab.y);
            perTab.add(tab.getCategory().name(), td);
        }
        out.add("tabs", perTab);
        return out;
    }

    public void applyState(com.google.gson.JsonObject state) {
        if (state == null) return;
        com.google.gson.JsonObject perTab = state.has("tabs") ? state.get("tabs").getAsJsonObject() : null;
        if (perTab != null) {
            for (SpaciousCategoryTab tab : tabs) {
                String key = tab.getCategory().name();
                if (!perTab.has(key)) continue;
                com.google.gson.JsonObject td = perTab.get(key).getAsJsonObject();
                if (td.has("x") && td.has("y")) {
                    tab.setPosition(td.get("x").getAsInt(), td.get("y").getAsInt());
                }
            }
        }
        // Skip the auto-arrange grid in initGui now that we've applied a
        // saved layout — otherwise the saved positions would be overwritten
        // on next open.
        firstInit = false;
    }

    /* ====================================================================== */
    /* Scissor helpers — Mojang's GL projection already scales virtual coords */
    /* by the ScaledResolution scale factor, so we convert from virtual to    */
    /* display pixels here before calling glScissor.                          */
    /* ====================================================================== */

    private boolean scissorEnabledStack;

    void pushScissor(int virtX, int virtY, int virtW, int virtH) {
        int sf = Math.max(1, new ScaledResolution(mc).getScaleFactor());
        int px = virtX * sf;
        int py = virtY * sf;
        int pw = virtW * sf;
        int ph = virtH * sf;
        if (pw < 0) pw = 0;
        if (ph < 0) ph = 0;
        scissorEnabledStack = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(px, mc.displayHeight - (py + ph), pw, ph);
    }

    void popScissor() {
        if (!scissorEnabledStack) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    /**
     * A scissor plus a rounded clip on the shapes inside it. The scissor
     * handles text and textures; {@link RenderUtils#setRoundedClip} rounds the
     * corners of everything drawn through the SDF shader, antialiased, so rows
     * that run to the panel edge stop following it instead of squaring it off.
     *
     * <p>{@code corners} is {TL, BL, BR, TR}.
     */
    void pushRoundedScissor(int virtX, int virtY, int virtW, int virtH,
                            float radius, boolean[] corners) {
        pushScissor(virtX, virtY, virtW, virtH);
        RenderUtils.setRoundedClip(virtX, virtY, virtX + virtW, virtY + virtH,
                radius, corners);
    }

    void popRoundedScissor() {
        RenderUtils.clearRoundedClip();
        popScissor();
    }

    void applyTabClipScissor(int x, int y, int w, int h) {
        // Inline settings render inside the tab body scissor already pushed
        // by the tab's draw(); no extra clip needed.
    }

    void restoreTabScissor() {
        // No-op.
    }

}
