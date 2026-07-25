package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.other.NameHider;
import crow.client.module.modules.world.AntiBot;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.util.ArrayList;
import java.util.List;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Nametags extends Module {
    public static SliderSetting offset;
    public static TickSetting showRect;
    public static TickSetting showHealth;
    public static TickSetting showInvis;
    public static TickSetting showArmor;
    public static TickSetting removeTags;
    public static TickSetting customFont;
    public static SliderSetting nameScale;

    private static final FloatBuffer MODEL_VIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer RESULT = BufferUtils.createFloatBuffer(3);

    private static class TagRenderData {
        final float screenX;
        final float screenY;
        final float scale;
        final String text;
        final List<ItemStack> equipment;

        TagRenderData(float screenX, float screenY, float scale, String text, List<ItemStack> equipment) {
            this.screenX = screenX;
            this.screenY = screenY;
            this.scale = scale;
            this.text = text;
            this.equipment = equipment;
        }
    }

    private final List<TagRenderData> pendingTags = new ArrayList<>();

    public Nametags() {
        super("Nametags", ModuleCategory.render);
        this.registerSetting(offset = new SliderSetting("Offset", 0.0D, -40.0D, 40.0D, 1.0D));
        this.registerSetting(nameScale = new SliderSetting("Size", 1.0D, 0.5D, 2.0D, 0.05D));
        this.registerSetting(showRect = new TickSetting("Background", true));
        this.registerSetting(showHealth = new TickSetting("Show health", true));
        this.registerSetting(showInvis = new TickSetting("Show invis", true));
        this.registerSetting(showArmor = new TickSetting("Show armor", true));
        this.registerSetting(removeTags = new TickSetting("Hide vanilla", true));
        this.registerSetting(customFont = new TickSetting("Custom font", true));
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (mc.currentScreen != null) {
            pendingTags.clear();
            return;
        }

        if (fe.getEvent() instanceof RenderLivingEvent.Specials.Pre) {
            RenderLivingEvent.Specials.Pre e = (RenderLivingEvent.Specials.Pre) fe.getEvent();
            if (removeTags.isToggled() && e.entity instanceof EntityPlayer && e.entity != mc.thePlayer) {
                e.setCanceled(true);
            }
            return;
        }

        if (!(fe.getEvent() instanceof RenderWorldLastEvent) || !Utils.Player.isPlayerInGame()) {
            return;
        }

        pendingTags.clear();

        RenderWorldLastEvent event = (RenderWorldLastEvent) fe.getEvent();
        float partialTicks = event.partialTicks;

        MODEL_VIEW.rewind();
        PROJECTION.rewind();
        VIEWPORT.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);

        ScaledResolution sr = new ScaledResolution(mc);
        int scaleFactor = sr.getScaleFactor();

        for (EntityPlayer player : new java.util.ArrayList<>(mc.theWorld.playerEntities)) {
            if (player == null || player == mc.thePlayer || player.deathTime != 0) {
                continue;
            }
            if (!showInvis.isToggled() && player.isInvisible()) {
                continue;
            }
            if (AntiBot.renderBot(player) || player.getDisplayNameString().isEmpty()) {
                continue;
            }

            TagRenderData data = projectTag(player, partialTicks, scaleFactor);
            if (data != null) {
                pendingTags.add(data);
            }
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (mc.currentScreen != null || pendingTags.isEmpty() || !Utils.Player.isPlayerInGame()) {
            return;
        }

        GlStateManager.pushMatrix();
        mc.entityRenderer.setupOverlayRendering();

        for (TagRenderData tag : pendingTags) {
            drawTag(tag);
        }

        GlStateManager.popMatrix();
    }

    private TagRenderData projectTag(EntityPlayer player, float partialTicks, int scaleFactor) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;

        float scale = (float) nameScale.getInput();

        String nameText = player.getDisplayName().getFormattedText();

        Module nameHider = Crow.moduleManager != null ? Crow.moduleManager.getModuleByClazz(NameHider.class) : null;
        if (nameHider != null && nameHider.isEnabled()) {
            nameText = NameHider.format(nameText);
        }

        if (showHealth.isToggled()) {
            double ratio = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
            String colorCode = ratio < 0.3 ? "\u00a7c" : (ratio < 0.5 ? "\u00a76" : (ratio < 0.7 ? "\u00a7e" : "\u00a7a"));
            nameText += " " + colorCode + Utils.Java.round(player.getHealth(), 1);
        }

        RESULT.rewind();
        MODEL_VIEW.rewind();
        PROJECTION.rewind();
        VIEWPORT.rewind();
        boolean ok = GLU.gluProject(
                (float) x,
                (float) (y + player.height + 0.7F + (offset.getInput() / 40.0F)),
                (float) z,
                MODEL_VIEW,
                PROJECTION,
                VIEWPORT,
                RESULT
        );

        if (!ok) {
            return null;
        }

        float depth = RESULT.get(2);
        if (depth < 0.0F || depth > 1.0F) {
            return null;
        }

        float screenX = RESULT.get(0) / scaleFactor;
        float screenY = (VIEWPORT.get(3) - RESULT.get(1)) / scaleFactor;

        return new TagRenderData(screenX, screenY, scale, nameText, collectEquipment(player));
    }

    private void drawTag(TagRenderData tag) {
        boolean useCustomFont = customFont.isToggled();
        int textWidth = useCustomFont
                ? (int) FontUtil.semiBold.getStringWidth(tag.text)
                : mc.fontRendererObj.getStringWidth(tag.text);
        int itemRowWidth = tag.equipment.size() * 18 - (tag.equipment.isEmpty() ? 0 : 2);
        int contentWidth = Math.max(textWidth + 10, itemRowWidth + 6);
        int halfWidth = contentWidth / 2;
        int cardTop = showArmor.isToggled() && !tag.equipment.isEmpty() ? -24 : -10;
        int cardBottom = 10;

        GlStateManager.pushMatrix();
        GlStateManager.translate(tag.screenX, tag.screenY, 0.0F);
        GlStateManager.scale(tag.scale, tag.scale, 1.0F);

        if (showRect.isToggled()) {
            // Glass plate: translucent dark fill + lit rim/hairline, no shadow in-world.
            RenderUtils.drawGlassPanel(-halfWidth - 3, cardTop, halfWidth + 3, cardBottom, 4, 0xB80E1014, 0);
        }

        if (showArmor.isToggled() && !tag.equipment.isEmpty()) {
            renderEquipmentRow(tag.equipment, -itemRowWidth / 2, -21);
        }

        if (useCustomFont) {
            FontUtil.semiBold.drawCenteredStringWithShadow(tag.text, 0, -1, 0xFFFFFFFF);
        } else {
            mc.fontRendererObj.drawStringWithShadow(tag.text, -textWidth / 2.0F, -2, 0xFFFFFFFF);
        }
        GlStateManager.popMatrix();
    }

    private List<ItemStack> collectEquipment(EntityPlayer player) {
        List<ItemStack> equipment = new ArrayList<>();
        for (int i = 3; i >= 0; i--) {
            ItemStack armor = player.getCurrentArmor(i);
            if (armor != null) {
                equipment.add(armor);
            }
        }
        ItemStack held = player.getHeldItem();
        if (held != null) {
            equipment.add(held);
        }
        return equipment;
    }

    private void renderEquipmentRow(List<ItemStack> equipment, int startX, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 150.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableRescaleNormal();

        RenderItem renderItem = mc.getRenderItem();
        int x = startX;
        for (ItemStack stack : equipment) {
            renderItem.renderItemIntoGUI(stack, x, y);
            renderItem.renderItemOverlayIntoGUI(mc.fontRendererObj, stack, x, y, "");
            x += 18;
        }

        GlStateManager.disableRescaleNormal();
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
