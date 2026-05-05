package crow.client.utils.font.msdf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

public final class MsdfAtlas {

    public static final class Glyph {
        public final char codepoint;
        public final float advance;
        public final float planeLeft, planeBottom, planeRight, planeTop;
        public final float atlasLeft, atlasBottom, atlasRight, atlasTop;

        Glyph(char cp, float advance,
              float pl, float pb, float pr, float pt,
              float al, float ab, float ar, float at) {
            this.codepoint = cp;
            this.advance = advance;
            this.planeLeft = pl; this.planeBottom = pb;
            this.planeRight = pr; this.planeTop = pt;
            this.atlasLeft = al; this.atlasBottom = ab;
            this.atlasRight = ar; this.atlasTop = at;
        }
    }

    private final String name;
    private boolean loaded = false;

    private int textureId = 0;

    private int atlasWidth = 0;
    private int atlasHeight = 0;

    private float pxRange = 4.0F;

    private float emLineHeight = 1.2F;
    private float emAscender   = 0.95F;
    private float emDescender  = -0.25F;

    private final Map<Character, Glyph> glyphs = new HashMap<>();

    private int atlasGlyphSize = 32;

    public MsdfAtlas(String name) {
        this.name = name;
    }

    public boolean isLoaded() { return loaded; }
    public int getTextureId() { return textureId; }
    public int getAtlasWidth() { return atlasWidth; }
    public int getAtlasHeight() { return atlasHeight; }
    public float getPxRange() { return pxRange; }
    public float getEmLineHeight() { return emLineHeight; }
    public float getEmAscender() { return emAscender; }
    public int getAtlasGlyphSize() { return atlasGlyphSize; }
    public Glyph getGlyph(char c) { return glyphs.get(c); }

    public boolean tryLoad(String pngResource, String jsonResource) {
        try (InputStream png = MsdfAtlas.class.getResourceAsStream(pngResource);
             InputStream json = MsdfAtlas.class.getResourceAsStream(jsonResource)) {
            if (png == null || json == null) return false;

            BufferedImage img = ImageIO.read(png);
            if (img == null) return false;
            atlasWidth = img.getWidth();
            atlasHeight = img.getHeight();

            JsonElement root = new JsonParser().parse(new InputStreamReader(json));
            if (!root.isJsonObject()) return false;
            JsonObject obj = root.getAsJsonObject();

            if (obj.has("atlas")) {
                JsonObject atlas = obj.getAsJsonObject("atlas");
                if (atlas.has("distanceRange")) pxRange = atlas.get("distanceRange").getAsFloat();
                if (atlas.has("size")) atlasGlyphSize = atlas.get("size").getAsInt();
                if (atlas.has("width")) atlasWidth = atlas.get("width").getAsInt();
                if (atlas.has("height")) atlasHeight = atlas.get("height").getAsInt();
            }

            if (obj.has("metrics")) {
                JsonObject metrics = obj.getAsJsonObject("metrics");
                if (metrics.has("lineHeight")) emLineHeight = metrics.get("lineHeight").getAsFloat();
                if (metrics.has("ascender"))   emAscender   = metrics.get("ascender").getAsFloat();
                if (metrics.has("descender"))  emDescender  = metrics.get("descender").getAsFloat();
            }

            if (obj.has("glyphs")) {
                JsonArray glyphArr = obj.getAsJsonArray("glyphs");
                for (int i = 0; i < glyphArr.size(); i++) {
                    JsonObject g = glyphArr.get(i).getAsJsonObject();
                    int cp = g.has("unicode") ? g.get("unicode").getAsInt() : 0;
                    float advance = g.has("advance") ? g.get("advance").getAsFloat() : 0.0F;
                    float pl = 0, pb = 0, pr = 0, pt = 0;
                    if (g.has("planeBounds")) {
                        JsonObject pb_ = g.getAsJsonObject("planeBounds");
                        pl = pb_.get("left").getAsFloat();
                        pb = pb_.get("bottom").getAsFloat();
                        pr = pb_.get("right").getAsFloat();
                        pt = pb_.get("top").getAsFloat();
                    }
                    float al = 0, ab = 0, ar = 0, at = 0;
                    if (g.has("atlasBounds")) {
                        JsonObject ab_ = g.getAsJsonObject("atlasBounds");
                        al = ab_.get("left").getAsFloat();
                        ab = ab_.get("bottom").getAsFloat();
                        ar = ab_.get("right").getAsFloat();
                        at = ab_.get("top").getAsFloat();
                    }
                    if (cp >= 0 && cp <= 0xFFFF) {
                        glyphs.put((char) cp, new Glyph(
                                (char) cp, advance,
                                pl, pb, pr, pt,
                                al, ab, ar, at));
                    }
                }
            }

            uploadTexture(img);

            loaded = true;
            return true;
        } catch (Throwable t) {

            try {
                java.io.File dir = net.minecraft.client.Minecraft.getMinecraft().mcDataDir;
                java.io.File f = new java.io.File(dir, "crow-msdf-diagnostic.log");
                try (java.io.FileWriter w = new java.io.FileWriter(f, true)) {
                    w.write("[" + System.currentTimeMillis() + "] " + name
                            + " load failed: " + t + "\n");
                }
            } catch (Throwable ignored) {}
            loaded = false;
            return false;
        }
    }

    private void uploadTexture(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);

        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = pixels[y * w + x];
                buf.put((byte) ((argb >> 16) & 0xFF));
                buf.put((byte) ((argb >>  8) & 0xFF));
                buf.put((byte) ( argb        & 0xFF));
                buf.put((byte) ((argb >> 24) & 0xFF));
            }
        }
        buf.flip();

        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
}
