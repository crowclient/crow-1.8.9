package crow.client.utils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import crow.client.module.Module.ModuleCategory;
import crow.client.module.modules.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * Phosphor icon glyphs (MIT — see assets/crow/fonts/PHOSPHOR-LICENSE.txt).
 *
 * <p>The glyphs are rasterized out of the bundled icon font into small cached
 * textures rather than drawn through {@link crow.client.utils.font.FontRenderer}.
 * That renderer builds its atlas as {@code new CharData[256]}, so it can only
 * ever draw the first 256 codepoints — every Phosphor icon lives in the
 * Private Use Area around U+E000 and silently drew nothing. Rasterizing once
 * per icon sidesteps that entirely and still beats shipping PNGs: one font
 * file covers every icon, each is generated at a size we choose, and the
 * result tints with {@code glColor} like any other texture.
 *
 * <p>The bundled file is Phosphor's <b>Fill</b> weight — solid glyphs, which
 * hold up far better at the 11–13 px sizes this UI draws them at than hairline
 * outlines do. Fill uses the same codepoints as the regular weight, so the
 * constants below are unchanged and swapping the .ttf is the whole switch.
 *
 * <p>Codepoints are written as {@code \\u} escapes rather than literal glyphs
 * so the source survives any file encoding.
 */
public final class Icons {

    private Icons() {}

    /* Module categories. */
    public static final String COMBAT    = "\uE5BA"; // sword
    public static final String MOVEMENT  = "\uE730"; // person-simple-run
    public static final String PLAYER    = "\uE4C2"; // user
    public static final String WORLD     = "\uE28C"; // globe-hemisphere-west
    public static final String RENDER    = "\uE220"; // eye
    public static final String CLIENT    = "\uE272"; // gear-six
    public static final String CONFIG    = "\uE24A"; // folder
    public static final String THEMES    = "\uE6C8"; // palette
    public static final String OTHER     = "\uE204"; // dots-three-outline
    public static final String SEARCH    = "\uE30C"; // magnifying-glass

    /* Controls. */
    public static final String CARET_DOWN  = "\uE136";
    public static final String CARET_RIGHT = "\uE13A";
    public static final String KEYBOARD    = "\uE2D8";
    public static final String CLOSE       = "\uE4F6"; // x
    public static final String CHECK       = "\uE182";
    public static final String GRIP        = "\uEAE2"; // dots-six-vertical
    public static final String SLIDERS     = "\uE434"; // sliders-horizontal

    /* Stats / HUD. */
    public static final String KILLS   = "\uE1D6"; // crosshair
    public static final String DEATHS  = "\uE916"; // skull
    public static final String CLOCK   = "\uE19A";
    public static final String FPS     = "\uE000"; // pulse
    public static final String PING    = "\uE4EA"; // wifi-high
    public static final String SERVER  = "\uE2A0"; // hard-drives

    /* Main menu. */
    public static final String SINGLEPLAYER = "\uE1DA"; // cube
    public static final String MULTIPLAYER  = "\uE4D6"; // users
    public static final String OPTIONS      = "\uE434"; // sliders-horizontal
    public static final String LANGUAGE     = "\uE4A2"; // translate
    public static final String GAMEPAD      = "\uE26E"; // game-controller

    /* Menu actions. */
    public static final String PLAY     = "\uE3D0";
    public static final String FOLDER   = "\uE256"; // folder-open
    public static final String QUIT     = "\uE42A"; // sign-out
    public static final String REFRESH  = "\uE036"; // arrow-clockwise
    public static final String INFO     = "\uE2CE";
    public static final String SAVE     = "\uE248"; // floppy-disk
    public static final String TRASH    = "\uE4A6";
    public static final String EXPORT   = "\uEAF0";
    public static final String MONITOR  = "\uE32E";

