package crow.client.clickgui.compact;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import crow.client.clickgui.crow.ClickGui;
import crow.client.config.Config;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.Module.ModuleCategory;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.modules.config.ConfigSettings;
import crow.client.module.modules.themes.ThemeModule;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ButtonSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class CompactGui extends GuiScreen {

    private static final int SIDEBAR_WIDTH = 108;
    private static final int PANEL_RADIUS = 16;
    private static final int PANEL_PADDING = 12;
    private static final int CONTENT_PADDING = 16;
    private static final int CATEGORY_HEIGHT = 18;
    private static final int CATEGORY_GAP = 4;
    private static final int HEADER_HEIGHT = 40;
    private static final int CARD_HEIGHT = 46;
    private static final int CARD_GAP = 6;

    private static final long CARD_STAGGER_MS = 35L;

    private static final long CARD_REVEAL_MS = 220L;
    private static final int CONFIG_ROW_HEIGHT = 38;
    private static final int CONFIG_SECTION_GAP = 10;
    private static final int THEME_CARD_HEIGHT = 88;
    private static final int THEME_CARD_GAP = 10;
    private static final int DRAG_ZONE_H = 34;
    private static final int DETACHED_PANEL_WIDTH = 304;
    private static final int DETACHED_PANEL_GAP = 12;
    private static final int DETACHED_PANEL_HEADER = 50;
    private static final int DETACHED_PANEL_PADDING = 12;
    private static final ResourceLocation BLUR_SHADER = new ResourceLocation("shaders/post/blur.json");
    private static final ResourceLocation CROW_ICON = RenderUtils.getResourcePath("/assets/crow/crow.png");
    private static final ResourceLocation SEARCH_ICON = RenderUtils.getResourcePath("/assets/crow/crowclickgui/search.png");
    private static final SimpleDateFormat CONFIG_TIME_FORMAT = new SimpleDateFormat("MMM dd yyyy");

    private int containerX;
    private int containerY;
    private int containerW;
    private int containerH;

    private ModuleCategory selectedCategory = ModuleCategory.combat;
    private final List<CompactCategoryItem> categoryItems = new ArrayList<>();
    private final List<CompactModuleCard> moduleCards = new ArrayList<>();
    private CompactModuleCard expandedCard;
    private CompactBind activeBindCard;

    int contentScissorX;
    int contentScissorY;
    int contentScissorW;
    int contentScissorH;
    int detachedScissorX;
    int detachedScissorY;
    int detachedScissorW;
    int detachedScissorH;

    private float openAnimation;

    private final crow.client.utils.anim.Animatable contentSwapAnim =
            new crow.client.utils.anim.Animatable(1.0F, 14.0F);
    private final crow.client.utils.anim.Animatable smoothScrollAnim =
            new crow.client.utils.anim.Animatable(0.0F, 22.0F);
    private final crow.client.utils.anim.Animatable detachedScrollAnim =
            new crow.client.utils.anim.Animatable(0.0F, 22.0F);
    private long openTime;
    private long lastLayoutSave;
    private int scrollTarget;

    private long contentSwapStartTime;
    private int detachedScrollTarget;
    private boolean detachedDragging;
    private int detachedDragOffsetX;
    private int detachedDragOffsetY;
    private int detachedPanelX = Integer.MIN_VALUE;
    private int detachedPanelY = Integer.MIN_VALUE;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    private boolean scrollbarVisible;
    private int scrollbarThumbX, scrollbarThumbY, scrollbarThumbW, scrollbarThumbH;
    private int scrollbarTrackTop, scrollbarTrackBottom;
    private int scrollbarContentH;
    private boolean scrollbarDragging;
    private int scrollbarDragStartMouseY;
    private int scrollbarDragStartScroll;

    private boolean detachedScrollbarVisible;
    private int detachedScrollbarThumbX, detachedScrollbarThumbY, detachedScrollbarThumbW, detachedScrollbarThumbH;
    private int detachedScrollbarTrackTop, detachedScrollbarTrackBottom;
    private int detachedScrollbarContentH;
    private boolean detachedScrollbarDragging;
    private int detachedScrollbarDragStartMouseY;
    private int detachedScrollbarDragStartScroll;

    private GuiTextField searchField;
    private Config pendingDeleteConfig;
    private long themeBounceStart;
    private String themeBounceName;
    private int savedContainerX = Integer.MIN_VALUE;
    private int savedContainerY = Integer.MIN_VALUE;

    private static final ModuleCategory[] VISIBLE_CATEGORIES = {
            ModuleCategory.search, ModuleCategory.combat, ModuleCategory.movement, ModuleCategory.player,
            ModuleCategory.world, ModuleCategory.render, ModuleCategory.other, ModuleCategory.client, ModuleCategory.themes, ModuleCategory.config
    };

    public CompactGui() {
        for (ModuleCategory cat : VISIBLE_CATEGORIES) {
            categoryItems.add(new CompactCategoryItem(cat));
        }
        rebuildModuleCards();
    }

    private void rebuildModuleCards() {
        moduleCards.clear();
        expandedCard = null;
        activeBindCard = null;
        contentSwapAnim.snap(0.0F);

        contentSwapStartTime = System.currentTimeMillis();
        List<Module> modules = getFilteredModules();
        for (Module mod : modules) {
            moduleCards.add(new CompactModuleCard(mod, this));
        }
    }

    private List<Module> getFilteredModules() {
        String query = getSearchQuery();
        List<Module> modules = new ArrayList<>();
        if (selectedCategory == ModuleCategory.search) {
            for (ModuleCategory category : VISIBLE_CATEGORIES) {
                if (category == ModuleCategory.search || category == ModuleCategory.themes || category == ModuleCategory.config) {
                    continue;
                }
                modules.addAll(Crow.moduleManager.getModulesInCategory(category));
            }
            if (!query.isEmpty()) {
                modules.removeIf(module -> !module.getName().toLowerCase().contains(query));
            }
            modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
            return modules;
        }
        if (query.isEmpty()) {
            modules.addAll(Crow.moduleManager.getModulesInCategory(selectedCategory));
            return modules;
        }

        for (ModuleCategory category : VISIBLE_CATEGORIES) {
            if (category == ModuleCategory.search || category == ModuleCategory.themes || category == ModuleCategory.config) {
                continue;
            }
            modules.addAll(Crow.moduleManager.getModulesInCategory(category));
        }

        modules.removeIf(module -> !module.getName().toLowerCase().contains(query));
        modules.sort(Comparator
                .comparing((Module module) -> module.moduleCategory().getName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        return modules;
    }

    public boolean isSearching() {
        return !getSearchQuery().isEmpty();
    }

    private String getSearchQuery() {
        return searchField == null ? "" : searchField.getText().trim().toLowerCase();
    }

    public void selectCategory(ModuleCategory cat) {
        if (cat == selectedCategory) {
            return;
        }
        selectedCategory = cat;
        scrollTarget = 0;
        smoothScrollAnim.snap(0.0F);
        detachedScrollTarget = 0;
        detachedScrollAnim.snap(0.0F);
        contentSwapAnim.snap(0.0F);
        pendingDeleteConfig = null;
        rebuildModuleCards();
    }

    public void setExpandedCard(CompactModuleCard card) {
        expandedCard = expandedCard == card ? null : card;
        detachedScrollTarget = 0;
        detachedScrollAnim.snap(0.0F);
        clampScroll();
    }

    public CompactModuleCard getExpandedCard() {
        return expandedCard;
    }

    public void setActiveBindCard(CompactBind bind) {
        activeBindCard = bind;
    }

    public CompactBind getActiveBindCard() {
        return activeBindCard;
    }

    public float getRevealTargetForIndex(int index) {

        long elapsed = System.currentTimeMillis() - contentSwapStartTime;
        long delay = (long) index * CARD_STAGGER_MS;
        return elapsed >= delay ? 1.0F : 0.0F;
    }

    @Override
    public void setWorldAndResolution(net.minecraft.client.Minecraft mcIn, int scaledW, int scaledH) {
        // Render the GUI in display-pixel coordinates so it looks the same
        // physical size at every guiScale setting. Passing displayWidth/Height
        // as the GuiScreen's width/height has two effects:
        //  1. Layout math in initGui (containerW/H, etc.) is in display px.
        //  2. super.handleMouseInput's mouse-coord calculation
        //     (Mouse.getEventX() * this.width / displayWidth) reduces to just
        //     Mouse.getEventX(), so mouse handlers receive display px too —
        //     same coordinate space as the layout. No conversion needed.
        super.setWorldAndResolution(mcIn, mcIn.displayWidth, mcIn.displayHeight);
    }

    @Override
    public void initGui() {
        super.initGui();
        openTime = System.currentTimeMillis();
        openAnimation = 0.0F;
        contentSwapAnim.snap(Math.max(contentSwapAnim.get(), 0.75F));

        contentSwapStartTime = openTime;

        int availW = Math.max(0, this.width  - 80);
        int availH = Math.max(0, this.height - 60);

        int unitFromW = availW / 3;
        int unitFromH = availH / 2;
        int maxUnitFit = Math.min(unitFromW, unitFromH);

        int unit = (int) (maxUnitFit * 0.575F);
        final int MIN_UNIT = 125;
        final int MAX_UNIT = 213;
        unit = Math.max(MIN_UNIT, Math.min(MAX_UNIT, unit));

        if (maxUnitFit > 0 && unit > maxUnitFit) {
            unit = maxUnitFit;
        }

        containerW = unit * 3;
        containerH = unit * 2;
        int defaultX = (this.width - containerW) / 2;
        int defaultY = (this.height - containerH) / 2;
        containerX = savedContainerX == Integer.MIN_VALUE ? defaultX : savedContainerX;
        containerY = savedContainerY == Integer.MIN_VALUE ? defaultY : savedContainerY;
        clampContainerToScreen();
        searchField = new GuiTextField(0, mc.fontRendererObj, 0, 0, 110, 16);
        searchField.setCanLoseFocus(true);
        searchField.setMaxStringLength(40);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setFocused(false);

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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!Display.isActive()) {
            persistLayoutState(true);
            mc.displayGuiScreen(null);
            return;
        }

        // Cancel Minecraft's GUI-scale projection so 1 unit = 1 display px.
        // Combined with setWorldAndResolution feeding displayWidth/Height,
        // this makes the GUI render at a fixed physical size regardless of
        // the user's guiScale setting.
        int sf = Math.max(1, new ScaledResolution(mc).getScaleFactor());
        GL11.glPushMatrix();
        GL11.glScalef(1.0F / sf, 1.0F / sf, 1.0F);
        try {
            crow.client.utils.MSAAFramebuffer.begin();
            try {
                drawScreenMSAA(mouseX, mouseY, partialTicks);
            } finally {
                crow.client.utils.MSAAFramebuffer.end();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private void glScissorDp(int x, int y, int w, int h) {
        GL11.glScissor(x, mc.displayHeight - (y + h), w, h);
    }

    private void drawScreenMSAA(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        ScaledResolution sr = new ScaledResolution(mc);
        ClickGui.screenWidth = sr.getScaledWidth();
        ClickGui.screenHeight = sr.getScaledHeight();

        long openElapsed = System.currentTimeMillis() - openTime;
        float bounceT = Math.min(1.0F, openElapsed / 380.0F);
        float bt = bounceT - 1.0F;
        float overshoot = 1.70158F;
        float bounceEase = bt * bt * ((overshoot + 1.0F) * bt + overshoot) + 1.0F;
        openAnimation = Math.min(1.0F, openElapsed / 250.0F);

        contentSwapAnim.setTarget(1.0F);
        contentSwapAnim.update();

        smoothScrollAnim.setTarget(scrollTarget);
        smoothScrollAnim.update();
        clampScroll();
        detachedScrollAnim.setTarget(detachedScrollTarget);
        detachedScrollAnim.update();

        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (dragging) {
            containerX = clampContainerX(mouseX - dragOffsetX);
            containerY = clampContainerY(mouseY - dragOffsetY);
            persistLayoutState(false);
        }
        if (detachedDragging && expandedCard != null && expandedCard.usesDetachedSettingsPanel()) {
            int[] bounds = getDetachedPanelBounds();
            detachedPanelX = clampDetachedPanelX(mouseX - detachedDragOffsetX, bounds[2]);
            detachedPanelY = clampDetachedPanelY(mouseY - detachedDragOffsetY, bounds[3]);
        }

        if (scrollbarDragging && scrollbarVisible) {
            int totalContent = getTotalContentHeight();
            int trackUsable = (scrollbarTrackBottom - scrollbarTrackTop) - scrollbarThumbH;
            int contentRange = totalContent - scrollbarContentH;
            if (trackUsable > 0 && contentRange > 0) {
                int dy = mouseY - scrollbarDragStartMouseY;
                float ratio = contentRange / (float) trackUsable;
                scrollTarget = scrollbarDragStartScroll - Math.round(dy * ratio);
                clampScroll();
            }
        }
        if (detachedScrollbarDragging && detachedScrollbarVisible) {
            int totalContent = getDetachedContentHeight();
            int trackUsable = (detachedScrollbarTrackBottom - detachedScrollbarTrackTop) - detachedScrollbarThumbH;
            int contentRange = totalContent - detachedScrollbarContentH;
            if (trackUsable > 0 && contentRange > 0) {
                int dy = mouseY - detachedScrollbarDragStartMouseY;
                float ratio = contentRange / (float) trackUsable;
                detachedScrollTarget = detachedScrollbarDragStartScroll - Math.round(dy * ratio);
                clampDetachedScroll();
            }
        }

        CompactPalette palette = GuiModule.getCompactPalette();
        float scale = 0.5F + 0.5F * bounceEase;

        int scrimColor = palette.scrim;
        int scrimBaseAlpha = (scrimColor >> 24) & 0xFF;
        int scrimAlpha = (int) (scrimBaseAlpha * openAnimation);
        Gui.drawRect(0, 0, width, height, (scrimAlpha << 24) | (scrimColor & 0x00FFFFFF));

        GL11.glPushMatrix();
        GL11.glTranslatef(containerX + containerW / 2.0F, containerY + containerH / 2.0F, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        GL11.glTranslatef(-(containerX + containerW / 2.0F), -(containerY + containerH / 2.0F), 0.0F);

        drawLayout(mouseX, mouseY, palette);

        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (pendingDeleteConfig != null) {
            drawDeleteConfirmation(mouseX, mouseY, palette);
        }
    }

    private void drawLayout(int mouseX, int mouseY, CompactPalette palette) {

        RenderUtils.drawRoundedRectAA(containerX, containerY,
                containerX + containerW, containerY + containerH, PANEL_RADIUS, palette.background);
        RenderUtils.drawRoundedRectAA(containerX, containerY,
                containerX + SIDEBAR_WIDTH, containerY + containerH, PANEL_RADIUS, palette.sidebar,
                new boolean[]{true, true, false, false});
        RenderUtils.drawRoundedRectAA(containerX + SIDEBAR_WIDTH, containerY,
                containerX + containerW, containerY + containerH, PANEL_RADIUS, palette.content,
                new boolean[]{false, false, true, true});

        drawSidebar(mouseX, mouseY, palette);
        GL11.glPushMatrix();

        float slideOffset = (1.0F - contentSwapAnim.get()) * (1.0F - contentSwapAnim.get()) * 16.0F;
        float fadeAlpha = Math.min(1.0F, contentSwapAnim.get() * 1.5F);
        GL11.glTranslatef(slideOffset, 0.0F, 0.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, fadeAlpha);
        drawContent(mouseX, mouseY, palette);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
        drawDetachedSettingsPanel(mouseX, mouseY, palette);
    }

    private void drawSidebar(int mouseX, int mouseY, CompactPalette palette) {

        int brandIconSize = 18;
        int brandGap = 6;
        int brandTextHeight = (int) Math.ceil(FontUtil.bold.getHeight());
        int brandTextWidth = (int) Math.ceil(FontUtil.bold.getStringWidth("Crow"));
        int brandRowHeight = Math.max(brandIconSize, brandTextHeight);
        int brandRowWidth = brandIconSize + brandGap + brandTextWidth;
        int brandX = containerX + (SIDEBAR_WIDTH - brandRowWidth) / 2;
        int brandY = containerY + 12;
        int iconY = brandY + (brandRowHeight - brandIconSize) / 2;
        int textY = brandY + (brandRowHeight - brandTextHeight) / 2;

        if (CROW_ICON != null) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(CROW_ICON);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            Gui.drawModalRectWithCustomSizedTexture(brandX, iconY, 0, 0, brandIconSize, brandIconSize, brandIconSize, brandIconSize);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        FontUtil.bold.drawSmoothString("Crow", brandX + brandIconSize + brandGap, textY, 0xFFFFFFFF);
        int catY = brandY + brandRowHeight + 10;
        int catX = containerX + 8;
        int catW = SIDEBAR_WIDTH - 16;
        for (CompactCategoryItem item : categoryItems) {
            item.setPosition(catX, catY, catW, CATEGORY_HEIGHT);
            item.draw(mouseX, mouseY, selectedCategory == item.getCategory(), palette);
            catY += CATEGORY_HEIGHT + CATEGORY_GAP;
        }
    }

    private void drawContent(int mouseX, int mouseY, CompactPalette palette) {
        int rightX = containerX + SIDEBAR_WIDTH;
        int contentX = rightX + CONTENT_PADDING;
        int contentW = containerW - SIDEBAR_WIDTH - CONTENT_PADDING * 2;

        int headerCenterY = containerY + HEADER_HEIGHT / 2;
        int headerY = containerY + 10;
        int searchW = 134;
        int searchH = 18;
        int searchX = contentX + contentW - searchW;
        int searchY = headerCenterY - searchH / 2;

        boolean customConfigView = isConfigView();
        boolean customThemeView = isThemeView();
        boolean customSearchView = isSearchView();
        if (customSearchView) {
            searchField.xPosition = contentX + 28;
            searchField.yPosition = (containerY + HEADER_HEIGHT + 10) + (int) smoothScrollAnim.get() + 40;
            searchField.width = contentW - 46;
            searchField.height = 18;
        }
        if (!customConfigView && !customThemeView && !customSearchView) {
            searchField.xPosition = searchX + 10;
            searchField.yPosition = searchY + 5;
            searchField.width = searchW - 34;
            searchField.height = 10;

            RenderUtils.drawRoundedRectAA(searchX, searchY, searchX + searchW, searchY + searchH, 8,
                    searchField.isFocused() ? blendSearchColor(palette.card, palette.hoverCard, 0.65F) : palette.card);
            RenderUtils.drawRoundedOutline(searchX, searchY, searchX + searchW, searchY + searchH, 8, 1.0F,
                    searchField.isFocused() ? 0x55FFFFFF : palette.separator);
            FontUtil.small.drawSmoothString("S", searchX + 7, searchY + 6, palette.mutedText);

            if (searchField.getText().isEmpty() && !searchField.isFocused()) {
                FontUtil.small.drawSmoothString("Search modules...", searchX + 20, searchY + 6, palette.mutedText);
            } else {
                String text = searchField.getText();
                FontUtil.small.drawSmoothString(text, searchX + 20, searchY + 6, palette.titleText);

                if (searchField.isFocused() && System.currentTimeMillis() % 1000 > 500) {
                    int cursorX = searchX + 20 + (text.isEmpty() ? 0 : (int) FontUtil.small.getStringWidth(text));
                    Gui.drawRect(cursorX, searchY + 5, cursorX + 1, searchY + 14, palette.titleText);
                }
            }

            if (!searchField.getText().isEmpty()) {
                FontUtil.small.drawSmoothString("x", searchX + searchW - 11, searchY + 6, palette.titleText);
            } else {

                int chipBg   = 0x14FFFFFF;
                int chipText = palette.mutedText;
                int chipH    = 12;
                int chipY    = searchY + (searchH - chipH) / 2;

                String fLabel  = "F";
                int    fW      = (int) FontUtil.small.getStringWidth(fLabel) + 8;
                int    fX      = searchX + searchW - 4 - fW;
                RenderUtils.drawRoundedRectAA(fX, chipY, fX + fW, chipY + chipH, 3, chipBg);
                FontUtil.small.drawSmoothString(fLabel,
                        fX + (fW - FontUtil.small.getStringWidth(fLabel)) / 2.0F,
                        chipY + 2, chipText);

                int    plusX = fX - 8;
                FontUtil.small.drawSmoothString("+", plusX, searchY + 6, chipText);

                String cLabel  = "Ctrl";
                int    cW      = (int) FontUtil.small.getStringWidth(cLabel) + 8;
                int    cX      = plusX - 4 - cW;
                RenderUtils.drawRoundedRectAA(cX, chipY, cX + cW, chipY + chipH, 3, chipBg);
                FontUtil.small.drawSmoothString(cLabel,
                        cX + (cW - FontUtil.small.getStringWidth(cLabel)) / 2.0F,
                        chipY + 2, chipText);
            }
        }

        String headerTitle = customConfigView ? "Configs" : customThemeView ? "Themes" : customSearchView ? "Search" : getSearchQuery().isEmpty() ? selectedCategory.getName() : "Search";
        int dividerY = containerY + HEADER_HEIGHT;

        int titleY = headerCenterY - 3;
        int subtitleY = headerCenterY - 1;

        GL11.glPushMatrix();
        GL11.glTranslatef(contentX, titleY, 0);
        GL11.glScalef(1.4F, 1.4F, 1.0F);
        FontUtil.bold.drawSmoothString(headerTitle, 0, 0, palette.titleText);
        GL11.glPopMatrix();

        String subtitle = customConfigView
                ? Crow.configManager.getConfigs().size() + " saved configs"
                : customThemeView
                    ? getThemeModules().size() + " themes"
                : customSearchView
                    ? moduleCards.size() + " indexed modules"
                : getSearchQuery().isEmpty()
                    ? moduleCards.size() + " modules"
                    : moduleCards.size() + " results";
        int subtitleRight = (customConfigView || customThemeView || customSearchView) ? contentX + contentW : searchX - 10;
        FontUtil.small.drawSmoothString(subtitle,
                subtitleRight - (int) FontUtil.small.getStringWidth(subtitle),
                subtitleY, palette.mutedText);
        if (!customConfigView && !customThemeView && !customSearchView && isSearching()) {
            FontUtil.small.drawSmoothString("Showing matches across every category", contentX, headerCenterY + 6, palette.mutedText);
        }

        RenderUtils.drawFlowingGradientRoundedRect(contentX, dividerY, contentX + contentW, dividerY + 2, 1, 120, 0);

        int contentY = dividerY + 10;
        int contentH = containerH - (contentY - containerY) - 12;

        contentScissorX = rightX + 8;
        contentScissorY = contentY - 8;
        contentScissorW = containerW - SIDEBAR_WIDTH - 16;
        contentScissorH = contentH + 8;

        glScissorDp(contentScissorX, contentScissorY, contentScissorW, contentScissorH);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        if (customConfigView) {
            drawConfigRows(mouseX, mouseY, palette, contentX, contentW, contentY + (int) smoothScrollAnim.get());
        } else if (customThemeView) {
            drawThemeCards(mouseX, mouseY, palette, contentX, contentW, contentY + (int) smoothScrollAnim.get());
        } else if (customSearchView) {
            drawSearchView(mouseX, mouseY, palette, contentX, contentW, contentY + (int) smoothScrollAnim.get());
        } else {
            int cardY = contentY + (int) smoothScrollAnim.get();
            for (int i = 0; i < moduleCards.size(); i++) {
                CompactModuleCard card = moduleCards.get(i);
                card.setListIndex(i);
                card.setPosition(contentX, cardY, contentW, CARD_HEIGHT);
                card.drawHeader(mouseX, mouseY, palette);
                cardY += card.getTotalHeight() + CARD_GAP;
            }
            if (moduleCards.isEmpty()) {
                drawEmptyState(palette, contentX, contentY, contentW, contentH);
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        if (!customConfigView && !customThemeView && expandedCard != null && !expandedCard.usesDetachedSettingsPanel()) {
            glScissorDp(contentScissorX, contentScissorY, contentScissorW, contentScissorH);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            expandedCard.drawExpandedSettings(mouseX, mouseY, palette);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        drawScrollDecorations(palette, contentX + contentW - 3, contentY, contentH, mouseX, mouseY);
    }

    private void drawDetachedSettingsPanel(int mouseX, int mouseY, CompactPalette palette) {
        if (expandedCard == null || !expandedCard.usesDetachedSettingsPanel()) {
            detachedScissorW = detachedScissorH = 0;
            return;
        }

        clampDetachedScroll();

        int[] bounds = getDetachedPanelBounds();
        int panelX = bounds[0];
        int panelY = bounds[1];
        int panelW = bounds[2];
        int panelH = bounds[3];
        int contentX = panelX + DETACHED_PANEL_PADDING;
        int contentY = panelY + DETACHED_PANEL_HEADER;
        int contentW = panelW - DETACHED_PANEL_PADDING * 2;
        int contentH = panelH - DETACHED_PANEL_HEADER - DETACHED_PANEL_PADDING;

        RenderUtils.drawRoundedRectAA(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_RADIUS, palette.background);
        RenderUtils.drawRoundedRectAA(panelX, panelY, panelX + panelW, panelY + DETACHED_PANEL_HEADER, PANEL_RADIUS, palette.content,
                new boolean[]{true, true, false, false});
        RenderUtils.drawFlowingGradientRoundedRect(panelX + 1, panelY + DETACHED_PANEL_HEADER - 2, panelX + panelW - 1, panelY + DETACHED_PANEL_HEADER, 1, 110, 0);

        FontUtil.bold.drawSmoothString(expandedCard.mod.getName(), panelX + 14, panelY + 14, palette.titleText);
        FontUtil.small.drawSmoothString("Dedicated manager settings", panelX + 14, panelY + 29, palette.mutedText);

        detachedScissorX = contentX;
        detachedScissorY = contentY;
        detachedScissorW = contentW;
        detachedScissorH = contentH;

        glScissorDp(detachedScissorX, detachedScissorY, detachedScissorW, detachedScissorH);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        expandedCard.drawDetachedSettings(mouseX, mouseY, palette, contentX, contentY, contentW, (int) detachedScrollAnim.get());
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        drawDetachedScrollDecorations(palette, panelX + panelW - 8, contentY, contentH, mouseX, mouseY);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (pendingDeleteConfig != null) {
            handleDeleteConfirmationClick(mouseX, mouseY, mouseButton);
            return;
        }

        if (activeBindCard != null) {
            activeBindCard.mouseBind(mouseButton);
            return;
        }

        for (CompactCategoryItem item : categoryItems) {
            if (item.isMouseOver(mouseX, mouseY)) {
                selectCategory(item.getCategory());
                return;
            }
        }

        if (searchField != null) {
            if (!isConfigView() && !isThemeView()) {
                int visualSearchX = searchField.xPosition - 10;
                int visualSearchY = searchField.yPosition - (isSearchView() ? 3 : 5);
                int visualSearchW = searchField.width + 20;
                int visualSearchH = isSearchView() ? 22 : 18;
                if (!searchField.getText().isEmpty() && isOverRect(visualSearchX, visualSearchY, visualSearchW, visualSearchH, mouseX, mouseY)
                        && mouseX >= visualSearchX + visualSearchW - 16 && mouseButton == 0) {
                    searchField.setText("");
                    rebuildModuleCards();
                    scrollTarget = 0;
                    smoothScrollAnim.snap(0.0F);
                    return;
                }
                searchField.mouseClicked(mouseX, mouseY, mouseButton);
                if (searchField.isFocused()) {
                    return;
                }
            }
        }

        if (mouseButton == 0 && isOverDragZone(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - containerX;
            dragOffsetY = mouseY - containerY;
            return;
        }

        if (mouseButton == 0 && isOverScrollbarThumb(mouseX, mouseY)) {
            scrollbarDragging = true;
            scrollbarDragStartMouseY = mouseY;
            scrollbarDragStartScroll = scrollTarget;
            return;
        }

        if (mouseButton == 0 && isOverScrollbarTrack(mouseX, mouseY) && !isOverScrollbarThumb(mouseX, mouseY)) {
            int page = Math.max(40, scrollbarContentH - 20);
            if (mouseY < scrollbarThumbY) {
                scrollTarget += page;
            } else {
                scrollTarget -= page;
            }
            clampScroll();
            return;
        }

        if (mouseButton == 0 && isOverDetachedScrollbarThumb(mouseX, mouseY)) {
            detachedScrollbarDragging = true;
            detachedScrollbarDragStartMouseY = mouseY;
            detachedScrollbarDragStartScroll = detachedScrollTarget;
            return;
        }
        if (mouseButton == 0 && isOverDetachedScrollbarTrack(mouseX, mouseY) && !isOverDetachedScrollbarThumb(mouseX, mouseY)) {
            int page = Math.max(40, detachedScrollbarContentH - 20);
            if (mouseY < detachedScrollbarThumbY) {
                detachedScrollTarget += page;
            } else {
                detachedScrollTarget -= page;
            }
            clampDetachedScroll();
            return;
        }

        if (mouseButton == 0 && isOverDetachedHeader(mouseX, mouseY)) {
            int[] bounds = getDetachedPanelBounds();
            detachedDragging = true;
            detachedDragOffsetX = mouseX - bounds[0];
            detachedDragOffsetY = mouseY - bounds[1];
            return;
        }

        if (expandedCard != null && expandedCard.usesDetachedSettingsPanel() && isOverDetachedPanel(mouseX, mouseY)) {
            int[] bounds = getDetachedPanelBounds();
            if (expandedCard.mouseClickedDetached(mouseX, mouseY, mouseButton,
                    bounds[0] + DETACHED_PANEL_PADDING,
                    bounds[1] + DETACHED_PANEL_HEADER,
                    bounds[2] - DETACHED_PANEL_PADDING * 2,
                    (int) detachedScrollAnim.get())) {
                return;
            }
        }

        if (isConfigView()) {
            if (handleConfigClick(mouseX, mouseY, mouseButton)) {
                return;
            }
        }
        if (isThemeView()) {
            if (handleThemeClick(mouseX, mouseY, mouseButton)) {
                return;
            }
        }

        for (CompactModuleCard card : moduleCards) {
            if (card.mouseClicked(mouseX, mouseY, mouseButton)) {
                clampScroll();
                return;
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            dragging = false;
            detachedDragging = false;
            scrollbarDragging = false;
            detachedScrollbarDragging = false;
            persistLayoutState(true);
        }
        for (CompactModuleCard card : moduleCards) {
            card.mouseReleased(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (activeBindCard != null) {
            activeBindCard.keyTyped(typedChar, keyCode);
            return;
        }
        if (CompactTextInput.handleGlobalKeyTyped(typedChar, keyCode)) {
            return;
        }

        if (expandedCard != null) {
            expandedCard.keyTypedSettings(typedChar, keyCode);
            clampScroll();
        }
        if (searchField != null && searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                searchField.setFocused(false);
                return;
            }
            String before = searchField.getText();
            searchField.textboxKeyTyped(typedChar, keyCode);
            if (!before.equals(searchField.getText())) {
                scrollTarget = 0;
                smoothScrollAnim.snap(0.0F);
                rebuildModuleCards();
            }
            return;
        }
        if ((keyCode == Keyboard.KEY_F && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)))
                && searchField != null) {
            searchField.setFocused(true);
            return;
        }
        if (keyCode == 1) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) {
            return;
        }

        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        if (mx >= contentScissorX && mx <= contentScissorX + contentScissorW
                && my >= contentScissorY && my <= contentScissorY + contentScissorH) {
            scrollTarget += Integer.compare(scroll, 0) * 18;
            clampScroll();
            return;
        }

        if (expandedCard != null && expandedCard.usesDetachedSettingsPanel()
                && mx >= detachedScissorX && mx <= detachedScissorX + detachedScissorW
                && my >= detachedScissorY && my <= detachedScissorY + detachedScissorH) {
            detachedScrollTarget += Integer.compare(scroll, 0) * 18;
            clampDetachedScroll();
        }
    }

    private int getTotalContentHeight() {
        if (isConfigView()) {
            int total = CONFIG_SECTION_GAP;
            total += 20;
            total += getConfigActionButtons().size() * (CONFIG_ROW_HEIGHT + CARD_GAP);
            total += CONFIG_SECTION_GAP + 20;
            total += Crow.configManager.getConfigs().size() * (CONFIG_ROW_HEIGHT + CARD_GAP);
            return total;
        }
        if (isThemeView()) {
            int count = getThemeModules().size();
            int rows = (count + 1) / 2;
            return rows * (THEME_CARD_HEIGHT + THEME_CARD_GAP);
        }
        if (isSearchView()) {
            int total = 82;
            for (CompactModuleCard card : moduleCards) {
                total += card.getTotalHeight() + CARD_GAP;
            }
            return total;
        }
        int total = 0;
        for (CompactModuleCard card : moduleCards) {
            total += card.getTotalHeight() + CARD_GAP;
        }
        return total;
    }

    private void clampScroll() {
        int viewHeight = containerH - HEADER_HEIGHT - 22;
        int totalContent = getTotalContentHeight();
        int maxScroll = 0;
        int minScroll = -(totalContent - viewHeight);
        if (minScroll > 0) {
            minScroll = 0;
        }
        if (scrollTarget > maxScroll) {
            scrollTarget = maxScroll;
        }
        if (scrollTarget < minScroll) {
            scrollTarget = minScroll;
        }
    }

    private int getDetachedContentHeight() {
        if (expandedCard == null || !expandedCard.usesDetachedSettingsPanel()) {
            return 0;
        }
        return expandedCard.getDetachedSettingsHeight();
    }

    private void clampDetachedScroll() {
        if (expandedCard == null || !expandedCard.usesDetachedSettingsPanel()) {
            detachedScrollTarget = 0;
            detachedScrollAnim.snap(0.0F);
            return;
        }

        int viewHeight = containerH - DETACHED_PANEL_HEADER - DETACHED_PANEL_PADDING;
        int totalContent = getDetachedContentHeight();
        int maxScroll = 0;
        int minScroll = -(totalContent - viewHeight);
        if (minScroll > 0) {
            minScroll = 0;
        }
        if (detachedScrollTarget > maxScroll) {
            detachedScrollTarget = maxScroll;
        }
        if (detachedScrollTarget < minScroll) {
            detachedScrollTarget = minScroll;
        }
    }

    private boolean isOverDragZone(int mouseX, int mouseY) {
        return mouseX >= containerX && mouseX <= containerX + containerW
                && mouseY >= containerY && mouseY <= containerY + DRAG_ZONE_H;
    }

    private int clampContainerX(int value) {
        int max = Math.max(0, width - containerW);
        return Math.max(0, Math.min(max, value));
    }

    private int clampContainerY(int value) {
        int max = Math.max(0, height - containerH);
        return Math.max(0, Math.min(max, value));
    }

    private void clampContainerToScreen() {
        containerX = clampContainerX(containerX);
        containerY = clampContainerY(containerY);
        savedContainerX = containerX;
        savedContainerY = containerY;
    }

    private void persistLayoutState(boolean force) {
        savedContainerX = clampContainerX(containerX);
        savedContainerY = clampContainerY(containerY);
        long now = System.currentTimeMillis();
        if (!force && now - lastLayoutSave < 180L) {
            return;
        }
        lastLayoutSave = now;
        if (Crow.clientConfig != null) {
            Crow.clientConfig.updateCompactGuiPosition(savedContainerX, savedContainerY);
        }
    }

    public void setSavedPosition(int x, int y) {
        savedContainerX = x;
        savedContainerY = y;
        if (containerW > 0 && containerH > 0) {
            containerX = x;
            containerY = y;
            clampContainerToScreen();
        }
    }

    public int getSavedContainerX() {
        return savedContainerX == Integer.MIN_VALUE ? containerX : savedContainerX;
    }

    public int getSavedContainerY() {
        return savedContainerY == Integer.MIN_VALUE ? containerY : savedContainerY;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        try {
            if (mc.entityRenderer != null && mc.entityRenderer.isShaderActive()) {
                mc.entityRenderer.stopUseShader();
            }
        } catch (Exception ignored) {
        }
        persistLayoutState(true);
        Crow.configManager.save();
        if (Crow.clientConfig != null) {
            Crow.clientConfig.saveConfig();
        }
        GuiModule.handleGuiClosed();
    }

    private int blendSearchColor(int c1, int c2, float t) {
        return CompactModuleCard.blendColor(c1, c2, t);
    }

    private boolean isConfigView() {
        return selectedCategory == ModuleCategory.config;
    }

    private boolean isThemeView() {
        return selectedCategory == ModuleCategory.themes;
    }

    private boolean isSearchView() {
        return selectedCategory == ModuleCategory.search;
    }

    private ConfigSettings getConfigSettingsModule() {
        Module module = Crow.moduleManager.getModuleByClazz(ConfigSettings.class);
        return module instanceof ConfigSettings ? (ConfigSettings) module : null;
    }

    private List<ButtonSetting> getConfigActionButtons() {
        List<ButtonSetting> buttons = new ArrayList<>();
        ConfigSettings settingsModule = getConfigSettingsModule();
        if (settingsModule == null) {
            return buttons;
        }
        for (Setting setting : settingsModule.getSettings()) {
            if (setting instanceof ButtonSetting) {
                buttons.add((ButtonSetting) setting);
            }
        }
        return buttons;
    }

    private List<ThemeModule> getThemeModules() {
        List<ThemeModule> themes = new ArrayList<>();
        for (Module module : Crow.moduleManager.getModulesInCategory(ModuleCategory.themes)) {
            if (module instanceof ThemeModule) {
                themes.add((ThemeModule) module);
            }
        }
        return themes;
    }

    private void drawConfigRows(int mouseX, int mouseY, CompactPalette palette, int contentX, int contentW, int startY) {
        int rowY = startY;
        FontUtil.semiBold.drawSmoothString("Actions", contentX, rowY, palette.titleText);
        FontUtil.small.drawSmoothString("Quick file operations and sync tools", contentX + 60, rowY + 1, palette.mutedText);
        rowY += 20;

        for (ButtonSetting button : getConfigActionButtons()) {
            drawConfigActionRow(button, mouseX, mouseY, palette, contentX, rowY, contentW);
            rowY += CONFIG_ROW_HEIGHT + CARD_GAP;
        }

        rowY += CONFIG_SECTION_GAP;
        FontUtil.semiBold.drawSmoothString("Saved Configs", contentX, rowY, palette.titleText);
        FontUtil.small.drawSmoothString("Click any config to load it instantly", contentX + 84, rowY + 1, palette.mutedText);
        rowY += 20;

        for (Config config : Crow.configManager.getConfigs()) {
            drawSavedConfigRow(config, mouseX, mouseY, palette, contentX, rowY, contentW);
            rowY += CONFIG_ROW_HEIGHT + CARD_GAP;
        }
    }

    private void drawConfigActionRow(ButtonSetting button, int mouseX, int mouseY, CompactPalette palette, int x, int y, int w) {
        boolean hovered = isOverRect(x, y, w, CONFIG_ROW_HEIGHT, mouseX, mouseY);
        int bg = CompactModuleCard.blendColor(palette.card, palette.hoverCard, hovered ? 0.75F : 0.15F);
        RenderUtils.drawRoundedRectAA(x, y, x + w, y + CONFIG_ROW_HEIGHT, 10, bg);
        RenderUtils.drawFlowingGradientRoundedRect(x + 1, y + 1, x + w - 1, y + CONFIG_ROW_HEIGHT - 1, 9, hovered ? 22 : 14, 0);

        FontUtil.semiBold.drawSmoothString(button.getName(), x + 12, y + 9, palette.titleText);
        FontUtil.small.drawSmoothString(getActionSubtitle(button.getName()), x + 12, y + 22, palette.mutedText);
    }

    private void drawSavedConfigRow(Config config, int mouseX, int mouseY, CompactPalette palette, int x, int y, int w) {
        boolean current = Crow.configManager.getConfig() != null
                && Crow.configManager.getConfig().getName().equalsIgnoreCase(config.getName());
        boolean hovered = isOverRect(x, y, w, CONFIG_ROW_HEIGHT, mouseX, mouseY);
        int bg = current
                ? CompactModuleCard.blendColor(palette.card, palette.sidebarSelected, 0.35F)
                : CompactModuleCard.blendColor(palette.card, palette.hoverCard, hovered ? 0.6F : 0.12F);

        RenderUtils.drawRoundedRectAA(x, y, x + w, y + CONFIG_ROW_HEIGHT, 10, bg);
        if (current) {
            RenderUtils.drawRoundedOutline(x, y, x + w, y + CONFIG_ROW_HEIGHT, 10, 1.0F, palette.accent);
            RenderUtils.drawFlowingGradientRoundedRect(x + 1, y + 1, x + w - 1, y + CONFIG_ROW_HEIGHT - 1, 9, 30, 0);
        }

        long lastEdit = readLastEdit(config);
        String when = lastEdit > 0L ? CONFIG_TIME_FORMAT.format(new Date(lastEdit)) : "";
        int dateW = when.isEmpty() ? 0 : (int) FontUtil.small.getStringWidth(when) + 10;
        int nameMaxW = w - 12 - 12 - dateW;
        FontUtil.semiBold.drawSmoothString(
                trimToWidth(config.getName(), FontUtil.semiBold, nameMaxW),
                x + 12, y + 9, palette.titleText);

        String status = current ? "Current config" : "Click to load";
        FontUtil.small.drawSmoothString(
                trimToWidth(status, FontUtil.small, nameMaxW),
                x + 12, y + 22, current ? palette.accent : palette.mutedText);
        if (lastEdit > 0L) {
            FontUtil.small.drawSmoothString(when, x + w - 12 - (int) FontUtil.small.getStringWidth(when), y + 22, palette.mutedText);
        }
    }

    private boolean handleConfigClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }

        int rightX = containerX + SIDEBAR_WIDTH;
        int contentX = rightX + CONTENT_PADDING;
        int contentW = containerW - SIDEBAR_WIDTH - CONTENT_PADDING * 2;
        int contentY = containerY + HEADER_HEIGHT + 10 + (int) smoothScrollAnim.get();
        int rowY = contentY + 20;

        for (ButtonSetting button : getConfigActionButtons()) {
            if (isOverRect(contentX, rowY, contentW, CONFIG_ROW_HEIGHT, mouseX, mouseY)) {
                button.press();
                return true;
            }
            rowY += CONFIG_ROW_HEIGHT + CARD_GAP;
        }

        rowY += CONFIG_SECTION_GAP + 20;
        for (Config config : Crow.configManager.getConfigs()) {
            if (isOverRect(contentX, rowY, contentW, CONFIG_ROW_HEIGHT, mouseX, mouseY)) {
                Crow.configManager.save();
                Crow.configManager.loadConfigByName(config.getName());
                if (Crow.clientConfig != null) {
                    Crow.clientConfig.saveConfig();
                }
                return true;
            }
            rowY += CONFIG_ROW_HEIGHT + CARD_GAP;
        }
        return false;
    }

    private void drawDeleteConfirmation(int mouseX, int mouseY, CompactPalette palette) {
        Gui.drawRect(0, 0, width, height, 0x7A000000);
        int boxW = 240;
        int boxH = 112;
        int boxX = (width - boxW) / 2;
        int boxY = (height - boxH) / 2;
        RenderUtils.drawRoundedRectAA(boxX, boxY, boxX + boxW, boxY + boxH, 14, palette.card);
        FontUtil.bold.drawCenteredSmoothString("Delete Config?", boxX + boxW / 2.0F, boxY + 18, palette.titleText);
        FontUtil.small.drawCenteredSmoothString(
                "Remove " + pendingDeleteConfig.getName() + " permanently?",
                boxX + boxW / 2.0F, boxY + 40, palette.mutedText);

        int cancelX = boxX + 18;
        int confirmX = boxX + boxW - 98;
        int buttonY = boxY + 70;
        RenderUtils.drawRoundedRectAA(cancelX, buttonY, cancelX + 80, buttonY + 24, 10, palette.toggleOff);
        RenderUtils.drawRoundedRectAA(confirmX, buttonY, confirmX + 80, buttonY + 24, 10, 0xAA9C2B2B);
        FontUtil.semiBold.drawCenteredSmoothString("Cancel", cancelX + 40, buttonY + 8, palette.titleText);
        FontUtil.semiBold.drawCenteredSmoothString("Delete", confirmX + 40, buttonY + 8, 0xFFFFFFFF);
    }

    private void handleDeleteConfirmationClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return;
        }
        int boxW = 240;
        int boxH = 112;
        int boxX = (width - boxW) / 2;
        int boxY = (height - boxH) / 2;
        int cancelX = boxX + 18;
        int confirmX = boxX + boxW - 98;
        int buttonY = boxY + 70;
        if (isOverRect(cancelX, buttonY, 80, 24, mouseX, mouseY)) {
            pendingDeleteConfig = null;
            return;
        }
        if (isOverRect(confirmX, buttonY, 80, 24, mouseX, mouseY)) {
            Crow.configManager.deleteConfig(pendingDeleteConfig);
            if (Crow.clientConfig != null) {
                Crow.clientConfig.saveConfig();
            }
            pendingDeleteConfig = null;
        }
    }

    private void drawThemeCards(int mouseX, int mouseY, CompactPalette palette, int contentX, int contentW, int startY) {
        List<ThemeModule> themes = getThemeModules();
        int columnGap = 10;
        int cardW = (contentW - columnGap) / 2;
        int row = 0;
        int col = 0;
        for (ThemeModule theme : themes) {
            int x = contentX + col * (cardW + columnGap);
            int y = startY + row * (THEME_CARD_HEIGHT + THEME_CARD_GAP);
            drawThemeCard(theme, mouseX, mouseY, palette, x, y, cardW, THEME_CARD_HEIGHT);
            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }
    }

    private void drawSearchView(int mouseX, int mouseY, CompactPalette palette, int contentX, int contentW, int startY) {
        int heroX = contentX;
        int heroY = startY - 2;
        int heroH = 72;
        RenderUtils.drawRoundedRectAA(heroX, heroY, heroX + contentW, heroY + heroH, 14, palette.card);
        RenderUtils.drawFlowingGradientRoundedRect(heroX + 1, heroY + 1, heroX + contentW - 1, heroY + heroH - 1, 13, 20, 0);

        FontUtil.bold.drawSmoothString("Search Everything", heroX + 18, heroY + 14, palette.titleText);
        FontUtil.small.drawSmoothString("Find any module instantly across Crow.", heroX + 18, heroY + 28, palette.mutedText);

        int barX = heroX + 18;
        int barY = heroY + 42;
        int barW = contentW - 36;
        int barH = 22;
        RenderUtils.drawRoundedRectAA(barX, barY, barX + barW, barY + barH, 11,
                searchField.isFocused() ? blendSearchColor(palette.content, palette.hoverCard, 0.72F) : blendSearchColor(palette.content, palette.card, 0.55F));
        RenderUtils.drawRoundedOutline(barX, barY, barX + barW, barY + barH, 11, 1.0F, searchField.isFocused() ? 0x55FFFFFF : palette.separator);
        RenderUtils.drawFlowingGradientRoundedRect(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 10, 16, 0);
        drawSearchIcon(barX + 9, barY + 5, 12);
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            FontUtil.normal.drawSmoothString("Type to search modules...", barX + 28, barY + 7, palette.mutedText);
        } else {
            String text = searchField.getText();
            FontUtil.normal.drawSmoothString(text, barX + 28, barY + 7, palette.titleText);

            if (searchField.isFocused() && System.currentTimeMillis() % 1000 > 500) {
                int cursorX = barX + 28 + (text.isEmpty() ? 0 : (int) FontUtil.normal.getStringWidth(text));
                Gui.drawRect(cursorX, barY + 6, cursorX + 1, barY + 18, palette.titleText);
            }
        }

        int listY = heroY + heroH + 10;
        int cardY = listY;
        for (int i = 0; i < moduleCards.size(); i++) {
            CompactModuleCard card = moduleCards.get(i);
            card.setListIndex(i);
            card.setPosition(contentX, cardY, contentW, CARD_HEIGHT);
            card.drawHeader(mouseX, mouseY, palette);
            cardY += card.getTotalHeight() + CARD_GAP;
        }

        if (moduleCards.isEmpty()) {
            drawEmptyState(palette, contentX, listY, contentW, contentScissorH - heroH - 12);
        }
    }

    private void drawThemeCard(ThemeModule theme, int mouseX, int mouseY, CompactPalette palette, int x, int y, int w, int h) {
        boolean hovered = isOverRect(x, y, w, h, mouseX, mouseY);
        boolean active = theme.isEnabled();
        float bounce = 1.0F;
        if (themeBounceName != null && themeBounceName.equalsIgnoreCase(theme.getName())) {
            float elapsed = Math.min(1.0F, (System.currentTimeMillis() - themeBounceStart) / 320.0F);
            bounce = 1.0F + (float) Math.sin(elapsed * Math.PI) * 0.035F;
        }
        int drawX = x;
        int drawY = y;
        int drawW = w;
        int drawH = h;
        if (bounce != 1.0F) {
            drawW = Math.round(w * bounce);
            drawH = Math.round(h * bounce);
            drawX = x - (drawW - w) / 2;
            drawY = y - (drawH - h) / 2;
        }
        int bg = active
                ? CompactModuleCard.blendColor(palette.card, palette.sidebarSelected, 0.42F)
                : CompactModuleCard.blendColor(palette.card, palette.hoverCard, hovered ? 0.58F : 0.15F);
        RenderUtils.drawRoundedRectAA(drawX, drawY, drawX + drawW, drawY + drawH, 12, bg);
        if (active) {
            RenderUtils.drawRoundedOutline(drawX, drawY, drawX + drawW, drawY + drawH, 12, 1.0F, palette.accent);
            RenderUtils.drawFlowingGradientRoundedRect(drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1, 11, 24, 0);
        }

        int previewX = drawX + 10;
        int previewY = drawY + 10;
        int previewW = drawW - 20;
        int previewH = 34;
        RenderUtils.drawRoundedRectAA(previewX, previewY, previewX + previewW, previewY + previewH, 9, 0x18000000);
        drawAnimatedThemeGradient(theme, previewX + 1, previewY + 1, previewW - 2, previewH - 2);

        String descriptor = "3-color flowing gradient";
        int descriptorW = (int) FontUtil.small.getStringWidth(descriptor);
        int nameMaxW = drawW - 12 - 12 - descriptorW - 8;
        FontUtil.semiBold.drawSmoothString(
                trimToWidth(theme.getName(), FontUtil.semiBold, nameMaxW),
                drawX + 12, drawY + 52, palette.titleText);
        String subtitle = active ? "Active theme" : "Click to apply";
        FontUtil.small.drawSmoothString(subtitle, drawX + 12, drawY + 65, active ? palette.accent : palette.mutedText);
        FontUtil.small.drawSmoothString(descriptor, drawX + drawW - 12 - descriptorW, drawY + 65, palette.mutedText);
    }

    private void drawAnimatedThemeGradient(ThemeModule theme, int x, int y, int w, int h) {
        int steps = Math.max(18, w / 6);
        long timeOffset = System.currentTimeMillis() / 4L;
        for (int i = 0; i < steps; i++) {
            float start = i / (float) steps;
            float end = (i + 1) / (float) steps;
            int sx = x + Math.round(start * w);
            int ex = x + Math.round(end * w);
            int color = theme.getColor((int) (-timeOffset - i * 34L));
            Gui.drawRect(sx, y, Math.max(sx + 1, ex), y + h, color);
        }
        int shimmerWidth = Math.max(18, w / 7);
        int shimmerTravel = Math.max(1, w + shimmerWidth);
        int shimmerX = x + (int) ((System.currentTimeMillis() / 5L) % shimmerTravel) - shimmerWidth;
        Gui.drawRect(Math.max(x, shimmerX), y, Math.min(x + w, shimmerX + shimmerWidth), y + h, 0x22FFFFFF);
    }

    private void drawSearchIcon(int x, int y, int size) {
        if (SEARCH_ICON == null) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.getTextureManager().bindTexture(SEARCH_ICON);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.92F);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private boolean handleThemeClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }

        int rightX = containerX + SIDEBAR_WIDTH;
        int contentX = rightX + CONTENT_PADDING;
        int contentW = containerW - SIDEBAR_WIDTH - CONTENT_PADDING * 2;
        int columnGap = 10;
        int cardW = (contentW - columnGap) / 2;
        int startY = containerY + HEADER_HEIGHT + 10 + (int) smoothScrollAnim.get();

        List<ThemeModule> themes = getThemeModules();
        int row = 0;
        int col = 0;
        for (ThemeModule theme : themes) {
            int x = contentX + col * (cardW + columnGap);
            int y = startY + row * (THEME_CARD_HEIGHT + THEME_CARD_GAP);
            if (isOverRect(x, y, cardW, THEME_CARD_HEIGHT, mouseX, mouseY)) {
                if (!theme.isEnabled()) {
                    theme.enable();
                    themeBounceName = theme.getName();
                    themeBounceStart = System.currentTimeMillis();
                }
                return true;
            }
            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }
        return false;
    }

    private boolean isOverRect(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private boolean isOverDetachedPanel(int mouseX, int mouseY) {
        if (expandedCard == null || !expandedCard.usesDetachedSettingsPanel()) {
            return false;
        }
        int[] bounds = getDetachedPanelBounds();
        return isOverRect(bounds[0], bounds[1], bounds[2], bounds[3], mouseX, mouseY);
    }

    private boolean isOverDetachedHeader(int mouseX, int mouseY) {
        if (expandedCard == null || !expandedCard.usesDetachedSettingsPanel()) {
            return false;
        }
        int[] bounds = getDetachedPanelBounds();
        return isOverRect(bounds[0], bounds[1], bounds[2], DETACHED_PANEL_HEADER, mouseX, mouseY);
    }

    private int[] getDetachedPanelBounds() {
        int panelW = DETACHED_PANEL_WIDTH;
        int panelH = containerH;
        if (detachedPanelX == Integer.MIN_VALUE || detachedPanelY == Integer.MIN_VALUE) {
            int rightX = containerX + containerW + DETACHED_PANEL_GAP;
            int leftX = containerX - panelW - DETACHED_PANEL_GAP;
            detachedPanelX = rightX + panelW <= width ? rightX : Math.max(0, leftX);
            detachedPanelY = containerY;
        }
        detachedPanelX = clampDetachedPanelX(detachedPanelX, panelW);
        detachedPanelY = clampDetachedPanelY(detachedPanelY, panelH);
        int panelX = detachedPanelX;
        int panelY = detachedPanelY;
        return new int[]{panelX, panelY, panelW, panelH};
    }

    private int clampDetachedPanelX(int value, int panelW) {
        int max = Math.max(0, width - panelW);
        return Math.max(0, Math.min(max, value));
    }

    private int clampDetachedPanelY(int value, int panelH) {
        int max = Math.max(0, height - panelH);
        return Math.max(0, Math.min(max, value));
    }

    private String getActionSubtitle(String name) {
        if ("Save Current Config".equalsIgnoreCase(name)) {
            return "Save your active setup right now.";
        }
        if ("Refresh Config List".equalsIgnoreCase(name)) {
            return "Rescan your available Crow configs.";
        }
        if ("Export .crow File".equalsIgnoreCase(name)) {
            return "Write your current config to a .crow file.";
        }
        if ("Import .crow File".equalsIgnoreCase(name)) {
            return "Browse and import a .crow config file.";
        }
        if ("Import Clipboard Config".equalsIgnoreCase(name)) {
            return "Load config text directly from your clipboard.";
        }
        return "Run this config action.";
    }

    private void drawEmptyState(CompactPalette palette, int x, int y, int w, int h) {
        int boxW = Math.min(220, w - 40);
        int boxH = 66;
        int boxX = x + (w - boxW) / 2;
        int boxY = y + Math.max(16, (h - boxH) / 2);
        RenderUtils.drawRoundedRectAA(boxX, boxY, boxX + boxW, boxY + boxH, 12, palette.card);
        FontUtil.semiBold.drawCenteredSmoothString("No modules found", boxX + boxW / 2.0F, boxY + 18, palette.titleText);
        FontUtil.small.drawCenteredSmoothString("Try a different search or clear the filter.", boxX + boxW / 2.0F, boxY + 34, palette.mutedText);
    }

    private void drawScrollDecorations(CompactPalette palette, int scrollX, int contentY, int contentH, int mouseX, int mouseY) {
        int totalContent = getTotalContentHeight();
        if (totalContent <= contentH) {
            scrollbarVisible = false;
            return;
        }

        int trackTop = contentY + 2;
        int trackBottom = contentY + contentH - 2;
        int trackX = contentScissorX + contentScissorW + 4;
        Gui.drawRect(trackX, trackTop, trackX + 1, trackBottom, 0x22000000);

        float viewRatio = Math.max(0.15F, contentH / (float) totalContent);
        int thumbHeight = Math.max(13, (int) ((trackBottom - trackTop) * viewRatio));
        float scrollProgress = Math.min(1.0F, Math.max(0.0F,
                -smoothScrollAnim.get() / (float) (totalContent - contentH)));
        int thumbY = trackTop + (int) ((trackBottom - trackTop - thumbHeight) * scrollProgress);
        int thumbX = trackX - 1;
        int thumbW = 2;

        scrollbarVisible = true;
        scrollbarThumbX = thumbX;
        scrollbarThumbY = thumbY;
        scrollbarThumbW = thumbW;
        scrollbarThumbH = thumbHeight;
        scrollbarTrackTop = trackTop;
        scrollbarTrackBottom = trackBottom;
        scrollbarContentH = contentH;

        boolean hovering = !scrollbarDragging && isOverScrollbarThumb(mouseX, mouseY);
        int thumbColor = scrollbarDragging ? 0xDDFFFFFF : (hovering ? 0xAAFFFFFF : 0x66FFFFFF);
        RenderUtils.drawRoundedRectAA(thumbX, thumbY, thumbX + thumbW, thumbY + thumbHeight, 1, thumbColor);
    }

    private boolean isOverScrollbarThumb(int mouseX, int mouseY) {
        if (!scrollbarVisible) return false;
        int hitX1 = scrollbarThumbX - 4;
        int hitX2 = scrollbarThumbX + scrollbarThumbW + 4;
        return mouseX >= hitX1 && mouseX <= hitX2
                && mouseY >= scrollbarThumbY && mouseY <= scrollbarThumbY + scrollbarThumbH;
    }

    private boolean isOverScrollbarTrack(int mouseX, int mouseY) {
        if (!scrollbarVisible) return false;
        int hitX1 = scrollbarThumbX - 4;
        int hitX2 = scrollbarThumbX + scrollbarThumbW + 4;
        return mouseX >= hitX1 && mouseX <= hitX2
                && mouseY >= scrollbarTrackTop && mouseY <= scrollbarTrackBottom;
    }

    private void drawDetachedScrollDecorations(CompactPalette palette, int trackX, int contentY, int contentH, int mouseX, int mouseY) {
        int totalContent = getDetachedContentHeight();
        if (totalContent <= contentH) {
            detachedScrollbarVisible = false;
            return;
        }
        int trackTop = contentY + 1;
        int trackBottom = contentY + contentH - 1;
        Gui.drawRect(trackX, trackTop, trackX + 1, trackBottom, 0x22000000);
        float viewRatio = Math.max(0.15F, contentH / (float) totalContent);
        int thumbHeight = Math.max(13, (int) ((trackBottom - trackTop) * viewRatio));
        float scrollProgress = Math.min(1.0F, Math.max(0.0F,
                -detachedScrollAnim.get() / (float) (totalContent - contentH)));
        int thumbY = trackTop + (int) ((trackBottom - trackTop - thumbHeight) * scrollProgress);
        int thumbX = trackX - 1;
        int thumbW = 2;

        detachedScrollbarVisible = true;
        detachedScrollbarThumbX = thumbX;
        detachedScrollbarThumbY = thumbY;
        detachedScrollbarThumbW = thumbW;
        detachedScrollbarThumbH = thumbHeight;
        detachedScrollbarTrackTop = trackTop;
        detachedScrollbarTrackBottom = trackBottom;
        detachedScrollbarContentH = contentH;

        boolean hovering = !detachedScrollbarDragging && isOverDetachedScrollbarThumb(mouseX, mouseY);
        int thumbColor = detachedScrollbarDragging ? 0xDDFFFFFF : (hovering ? 0xAAFFFFFF : 0x66FFFFFF);
        RenderUtils.drawRoundedRectAA(thumbX, thumbY, thumbX + thumbW, thumbY + thumbHeight, 1, thumbColor);
    }

    private boolean isOverDetachedScrollbarThumb(int mouseX, int mouseY) {
        if (!detachedScrollbarVisible) return false;
        int hitX1 = detachedScrollbarThumbX - 4;
        int hitX2 = detachedScrollbarThumbX + detachedScrollbarThumbW + 4;
        return mouseX >= hitX1 && mouseX <= hitX2
                && mouseY >= detachedScrollbarThumbY && mouseY <= detachedScrollbarThumbY + detachedScrollbarThumbH;
    }

    private boolean isOverDetachedScrollbarTrack(int mouseX, int mouseY) {
        if (!detachedScrollbarVisible) return false;
        int hitX1 = detachedScrollbarThumbX - 4;
        int hitX2 = detachedScrollbarThumbX + detachedScrollbarThumbW + 4;
        return mouseX >= hitX1 && mouseX <= hitX2
                && mouseY >= detachedScrollbarTrackTop && mouseY <= detachedScrollbarTrackBottom;
    }

    private int searchX() {
        int rightX = containerX + SIDEBAR_WIDTH;
        int contentX = rightX + CONTENT_PADDING;
        int contentW = containerW - SIDEBAR_WIDTH - CONTENT_PADDING * 2;
        return contentX + contentW - 134;
    }

    private int searchY() {
        return containerY + 16;
    }

    private long readLastEdit(Config config) {
        try {
            if (config.getData() != null && config.getData().has("lastEditTime")) {
                return config.getData().get("lastEditTime").getAsLong();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    static String trimToWidth(String text, crow.client.utils.font.FontRenderer font, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0) return "";
        if (font == null) return text;
        if (font.getStringWidth(text) <= maxWidth) return text;
        String trimmed = text;
        while (trimmed.length() > 1 && font.getStringWidth(trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }
}
