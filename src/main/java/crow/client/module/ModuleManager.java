package crow.client.module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import crow.client.main.Crow;
import crow.client.module.Module.ModuleCategory;
import crow.client.module.modules.HUD;
import crow.client.module.modules.client.Developer;
import crow.client.module.modules.client.FPSSpoofer;
import crow.client.module.modules.client.RecordingMode;
import crow.client.module.modules.client.FakeHud;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.SelfDestruct;
import crow.client.module.modules.client.Targets;
import crow.client.module.modules.client.UpdateCheck;
import crow.client.module.modules.combat.AimAssist;
import crow.client.module.modules.combat.AntiFireball;
import crow.client.module.modules.combat.BowAimbot;
import crow.client.module.modules.combat.AutoBlock;
import crow.client.module.modules.combat.AutoGHead;
import crow.client.module.modules.combat.AutoSoup;
import crow.client.module.modules.combat.AutoWeapon;
import crow.client.module.modules.combat.BlockHit;
import crow.client.module.modules.combat.ClickAssist;
import crow.client.module.modules.combat.DelayRemover;
import crow.client.module.modules.combat.LagKB;
import crow.client.module.modules.combat.LagRange;
import crow.client.module.modules.combat.AutoRod;
import crow.client.module.modules.combat.BlockIn;
import crow.client.module.modules.combat.Clutch;
import crow.client.module.modules.combat.Backtrack;
import crow.client.module.modules.combat.HitBox;
import crow.client.module.modules.combat.JumpReset;
import crow.client.module.modules.combat.LeftClicker;
import crow.client.module.modules.combat.Reach;
import crow.client.module.modules.player.SafeWalk;
import crow.client.module.modules.combat.Velocity;
import crow.client.module.modules.combat.WTap;
import crow.client.module.modules.combat.aura.KillAura;
import crow.client.module.modules.config.ConfigSettings;
import crow.client.module.modules.hotkey.Armour;
import crow.client.module.modules.hotkey.Blocks;
import crow.client.module.modules.hotkey.Healing;
import crow.client.module.modules.hotkey.Ladders;
import crow.client.module.modules.hotkey.Pearl;
import crow.client.module.modules.hotkey.Trajectories;
import crow.client.module.modules.hotkey.Weapon;
import crow.client.module.modules.movement.AutoHeader;
import crow.client.module.modules.movement.Boost;
import crow.client.module.modules.movement.Fly;
import crow.client.module.modules.movement.InvMove;
import crow.client.module.modules.movement.KeepSprint;
import crow.client.module.modules.movement.LegitSpeed;
import crow.client.module.modules.movement.NoSlow;
import crow.client.module.modules.movement.SlyPort;
import crow.client.module.modules.movement.Sprint;
import crow.client.module.modules.movement.StopMotion;
import crow.client.module.modules.movement.Timer;
import crow.client.module.modules.movement.VClip;
import crow.client.module.modules.other.AutoGG;
import crow.client.module.modules.other.FakeChat;
import crow.client.module.modules.other.MiddleClick;
import crow.client.module.modules.other.NameHider;
import crow.client.module.modules.other.WaterBucket;
import crow.client.module.modules.player.AutoArmour;
import crow.client.module.modules.player.AutoJump;
import crow.client.module.modules.player.AutoPlace;
import crow.client.module.modules.player.AutoTool;
import crow.client.module.modules.player.Blink;
import crow.client.module.modules.player.BlinkInfinite;
import crow.client.module.modules.player.Bridger;
import crow.client.module.modules.player.ChestStealer;
import crow.client.module.modules.player.FallSpeed;
import crow.client.module.modules.player.FastPlace;
import crow.client.module.modules.player.Freecam;
import crow.client.module.modules.player.InvManager;
import crow.client.module.modules.player.NoFall;
import crow.client.module.modules.player.Parkour;
import crow.client.module.modules.player.RightClicker;
import crow.client.module.modules.player.SafeWalk;
import crow.client.module.modules.render.AntiShuffle;
import crow.client.module.modules.render.Chams;
import crow.client.module.modules.render.ArrayListMod;
import crow.client.module.modules.render.Keystrokes;
import crow.client.module.modules.render.Logo;
import crow.client.module.modules.render.Statistics;
import crow.client.module.modules.render.TargetHUD;
import crow.client.module.modules.render.ChestESP;
import crow.client.module.modules.render.CustomHotbar;
import crow.client.module.modules.render.CustomTitle;
import crow.client.module.modules.render.Crosshair;
import crow.client.module.modules.render.Fullbright;
import crow.client.module.modules.render.Nametags;
import crow.client.module.modules.render.PenisESP;
import crow.client.module.modules.render.PlayerESP;
import crow.client.module.modules.render.Radar;
import crow.client.module.modules.render.Projectiles;
import crow.client.module.modules.render.ScoreboardMod;
import crow.client.module.modules.render.Tracers;
import crow.client.module.modules.render.BedDefense;
import crow.client.module.modules.render.Indicators;
import crow.client.module.modules.render.ServerPos;
import crow.client.module.modules.render.Notifications;
import crow.client.module.modules.render.Viewmodel;
import crow.client.module.modules.themes.ThemeAurora;
import crow.client.module.modules.themes.ThemeCandy;
import crow.client.module.modules.themes.ThemeEmber;
import crow.client.module.modules.themes.ThemeLavender;
import crow.client.module.modules.themes.ThemeMint;
import crow.client.module.modules.themes.ThemeOcean;
import crow.client.module.modules.themes.ThemePink;
import crow.client.module.modules.themes.ThemePeriwinkle;
import crow.client.module.modules.themes.ThemeRainbow;
import crow.client.module.modules.themes.ThemeSapphire;
import crow.client.module.modules.themes.ThemeSunset;
import crow.client.module.modules.themes.ThemeWhiteGray;
import crow.client.module.modules.themes.ThemeSundae;
import crow.client.module.modules.themes.ThemeSunkist;
import crow.client.module.modules.themes.ThemeWater;
import crow.client.module.modules.themes.ThemeLegacy;
import crow.client.module.modules.themes.ThemeWinter;
import crow.client.module.modules.themes.ThemePeony;
import crow.client.module.modules.themes.ThemeShadow;
import crow.client.module.modules.themes.ThemeGothic;
import crow.client.module.modules.themes.ThemeNord;
import crow.client.module.modules.themes.ThemeCustom;
import crow.client.module.modules.world.AntiBot;
import crow.client.module.modules.world.ChatLogger;
import crow.client.module.modules.world.Scaffold;
import crow.client.utils.Utils;
import net.minecraft.client.gui.FontRenderer;

