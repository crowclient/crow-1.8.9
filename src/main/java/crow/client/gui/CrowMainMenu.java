package crow.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.opengl.GL11;

import crow.client.module.modules.client.GuiModule;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

public class CrowMainMenu extends GuiScreen implements GuiYesNoCallback {

    public static boolean redirecting = false;

    private static final ResourceLocation CROW_ICON       = RenderUtils.getResourcePath("/assets/crow/crow.png");
    private static final ResourceLocation MENU_BACKGROUND = RenderUtils.getResourcePath("/assets/crow/menubg.jpg");

    private static final int PARTICLE_COUNT  = 36;
    private static final int PANEL_WIDTH     = 296;
    private static final int PANEL_HEIGHT    = 260;
    private static final int BUTTON_HEIGHT   = 30;
    private static final int BUTTON_GAP      = 7;
    private static final int PANEL_TOP_OFFSET = -10;

    private static final float SPEED_PARALLAX     = 6.0F;
    private static final float SPEED_REVEAL       = 6.0F;
    private static final float SPEED_HOVER        = 14.0F;
    private static final float SPEED_MENU_SWITCH  = 12.0F;

    private static final long  BUTTON_STAGGER_MS  = 70L;

    private static final long  PANEL_DELAY_MS     = 80L;

    private long  openTime;

    private long  lastFrameMs;

    private float bgRevealAnim;

    private float panelRevealAnim;

    private float parallaxX, parallaxY;

    private float menuSwitchAnim;

    private final List<CrowMenuButton> crowButtons = new ArrayList<>();
    private final List<MenuParticle>   particles   = new ArrayList<>();
    private final Random               particleRandom = new Random();

    private int cachedPanelX, cachedPanelY;

