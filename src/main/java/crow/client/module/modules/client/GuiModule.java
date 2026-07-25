package crow.client.module.modules.client;

import com.google.gson.JsonObject;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.themes.ThemeModule;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.ColorM;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;

import java.util.List;

public class GuiModule extends Module {

    private static ComboSetting preset;

    private static ComboSetting<GuiMode> guiMode;
    private static TickSetting notifications, toggleSounds, customMainMenu;
    public static TickSetting blur;

    public enum GuiMode { Compact, Spacious }

    private static boolean suppressScreenClose;

    public GuiModule() {
        super("Gui", ModuleCategory.client);
        withKeycode(54);
        withEnabled(false);

        this.registerSetting(guiMode = new ComboSetting<>("Layout", GuiMode.Compact));
        this.registerSetting(customMainMenu = new TickSetting("Custom menu", true));
        this.registerSetting(notifications = new TickSetting("Notifications", true));
        this.registerSetting(toggleSounds = new TickSetting("Sounds", false));
        this.registerSetting(blur = new TickSetting("Blur", true));
    }

    public static GuiMode getGuiMode() {
        if (guiMode == null) return GuiMode.Compact;
        return (GuiMode) guiMode.getMode();
    }

    public static boolean isBlurEnabled() {
        return blur == null || blur.isToggled();
    }

    private static final net.minecraft.util.ResourceLocation BLUR_SHADER =
            new net.minecraft.util.ResourceLocation("shaders/post/blur.json");

