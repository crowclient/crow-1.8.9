package crow.client.module.modules.render;

import com.google.common.collect.Multimap;
import com.google.common.eventbus.Subscribe;

import org.lwjgl.opengl.GL11;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.modules.HUD;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

public class CustomHotbar extends Module {
    public static SliderSetting scale;
    public static SliderSetting opacity;
    public static TickSetting blurBackground;

    private static CustomHotbar instance;

    private float selectedIndexAnim = -1.0F;

    private final float[] slotScales = new float[9];

    private String displayedName = "";
    private String displayedDamage = "";

    private String pendingName = "";
    private String pendingDamage = "";

    private float nameSwapAlpha = 0.0F;

    private float tooltipAlpha = 0.0F;

    private long lastNameChangeMs;

    private static final long TOOLTIP_LINGER_MS = 3000L;

    private float displayedTooltipX = -1.0F;

    private float displayedBoxW = -1.0F;
    private float displayedBoxH = -1.0F;

    private int lastSlot = -1;

    private long lastFrameMs;

    private static final float SPEED_INDICATOR    = 14.0F;

    private static final float SPEED_SLOT_SCALE   = 18.0F;

    private static final float SPEED_NAME_FADE_OUT = 22.0F;

    private static final float SPEED_NAME_FADE_IN  = 14.0F;

    private static final float SPEED_TOOLTIP_X    = 14.0F;

    private static final float SPEED_BOX_SIZE     = 11.0F;

    private static final float SPEED_ENVELOPE_IN  = 18.0F;

    private static final float SPEED_ENVELOPE_OUT = 4.5F;

    private static final float NAME_SWAP_THRESHOLD = 0.04F;

    public CustomHotbar() {
        super("CustomHotbar", ModuleCategory.render);
        instance = this;
        this.registerSetting(scale          = new SliderSetting("Scale",   1.0D, 0.8D, 1.5D, 0.01D));
        this.registerSetting(opacity        = new SliderSetting("Opacity", 0.85D, 0.2D, 1.0D, 0.01D));
        this.registerSetting(blurBackground = new TickSetting("Blur", true));
        for (int i = 0; i < 9; i++) slotScales[i] = 1.0F;
    }

