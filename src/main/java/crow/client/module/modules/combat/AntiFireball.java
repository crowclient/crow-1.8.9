package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class AntiFireball extends Module {

    private final SliderSetting range;
    private final SliderSetting rotationSpeed;
    private final TickSetting swing;

    private Entity targetFireball = null;

    private boolean engaging = false;

    private float lerpYaw;
    private float lerpPitch;

    private float targetYaw;
    private float targetPitch;

    private int engageTicks;

    private final Set<Integer> reflectedIds = new HashSet<>();

    private static final float ANGLE_THRESHOLD = 4.0F;

    public AntiFireball() {
        super("AntiFireball", ModuleCategory.combat);
        this.registerSetting(range = new SliderSetting("Range", 6.0D, 3.0D, 8.0D, 0.5D));
        this.registerSetting(rotationSpeed = new SliderSetting("Rot speed", 20.0D, 5.0D, 45.0D, 1.0D));
        this.registerSetting(swing = new TickSetting("Swing", true));
    }

    @Override
    public void onEnable() {
        targetFireball = null;
        engaging = false;
        engageTicks = 0;
        reflectedIds.clear();
    }

    @Override
    public void onDisable() {
        if (engaging) {
            releaseMovementFreeze();
        }
        targetFireball = null;
        engaging = false;
        engageTicks = 0;
        reflectedIds.clear();
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;

        if (targetFireball != null) {
            if (targetFireball.isDead || mc.thePlayer.getDistanceToEntity(targetFireball) > range.getInput() + 2.0) {
                disengage();
            }
        }

        if (targetFireball == null) {
            Entity closest = null;
            double closestDist = range.getInput();

            reflectedIds.removeIf(id -> mc.theWorld.getEntityByID(id) == null);

            for (Entity en : new java.util.ArrayList<>(mc.theWorld.loadedEntityList)) {
                if (!(en instanceof EntityFireball) && !(en instanceof EntityWitherSkull)) continue;

                if (reflectedIds.contains(en.getEntityId())) continue;

                // Hard filter 1: server-set shootingEntity is us. This
                // catches the simple case where the fireball entity has
                // a shootingEntity field pointing at the local player.
                if (en instanceof EntityFireball && ((EntityFireball) en).shootingEntity == mc.thePlayer) continue;
                double dist = mc.thePlayer.getDistanceToEntity(en);
                if (dist > closestDist) continue;

                Vec3 fireballPos = new Vec3(en.posX, en.posY, en.posZ);
                Vec3 playerPos = mc.thePlayer.getPositionVector();
                double motionMagSq = en.motionX * en.motionX + en.motionY * en.motionY + en.motionZ * en.motionZ;
                if (motionMagSq < 1.0E-6D) continue; // stationary — not a threat
                double motionMag = Math.sqrt(motionMagSq);
                Vec3 motionNorm = new Vec3(
                        en.motionX / motionMag, en.motionY / motionMag, en.motionZ / motionMag);
                Vec3 toPlayer = playerPos.subtract(fireballPos);
                double toPlayerMag = toPlayer.lengthVector();
                if (toPlayerMag < 1.0E-3D) continue;
                Vec3 toPlayerNorm = new Vec3(
                        toPlayer.xCoord / toPlayerMag,
                        toPlayer.yCoord / toPlayerMag,
                        toPlayer.zCoord / toPlayerMag);

                // Hard filter 2: motion must be clearly toward the
                // player. Raised the threshold from 0.0 (any-positive)
                // to 0.25 — a fireball flying tangentially or barely
                // sideways toward us isn't a real threat and shouldn't
                // grab focus.
                double dotToPlayer = motionNorm.xCoord * toPlayerNorm.xCoord
                        + motionNorm.yCoord * toPlayerNorm.yCoord
                        + motionNorm.zCoord * toPlayerNorm.zCoord;
                if (dotToPlayer <= 0.25) continue;

                // Hard filter 3: own-throw detection. If the fireball's
                // motion direction is aligned with our look vector AND
                // it spawned close to us, it's almost certainly one we
                // just launched — even when shootingEntity isn't set
                // (server-side fireball items, hack-thrown projectiles).
                Vec3 lookVec = mc.thePlayer.getLook(1.0F);
                double lookAlign = motionNorm.xCoord * lookVec.xCoord
                        + motionNorm.yCoord * lookVec.yCoord
                        + motionNorm.zCoord * lookVec.zCoord;
                if (lookAlign > 0.80 && dist < 4.0) {
                    // Mark it so we don't keep re-checking every tick
                    // until it's clearly past us.
                    reflectedIds.add(en.getEntityId());
                    continue;
                }

                closestDist = dist;
                closest = en;
            }

            if (closest != null) {
                targetFireball = closest;
                engage();
            }
        }

        if (!engaging || targetFireball == null) return;

        freezeMovement();

        engageTicks++;

        computeTargetAngles();

        smoothRotate();

        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(targetPitch - mc.thePlayer.rotationPitch);

        if (yawDiff <= ANGLE_THRESHOLD && pitchDiff <= ANGLE_THRESHOLD) {

            reflectedIds.add(targetFireball.getEntityId());

            // Swing before the attack, same as Minecraft.clickMouse — C02 ahead
            // of C0A is Grim's PacketOrderB.
            if (swing.isToggled()) mc.thePlayer.swingItem();
            mc.playerController.attackEntity(mc.thePlayer, targetFireball);
            disengage();
        }
    }

    private void engage() {
        engaging = true;
        engageTicks = 0;

        lerpYaw = mc.thePlayer.rotationYaw;
        lerpPitch = mc.thePlayer.rotationPitch;

        freezeMovement();
    }

    private void disengage() {
        if (engaging) {
            releaseMovementFreeze();
        }
        engaging = false;
        targetFireball = null;
        engageTicks = 0;
    }

    private void freezeMovement() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.setSprinting(false);
    }

    private void releaseMovementFreeze() {
        if (mc == null || mc.gameSettings == null) return;

        restoreBinding(mc.gameSettings.keyBindForward);
        restoreBinding(mc.gameSettings.keyBindBack);
        restoreBinding(mc.gameSettings.keyBindLeft);
        restoreBinding(mc.gameSettings.keyBindRight);
        restoreBinding(mc.gameSettings.keyBindJump);
        restoreBinding(mc.gameSettings.keyBindSprint);
    }

    private void restoreBinding(KeyBinding binding) {
        if (binding != null) {
            KeyBinding.setKeyBindState(binding.getKeyCode(), GameSettings.isKeyDown(binding));
        }
    }

    private void computeTargetAngles() {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double dx = targetFireball.posX - eyes.xCoord;
        double dy = targetFireball.posY + (targetFireball.height / 2.0) - eyes.yCoord;
        double dz = targetFireball.posZ - eyes.zCoord;
        double dist = Math.sqrt(dx * dx + dz * dz);

        targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        targetPitch = (float) -(Math.atan2(dy, dist) * 180.0 / Math.PI);
        targetPitch = MathHelper.clamp_float(targetPitch, -90.0F, 90.0F);
    }

    private void smoothRotate() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float baseSpeed = (float) rotationSpeed.getInput();

        float accelFactor;
        if (engageTicks <= 1) {
            accelFactor = 0.25F + r.nextFloat() * 0.15F;
        } else if (engageTicks == 2) {
            accelFactor = 0.60F + r.nextFloat() * 0.15F;
        } else {
            accelFactor = 0.90F + r.nextFloat() * 0.10F;
        }

        float maxTurn = baseSpeed * accelFactor;

        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - lerpYaw);
        float pitchDiff = targetPitch - lerpPitch;

        float yawStep = MathHelper.clamp_float(yawDiff * 0.50F, -maxTurn, maxTurn);
        float pitchStep = MathHelper.clamp_float(pitchDiff * 0.45F, -maxTurn * 0.80F, maxTurn * 0.80F);

        float jitter = baseSpeed * 0.015F;
        yawStep += (r.nextFloat() - 0.5F) * jitter;
        pitchStep += (r.nextFloat() - 0.5F) * jitter * 0.6F;

        yawStep *= 0.95F + r.nextFloat() * 0.10F;
        pitchStep *= 0.95F + r.nextFloat() * 0.10F;

        lerpYaw += yawStep;
        lerpPitch = MathHelper.clamp_float(lerpPitch + pitchStep, -90.0F, 90.0F);

        float prevYaw = mc.thePlayer.rotationYaw;
        float prevPitch = mc.thePlayer.rotationPitch;

        float rawDeltaYaw = MathHelper.wrapAngleTo180_float(lerpYaw - prevYaw);
        float rawDeltaPitch = lerpPitch - prevPitch;

        float patchedYaw = Utils.Player.patchGCD(rawDeltaYaw);
        float patchedPitch = Utils.Player.patchGCD(rawDeltaPitch);

        float gcd = Utils.Player.getGcd();
        if (Math.abs(patchedYaw) < gcd) patchedYaw = 0;
        if (Math.abs(patchedPitch) < gcd) patchedPitch = 0;

        mc.thePlayer.rotationYaw += patchedYaw;
        mc.thePlayer.rotationPitch = MathHelper.clamp_float(prevPitch + patchedPitch, -90.0F, 90.0F);

        lerpYaw = mc.thePlayer.rotationYaw;
        lerpPitch = mc.thePlayer.rotationPitch;

        mc.thePlayer.prevRotationYawHead = mc.thePlayer.rotationYawHead;
        mc.thePlayer.rotationYawHead = mc.thePlayer.rotationYaw;
        mc.thePlayer.prevRenderYawOffset = mc.thePlayer.renderYawOffset;
        float bodyBlend = 0.30F + r.nextFloat() * 0.15F;
        mc.thePlayer.renderYawOffset += MathHelper.wrapAngleTo180_float(
                mc.thePlayer.rotationYaw - mc.thePlayer.renderYawOffset) * bodyBlend;
    }
}
