package crow.client.module.modules.movement;

import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.combat.Reach;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class KeepSprint extends Module {
    public static DescriptionSetting a, a2;
    public static SliderSetting b;
    public static TickSetting c, sprint;

    public KeepSprint() {
        super("KeepSprint", ModuleCategory.movement);
        this.registerSetting(a = new DescriptionSetting("Default is 40% motion reduction"));
        this.registerSetting(a2 = new DescriptionSetting("and stopping sprint."));
        this.registerSetting(b = new SliderSetting("Slow %", 40.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(c = new TickSetting("Reach only", false));
        this.registerSetting(sprint = new TickSetting("Stop sprint", true));
    }

    public static void sl(Entity en) {
        if (en == null || mc == null || mc.thePlayer == null) return;

        double dist;
        Module reach = Crow.moduleManager == null ? null : Crow.moduleManager.getModuleByClazz(Reach.class);
        if (c.isToggled() && reach != null && reach.isEnabled() && !mc.thePlayer.capabilities.isCreativeMode) {
            dist = distanceToBoundingBox(en);
            double val;
            if (dist > 3.0D) {
                val = (100.0D - (double) ((float) b.getInput())) / 100.0D;
            } else {
                val = 0.6D;
            }

            mc.thePlayer.motionX *= val;
            mc.thePlayer.motionZ *= val;
        } else {
            dist = (100.0D - (double) ((float) b.getInput())) / 100.0D;
            mc.thePlayer.motionX *= dist;
            mc.thePlayer.motionZ *= dist;
        }
        if (sprint.isToggled()) {
            mc.thePlayer.setSprinting(false);
        }

    }

    private static double distanceToBoundingBox(Entity target) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        AxisAlignedBB box = target.getEntityBoundingBox();
        if (box == null) {
            return mc.thePlayer.getDistanceToEntity(target);
        }

        double closestX = MathHelper.clamp_double(eyes.xCoord, box.minX, box.maxX);
        double closestY = MathHelper.clamp_double(eyes.yCoord, box.minY, box.maxY);
        double closestZ = MathHelper.clamp_double(eyes.zCoord, box.minZ, box.maxZ);
        double dx = eyes.xCoord - closestX;
        double dy = eyes.yCoord - closestY;
        double dz = eyes.zCoord - closestZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