    public static boolean shouldReplaceVanilla() {
        return instance != null && instance.isEnabled() && Utils.Player.isPlayerInGame();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent event) {
        if (!(event.getEvent() instanceof RenderGameOverlayEvent.Pre) || !shouldReplaceVanilla()) return;
        RenderGameOverlayEvent.Pre pre = (RenderGameOverlayEvent.Pre) event.getEvent();

        if (pre.type == RenderGameOverlayEvent.ElementType.HOTBAR
                || pre.type == RenderGameOverlayEvent.ElementType.TEXT) {
            pre.setCanceled(true);
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (!shouldReplaceVanilla() || mc.thePlayer == null || mc.thePlayer.inventory == null) return;
        // CustomHotbar stays visible with F3 open — the vanilla hotbar
        // doesn't get hidden by F3 either, so the replacement shouldn't.
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;

        long now = System.currentTimeMillis();
        if (lastFrameMs == 0L) lastFrameMs = now;

        float dt = Math.min(0.1F, (now - lastFrameMs) / 1000.0F);
        lastFrameMs = now;

        ScaledResolution sr = crow.client.utils.RenderUtils.scaled();
        int   screenW    = sr.getScaledWidth();
        int   screenH    = sr.getScaledHeight();
        float uiScale    = (float) scale.getInput();
        float opacityVal = (float) opacity.getInput();
        int   currentSlot = mc.thePlayer.inventory.currentItem;

        float barWidth  = 184.0F * uiScale;
        float barHeight = 24.0F  * uiScale;
        float cornerR   = barHeight / 2.0F;
        float slotSize  = 20.0F * uiScale;
        float barX      = screenW / 2.0F - barWidth / 2.0F;
        float barY      = screenH - barHeight + 1.0F;
        float slotPadding = (barWidth - slotSize * 9.0F) / 2.0F;
        float firstSlotX  = barX + slotPadding;

        if (selectedIndexAnim < 0.0F) selectedIndexAnim = currentSlot;

        selectedIndexAnim = expEase(selectedIndexAnim, currentSlot, SPEED_INDICATOR, dt);
        for (int i = 0; i < 9; i++) {
            float target = (i == currentSlot) ? 1.12F : 1.0F;
            slotScales[i] = expEase(slotScales[i], target, SPEED_SLOT_SCALE, dt);
        }

        updateTooltipState(currentSlot, dt, now);

        float tooltipTargetX = firstSlotX + slotSize * currentSlot + slotSize / 2.0F;
        if (displayedTooltipX < 0.0F) {
            displayedTooltipX = tooltipTargetX;
        } else {
            displayedTooltipX = expEase(displayedTooltipX, tooltipTargetX, SPEED_TOOLTIP_X, dt);
            if (Math.abs(displayedTooltipX - tooltipTargetX) < 0.05F) displayedTooltipX = tooltipTargetX;
        }

        updateBoxSizeEase(dt);

        if (blurBackground.isToggled() && HUD.enableBlur != null && HUD.enableBlur.isToggled()) {
            GUIBlurUtil.drawBlurredBackground(
                    (int) barX, (int) barY, (int) barWidth, (int) barHeight,
                    (int) HUD.blurRadius.getInput(), (int) cornerR, opacityVal);
            mc.entityRenderer.setupOverlayRendering();
        }

        int bgAlpha = (int) (opacityVal * 190);
        int bgColor = (bgAlpha << 24) | 0x101318;
        RenderUtils.drawGlassPanel(barX, barY, barX + barWidth, barY + barHeight, cornerR, bgColor,
                HUD.dropShadow == null || HUD.dropShadow.isToggled() ? RenderUtils.GLASS_SHADOW_CHROME : 0);

        int themeColor  = GuiModule.getThemeColor(0);
        int accentAlpha = (int) (opacityVal * 80);
        int accentColor = (accentAlpha << 24) | (themeColor & 0x00FFFFFF);
        RenderUtils.drawRoundedRectAA(barX + cornerR, barY, barX + barWidth - cornerR, barY + 1.0F, 0.5F, accentColor);

        float indX = firstSlotX + slotSize * selectedIndexAnim;
        float indY = barY + (barHeight - slotSize) / 2.0F;
        float indR = slotSize / 2.0F;
        int   indFillColor    = ((int) (opacityVal * 48) << 24)  | (themeColor & 0x00FFFFFF);
        int   indOutlineColor = ((int) (opacityVal * 136) << 24) | (themeColor & 0x00FFFFFF);
        RenderUtils.drawRoundedRectAA(indX, indY, indX + slotSize, indY + slotSize, indR, indFillColor);
        RenderUtils.drawRoundedOutline(indX, indY, indX + slotSize, indY + slotSize, indR, 0.8F, indOutlineColor);

        renderItems(firstSlotX, barY, barHeight, slotSize, uiScale);

        renderStackCounts(firstSlotX, barY, barHeight, slotSize);

        renderTooltip(barY);

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderItems(float firstSlotX, float barY, float barHeight, float slotSize, float uiScale) {

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableAlpha(); GlStateManager.enableAlpha();
        GlStateManager.disableBlend(); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableRescaleNormal();

        RenderItem ri = mc.getRenderItem();
        float prevZLevel = ri.zLevel;
        ri.zLevel = 100.0F;

        GlStateManager.pushMatrix();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack == null) continue;

            float slotX = firstSlotX + slotSize * i;
            float itemScale = uiScale * slotScales[i];
            float cx = slotX + slotSize / 2.0F;
            float cy = barY + barHeight / 2.0F;

            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0.0F);
            GlStateManager.scale(itemScale, itemScale, 1.0F);
            GlStateManager.translate(-8.0F, -8.0F, 0.0F);
            ri.renderItemIntoGUI(stack, 0, 0);

            ri.renderItemOverlayIntoGUI(mc.fontRendererObj, stack, 0, 0, "");
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();

        ri.zLevel = prevZLevel;
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();

        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderStackCounts(float firstSlotX, float barY, float barHeight, float slotSize) {
        boolean hasFont = FontUtil.hasLoaded() && FontUtil.small != null;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack == null || stack.stackSize <= 1) continue;

            String count = String.valueOf(stack.stackSize);
            float slotX = firstSlotX + slotSize * i;
            float cw, ch;
            if (hasFont) {
                cw = (float) FontUtil.small.getStringWidth(count);
                ch = FontUtil.small.getHeight();
            } else {
                cw = mc.fontRendererObj.getStringWidth(count);
                ch = mc.fontRendererObj.FONT_HEIGHT;
            }
            float tx = slotX + slotSize - cw - 1.0F;
            float ty = barY   + barHeight - ch - 1.0F;
            if (hasFont) {
                FontUtil.small.drawSmoothString(count, tx + 0.75F, ty + 0.75F, 0x88000000);
                FontUtil.small.drawSmoothString(count, tx, ty, 0xFFFFFFFF);
            } else {
                mc.fontRendererObj.drawStringWithShadow(count, tx, ty, 0xFFFFFFFF);
            }
        }
    }

    private void updateTooltipState(int currentSlot, float dt, long now) {
        ItemStack held = mc.thePlayer.getHeldItem();
        String itemName   = held != null ? held.getDisplayName() : "";
        String itemDamage = held != null ? getItemDamageText(held) : "";

        boolean slotChanged = currentSlot != lastSlot;
        lastSlot = currentSlot;

        if (displayedName.isEmpty() && !itemName.isEmpty()) {
            displayedName   = itemName;
            displayedDamage = itemDamage;
            pendingName     = itemName;
            pendingDamage   = itemDamage;
            lastNameChangeMs = now;
        } else if (!itemName.equals(pendingName)) {

            pendingName   = itemName;
            pendingDamage = itemDamage;
            lastNameChangeMs = now;
        } else if (slotChanged) {

            lastNameChangeMs = now;
        }

        boolean targetMatches = displayedName.equals(pendingName);
        if (!targetMatches) {
            nameSwapAlpha = expEase(nameSwapAlpha, 0.0F, SPEED_NAME_FADE_OUT, dt);
            if (nameSwapAlpha < NAME_SWAP_THRESHOLD) {

                displayedName   = pendingName;
                displayedDamage = pendingDamage;
                nameSwapAlpha   = 0.0F;
            }
        } else if (!displayedName.isEmpty()) {
            nameSwapAlpha = expEase(nameSwapAlpha, 1.0F, SPEED_NAME_FADE_IN, dt);
        } else {
            nameSwapAlpha = 0.0F;
        }

        boolean withinLinger = !pendingName.isEmpty()
                && (now - lastNameChangeMs) <= TOOLTIP_LINGER_MS;
        float envTarget = withinLinger ? 1.0F : 0.0F;
        float envSpeed  = withinLinger ? SPEED_ENVELOPE_IN : SPEED_ENVELOPE_OUT;
        tooltipAlpha = expEase(tooltipAlpha, envTarget, envSpeed, dt);
        if (tooltipAlpha < 0.01F) {
            tooltipAlpha = 0.0F;

            if (pendingName.isEmpty()) {
                displayedName = "";
                displayedDamage = "";
                nameSwapAlpha = 0.0F;
            }
        }
    }

    private static final float TT_PAD_X = 9.0F;
    private static final float TT_PAD_Y = 4.0F;
    private static final float TT_LINE_GAP = 1.0F;

    private float targetBoxW(String name, String damage) {
        float nameW   = measureName(name);
        float damageW = measureSmall(damage);
        return Math.max(nameW, damageW) + TT_PAD_X * 2;
    }

    private float targetBoxH(String damage) {
        boolean hasCustomFont = FontUtil.hasLoaded()
                && FontUtil.semiBold != null && FontUtil.small != null;
        float nameH = hasCustomFont
                ? FontUtil.semiBold.getHeight()
                : mc.fontRendererObj.FONT_HEIGHT;
        float damageH = hasCustomFont
                ? FontUtil.small.getHeight()
                : mc.fontRendererObj.FONT_HEIGHT;
        boolean hasDamage = damage != null && !damage.isEmpty();
        float contentH = nameH + (hasDamage ? (TT_LINE_GAP + damageH) : 0.0F);
        return contentH + TT_PAD_Y * 2;
    }

    private void updateBoxSizeEase(float dt) {
        String tName, tDamage;
        if (!pendingName.isEmpty()) {
            tName   = pendingName;
            tDamage = pendingDamage;
        } else if (!displayedName.isEmpty()) {
            tName   = displayedName;
            tDamage = displayedDamage;
        } else {

            return;
        }

        float tW = targetBoxW(tName, tDamage);
        float tH = targetBoxH(tDamage);

        if (displayedBoxW < 0.0F || displayedBoxH < 0.0F) {

            displayedBoxW = tW;
            displayedBoxH = tH;
        } else {
            displayedBoxW = expEase(displayedBoxW, tW, SPEED_BOX_SIZE, dt);
            displayedBoxH = expEase(displayedBoxH, tH, SPEED_BOX_SIZE, dt);
        }
    }

    private void renderTooltip(float barY) {
        if (tooltipAlpha < 0.01F) return;
        if (displayedName.isEmpty()) return;
        if (displayedBoxW <= 0.0F || displayedBoxH <= 0.0F) return;

        boolean hasCustomFont = FontUtil.hasLoaded()
                && FontUtil.semiBold != null && FontUtil.small != null;

        float nameH = hasCustomFont
                ? FontUtil.semiBold.getHeight()
                : mc.fontRendererObj.FONT_HEIGHT;
        float damageH = hasCustomFont
                ? FontUtil.small.getHeight()
                : mc.fontRendererObj.FONT_HEIGHT;

        float nameW = measureName(displayedName);
        float damageW = measureSmall(displayedDamage);
        boolean hasDamage = !displayedDamage.isEmpty();
        float contentH = nameH + (hasDamage ? (TT_LINE_GAP + damageH) : 0.0F);

        boolean isCreative = mc.playerController != null
                && mc.playerController.getCurrentGameType() == GameType.CREATIVE;
        float ttBottomY = isCreative ? (barY - 8.0F) : (barY - 38.0F);

        float boxH = displayedBoxH;
        float boxW = displayedBoxW;
        float ttBoxY2 = ttBottomY;
        float ttBoxY1 = ttBottomY - boxH;
        float ttBoxX1 = displayedTooltipX - boxW / 2.0F;
        float ttBoxX2 = displayedTooltipX + boxW / 2.0F;
        float ttCornerR = boxH / 2.0F;

        int boxAlpha = (int) (tooltipAlpha * 210);
        int boxColor = (boxAlpha << 24) | 0x0B0D12;
        RenderUtils.drawRoundedRectAA(ttBoxX1, ttBoxY1, ttBoxX2, ttBoxY2, ttCornerR, boxColor);

        int themeColor = GuiModule.getThemeColor(0);
        int accentAlpha = (int) (tooltipAlpha * 70);
        int accentColor = (accentAlpha << 24) | (themeColor & 0x00FFFFFF);
        RenderUtils.drawRoundedRectAA(
                ttBoxX1 + ttCornerR, ttBoxY1,
                ttBoxX2 - ttCornerR, ttBoxY1 + 1.0F,
                0.5F, accentColor);

        float textTop = ttBoxY1 + (boxH - contentH) / 2.0F;

        float layerAlpha = tooltipAlpha * nameSwapAlpha;
        if (layerAlpha < 0.005F) return;

        int aByte = clamp255((int) (layerAlpha * 255.0F));
        int nameColor = (aByte << 24) | 0xF0F2F8;
        float nameX = displayedTooltipX - nameW / 2.0F;
        if (hasCustomFont) {
            FontUtil.semiBold.drawSmoothString(displayedName, nameX, textTop, nameColor);
        } else {
            mc.fontRendererObj.drawStringWithShadow(displayedName, nameX, textTop, nameColor);
        }

        if (hasDamage) {
            int dmgColor = (aByte << 24) | 0xFF6B6B;
            float dmgY = textTop + nameH + TT_LINE_GAP;
            float dmgX = displayedTooltipX - damageW / 2.0F;
            if (hasCustomFont) {
                FontUtil.small.drawSmoothString(displayedDamage, dmgX, dmgY, dmgColor);
            } else {
                mc.fontRendererObj.drawStringWithShadow(displayedDamage, dmgX, dmgY, dmgColor);
            }
        }
    }

    private float measureName(String s) {
        if (s == null || s.isEmpty()) return 0.0F;
        boolean has = FontUtil.hasLoaded() && FontUtil.semiBold != null;
        return has ? (float) FontUtil.semiBold.getStringWidth(s)
                   : (float) mc.fontRendererObj.getStringWidth(s);
    }

    private float measureSmall(String s) {
        if (s == null || s.isEmpty()) return 0.0F;
        boolean has = FontUtil.hasLoaded() && FontUtil.small != null;
        return has ? (float) FontUtil.small.getStringWidth(s)
                   : (float) mc.fontRendererObj.getStringWidth(s);
    }

    private String getItemDamageText(ItemStack stack) {
        if (stack == null) return "";
        try {
            Multimap<String, AttributeModifier> attrs = stack.getAttributeModifiers();
            String key = SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName();
            if (attrs != null && attrs.containsKey(key)) {
                for (AttributeModifier mod : attrs.get(key)) {
                    return String.format("%.1f Attack Damage", mod.getAmount() + 1.0);
                }
            }
            if (stack.getItem() instanceof ItemSword) {
                float dmg = ((ItemSword) stack.getItem()).getDamageVsEntity() + 4.0F;
                return String.format("%.1f Attack Damage", dmg);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static float expEase(float current, float target, float speed, float dt) {
        if (dt <= 0.0F) return current;
        float k = 1.0F - (float) Math.exp(-speed * dt);
        return current + (target - current) * k;
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
