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

    private static final int LAUNCHER_WIDTH = 152;
    private static final int LAUNCHER_HEADER = 38;
    private static final int LAUNCHER_PADDING = 6;
    private static final int LAUNCHER_RADIUS = 8;
    private static final int LAUNCHER_BTN_HEIGHT = 17;
    private static final int LAUNCHER_BTN_GAP = 2;
    private static final int LAUNCHER_RESET_HEIGHT = 16;
    private static final int LAUNCHER_RESET_GAP = 5;

    private final List<SpaciousCategoryTab> tabs = new ArrayList<>();
    private final List<LauncherButton> buttons = new ArrayList<>();
    private SpaciousCategoryTab topTab;

    private int launcherX, launcherY;
    private boolean launcherDragging;
    private int launcherDragOffsetX, launcherDragOffsetY;

    private long openTime;
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

        buttons.clear();
        for (SpaciousCategoryTab tab : tabs) {
            buttons.add(new LauncherButton(tab));
        }

        if (firstInit) {
            launcherX = 24;
            launcherY = 22;
            layoutTabsToColumn();
            firstInit = false;
        }
    }

    /** Stride used when stacking tabs in the default vertical column.
     *  Collapsed-tab height (header only) plus a few px of breathing room. */
    private static final int STACK_STRIDE = 25;

    /** The X coordinate of the stacked column to the right of the launcher. */
    private int stackColumnX() {
        return launcherX + LAUNCHER_WIDTH + 12;
    }

    /** Lay every tab out in a single vertical column to the right of the
     *  launcher. Used on first init and by Reset Layout. */
    private void layoutTabsToColumn() {
        int colX = stackColumnX();
        int colY = launcherY;
        for (SpaciousCategoryTab tab : tabs) {
            tab.setPosition(colX, colY);
            colY += STACK_STRIDE;
        }
    }

    /** Snap a freshly-shown tab to the slot just below the currently-
     *  visible tabs in the stacked column. Lets the user toggle category
     *  buttons on the launcher and see each one appear in order without
     *  needing to drag. After this they can drag freely; the new position
     *  is saved when the GUI closes. */
    void positionAtBottomOfVisibleStack(SpaciousCategoryTab incoming) {
        int colX = stackColumnX();
        int bottomY = launcherY;
        for (SpaciousCategoryTab t : tabs) {
            if (t == incoming || !t.visible) continue;
            int slotBottom = t.y + STACK_STRIDE;
            if (slotBottom > bottomY) bottomY = slotBottom;
        }
        incoming.setPosition(colX, bottomY);
    }

    /** Wipe per-tab user layout state back to default — positions snap
     *  to the stacked column, visibility off, all tabs re-collapse. */
    private void resetTabLayout() {
        launcherX = 24;
        launcherY = 22;
        layoutTabsToColumn();
        for (SpaciousCategoryTab tab : tabs) {
            tab.visible = false;
            tab.collapsed = true;
        }
        topTab = null;
        if (Crow.clientConfig != null) {
            Crow.clientConfig.saveConfig();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        if (!Display.isActive()) {
            mc.displayGuiScreen(null);
            return;
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

        if (launcherDragging) {
            launcherX = clampLauncherX(mouseX - launcherDragOffsetX);
            launcherY = clampLauncherY(mouseY - launcherDragOffsetY);
        }

        // Draw all visible tabs (most-recently-clicked last so it stacks on top).
        List<SpaciousCategoryTab> drawOrder = new ArrayList<>();
        for (SpaciousCategoryTab t : tabs) {
            if ((t.visible || isAnimatingClosed(t)) && t != topTab) drawOrder.add(t);
        }
        if (topTab != null && (topTab.visible || isAnimatingClosed(topTab))) drawOrder.add(topTab);
        for (SpaciousCategoryTab t : drawOrder) {
            t.draw(mouseX, mouseY, palette);
        }

        // Launcher always on top.
        drawLauncher(mouseX, mouseY, palette, intro);

        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private boolean isAnimatingClosed(SpaciousCategoryTab t) {
        // Hook for the tab's open-anim: we keep drawing during its decay
        // even after visible flips false, so the user sees the close
        // animation. The tab's own draw() short-circuits at very low
        // anim, so this is just a cooperative "let it tick" flag.
        return false; // tab.draw checks its own anim — simplified for now.
    }

    private void drawLauncher(int mouseX, int mouseY, CompactPalette palette, float intro) {
        int hgt = launcherHeight();
        int x0 = launcherX, y0 = launcherY;
        int x1 = x0 + LAUNCHER_WIDTH, y1 = y0 + hgt;

        // Zoom-in animation anchored at the panel's own center, so the
        // launcher grows out of itself rather than unfolding from the
        // corner. Fires every time the GUI opens (introAnim is reset in
        // initGui).
        float lcx = x0 + LAUNCHER_WIDTH / 2.0F;
        float lcy = y0 + hgt / 2.0F;
        GL11.glPushMatrix();
        GL11.glTranslatef(lcx, lcy, 0f);
        GL11.glScalef(intro, intro, 1f);
        GL11.glTranslatef(-lcx, -lcy, 0f);

        // (Drop shadow removed.)

        // Card body.
        RenderUtils.drawRoundedRectAA(x0, y0, x1, y1, LAUNCHER_RADIUS, palette.background);

        // Header strip — same outer coords + radius as the body. Corner
        // mask order is {TL, BL, BR, TR}: round the TOP two, square the
        // BOTTOM two so the header tucks flush against the body below it.
        int headerY = y0 + LAUNCHER_HEADER;
        RenderUtils.drawRoundedRectAA(x0, y0, x1, headerY,
                LAUNCHER_RADIUS, palette.sidebar,
                new boolean[] { true, false, false, true });

        // (Whitish outline removed — relies on the SDF anti-aliased edges
        // of the rounded fill rects for the visual edge.)

        // Logo + name + version stacked, sized for the 38px header.
        int logoSize = 22;
        int logoX = x0 + 8;
        int logoY = y0 + (LAUNCHER_HEADER - logoSize) / 2;
        if (CROW_ICON != null) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1F, 1F, 1F, intro);
            mc.getTextureManager().bindTexture(CROW_ICON);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            Gui.drawModalRectWithCustomSizedTexture(logoX, logoY, 0, 0, logoSize, logoSize, logoSize, logoSize);
            GlStateManager.color(1F, 1F, 1F, 1F);
        }

        int textX = logoX + logoSize + 7;
        FontUtil.semiBold.drawSmoothString(Crow.CLIENT_NAME, textX, y0 + 8, palette.titleText);
        String ver = Crow.versionManager.getClientVersion().toString();
        FontUtil.small.drawSmoothString(ver, textX, y0 + 21, palette.mutedText);

        // (Divider under launcher header removed — accent bars stripped.)

        // Buttons — with per-index slide stagger on the GUI's initial
        // intro animation. Each button enters from 8px above its final
        // position, fully arriving once intro >= (i+1) * 0.1.
        int btnX = x0 + LAUNCHER_PADDING;
        int btnY = headerY + LAUNCHER_PADDING;
        int btnW = LAUNCHER_WIDTH - LAUNCHER_PADDING * 2;
        long elapsed = System.currentTimeMillis() - openTime;
        int idx = 0;
        for (LauncherButton b : buttons) {
            long delay = idx * 30L;
            float reveal = (elapsed - delay) / 220F;
            if (reveal > 1F) reveal = 1F;
            if (reveal < 0F) reveal = 0F;
            reveal = 1F - (1F - reveal) * (1F - reveal) * (1F - reveal); // easeOutCubic
            int yOff = (int) Math.round((1F - reveal) * -8F);

            b.layout(btnX, btnY, btnW, LAUNCHER_BTN_HEIGHT);
            GL11.glPushMatrix();
            if (yOff != 0) GL11.glTranslatef(0f, yOff, 0f);
            b.draw(mouseX, mouseY, palette);
            GL11.glPopMatrix();
            btnY += LAUNCHER_BTN_HEIGHT + LAUNCHER_BTN_GAP;
            idx++;
        }

        // Reset-layout button — at the bottom of the launcher with its
        // own bottom margin so it doesn't crowd the category buttons.
        int resetY = btnY + LAUNCHER_RESET_GAP;
        resetBtnX = btnX;
        resetBtnY = resetY;
        resetBtnW = btnW;
        resetBtnH = LAUNCHER_RESET_HEIGHT;
        boolean resetHover = mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW
                && mouseY >= resetBtnY && mouseY <= resetBtnY + resetBtnH;
        int resetIdle = CompactModuleCard.blendColor(palette.card, palette.hoverCard, 0.55F);
        int resetHovered = CompactModuleCard.blendColor(palette.hoverCard, palette.sidebarSelected, 0.50F);
        int resetBg = CompactModuleCard.blendColor(resetIdle, resetHovered, resetHover ? 1.0F : 0.0F);
        RenderUtils.drawRoundedRectAA(resetBtnX, resetBtnY, resetBtnX + resetBtnW, resetBtnY + resetBtnH, 6, resetBg);
        FontUtil.small.drawCenteredSmoothString("Reset Layout",
                resetBtnX + resetBtnW / 2.0F, resetBtnY + (resetBtnH - 8) / 2,
                resetHover ? palette.titleText : palette.mutedText);

        GL11.glPopMatrix();
    }

    /** Bounds of the reset-layout button (recomputed every frame). */
    private int resetBtnX, resetBtnY, resetBtnW, resetBtnH;

    private int launcherHeight() {
        int n = buttons.size();
        int btns = n * LAUNCHER_BTN_HEIGHT + Math.max(0, n - 1) * LAUNCHER_BTN_GAP;
        return LAUNCHER_HEADER + LAUNCHER_PADDING + btns
                + LAUNCHER_RESET_GAP + LAUNCHER_RESET_HEIGHT
                + LAUNCHER_PADDING;
    }

    private int clampLauncherX(int v) { return Math.max(0, Math.min(width - LAUNCHER_WIDTH, v)); }
    private int clampLauncherY(int v) { return Math.max(0, Math.min(Math.max(0, height - launcherHeight()), v)); }

    int clampTabX(int v) { return Math.max(0, Math.min(width - SpaciousCategoryTab.TAB_WIDTH, v)); }
    int clampTabY(int v, int tabHeight) { return Math.max(0, Math.min(Math.max(0, height - tabHeight), v)); }

    @Override
    public void mouseClicked(int x, int y, int mouseButton) throws IOException {
        // Topmost-first.
        List<SpaciousCategoryTab> order = new ArrayList<>();
        if (topTab != null && topTab.visible) order.add(topTab);
        for (int i = tabs.size() - 1; i >= 0; i--) {
            SpaciousCategoryTab t = tabs.get(i);
            if (t.visible && t != topTab) order.add(t);
        }
        for (SpaciousCategoryTab t : order) {
            if (t.mouseClicked(x, y, mouseButton)) {
                topTab = t;
                return;
            }
        }

        // Reset layout — clears tab positions back to the default grid.
        if (mouseButton == 0
                && x >= resetBtnX && x <= resetBtnX + resetBtnW
                && y >= resetBtnY && y <= resetBtnY + resetBtnH) {
            resetTabLayout();
            return;
        }

        // Launcher buttons.
        for (LauncherButton b : buttons) {
            if (b.contains(x, y) && mouseButton == 0) {
                b.toggle();
                if (b.tab.visible) {
                    // Tab keeps its last position — if the user previously
                    // dragged it somewhere, it reappears there on re-
                    // toggle. The default cascade-column position is
                    // applied once on firstInit (and by Reset Layout);
                    // we deliberately do NOT re-snap to the stack on
                    // every toggle, so position survives off→on cycles.
                    topTab = b.tab;
                }
                // Persist immediately so visibility changes survive
                // GUI close + game restart even if no drag follows.
                if (Crow.clientConfig != null) {
                    Crow.clientConfig.saveConfig();
                }
                return;
            }
        }

        // Launcher header drag.
        if (mouseButton == 0
                && x >= launcherX && x <= launcherX + LAUNCHER_WIDTH
                && y >= launcherY && y <= launcherY + LAUNCHER_HEADER) {
            launcherDragging = true;
            launcherDragOffsetX = x - launcherX;
            launcherDragOffsetY = y - launcherY;
        }
    }

    @Override
    public void mouseReleased(int x, int y, int mouseButton) {
        for (SpaciousCategoryTab t : tabs) {
            t.mouseReleased(x, y, mouseButton);
        }
        launcherDragging = false;
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
        // Find the topmost visible tab under the cursor and route scroll
        // to it. If no tab is under the cursor, the wheel event is ignored
        // (we don't scroll the launcher — it's small enough to never need
        // it).
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (topTab != null && topTab.visible && topTab.containsPoint(mx, my)) {
            topTab.scroll(wheel);
            return;
        }
        for (int i = tabs.size() - 1; i >= 0; i--) {
            SpaciousCategoryTab t = tabs.get(i);
            if (t.visible && t != topTab && t.containsPoint(mx, my)) {
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
        out.addProperty("launcherX", launcherX);
        out.addProperty("launcherY", launcherY);
        com.google.gson.JsonObject perTab = new com.google.gson.JsonObject();
        for (SpaciousCategoryTab tab : tabs) {
            com.google.gson.JsonObject td = new com.google.gson.JsonObject();
            td.addProperty("x", tab.x);
            td.addProperty("y", tab.y);
            td.addProperty("visible", tab.visible);
            td.addProperty("collapsed", tab.collapsed);
            perTab.add(tab.getCategory().name(), td);
        }
        out.add("tabs", perTab);
        return out;
    }

    public void applyState(com.google.gson.JsonObject state) {
        if (state == null) return;
        if (state.has("launcherX") && state.has("launcherY")) {
            launcherX = state.get("launcherX").getAsInt();
            launcherY = state.get("launcherY").getAsInt();
        }
        com.google.gson.JsonObject perTab = state.has("tabs") ? state.get("tabs").getAsJsonObject() : null;
        if (perTab != null) {
            for (SpaciousCategoryTab tab : tabs) {
                String key = tab.getCategory().name();
                if (!perTab.has(key)) continue;
                com.google.gson.JsonObject td = perTab.get(key).getAsJsonObject();
                if (td.has("x") && td.has("y")) {
                    tab.setPosition(td.get("x").getAsInt(), td.get("y").getAsInt());
                }
                if (td.has("visible")) {
                    tab.visible = td.get("visible").getAsBoolean();
                }
                if (td.has("collapsed")) {
                    tab.collapsed = td.get("collapsed").getAsBoolean();
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

    void applyTabClipScissor(int x, int y, int w, int h) {
        // Inline settings render inside the tab body scissor already pushed
        // by the tab's draw(); no extra clip needed.
    }

    void restoreTabScissor() {
        // No-op.
    }

    /** Pick a contrast-appropriate text color for the given background.
     *  Returns near-black for bright backgrounds, white for dark ones.
     *  Used so module / button labels stay readable when the theme tint
     *  shifts a panel's luminance. */
    static int pickContrastText(int bgArgb) {
        int r = (bgArgb >> 16) & 0xFF;
        int g = (bgArgb >>  8) & 0xFF;
        int b = bgArgb & 0xFF;
        float lum = (0.299F * r + 0.587F * g + 0.114F * b) / 255F;
        return lum > 0.58F ? 0xFF121820 : 0xFFFFFFFF;
    }

    /* ====================================================================== */
    /* Launcher button — toggles visibility of its category tab.              */
    /* ====================================================================== */

    private static final class LauncherButton {
        final SpaciousCategoryTab tab;
        int x, y, w, h;
        private final Animation hoverAnim = new Animation(140, Animation::easeOutCubic);
        private final Animation openAnim = new Animation(180, Animation::easeOutCubic);

        LauncherButton(SpaciousCategoryTab tab) {
            this.tab = tab;
        }

        void layout(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }

        boolean contains(int mx, int my) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

        void toggle() { tab.visible = !tab.visible; }

        void draw(int mx, int my, CompactPalette palette) {
            boolean hovered = contains(mx, my);
            hoverAnim.setTarget(hovered ? 1F : 0F);
            hoverAnim.update();
            float hover = hoverAnim.get();

            openAnim.setTarget(tab.visible ? 1F : 0F);
            openAnim.update();
            float open = openAnim.get();

            int themeColor = 0xFF000000 | (GuiModule.getThemeColor(0) & 0x00FFFFFF);
            int themeRgb = themeColor & 0x00FFFFFF;

            // Bright, actively-lit base. The previous 0.55/0.45 blends
            // sat too close to the dark `card` color and read as muted.
            // Idle now leans fully toward hoverCard, hover bites into
            // sidebarSelected, and open-state mixes in a stronger theme
            // tint with a subtle theme rim. Reads as a real button.
            int idleBg = CompactModuleCard.blendColor(palette.card, palette.hoverCard, 0.90F);
            int hoverBg = CompactModuleCard.blendColor(palette.hoverCard, palette.sidebarSelected, 0.80F);
            int bg = CompactModuleCard.blendColor(idleBg, hoverBg, hover);
            if (open > 0.02F) {
                // Open state pops with a stronger theme tint (was 0.55, now 0.70).
                bg = CompactModuleCard.blendColor(bg, themeColor, open * 0.70F);
            }
            RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, 7, bg);

            // Subtle theme-tinted rim on hover / open so the active
            // button has a defined edge, not just a fill blend.
            if (hover > 0.02F || open > 0.02F) {
                int rimAlpha = Math.min(200,
                        (int) (60 + hover * 60 + open * 100));
                int rim = (rimAlpha << 24) | themeRgb;
                RenderUtils.drawRoundedOutline(x, y, x + w, y + h, 7, 1.0F, rim);
            }

            // Text — start much higher than muted (was floor at mutedText,
            // now floor at 65% toward titleText) so the label is legible
            // at rest, then fully bright on hover/open.
            String label = titleCase(tab.getCategory().name());
            float textT = Math.max(0.65F, Math.max(hover * 0.85F, open));
            int textColor = CompactModuleCard.blendColor(palette.mutedText, palette.titleText, textT);
            FontUtil.semiBold.drawSmoothString(label, x + 12, y + (h - 9) / 2 + 1, textColor);

            // Right-side dot indicator — solid theme color when open,
            // brighter idle dot than before for visual punch.
            int dotX = x + w - 12;
            int dotY = y + h / 2;
            int dotColor = open > 0.5F
                    ? 0xFF000000 | themeRgb
                    : (Math.max(120, (int)(140 + hover * 100)) << 24) | 0xFFFFFF;
            RenderUtils.drawRoundedRectAA(dotX - 3, dotY - 3, dotX + 3, dotY + 3, 3, dotColor);
        }

        private static String titleCase(String raw) {
            if (raw == null || raw.isEmpty()) return "";
            String s = raw.toLowerCase();
            StringBuilder out = new StringBuilder(s.length());
            boolean upper = true;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '_' || c == ' ') { out.append(' '); upper = true; }
                else if (upper) { out.append(Character.toUpperCase(c)); upper = false; }
                else out.append(c);
            }
            return out.toString();
        }
    }
}