    /** Glyph for a module category, or {@link #OTHER} if it has no dedicated one. */
    public static String forCategory(ModuleCategory category) {
        if (category == null) return OTHER;
        switch (category) {
            case combat:   return COMBAT;
            case movement: return MOVEMENT;
            case player:   return PLAYER;
            case world:    return WORLD;
            case render:   return RENDER;
            case client:   return CLIENT;
            case config:   return CONFIG;
            case themes:   return THEMES;
            case search:   return SEARCH;
            default:       return OTHER;
        }
    }

    /* ===================================================================== */
    /* Rasterization                                                          */
    /* ===================================================================== */

    /** Every icon is baked at this pixel size and scaled down when drawn.
     *  Comfortably above the largest on-screen size (a ~13 px icon at GUI
     *  scale 4), so the GPU only ever downsamples. */
    private static final int BAKE_PX = 64;

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static Font iconFont;
    private static boolean fontLoadFailed;

    private static Font font() {
        if (iconFont != null || fontLoadFailed) return iconFont;
        try (InputStream in = HUD.class.getResourceAsStream("/assets/crow/fonts/phosphor.ttf")) {
            if (in == null) {
                fontLoadFailed = true;
                return null;
            }
            iconFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) BAKE_PX);
        } catch (Throwable t) {
            fontLoadFailed = true;
        }
        return iconFont;
    }

    /** True once the icon font is usable. Callers must tolerate false. */
    public static boolean available() {
        return font() != null;
    }

    private static ResourceLocation texture(String glyph) {
        ResourceLocation cached = CACHE.get(glyph);
        if (cached != null) return cached;

        Font f = font();
        if (f == null) return null;

        try {
            // Pad so antialiased edges and any glyph that overflows its
            // nominal box aren't clipped.
            int size = BAKE_PX + 16;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setFont(f);
            // White, so glColor at draw time is the only thing deciding colour.
            g.setColor(Color.WHITE);

            java.awt.FontMetrics fm = g.getFontMetrics();
            Rectangle2D bounds = fm.getStringBounds(glyph, g);
            float gx = (float) ((size - bounds.getWidth()) / 2.0 - bounds.getX());
            float gy = (float) ((size - bounds.getHeight()) / 2.0 - bounds.getY());
            g.drawString(glyph, gx, gy);
            g.dispose();

            ResourceLocation loc = Minecraft.getMinecraft().renderEngine
                    .getDynamicTextureLocation("crow_icon_" + Integer.toHexString(glyph.charAt(0)),
                            new DynamicTexture(img));
            CACHE.put(glyph, loc);
            return loc;
        } catch (Throwable t) {
            CACHE.put(glyph, null);
            return null;
        }
    }

    /* ===================================================================== */
    /* Drawing                                                                */
    /* ===================================================================== */

    /** Draw {@code glyph} centered on ({@code cx}, {@code cy}), {@code size} px tall. */
    public static void draw(String glyph, float cx, float cy, float size, int color) {
        if (glyph == null || size <= 0.0F) return;
        ResourceLocation tex = texture(glyph);
        if (tex == null) return;

        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float gg = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        if (a <= 0.0F) return;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, gg, b, a);
        RenderUtils.bindSmoothIcon(tex);

        // The baked image is square, so one scale factor covers both axes.
        GlStateManager.translate(cx - size / 2.0F, cy - size / 2.0F, 0.0F);
        float scale = size / (float) (BAKE_PX + 16);
        GlStateManager.scale(scale, scale, 1.0F);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0,
                BAKE_PX + 16, BAKE_PX + 16, BAKE_PX + 16, BAKE_PX + 16);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    /** Draw left-aligned from {@code x}, vertically centered on {@code cy}. */
    public static void drawLeft(String glyph, float x, float cy, float size, int color) {
        draw(glyph, x + size / 2.0F, cy, size, color);
    }

    /** Phosphor glyphs are square, so an icon's advance is its size. */
    public static float width(String glyph, float size) {
        return glyph == null || !available() ? 0.0F : size;
    }
}
