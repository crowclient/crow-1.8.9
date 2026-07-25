package crow.client.clickgui.spacious;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.lwjgl.opengl.GL11;

import crow.client.clickgui.compact.CompactModuleCard;
import crow.client.config.Config;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.Module.ModuleCategory;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.modules.themes.ThemeModule;
import crow.client.module.setting.impl.ButtonSetting;
import crow.client.utils.Animation;
import crow.client.utils.Icons;
import crow.client.utils.RenderUtils;
import crow.client.utils.anim.Animatable;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/**
 * One draggable tab in the Spacious GUI — a rounded card with a header
 * strip and a vertical list of {@link SpaciousModuleCard}s. Tabs cap at
 * {@link #MAX_VISIBLE_CARDS} cards of visible body height; anything
 * beyond scrolls. Scrolling uses a smooth interpolation toward
 * {@code scrollTarget} and clips the card list via a GL scissor so
 * overflow is hidden inside the tab's body.
 *
 * <p>Every column is always open and laid out on a fixed grid by
 * {@link SpaciousGui}; the header drags it if you want to rearrange.
 *
 * <p>The {@code config} and {@code themes} categories get specialised
 * row renderers — configs show as a flat list of save files (no module
 * wrapper) and themes get a flowing gradient swatch on the right edge
 * of each card.
 */
public class SpaciousCategoryTab {

    public static final int TAB_WIDTH = 186;
    private static final int HEADER_HEIGHT = 22;
    /** Rows run to the panel edge, but the body is masked to this radius via
     *  {@link SpaciousGui#pushRoundedScissor}, so the content can't square off
     *  the corners and the radius is free to be generous. */
    private static final int CARD_RADIUS = 10;
    /** Rows run the full width of the column and butt straight up against
     *  each other: no side padding, no gutter. The panel background is only
     *  ever visible above the first row and below the last, so a row and the
     *  panel read as one surface rather than a cell floating in a tray. */
    private static final int LIST_PADDING = 0;
    private static final int CARD_GAP = 0;

    /** Cap on how many module cards are shown at once before scrolling kicks in. */
    public static final int MAX_VISIBLE_CARDS = 8;

    /** Ceiling on body height once settings expand. Without a cap a module with
     *  a long settings list would grow the tab straight off the bottom of the
     *  screen; past this the settings scroll instead. */
    private static final int MAX_EXPANDED_BODY = 320;
    /** Floor for the expanded cap, so a tab parked near the bottom of the
     *  screen still opens to something readable rather than a sliver. */
    private static final int MIN_EXPANDED_BODY = 120;

    /* ---- Config-row layout (special-case for ModuleCategory.config). ---- */
    private static final int CONFIG_ROW_HEIGHT = 26;
    private static final int CONFIG_ACTION_ROW_HEIGHT = 19;
    private static final int CONFIG_SECTION_GAP = 6;
    private static final SimpleDateFormat CONFIG_TIME_FORMAT = new SimpleDateFormat("MMM dd");

    private final ModuleCategory category;
    private final SpaciousGui gui;
    private final ResourceLocation icon;
    private final List<SpaciousModuleCard> moduleCards = new ArrayList<>();
    private final List<ButtonSetting> configActions = new ArrayList<>();

    /** Every column is always shown — kept only because the config
     *  round-trip and a few guards still read it. */
    public boolean visible = true;
    public int x, y;
    public boolean dragging;
    private int dragOffsetX, dragOffsetY;

    /* Open / close (visibility) animation — distinct from collapse. */
    private final Animation openAnim = new Animation(280, Animation::easeOutCubic);
    /** Wallclock at which this tab last became visible — drives card stagger. */
    private long openedAtMs;
    /** True when the tab became visible this frame (used to reset stagger). */
    private boolean prevVisible;

    /* Columns are always open. The field and its animation are kept only so
     * the body-height maths below has a single expanded/collapsed factor to
     * multiply by; nothing ever sets it true now that the collapse chevron is
     * gone, and a state you cannot see or undo is worse than no state. */
    public boolean collapsed = false;
    private final Animation collapseAnim = new Animation(260, Animation::easeOutCubic);

