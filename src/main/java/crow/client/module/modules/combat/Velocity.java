package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.module.Module;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class Velocity extends Module {

    private static final double SCALE_JITTER_PCT = 0.012D;
    public static SliderSetting horizontal, vertical, chance, projHorizontal, projVertical, projChance, projDistance, delay;
    public static TickSetting onlyWhileTargeting, disableHoldingS, diffProjectiles;
    public static ComboSetting projMode, modeSetting;

    public Velocity() {
        super("Velocity", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Scale knockback or use vanilla jump resets."));
        this.registerSetting(modeSetting = new ComboSetting("Mode", VelocityMode.Scale));

        this.registerSetting(horizontal = new SliderSetting("Horizontal", 100.0D, -100.0D, 100.0D, 1.0D));
        this.registerSetting(vertical = new SliderSetting("Vertical", 100.0D, -100.0D, 100.0D, 1.0D));
        this.registerSetting(chance = new SliderSetting("Chance", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(delay = new SliderSetting("Reaction Delay", 0.0D, 0.0D, 9.0D, 1.0D));
        this.registerSetting(onlyWhileTargeting = new TickSetting("Only while targeting", false));
        this.registerSetting(disableHoldingS = new TickSetting("Disable while holding S", false));
        this.registerSetting(diffProjectiles = new TickSetting("Different velo for projectiles", false));
        this.registerSetting(projMode = new ComboSetting("Projectiles Mode", ProjectileMode.Distance));
        this.registerSetting(projHorizontal = new SliderSetting("Proj Horizontal", 90.0D, -100.0D, 100.0D, 1.0D));
        this.registerSetting(projVertical = new SliderSetting("Proj Vertical", 100.0D, -100.0D, 100.0D, 1.0D));
        this.registerSetting(projChance = new SliderSetting("Proj Chance", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(projDistance = new SliderSetting("Proj Distance", 3D, 0.0D, 20D, 0.1D));
    }

    @Override
    public String getHudSuffix() {
        return (int) horizontal.getInput() + "% " + (int) vertical.getInput() + "%";
    }

    @Override
    public void postApplyConfig() {
        syncVisibility();
    }

    @Override
    public void guiButtonToggled(Setting setting) {
        if (setting == modeSetting || setting == projMode) {
            syncVisibility();
        }
    }

    @Subscribe
    public void onLivingUpdate(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof LivingUpdateEvent)) return;
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return;
        if (mc.thePlayer.maxHurtTime <= 0) return;

        int delayTicks = (int) delay.getInput();
        int targetHurtTime = mc.thePlayer.maxHurtTime - delayTicks;
        if (mc.thePlayer.hurtTime > targetHurtTime || mc.thePlayer.hurtTime < targetHurtTime) return;

        if (onlyWhileTargeting.isToggled() && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) return;
        if (disableHoldingS.isToggled() && Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) return;
        if (!passesChance(chance.getInput())) return;

        if (modeSetting.getMode() == VelocityMode.JumpReset) {
            handleJumpReset();
            return;
        }

        if (diffProjectiles.isToggled()) {
            EntityPlayer attacker = null;
            if (mc.thePlayer.getLastAttacker() instanceof EntityPlayer) {
                attacker = (EntityPlayer) mc.thePlayer.getLastAttacker();
            }
            if (attacker != null) {
                ProjectileMode projectileMode = (ProjectileMode) projMode.getMode();
                boolean useProjectileVelo = false;

                if (projectileMode == ProjectileMode.Distance
                        && attacker.getDistanceToEntity(mc.thePlayer) > projDistance.getInput()) {
                    useProjectileVelo = true;
                } else if (projectileMode == ProjectileMode.ItemHeld) {
                    Item item = attacker.getCurrentEquippedItem() != null
                            ? attacker.getCurrentEquippedItem().getItem() : null;
                    if (item instanceof ItemEgg || item instanceof ItemBow
                            || item instanceof ItemSnow || item instanceof ItemFishingRod) {
                        useProjectileVelo = true;
                    }
                }

                if (useProjectileVelo) {
                    velo();
                    return;
                }
            }
        }

        if (horizontal.getInput() != 100.0D) {
            double hj = jitterFactor();
            mc.thePlayer.motionX *= (horizontal.getInput() / 100.0D) * hj;
            mc.thePlayer.motionZ *= (horizontal.getInput() / 100.0D) * hj;
        }

        if (vertical.getInput() != 100.0D) {
            double vj = jitterFactor();
            mc.thePlayer.motionY *= (vertical.getInput() / 100.0D) * vj;
        }
    }

    public void velo() {
        if (modeSetting.getMode() == VelocityMode.JumpReset) {
            if (passesChance(projChance.getInput())) {
                handleJumpReset();
            }
            return;
        }

        if (!passesChance(projChance.getInput())) {
            return;
        }

        if (projHorizontal.getInput() != 100.0D) {
            double hj = jitterFactor();
            mc.thePlayer.motionX *= (projHorizontal.getInput() / 100.0D) * hj;
            mc.thePlayer.motionZ *= (projHorizontal.getInput() / 100.0D) * hj;
        }

        if (projVertical.getInput() != 100.0D) {
            double vj = jitterFactor();
            mc.thePlayer.motionY *= (projVertical.getInput() / 100.0D) * vj;
        }
    }

    private static double jitterFactor() {
        return 1.0D + (ThreadLocalRandom.current().nextDouble() * 2.0D - 1.0D) * SCALE_JITTER_PCT;
    }

    private boolean passesChance(double chance) {
        return chance >= 100.0D || Math.random() < chance / 100.0D;
    }

    private void handleJumpReset() {
        if (mc.thePlayer.onGround && !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava() && !mc.thePlayer.isOnLadder()) {
            mc.thePlayer.jump();
        }
    }

    private void syncVisibility() {
        boolean scaleMode = modeSetting.getMode() == VelocityMode.Scale;
        boolean projectileMode = scaleMode && diffProjectiles.isToggled();
        boolean projectileDistanceMode = projectileMode && (ProjectileMode) projMode.getMode() == ProjectileMode.Distance;
        hideIfReady(horizontal,       !scaleMode);
        hideIfReady(vertical,         !scaleMode);
        hideIfReady(chance,           !scaleMode);
        hideIfReady(delay,            !scaleMode);
        hideIfReady(onlyWhileTargeting, !scaleMode);
        hideIfReady(disableHoldingS,  !scaleMode);
        hideIfReady(diffProjectiles,  !scaleMode);
        hideIfReady(projMode,         !projectileMode);
        hideIfReady(projHorizontal,   !projectileMode);
        hideIfReady(projVertical,     !projectileMode);
        hideIfReady(projChance,       !projectileMode);
        hideIfReady(projDistance,      !projectileDistanceMode);
    }

    private void hideIfReady(Setting setting, boolean hidden) {
        try {
            setting.hideComponent(hidden);
        } catch (NullPointerException ignored) {
        }
    }

    public enum ProjectileMode {
        Distance, ItemHeld
    }

    public enum VelocityMode {
        Scale, JumpReset
    }
}
