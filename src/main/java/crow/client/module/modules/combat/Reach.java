package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.combat.aura.KillAura;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import java.util.concurrent.ThreadLocalRandom;

public class Reach extends Module {

    public static DoubleSliderSetting reach;
    public static SliderSetting chance;
    public static TickSetting weapon_only;
    public static TickSetting moving_only;
    public static TickSetting sprint_only;
    public static TickSetting showReachDisplay;
    public static ComboSetting reachMode;
    public static SliderSetting displayDuration;

    public enum ReachMode { NORMAL, STRAFE_BOOST, DYNAMIC }

    private static double currentReach = 3.0;
    private static long lastUpdate = 0;
    private static double lastHitDistance = 0.0;
    private static long lastHitTime = 0;
    private static float displayAlpha = 0.0F;

    private static int hitCount = 0;
    private static long combatStartTime = 0;
    private static final long COMBAT_RESET_MS = 4000;

    public Reach() {
        super("Reach", ModuleCategory.combat);
        this.registerSetting(reachMode = new ComboSetting("Mode", ReachMode.NORMAL));
        this.registerSetting(reach = new DoubleSliderSetting("Reach (Blocks)", 3.1, 3.3, 3, 6, 0.05));
        this.registerSetting(chance = new SliderSetting("Chance %", 100, 0, 100, 1));
        this.registerSetting(weapon_only = new TickSetting("Weapon only", false));
        this.registerSetting(moving_only = new TickSetting("Moving only", false));
        this.registerSetting(sprint_only = new TickSetting("Sprint only", false));
        this.registerSetting(showReachDisplay = new TickSetting("Show Reach Display", true));
        this.registerSetting(displayDuration = new SliderSetting("Display Duration (ms)", 1500, 500, 5000, 100));
        displayDuration.visibleWhen(() -> showReachDisplay != null && showReachDisplay.isToggled());
    }

    @Override
    public String getHudSuffix() {
        return reach.getInputMin() + "-" + reach.getInputMax();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof AttackEntityEvent)) return;
        AttackEntityEvent e = (AttackEntityEvent) fe.getEvent();
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return;
        if (!(e.target instanceof EntityLivingBase)) return;

        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 targetPos = e.target.getPositionVector();

        Vec3 closest = closestPointOnBB(eyes, e.target);
        lastHitDistance = eyes.distanceTo(closest);
        lastHitTime = System.currentTimeMillis();

        if (System.currentTimeMillis() - combatStartTime > COMBAT_RESET_MS) {
            hitCount = 0;
            combatStartTime = System.currentTimeMillis();
        }
        hitCount++;
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        if (showReachDisplay == null || !showReachDisplay.isToggled()) return;

        long elapsed = System.currentTimeMillis() - lastHitTime;
        long duration = (long) displayDuration.getInput();

        float targetAlpha;
        if (elapsed < 150) {
            targetAlpha = elapsed / 150.0F;
        } else if (elapsed < duration - 300) {
            targetAlpha = 1.0F;
        } else if (elapsed < duration) {
            targetAlpha = 1.0F - ((elapsed - (duration - 300)) / 300.0F);
        } else {
            targetAlpha = 0.0F;
        }
        displayAlpha += (targetAlpha - displayAlpha) * 0.3F;
        if (displayAlpha < 0.02F) {
            displayAlpha = 0.0F;
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth() / 2;
        int cy = sr.getScaledHeight() / 2;

        String text = String.format("%.2f blocks", lastHitDistance);
        int alpha = (int) (255 * displayAlpha);
        int color = (alpha << 24) | 0xFFFFFF;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();

        if (FontUtil.hasLoaded()) {
            float textW = (float) FontUtil.semiBold.getStringWidth(text);
            FontUtil.semiBold.drawSmoothString(text, cx - textW / 2.0F, cy + 12, color);
        } else {
            int textW = mc.fontRendererObj.getStringWidth(text);
            mc.fontRendererObj.drawString(text, cx - textW / 2, cy + 12, color, true);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static double getReach() {

        KillAura aura = Crow.moduleManager == null ? null
                : (KillAura) Crow.moduleManager.getModuleByClazz(KillAura.class);
        if (aura != null && aura.isEnabled() && KillAura.reach != null) {
            return KillAura.reach.getInput();
        }

        double normal = (mc == null || mc.playerController == null) ? 3.0
                : (mc.playerController.extendedReach() ? 5.0 : 3.0);

        if (weapon_only == null || moving_only == null || sprint_only == null || reach == null) {
            return normal;
        }

        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return normal;

        if (weapon_only.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return normal;
        if (moving_only.isToggled() && mc.thePlayer.moveForward == 0.0F && mc.thePlayer.moveStrafing == 0.0F)
            return normal;
        if (sprint_only.isToggled() && !mc.thePlayer.isSprinting()) return normal;

        if (System.currentTimeMillis() - lastUpdate > 50) {
            lastUpdate = System.currentTimeMillis();

            if (chance != null && ThreadLocalRandom.current().nextDouble(100) >= chance.getInput()) {
                currentReach = normal;
                return currentReach + creativeOffset();
            }

            double min = reach.getInputMin();
            double max = reach.getInputMax();
            ReachMode mode = reachMode != null ? (ReachMode) reachMode.getMode() : ReachMode.NORMAL;

            switch (mode) {
                case STRAFE_BOOST: {

                    double base = gaussianReach(min, max);
                    if (mc.thePlayer != null && Math.abs(mc.thePlayer.moveStrafing) > 0.01F) {
                        base += 0.05 + ThreadLocalRandom.current().nextDouble(0.08);
                    }
                    currentReach = clampReach(base, min, max + 0.15);
                    break;
                }
                case DYNAMIC: {

                    double progress = Math.min(1.0, hitCount / 12.0);
                    double dynMin = min + (max - min) * 0.2 * progress;
                    double dynMax = min + (max - min) * (0.5 + 0.5 * progress);
                    currentReach = gaussianReach(dynMin, dynMax);
                    break;
                }
                case NORMAL:
                default: {
                    currentReach = gaussianReach(min, max);
                    break;
                }
            }
        }

        return currentReach + creativeOffset();
    }

    private static double gaussianReach(double min, double max) {
        double mid = (min + max) / 2.0;
        double range = (max - min) / 2.0;
        double g = ThreadLocalRandom.current().nextGaussian() * 0.4;
        g = Math.max(-1.0, Math.min(1.0, g));
        return mid + range * g;
    }

    private static double clampReach(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double creativeOffset() {
        return (mc.playerController != null && mc.playerController.extendedReach()) ? 2.0 : 0.0;
    }

    private static Vec3 closestPointOnBB(Vec3 point, Entity entity) {
        double x = Math.max(entity.getEntityBoundingBox().minX,
                Math.min(point.xCoord, entity.getEntityBoundingBox().maxX));
        double y = Math.max(entity.getEntityBoundingBox().minY,
                Math.min(point.yCoord, entity.getEntityBoundingBox().maxY));
        double z = Math.max(entity.getEntityBoundingBox().minZ,
                Math.min(point.zCoord, entity.getEntityBoundingBox().maxZ));
        return new Vec3(x, y, z);
    }
}
