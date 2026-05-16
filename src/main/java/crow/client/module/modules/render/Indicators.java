package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class Indicators extends Module {

    public static SliderSetting range;
    public static SliderSetting circleRadius;
    public static SliderSetting iconScale;
    public static TickSetting showFireballs;
    public static TickSetting showArrows;
    public static TickSetting showPearls;
    public static TickSetting showTNT;
    public static TickSetting showSnowballs;
    public static TickSetting showEggs;
    public static TickSetting showDistance;
    public static TickSetting showCircle;

    private static final ItemStack FIRE_CHARGE_STACK = new ItemStack(Items.fire_charge);
    private static final ItemStack ARROW_STACK = new ItemStack(Items.arrow);
    private static final ItemStack PEARL_STACK = new ItemStack(Items.ender_pearl);
    private static final ItemStack TNT_STACK = new ItemStack(Item.getItemFromBlock(Blocks.tnt));
    private static final ItemStack SNOWBALL_STACK = new ItemStack(Items.snowball);
    private static final ItemStack EGG_STACK = new ItemStack(Items.egg);

    private final List<Entity> tracked = new ArrayList<>();

    public Indicators() {
        super("Indicators", ModuleCategory.render);
        this.registerSetting(range = new SliderSetting("Range", 30.0D, 5.0D, 80.0D, 1.0D));
        this.registerSetting(circleRadius = new SliderSetting("Radius", 50.0D, 20.0D, 120.0D, 1.0D));
        this.registerSetting(iconScale = new SliderSetting("Icon size", 1.0D, 0.5D, 2.0D, 0.05D));
        this.registerSetting(showCircle = new TickSetting("Circle", true));
        this.registerSetting(showDistance = new TickSetting("Distance", true));
        this.registerSetting(showFireballs = new TickSetting("Fireballs", true));
        this.registerSetting(showArrows = new TickSetting("Arrows", true));
        this.registerSetting(showPearls = new TickSetting("Pearls", true));
        this.registerSetting(showTNT = new TickSetting("TNT", true));
        this.registerSetting(showSnowballs = new TickSetting("Snowballs", true));
        this.registerSetting(showEggs = new TickSetting("Eggs", true));
    }

    private static ItemStack stackFor(Entity e) {
        if (e instanceof EntityFireball || e instanceof EntityWitherSkull) return FIRE_CHARGE_STACK;
        if (e instanceof EntityArrow) return ARROW_STACK;
        if (e instanceof EntityEnderPearl) return PEARL_STACK;
        if (e instanceof EntityTNTPrimed) return TNT_STACK;
        if (e instanceof EntitySnowball) return SNOWBALL_STACK;
        if (e instanceof EntityEgg) return EGG_STACK;
        return null;
    }

    private boolean isTracked(Entity e) {
        if (e instanceof EntityFireball || e instanceof EntityWitherSkull)
            return showFireballs.isToggled();
        if (e instanceof EntityArrow)
            return showArrows.isToggled();
        if (e instanceof EntityEnderPearl)
            return showPearls.isToggled();
        if (e instanceof EntityTNTPrimed)
            return showTNT.isToggled();
        if (e instanceof EntitySnowball)
            return showSnowballs.isToggled();
        if (e instanceof EntityEgg)
            return showEggs.isToggled();
        return false;
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;
        if (!Utils.Player.isPlayerInGame() || mc.theWorld == null || mc.thePlayer == null) return;

        double maxRangeSq = range.getInput() * range.getInput();
        double playerX = mc.thePlayer.posX;
        double playerZ = mc.thePlayer.posZ;

        tracked.clear();
        List<Entity> entities = mc.theWorld.loadedEntityList;
        for (int i = 0, size = entities.size(); i < size; i++) {
            Entity entity = entities.get(i);
            if (entity == mc.thePlayer) continue;
            if (!(entity instanceof EntityFireball || entity instanceof EntityWitherSkull
                || entity instanceof EntityArrow || entity instanceof EntityEnderPearl
                || entity instanceof EntityTNTPrimed || entity instanceof EntitySnowball
                || entity instanceof EntityEgg)) continue;
            if (!isTracked(entity)) continue;
            double dx = entity.posX - playerX;
            double dz = entity.posZ - playerZ;
            if (dx * dx + dz * dz > maxRangeSq) continue;
            tracked.add(entity);
        }

        if (tracked.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();
        float centerX = screenW / 2.0F;
        float centerY = screenH / 2.0F;
        float radius = (float) circleRadius.getInput();
        float iScale = (float) iconScale.getInput();
        boolean showDist = showDistance.isToggled();
        boolean hasFont = FontUtil.hasLoaded() && FontUtil.small != null;

        double yawRad = Math.toRadians(mc.thePlayer.rotationYaw);

        if (showCircle.isToggled()) {
            RenderUtils.drawCircleOutline(centerX, centerY, radius, 1.0F, 0x14FFFFFF);
        }

        RenderItem renderItem = mc.getRenderItem();

        for (int i = 0, sz = tracked.size(); i < sz; i++) {
            Entity entity = tracked.get(i);

            double dx = entity.posX - playerX;
            double dz = entity.posZ - playerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);

            double worldAngle = Math.atan2(dz, dx);

            double relAngle = worldAngle - yawRad + Math.PI / 2.0;

            float iconX = centerX + (float) Math.sin(relAngle) * radius;
            float iconY = centerY - (float) Math.cos(relAngle) * radius;

            float iconSize = 16.0F * iScale;
            float halfIcon = iconSize / 2.0F;

            GlStateManager.pushMatrix();
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();

            GlStateManager.pushMatrix();
            GlStateManager.translate(iconX - halfIcon, iconY - halfIcon, 150.0F);
            GlStateManager.scale(iScale, iScale, 1.0F);

            ItemStack stack = stackFor(entity);
            if (stack != null) {
                renderItem.renderItemIntoGUI(stack, 0, 0);
            }

            GlStateManager.popMatrix();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();

            GlStateManager.disableLighting();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            if (showDist) {
                String distStr = String.format("%.0f", dist);

                float outward = halfIcon + 4.0F;
                float labelX = centerX + (float) Math.sin(relAngle) * (radius + outward);
                float labelY = centerY - (float) Math.cos(relAngle) * (radius + outward);

                if (hasFont) {
                    float tw = (float) FontUtil.small.getStringWidth(distStr);
                    FontUtil.small.drawSmoothString(distStr, labelX - tw / 2.0F, labelY - FontUtil.small.getHeight() / 2.0F, 0xAAFFFFFF);
                } else {
                    int tw = mc.fontRendererObj.getStringWidth(distStr);
                    mc.fontRendererObj.drawStringWithShadow(distStr, labelX - tw / 2.0F, labelY - 4, 0xAAFFFFFF);
                }
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
