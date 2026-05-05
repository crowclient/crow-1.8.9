package crow.client.module.modules.render;

import com.google.gson.JsonObject;
import crow.client.clickgui.crow.ClickGui;
import crow.client.module.Module;
import crow.client.module.modules.render.music.AppleMusicSource;
import crow.client.module.modules.render.music.DesktopMediaSource;
import crow.client.module.modules.render.music.MusicSource;
import crow.client.module.modules.render.music.MusicTrack;
import crow.client.module.modules.render.music.SpotifySource;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TextSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;

public class MusicWidget extends Module {

    public enum SourceMode { Desktop, Spotify, Apple_Music }

    private final ComboSetting<SourceMode>  sourceSetting;
    private final TextSetting               tokenSetting;
    private final TextSetting               devTokenSetting;
    private final DescriptionSetting        statusDesc;
    private final TickSetting               showAlbumArt;
    private final TickSetting               showControls;
    private final SliderSetting             widgetWidth;

    private static final int PAD_H    = 8;
    private static final int PAD_V    = 8;
    private static final int ART_SIZE = 44;
    private static final int ART_GAP  = 7;
    private static final int BAR_H    = 3;
    private static final int CTRL_H   = 28;
    private static final int BASE_H   = PAD_V + ART_SIZE + PAD_V;

    private int posX = 10;
    private int posY = 10;

    private boolean dragging     = false;
    private int     dragOffX     = 0;
    private int     dragOffY     = 0;
    private boolean mouseWasDown = false;

    private final DesktopMediaSource desktopSource = new DesktopMediaSource();
    private final SpotifySource    spotifySource    = new SpotifySource();
    private final AppleMusicSource appleMusicSource = new AppleMusicSource();
    private MusicSource            activeSource     = null;
    private SourceMode             lastSource       = null;

    private ResourceLocation albumTexture       = null;
    private String           lastTextureTrackUrl = "";

    private static MusicWidget INSTANCE;

    public MusicWidget() {
        super("Music", ModuleCategory.render);
        INSTANCE = this;

        this.registerSetting(sourceSetting   = new ComboSetting<>("Source", SourceMode.Desktop));
        this.registerSetting(tokenSetting    = new TextSetting(
                "Token", "", "Spotify OAuth token  —OR—  Apple Music User Token"));
        this.registerSetting(devTokenSetting = new TextSetting(
                "Dev Token", "", "Apple Music Developer JWT (leave blank for Spotify)"));
        this.registerSetting(statusDesc      = new DescriptionSetting("Status: not connected"));
        this.registerSetting(showAlbumArt    = new TickSetting("Album Art",  true));
        this.registerSetting(showControls    = new TickSetting("Controls",   true));
        this.registerSetting(widgetWidth     = new SliderSetting("Width", 220, 160, 310, 10));
        tokenSetting.visibleWhen(() -> sourceSetting != null && sourceSetting.getMode() != SourceMode.Desktop);
        devTokenSetting.visibleWhen(() -> sourceSetting != null && sourceSetting.getMode() == SourceMode.Apple_Music);
    }

    public static MusicWidget getInstance() { return INSTANCE; }

    @Override
    public void onEnable() {
        startActiveSource();
    }

    @Override
    public void onDisable() {
        if (activeSource != null) { activeSource.stop(); activeSource = null; }
        lastSource = null;
        dragging   = false;
    }

    @Override
    public void guiUpdate() {
        SourceMode sel = sourceSetting.getMode();

        if (isEnabled() && sel != lastSource) {
            if (activeSource != null) activeSource.stop();
            startActiveSource();
        }
        if (activeSource != null) {
            activeSource.setToken(tokenSetting.getValue());
            activeSource.setSecondaryToken(devTokenSetting.getValue());
        }
        statusDesc.setDesc("Status: " + (activeSource != null ? activeSource.getStatus() : "off"));
    }

    @Override
    public JsonObject getConfigAsJson() {
        JsonObject data = super.getConfigAsJson();
        data.addProperty("widgetX", posX);
        data.addProperty("widgetY", posY);
        return data;
    }

    @Override
    public void applyConfigFromJson(JsonObject data) {
        super.applyConfigFromJson(data);
        if (data.has("widgetX")) posX = data.get("widgetX").getAsInt();
        if (data.has("widgetY")) posY = data.get("widgetY").getAsInt();
    }