    /* Scroll state. */
    private int scrollTarget;
    private final Animatable scrollAnim = new Animatable(0.0F, 22.0F);

    /* Scrollbar interaction. */
    private boolean scrollbarDragging;
    private int scrollbarDragStartMouseY;
    private int scrollbarDragStartScroll;
    private boolean scrollbarVisible;
    private int scrollbarThumbY, scrollbarThumbH, scrollbarTrackTop, scrollbarTrackBottom;

    /** Gap reserved between the card list and the scrollbar when it's drawn. */
    private static final int SCROLLBAR_RESERVE = 9;

    public SpaciousCategoryTab(ModuleCategory category, SpaciousGui gui) {
        this.category = category;
        this.gui = gui;
        this.icon = resolveCategoryIcon(category);
        rebuildContents();
        // Start fully open — columns never collapse.
        collapseAnim.set(1F);
    }

    /** Resolve the category badge from the dedicated Spacious icon set
     *  in /assets/crow/spaciousgui/. These are 512×512 sources so the
     *  size-aware loader pre-scales them to a 32-px variant for clean
     *  display at the 11/12-px header sizes. Falls back to null when the
     *  file isn't present, in which case the header simply renders
     *  without an icon. */
    private static ResourceLocation resolveCategoryIcon(ModuleCategory category) {
        if (category == null) return null;
        String iconName;
        switch (category) {
            case combat:   iconName = "Combat";   break;
            case movement: iconName = "Movement"; break;
            case world:    iconName = "World";    break;
            case render:   iconName = "Render";   break;
            case other:    iconName = "Other";    break;
            case config:   iconName = "Configs";  break;
            case client:   iconName = "Settings"; break;
            case player:   iconName = "Player";   break;
            case themes:   iconName = "Themes";   break;
            default:       return null;
        }
        return RenderUtils.getResourcePathAtSize(
                "/assets/crow/spaciousgui/" + iconName + ".png", 14);
    }

    public ResourceLocation getIcon() { return icon; }

    /** Reset the open animation so the next frame replays the zoom-in
     *  from scale 0 → 1. Used by {@link SpaciousGui#initGui()} so visible
     *  tabs animate in every time the GUI is opened, not just the first
     *  time. Also clears {@code prevVisible} so the card-stagger clock
     *  re-fires alongside the panel zoom. */
    public void resetOpenAnim() {
        openAnim.set(0F);
        prevVisible = false;
    }

    /** Rebuild the displayed rows. Called on construction and when the
     *  underlying config list changes (the config tab discovers new saves). */
    private void rebuildContents() {
        moduleCards.clear();
        configActions.clear();

        if (category == ModuleCategory.config) {
            // Config tab does NOT wrap configs in module cards. The
            // ConfigSettings module's buttons become the action row,
            // and each saved Config is its own row.
            for (Module m : Crow.moduleManager.getModulesInCategory(ModuleCategory.config)) {
                if (m instanceof crow.client.module.modules.config.ConfigSettings) {
                    for (crow.client.module.setting.Setting s : m.getSettings()) {
                        if (s instanceof ButtonSetting) configActions.add((ButtonSetting) s);
                    }
                }
            }
            return;
        }

        for (Module m : Crow.moduleManager.getModulesInCategory(category)) {
            if (!m.isHidden() || Module.revealHiddenModules) {
                moduleCards.add(new SpaciousModuleCard(m, this));
            }
        }
    }

    public ModuleCategory getCategory() { return category; }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /* ====================================================================== */
    /* Height calculation                                                     */
    /* ====================================================================== */

    /** Sum of all card / row heights (including expanded settings) + gaps + padding. */
    private int getContentHeight() {
        if (category == ModuleCategory.config) {
            int total = LIST_PADDING;
            // "Actions" mini-section (no header, just rows).
            total += configActions.size() * (CONFIG_ACTION_ROW_HEIGHT + CARD_GAP);
            if (!configActions.isEmpty()) total += CONFIG_SECTION_GAP;
            // Saved configs.
            int n = Crow.configManager.getConfigs().size();
            total += n * (CONFIG_ROW_HEIGHT + CARD_GAP);
            total += LIST_PADDING;
            return total;
        }
        int total = LIST_PADDING;
        for (int i = 0; i < moduleCards.size(); i++) {
            total += moduleCards.get(i).getTotalHeight();
            if (i < moduleCards.size() - 1) total += CARD_GAP;
        }
        total += LIST_PADDING;
        return total;
    }