public class ModuleManager {
    private List<Module> modules = new ArrayList<>();

    public static boolean initialized;
    public GuiModuleManager guiModuleManager;

    public ModuleManager() {
        System.out.println(ModuleCategory.values());
        if(initialized)
            return;

        preloadLazyClasses();

        this.guiModuleManager = new GuiModuleManager();

        addModule(new LegitSpeed().withHidden(true));
        addModule(new Sprint());
        addModule(new KeepSprint().withHidden(true));
        addModule(new NoFall().withHidden(true));
        addModule(new Fly().withHidden(true));

        addModule(new KillAura());
        addModule(new Reach());
        addModule(new Velocity());
        addModule(new JumpReset());
        addModule(new WTap());
        addModule(new LeftClicker());
        addModule(new RightClicker());
        addModule(new AimAssist());
        addModule(new BowAimbot());
        addModule(new HitBox());
        addModule(new LagKB().withHidden(true));
        addModule(new LagRange().withHidden(true));
        addModule(new Backtrack());
        addModule(new AutoRod());
        addModule(new BlockIn());
        addModule(new BlockHit());
        addModule(new Clutch());
        addModule(new AntiFireball());

        addModule(new PlayerESP());
        addModule(new PenisESP());
        addModule(new ChestESP());
        addModule(new Tracers());
        addModule(new Chams());
        addModule(new Fullbright());
        addModule(new Nametags());
        addModule(new ArrayListMod());
        addModule(new Keystrokes());
        addModule(new TargetHUD());
        addModule(new Statistics());
        addModule(new Logo().withHidden(true));
        addModule(new ScoreboardMod());
        addModule(new CustomHotbar());
        addModule(new CustomTitle().withHidden(true));
        addModule(new Crosshair().withHidden(true));
        addModule(new Radar());
        addModule(new Viewmodel());
        addModule(new BedDefense());
        addModule(new Indicators());
        addModule(new Notifications());
        addModule(new ServerPos().withHidden(true));

        addModule(new NoSlow().withHidden(true));
        addModule(new AutoTool());
        addModule(new FastPlace());
        addModule(new Blink());
        addModule(new BlinkInfinite().withHidden(true));
        addModule(new Bridger());
        addModule(new SafeWalk());
        addModule(new ChestStealer());
        addModule(new AutoArmour());
        addModule(new InvManager());

        addModule(new Timer());
        addModule(new Scaffold().withHidden(true));

        addModule(new FPSSpoofer().withHidden(true));
        addModule(new AntiBot());
        addModule(new NameHider());
        addModule(new AutoGG());
        addModule(new ChatLogger());
        addModule(new FakeChat());
        addModule(new MiddleClick());
        addModule(new WaterBucket());
        addModule(new AutoPlace());
        addModule(new AutoJump());
        addModule(new Parkour());
        addModule(new FallSpeed());
        addModule(new Freecam());

        addModule(new Developer());
        addModule(new GuiModule());
        addModule(new Targets());
        addModule(new ConfigSettings());
        addModule(new HUD().withHidden(true));
        addModule(new FakeHud().withHidden(true));
        addModule(new RecordingMode().withHidden(true));

        addModule(new ThemePeriwinkle());
        addModule(new ThemeRainbow());
        addModule(new ThemePink());
        addModule(new ThemeOcean());
        addModule(new ThemeSunset());
        addModule(new ThemeWhiteGray());
        addModule(new ThemeAurora());
        addModule(new ThemeMint());
        addModule(new ThemeLavender());
        addModule(new ThemeSapphire());
        addModule(new ThemeEmber());
        addModule(new ThemeCandy());
        addModule(new ThemeSundae());
        addModule(new ThemeSunkist());
        addModule(new ThemeWater());
        addModule(new ThemeLegacy());
        addModule(new ThemeWinter());
        addModule(new ThemePeony());
        addModule(new ThemeShadow());
        addModule(new ThemeGothic());
        addModule(new ThemeNord());
        addModule(new ThemeCustom());

        initialized = true;
    }

