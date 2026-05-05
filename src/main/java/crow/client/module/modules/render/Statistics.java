package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.modules.HUD;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.GlowUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Statistics extends Module {
    public enum DisplayMode { Bar, Modern, Exhibition, Classic, Minimal }

    public static ComboSetting<DisplayMode> displayMode;
    public static TickSetting showLogo;
    public static TickSetting showWordmark;
    public static TickSetting showKills;
    public static TickSetting showPlaytime;
    public static TickSetting showClock;
    public static TickSetting showIrlDay;
    public static TickSetting showFPS;
    public static TickSetting showPing;
    public static TickSetting showCoords;
    public static TickSetting showDirection;
    public static TickSetting showBiome;
    public static TickSetting showServer;
    public static TickSetting showSpeed;
    public static TickSetting showDay;

    public static SliderSetting orderLogo;
    public static SliderSetting orderWordmark;
    public static SliderSetting orderServer;
    public static SliderSetting orderPing;
    public static SliderSetting orderFPS;
    public static SliderSetting orderSession;
    public static SliderSetting orderClock;
    public static SliderSetting orderIrlDay;
    public static SliderSetting orderKills;
    public static SliderSetting orderCoords;
    public static SliderSetting orderDirection;
    public static SliderSetting orderBiome;
    public static SliderSetting orderSpeed;
    public static SliderSetting orderMcDay;
    public static TickSetting customFont;
    public static SliderSetting posX, posY;

    public static SliderSetting barSize;

    private static final ResourceLocation ICON_FPS    = new ResourceLocation("crow", "icons/fps.png");
    private static final ResourceLocation ICON_PING   = new ResourceLocation("crow", "icons/ping.png");
    private static final ResourceLocation ICON_SERVER = new ResourceLocation("crow", "icons/server.png");
    private static final ResourceLocation ICON_CLOCK  = new ResourceLocation("crow", "icons/clock.png");

    private int draggingChipIdx = -1;

    private int chipDragMouseAnchorX;

    private int chipDragChipAnchorX;

    private boolean wasMouseDown = false;

    private final List<ChipBounds> lastChipBounds = new ArrayList<>();

    private static final class ChipBounds {
        final int idx;
        final int leftX, rightX;
        final Chip.Type type;
        ChipBounds(int idx, int leftX, int rightX, Chip.Type type) {
            this.idx = idx; this.leftX = leftX; this.rightX = rightX; this.type = type;
        }
    }

    private int sessionKills;
    private long sessionStartTime;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private int lastValidPing;
    private int displayedPing;
    private long lastPingUpdate;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("h:mm a");
    private static final String[] CARDINALS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private static final float RING_FULL_MINUTES = 60.0F;

    public Statistics() {
        super("Statistics", ModuleCategory.render);

        this.registerSetting(showLogo = new TickSetting("Show Logo", true));
        this.registerSetting(showWordmark = new TickSetting("Show Wordmark", true));
        this.registerSetting(showServer = new TickSetting("Show Server", true));
        this.registerSetting(showPing = new TickSetting("Show Ping", true));
        this.registerSetting(showFPS = new TickSetting("Show FPS", true));
        this.registerSetting(showPlaytime = new TickSetting("Show Session Time", false));
        this.registerSetting(showClock = new TickSetting("Show IRL Time", false));
        this.registerSetting(showIrlDay = new TickSetting("Show IRL Day", false));
        this.registerSetting(showKills = new TickSetting("Show Kills", false));
        this.registerSetting(showCoords = new TickSetting("Show Coordinates", false));
        this.registerSetting(showDirection = new TickSetting("Show Direction", false));
        this.registerSetting(showBiome = new TickSetting("Show Biome", false));
        this.registerSetting(showSpeed = new TickSetting("Show Speed", false));
        this.registerSetting(showDay = new TickSetting("Show MC Day", false));

        this.registerSetting(orderLogo      = new SliderSetting("Logo Order", 1, 1, 20, 1));
        this.registerSetting(orderWordmark  = new SliderSetting("Wordmark Order", 2, 1, 20, 1));
        this.registerSetting(orderServer    = new SliderSetting("Server Order", 3, 1, 20, 1));
        this.registerSetting(orderPing      = new SliderSetting("Ping Order", 4, 1, 20, 1));
        this.registerSetting(orderFPS       = new SliderSetting("FPS Order", 5, 1, 20, 1));
        this.registerSetting(orderSession   = new SliderSetting("Session Time Order", 6, 1, 20, 1));
        this.registerSetting(orderClock     = new SliderSetting("IRL Time Order", 7, 1, 20, 1));
        this.registerSetting(orderIrlDay    = new SliderSetting("IRL Day Order", 8, 1, 20, 1));
        this.registerSetting(orderKills     = new SliderSetting("Kills Order", 9, 1, 20, 1));
        this.registerSetting(orderCoords    = new SliderSetting("Coordinates Order", 10, 1, 20, 1));
        this.registerSetting(orderDirection = new SliderSetting("Direction Order", 11, 1, 20, 1));
        this.registerSetting(orderBiome     = new SliderSetting("Biome Order", 12, 1, 20, 1));
        this.registerSetting(orderSpeed     = new SliderSetting("Speed Order", 13, 1, 20, 1));
        this.registerSetting(orderMcDay     = new SliderSetting("MC Day Order", 14, 1, 20, 1));
        this.registerSetting(customFont = new TickSetting("Custom font", true));
        this.registerSetting(barSize = new SliderSetting("Size", 1.0D, 0.6D, 2.0D, 0.05D));

        this.registerSetting(posX = new SliderSetting("X", 10, 0, 4000, 1));
        this.registerSetting(posY = new SliderSetting("Y", 10, 0, 2000, 1));
    }

    @Override
    public void onEnable() {
        sessionKills = 0;
        sessionStartTime = System.currentTimeMillis();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof AttackEntityEvent) {
            AttackEntityEvent e = (AttackEntityEvent) fe.getEvent();
            if (e.target instanceof net.minecraft.entity.player.EntityPlayer) {
                net.minecraft.entity.player.EntityPlayer target = (net.minecraft.entity.player.EntityPlayer) e.target;
                if (target.getHealth() <= 0) {
                    sessionKills++;
                }
            }
        }
        if (fe.getEvent() instanceof LivingDeathEvent) {
            LivingDeathEvent e = (LivingDeathEvent) fe.getEvent();
            if (e.source != null && e.source.getEntity() == mc.thePlayer
                    && e.entity instanceof net.minecraft.entity.player.EntityPlayer) {
                sessionKills++;
            }
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;

        List<StatLine> lines = buildLines();
        if (lines.isEmpty() && !showPlaytime.isToggled()) return;

        renderBar();
    }

    private static void drawArc(float cx, float cy, float outerR, float innerR,
                                float startDeg, float endDeg, int color) {
        RenderUtils.drawArcAA(cx, cy, outerR, innerR, startDeg, endDeg, color);
    }

    private List<StatLine> buildLines() {
        List<StatLine> lines = new ArrayList<>();

        if (shouldInclude(StatKey.CLOCK)) {
            lines.add(new StatLine("", TIME_FORMAT.format(new Date())));
        }

        if (shouldInclude(StatKey.FPS)) {
            lines.add(new StatLine("FPS ", String.valueOf(Minecraft.getDebugFPS())));
        }

        if (shouldInclude(StatKey.PING)) {
            lines.add(new StatLine("Ping ", getStablePing() + "ms"));
        }

        if (shouldInclude(StatKey.KILLS)) {
            lines.add(new StatLine("Kills ", String.valueOf(sessionKills)));
        }

        if (shouldInclude(StatKey.COORDS) && mc.thePlayer != null) {
            lines.add(new StatLine("XYZ ", String.format("%.0f %.0f %.0f",
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)));
        }

        if (shouldInclude(StatKey.DIRECTION) && mc.thePlayer != null) {
            float yaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw);
            int idx = Math.round(yaw / 45.0F) & 7;
            lines.add(new StatLine("Dir ", CARDINALS[idx]));
        }

        if (shouldInclude(StatKey.BIOME) && mc.thePlayer != null && mc.theWorld != null) {
            try {
                net.minecraft.util.BlockPos pos = new net.minecraft.util.BlockPos(
                        mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                net.minecraft.world.biome.BiomeGenBase biome = mc.theWorld.getBiomeGenForCoords(pos);
                lines.add(new StatLine("", biome.biomeName));
            } catch (Exception ignored) {}
        }

        if (shouldInclude(StatKey.SPEED) && mc.thePlayer != null) {
            double dx = mc.thePlayer.posX - mc.thePlayer.prevPosX;
            double dz = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
            double bps = Math.sqrt(dx * dx + dz * dz) * 20.0;
            lines.add(new StatLine("Speed ", String.format("%.1f", bps)));
        }

        if (shouldInclude(StatKey.SERVER)) {
            String ip = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "Local";
            lines.add(new StatLine("", ip));
        }

        if (shouldInclude(StatKey.DAY) && mc.theWorld != null) {
            long day = mc.theWorld.getWorldTime() / 24000L + 1;
            lines.add(new StatLine("Day ", String.valueOf(day)));
        }

        return lines;
    }

    private int getStablePing() {
        long now = System.currentTimeMillis();
        if (now - lastPingUpdate < 250L) {
            return displayedPing;
        }
        lastPingUpdate = now;

        if (mc.thePlayer == null || mc.getNetHandler() == null) return 0;
        try {
            net.minecraft.client.network.NetworkPlayerInfo info =
                    mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            int ping = info != null ? info.getResponseTime() : 0;
            if (ping > 0 && ping < 10000) {
                lastValidPing = ping;
            }
            int targetPing = lastValidPing;
            displayedPing = displayedPing == 0 ? targetPing : (int) (displayedPing + (targetPing - displayedPing) * 0.35F);
            return displayedPing;
        } catch (Exception e) {
            return displayedPing > 0 ? displayedPing : lastValidPing;
        }
    }

    private enum StatKey { KILLS, CLOCK, FPS, PING, COORDS, DIRECTION, BIOME, SERVER, SPEED, DAY }

    private boolean shouldInclude(StatKey key) {
        switch (key) {
            case KILLS: return showKills.isToggled();
            case CLOCK: return showClock.isToggled();
            case FPS: return showFPS.isToggled();
            case PING: return showPing.isToggled();
            case COORDS: return showCoords.isToggled();
            case DIRECTION: return showDirection.isToggled();
            case BIOME: return showBiome.isToggled();
            case SERVER: return showServer.isToggled();
            case SPEED: return showSpeed.isToggled();
            case DAY: return showDay.isToggled();
            default: return false;
        }
    }

    private void renderExhibition(List<StatLine> lines) {
        String text = buildInlineText(lines, true, " | ");
        if (text.isEmpty()) return;

        int x = (int) posX.getInput();
        int y = (int) posY.getInput();
        int textW = getInlineWidth(text, true);
        int boxH = 20;
        int boxW = textW + 18;
        int left = x;
        int top = y;
        int right = x + boxW;
        int bottom = y + boxH;
        handleDragging(left, top, right, bottom);

        boolean useGlow = HUD.enableGlow != null && HUD.enableGlow.isToggled();
        boolean useBlur = HUD.enableBlur != null && HUD.enableBlur.isToggled();
        int accent = GuiModule.getThemeColor(0);
        int accentRGB = accent & 0x00FFFFFF;

        if (useBlur) {
            GUIBlurUtil.drawBlurredBackground(left, top, boxW, boxH,
                    (int) HUD.blurRadius.getInput(), boxH / 2, 0.55f);
            mc.entityRenderer.setupOverlayRendering();
        }

        RenderUtils.drawRoundedRectAA(left, top, right, bottom, boxH / 2.0F, 0xD814171D);
        if (useGlow) {
            GlowUtil.drawGlow(left, top, boxW, boxH,
                    (int) HUD.glowRadius.getInput(), boxH / 2.0F,
                    0xFF000000 | accentRGB, (float) HUD.glowIntensity.getInput() * 0.45F);
        }

        float textY = top + (boxH - getInlineHeight(true)) / 2.0F;
        drawInlineText(text, left + 9, textY, accentRGB, useGlow, true);
    }

    private void renderClassic(List<StatLine> lines) {
        int x = (int) posX.getInput();
        int y = (int) posY.getInput();
        handleDragging(x, y, x + getClassicWidth(lines), y + Math.max(1, lines.size()) * getLineHeight());

        int accentRGB = GuiModule.getThemeColor(0) & 0x00FFFFFF;
        boolean useGlow = HUD.enableGlow != null && HUD.enableGlow.isToggled();
        float drawY = y;
        for (StatLine line : lines) {
            String text = line.label + line.value;
            drawValueText(text, x, drawY, 0xFFF2F4F7, accentRGB, useGlow, false);
            drawY += getLineHeight();
        }
    }

    private void renderMinimal(List<StatLine> lines) {
        String text = buildInlineText(lines, false, " | ");
        if (text.isEmpty()) return;

        int x = (int) posX.getInput();
        int y = (int) posY.getInput();
        int h = 16;
        int w = getInlineWidth(text, false) + 14;
        handleDragging(x, y, x + w, y + h);

        boolean useGlow = HUD.enableGlow != null && HUD.enableGlow.isToggled();
        int accentRGB = GuiModule.getThemeColor(0) & 0x00FFFFFF;
        RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, h / 2.0F, 0xC414171D);
        if (useGlow) {
            GlowUtil.drawGlow(x, y, w, h,
                    Math.max(4, (int) HUD.glowRadius.getInput() - 2), h / 2.0F,
                    0xFF000000 | accentRGB, (float) HUD.glowIntensity.getInput() * 0.35F);
        }
        float textY = y + (h - getInlineHeight(false)) / 2.0F;
        drawInlineText(text, x + 7, textY, accentRGB, useGlow, false);
    }

    private String buildInlineText(List<StatLine> lines, boolean includeCrow, String separator) {
        List<String> parts = new ArrayList<>();
        if (includeCrow) {
            parts.add("Crow");
        }
        if (showPlaytime.isToggled()) {
            parts.add("Time " + getSessionTimeString());
        }
        for (StatLine line : lines) {
            String combined = (line.label == null ? "" : line.label) + line.value;
            combined = combined.trim();
            if (!combined.isEmpty()) {
                parts.add(combined);
            }
        }
        return String.join(separator, parts);
    }

    private String getSessionTimeString() {
        long elapsedSec = Math.max(0L, (System.currentTimeMillis() - sessionStartTime) / 1000L);
        long hrs = elapsedSec / 3600L;
        long mins = (elapsedSec % 3600L) / 60L;
        long secs = elapsedSec % 60L;
        if (hrs > 0L) {
            return hrs + "h " + mins + "m";
        }
        if (mins > 0L) {
            return mins + "m " + secs + "s";
        }
        return secs + "s";
    }

    private int getClassicWidth(List<StatLine> lines) {
        int width = 0;
        for (StatLine line : lines) {
            width = Math.max(width, getTextWidth(line.label + line.value));
        }
        return width;
    }

    private int getInlineWidth(String text, boolean boldStyle) {
        if (customFont.isToggled()) {
            return (int) ((boldStyle ? FontUtil.semiBold : FontUtil.normal).getStringWidth(text));
        }
        return mc.fontRendererObj.getStringWidth(text);
    }

    private int getInlineHeight(boolean boldStyle) {
        if (customFont.isToggled()) {
            return boldStyle ? FontUtil.semiBold.getHeight() : FontUtil.normal.getHeight();
        }
        return mc.fontRendererObj.FONT_HEIGHT;
    }

    private void drawInlineText(String text, float x, float y, int accentRGB, boolean useGlow, boolean boldStyle) {
        int color = 0xFFF2F4F7;
        if (customFont.isToggled()) {
            if (useGlow) {
                (boldStyle ? FontUtil.semiBold : FontUtil.normal).drawGlowString(
                        text, x, y, color, 0xFF000000 | accentRGB, 2.0F, 0.4F);
            } else {
                (boldStyle ? FontUtil.semiBold : FontUtil.normal).drawSmoothString(text, x, y, color);
            }
        } else {
            if (useGlow) {
                RenderUtils.drawVanillaTextGlow(mc.fontRendererObj, text, x, y,
                        0xFF000000 | accentRGB, 2.0F, 0.4F);
            }
            mc.fontRendererObj.drawString(text, (int) x, (int) y, color, false);
        }
    }

    private void drawLabelText(String text, float x, float y, int color) {
        if (customFont.isToggled()) {
            FontUtil.small.drawSmoothString(text, x, y, color);
        } else {
            mc.fontRendererObj.drawString(text, (int) x, (int) y, color, false);
        }
    }

    private void drawValueText(String text, float x, float y, int color, int accentRGB, boolean useGlow, boolean strong) {
        float radius = strong ? 2.0F : 1.75F;
        float intensity = strong ? 0.7F : 0.5F;
        if (customFont.isToggled()) {
            if (useGlow) {
                FontUtil.semiBold.drawGlowString(text, x, y, color, 0xFF000000 | accentRGB, radius, intensity);
            } else {
                FontUtil.semiBold.drawSmoothString(text, x, y, color);
            }
        } else {
            if (useGlow) {
                RenderUtils.drawVanillaTextGlow(mc.fontRendererObj, text, x, y,
                        0xFF000000 | accentRGB, radius, intensity);
            }
            mc.fontRendererObj.drawString(text, (int) x, (int) y, color, false);
        }
    }

    private int getTextWidth(String text) {
        return customFont.isToggled()
                ? (int) FontUtil.semiBold.getStringWidth(text)
                : mc.fontRendererObj.getStringWidth(text);
    }

    private int getLineHeight() {
        return (customFont.isToggled()
                ? (int) FontUtil.semiBold.getHeight()
                : mc.fontRendererObj.FONT_HEIGHT) + 3;
    }

    private void handleDragging(int left, int top, int right, int bottom) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }

        int mx = Mouse.getX() * mc.currentScreen.width / mc.displayWidth;
        int my = mc.currentScreen.height - Mouse.getY() * mc.currentScreen.height / mc.displayHeight - 1;
        boolean hovering = mx >= left && mx <= right && my >= top && my <= bottom;

        if (Mouse.isButtonDown(0)) {
            if (!dragging && hovering) {
                dragging = true;
                dragOffsetX = (int) posX.getInput() - mx;
                dragOffsetY = (int) posY.getInput() - my;
            }
            if (dragging) {
                posX.setValue(mx + dragOffsetX);
                posY.setValue(my + dragOffsetY);
            }
        } else {
            dragging = false;
        }
    }

    private static class StatLine {
        final String label;
        final String value;
        StatLine(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private static final class Chip {
        enum Type { LOGO, WORDMARK, SERVER, PING, FPS, SESSION_TIME,
                    IRL_TIME, IRL_DAY, KILLS, COORDS, DIRECTION,
                    BIOME, SPEED, MC_DAY }
        final Type type;
        final String text;
        final int order;
        final int seq;
        Chip(Type t, String text, int order, int seq) {
            this.type = t;
            this.text = text;
            this.order = order;
            this.seq = seq;
        }
    }

    private static final SimpleDateFormat IRL_DAY_FMT = new SimpleDateFormat("EEE, MMM d");

    private List<Chip> buildBarChips() {
        List<Chip> chips = new ArrayList<>();
        int seq = 0;
        if (showLogo.isToggled()) {
            chips.add(new Chip(Chip.Type.LOGO, "", (int) orderLogo.getInput(), seq++));
        }
        if (showWordmark.isToggled()) {
            chips.add(new Chip(Chip.Type.WORDMARK, "Crow", (int) orderWordmark.getInput(), seq++));
        }
        if (showServer.isToggled()) {
            String ip = mc.getCurrentServerData() != null
                    ? mc.getCurrentServerData().serverIP : "Local";
            chips.add(new Chip(Chip.Type.SERVER, ip, (int) orderServer.getInput(), seq++));
        }
        if (showPing.isToggled()) {
            chips.add(new Chip(Chip.Type.PING, getStablePing() + " ms",
                    (int) orderPing.getInput(), seq++));
        }
        if (showFPS.isToggled()) {
            chips.add(new Chip(Chip.Type.FPS, Minecraft.getDebugFPS() + " FPS",
                    (int) orderFPS.getInput(), seq++));
        }
        if (showPlaytime.isToggled()) {
            chips.add(new Chip(Chip.Type.SESSION_TIME, getSessionTimeString(),
                    (int) orderSession.getInput(), seq++));
        }
        if (showClock.isToggled()) {
            chips.add(new Chip(Chip.Type.IRL_TIME, TIME_FORMAT.format(new Date()),
                    (int) orderClock.getInput(), seq++));
        }
        if (showIrlDay.isToggled()) {
            chips.add(new Chip(Chip.Type.IRL_DAY, IRL_DAY_FMT.format(new Date()),
                    (int) orderIrlDay.getInput(), seq++));
        }
        if (showKills.isToggled()) {
            chips.add(new Chip(Chip.Type.KILLS, sessionKills + " kills",
                    (int) orderKills.getInput(), seq++));
        }
        if (showCoords.isToggled() && mc.thePlayer != null) {
            String c = String.format("%.0f, %.0f, %.0f",
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            chips.add(new Chip(Chip.Type.COORDS, c, (int) orderCoords.getInput(), seq++));
        }
        if (showDirection.isToggled() && mc.thePlayer != null) {
            float yaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw);
            int idx = Math.round(yaw / 45.0F) & 7;
            chips.add(new Chip(Chip.Type.DIRECTION, CARDINALS[idx],
                    (int) orderDirection.getInput(), seq++));
        }
        if (showBiome.isToggled() && mc.thePlayer != null && mc.theWorld != null) {
            try {
                net.minecraft.util.BlockPos pos = new net.minecraft.util.BlockPos(
                        mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                String biome = mc.theWorld.getBiomeGenForCoords(pos).biomeName;
                chips.add(new Chip(Chip.Type.BIOME, biome, (int) orderBiome.getInput(), seq++));
            } catch (Exception ignored) {}
        }
        if (showSpeed.isToggled() && mc.thePlayer != null) {
            double dx = mc.thePlayer.posX - mc.thePlayer.prevPosX;
            double dz = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
            double bps = Math.sqrt(dx * dx + dz * dz) * 20.0;
            chips.add(new Chip(Chip.Type.SPEED, String.format("%.1f bps", bps),
                    (int) orderSpeed.getInput(), seq++));
        }
        if (showDay.isToggled() && mc.theWorld != null) {
            long day = mc.theWorld.getWorldTime() / 24000L + 1;
            chips.add(new Chip(Chip.Type.MC_DAY, "Day " + day,
                    (int) orderMcDay.getInput(), seq++));
        }

        chips.sort((a, b) -> {
            int c = Integer.compare(a.order, b.order);
            return c != 0 ? c : Integer.compare(a.seq, b.seq);
        });
        return chips;
    }

    private void renderBar() {
        List<Chip> chips = buildBarChips();
        if (chips.isEmpty()) return;

        float scale = (float) barSize.getInput();

        int x = (int) posX.getInput();
        int y = (int) posY.getInput();

        final int padX     = Math.round(12 * scale);
        final int padY     = Math.round(6  * scale);
        final int chipGap  = Math.round(12 * scale);
        final int dividerH = Math.round(12 * scale);
        final int iconGap  = Math.round(6  * scale);
        final int iconH    = Math.round(14 * scale);
        final int logoIcon = Math.round(14 * scale);

        int textH = customFont.isToggled()
                ? FontUtil.semiBold.getHeight()
                : mc.fontRendererObj.FONT_HEIGHT;

        int scaledTextH = Math.round(textH * scale);
        int barH = padY * 2 + Math.max(scaledTextH, iconH);

        int[] widths = new int[chips.size()];
        int contentW = 0;
        for (int i = 0; i < chips.size(); i++) {
            int w = chipWidth(chips.get(i), iconGap, iconH, logoIcon, scale);
            widths[i] = w;
            contentW += w;
            if (i > 0) contentW += chipGap * 2 + 1;
        }
        int barW = padX * 2 + contentW;

        int boxL = x;
        int boxT = y;
        int boxR = x + barW;
        int boxB = y + barH;

        boolean useBlur = HUD.enableBlur != null && HUD.enableBlur.isToggled();
        boolean useGlow = HUD.enableGlow != null && HUD.enableGlow.isToggled();
        int accentRGB = GuiModule.getThemeColor(0) & 0x00FFFFFF;

        float corner = barH / 2.0F;
        if (useBlur) {
            GUIBlurUtil.drawBlurredBackground(
                    boxL, boxT, barW, barH,
                    (int) HUD.blurRadius.getInput(),
                    (int) corner, 0.6F);
            mc.entityRenderer.setupOverlayRendering();
        }

        RenderUtils.drawRoundedRectAA(boxL, boxT, boxR, boxB, corner, 0xCC0E0F12);

        boolean inChat = mc.currentScreen instanceof GuiChat;
        int mx = -1, my = -1;
        if (inChat) {
            mx = Mouse.getX() * mc.currentScreen.width / mc.displayWidth;
            my = mc.currentScreen.height - Mouse.getY() * mc.currentScreen.height / mc.displayHeight - 1;
        }

        lastChipBounds.clear();
        int[] chipStartX = new int[chips.size()];
        {
            int cursorX = boxL + padX;
            for (int i = 0; i < chips.size(); i++) {
                if (i > 0) cursorX += chipGap * 2 + 1;
                chipStartX[i] = cursorX;
                lastChipBounds.add(new ChipBounds(i, cursorX, cursorX + widths[i], chips.get(i).type));
                cursorX += widths[i];
            }
        }

        int dragVisualDx = 0;
        if (inChat) {
            dragVisualDx = handleChipDragOrBarDrag(chips, widths, chipStartX,
                                                    boxL, boxT, boxR, boxB,
                                                    padX, chipGap,
                                                    mx, my);
        } else {

            dragging = false;
            draggingChipIdx = -1;
            wasMouseDown = false;
        }

        int chipMidY = boxT + barH / 2;
        for (int i = 1; i < chips.size(); i++) {
            int dx0 = chipStartX[i] - chipGap - 1;
            int dy0 = chipMidY - dividerH / 2;
            Gui.drawRect(dx0, dy0, dx0 + 1, dy0 + dividerH, 0x40FFFFFF);
        }

        for (int i = 0; i < chips.size(); i++) {
            int chipX = chipStartX[i];
            if (i == draggingChipIdx) {
                chipX += dragVisualDx;
            }
            drawChip(chips.get(i), chipX, chipMidY, accentRGB, useGlow,
                     iconGap, iconH, logoIcon, scale,
                     i == draggingChipIdx);
        }
    }

    private int handleChipDragOrBarDrag(List<Chip> chips, int[] widths, int[] startX,
                                         int boxL, int boxT, int boxR, int boxB,
                                         int padX, int chipGap, int mx, int my) {
        boolean down = Mouse.isButtonDown(0);
        boolean overBar = mx >= boxL && mx <= boxR && my >= boxT && my <= boxB;
        boolean justPressed = down && !wasMouseDown;
        wasMouseDown = down;

        if (justPressed && overBar) {

            for (ChipBounds b : lastChipBounds) {
                if (mx >= b.leftX && mx <= b.rightX) {
                    draggingChipIdx = b.idx;
                    chipDragMouseAnchorX = mx;
                    chipDragChipAnchorX = b.leftX;
                    return 0;
                }
            }

            dragging = true;
            dragOffsetX = (int) posX.getInput() - mx;
            dragOffsetY = (int) posY.getInput() - my;
            return 0;
        }

        if (!down) {

            if (draggingChipIdx != -1) {
                commitChipDragDrop(chips, widths, startX, mx);
                draggingChipIdx = -1;
            }
            dragging = false;
            return 0;
        }

        if (draggingChipIdx != -1) {

            return mx - chipDragMouseAnchorX;
        }
        if (dragging) {

            posX.setValue(mx + dragOffsetX);
            posY.setValue(my + dragOffsetY);
        }
        return 0;
    }

    private void commitChipDragDrop(List<Chip> chips, int[] widths, int[] startX, int mx) {
        if (draggingChipIdx < 0 || draggingChipIdx >= chips.size()) return;

        int draggedCentreX = mx;
        int newIdx = chips.size() - 1;
        for (int i = 0; i < chips.size(); i++) {
            int slotCentre = startX[i] + widths[i] / 2;
            if (draggedCentreX < slotCentre) { newIdx = i; break; }
        }

        int oldIdx = draggingChipIdx;
        if (newIdx == oldIdx) return;

        List<Chip> reordered = new ArrayList<>(chips);
        Chip moved = reordered.remove(oldIdx);

        if (newIdx > oldIdx) newIdx--;
        reordered.add(newIdx, moved);

        int order = 1;
        for (Chip c : reordered) {
            setOrderForChip(c.type, order++);
        }
    }

    private void setOrderForChip(Chip.Type t, int order) {
        switch (t) {
            case LOGO:        orderLogo.setValue(order); break;
            case WORDMARK:    orderWordmark.setValue(order); break;
            case SERVER:      orderServer.setValue(order); break;
            case PING:        orderPing.setValue(order); break;
            case FPS:         orderFPS.setValue(order); break;
            case SESSION_TIME:orderSession.setValue(order); break;
            case IRL_TIME:    orderClock.setValue(order); break;
            case IRL_DAY:     orderIrlDay.setValue(order); break;
            case KILLS:       orderKills.setValue(order); break;
            case COORDS:      orderCoords.setValue(order); break;
            case DIRECTION:   orderDirection.setValue(order); break;
            case BIOME:       orderBiome.setValue(order); break;
            case SPEED:       orderSpeed.setValue(order); break;
            case MC_DAY:      orderMcDay.setValue(order); break;
        }
    }

    private int chipWidth(Chip chip, int iconGap, int chipIconSize, int logoIconSize, float scale) {
        int rawTextW = chip.text == null || chip.text.isEmpty()
                ? 0
                : (customFont.isToggled()
                        ? (int) FontUtil.semiBold.getStringWidth(chip.text)
                        : mc.fontRendererObj.getStringWidth(chip.text));
        int textW = Math.round(rawTextW * scale);
        boolean hasIcon = chipHasIcon(chip.type);
        int iconW = !hasIcon ? 0 : (chip.type == Chip.Type.LOGO ? logoIconSize : chipIconSize);
        int gap = (hasIcon && !chip.text.isEmpty()) ? iconGap : 0;
        return iconW + gap + textW;
    }

    private boolean chipHasIcon(Chip.Type t) {
        switch (t) {
            case LOGO:
            case SERVER:
            case PING:
            case FPS:
            case SESSION_TIME:
            case IRL_TIME:
            case IRL_DAY:
                return true;
            default:
                return false;
        }
    }

    private void drawChip(Chip chip, int x, int midY, int accentRGB, boolean useGlow,
                           int iconGap, int chipIconSize, int logoIconSize,
                           float scale, boolean isDragging) {
        int textAlpha = isDragging ? 0xCC : 0xFF;
        int textColor = (textAlpha << 24) | 0xF2F4F7;
        int iconColor = (textAlpha << 24) | 0xD7DAE0;
        int textH = customFont.isToggled()
                ? FontUtil.semiBold.getHeight()
                : mc.fontRendererObj.FONT_HEIGHT;
        int scaledTextH = Math.round(textH * scale);
        int iconH = (chip.type == Chip.Type.LOGO ? logoIconSize : chipIconSize);
        int textY = midY - scaledTextH / 2;
        int iconY = midY - iconH / 2;

        int cursorX = x;
        if (chipHasIcon(chip.type)) {
            int iconW = (chip.type == Chip.Type.LOGO ? logoIconSize : chipIconSize);
            drawChipIcon(chip.type, cursorX, iconY, iconW, iconH, iconColor, accentRGB);
            cursorX += iconW;
            if (chip.text != null && !chip.text.isEmpty()) cursorX += iconGap;
        }
        if (chip.text != null && !chip.text.isEmpty()) {

            GL11.glPushMatrix();
            GL11.glTranslatef(cursorX, textY, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            drawValueText(chip.text, 0, 0, textColor, accentRGB, useGlow, false);
            GL11.glPopMatrix();
        }
    }

    private void drawChipIcon(Chip.Type type, int x, int y, int w, int h,
                               int color, int accentRGB) {
        switch (type) {
            case LOGO:
                if (crow.client.main.Crow.mResourceLocation != null) {
                    drawIconTexture(crow.client.main.Crow.mResourceLocation, x, y, w, h, color);
                }
                break;
            case SERVER:
                drawIconTexture(ICON_SERVER, x, y, w, h, color);
                break;
            case PING:
                drawIconTexture(ICON_PING, x, y, w, h, color);
                break;
            case FPS:
                drawIconTexture(ICON_FPS, x, y, w, h, color);
                break;
            case SESSION_TIME:
            case IRL_TIME:
                drawIconTexture(ICON_CLOCK, x, y, w, h, color);
                break;
            case IRL_DAY: {

                RenderUtils.drawRoundedRectAA(x, y + 2, x + w, y + h, 1.5F, color);
                Gui.drawRect(x + 2,     y, x + 4,     y + 3, color);
                Gui.drawRect(x + w - 4, y, x + w - 2, y + 3, color);
                Gui.drawRect(x + 1,     y + 4, x + w - 1, y + 5, 0xCC0E0F12);
                break;
            }
            default: break;
        }
    }

    private void drawIconTexture(ResourceLocation resource, int x, int y, int w, int h, int tintColor) {
        if (resource == null) return;

        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            mc.getTextureManager().bindTexture(resource);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            float a = ((tintColor >>> 24) & 0xFF) / 255.0F;
            float r = ((tintColor >>> 16) & 0xFF) / 255.0F;
            float g = ((tintColor >>>  8) & 0xFF) / 255.0F;
            float b = ( tintColor         & 0xFF) / 255.0F;
            if (a == 0) a = 1.0F;
            GlStateManager.color(r, g, b, a);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, w, h, w, h);
        } catch (Throwable ignored) {

        } finally {

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (!prevBlend) {
                GL11.glDisable(GL11.GL_BLEND);
                GlStateManager.enableBlend();
                GlStateManager.disableBlend();
            }
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        }
    }
}
