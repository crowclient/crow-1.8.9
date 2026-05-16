package crow.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.Render2DEvent;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.module.modules.HUD;
import crow.client.utils.CoolDown;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Keyboard;

public class Bridger extends Module {

    public static TickSetting autoShift;
    public static TickSetting blocksOnly;
    public static TickSetting shiftOnJump;
    public static TickSetting requireShiftHold;
    public static TickSetting lookDown;
    public static TickSetting showBlockCount;
    public static TickSetting autoSwitch;
    public static DoubleSliderSetting pitchRange;
    public static DoubleSliderSetting shiftTime;
    public static ComboSetting countMode;
    public static SliderSetting backwardOnly;

    public enum CountMode { HOTBAR, TOTAL }

    private boolean shouldBridge;
    private boolean isShifting;
    private final CoolDown shiftTimer = new CoolDown(0);

    private int autoSwitchedFromSlot = -1;

    private float hudFadeRaw;
    private float hudFadeAlpha;
    private long  hudLastFrameMs;

    private float hudDisplayedCount;

    public Bridger() {
        super("Bridger", ModuleCategory.world);
        this.registerSetting(autoShift = new TickSetting("Auto shift", true));
        this.registerSetting(requireShiftHold = new TickSetting("Hold shift", false));
        this.registerSetting(shiftOnJump = new TickSetting("Shift jumps", false));
        this.registerSetting(shiftTime = new DoubleSliderSetting("Shift time", 140, 200, 0, 280, 5));
        this.registerSetting(blocksOnly = new TickSetting("Blocks only", true));
        this.registerSetting(autoSwitch = new TickSetting("Auto switch", true));
        this.registerSetting(lookDown = new TickSetting("Look down", true));
        this.registerSetting(pitchRange = new DoubleSliderSetting("Pitch range", 70, 85, 0, 90, 1));
        this.registerSetting(backwardOnly = new SliderSetting("Direction", 1, 1, 2, 1));
        this.registerSetting(showBlockCount = new TickSetting("Show count", true));
        this.registerSetting(countMode = new ComboSetting("Count mode", CountMode.TOTAL));
    }

    @Override
    public void onDisable() {
        if (isShifting) setShift(false);
        shouldBridge = false;
        isShifting = false;
        autoSwitchedFromSlot = -1;
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Client.currentScreenMinecraft() || !Utils.Player.isPlayerInGame()) return;

        if (!autoShift.isToggled()) return;

        boolean shiftTimeActive = shiftTime.getInputMax() > 0;

        if (lookDown.isToggled()) {
            float pitch = mc.thePlayer.rotationPitch;
            if (pitch < pitchRange.getInputMin() || pitch > pitchRange.getInputMax()) {
                shouldBridge = false;
                if (Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) setShift(true);
                return;
            }
        }

        if (requireShiftHold.isToggled() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            shouldBridge = false;
            return;
        }

        if (autoSwitch.isToggled()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock && held.stackSize > 0;
            if (!holdingBlock) {
                int bestSlot = -1;
                int bestCount = 0;
                for (int slot = 0; slot <= 8; slot++) {
                    int count = Utils.Player.getBlockAmountInCurrentStack(slot);
                    if (count > bestCount) {
                        bestCount = count;
                        bestSlot = slot;
                    }
                }
                if (bestSlot != -1) {
                    autoSwitchedFromSlot = mc.thePlayer.inventory.currentItem;
                    Utils.Player.hotkeyToSlot(bestSlot);
                }
            }
        }