    public void drawInGui(int mx, int my) {

        if (activeSource != null) {
            activeSource.setToken(tokenSetting.getValue());
            activeSource.setSecondaryToken(devTokenSetting.getValue());
            statusDesc.setDesc("Status: " + activeSource.getStatus());
        }

        checkUploadAlbumArt();

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        int     W        = (int) widgetWidth.getInput();
        boolean art      = showAlbumArt.isToggled();
        boolean controls = showControls.isToggled()
                && (activeSource == null || activeSource.supportsControls());
        int H = BASE_H + (controls ? CTRL_H : 0);

        posX = Math.max(0, Math.min(posX, sw - W));
        posY = Math.max(0, Math.min(posY, sh - H));

        boolean mouseDown = org.lwjgl.input.Mouse.isButtonDown(0);
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            mouseWasDown = mouseDown;
        } else {
            if (mouseDown && !mouseWasDown) {
                if (inWidget(mx, my, W, H)) {
                    if (!controls || !inControlsRow(mx, my)) {
                        dragging = true;
                        dragOffX = mx - posX;
                        dragOffY = my - posY;
                    }
                }
            }
            if (!mouseDown) dragging = false;
            if (dragging && mouseDown) {
                posX = Math.max(0, Math.min(mx - dragOffX, sw - W));
                posY = Math.max(0, Math.min(my - dragOffY, sh - H));
            }
            mouseWasDown = mouseDown;
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();

        RenderUtils.drawRoundedRectAA(posX, posY, posX + W, posY + H, 7, 0xE0101014);
        RenderUtils.drawRoundedOutline(posX, posY, posX + W, posY + H, 7, 1.0F, 0x30FFFFFF);

        MusicTrack track = activeSource != null ? activeSource.getCurrentTrack() : null;
        if (track == null) {
            drawNoTrack(W, H);
        } else {
            drawTrack(track, W, art);
            if (controls) drawControls(track, W, mx, my);
        }

        GlStateManager.enableDepth();
    }

    public boolean handleGuiClick(int mx, int my, int button) {
        int     W        = (int) widgetWidth.getInput();
        boolean controls = showControls.isToggled()
                && (activeSource == null || activeSource.supportsControls());
        int H = BASE_H + (controls ? CTRL_H : 0);

        if (!inWidget(mx, my, W, H)) return false;

        if (controls && inControlsRow(mx, my)) {
            MusicTrack track = activeSource != null ? activeSource.getCurrentTrack() : null;
            int btnW  = (W - PAD_H * 4) / 3;
            int btnH  = 16;
            int btnY  = posY + BASE_H + 6;
            int prevX = posX + PAD_H;
            int playX = prevX + btnW + PAD_H;
            int nextX = playX + btnW + PAD_H;

            if (inRect(mx, my, prevX, btnY, btnW, btnH)) { if (activeSource != null) activeSource.sendPrevious(); return true; }
            if (inRect(mx, my, playX, btnY, btnW, btnH)) {
                if (activeSource != null) {
                    if (track != null && track.isPlaying) activeSource.sendPause();
                    else                                  activeSource.sendPlay();
                }
                return true;
            }
            if (inRect(mx, my, nextX, btnY, btnW, btnH)) { if (activeSource != null) activeSource.sendNext(); return true; }
        }
        return true;
    }

    public void handleGuiRelease() {
        dragging = false;
    }

    private void startActiveSource() {
        SourceMode sel = sourceSetting.getMode();
        lastSource = sel;
        if (sel == SourceMode.Desktop) {
            desktopSource.start("");
            activeSource = desktopSource;
        } else if (sel == SourceMode.Apple_Music) {
            appleMusicSource.setSecondaryToken(devTokenSetting.getValue());
            appleMusicSource.start(tokenSetting.getValue());
            activeSource = appleMusicSource;
        } else {
            spotifySource.start(tokenSetting.getValue());
            activeSource = spotifySource;
        }
    }

    private void drawNoTrack(int W, int H) {
        String msg = activeSource != null ? activeSource.getStatus() : "Disabled";
        double tw = FontUtil.small.getStringWidth(msg);
        FontUtil.small.drawSmoothString(msg,
                posX + (W - tw) / 2.0,
                posY + (float)(H - FontUtil.small.getHeight()) / 2f,
                0x66FFFFFF);
    }

