package crow.client.module.modules.combat;

import java.util.concurrent.ThreadLocalRandom;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.module.Module;
import crow.client.module.modules.client.Targets;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBow;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class BowAimbot extends Module {

    private static final double ARROW_GRAVITY = 0.05D;
    private static final double ARROW_DRAG    = 0.99D;
    private static final float  MAX_CHARGE    = 3.0F;
    private static final float  MAX_TOF       = 120.0F;

    private final SliderSetting lerpSpeed;
    private final SliderSetting fov;
    private final SliderSetting distance;
    private final SliderSetting pitchOffset;
    private final SliderSetting minCharge;
    private final SliderSetting prediction;
    private final SliderSetting randomization;
    private final TickSetting   requireCharge;
    private final TickSetting   gravityComp;
    private final TickSetting   wallCheck;
    private final TickSetting   aimPitch;

    private float   lerpYaw;
    private float   lerpPitch;
    private boolean tracking;

    private EntityLivingBase currentTarget;

    public BowAimbot() {
        super("BowAimbot", ModuleCategory.combat);

        this.registerSetting(new DescriptionSetting("Lerp bow aimbot with prediction"));
        this.registerSetting(lerpSpeed     = new SliderSetting("Lerp speed",    8.0D,  1.0D,  20.0D,  0.5D));
        this.registerSetting(fov           = new SliderSetting("FOV",           90.0D, 15.0D, 180.0D, 1.0D));
        this.registerSetting(distance      = new SliderSetting("Distance",      64.0D, 5.0D,  150.0D, 1.0D));
        this.registerSetting(pitchOffset   = new SliderSetting("Pitch off",  0.0D, -15.0D, 15.0D,  0.5D));
        this.registerSetting(minCharge     = new SliderSetting("Min charge %",  0.0D,  0.0D,  90.0D,  5.0D));
        this.registerSetting(prediction    = new SliderSetting("Prediction",    1.0D,  0.0D,  2.0D,   0.1D));
        this.registerSetting(randomization = new SliderSetting("Random", 1.0D,  0.0D,  5.0D,   0.5D));
        this.registerSetting(requireCharge = new TickSetting("Need charge",  true));
        this.registerSetting(gravityComp   = new TickSetting("Gravity",    true));
        this.registerSetting(wallCheck     = new TickSetting("Wall check",      true));
        this.registerSetting(aimPitch      = new TickSetting("Aim pitch",       true));
    }

    @Override
    public void onEnable() {
        tracking      = false;
        currentTarget = null;
    }

    @Override
    public void onDisable() {
        tracking      = false;
        currentTarget = null;
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {

        if (fe.getEvent() instanceof RenderWorldLastEvent) {
            return;
        }

        if (!(fe.getEvent() instanceof TickEvent.RenderTickEvent)) return;
        TickEvent.RenderTickEvent rte = (TickEvent.RenderTickEvent) fe.getEvent();
        if (rte.phase != TickEvent.Phase.END) return;

        if (!Utils.Client.currentScreenMinecraft() || !Utils.Player.isPlayerInGame()) {
            stopTracking(); return;
        }

        if (mc.thePlayer.getHeldItem() == null
                || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow)) {
            stopTracking(); return;
        }

        float charge = 1.0F;
        if (mc.thePlayer.isUsingItem()) {
            charge = Math.min(1.0F, (72000 - mc.thePlayer.getItemInUseCount()) / 20.0F);
        }
        if (requireCharge.isToggled()) {
            if (!mc.thePlayer.isUsingItem()) { stopTracking(); return; }
            if (charge * 100.0F < (float) minCharge.getInput()) { stopTracking(); return; }
        }

        EntityLivingBase target = Targets.getTargetEntityNoFov(distance.getInput());
        if (target == null) { stopTracking(); return; }

        if (wallCheck.isToggled() && !mc.thePlayer.canEntityBeSeen(target)) {
            stopTracking(); return;
        }

        if (Math.abs(Utils.Player.fovFromEntity(target)) > fov.getInput() / 2.0D) {
            stopTracking(); return;
        }

        currentTarget = target;

        float arrowSpeed = Math.max(0.1F, charge * MAX_CHARGE);

        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeZ = mc.thePlayer.posZ;

        double tgtX = target.posX;
        double tgtY = target.posY + target.height * 0.7D;
        double tgtZ = target.posZ;

        double hDist  = Math.sqrt((tgtX-eyeX)*(tgtX-eyeX) + (tgtZ-eyeZ)*(tgtZ-eyeZ));
        double logArg = 1.0D - hDist * (1.0D - ARROW_DRAG) / arrowSpeed;
        float  tof    = (logArg <= 0.0D)
                ? MAX_TOF
                : Math.min(MAX_TOF, (float)(Math.log(logArg) / Math.log(ARROW_DRAG)));

        float  pred  = (float) prediction.getInput();
        double speedX = target.posX - target.prevPosX;
        double speedZ = target.posZ - target.prevPosZ;
        double predX = tgtX + speedX * tof * pred;
        double predZ = tgtZ + speedZ * tof * pred;

        double gravityLift = 0.0D;
        if (gravityComp.isToggled() && aimPitch.isToggled()) {
            double simVy = 0.0D;
            double simDrop = 0.0D;
            int ticks = Math.min((int) Math.ceil(tof), 200);
            for (int i = 0; i < ticks; i++) {
                simVy -= ARROW_GRAVITY;
                simVy *= ARROW_DRAG;
                simDrop += simVy;
            }
            gravityLift = -simDrop;
        }

        double diffX    = predX - eyeX;
        double diffY    = (tgtY + gravityLift + pitchOffset.getInput()) - eyeY;
        double diffZ    = predZ - eyeZ;
        double flatDist = Math.sqrt(diffX*diffX + diffZ*diffZ);

        float desiredYaw   = (float)(Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
        float desiredPitch = MathHelper.clamp_float(
                (float)-(Math.atan2(diffY, flatDist) * 180.0D / Math.PI), -90.0F, 90.0F);

        desiredYaw = mc.thePlayer.rotationYaw
                   + MathHelper.wrapAngleTo180_float(desiredYaw - mc.thePlayer.rotationYaw);

        if (!tracking) {
            lerpYaw   = mc.thePlayer.rotationYaw;
            lerpPitch = mc.thePlayer.rotationPitch;
            tracking  = true;
        }

        float chargeScale = Math.max(0.15F, charge * charge);
        float t = MathHelper.clamp_float((float) lerpSpeed.getInput() * 0.025F * chargeScale, 0.005F, 0.99F);

        float rand       = (float) randomization.getInput();
        float noiseYaw   = (ThreadLocalRandom.current().nextFloat() - 0.5F) * rand * 0.05F;
        float noisePitch = (ThreadLocalRandom.current().nextFloat() - 0.5F) * rand * 0.04F;

        lerpYaw   += MathHelper.wrapAngleTo180_float(desiredYaw   - lerpYaw)   * t + noiseYaw;
        lerpPitch += (desiredPitch - lerpPitch)                                 * t + noisePitch;
        lerpPitch  = MathHelper.clamp_float(lerpPitch, -90.0F, 90.0F);

        // GCD-snap deltas before writing to rotationYaw/Pitch — Grim's
        // AimModulo360 flags any delta that isn't an integer multiple of the
        // mouse-sensitivity GCD. Direct lerpYaw assignment leaks fractional
        // values from the t-blend and noise terms.
        float rawDeltaYaw = MathHelper.wrapAngleTo180_float(lerpYaw - mc.thePlayer.rotationYaw);
        float patchedYaw = Utils.Player.patchGCD(rawDeltaYaw);
        mc.thePlayer.rotationYaw += patchedYaw;
        lerpYaw = mc.thePlayer.rotationYaw;

        mc.thePlayer.prevRotationYawHead = mc.thePlayer.rotationYawHead;
        mc.thePlayer.rotationYawHead     = mc.thePlayer.rotationYaw;
        mc.thePlayer.prevRenderYawOffset = mc.thePlayer.renderYawOffset;
        mc.thePlayer.renderYawOffset    +=
                MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - mc.thePlayer.renderYawOffset) * 0.35F;

        if (aimPitch.isToggled()) {
            float rawDeltaPitch = lerpPitch - mc.thePlayer.rotationPitch;
            float patchedPitch = Utils.Player.patchGCD(rawDeltaPitch);
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(
                    mc.thePlayer.rotationPitch + patchedPitch, -90.0F, 90.0F);
            lerpPitch = mc.thePlayer.rotationPitch;
        }
    }

    private void stopTracking() {
        tracking      = false;
        currentTarget = null;
    }
}