        if (blocksOnly.isToggled()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock)) {
                if (isShifting) { isShifting = false; setShift(false); }
                return;
            }
        }

        if ((int) backwardOnly.getInput() == 1 && mc.thePlayer.movementInput.moveForward >= 0) {
            shouldBridge = false;
            return;
        }

        if (mc.thePlayer.onGround) {
            if (Utils.Player.playerOverAir()) {
                if (shiftTimeActive) {
                    shiftTimer.setCooldown(Utils.Java.randomInt(shiftTime.getInputMin(), shiftTime.getInputMax() + 0.1));
                    shiftTimer.start();
                }
                isShifting = true;
                setShift(true);
                shouldBridge = true;
            } else if (mc.thePlayer.isSneaking() && !requireShiftHold.isToggled()
                    && (!shiftTimeActive || shiftTimer.hasFinished())) {
                isShifting = false;
                setShift(false);
                shouldBridge = true;
            } else if (mc.thePlayer.isSneaking() && requireShiftHold.isToggled()
                    && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
                isShifting = false;
                shouldBridge = false;
                setShift(false);
            } else if (mc.thePlayer.isSneaking() && requireShiftHold.isToggled()
                    && (!shiftTimeActive || shiftTimer.hasFinished())) {
                isShifting = false;
                setShift(false);
                shouldBridge = true;
            }
        } else if (shouldBridge && mc.thePlayer.capabilities.isFlying) {
            setShift(false);
            shouldBridge = false;
        } else if (shouldBridge && Utils.Player.playerOverAir() && shiftOnJump.isToggled()) {
            isShifting = true;
            setShift(true);
        } else {
            isShifting = false;
            setShift(false);
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!showBlockCount.isToggled() || !Utils.Player.isPlayerInGame()) return;
        if (mc.currentScreen != null) return;

        long now = System.currentTimeMillis();
        if (hudLastFrameMs == 0L) hudLastFrameMs = now;
        float dt = Math.min(0.1F, (now - hudLastFrameMs) / 1000.0F);
        hudLastFrameMs = now;

        boolean visible = shouldBridge;
        float fadeTarget = visible ? 1.0F : 0.0F;

        float fadeSpeed = fadeTarget > hudFadeRaw ? 8.0F : 5.0F;
        float k = 1.0F - (float) Math.exp(-fadeSpeed * dt);
        hudFadeRaw += (fadeTarget - hudFadeRaw) * k;
        if (hudFadeRaw < 0.0F) hudFadeRaw = 0.0F;
        if (hudFadeRaw > 1.0F) hudFadeRaw = 1.0F;

        hudFadeAlpha = 1.0F - (float) Math.pow(1.0F - hudFadeRaw, 3.0F);

        // Earlier cut-off (0.01 → 0.03). FontUtil's drawSmoothString forces
        // alpha to 0xFF when the alpha byte's top 6 bits are zero (alpha <
        // ~0.016), which made text snap to full opacity at the tail of the
        // fade. Cutting at 0.03 keeps us above that clamp; the RGB pre-
        // multiply below carries the fade-out smoothly to invisibility.
        if (hudFadeAlpha < 0.03F) return;

        int totalBlocks = getBlockCount();
        if (hudDisplayedCount <= 0.0F) hudDisplayedCount = totalBlocks;

        if (totalBlocks > hudDisplayedCount) {
            hudDisplayedCount = totalBlocks;
        } else {
            float countK = 1.0F - (float) Math.exp(-12.0F * dt);
            hudDisplayedCount += (totalBlocks - hudDisplayedCount) * countK;
            if (Math.abs(hudDisplayedCount - totalBlocks) < 0.5F) hudDisplayedCount = totalBlocks;
        }
        int displayedInt = Math.max(0, (int) Math.round(hudDisplayedCount));
        if (displayedInt <= 0 && !visible) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth() / 2;
        int cy = sr.getScaledHeight() / 2;

        boolean hasFont = FontUtil.hasLoaded() && FontUtil.semiBold != null;

        String countStr = String.valueOf(displayedInt);
        float countW = hasFont
                ? (float) FontUtil.semiBold.getStringWidth(countStr)
                : mc.fontRendererObj.getStringWidth(countStr);
        float countH = hasFont
                ? FontUtil.semiBold.getHeight()
                : mc.fontRendererObj.FONT_HEIGHT;

        float iconSize = 14.0F;
        float padX = 5.0F;
        float padY = 3.0F;
        float gap = 4.0F;

        float contentH = Math.max(iconSize, countH);
        float totalW = padX + iconSize + gap + countW + padX;
        float totalH = contentH + padY * 2;
        float cornerR = totalH / 2.0F;

        float boxX = cx - totalW / 2.0F;
        float boxY = cy + 12.0F;
        float centerX = boxX + totalW / 2.0F;
        float centerY = boxY + totalH / 2.0F;

        // Zoom-in / zoom-out scale animation. Scale runs from 0.82 (just-
        // appearing or just-disappearing) to 1.0 (fully visible), pivoted
        // around the HUD's center so it grows / shrinks in place rather
        // than translating.
        float zoomScale = 0.82F + 0.18F * hudFadeAlpha;
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, centerY, 0.0F);
        GL11.glScalef(zoomScale, zoomScale, 1.0F);
        GL11.glTranslatef(-centerX, -centerY, 0.0F);

        // Max bg opacity dropped from 0xFF to 0xCC so even at full fade-in
        // the HUD is slightly transparent — sits more lightly over the
        // world view. alphaByte still scales linearly with hudFadeAlpha.
        int bgAlphaByte = clamp255((int) (hudFadeAlpha * 0xCC));
        int textAlphaByte = clamp255((int) (hudFadeAlpha * 255.0F));
        int themeColor = GuiModule.getThemeColor(0);

        if (HUD.enableBlur != null && HUD.enableBlur.isToggled()) {
            GUIBlurUtil.drawBlurredBackground(
                    (int) boxX, (int) boxY, (int) totalW, (int) totalH,
                    (int) HUD.blurRadius.getInput(), (int) cornerR,
                    Math.min(0.65F, hudFadeAlpha * 0.65F));
            mc.entityRenderer.setupOverlayRendering();
        }

        int bgColor = (bgAlphaByte << 24) | 0x0E1117;
        RenderUtils.drawRoundedRectAA(boxX, boxY, boxX + totalW, boxY + totalH, cornerR, bgColor);

        int accentAlpha = clamp255((int) (hudFadeAlpha * 75));
        int accentColor = (accentAlpha << 24) | (themeColor & 0x00FFFFFF);
        RenderUtils.drawRoundedRectAA(
                boxX + cornerR, boxY,
                boxX + totalW - cornerR, boxY + 1.0F,
                0.5F, accentColor);

        float iconX = boxX + padX;
        float iconY = boxY + (totalH - iconSize) / 2.0F;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && held.getItem() instanceof ItemBlock) {
            renderBlockIcon(held, iconX, iconY, iconSize, hudFadeAlpha);
        }

        int countRGB;
        if (displayedInt < 16)        countRGB = 0xFF6B6B;
        else if (displayedInt < 32)   countRGB = 0xFFB347;
        else if (displayedInt < 128)  countRGB = 0xF8E16C;
        else                          countRGB = 0x7EE787;

        // Pre-multiply RGB by hudFadeAlpha so the text DARKENS as it
        // fades, even though FontUtil's drawSmoothString clamps very-low
        // alpha bytes back up to 0xFF. Combined with the 0.03 early-out
        // above, this produces a smooth fade-out without the text snapping
        // back to full opacity at the tail of the animation.
        int textR = clamp255((int) (((countRGB >> 16) & 0xFF) * hudFadeAlpha));
        int textG = clamp255((int) (((countRGB >>  8) & 0xFF) * hudFadeAlpha));
        int textB = clamp255((int) (( countRGB        & 0xFF) * hudFadeAlpha));
        int countColor = (textAlphaByte << 24) | (textR << 16) | (textG << 8) | textB;
        float textX = iconX + iconSize + gap;
        float textY = boxY + (totalH - countH) / 2.0F;
        if (hasFont) {
            FontUtil.semiBold.drawSmoothString(countStr, textX, textY, countColor);
        } else {
            mc.fontRendererObj.drawStringWithShadow(countStr, textX, textY, countColor);
        }

        GL11.glPopMatrix();

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderBlockIcon(ItemStack stack, float x, float y, float size, float fadeAlpha) {
        org.lwjgl.opengl.GL20.glUseProgram(0);
        RenderItem ri = mc.getRenderItem();
        float prevZLevel = ri.zLevel;
        ri.zLevel = 100.0F;

        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        // Drop the alpha-test floor when fading so partially-transparent
        // icon pixels survive the test instead of getting hard-clipped.
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.02F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,       GL11.GL_ZERO);
        // Multiply icon RGB *and* alpha by the fade — RGB darkens, alpha
        // fades. This works whether the GUI-item lighting path respects
        // our alpha or just multiplies the RGB modulator into its output.
        GlStateManager.color(fadeAlpha, fadeAlpha, fadeAlpha, fadeAlpha);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();

        float scale = size / 16.0F;
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        ri.renderItemIntoGUI(stack, 0, 0);

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        ri.zLevel = prevZLevel;
        GlStateManager.popMatrix();

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private int getBlockCount() {
        CountMode mode = (CountMode) countMode.getMode();
        int total = 0;
        if (mode == CountMode.HOTBAR) {
            total = Utils.Player.getBlockAmountInCurrentStack(mc.thePlayer.inventory.currentItem);
        } else {
            for (int slot = 0; slot < 36; slot++) {
                total += Utils.Player.getBlockAmountInCurrentStack(slot);
            }
        }
        return total;
    }

    private void setShift(boolean shift) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), shift);
    }

    @Override
    public String getHudSuffix() {
        // Show the configured shift-time range in ms so the user can see
        // their current tuning at a glance from the ArrayList. A single-
        // value collapse for when min == max keeps the suffix short.
        int min = (int) shiftTime.getInputMin();
        int max = (int) shiftTime.getInputMax();
        if (min == max) return min + "ms";
        return min + "-" + max + "ms";
    }
}