    /** Height of the visible body region when fully expanded — capped at
     *  MAX_VISIBLE_CARDS rows (or equivalent for special tabs). */
    private int getExpandedBodyHeight() {
        if (category == ModuleCategory.config) {
            // Configs use shorter rows so we can show a few more.
            int visibleRows = Math.min(MAX_VISIBLE_CARDS, Math.max(1,
                    configActions.size() + Crow.configManager.getConfigs().size()));
            int avgRow = (CONFIG_ACTION_ROW_HEIGHT + CONFIG_ROW_HEIGHT) / 2;
            return LIST_PADDING + visibleRows * (avgRow + CARD_GAP) + LIST_PADDING;
        }
        int visibleCards = Math.min(MAX_VISIBLE_CARDS, moduleCards.size());
        if (visibleCards == 0) visibleCards = 1;
        int rowsHeight = visibleCards * SpaciousModuleCard.CARD_HEIGHT
                + Math.max(0, visibleCards - 1) * CARD_GAP;
        int baseline = LIST_PADDING + rowsHeight + LIST_PADDING;

        // Grow the box by however much the expanded settings add on top of the
        // collapsed list. Without this the baseline stayed fixed at the
        // collapsed row height, so opening a module crammed its settings into
        // the scroll region instead of making room for them. Tracks expandAnim
        // through getContentHeight(), so the tab grows and shrinks smoothly.
        int expansionExtra = Math.max(0, getContentHeight() - collapsedContentHeight());
        return Math.min(baseline + expansionExtra, maxExpandedBodyHeight());
    }

    /** List height with every card collapsed — the reference the expansion
     *  delta is measured against. */
    private int collapsedContentHeight() {
        int total = LIST_PADDING;
        for (int i = 0; i < moduleCards.size(); i++) {
            total += SpaciousModuleCard.CARD_HEIGHT;
            if (i < moduleCards.size() - 1) total += CARD_GAP;
        }
        return total + LIST_PADDING;
    }

    /** How tall the body is allowed to get — bounded by the screen room left
     *  below this tab so an expanded panel can't spill off the bottom. */
    private int maxExpandedBodyHeight() {
        int screenRoom = gui.height - y - HEADER_HEIGHT - 8;
        return Math.max(MIN_EXPANDED_BODY, Math.min(MAX_EXPANDED_BODY, screenRoom));
    }

    /** Current animated body height — scales by collapseAnim 0→1. */
    private int getBodyHeight() {
        float anim = collapseAnim.get();
        if (anim < 0.005F) return 0;
        return Math.round(getExpandedBodyHeight() * anim);
    }

    /** Total drawn tab height = header + (animated) body. */
    public int getCurrentHeight() {
        return HEADER_HEIGHT + getBodyHeight();
    }

    private int getMaxScroll() {
        int body = getBodyHeight();
        if (body <= 0) return 0;
        return Math.max(0, getContentHeight() - body);
    }

    /* ====================================================================== */
    /* Drawing                                                                */
    /* ====================================================================== */

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        // Reset stagger clock the moment the tab transitions from hidden
        // to visible. This drives the intro-animation cascade — cards fade
        // / slide into place with a per-index delay.
        if (!prevVisible) {
            openedAtMs = System.currentTimeMillis();
        }
        prevVisible = true;

        openAnim.setTarget(1.0F);
        openAnim.update();
        float open = openAnim.get();
        if (open < 0.01F) return;

        // Drive collapse animation (1 = expanded, 0 = collapsed).
        collapseAnim.setTarget(collapsed ? 0F : 1F);
        collapseAnim.update();

        if (dragging) {
            x = gui.clampTabX(mouseX - dragOffsetX);
            y = gui.clampTabY(mouseY - dragOffsetY, getCurrentHeight());
        }

        // Refresh config rows each frame for the config tab (so new
        // saves show up immediately after a Save action).
        if (category == ModuleCategory.config) {
            // (configActions are stable, only the configs list changes.)
        }

