package crow.client.module.modules.combat;

import java.util.concurrent.ThreadLocalRandom;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.PacketEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

/**
 * JumpReset — abuses a vanilla 1.8 mechanic to reduce taken knockback by
 * jumping right as a hit lands.
 *
 * <p>The S12 velocity packet from the server is the earliest reliable
 * "you're about to be knocked back" signal we get. Pressing jump on
 * receipt fires the vanilla sprint-jump horizontal boost (0.2 along yaw)
 * which directly counters part of the knockback's {@code motionX -= 0.5 * dx}
 * subtraction. Net horizontal displacement after the hit is meaningfully
 * less than a flat-footed hit would produce.
 *
 * <h2>Settings</h2>
 * <ul>
 *   <li><b>Chance</b> — % the module decides to attempt a reset on a
 *       given hit. Below 100 % the module also takes some natural-looking
 *       hits unmodified.</li>
 *   <li><b>Accuracy</b> — double-slider sampled per attempt; the sampled
 *       value is the probability the jump fires on the perfect tick.
 *       Remainder fires 1–3 ticks late so the reset partially misses,
 *       matching a human flubbing the timing.</li>
 *   <li><b>Only when targeting</b> — only attempt when an entity is
 *       under the crosshair, so passive damage (fall, fire, projectile
 *       from off-screen) doesn't burn jumps.</li>
 *   <li><b>Water check</b> — disabled while in water / lava since the
 *       jump pattern there reads as bot-like.</li>
 * </ul>
 */
public class JumpReset extends Module {

    public static SliderSetting chance;
    public static DoubleSliderSetting accuracy;
    public static TickSetting onlyWhenTargeting;
    public static TickSetting waterCheck;

    /** Ticks remaining before a delayed (intentionally-mistimed) jump fires. */
    private int pendingMistimeTicks;

    public JumpReset() {
        super("JumpReset", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting(
                "Reduces taken knockback by timing a jump on the hit tick."));
        this.registerSetting(chance = new SliderSetting("Chance", 30, 0, 100, 1));
        this.registerSetting(accuracy = new DoubleSliderSetting("Accuracy", 70, 90, 0, 100, 1));
        this.registerSetting(onlyWhenTargeting = new TickSetting("Only when targeting", true));
        this.registerSetting(waterCheck = new TickSetting("Water check", true));
    }

    @Override
    public void onEnable() {
        pendingMistimeTicks = 0;
    }

    /** Inbound S12 velocity packet for the local player — the KB signal. */
    @Subscribe
    public void onPacket(PacketEvent e) {
        if (!e.isIncoming()) return;
        if (!(e.getPacket() instanceof S12PacketEntityVelocity)) return;
        if (mc.thePlayer == null || !Utils.Player.isPlayerInGame()) return;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) e.getPacket();
        if (vel.getEntityID() != mc.thePlayer.getEntityId()) return;

        // Chance gate first so failed rolls don't preclude future hits.
        if (!passesChance(chance.getInput())) return;

        // Only when targeting — filters passive damage (fall, fire,
        // ranged off-screen) so we don't burn jumps on non-PvP hits.
        if (onlyWhenTargeting.isToggled()
                && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) {
            return;
        }

        if (waterCheck.isToggled()
                && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava())) {
            return;
        }

        // Sample accuracy from the [min, max] range; rolled value is the
        // probability of perfect timing on this hit.
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double accSample = accuracy.getInputMin()
                + rand.nextDouble() * (accuracy.getInputMax() - accuracy.getInputMin());
        boolean perfectTiming = rand.nextDouble() * 100.0D < accSample;

        if (perfectTiming) {
            doJump();
        } else {
            // Mistimed — fire 1–3 ticks late from the main-thread
            // LivingUpdateEvent. The KB packet still gets applied first,
            // so the jump partially misses (which is the point: looks
            // like a human who fumbled the input).
            pendingMistimeTicks = 1 + rand.nextInt(3);
        }
    }

    /** Main-thread tick — drives the mistimed-jump countdown. */
    @Subscribe
    public void onLivingUpdate(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof LivingUpdateEvent)) return;
        if (mc.thePlayer == null || !Utils.Player.isPlayerInGame()) return;
        if (pendingMistimeTicks <= 0) return;

        pendingMistimeTicks--;
        if (pendingMistimeTicks == 0) {
            doJump();
        }
    }

    private void doJump() {
        if (mc.thePlayer == null) return;
        if (!mc.thePlayer.onGround) return;
        if (mc.thePlayer.isOnLadder()) return;
        if (waterCheck.isToggled()
                && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava())) {
            return;
        }
        mc.thePlayer.jump();
    }

    private static boolean passesChance(double pct) {
        return pct >= 100.0D || Math.random() < pct / 100.0D;
    }
}