    private void drawTrack(MusicTrack track, int W, boolean artEnabled) {
        int artX  = artEnabled ? posX + PAD_H : -999;
        int artY  = posY + PAD_V;
        int textX = artEnabled ? artX + ART_SIZE + ART_GAP : posX + PAD_H;
        float textW = W - (textX - posX) - PAD_H;

        if (artEnabled) {
            if (albumTexture != null) {
                GlStateManager.enableBlend();
                GlStateManager.enableTexture2D();
                GlStateManager.color(1f, 1f, 1f, 1f);
                mc.getTextureManager().bindTexture(albumTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                Gui.drawModalRectWithCustomSizedTexture(artX, artY, 0, 0, ART_SIZE, ART_SIZE, ART_SIZE, ART_SIZE);

            } else {
                RenderUtils.drawRoundedRectAA(artX, artY, artX + ART_SIZE, artY + ART_SIZE, 4, 0xFF1E1E24);
                double noteX = artX + (ART_SIZE - FontUtil.small.getStringWidth("?")) / 2.0;
                FontUtil.small.drawSmoothString("?", noteX,
                        artY + (float)(ART_SIZE - FontUtil.small.getHeight()) / 2f, 0x44FFFFFF);
            }
        }

        float titleY  = posY + PAD_V;
        float artistY = titleY + 12f;
        float barY    = artistY + 10f;
        float tsY     = barY + BAR_H + 4f;

        String title  = truncate(track.title,  textW, FontUtil.semiBold);
        String artist = truncate(track.artist, textW, FontUtil.small);

        FontUtil.semiBold.drawSmoothString(title,  textX, titleY,  0xFFFFFFFF);
        FontUtil.small.drawSmoothString   (artist, textX, artistY, 0xFF9A9AA4);

        float barW   = textW;
        float filled = barW * track.getProgress();

        Gui.drawRect((int) textX, (int) barY,
                     (int)(textX + barW), (int)(barY + BAR_H), 0xFF2A2A2E);
        if (filled > 1f) {
            RenderUtils.drawFlowingGradientRect(
                    (int) textX, (int) barY,
                    (int)(textX + filled), (int)(barY + BAR_H),
                    220, 0);
        }
        if (filled >= 2f) {
            int dotX = (int)(textX + filled - 1);
            Gui.drawRect(dotX - 1, (int) barY - 1, dotX + 2, (int)(barY + BAR_H + 1), 0xFFFFFFFF);
        }

        long liveProgressMs = track.isPlaying
                ? track.progressMs + (System.currentTimeMillis() % 1500L)
                : track.progressMs;
        liveProgressMs = Math.min(liveProgressMs, track.durationMs);

        String ts = MusicTrack.formatMs(liveProgressMs) + " / " + MusicTrack.formatMs(track.durationMs);
        FontUtil.small.drawSmoothString(ts, textX, tsY, 0xFF666670);

        String stateIcon = track.isPlaying ? "\u25B6" : "\u23F8";
        double iconW = FontUtil.small.getStringWidth(stateIcon);
        FontUtil.small.drawSmoothString(stateIcon, posX + W - PAD_H - iconW, tsY, 0xFF555560);
    }

    private void drawControls(MusicTrack track, int W, int mx, int my) {
        int dividerY = posY + BASE_H;
        Gui.drawRect(posX + PAD_H, dividerY + 2, posX + W - PAD_H, dividerY + 3, 0x28FFFFFF);

        int btnW  = (W - PAD_H * 4) / 3;
        int btnH  = 16;
        int btnY  = dividerY + 6;
        int prevX = posX + PAD_H;
        int playX = prevX + btnW + PAD_H;
        int nextX = playX + btnW + PAD_H;

        boolean prevHov = inRect(mx, my, prevX, btnY, btnW, btnH);
        boolean playHov = inRect(mx, my, playX, btnY, btnW, btnH);
        boolean nextHov = inRect(mx, my, nextX, btnY, btnW, btnH);

        drawCtrlButton(prevX, btnY, btnW, btnH, "|<", prevHov);
        drawCtrlButton(playX, btnY, btnW, btnH,
                (track != null && track.isPlaying) ? "||" : ">", playHov);
        drawCtrlButton(nextX, btnY, btnW, btnH, ">|", nextHov);
    }

    private void drawCtrlButton(int bx, int by, int bw, int bh, String label, boolean hovered) {
        int bg = hovered ? 0xFF2A2A3A : 0xFF1A1A22;
        RenderUtils.drawRoundedRectAA(bx, by, bx + bw, by + bh, 4, bg);
        if (hovered) RenderUtils.drawRoundedOutline(bx, by, bx + bw, by + bh, 4, 1.0F,
                ClickGui.getRainbowAtX(bx));
        double lw = FontUtil.small.getStringWidth(label);
        float  lh = FontUtil.small.getHeight();
        FontUtil.small.drawSmoothString(label, bx + (bw - lw) / 2.0, by + (bh - lh) / 2f,
                hovered ? 0xFFFFFFFF : 0xFFAAAAAA);
    }

    private void checkUploadAlbumArt() {
        MusicTrack track = activeSource != null ? activeSource.getCurrentTrack() : null;
        if (track == null) return;

        if (!track.albumArtUrl.equals(lastTextureTrackUrl)) {
            if (albumTexture != null) {
                mc.getTextureManager().deleteTexture(albumTexture);
                albumTexture = null;
            }
            lastTextureTrackUrl = track.albumArtUrl;
        }

        if (albumTexture == null) {
            BufferedImage img = activeSource.takeAlbumImage();
            if (img != null) {
                try {
                    DynamicTexture dt = new DynamicTexture(img);
                    albumTexture = mc.getTextureManager()
                            .getDynamicTextureLocation("music_album_art", dt);
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean inWidget(int mx, int my, int W, int H) {
        return mx >= posX && mx <= posX + W && my >= posY && my <= posY + H;
    }

    private boolean inControlsRow(int mx, int my) {
        return my >= posY + BASE_H && my <= posY + BASE_H + CTRL_H;
    }

    private boolean inRect(int mx, int my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    private String truncate(String text, double maxWidth, crow.client.utils.font.FontRenderer font) {
        if (font.getStringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        double ellipsisW = font.getStringWidth(ellipsis);
        while (text.length() > 1 && font.getStringWidth(text) + ellipsisW > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