        int maxScroll = getMaxScroll();
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
        if (scrollTarget < 0) scrollTarget = 0;
        scrollAnim.setTarget((float) scrollTarget);
        scrollAnim.update();
        float scrollNow = scrollAnim.get();

        int totalH = getCurrentHeight();
        int bodyTop = y + HEADER_HEIGHT;
        int bodyHeight = getBodyHeight();
        int bodyBottom = bodyTop + bodyHeight;

        // Zoom-in animation anchored at the tab's own center so the panel
        // grows out of itself when the GUI is opened (openAnim is reset
        // for every tab in SpaciousGui.initGui()).
        float tcx = x + TAB_WIDTH / 2.0F;
        float tcy = y + totalH / 2.0F;
        GL11.glPushMatrix();
        GL11.glTranslatef(tcx, tcy, 0f);
        GL11.glScalef(open, open, 1f);
        GL11.glTranslatef(-tcx, -tcy, 0f);

        float collapseT = collapseAnim.get();
        boolean drawBody = collapseT > 0.005F;

        // Each tab is its own floating window, so it carries chrome elevation.
        int tabBottom = drawBody ? y + totalH : y + HEADER_HEIGHT;
        int edgeAlpha = (int) (((palette.background >>> 24) & 0xFF) * openAnim.get());
        RenderUtils.drawGlassChromeShadow(x, y, TAB_WIDTH, tabBottom - y, CARD_RADIUS,
                openAnim.get());

        // Header is a touch LIGHTER than the body, not darker. A header
        // painted in `sidebar` over `background` reads as a hole punched in
        // the top of the tab rather than a title bar; toolbars catch light.
        int headerFill = CompactModuleCard.blendColor(palette.background, palette.hoverCard, 0.55F);
        if (drawBody) {
            // Full body + header — same outer rect, header overlay on top.
            RenderUtils.drawRoundedRectAA(x, y, x + TAB_WIDTH, y + totalH, CARD_RADIUS, palette.background);
            RenderUtils.drawRoundedRectAA(x, y, x + TAB_WIDTH, bodyTop,
                    CARD_RADIUS, headerFill,
                    new boolean[] { true, false, false, true });
            // Hairline under the header does the separating, so neither
            // surface has to be muddy to be distinguishable.
            RenderUtils.drawRoundedRectAA(x + 1, bodyTop - 0.5F, x + TAB_WIDTH - 1, bodyTop + 0.5F,
                    0.25F, ((int) (0x24 * openAnim.get()) << 24) | 0xFFFFFF);
        } else {
            // Collapsed — header only, fully rounded on all 4 corners.
            RenderUtils.drawRoundedRectAA(x, y, x + TAB_WIDTH, y + HEADER_HEIGHT,
                    CARD_RADIUS, headerFill);
        }
        // Hairline border only — no lit top rim. The rim is a short straight
        // bar inset from the sides, which at this radius starts outside the
        // curve of the corner and reads as a stray bar floating over the tab.
        int borderAlpha = Math.min(255, (int) (0x3A * (edgeAlpha / 255.0F)));
        RenderUtils.drawRoundedOutline(x, y, x + TAB_WIDTH, tabBottom, CARD_RADIUS,
                1.0F, (borderAlpha << 24) | 0xFFFFFF);

        // Header is icon + name only. No grip dots, no module count, no
        // collapse chevron — the columns are a fixed grid, so none of that
        // chrome has a job to do, and stripping it lets the category title
        // read as a section heading instead of a toolbar.
        float iconSize = 13.0F;
        float iconX = x + 9;
        float headerMid = y + HEADER_HEIGHT / 2.0F;
        String glyph = Icons.forCategory(category);
        Icons.drawLeft(glyph, iconX, headerMid, iconSize, palette.titleText);

        float titleX = iconX + Icons.width(glyph, iconSize) + 7;
        String title = titleCase(category.name());
        FontUtil.semiBold.drawSmoothString(title, titleX,
                headerMid - FontUtil.semiBold.getHeight() / 2.0F, palette.titleText);

