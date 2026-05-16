package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.Render2DEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.HUD;
import crow.client.module.setting.impl.RGBSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ScoreboardMod extends Module {
    public static TickSetting customFont;
    public static TickSetting background;
    public static TickSetting outline;
    public static SliderSetting posX;
    public static SliderSetting posY;
    public static SliderSetting bgOpacity;
    public static RGBSetting bgColor;
    public static RGBSetting outlineColor;
    public static SliderSetting scale;

    private static ScoreboardMod instance;

    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean initializedPosition;
    private int lastBoardWidth;
    private static final int DEFAULT_POS_X = 6;
    private static final int DEFAULT_POS_Y = 40;

    public ScoreboardMod() {
        super("Scoreboard", ModuleCategory.render);
        instance = this;
        withDescription("Replaces the vanilla scoreboard with a draggable Crow-styled version.");
        withEnabled(false);

        this.registerSetting(customFont = new TickSetting("Custom font", true));
        this.registerSetting(background = new TickSetting("Background", true));
        this.registerSetting(outline = new TickSetting("Outline", true));
        this.registerSetting(posX = new SliderSetting("X", DEFAULT_POS_X, 0, 4000, 1));
        this.registerSetting(posY = new SliderSetting("Y", DEFAULT_POS_Y, 0, 2000, 1));
        this.registerSetting(bgOpacity = new SliderSetting("BG opacity", 162, 0, 255, 1));
        this.registerSetting(bgColor = new RGBSetting("BG color", 18, 20, 26));
        this.registerSetting(outlineColor = new RGBSetting("Outline color", 255, 255, 255));
        this.registerSetting(scale = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.05));
    }

    public static boolean shouldReplaceVanilla() {
        return instance != null && instance.isEnabled() && Utils.Player.isPlayerInGame() && instance.getActiveObjective() != null;
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }
        if (!shouldReplaceVanilla()) {
            dragging = false;
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();
        ScoreObjective objective = getActiveObjective();
        if (objective == null) {
            return;
        }

        float s = (float) scale.getInput();
        RenderedBoard board = buildBoard(objective);
        int scaledWidth = Math.round(board.width * s);
        int scaledHeight = Math.round(board.height * s);

        if (!initializedPosition) {
            if ((int) posX.getInput() == DEFAULT_POS_X && (int) posY.getInput() == DEFAULT_POS_Y) {
                posX.setValue(screenW - scaledWidth - 8);
                posY.setValue(28);
            }
            initializedPosition = true;
            lastBoardWidth = scaledWidth;
        }

        if (lastBoardWidth != 0 && scaledWidth != lastBoardWidth) {
            int currentX = (int) posX.getInput();
            boolean onRightSide = (currentX + lastBoardWidth / 2) > (screenW / 2);
            if (onRightSide) {
                int rightEdge = currentX + lastBoardWidth;
                posX.setValue(rightEdge - scaledWidth);
            }

        }
        lastBoardWidth = scaledWidth;

        int x = clampX((int) posX.getInput(), scaledWidth, screenW);
        int y = clampY((int) posY.getInput(), scaledHeight, screenH);
        posX.setValue(x);
        posY.setValue(y);
        handleDragging(x, y, scaledWidth, scaledHeight, screenW, screenH);
        x = clampX((int) posX.getInput(), scaledWidth, screenW);
        y = clampY((int) posY.getInput(), scaledHeight, screenH);
        posX.setValue(x);
        posY.setValue(y);

        int bg = (((int) bgOpacity.getInput()) << 24)
                | (bgColor.getRed() << 16)
                | (bgColor.getGreen() << 8)
                | bgColor.getBlue();
        int border = 0xFF000000 | (outlineColor.getRed() << 16) | (outlineColor.getGreen() << 8) | outlineColor.getBlue();

        if (HUD.enableBlur != null && HUD.enableBlur.isToggled()) {
            GUIBlurUtil.drawBlurredBackground(x, y, scaledWidth, scaledHeight,
                    (int) HUD.blurRadius.getInput(), 8, 0.6f);
            mc.entityRenderer.setupOverlayRendering();
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(s, s, 1);
        GlStateManager.translate(-x, -y, 0);

        if (background.isToggled()) {
            RenderUtils.drawRoundedRectAA(x, y, x + board.width, y + board.height, 8, bg);
        }
        if (outline.isToggled()) {
            RenderUtils.drawRoundedOutline(x, y, x + board.width, y + board.height, 8, 1.0F, border);
        }

        drawText(board.title, x + board.width - 6 - getTextWidth(board.title), y + 6, 0xFFFFFFFF);

        int lineY = y + 21;
        lineY += 5;

        for (BoardLine line : board.lines) {
            drawText(line.left, x + 6, lineY, 0xFFFFFFFF);
            int rightWidth = getTextWidth(line.right);
            drawText(line.right, x + board.width - 6 - rightWidth, lineY, 0xFFFFFFFF);
            lineY += board.lineHeight;
        }

        GlStateManager.popMatrix();
    }

    private void handleDragging(int x, int y, int width, int height, int screenW, int screenH) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            dragging = false;
            return;
        }

        int mx = Mouse.getX() * screenW / mc.displayWidth;
        int my = screenH - Mouse.getY() * screenH / mc.displayHeight - 1;
        boolean hovering = mx >= x && mx <= x + width && my >= y && my <= y + height;

        if (Mouse.isButtonDown(0)) {
            if (!dragging && hovering) {
                dragging = true;
                dragOffsetX = x - mx;
                dragOffsetY = y - my;
            }
            if (dragging) {
                posX.setValue(clampX(mx + dragOffsetX, width, screenW));
                posY.setValue(clampY(my + dragOffsetY, height, screenH));
                if (Crow.clientConfig != null) {
                    Crow.clientConfig.updateScoreboardPosition((int) posX.getInput(), (int) posY.getInput());
                }
            }
        } else {
            dragging = false;
        }
    }

    private RenderedBoard buildBoard(ScoreObjective objective) {
        List<BoardLine> lines = new ArrayList<>();
        Collection<Score> sorted = objective.getScoreboard().getSortedScores(objective);
        List<Score> filtered = new ArrayList<>();
        for (Score score : sorted) {
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                filtered.add(score);
            }
        }
        if (filtered.size() > 15) {
            filtered = filtered.subList(filtered.size() - 15, filtered.size());
        }

        Collections.reverse(filtered);

        int lineHeight = getLineHeight();
        String title = objective.getDisplayName();
        int width = getTextWidth(title) + 12;
        for (Score score : filtered) {
            ScorePlayerTeam team = objective.getScoreboard().getPlayersTeam(score.getPlayerName());
            String name = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            String points = String.valueOf(score.getScorePoints());
            lines.add(new BoardLine(name, points));
            width = Math.max(width, getTextWidth(name) + getTextWidth(points) + 22);
        }

        int height = 28 + lines.size() * lineHeight + 6;
        return new RenderedBoard(title, lines, width, height, lineHeight);
    }

    private ScoreObjective getActiveObjective() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(mc.thePlayer.getName());
            if (team != null) {
                int colorIndex = team.getChatFormat().getColorIndex();
                if (colorIndex >= 0) {
                    objective = scoreboard.getObjectiveInDisplaySlot(3 + colorIndex);
                }
            }
        }
        return objective;
    }

    private int clampX(int value, int width, int screenW) {
        return Math.max(4, Math.min(screenW - width - 4, value));
    }

    private int clampY(int value, int height, int screenH) {
        return Math.max(4, Math.min(screenH - height - 4, value));
    }

    private boolean requiresVanillaFont(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\u00A7' && i + 1 < text.length()) {
                i++;
                continue;
            }

            if (c > 0xFF) {
                return true;
            }
        }
        return false;
    }

    private void drawText(String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        drawTextSegment(text, x, y, color);
    }

    private void drawTextSegment(String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        if (customFont.isToggled() && !requiresVanillaFont(text)) {
            FontUtil.semiBold.drawSmoothString(text, x, y, color);
        } else {
            mc.fontRendererObj.drawString(text, x, y, color, false);
        }
    }

    private int getTextWidth(String text) {
        return (customFont.isToggled() && !requiresVanillaFont(text))
                ? (int) FontUtil.semiBold.getStringWidth(text)
                : mc.fontRendererObj.getStringWidth(text);
    }

    private int getLineHeight() {
        return (customFont.isToggled() ? FontUtil.semiBold.getHeight() : mc.fontRendererObj.FONT_HEIGHT) + 2;
    }

    private static class BoardLine {
        private final String left;
        private final String right;

        private BoardLine(String left, String right) {
            this.left = left;
            this.right = right;
        }
    }

    private static class RenderedBoard {
        private final String title;
        private final List<BoardLine> lines;
        private final int width;
        private final int height;
        private final int lineHeight;

        private RenderedBoard(String title, List<BoardLine> lines, int width, int height, int lineHeight) {
            this.title = title;
            this.lines = lines;
            this.width = width;
            this.height = height;
            this.lineHeight = lineHeight;
        }
    }
}