    @Override
    public void initGui() {
        super.initGui();
        crowButtons.clear();
        particles.clear();

        cachedPanelX = width / 2 - PANEL_WIDTH / 2;
        cachedPanelY = height / 2 - PANEL_HEIGHT / 2 + PANEL_TOP_OFFSET;
        int panelX = cachedPanelX;
        int panelY = cachedPanelY;
        int buttonWidth = PANEL_WIDTH - 44;
        int buttonX = panelX + 22;

        int buttonsBlockH = BUTTON_HEIGHT * 4 + BUTTON_GAP * 3;
        int desiredTop = panelY + 92;
        int maxTop = panelY + PANEL_HEIGHT - 34 - buttonsBlockH;
        int buttonStartY = Math.min(desiredTop, maxTop);

        int row = buttonStartY;
        crowButtons.add(new CrowMenuButton(1, buttonX, row, buttonWidth, BUTTON_HEIGHT, "Singleplayer", 0));
        row += BUTTON_HEIGHT + BUTTON_GAP;
        crowButtons.add(new CrowMenuButton(2, buttonX, row, buttonWidth, BUTTON_HEIGHT, "Multiplayer", 1));
        row += BUTTON_HEIGHT + BUTTON_GAP;
        crowButtons.add(new CrowMenuButton(3, buttonX, row, buttonWidth, BUTTON_HEIGHT, "Options", 2));
        row += BUTTON_HEIGHT + BUTTON_GAP;
        int halfW = (buttonWidth - 8) / 2;
        crowButtons.add(new CrowMenuButton(4, buttonX, row, halfW, BUTTON_HEIGHT, "Language", 3));
        crowButtons.add(new CrowMenuButton(5, buttonX + halfW + 8, row, halfW, BUTTON_HEIGHT, "Quit", 3));

        for (int i = 0; i < PARTICLE_COUNT; i++) particles.add(new MenuParticle());

        openTime = System.currentTimeMillis();
        lastFrameMs = openTime;
        bgRevealAnim = 0.0F;
        panelRevealAnim = 0.0F;
        parallaxX = 0.0F;
        parallaxY = 0.0F;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        if (lastFrameMs == 0L) lastFrameMs = now;
        float dt = Math.min(0.1F, (now - lastFrameMs) / 1000.0F);
        lastFrameMs = now;

        float targetParX = (mouseX - width / 2.0F);
        float targetParY = (mouseY - height / 2.0F);
        parallaxX = expEase(parallaxX, targetParX, SPEED_PARALLAX, dt);
        parallaxY = expEase(parallaxY, targetParY, SPEED_PARALLAX, dt);

        long elapsed = now - openTime;
        float bgTarget = 1.0F;
        bgRevealAnim = expEase(bgRevealAnim, bgTarget, SPEED_REVEAL, dt);
        float panelTarget = elapsed >= PANEL_DELAY_MS ? 1.0F : 0.0F;
        panelRevealAnim = expEase(panelRevealAnim, panelTarget, SPEED_REVEAL, dt);
        float panelEased = easeOutCubic(panelRevealAnim);

        drawBackground(mouseX, mouseY, partialTicks, dt);

        if (panelEased > 0.01F) {
            int panelX = cachedPanelX;
            int panelY = cachedPanelY;

            float panelDriftX = -parallaxX * 0.012F;
            float panelDriftY = -parallaxY * 0.012F;
            float slideOffset = (1.0F - panelEased) * 24.0F;

            GL11.glPushMatrix();
            GL11.glTranslatef(panelDriftX, panelDriftY + slideOffset, 0.0F);

            int panelAlpha = clamp255((int) (panelEased * 255));
            drawPanel(panelX, panelY, panelAlpha);
            drawBranding(panelX, panelY, PANEL_WIDTH, panelAlpha);

            for (CrowMenuButton button : crowButtons) {
                button.update(now, dt, mouseX - (int) (panelDriftX + slideOffset),
                                       mouseY - (int) (panelDriftY + slideOffset));
                button.draw(panelEased);
            }

            String loggedIn = "Logged in as " + mc.getSession().getUsername();
            drawSmall(loggedIn, panelX + 14, panelY + PANEL_HEIGHT - 18,
                    withAlpha(0xFF8899AA, panelAlpha));

            GL11.glPopMatrix();
        }

        drawMenuSwitch(mouseX, mouseY, dt);

        String version = EnumChatFormatting.GRAY + "Build 1.2.8";
        drawSmall(version, width - getSmallWidth(version) - 12, height - 18, 0xA0FFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawBackground(int mouseX, int mouseY, float partialTicks, float dt) {

        drawGradientRect(0, 0, width, height, 0xFF090B11, 0xFF121722);

        if (MENU_BACKGROUND != null) {
            drawParallaxBackground(0.025F);
        } else {
            drawFallbackAtmosphere();
        }

        drawAtmosphereBlobs(0.05F);

        drawParticles(partialTicks, 0.09F);

        int flowAlpha = (int) (18 * bgRevealAnim);
        if (flowAlpha > 0) {
            RenderUtils.drawFlowingGradientRect(0, 0, width, height,
                    flowAlpha, (int) (System.currentTimeMillis() / 10L));
        }

        int scrimAlpha = (int) (0x66 * bgRevealAnim);
        GuiScreen.drawRect(0, 0, width, height, (scrimAlpha << 24) | 0x080A10);
    }

    private void drawParallaxBackground(float scalar) {
        float driftX = -parallaxX * scalar;
        float driftY = -parallaxY * scalar;

        int drawX = -64 + (int) driftX;
        int drawY = -48 + (int) driftY;
        int drawW = width  + 128;
        int drawH = height + 96;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.getTextureManager().bindTexture(MENU_BACKGROUND);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        float fadeIn = bgRevealAnim;
        drawTexturedLayer(drawX - 16, drawY - 10, drawW + 32, drawH + 20, 0.14F * fadeIn);
        drawTexturedLayer(drawX + 16, drawY + 10, drawW + 32, drawH + 20, 0.14F * fadeIn);
        drawTexturedLayer(drawX,      drawY - 18, drawW,      drawH + 36, 0.12F * fadeIn);
        drawTexturedLayer(drawX,      drawY + 18, drawW,      drawH + 36, 0.12F * fadeIn);
        drawTexturedLayer(drawX - 24, drawY,      drawW + 48, drawH,      0.10F * fadeIn);
        drawTexturedLayer(drawX + 24, drawY,      drawW + 48, drawH,      0.10F * fadeIn);
        drawTexturedLayer(drawX,      drawY,      drawW,      drawH,      0.30F * fadeIn);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int darkenAlpha = (int) (0x7A * fadeIn);
        GuiScreen.drawRect(0, 0, width, height, (darkenAlpha << 24) | 0x0A0B11);
    }

    private void drawAtmosphereBlobs(float scalar) {
        float dx = -parallaxX * scalar;
        float dy = -parallaxY * scalar;
        float fade = bgRevealAnim;
        int alpha = (int) (0x14 * fade);
        if (alpha <= 0) return;

        int color = (alpha << 24);
        RenderUtils.drawRoundedRectAA(-40 + dx, -30 + dy,
                width * 0.40F + dx, height * 0.36F + dy, 120, color);
        RenderUtils.drawRoundedRectAA(width * 0.62F + dx, -30 + dy,
                width + 50 + dx, height * 0.34F + dy, 140, color);
        RenderUtils.drawRoundedRectAA(width * 0.12F + dx, height * 0.70F + dy,
                width * 0.42F + dx, height + 60 + dy, 120, color);
        RenderUtils.drawRoundedRectAA(width * 0.68F + dx, height * 0.62F + dy,
                width + 80 + dx, height + 70 + dy, 160, color);
    }

    private void drawTexturedLayer(int x, int y, int w, int h, float alpha) {
        if (alpha <= 0.0F) return;
        GlStateManager.color(1.0F, 1.0F, 1.0F, Math.min(1.0F, alpha));
        drawModalRectWithCustomSizedTexture(x, y, 0, 0, w, h, w, h);
    }

    private void drawFallbackAtmosphere() {

        float dx = -parallaxX * 0.04F;
        float dy = -parallaxY * 0.04F;
        RenderUtils.drawRoundedRectAA(-60 + dx, 42 + dy,
                width * 0.42F + dx, height * 0.42F + dy, 120, 0x182B3445);
        RenderUtils.drawRoundedRectAA(width * 0.58F + dx, -30 + dy,
                width + 50 + dx, height * 0.38F + dy, 140, 0x162B2238);
        RenderUtils.drawRoundedRectAA(width * 0.08F + dx, height * 0.72F + dy,
                width * 0.42F + dx, height + 70 + dy, 120, 0x14233530);
        RenderUtils.drawRoundedRectAA(width * 0.70F + dx, height * 0.60F + dy,
                width + 90 + dx, height + 80 + dy, 150, 0x142E2638);
    }

    private void drawParticles(float partialTicks, float parallaxScalar) {
        float dx = -parallaxX * parallaxScalar;
        float dy = -parallaxY * parallaxScalar;
        for (MenuParticle particle : particles) {
            particle.update(partialTicks);
            int size = (int) particle.size;
            int themeRGB = GuiModule.getThemeColor((int) particle.x + (int) particle.y) & 0x00FFFFFF;
            int alphaByte = clamp255((int) (Math.min(particle.alpha, 60) * bgRevealAnim));
            int color = (alphaByte << 24) | themeRGB;
            RenderUtils.drawRoundedRectAA(particle.x + dx, particle.y + dy,
                    particle.x + dx + size, particle.y + dy + size,
                    size / 2.0F, color);
        }
    }

    private void drawPanel(int panelX, int panelY, int alpha) {

        int shadowAlpha = clamp255((int) (alpha * 0.14F));
        RenderUtils.drawRoundedRectAA(panelX - 8, panelY + 8,
                panelX + PANEL_WIDTH + 8, panelY + PANEL_HEIGHT + 12,
                22, (shadowAlpha << 24));

        int outerColor = (alpha << 24) | 0x1A1F28;
        int innerColor = (clamp255((int) (alpha * 0.97F)) << 24) | 0x242A34;
        RenderUtils.drawRoundedRectAA(panelX, panelY,
                panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 22, outerColor);
        RenderUtils.drawRoundedRectAA(panelX + 1, panelY + 1,
                panelX + PANEL_WIDTH - 1, panelY + PANEL_HEIGHT - 1, 21, innerColor);

        int sepAlpha = clamp255((int) (alpha * 0.55F));
        RenderUtils.drawFlowingGradientRoundedRect(panelX + 16, panelY + 77,
                panelX + PANEL_WIDTH - 16, panelY + 80, 1.5F, sepAlpha, 0);
    }

    private void drawBranding(int panelX, int panelY, int panelWidth, int alpha) {
        int iconSize = 30;
        int approxFontH = 20;
        int gap = 7;
        int titleWidth = getBoldWidth("Crow");
        int rowWidth = iconSize + gap + titleWidth;
        int rowX = panelX + (panelWidth - rowWidth) / 2;
        int rowY = panelY + 18;

        if (CROW_ICON != null) {
            float a = alpha / 255.0F;
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, a);
            mc.getTextureManager().bindTexture(CROW_ICON);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            drawModalRectWithCustomSizedTexture(rowX, rowY, 0, 0, iconSize, iconSize, iconSize, iconSize);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int titleColor = (alpha << 24) | (GuiModule.getThemeColor(0) & 0x00FFFFFF);
        drawBold("Crow", rowX + iconSize + gap, rowY + (iconSize - approxFontH) / 2, titleColor);

        String subtitle = "A cleaner way back into Minecraft.";
        int subtitleWidth = getSemiBoldWidth(subtitle);
        drawSemiBold(subtitle, panelX + (panelWidth - subtitleWidth) / 2.0F, panelY + 58,
                withAlpha(0xFFB6BDC9, alpha));
    }

    private void drawMenuSwitch(int mouseX, int mouseY, float dt) {
        int x = width - 104;
        int y = 10;
        int w = 94;
        int h = 22;
        boolean hovered = isMenuSwitchHovered(mouseX, mouseY);
        menuSwitchAnim = expEase(menuSwitchAnim, hovered ? 1.0F : 0.0F, SPEED_MENU_SWITCH, dt);

        int themeColor = GuiModule.getThemeColor(0);
        int hoverFill = ((int) (0x30 * menuSwitchAnim) << 24) | (themeColor & 0x00FFFFFF);
        int fill = blendColor(0xCC222833, 0xE0323946, menuSwitchAnim);
        int border = blendColor(0x14000000, (0x40 << 24) | (themeColor & 0x00FFFFFF), menuSwitchAnim);
        int text = blendColor(0xFFD6DCE7, 0xFFFFFFFF, menuSwitchAnim);

        RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, 9, fill);
        if (menuSwitchAnim > 0.01F) {
            RenderUtils.drawRoundedRectAA(x, y, x + w, y + h, 9, hoverFill);
        }
        RenderUtils.drawRoundedOutline(x, y, x + w, y + h, 9, 1.0F, border);
        drawSmall("Vanilla Menu", x + (w - getSmallWidth("Vanilla Menu")) / 2.0F, y + 7, text);
    }

    private boolean isMenuSwitchHovered(int mouseX, int mouseY) {
        return mouseX >= width - 104 && mouseX <= width - 10 && mouseY >= 10 && mouseY <= 32;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (isMenuSwitchHovered(mouseX, mouseY)) {
            GuiModule.setCustomMainMenu(false);
            CrowMainMenu.redirecting = true;
            mc.displayGuiScreen(new GuiMainMenu());
            CrowMainMenu.redirecting = false;
            return;
        }
        for (CrowMenuButton button : crowButtons) {
            if (button.isMouseOver(mouseX, mouseY)) {
                actionPerformed(button);
                return;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 1: mc.displayGuiScreen(new GuiSelectWorld(this)); break;
            case 2: mc.displayGuiScreen(new GuiMultiplayer(this)); break;
            case 3: mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)); break;
            case 4: mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager())); break;
            case 5: mc.displayGuiScreen(new GuiYesNo(this, "Quit game?", "Close Crow and return to desktop?", 0)); break;
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (result) mc.shutdown();
        else mc.displayGuiScreen(this);
    }

    private static float expEase(float current, float target, float speed, float dt) {
        if (dt <= 0.0F) return current;
        float k = 1.0F - (float) Math.exp(-speed * dt);
        return current + (target - current) * k;
    }

    private static float easeOutCubic(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    private int withAlpha(int rgb, int alpha) {
        int origA = (rgb >> 24) & 0xFF;
        int newA = clamp255((origA * alpha) / 255);
        return (newA << 24) | (rgb & 0x00FFFFFF);
    }

    private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private int blendColor(int from, int to, float progress) {
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        int a1 = (from >> 24) & 0xFF, r1 = (from >> 16) & 0xFF, g1 = (from >> 8) & 0xFF, b1 = from & 0xFF;
        int a2 = (to   >> 24) & 0xFF, r2 = (to   >> 16) & 0xFF, g2 = (to   >> 8) & 0xFF, b2 = to   & 0xFF;
        int a = (int) (a1 + (a2 - a1) * progress);
        int r = (int) (r1 + (r2 - r1) * progress);
        int g = (int) (g1 + (g2 - g1) * progress);
        int b = (int) (b1 + (b2 - b1) * progress);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawBold(String text, float x, float y, int color) {
        if (FontUtil.bold != null) FontUtil.bold.drawSmoothString(text, x, y, color);
        else mc.fontRendererObj.drawString(text, (int) x, (int) y, color, false);
    }
    private void drawSemiBold(String text, float x, float y, int color) {
        if (FontUtil.semiBold != null) FontUtil.semiBold.drawSmoothString(text, x, y, color);
        else mc.fontRendererObj.drawString(text, (int) x, (int) y, color, false);
    }
    private void drawSmall(String text, float x, float y, int color) {
        if (FontUtil.small != null) FontUtil.small.drawSmoothString(text, x, y, color);
        else mc.fontRendererObj.drawString(text, (int) x, (int) y, color, false);
    }
    private int getBoldWidth(String s) {
        return FontUtil.bold != null ? (int) FontUtil.bold.getStringWidth(s) : mc.fontRendererObj.getStringWidth(s);
    }
    private int getSemiBoldWidth(String s) {
        return FontUtil.semiBold != null ? (int) FontUtil.semiBold.getStringWidth(s) : mc.fontRendererObj.getStringWidth(s);
    }
    private int getSmallWidth(String s) {
        return FontUtil.small != null ? (int) FontUtil.small.getStringWidth(s) : mc.fontRendererObj.getStringWidth(s);
    }

    private class CrowMenuButton extends GuiButton {
        private final int rowIndex;
        private float hoverAnim;

        private float revealAnim;
        private float pressAnim;
        private boolean hovered;

        private CrowMenuButton(int buttonId, int x, int y, int widthIn, int heightIn,
                               String buttonText, int rowIndex) {
            super(buttonId, x, y, widthIn, heightIn, buttonText);
            this.rowIndex = rowIndex;
        }

        private void update(long now, float dt, int mouseX, int mouseY) {
            hovered = isMouseOver(mouseX, mouseY);
            hoverAnim = expEase(hoverAnim, hovered ? 1.0F : 0.0F, SPEED_HOVER, dt);

            long sinceOpen = now - openTime;
            long delay = PANEL_DELAY_MS + 240L + rowIndex * BUTTON_STAGGER_MS;
            float target = sinceOpen >= delay ? 1.0F : 0.0F;
            revealAnim = expEase(revealAnim, target, SPEED_REVEAL + 4.0F, dt);
        }

        private void draw(float panelEnvelope) {
            float r = easeOutCubic(revealAnim) * panelEnvelope;
            if (r < 0.005F) return;

            int alphaByte = clamp255((int) (r * 255));
            float lift = hoverAnim * 1.0F;

            float entryOffset = (1.0F - easeOutCubic(revealAnim)) * 12.0F;
            int top = (int) (yPosition - lift + entryOffset);
            int bottom = (int) (yPosition + height - lift + entryOffset);
            float radius = 12;

            int themeColor = GuiModule.getThemeColor(xPosition + width / 2);
            int baseFill   = withAlpha(0xFF272D38, alphaByte);
            int hoverFill  = withAlpha(blendColor(0xFF272D38, 0xFF313845, hoverAnim), alphaByte);
            int finalFill  = blendColor(baseFill, hoverFill, hoverAnim);
            RenderUtils.drawRoundedRectAA(xPosition, top, xPosition + width, bottom, radius, finalFill);

            if (hoverAnim > 0.01F) {
                int tintAlpha = clamp255((int) (0x18 * hoverAnim * r));
                int tint = (tintAlpha << 24) | (themeColor & 0x00FFFFFF);
                RenderUtils.drawRoundedRectAA(xPosition, top, xPosition + width, bottom, radius, tint);
            }

            int borderColor = withAlpha(
                    blendColor(0x16000000, (0x50 << 24) | (themeColor & 0x00FFFFFF), hoverAnim),
                    alphaByte);
            RenderUtils.drawRoundedOutline(xPosition, top, xPosition + width, bottom, radius, 1.0F, borderColor);

            int accentAlpha = clamp255((int) ((60 + 100 * hoverAnim) * r));
            RenderUtils.drawFlowingGradientRoundedRect(xPosition, bottom - 3,
                    xPosition + width, bottom, radius, accentAlpha, xPosition);

            int textColor = withAlpha(blendColor(0xFFD6DCE7, 0xFFFFFFFF, hoverAnim), alphaByte);
            int textWidth = getSemiBoldWidth(displayString);
            drawSemiBold(displayString, xPosition + (width - textWidth) / 2.0F,
                    yPosition + 11 - lift + entryOffset, textColor);
        }

        private boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= xPosition && mouseX <= xPosition + width
                && mouseY >= yPosition && mouseY <= yPosition + height;
        }
    }

    private class MenuParticle {
        private float x, y;
        private float speedX, speedY;
        private float size;
        private int   alpha;

        private MenuParticle() { reset(true); }

        private void update(float partialTicks) {
            x += speedX * partialTicks;
            y += speedY * partialTicks;
            if (y > height + 14 || x < -18 || x > width + 18) reset(false);
        }

        private void reset(boolean randomY) {
            x = particleRandom.nextInt(Math.max(1, width + 40)) - 20;
            y = randomY ? particleRandom.nextInt(Math.max(1, height + 40)) - 20
                        : -12 - particleRandom.nextInt(36);
            speedX = -0.06F + particleRandom.nextFloat() * 0.12F;
            speedY = 0.12F + particleRandom.nextFloat() * 0.30F;
            size = 2.0F + particleRandom.nextFloat() * 2.5F;
            alpha = 30 + particleRandom.nextInt(40);
        }
    }
}