    private void preloadLazyClasses() {

        ClassLoader cl = ModuleManager.class.getClassLoader();
        java.net.URL self = cl.getResource(
                ModuleManager.class.getName().replace('.', '/') + ".class");
        if (self == null) {
            System.err.println("[Crow] Preload skipped: cannot resolve own jar URL");
            return;
        }

        java.util.List<String> classNames = new java.util.ArrayList<>();
        try {
            String url = self.toString();
            if (url.startsWith("jar:")) {

                int bang = url.indexOf("!/");
                String jarPath = url.substring("jar:".length(), bang);
                if (jarPath.startsWith("file:")) jarPath = jarPath.substring("file:".length());
                jarPath = java.net.URLDecoder.decode(jarPath, "UTF-8");
                java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath);
                try {
                    java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        java.util.jar.JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!name.startsWith("crow/")) continue;
                        if (!name.endsWith(".class")) continue;

                        if (name.endsWith("module-info.class")) continue;
                        if (name.endsWith("package-info.class")) continue;

                        String dotted = name.substring(0, name.length() - ".class".length())
                                            .replace('/', '.');
                        classNames.add(dotted);
                    }
                } finally {
                    jar.close();
                }
            } else {

                classNames.addAll(java.util.Arrays.asList(FALLBACK_PRELOAD));
            }
        } catch (Throwable t) {
            System.err.println("[Crow] Jar enumeration failed, using fallback list: " + t);
            classNames.addAll(java.util.Arrays.asList(FALLBACK_PRELOAD));
        }

        int loaded = 0, failed = 0;
        for (String dotted : classNames) {
            try {
                Class.forName(dotted, true, cl);
                loaded++;
            } catch (Throwable t) {
                failed++;

                if (dotted.contains("$")) {
                    System.err.println("[Crow] Preload failed for " + dotted + ": " + t);
                }
            }
        }
        System.out.println("[Crow] Preloaded " + loaded + " classes (" + failed + " skipped)");
    }

    private static final String[] FALLBACK_PRELOAD = new String[] {
            "crow.client.clickgui.compact.CompactSlider",
            "crow.client.clickgui.compact.CompactDoubleSlider",
            "crow.client.clickgui.compact.CompactDoubleSlider$Handle",
            "crow.client.clickgui.compact.CompactToggle",
            "crow.client.clickgui.compact.CompactCombo",
            "crow.client.clickgui.compact.CompactButton",
            "crow.client.clickgui.compact.CompactTextInput",
            "crow.client.clickgui.compact.CompactHotbarLayout",
            "crow.client.clickgui.compact.CompactHotbarLayout$1",
            "crow.client.clickgui.compact.CompactBind",
            "crow.client.clickgui.compact.CompactCategoryItem",
            "crow.client.clickgui.compact.CompactModuleCard",
            "crow.client.module.modules.combat.BlockIn$PlaceInfo",
            "crow.client.module.modules.combat.BlockIn$RaycastMode",
            "crow.client.module.modules.combat.BlockIn$RotationPair",
            "crow.client.module.modules.combat.Clutch$PlaceTarget",
            "crow.client.module.modules.combat.Clutch$FallProjection",
            "crow.client.module.modules.combat.Clutch$FallVerdict",
            "crow.client.module.modules.combat.Clutch$Danger",
            "crow.client.module.modules.combat.Clutch$State",
            "crow.client.module.modules.render.ScoreboardMod$BoardLine",
            "crow.client.module.modules.render.ScoreboardMod$RenderedBoard",
            "crow.client.module.modules.render.Statistics$Chip",
            "crow.client.module.modules.render.Statistics$Chip$Type",
            "crow.client.module.modules.render.Statistics$ChipBounds",
    };

    public void addModule(Module m) {
        modules.add(m);
    }

    public void removeModuleByName(String s) {
        Module m = getModuleByName(s);
        modules.remove(m);
    }

    public Module getModuleByName(String name) {
        if (!initialized)
            return null;

        for (Module module : modules)
			if (module.getName().replaceAll(" ", "").equalsIgnoreCase(name) || module.getName().equalsIgnoreCase(name))
                return module;
        return null;
    }

    public Module getModuleByClazz(Class<? extends Module> c) {
        if (!initialized)
            return null;

        for (Module module : modules)
			if (module.getClass().equals(c))
                return module;
        return null;
    }

    public List<Module> getModules() {
        ArrayList<Module> allModules = new ArrayList<>(modules);
        try {
            allModules.addAll(Crow.configManager.configModuleManager.getConfigModules());
        } catch (NullPointerException ignored) {
        }
        try {
            allModules.addAll(guiModuleManager.getModules());
        } catch (NullPointerException ignored) {
        }
        return allModules;
    }

    public List<Module> getConfigModules() {
        List<Module> modulesOfC = new ArrayList<>();

        for (Module mod : getModules())
			if (!mod.isClientConfig())
				modulesOfC.add(mod);

        return modulesOfC;
    }

    public List<Module> getClientConfigModules() {
        List<Module> modulesOfCC = new ArrayList<>();

        for (Module mod : getModules())
			if (mod.isClientConfig())
				modulesOfCC.add(mod);

        return modulesOfCC;
    }

    public List<Module> getModulesInCategory(Module.ModuleCategory categ) {
        ArrayList<Module> modulesOfCat = new ArrayList<>();

        addMatching(modules, categ, modulesOfCat);
        try {
            addMatching(Crow.configManager.configModuleManager.getConfigModules(), categ, modulesOfCat);
        } catch (NullPointerException ignored) {
        }
        try {
            addMatching(guiModuleManager.getModules(), categ, modulesOfCat);
        } catch (NullPointerException ignored) {
        }

        return modulesOfCat;
    }

    // ponytail: filter the three source lists directly instead of going through
    // getModules(), which merges all ~150 modules into a throwaway ArrayList
    // first. An open ClickGui calls this once per category per frame.
    private static void addMatching(List<? extends Module> src,
                                    Module.ModuleCategory categ,
                                    List<Module> out) {
        if (src == null) return;
        for (int i = 0; i < src.size(); i++) {
            Module mod = src.get(i);
            if (mod.moduleCategory().equals(categ)
                    && (!mod.isHidden() || Module.revealHiddenModules)) {
                out.add(mod);
            }
        }
    }

    public void activateDefaultModules() {
        for (Module module : modules) {
            module.activateDefaultState();
        }
    }

    public void ensureDefaultTheme() {
        if (!initialized) {
            return;
        }

        for (Module module : modules) {
            if (module.moduleCategory() == ModuleCategory.themes && module.isEnabled()) {
                return;
            }
        }

        // Periwinkle rather than Rainbow: the accent is used as a full-row
        // flood for enabled modules, and a colour that cycles while you read
        // the list works against the one thing that list has to do.
        Module fallback = getModuleByClazz(ThemePeriwinkle.class);
        if (fallback == null) fallback = getModuleByClazz(ThemeRainbow.class);
        if (fallback != null) {
            fallback.enable();
        }
    }

    public void sort() {
        modules.sort((o1, o2) -> Utils.mc.fontRendererObj.getStringWidth(o2.getName())
                - Utils.mc.fontRendererObj.getStringWidth(o1.getName()));
    }

    public int numberOfModules() {
        return modules.size();
    }

    public void sortLongShort() {
        modules.sort(Comparator.comparingInt(o2 -> Utils.mc.fontRendererObj.getStringWidth(o2.getName())));
    }

    public void sortShortLong() {
        modules.sort((o1, o2) -> Utils.mc.fontRendererObj.getStringWidth(o2.getName())
                - Utils.mc.fontRendererObj.getStringWidth(o1.getName()));
    }

    public int getLongestActiveModule(FontRenderer fr) {
        int length = 0;
        for (Module mod : modules)
			if (mod.isEnabled())
				if (fr.getStringWidth(mod.getName()) > length)
					length = fr.getStringWidth(mod.getName());
        return length;
    }

    public int getBoxHeight(FontRenderer fr, int margin) {
        int length = 0;
        for (Module mod : modules)
			if (mod.isEnabled())
				length += fr.FONT_HEIGHT + margin;
        return length;
    }

}