        // Only render body contents if we have meaningful body height.
        if (drawBody && bodyHeight > 2) {
            boolean willShowScrollbar = maxScroll > 0;
            int cardX = x + LIST_PADDING;
            int cardW = TAB_WIDTH - LIST_PADDING * 2;

            // Clip the body to its animated height so content doesn't bleed
            // past while the tab is opening/closing — rounded at the bottom so
            // the last row follows the panel's corners instead of squaring
            // them off. The top two corners belong to the header.
            gui.pushRoundedScissor(x, bodyTop, TAB_WIDTH, bodyHeight, CARD_RADIUS,
                    new boolean[] { false, true, true, false });

            if (category == ModuleCategory.config) {
                drawConfigBody(mouseX, mouseY, palette, cardX, cardW, bodyTop, bodyBottom, scrollNow);
            } else {
                drawModuleCardBody(mouseX, mouseY, palette, cardX, cardW, bodyTop, bodyBottom, scrollNow);
            }

            gui.popRoundedScissor();

            // Scrollbar — drawn only when content overflows the visible body.
            drawScrollbar(mouseX, mouseY, palette, bodyTop, bodyHeight, scrollNow, maxScroll);
        } else {
            scrollbarVisible = false;
        }

        GL11.glPopMatrix();
    }

    private void drawModuleCardBody(int mouseX, int mouseY, CompactPalette palette,
                                    int cardX, int cardW, int bodyTop, int bodyBottom, float scrollNow) {
        long elapsedSinceOpen = System.currentTimeMillis() - openedAtMs;
        int cardY = bodyTop + LIST_PADDING - Math.round(scrollNow);
        int index = 0;
        boolean isThemesTab = category == ModuleCategory.themes;

        for (SpaciousModuleCard card : moduleCards) {
            card.setPosition(cardX, cardY, cardW, SpaciousModuleCard.CARD_HEIGHT);

            int cardBottom = cardY + card.getTotalHeight();
            if (cardBottom >= bodyTop && cardY <= bodyBottom) {
                // Intro stagger.
                long cardDelay = index * 35L;
                float reveal = (elapsedSinceOpen - cardDelay) / 220.0F;
                if (reveal > 1F) reveal = 1F;
                if (reveal < 0F) reveal = 0F;
                reveal = easeOutCubic(reveal);
                int revealYOff = (int) Math.round((1F - reveal) * -10F);

                GL11.glPushMatrix();
                if (revealYOff != 0) GL11.glTranslatef(0f, revealYOff, 0f);
                card.draw(mouseX, mouseY, palette);

                // Theme color swatch overlay — drawn on top of the card,
                // tucked next to the chevron arrow. Only applies to the
                // themes category where every module IS a ThemeModule.
                if (isThemesTab && card.mod instanceof ThemeModule) {
                    drawThemeSwatch((ThemeModule) card.mod, cardX + cardW - 28, cardY + 4,
                            14, SpaciousModuleCard.CARD_HEIGHT - 8);
                }

                GL11.glPopMatrix();
            }
            cardY += card.getTotalHeight() + CARD_GAP;
            index++;
        }
    }

    private void drawConfigBody(int mouseX, int mouseY, CompactPalette palette,
                                int cardX, int cardW, int bodyTop, int bodyBottom, float scrollNow) {
        long elapsedSinceOpen = System.currentTimeMillis() - openedAtMs;
        int rowY = bodyTop + LIST_PADDING - Math.round(scrollNow);
        int index = 0;

        for (ButtonSetting action : configActions) {
            int rowBottom = rowY + CONFIG_ACTION_ROW_HEIGHT;
            if (rowBottom >= bodyTop && rowY <= bodyBottom) {
                long cardDelay = index * 28L;
                float reveal = (elapsedSinceOpen - cardDelay) / 220.0F;
                if (reveal > 1F) reveal = 1F;
                if (reveal < 0F) reveal = 0F;
                reveal = easeOutCubic(reveal);
                int revealYOff = (int) Math.round((1F - reveal) * -8F);
                GL11.glPushMatrix();
                if (revealYOff != 0) GL11.glTranslatef(0f, revealYOff, 0f);
                drawActionRow(action, mouseX, mouseY, palette, cardX, rowY, cardW);
                GL11.glPopMatrix();
            }
            rowY += CONFIG_ACTION_ROW_HEIGHT + CARD_GAP;
            index++;
        }
        if (!configActions.isEmpty()) rowY += CONFIG_SECTION_GAP;

        for (Config cfg : Crow.configManager.getConfigs()) {
            int rowBottom = rowY + CONFIG_ROW_HEIGHT;
            if (rowBottom >= bodyTop && rowY <= bodyBottom) {
                long cardDelay = index * 28L;
                float reveal = (elapsedSinceOpen - cardDelay) / 220.0F;
                if (reveal > 1F) reveal = 1F;
                if (reveal < 0F) reveal = 0F;
                reveal = easeOutCubic(reveal);
                int revealYOff = (int) Math.round((1F - reveal) * -8F);
                GL11.glPushMatrix();
                if (revealYOff != 0) GL11.glTranslatef(0f, revealYOff, 0f);
                drawConfigRow(cfg, mouseX, mouseY, palette, cardX, rowY, cardW);
                GL11.glPopMatrix();
            }
            rowY += CONFIG_ROW_HEIGHT + CARD_GAP;
            index++;
        }
    }

    private void drawActionRow(ButtonSetting button, int mouseX, int mouseY, CompactPalette palette,
                               int x, int y, int w) {
        boolean hovered = isOver(x, y, w, CONFIG_ACTION_ROW_HEIGHT, mouseX, mouseY);
        int bg = CompactModuleCard.blendColor(palette.card, palette.hoverCard, hovered ? 0.7F : 0.18F);
        RenderUtils.drawRoundedRectAA(x, y, x + w, y + CONFIG_ACTION_ROW_HEIGHT, 6, bg);
        FontUtil.semiBold.drawSmoothString(button.getName(), x + 10,
                y + (CONFIG_ACTION_ROW_HEIGHT - 9) / 2, palette.titleText);
    }

    private void drawConfigRow(Config cfg, int mouseX, int mouseY, CompactPalette palette,
                               int x, int y, int w) {
        boolean current = Crow.configManager.getConfig() != null
                && Crow.configManager.getConfig().getName().equalsIgnoreCase(cfg.getName());
        boolean hovered = isOver(x, y, w, CONFIG_ROW_HEIGHT, mouseX, mouseY);
        int themeColor = 0xFF000000 | (GuiModule.getThemeColor(0) & 0x00FFFFFF);
        int bg;
        if (current) {
            int neutral = CompactModuleCard.blendColor(palette.card, palette.hoverCard, hovered ? 0.45F : 0.18F);
            bg = CompactModuleCard.blendColor(neutral, themeColor, 0.30F);
        } else {
            bg = CompactModuleCard.blendColor(palette.card, palette.hoverCard, hovered ? 0.65F : 0.15F);
        }
        RenderUtils.drawRoundedRectAA(x, y, x + w, y + CONFIG_ROW_HEIGHT, 6, bg);

        long lastEdit = 0L;
        try {
            if (cfg.getData() != null && cfg.getData().has("lastEditTime")) {
                lastEdit = cfg.getData().get("lastEditTime").getAsLong();
            }
        } catch (Exception ignored) {}
        String when = lastEdit > 0L ? CONFIG_TIME_FORMAT.format(new Date(lastEdit)) : "";
        int whenW = when.isEmpty() ? 0 : (int) FontUtil.small.getStringWidth(when) + 6;
        int nameMaxW = w - 12 - 8 - whenW;
        int titleColor = current
                ? CompactModuleCard.blendColor(palette.titleText, 0xFFFFFFFF, 0.15F)
                : palette.titleText;
        FontUtil.semiBold.drawSmoothString(
                trimToWidth(cfg.getName(), nameMaxW),
                x + 9, y + 4, titleColor);
        String status = current ? "Current" : "Load";
        int statusColor = current ? themeColor : palette.mutedText;
        FontUtil.small.drawSmoothString(status, x + 9, y + 15, statusColor);
        if (!when.isEmpty()) {
            FontUtil.small.drawSmoothString(when,
                    x + w - 7 - (int) FontUtil.small.getStringWidth(when), y + 15,
                    palette.mutedText);
        }
    }

    private void drawThemeSwatch(ThemeModule theme, int x, int y, int w, int h) {
        // Background pill behind the swatch.
        RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, 3, 0x40000000);
        int steps = Math.max(6, w);
        long timeOffset = System.currentTimeMillis() / 4L;
        for (int i = 0; i < steps; i++) {
            float start = i / (float) steps;
            float end = (i + 1) / (float) steps;
            int sx = x + Math.round(start * w);
            int ex = x + Math.round(end * w);
            int color = theme.getColor((int) (-timeOffset - i * 38L));
            net.minecraft.client.gui.Gui.drawRect(sx, y + 1, Math.max(sx + 1, ex), y + h - 1, color);
        }
    }

    private static float easeOutCubic(float t) {
        float u = 1F - t;
        return 1F - u * u * u;
    }

    private void drawScrollbar(int mouseX, int mouseY, CompactPalette palette,
                                int bodyTop, int bodyHeight, float scrollNow, int maxScroll) {
        if (maxScroll <= 0) {
            scrollbarVisible = false;
            return;
        }
        scrollbarVisible = true;

        // Fixed inset rather than one tied to CARD_RADIUS — the scrollbar sits
        // well inside the column, so the panel's corner radius has no say in
        // where it starts.
        int trackTop = bodyTop + 3;
        int trackBottom = bodyTop + bodyHeight - 5;
        int trackHeight = trackBottom - trackTop;
        if (trackHeight < 24) return;

        int contentHeight = getContentHeight();
        int thumbH = Math.max(20, (int) (trackHeight * (bodyHeight / (float) contentHeight)));
        int thumbY = trackTop + (int) ((trackHeight - thumbH)
                * (scrollNow / (float) maxScroll));

        // Overlays the rows rather than reserving a strip — rows now run the
        // full width of the column, so there is no gutter to sit in.
        int trackW = 3;
        int trackX = x + TAB_WIDTH - SCROLLBAR_RESERVE / 2 - trackW / 2;
        RenderUtils.drawRoundedRectAA(trackX, trackTop, trackX + trackW, trackBottom,
                trackW / 2.0F, (35 << 24) | 0xFFFFFF);

        int thumbW = 5;
        int thumbX = trackX + trackW / 2 - thumbW / 2;
        boolean overThumb = mouseX >= thumbX - 2 && mouseX <= thumbX + thumbW + 2
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        int thumbAlpha = scrollbarDragging ? 0xDD : (overThumb ? 0xAA : 0x77);
        RenderUtils.drawRoundedRectAA(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH,
                thumbW / 2.0F, (thumbAlpha << 24) | 0xFFFFFF);

        scrollbarThumbY = thumbY;
        scrollbarThumbH = thumbH;
        scrollbarTrackTop = trackTop;
        scrollbarTrackBottom = trackBottom;
    }

    /* ====================================================================== */
    /* Mouse + key input                                                      */
    /* ====================================================================== */

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (openAnim.get() < 0.4F) return false;
        int totalH = getCurrentHeight();
        if (mouseX < x || mouseX > x + TAB_WIDTH || mouseY < y || mouseY > y + totalH) return false;

        // Header drags the column. No right-click collapse — with the
        // chevron gone there would be no way to tell it had happened.
        if (mouseY < y + HEADER_HEIGHT) {
            if (button == 0) {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
                return true;
            }
        }

        // If collapsed (or animating closed) the body is not interactive.
        if (collapseAnim.get() < 0.4F) {
            return true; // header miss already handled — swallow body clicks
        }

        // Scrollbar thumb drag — generous hit zone across the full
        // scrollbar reserve column so the user doesn't need pixel-perfect
        // aim.
        if (button == 0 && scrollbarVisible) {
            int zoneLeft = x + TAB_WIDTH - LIST_PADDING - SCROLLBAR_RESERVE;
            int zoneRight = x + TAB_WIDTH - LIST_PADDING;
            boolean overThumb = mouseX >= zoneLeft && mouseX <= zoneRight
                    && mouseY >= scrollbarThumbY - 2 && mouseY <= scrollbarThumbY + scrollbarThumbH + 2;
            if (overThumb) {
                scrollbarDragging = true;
                scrollbarDragStartMouseY = mouseY;
                scrollbarDragStartScroll = scrollTarget;
                return true;
            }
        }

        // Config tab — actions + saved-config rows.
        if (category == ModuleCategory.config) {
            int rowY = y + HEADER_HEIGHT + LIST_PADDING - Math.round(scrollAnim.get());
            for (ButtonSetting action : configActions) {
                if (isOver(x + LIST_PADDING, rowY,
                        TAB_WIDTH - LIST_PADDING * 2 - (scrollbarVisible ? SCROLLBAR_RESERVE : 0),
                        CONFIG_ACTION_ROW_HEIGHT, mouseX, mouseY)) {
                    if (button == 0) action.press();
                    return true;
                }
                rowY += CONFIG_ACTION_ROW_HEIGHT + CARD_GAP;
            }
            if (!configActions.isEmpty()) rowY += CONFIG_SECTION_GAP;
            for (Config cfg : Crow.configManager.getConfigs()) {
                if (isOver(x + LIST_PADDING, rowY,
                        TAB_WIDTH - LIST_PADDING * 2 - (scrollbarVisible ? SCROLLBAR_RESERVE : 0),
                        CONFIG_ROW_HEIGHT, mouseX, mouseY)) {
                    if (button == 0) {
                        Crow.configManager.save();
                        Crow.configManager.loadConfigByName(cfg.getName());
                        if (Crow.clientConfig != null) Crow.clientConfig.saveConfig();
                    }
                    return true;
                }
                rowY += CONFIG_ROW_HEIGHT + CARD_GAP;
            }
            return true; // consumed inside config body
        }

        // Module cards.
        for (SpaciousModuleCard card : moduleCards) {
            if (card.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return true; // consumed (clicked in tab body)
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        dragging = false;
        scrollbarDragging = false;
        for (SpaciousModuleCard card : moduleCards) {
            card.mouseReleased(mouseX, mouseY, button);
        }
    }

    public void scrollbarDrag(int mouseY) {
        if (!scrollbarDragging) return;
        int trackHeight = scrollbarTrackBottom - scrollbarTrackTop - scrollbarThumbH;
        if (trackHeight <= 0) return;
        int maxScroll = getMaxScroll();
        int contentRange = maxScroll;
        if (contentRange <= 0) return;
        int dy = mouseY - scrollbarDragStartMouseY;
        float ratio = contentRange / (float) trackHeight;
        scrollTarget = scrollbarDragStartScroll + Math.round(dy * ratio);
        if (scrollTarget < 0) scrollTarget = 0;
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
    }

    public void scroll(int delta) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return;
        int step = (SpaciousModuleCard.CARD_HEIGHT + CARD_GAP) * 3 / 2;
        scrollTarget -= Integer.signum(delta) * step;
        if (scrollTarget < 0) scrollTarget = 0;
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
    }

    public boolean isScrollbarDragging() {
        return scrollbarDragging;
    }

    public boolean containsPoint(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + TAB_WIDTH
                && mouseY >= y && mouseY <= y + getCurrentHeight();
    }

    public void keyTyped(char c, int k) {
        for (SpaciousModuleCard card : moduleCards) {
            card.keyTyped(c, k);
        }
    }

    /* Scissor helpers exposed to SpaciousModuleCard for settings clipping. */
    public void applyClipScissor(int cardX, int cardY, int cardW, int cardH) {
        // Module-card settings already render inside the tab's body
        // scissor; no additional clip needed for inline expansion.
    }

    public void restoreClipScissor() {
        // No-op; the tab body's outer scissor is restored on pop.
    }

    private boolean isOver(int cx, int cy, int cw, int ch, int mx, int my) {
        return mx >= cx && mx <= cx + cw && my >= cy && my <= cy + ch;
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

    private static String trimToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0) return "";
        if (FontUtil.semiBold.getStringWidth(text) <= maxWidth) return text;
        String trimmed = text;
        while (trimmed.length() > 1 && FontUtil.semiBold.getStringWidth(trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }
}
