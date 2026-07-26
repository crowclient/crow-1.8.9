package crow.client.module.modules.combat.aura;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Mouse;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.GameLoopEvent;
import crow.client.event.impl.PacketEvent;
import crow.client.event.impl.UpdateEvent;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.module.modules.client.Targets;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.SilentAim;
import crow.client.utils.Utils;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class KillAura extends Module {

    private EntityLivingBase target;

    public static SliderSetting reach, rotationSpeed;
    private DoubleSliderSetting cps;
    private TickSetting disableOnTp, disableWhenFlying, mouseDown, onlySurvival;
    private TickSetting randomizeRotation, smoothRotation, autoBlock, targetRing;
    private SliderSetting attackTickDelay;
    private ComboSetting blockMode;

    private float targetYaw, targetPitch;
    private boolean locked;

    private final CoolDown tpCooldown = new CoolDown(1);
    private boolean leftDown;
    private long leftDownTime, leftUpTime, leftNextBurst;
    private double burstMultiplier;
    private boolean inBurst;
    private int ticksSinceAttack;
    private boolean auraUseItemDown;
    private boolean auraUseItemQueued;
    /** Ticks left holding the use-item key up so the server sees us not blocking. */
    private int unblockTicks;
    private volatile boolean teleportResetPending;

    private float aimDriftYaw, aimDriftPitch;
    private int aimDriftTicks;

    private int lastTargetId = -1;
    private long lastTargetSeenMs;
    private long targetAcquiredMs;
    private long reactionDelayMs;

    private static final long TARGET_PERSIST_MS = 600L;

    public KillAura() {
        super("SilentAura", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Set targets in Client->Targets"));
        this.registerSetting(reach = new SliderSetting("Reach", 4.0, 3.0, 6.0, 0.05));
        this.registerSetting(rotationSpeed = new SliderSetting("Rot speed", 80, 10, 300, 1));
        this.registerSetting(cps = new DoubleSliderSetting("CPS", 10, 14, 1, 20, 0.5));
        this.registerSetting(attackTickDelay = new SliderSetting("Min ticks", 1, 0, 5, 1));
        this.registerSetting(onlySurvival = new TickSetting("Survival only", true));
        this.registerSetting(disableOnTp = new TickSetting("Off on TP", true));
        this.registerSetting(disableWhenFlying = new TickSetting("Off flying", true));
        this.registerSetting(mouseDown = new TickSetting("Hold LMB", false));
        // No "Move fix" toggle: movement always follows the reported yaw. Walking
        // on the camera yaw while the packet carries the aim yaw is a guaranteed
        // simulation mismatch, so there is no correct way to turn it off.
        this.registerSetting(smoothRotation = new TickSetting("Smooth rot", true));
        this.registerSetting(randomizeRotation = new TickSetting("Rand rot", true));
        this.registerSetting(autoBlock = new TickSetting("Auto block", false));
        this.registerSetting(blockMode = new ComboSetting("Block", BlockMode.NONE));
        this.registerSetting(targetRing = new TickSetting("Target ring", true));
    }

    @Override
    public void onEnable() {
        if (Utils.Player.isPlayerInGame()) {
            targetYaw = mc.thePlayer.rotationYaw;
            targetPitch = mc.thePlayer.rotationPitch;
        }
        locked = true;
        leftDownTime = 0;
        leftUpTime = 0;
        ticksSinceAttack = 0;
        leftDown = false;
        aimDriftYaw = aimDriftPitch = 0;
        aimDriftTicks = 0;
        lastTargetId = -1;
        lastTargetSeenMs = 0L;
        targetAcquiredMs = 0L;
        reactionDelayMs = 0L;
        auraUseItemDown = false;
        auraUseItemQueued = false;
        unblockTicks = 0;
        teleportResetPending = false;
        SilentAim.release(this);
    }

    @Override
    public void onDisable() {
        teleportResetPending = false;
        target = null;
        locked = true;
        releaseAttackState();
        SilentAim.release(this);
    }

    /**
     * Runs at {@code Minecraft.runTick} HEAD, which is where the attack has to
     * happen — see {@link #runAttack()}.
     */
    @Subscribe
    public void gameLoopCleanup(GameLoopEvent e) {
        if (consumeTeleportReset()) return;
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null
                || mc.thePlayer == null || mc.playerController == null
                || (target != null && (!target.isEntityAlive()
                    || mc.thePlayer.getDistanceToEntity(target) > reach.getInput()))) {
            clearTargetState();
            return;
        }
        runAttack();
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (consumeTeleportReset()) return;

        if (!e.isPre()) return;

        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null || mc.thePlayer == null || mc.playerController == null) {
            clearTargetState();
            return;
        }

        if (onlySurvival.isToggled() && mc.playerController.getCurrentGameType() != GameType.SURVIVAL) {
            clearTargetState(); return;
        }
        if (!tpCooldown.hasFinished()) { clearTargetState(); return; }
        if (mouseDown.isToggled() && !Mouse.isButtonDown(0)) { clearTargetState(); return; }
        if (disableWhenFlying.isToggled() && mc.thePlayer.capabilities.isFlying) { clearTargetState(); return; }

        EntityLivingBase candidate = Targets.getTargetEntityNoFov(reach.getInput());
        EntityLivingBase entityTarget = candidate;
        if (target != null && target.isEntityAlive()
                && mc.thePlayer.getDistanceToEntity(target) <= reach.getInput()
                && candidate != null && candidate != target) {
            double curDist = mc.thePlayer.getDistanceToEntity(target);
            double newDist = mc.thePlayer.getDistanceToEntity(candidate);
            if (newDist >= curDist * 0.85) {
                entityTarget = target;
            }
        }
        if (entityTarget == null || !entityTarget.isEntityAlive() || mc.thePlayer.getDistanceToEntity(entityTarget) > reach.getInput()) {
            if (lastTargetId != -1
                    && System.currentTimeMillis() - lastTargetSeenMs > TARGET_PERSIST_MS) {
                lastTargetId = -1;
            }
            clearTargetState();
            return;
        }

        target = entityTarget;
        locked = false;

        ThreadLocalRandom r = ThreadLocalRandom.current();

        int currentId = target.getEntityId();
        if (currentId != lastTargetId) {
            targetAcquiredMs = System.currentTimeMillis();
            reactionDelayMs = 150L + r.nextInt(140);
            lastTargetId = currentId;
        }
        lastTargetSeenMs = System.currentTimeMillis();

        double heightFactor = 0.975D + r.nextDouble() * 0.018D;
        tickAimDrift(r);

        // Walk normally whenever we possibly can.
        //
        // Grim only demands that the look ray carried by the flying packet
        // actually intersects the target's hitbox — Reach raycasts it and a miss
        // is the Hitboxes flag. It does not demand that we point at any
        // particular part of them. So when your own view already lands on the
        // hitbox, the correct move is to send your view unchanged and deviate by
        // nothing at all.
        //
        // That matters far beyond tidiness: the movement remap is a no-op while
        // the reported yaw is within 22.5° of the camera, because your raw input
        // is then already the nearest grid direction. Offset zero means movement
        // is bit-for-bit vanilla, with no remap engaged and nothing to feel. In
        // ordinary play — looking roughly at whoever you are fighting — that is
        // the case nearly all the time, so the aura only deviates on the ticks
        // where your aim genuinely was not on them.
        //
        // Randomisation and drift are skipped on those ticks too. Their whole
        // point is to keep a synthetic rotation from looking synthetic, and this
        // rotation is not synthetic — it is yours.
        if (cameraAlreadyOnTarget()) {
            targetYaw = mc.thePlayer.rotationYaw;
            targetPitch = mc.thePlayer.rotationPitch;
        } else {
            float[] rot = aimPoint(heightFactor, 0.03F + aimDriftPitch * 0.02F);
            targetYaw = rot[0] + aimDriftYaw;
            targetPitch = rot[1];

            if (randomizeRotation.isToggled()) {

                targetYaw += (float) (r.nextGaussian() * 0.42);
                targetPitch += (float) (r.nextGaussian() * 0.22);
                targetPitch = MathHelper.clamp_float(targetPitch, -90.0F, 90.0F);
            }
        }

        SilentAim.Request req = new SilentAim.Request();
        req.yaw = targetYaw;
        req.pitch = targetPitch;
        req.profile = SilentAim.Profile.COMBAT;
        req.priority = 100;
        if (!smoothRotation.isToggled()) {
            req.instant = true;
            req.disableTremor = true;
        } else {
            float speed = (float) rotationSpeed.getInput();
            req.maxYawStepDeg = speed;
            req.maxPitchStepDeg = speed * 0.8f;
            // Drive stiffness off the slider too, the way BlockIn does. Mapping
            // the slider to the per-tick cap alone made everything above ~40 a
            // no-op — the spring never asks for more than stiffness*error — so
            // the whole upper range behaved identically and acquisition always
            // ran at the fixed 0.55, which lands a 60 degree sweep in one 33
            // degree step. Now the slider actually controls settle time.
            float t = MathHelper.clamp_float((speed - 10f) / (300f - 10f), 0f, 1f);
            req.stiffness = 0.15f + t * (0.80f - 0.15f);
        }
        req.claimant = this;
        SilentAim.aim(req);
    }

    /**
     * Attack from {@code runTick} HEAD, which is the only placement that satisfies
     * every packet-order check at once.
     *
     * <p>Vanilla's tick is: {@code clickMouse} (C0A swing, C02 attack) →
     * {@code rightClickMouse} (C08 place/use) → {@code theWorld.updateEntities()},
     * which is where {@code onUpdateWalkingPlayer} emits the C0B sprint/sneak
     * actions and then the C03 flying packet. Three separate checks read that
     * order:
     * <ul>
     *   <li>{@code Post} — any attack after the tick's C03.
     *   <li>{@code PacketOrderF} — any action packet after a sprint/sneak C0B in
     *       the same tick. Attacking from UpdateEvent.PRE trips this, because
     *       {@code onUpdateWalkingPlayer} sends the C0B before the event fires.
     *   <li>{@code PacketOrderI} — an attack after a place/use packet in the same
     *       tick, which Auto block's synthesised right-click can produce.
     * </ul>
     * runTick HEAD precedes all three, so none of the offending flags are set yet.
     *
     * <p>The target and {@code targetYaw} are one tick old here, which is correct
     * rather than merely tolerable: {@link SilentAim#beginCycle()} has just
     * stepped the spring toward the target submitted last tick, so
     * {@link #isAimedAtTarget()} is asking "did the spring arrive where it was
     * aimed", and vanilla's own mouseover is a tick stale in the same way.
     */
    private void runAttack() {
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null
                || mc.thePlayer == null || mc.playerController == null
                || target == null || locked || !target.isEntityAlive()
                || mc.thePlayer.getDistanceToEntity(target) > reach.getInput()) {
            clearTargetState();
            return;
        }
        if (!SilentAim.isClaimedBy(this) || isUsingConsumable()) {
            releaseAttackState();
            return;
        }

        // MultiActionsA ("attacked while using an item") and MultiActionsE
        // ("swinging while using an item") both fire on any attack or swing that
        // reaches the server while it still believes we are using an item — and
        // a blocking sword counts, whether Auto block put it up or the user is
        // simply holding right-click. Vanilla cannot produce that: the release
        // has to come first.
        //
        // The release cannot share a tick with the attack either, because
        // PacketOrderI flags an attack sent after a RELEASE_USE_ITEM in the same
        // tick, so this releases and then sits out the tick. Re-blocking is held
        // off for a couple of ticks afterwards by unblockTicks, otherwise the
        // user's own held right-click re-blocks instantly and we never get a
        // clear tick to swing in. That release/attack/re-block rhythm is exactly
        // what a human block-hitting produces.
        if (unblockTicks > 0) {
            unblockTicks--;
            forceUseItemUp();
        }
        if (mc.thePlayer.isUsingItem()) {
            forceUseItemUp();
            mc.playerController.onStoppedUsingItem(mc.thePlayer);
            unblockTicks = 2;
            return;
        }

        ticksSinceAttack++;
        boolean reacted = System.currentTimeMillis() - targetAcquiredMs >= reactionDelayMs;

        // No aim-stability gate. This used to also require the yaw step applied
        // this tick to be under 6°, which is precisely the condition that holds
        // while you are moving: circling a target demands a high turn rate, so
        // the step stays large even when the rotation is dead on them, and every
        // one of those ticks silently dropped a click. That is the CPS loss while
        // moving, and it hit hardest exactly when the fight was most active.
        //
        // It was redundant anyway. A large step only happens either mid-sweep,
        // where isAimedAtTarget() already refuses because we are far off target,
        // or while tracking, where being on target is the whole point. The
        // readiness test below is the check that actually matters.
        if (reacted
                && ticksSinceAttack >= (int) attackTickDelay.getInput()
                && isAimedAtTarget()) {
            crowClick();
        }

        // Putting the block back up in the tick we attacked in would land the C08
        // place after the C02 attack inside one tick, which is PacketOrderJ
        // ("use item after attacking"). ticksSinceAttack is zeroed by the attack
        // itself, so this simply skips the tick it happened in.
        if (unblockTicks > 0 || ticksSinceAttack < 1) return;

        BlockMode bm = autoBlock.isToggled() ? (BlockMode) blockMode.getMode() : BlockMode.NONE;
        boolean holdingSword = Utils.Player.isPlayerHoldingSword();
        if (bm == BlockMode.VANILLA && holdingSword
                && mc.thePlayer.prevSwingProgress < mc.thePlayer.swingProgress) {
            releaseAuraUseItemState();
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            auraUseItemQueued = true;
        } else if (bm == BlockMode.BLOCK_HIT && holdingSword) {

            long now = System.currentTimeMillis();
            boolean releaseWindow = leftUpTime > 0
                    && now >= leftUpTime - 90L
                    && now <= leftUpTime + 60L;
            setAuraUseItemState(!releaseWindow);
        } else {
            releaseAuraUseItemState();
        }
    }

    @Subscribe
    public void packetEvent(PacketEvent e) {
        if (e.isIncoming() && e.getPacket() instanceof S08PacketPlayerPosLook) {
            teleportResetPending = true;
        }
    }

    @Subscribe
    public void renderWorldLast(ForgeEvent fe) {
        if (fe.getEvent() instanceof RenderWorldLastEvent && target != null
                && targetRing.isToggled()) {
            int color = GuiModule.getThemeColor(0);
            Utils.HUD.drawRingAroundEntity(target, color, 0.6D, 0.05D, 2.5F);
        }
    }

    private void crowClick() {
        this.leftClickExecute();
    }

    public void leftClickExecute() {
        if (!SilentAim.isClaimedBy(this) || target == null || mc.thePlayer == null
                || mc.playerController == null || !target.isEntityAlive()) {
            releaseAttackState();
            return;
        }

        if (isUsingConsumable()) {
            releaseAttackState();
            return;
        }

        if (leftUpTime > 0L && leftDownTime > 0L) {
            // Press and release may resolve in the same tick. This was an
            // if/else, so a click always burned two ticks: one to reach
            // leftDownTime and set leftDown, another to reach leftUpTime and
            // actually swing. At 50ms a tick that put a hard floor of 100ms on
            // the interval however high the CPS slider went, and when the press
            // tick was missed by a millisecond it slipped to 150ms — a 6.7 CPS
            // that no setting could lift.
            if (!leftDown && System.currentTimeMillis() > leftDownTime) {
                leftDown = true;
            }
            // >= not >: a tick lands exactly on the deadline whenever the
            // interval is a multiple of 50ms, which 10 CPS is exactly. Missing it
            // by a strict comparison pushed that click a whole tick out and
            // turned a 10 CPS setting into 6.7.
            if (System.currentTimeMillis() >= leftUpTime && leftDown) {
                // No stopUsingItem() here. EntityPlayer.stopUsingItem is client
                // side only — it sends nothing, so it used to clear our own view
                // of the block while the server carried on believing we were
                // using the item, which is the MultiActionsA/E state exactly.
                // runAttack now releases properly, a tick earlier, via
                // PlayerControllerMP.onStoppedUsingItem, and refuses to reach
                // here until the server has seen it.

                // No random whiff. There used to be a 3% chance of consuming the
                // click and swinging at nothing, which costs real damage and buys
                // nothing — the server cannot see a decision not to attack, only
                // the packets, and a genuinely missed swing looks the same as a
                // slightly late one.
                if (mc.thePlayer.getDistanceToEntity(target) <= reach.getInput()) {
                    // Swing FIRST. Minecraft.clickMouse calls swingItem() before
                    // attackEntity(), so the wire order is C0A then C02. Sending
                    // C02 first is Grim's PacketOrderB — a vanilla client can
                    // never produce it.
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, target);
                }
                ticksSinceAttack = 0;
                genLeftTimings();
                leftDown = false;
            }
        } else {
            genLeftTimings();
        }
    }

    public void genLeftTimings() {
        double clickSpeed = Utils.Client.sampleClickCps(cps, Utils.Java.rand());
        long delay = Math.max(1L, Math.round(1000.0D / clickSpeed));

        if (System.currentTimeMillis() > leftNextBurst) {
            if (!inBurst && Utils.Java.rand().nextInt(100) >= 85) {
                inBurst = true;
                burstMultiplier = 1.1D + Utils.Java.rand().nextDouble() * 0.15D;
            } else {
                inBurst = false;
            }
            leftNextBurst = System.currentTimeMillis() + 500L + Utils.Java.rand().nextInt(1500);
        }
        if (inBurst) delay = (long) (delay / burstMultiplier);

        // Occasional slow click. This used to add 30-110ms one time in five,
        // which is not a hesitation, it is a dropped click: 110ms on top of a
        // 71ms interval is a momentary 5.5 CPS, and averaged out it cost well
        // over a click per second. Humans are not that inconsistent.
        if (Utils.Java.rand().nextInt(100) >= 80) {
            delay += 5L + Utils.Java.rand().nextInt(20);
        }

        // Anchor the next click to the previous scheduled one, not to now.
        //
        // Anchoring to now silently quantised every interval up to the next tick
        // boundary: the click fires on the first tick at or after the deadline,
        // and the following deadline was then measured from that tick. A 71ms
        // target (14 CPS) became a flat 100ms, so every setting above 10 CPS
        // collapsed to exactly 10 and no slider position could do anything about
        // it. Advancing from the schedule lets the error cancel instead of
        // accumulating — intervals alternate around the target and the average
        // comes out right, which is also what a real clicker looks like through
        // 20 TPS packets.
        long now = System.currentTimeMillis();
        long base = leftUpTime > 0L ? leftUpTime : now;
        // Never bank more than one click's worth of catch-up, so a lag spike or
        // a spell out of range resumes at the normal rate instead of firing a
        // burst to "make up" for it.
        if (base < now - delay) base = now - delay;

        leftUpTime = base + delay;
        leftDownTime = leftUpTime - (delay / 2L) - Utils.Java.rand().nextInt(10);
    }

    private boolean consumeTeleportReset() {
        if (!teleportResetPending) return false;
        teleportResetPending = false;
        SilentAim.release(this);
        if (!disableOnTp.isToggled()) return false;

        tpCooldown.setCooldown(2000);
        tpCooldown.start();
        clearTargetState();
        return true;
    }

    private void clearTargetState() {
        target = null;
        locked = true;
        aimDriftYaw = aimDriftPitch = 0f;
        aimDriftTicks = 0;
        if (mc.thePlayer != null) {
            targetYaw = mc.thePlayer.rotationYaw;
            targetPitch = mc.thePlayer.rotationPitch;
        }
        SilentAim.release(this);
        releaseAttackState();
    }

    private void releaseAttackState() {
        leftDown = false;
        leftDownTime = 0L;
        leftUpTime = 0L;
        ticksSinceAttack = 0;
        unblockTicks = 0;
        releaseAuraUseItemState();
    }

    /**
     * Hold the use-item key up regardless of whether the user is physically
     * pressing it. {@code auraUseItemDown} is set so the normal release path
     * still hands the key back to the physical state when the window ends.
     */
    private void forceUseItemUp() {
        if (mc.gameSettings == null || mc.gameSettings.keyBindUseItem == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        auraUseItemDown = true;
    }

    private void setAuraUseItemState(boolean pressed) {
        if (!pressed) {
            releaseAuraUseItemState();
            return;
        }
        if (mc.gameSettings == null || mc.gameSettings.keyBindUseItem == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        auraUseItemDown = true;
    }

    private void releaseAuraUseItemState() {
        if (mc.gameSettings != null && mc.gameSettings.keyBindUseItem != null) {
            KeyBinding useItem = mc.gameSettings.keyBindUseItem;
            if (auraUseItemQueued) {
                useItem.isPressed();
            }
            if (auraUseItemDown) {
                boolean physicallyDown = GameSettings.isKeyDown(useItem);
                KeyBinding.setKeyBindState(useItem.getKeyCode(), physicallyDown);
            }
        }
        auraUseItemQueued = false;
        auraUseItemDown = false;
    }

    private boolean isUsingConsumable() {
        if (mc.thePlayer == null || !mc.thePlayer.isUsingItem()) return false;
        ItemStack inUse = mc.thePlayer.getItemInUse();
        if (inUse == null) return false;
        return inUse.getItem() instanceof ItemFood
                || inUse.getItem() instanceof ItemBow
                || inUse.getItem() instanceof ItemPotion
                || inUse.getItem() instanceof ItemBucketMilk;
    }

    public double getReach() { return reach.getInput(); }

    /**
     * Whether the player's own view already puts the look ray on the target's
     * hitbox, in which case there is nothing to correct and we can leave the
     * rotation completely alone.
     *
     * <p>This mirrors what Grim's Reach check does to us: cast the ray the
     * flying packet will carry out to our reach and intersect it with the
     * target's box. The box is shrunk by {@code margin} so we only claim a hit
     * when the ray is comfortably inside it, not clipping an edge that a tick of
     * position desync could move out from under us.
     */
    private boolean cameraAlreadyOnTarget() {
        if (target == null || mc.thePlayer == null) return false;

        float yaw = mc.thePlayer.rotationYaw;
        float pitch = mc.thePlayer.rotationPitch;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);

        double dirX = -Math.sin(yawRad) * cosPitch;
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * cosPitch;

        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeZ = mc.thePlayer.posZ;

        double margin = 0.10D;
        net.minecraft.util.AxisAlignedBB box = target.getEntityBoundingBox();
        double minX = box.minX + margin, maxX = box.maxX - margin;
        double minY = box.minY + margin, maxY = box.maxY - margin;
        double minZ = box.minZ + margin, maxZ = box.maxZ - margin;
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) return false;

        // Slab method: the ray hits when the three per-axis entry/exit intervals
        // overlap, and that overlap lies ahead of us and within reach.
        double near = 0.0D, far = reach.getInput();
        double[][] slabs = {
            { dirX, eyeX, minX, maxX },
            { dirY, eyeY, minY, maxY },
            { dirZ, eyeZ, minZ, maxZ },
        };
        for (double[] s : slabs) {
            double d = s[0], origin = s[1], lo = s[2], hi = s[3];
            if (Math.abs(d) < 1.0E-8D) {
                if (origin < lo || origin > hi) return false;
                continue;
            }
            double t1 = (lo - origin) / d;
            double t2 = (hi - origin) / d;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > near) near = t1;
            if (t2 < far) far = t2;
            if (near > far) return false;
        }
        return true;
    }

    /**
     * Yaw/pitch to the nearest point on the target's hitbox, rather than to its
     * centre axis.
     *
     * <p>{@code Utils.Player.getTargetRotations} aims at {@code posX}/{@code posZ}
     * at eye height — a point <i>inside</i> the player. That is fine at range and
     * useless up close: as the gap shrinks, the angle to a point inside them
     * swings faster and faster, until at contact range it is essentially inside
     * your own head and a fraction of a block of movement demands tens of degrees
     * of rotation. The spring cannot follow that, so {@link #isAimedAtTarget()}
     * never settles and nothing is ever thrown — it stops hitting exactly when
     * you are closest.
     *
     * <p>Clamping our own eye position into their box gives the nearest point on
     * the surface facing us instead. Standing beside someone that point is level
     * with the eye, so the pitch is near zero and stable; the required rotation
     * stays small precisely where the centre-axis aim blew up. It is also where a
     * real player looks — at the body in front of them, not through it.
     *
     * <p>{@code inset} keeps the point off the exact edge so a tick of position
     * desync cannot put it outside the hitbox, and {@code heightFactor} keeps the
     * existing bias up towards the head when there is room for it.
     */
    private float[] aimPoint(double heightFactor, float pitchNudge) {
        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeZ = mc.thePlayer.posZ;

        net.minecraft.util.AxisAlignedBB box = target.getEntityBoundingBox();
        double inset = 0.12D;
        double minX = box.minX + inset, maxX = box.maxX - inset;
        double minZ = box.minZ + inset, maxZ = box.maxZ - inset;
        double minY = box.minY + inset;
        double maxY = Math.max(minY, box.minY + (box.maxY - box.minY) * heightFactor);

        double aimX = MathHelper.clamp_double(eyeX, Math.min(minX, maxX), Math.max(minX, maxX));
        double aimY = MathHelper.clamp_double(eyeY, minY, maxY);
        double aimZ = MathHelper.clamp_double(eyeZ, Math.min(minZ, maxZ), Math.max(minZ, maxZ));

        double dx = aimX - eyeX;
        double dz = aimZ - eyeZ;
        double dy = aimY - eyeY + pitchNudge;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        // Standing inside their footprint leaves no horizontal direction to point
        // at, so hold the yaw we already have rather than snapping somewhere
        // arbitrary — a snap there would be both jarring and a rotation spike.
        float yaw = horiz < 1.0E-4D
                ? SilentAim.getServerYaw()
                : (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(horiz, 1.0E-4D))));

        return new float[] { yaw, MathHelper.clamp_float(pitch, -89.5F, 89.5F) };
    }

    private boolean isAimedAtTarget() {
        if (target == null) {
            return false;
        }
        float thrYaw = 12.5F + ThreadLocalRandom.current().nextFloat() * 2.5F;
        float thrPitch = 10.5F + ThreadLocalRandom.current().nextFloat() * 2.0F;
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - SilentAim.getServerYaw()));
        float pitchDiff = Math.abs(targetPitch - SilentAim.getServerPitch());
        return yawDiff < thrYaw && pitchDiff < thrPitch;
    }

    private void tickAimDrift(ThreadLocalRandom r) {
        aimDriftTicks++;
        if (aimDriftTicks < 2 + r.nextInt(4)) {
            return;
        }
        aimDriftTicks = 0;
        aimDriftYaw += (r.nextFloat() - 0.5F) * 0.35F;
        aimDriftPitch += (r.nextFloat() - 0.5F) * 0.22F;
        aimDriftYaw = MathHelper.clamp_float(aimDriftYaw, -1.1F, 1.1F);
        aimDriftPitch = MathHelper.clamp_float(aimDriftPitch, -0.75F, 0.75F);
    }

    @Override
    public String getHudSuffix() { return target != null ? target.getName() : ""; }

    public enum BlockMode { NONE, VANILLA, BLOCK_HIT }
}
