package crow.client.utils.font;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import crow.client.module.modules.HUD;
import crow.client.utils.font.msdf.MsdfAtlas;
import crow.client.utils.font.msdf.MsdfFontRenderer;

public class FontUtil {
    public static volatile int completed;

    public static FontRenderer normal;
    public static FontRenderer two;
    public static FontRenderer small;
    public static FontRenderer semiBold;
    public static FontRenderer bold;
    public static FontRenderer medium;
    public static FontRenderer title;

    private static Font normal_;
    private static Font two_;
    private static Font small_;
    private static Font semiBold_;
    private static Font bold_;
    private static Font medium_;
    private static Font title_;

    private static final File WINDOWS_FONTS_DIR = new File("C:\\Windows\\Fonts");
    private static final File NEW_FONTS_DIR = new File("newfonts");
    private static final String DEFAULT_SYSTEM_FONT = "DM Sans";
    private static final long BOOTSTRAP_TIMEOUT_MS = 3000L;

    private static Font getBundledFont(Map<String, Font> cache, String location, int size, int style) {
        try {
            Font base = cache.get(location);
            if (base == null) {
                try (InputStream is = HUD.class.getResourceAsStream("/assets/crow/fonts/" + location)) {
                    if (is == null) {
                        return new Font("Dialog", style, size);
                    }
                    base = Font.createFont(Font.TRUETYPE_FONT, is);
                    cache.put(location, base);
                }
            }
            return base.deriveFont(style, (float) size);
        } catch (Exception ignored) {
            return new Font("Dialog", style, size);
        }
    }

    public static String normalizeFontName(String fontName) {
        if (fontName == null) return "";
        String normalized = fontName.trim().replace('_', ' ').replace('-', ' ');
        return normalized.replaceAll("\\s+", " ").trim();
    }