    /**
     * Reflects the Blur setting onto Minecraft's post-processing shader, which
     * is what puts the blurred world behind a glass panel. Call every frame
     * from an open GUI so live toggles take effect.
     *
     * <p>This lives here rather than on one screen because the panel fills are
     * translucent now: a GUI that opens without starting the shader shows the
     * raw unblurred world through its own chrome and looks broken.
     */
    public static void applyBackdropBlurShader() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || mc.entityRenderer == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        boolean wantBlur = isBlurEnabled();
        boolean active = mc.entityRenderer.isShaderActive();
        if (wantBlur && !active) {
            java.io.InputStream stream = null;
            try {
                stream = mc.getResourceManager().getResource(BLUR_SHADER).getInputStream();
                if (stream != null) mc.entityRenderer.loadShader(BLUR_SHADER);
            } catch (Exception ignored) {
            } finally {
                if (stream != null) try { stream.close(); } catch (Exception ignored) {}
            }
        } else if (!wantBlur && active) {
            try { mc.entityRenderer.stopUseShader(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onEnable() {
        if (!Utils.Player.isPlayerInGame()) {
            disable();
            return;
        }

        if ((mc.currentScreen == Crow.clickGui) || (mc.currentScreen == Crow.kvCompactGui)
                || (mc.currentScreen == Crow.compactGui) || (mc.currentScreen == Crow.spaciousGui)) {
            mc.displayGuiScreen(null);
            return;
        }

        mc.displayGuiScreen(getGuiMode() == GuiMode.Spacious ? Crow.spaciousGui : Crow.compactGui);
    }

    @Override
    public JsonObject getConfigAsJson() {
        // Never persist this module as enabled. enable() in Module.java sets
        // `this.enabled = true` *after* onEnable() returns, which means a
        // self-disable() inside onEnable() (the "player not in game" branch)
        // is a no-op — the module ends up flagged as enabled without the GUI
        // actually opening. On the next launch the config would restore that
        // ghost-enabled state, forcing the user to press the bind twice to
        // see the click GUI. Always serialize as disabled instead.
        JsonObject data = super.getConfigAsJson();
        data.addProperty("enabled", false);
        return data;
    }

    @Override
    public void onDisable() {
        if (!suppressScreenClose && ((mc.currentScreen == Crow.clickGui) || (mc.currentScreen == Crow.kvCompactGui)
                || (mc.currentScreen == Crow.compactGui) || (mc.currentScreen == Crow.spaciousGui))) {
            mc.displayGuiScreen(null);
        }
    }

    private static Preset getPresetMode() {
        return (Preset) preset.getMode();
    }

    public static boolean isCategoryBackgroundToggled() {
        return getPresetMode().categoryBackground;
    }

    public static boolean showGradientEnabled() {
        return getPresetMode().showGradientEnabled;
    }

    public static boolean showGradientDisabled() {
        return  getPresetMode().showGradientDisabled;
    }

    public static boolean useCustomFont() {
        return  getPresetMode().useCustomFont;
    }

    public static int getEnabledTopRGB(int delay) {
        return  getPresetMode().enabledTopRGB.color(delay);
    }

    public static int getEnabledTopRGB() {
        return getEnabledTopRGB(0);
    }

    public static int getEnabledBottomRGB(int delay) {
        return  getPresetMode().enabledBottomRGB.color(delay);
    }

    public static int getEnabledBottomRGB() {
        return getEnabledBottomRGB(0);
    }

    public static int getEnabledTextRGB(int delay) {
        return  getPresetMode().enabledTextRGB.color(delay);
    }

    public static int getEnabledTextRGB() {
        return getEnabledTextRGB(0);
    }

    public static int getDisabledTopRGB(int delay) {
        return  getPresetMode().disabledTopRGB.color(delay);
    }

    public static int getDisabledTopRGB() {
        return getDisabledTopRGB(0);
    }

    public static int getDisabledBottomRGB(int delay) {
        return  getPresetMode().disabledBottomRGB.color(delay);
    }

    public static int getDisabledBottomRGB() {
        return getDisabledBottomRGB(0);
    }

    public static int getDisabledTextRGB(int delay) {
        return  getPresetMode().disabledTextRGB.color(0);
    }

    public static int getDisabledTextRGB() {
        return getDisabledTextRGB(0);
    }

    public static int getBackgroundRGB(int delay) {
        return  getPresetMode().backgroundRGB.color(delay);
    }

    public static int getBackgroundRGB() {
        return getBackgroundRGB(0);
    }

    public static int getSettingBackgroundRGB(int delay) {
        return  getPresetMode().settingBackgroundRGB.color(delay);
    }

    public static int getSettingBackgroundRGB() {
        return getSettingBackgroundRGB(0);
    }

    public static int getCategoryBackgroundRGB(int delay) {
        return  getPresetMode().categoryBackgroundRGB.color(delay);
    }

    public static int getCategoryBackgroundRGB() {
        return getCategoryBackgroundRGB(0);
    }

    public static int getCategoryNameRGB(int delay) {
        return  getPresetMode().categoryNameRGB.color(delay);
    }

    public static int getCategoryNameRGB() {
        return getCategoryNameRGB(0);
    }

    public static int getBoarderColour(int delay) {
        return  getPresetMode().boarderColor.color(delay);
    }

    public static int getBoarderColour() {
        return getBoarderColour(0);
    }

    public static int getCategoryOutlineColor1(int delay) {
        return  getPresetMode().categoryOutlineColor.color(delay);
    }

    public static int getCategoryOutlineColor1() {
        return getCategoryOutlineColor1(0);
    }

    public static int getCategoryOutlineColor2(int delay) {
        return  getPresetMode().categoryOutlineColor2.color(delay);
    }

    public static int getCategoryOutlineColor2() {
        return getCategoryOutlineColor2(0);
    }

    public static CNColor getCNColor() {
        return  getPresetMode().cnColor;
    }

    public static boolean isSwingToggled() {
        return  getPresetMode().swing;
    }

    public static boolean isRoundedToggled() {
        return  getPresetMode().roundedCorners;
    }

    public static boolean isBoarderToggled() {
        return  getPresetMode().boarder;
    }

    public static boolean rainbowNotification() {
        return false;
    }

    public static boolean notifications() {
        return notifications != null && notifications.isToggled();
    }

    public static boolean customMainMenu() {
        return customMainMenu == null || customMainMenu.isToggled();
    }

    public static void setCustomMainMenu(boolean enabled) {
        if (customMainMenu != null) {
            customMainMenu.setEnabled(enabled);
        }
    }

    public static boolean toggleSounds() {
        return toggleSounds != null && toggleSounds.isToggled();
    }

    public static void handleGuiClosed() {
        Module gui = Crow.moduleManager.getModuleByClazz(GuiModule.class);
        if ((gui != null) && gui.isEnabled()) {
            suppressScreenClose = true;
            if (gui.isRegistered()) {
                Crow.eventBus.unregister(gui);
                ((GuiModule) gui).registered = false;
            }
            ((GuiModule) gui).enabled = false;
            suppressScreenClose = false;
        }
    }

    public static CompactPalette getCompactPalette() {
        return CompactPalette.Midnight;
    }

    /**
     * The UI's single accent, forced opaque.
     *
     * <p>Every accented surface — slider fills, focus rings, selected cells,
     * active outlines — routes through here so there is exactly one saturated
     * colour in the interface and it follows the user's theme. The palette
     * used to carry its own fixed {@code accent}, which meant picking Mint
     * still left periwinkle chrome scattered around.
     */
    public static int accent() {
        return 0xFF000000 | (getThemeColor(0) & 0x00FFFFFF);
    }

    // ponytail: the themes category is a fixed set registered once at startup
    // and never hidden, so resolve it once. getThemeColor is called per drawn
    // element (58 callsites, several inside per-row/per-slot loops), and
    // getModulesInCategory copies all ~150 modules into two fresh ArrayLists
    // on every call — that was thousands of element copies per frame.
    private static List<Module> themeModules;

    public static int getThemeColor(int delay) {
        if (Crow.moduleManager == null) return Utils.Client.rainbowDraw(2L, delay);
        List<Module> themes = themeModules;
        if (themes == null) {
            themes = Crow.moduleManager.getModulesInCategory(ModuleCategory.themes);
            // Don't cache an empty result — this can be called before the
            // theme modules finish registering.
            if (themes.isEmpty()) return Utils.Client.rainbowDraw(2L, delay);
            themeModules = themes;
        }
        for (int i = 0; i < themes.size(); i++) {
            Module m = themes.get(i);
            if (m.isEnabled() && m instanceof ThemeModule) {
                return ((ThemeModule) m).getColor(delay);
            }
        }
        return Utils.Client.rainbowDraw(2L, delay);
    }

    public enum Preset {

        BreathingRainbow(
                        true, false, true, true,
                        CNColor.STATIC,
                        in -> 0xFFF0F0F0,
                        in -> 0xFF111118,
                        in -> RenderUtils.getBreathingPastel().getRGB(),
                        in -> RenderUtils.getBreathingPastel().getRGB(),
                        in -> RenderUtils.getBreathingPastel().getRGB(),
                        in -> 0xFFF0F0F0,
                        in -> 0xFF111118,
                        in -> 0xFF111118,
                        in -> 0xFFF0F0F0,
                        in -> 0xFF111118,
                        true, true, true,
                        in -> RenderUtils.getBreathingPastel().getRGB(),
                        in -> RenderUtils.getBreathingPastel().getRGB(),
                        in -> RenderUtils.getBreathingPastel().getRGB()
                        ),

        Vape(
                        true, false, true, true,
                        CNColor.STATIC,
                        in -> 0xFFFFFFFE,
                        in -> 0x99808080,
                        in -> 0x99808080,
                        in -> -12876693,
                        in -> -12876693,
                        in -> 0xFFFFFFFE,
                        in -> 0xFF000000,
                        in -> 0xFF000000,
                        in -> 0xFFFFFFFE,
                        in -> 0x99808080,
                        true,
                        true,
                        false,
                        in -> -12876693,
                        in -> -12876693,
                        in -> Utils.Client.otherAstolfoColorsDraw(in, 10)
                        ),

        PlusPlus(
                        true, false, true, true,
                        CNColor.STATIC,
                        in -> 0xFFFFFFFF,
                        in -> 0x88141414,
                        in -> 0x881C1C1C,
                        in -> 0xCC7B68EE,
                        in -> 0xCC7B68EE,
                        in -> 0x88141414,
                        in -> 0x883A3A3A,
                        in -> 0x883A3A3A,
                        in -> 0xFFAAAAAA,
                        in -> 0x881C1C1C,
                        true,
                        true,
                        true,
                        in -> 0xFF5553E0,
                        in -> 0xFF2D2D2D,
                        in -> 0xFF7B68EE
                        );

        public boolean showGradientEnabled, showGradientDisabled, useCustomFont, categoryBackground, roundedCorners, swing, boarder;
        public ColorM categoryNameRGB, settingBackgroundRGB, categoryBackgroundRGB, enabledTopRGB, enabledBottomRGB,
        enabledTextRGB, disabledTopRGB, disabledBottomRGB, disabledTextRGB, backgroundRGB, boarderColor, categoryOutlineColor, categoryOutlineColor2;
        public CNColor cnColor;

        private Preset(
                        boolean showGradientEnabled, boolean showGradientDisabled, boolean useCustomFont,
                        boolean categoryBackground, CNColor cnColor, ColorM categoryNameRGB, ColorM settingBackgroundRGB,
                        ColorM categoryBackgroundRGB, ColorM enabledTopRGB, ColorM enabledBottomRGB, ColorM enabledTextRGB,
                        ColorM disabledTopRGB, ColorM disabledBottomRGB, ColorM disabledTextRGB, ColorM backgroundRGB,
                        boolean roundedCorners, boolean swing, boolean boarder, ColorM boarderColor, ColorM categoryOutlineColor, ColorM categoryOutlineColor2) {
            this.showGradientEnabled = showGradientEnabled;
            this.showGradientDisabled = showGradientDisabled;
            this.useCustomFont = useCustomFont;
            this.categoryBackground = categoryBackground;
            this.categoryNameRGB = categoryNameRGB;
            this.settingBackgroundRGB = settingBackgroundRGB;
            this.categoryBackgroundRGB = categoryBackgroundRGB;
            this.enabledTopRGB = enabledTopRGB;
            this.enabledBottomRGB = enabledBottomRGB;
            this.enabledTextRGB = enabledTextRGB;
            this.disabledTopRGB = disabledTopRGB;
            this.disabledBottomRGB = disabledBottomRGB;
            this.disabledTextRGB = disabledTextRGB;
            this.backgroundRGB = backgroundRGB;
            this.cnColor = cnColor;
            this.roundedCorners = roundedCorners;
            this.swing = swing;
            this.boarder = boarder;
            this.boarderColor = boarderColor;
            this.categoryOutlineColor = categoryOutlineColor;
            this.categoryOutlineColor2 = categoryOutlineColor2;
        }

    }

    public enum CNColor {
        RAINBOW, STATIC
    }

    public enum CompactPalette {

        /**
         * Near-black charcoal, two colours total.
         *
         * <p>The panel gutter sits around {@code #161616}; {@code card} is a
         * hair lighter so inactive rows separate from it without needing any
         * border. That small step is doing real work — keep it small, but
         * don't close it.
         *
         * <p>The only saturated colour in the whole panel is the theme accent,
         * which is why a flooded row reads instantly. {@code accent} here is
         * used for small chrome (slider fills, focus rings) and stays muted so
         * it never competes with that.
         *
         * <p>Fills sit around 0xC0–0xE0 so the blurred backdrop reads through
         * them; the contrast floor is held by the scrim, which darkens the
         * world <i>before</i> the panel samples it. Raise {@link #scrim} if
         * text gets hard to read over a bright biome — never the panel alphas,
         * that just turns the glass back into paint.
         *
         * <p>{@code outline} stays opaque: CompactTextInput paints it as a
         * full rect underneath an inset fill, so a white-at-alpha value there
         * would erase the field border.
         */
        Midnight(0xC8161616, 0xC0121212, 0xD0181818, 0xD81E1E20, 0xE22A2A2E, 0xFFFFFFFF, 0xFF9CA2AA,
                0xFF2E2E36, 0xFF3A3A3A, 0xE0242233, 0xB40A0A0E, 0x2EFFFFFF),
        Ocean(0xD01D2228, 0xD0141820, 0xD0222832, 0xD02A313C, 0xD02F3742, 0xFFF8FBFF, 0xFF9BAABD,
                0xFF404B57, 0xFF56606B, 0xD0323C48, 0x3010151B, 0xFF44515D),
        Sunset(0xD0251F1E, 0xD01B1615, 0xD02A2322, 0xD0332B29, 0xD039302E, 0xFFFFF6F2, 0xFFB19D97,
                0xFF4A4140, 0xFF665955, 0xD03A3130, 0x30161110, 0xFF5A4E49),
        Emerald(0xD01D2320, 0xD0141917, 0xD0222A26, 0xD02C3531, 0xD0333C37, 0xFFF7FFFA, 0xFF9CB0A4,
                0xFF414C46, 0xFF546159, 0xD0313A35, 0x30101311, 0xFF4E5A53);

        public final int background;
        public final int sidebar;
        public final int content;
        public final int card;
        public final int hoverCard;
        public final int titleText;
        public final int mutedText;
        public final int toggleOff;
        public final int outline;
        public final int sidebarSelected;
        public final int scrim;
        public final int separator;

        /** No {@code accent} member by design — the accent is the theme's, via
         *  {@link GuiModule#accent()}. A per-palette copy meant two competing
         *  saturated colours in one interface. */
        CompactPalette(int background, int sidebar, int content, int card, int hoverCard, int titleText, int mutedText,
                       int toggleOff, int outline, int sidebarSelected, int scrim, int separator) {
            this.background = background;
            this.sidebar = sidebar;
            this.content = content;
            this.card = card;
            this.hoverCard = hoverCard;
            this.titleText = titleText;
            this.mutedText = mutedText;
            this.toggleOff = toggleOff;
            this.outline = outline;
            this.sidebarSelected = sidebarSelected;
            this.scrim = scrim;
            this.separator = separator;
        }
    }
}