    public static String toDisplayName(String fontName) {
        String normalized = normalizeFontName(fontName);
        if (normalized.isEmpty()) return normalized;

        String[] parts = normalized.split(" ");
        StringBuilder display = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (display.length() > 0) display.append(" ");
            display.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                display.append(part.substring(1).toLowerCase());
            }
        }
        return display.toString();
    }

    public static String findInstalledFontName(String fontName) {
        Font resolved = resolveFont(fontName, Font.PLAIN, 19.0F);
        return resolved == null ? null : resolved.getFontName();
    }

    public static Font resolveFont(String fontName, int fontStyle, float fontSize) {
        String normalized = normalizeFontName(fontName);
        if (normalized.isEmpty()) return null;

        for (String familyName : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (familyName.equalsIgnoreCase(normalized)) {
                return calibrateFont(new Font(familyName, fontStyle, (int) fontSize), fontStyle, fontSize);
            }
        }

        String target = sanitizeFontToken(normalized);
        for (String familyName : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            String token = sanitizeFontToken(familyName);
            if (token.contains(target) || target.contains(token)) {
                return calibrateFont(new Font(familyName, fontStyle, (int) fontSize), fontStyle, fontSize);
            }
        }

        File fontFile = findWindowsFontFile(target);
        if (fontFile != null) {
            Font loaded = createFontFromFile(fontFile, fontStyle, fontSize);
            if (loaded != null) {
                return calibrateFont(loaded, fontStyle, fontSize);
            }
        }

        return null;
    }

    public static String changeFonts(String fontName) {
        try {
            Font newNormal = resolveAndCalibrateFont(fontName, Font.PLAIN, 19.0F);
            Font newTwo = resolveAndCalibrateFont(fontName, Font.BOLD, 30.0F);
            Font newSmall = resolveAndCalibrateFont(fontName, Font.PLAIN, 14.0F);
            Font newSemiBold = resolveAndCalibrateFont(fontName, Font.BOLD, 19.0F);
            Font newBold = resolveAndCalibrateFont(fontName, Font.BOLD, 24.0F);
            Font newMedium = resolveAndCalibrateFont(fontName, Font.PLAIN, 19.0F);
            if (newMedium == null) {
                newMedium = newSemiBold;
            }
            Font newTitle = resolveAndCalibrateFont(fontName, Font.BOLD, 52.0F);

            if (newNormal == null || newTwo == null || newSmall == null || newSemiBold == null
                    || newBold == null || newMedium == null || newTitle == null) {
                return null;
            }

            normal.setFont(newNormal);
            two.setFont(newTwo);
            small.setFont(newSmall);
            semiBold.setFont(newSemiBold);
            bold.setFont(newBold);
            medium.setFont(newMedium);
            title.setFont(newTitle);
            return newNormal.getFontName();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Font createFontFromFile(File fontFile, int fontStyle, float fontSize) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(fontStyle, fontSize);
        } catch (Exception ignored) {
        }
        try {
            return Font.createFont(Font.TYPE1_FONT, fontFile).deriveFont(fontStyle, fontSize);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static File findWindowsFontFile(String targetToken) {
        if (!WINDOWS_FONTS_DIR.isDirectory()) return null;
        File[] files = WINDOWS_FONTS_DIR.listFiles();
        if (files == null) return null;

        File partial = null;
        for (File file : files) {
            if (file == null || !file.isFile()) continue;
            String fileName = file.getName().toLowerCase();
            if (!(fileName.endsWith(".ttf") || fileName.endsWith(".otf"))) continue;

            String token = sanitizeFontToken(fileName.replaceFirst("\\.[^.]+$", ""));
            if (token.equals(targetToken)) return file;
            if (partial == null && (token.contains(targetToken) || targetToken.contains(token))) {
                partial = file;
            }
        }
        return partial;
    }

    private static String sanitizeFontToken(String input) {
        return normalizeFontName(input).toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static boolean hasLoaded() {
        return completed >= 3;
    }

    private static Font resolveAndCalibrateFont(String fontName, int fontStyle, float fontSize) {
        Font resolved = resolveFont(fontName, fontStyle, fontSize);
        return resolved == null ? null : calibrateFont(resolved, fontStyle, fontSize);
    }

    private static Font calibrateFont(Font font, int fontStyle, float requestedSize) {
        String token = sanitizeFontToken(font.getFontName());
        String familyToken = sanitizeFontToken(font.getFamily());
        float size = requestedSize;

        if (token.contains("dmsans") || familyToken.contains("dmsans")) {
            if (requestedSize >= 52.0F) {
                size = 36.0F;
            } else if (requestedSize >= 30.0F) {
                size = 20.0F;
            } else if (requestedSize >= 24.0F) {
                size = 16.0F;
            } else if (requestedSize >= 19.0F) {
                size = 13.5F;
            } else if (requestedSize >= 14.0F) {
                size = 10.5F;
            }
        } else if (token.contains("googlesans") || familyToken.contains("googlesans")
                || token.contains("productsans") || familyToken.contains("productsans")) {
            if (requestedSize >= 52.0F) {
                size = 44.0F;
            } else if (requestedSize >= 30.0F) {
                size = 26.0F;
            } else if (requestedSize >= 24.0F) {
                size = 20.0F;
            } else if (requestedSize >= 19.0F) {
                size = 16.5F;
            } else if (requestedSize >= 14.0F) {
                size = 12.5F;
            }
        }

        return font.deriveFont(fontStyle, size);
    }

    private static Font getNewFont(String fileName, int size, int style) {
        File fontFile = new File(NEW_FONTS_DIR, fileName);
        if (!fontFile.isFile()) return null;
        Font loaded = createFontFromFile(fontFile, style, size);
        return loaded == null ? null : calibrateFont(loaded, style, size);
    }

    private static Font getDefaultFont(Map<String, Font> cache, String bundledLocation, String boldBundledLocation, int size, int style) {
        String[] preferredFiles = style == Font.BOLD
                ? new String[] {"DMSans-Bold.ttf", "DMSans-SemiBold.ttf"}
                : new String[] {"DMSans-Regular.ttf"};

        for (String preferredFile : preferredFiles) {
            Font loaded = getNewFont(preferredFile, size, style);
            if (loaded != null) return loaded;
        }

        Font systemFont = resolveAndCalibrateFont(DEFAULT_SYSTEM_FONT, style, size);
        if (systemFont != null) return systemFont;

        String bundled = style == Font.BOLD ? boldBundledLocation : bundledLocation;
        return getBundledFont(cache, bundled, size, style);
    }

    public static void bootstrap() {
        completed = 0;

        new Thread(() -> {
            try {
                Map<String, Font> cache = new HashMap<>();
                normal_ = getDefaultFont(cache, "dmsans.ttf", "dmsansbold.ttf", 19, Font.PLAIN);
                two_ = getDefaultFont(cache, "dmsans.ttf", "dmsansbold.ttf", 30, Font.BOLD);
                small_ = getDefaultFont(cache, "dmsans.ttf", "dmsansbold.ttf", 14, Font.PLAIN);
                semiBold_ = getDefaultFont(cache, "dmsans.ttf", "dmsansbold.ttf", 19, Font.BOLD);
                bold_ = getDefaultFont(cache, "dmsans.ttf", "dmsansbold.ttf", 24, Font.BOLD);
                medium_ = getNewFont("DMSans-Medium.ttf", 19, Font.PLAIN);
                if (medium_ == null) {
                    medium_ = getDefaultFont(cache, "dmsans.ttf", "dmsansbold.ttf", 19, Font.BOLD);
                }
                title_ = getNewFont("DMSans-Medium.ttf", 52, Font.PLAIN);
                if (title_ == null) {
                    title_ = getDefaultFont(cache, "dmsans.ttf", "dmsansbold.ttf", 52, Font.BOLD);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                completed++;
            }
        }, "crow-font-bootstrap").start();

        new Thread(() -> completed++, "crow-font-bootstrap-aux-1").start();
        new Thread(() -> completed++, "crow-font-bootstrap-aux-2").start();

        long waitStart = System.currentTimeMillis();
        while (!hasLoaded() && System.currentTimeMillis() - waitStart < BOOTSTRAP_TIMEOUT_MS) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!hasLoaded()) {
            completed = 3;
        }

        normal_ = ensureFont(normal_, 19, Font.PLAIN);
        two_ = ensureFont(two_, 30, Font.BOLD);
        small_ = ensureFont(small_, 14, Font.PLAIN);
        semiBold_ = ensureFont(semiBold_, 19, Font.BOLD);
        bold_ = ensureFont(bold_, 24, Font.BOLD);
        medium_ = ensureFont(medium_, 19, Font.BOLD);
        title_ = ensureFont(title_, 52, Font.BOLD);

        normal = new FontRenderer(normal_, true, true);
        two = new FontRenderer(two_, true, true);
        small = new FontRenderer(small_, true, true);
        semiBold = new FontRenderer(semiBold_, true, true);
        bold = new FontRenderer(bold_, true, true);
        medium = new FontRenderer(medium_, true, true);
        title = new FontRenderer(title_, true, true);

        try {
            MsdfAtlas mediumAtlas = new MsdfAtlas("inter_medium");
            if (mediumAtlas.tryLoad(
                    "/assets/crow/msdf/inter_medium.png",
                    "/assets/crow/msdf/inter_medium.json")) {
                medium = new MsdfFontRenderer(medium_, mediumAtlas);
            }
            MsdfAtlas semiAtlas = new MsdfAtlas("inter_semibold");
            if (semiAtlas.tryLoad(
                    "/assets/crow/msdf/inter_semibold.png",
                    "/assets/crow/msdf/inter_semibold.json")) {
                semiBold = new MsdfFontRenderer(semiBold_, semiAtlas);
            }
        } catch (Throwable t) {

        }
    }

    private static Font ensureFont(Font font, int size, int style) {
        if (font != null) {
            return font;
        }
        return new Font("Dialog", style, size);
    }
}
