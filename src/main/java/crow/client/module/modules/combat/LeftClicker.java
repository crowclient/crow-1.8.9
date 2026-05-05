package crow.client.module.modules.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

import org.lwjgl.input.Mouse;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.MouseManager;
import crow.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class LeftClicker extends Module {

    public static DoubleSliderSetting leftCPS;
    public static SliderSetting jitterLeft;
    public static TickSetting weaponOnly, breakBlocks, blockHit;
    public static TickSetting jitterSound;

    public static boolean autoClickerEnabled;
    public EntityLivingBase target;

    private final Random rand = new Random();

    private long nextClickAt;

    private final Deque<Long> clickHistory = new ArrayDeque<>();

    private double measuredCps;

    private int lastClickTick = -1;

    private boolean breakHeld;

    private boolean blocking;
    private long blockHitTime;

    private float jitterPhaseYaw, jitterPhasePitch;

    private long sustainedClickStart;

    private double driftedMeanCps = -1.0;
    private long driftStepAt = 0L;

    private boolean jitterLoopPlaying;
    private long jitterCutoutEnd;
    private long jitterNextCutout;

    private long pauseUntil;

    private long nextBreatherRollAt;

    private long nextServerAirSwingAt;

    private long lastAttackMs;

    public LeftClicker() {
        super("Auto Clicker", ModuleCategory.combat);
        this.registerSetting(leftCPS     = new DoubleSliderSetting("Left CPS", 9, 13, 1, 60, 0.5));
        this.registerSetting(jitterLeft  = new SliderSetting("Jitter left", 0.0D, 0.0D, 3.0D, 0.1D));
        this.registerSetting(weaponOnly  = new TickSetting("Weapon only", false));
        this.registerSetting(breakBlocks = new TickSetting("Break blocks", false));
        this.registerSetting(blockHit    = new TickSetting("Block Hit", false));
        this.registerSetting(jitterSound = new TickSetting("Jitter click sound", false));
        autoClickerEnabled = false;
    }

    @Override
    public String getHudSuffix() {
        return String.format("%.1f-%.1f", leftCPS.getInputMin(), leftCPS.getInputMax());
    }

    @Override
    public void onEnable() {
        nextClickAt = 0L;
        clickHistory.clear();
        measuredCps = 0.0;
        lastClickTick = -1;
        sustainedClickStart = 0L;
        driftedMeanCps = -1.0;
        driftStepAt = 0L;
        breakHeld = false;
        blocking = false;
        target = null;
        jitterLoopPlaying = false;
        jitterCutoutEnd = 0L;
        jitterNextCutout = 0L;
        pauseUntil = 0L;
        nextBreatherRollAt = 0L;
        nextServerAirSwingAt = 0L;
        lastAttackMs = 0L;
        autoClickerEnabled = true;
    }

    @Override
    public void onDisable() {
        nextClickAt = 0L;
        clickHistory.clear();
        measuredCps = 0.0;
        lastClickTick = -1;
        sustainedClickStart = 0L;
        driftedMeanCps = -1.0;
        driftStepAt = 0L;
        breakHeld = false;
        target = null;
        jitterLoopPlaying = false;
        pauseUntil = 0L;
        nextBreatherRollAt = 0L;
        nextServerAirSwingAt = 0L;
        lastAttackMs = 0L;
        autoClickerEnabled = false;
        try { crow.client.utils.SoundUtils.stopLoop(); } catch (Throwable ignored) {}
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            if (blocking) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                blocking = false;
            }
        }
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        Object event = fe.getEvent();

        if (event instanceof AttackEntityEvent) {
            target = ((AttackEntityEvent) event).entityLiving;
            lastAttackMs = System.currentTimeMillis();
            if (blockHit.isToggled() && Utils.Player.isPlayerHoldingWeapon()
                    && mc.thePlayer != null && target != null) {
                blocking = true;
                blockHitTime = System.currentTimeMillis();
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            }
            return;
        }

        if (event instanceof TickEvent.RenderTickEvent) {
            TickEvent.RenderTickEvent ev = (TickEvent.RenderTickEvent) event;
            if (ev.phase == TickEvent.Phase.START) {
                tickJitterLoop();
                tickBlockHitRelease();
            }

            return;
        }

        if (event instanceof TickEvent.ClientTickEvent) {
            TickEvent.ClientTickEvent ct = (TickEvent.ClientTickEvent) event;
            if (ct.phase == TickEvent.Phase.START) {
                tickClickLoop();
            }
        }
    }

    private void tickClickLoop() {

        recomputeMeasuredCps();

        if (mc == null || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;
        if (!Mouse.isCreated()) return;

        if (!Mouse.isButtonDown(0)) {
            nextClickAt = 0L;
            sustainedClickStart = 0L;
            lastClickTick = -1;
            pauseUntil = 0L;
            nextBreatherRollAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (sustainedClickStart == 0L) sustainedClickStart = now;

        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return;
        if (handleBreakBlockHold()) return;
        applySmoothJitter();

        rollMaybeBreather(now);

        if (pauseUntil != 0L && now < pauseUntil) return;
        if (pauseUntil != 0L && now >= pauseUntil) pauseUntil = 0L;

        if (nextClickAt != 0L && now < nextClickAt) return;
        if (lastClickTick == mc.thePlayer.ticksExisted) return;

        fireClick();
        lastClickTick = mc.thePlayer.ticksExisted;
        clickHistory.addLast(now);
        nextClickAt = now + sampleClickIntervalMs(now);

        if (target != null && rand.nextInt(100) < 18) {
            pauseUntil = now + 60L + rand.nextInt(120);
        }
    }

    private void rollMaybeBreather(long now) {
        if (sustainedClickStart == 0L) return;

        if (now - sustainedClickStart < 700L) return;
        if (nextBreatherRollAt == 0L) {
            nextBreatherRollAt = now + 800L + rand.nextInt(1700);
            return;
        }
        if (now < nextBreatherRollAt) return;
        if (rand.nextInt(100) < 22) {
            pauseUntil = now + 80L + rand.nextInt(200);
        }
        nextBreatherRollAt = now + 800L + rand.nextInt(1700);
    }

    private void fireClick() {
        if (mc.thePlayer == null) return;

        MouseManager.addLeftClick();

        try {
            ((crow.client.mixin.mixins.IMinecraft) (Object) mc).setLeftClickCounter(0);
        } catch (Throwable ignored) {

        }
        KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
    }

    private void recomputeMeasuredCps() {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!clickHistory.isEmpty() && clickHistory.peekFirst() < cutoff) {
            clickHistory.pollFirst();
        }
        double instant = clickHistory.size();

        measuredCps += (instant - measuredCps) * 0.18;
    }

    private long sampleClickIntervalMs(long now) {
        double lo = leftCPS.getInputMin();
        double hi = leftCPS.getInputMax();
        if (lo > hi) { double t = lo; lo = hi; hi = t; }
        if (lo <= 0) lo = Math.min(hi, 1);
        if (hi <= 0) hi = 1;
        double range = hi - lo;

        double fatigue = 0.0;
        if (sustainedClickStart > 0L) {
            long sustainedMs = now - sustainedClickStart;
            fatigue = Math.max(0.0, Math.min(1.0, (sustainedMs - 2500.0) / 5000.0));
        }

        if (driftedMeanCps <= 0) {
            driftedMeanCps = (lo + hi) * 0.5;
        }
        if (now >= driftStepAt) {

            double preferred = ((lo + hi) * 0.5) - fatigue * range * 0.30;
            double step = (rand.nextDouble() - rand.nextDouble()) * range * 0.20;
            step += (preferred - driftedMeanCps) * 0.25;
            driftedMeanCps += step;

            double softLo = lo - range * 0.15;
            double softHi = hi + range * 0.15;
            driftedMeanCps = Math.max(softLo, Math.min(softHi, driftedMeanCps));
            if (driftedMeanCps < 0.5) driftedMeanCps = 0.5;
            driftStepAt = now + 400L + rand.nextInt(800);
        }

        double meanDelayMs = 1000.0 / Math.max(0.5, driftedMeanCps);

        double roll = rand.nextDouble();
        double delayMs;
        if (roll < 0.05) {

            delayMs = meanDelayMs * (0.50 + rand.nextDouble() * 0.25);
        } else if (roll < 0.12) {

            delayMs = meanDelayMs * (1.50 + rand.nextDouble() * 1.10);
        } else if (roll < 0.15) {

            delayMs = meanDelayMs * (3.00 + rand.nextDouble() * 2.00);
        } else {

            double sigma = 0.14 + rand.nextDouble() * 0.18;
            double n = rand.nextGaussian();
            delayMs = meanDelayMs * Math.exp(sigma * n);
        }

        delayMs += (rand.nextDouble() - 0.5) * 1.4;

        return Math.max(1L, Math.round(delayMs));
    }

    private boolean handleBreakBlockHold() {
        if (!breakBlocks.isToggled() || mc.objectMouseOver == null) {
            if (breakHeld) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                breakHeld = false;
            }
            return false;
        }
        BlockPos p = mc.objectMouseOver.getBlockPos();
        if (p == null) return false;
        Block bl = mc.theWorld.getBlockState(p).getBlock();
        if (bl == Blocks.air || bl instanceof BlockLiquid) {
            if (breakHeld) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                breakHeld = false;
            }
            return false;
        }
        if (!breakHeld) {
            int e = mc.gameSettings.keyBindAttack.getKeyCode();
            KeyBinding.setKeyBindState(e, true);
            KeyBinding.onTick(e);
            breakHeld = true;
        }
        return true;
    }

    private void tickBlockHitRelease() {
        if (!blocking) return;
        if (System.currentTimeMillis() - blockHitTime > 50L + rand.nextInt(50)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            blocking = false;
        }
    }

    private void applySmoothJitter() {
        double strength = jitterLeft.getInput();
        if (strength <= 0.0) return;

        float ampYaw   = (float) (strength * 0.30);
        float ampPitch = (float) (strength * 0.18);

        jitterPhaseYaw   += 0.035F + rand.nextFloat() * 0.03F;
        jitterPhasePitch += 0.027F + rand.nextFloat() * 0.025F;
        if (jitterPhaseYaw   > Math.PI * 4) jitterPhaseYaw   -= (float) (Math.PI * 4);
        if (jitterPhasePitch > Math.PI * 4) jitterPhasePitch -= (float) (Math.PI * 4);

        float yawTarget   = (float) Math.sin(jitterPhaseYaw)   * ampYaw;
        float pitchTarget = (float) Math.sin(jitterPhasePitch) * ampPitch;

        mc.thePlayer.rotationYaw   += Utils.Player.patchGCD(yawTarget   * 0.12F);
        mc.thePlayer.rotationPitch += Utils.Player.patchGCD(pitchTarget * 0.12F);
    }

    private void tickJitterLoop() {
        try {
            boolean breakingBlock = false;
            if (mc != null && mc.objectMouseOver != null && mc.theWorld != null) {
                BlockPos p = mc.objectMouseOver.getBlockPos();
                if (p != null) {
                    try {
                        Block bl = mc.theWorld.getBlockState(p).getBlock();
                        if (bl != null && bl != Blocks.air && !(bl instanceof BlockLiquid)) {
                            breakingBlock = true;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            boolean shouldLoop = jitterSound.isToggled()
                    && Mouse.isCreated() && Mouse.isButtonDown(0)
                    && mc != null && mc.currentScreen == null
                    && mc.inGameHasFocus
                    && !breakingBlock;

            if (shouldLoop && !jitterLoopPlaying) {
                crow.client.utils.SoundUtils.startLoop("jitterclick");
                jitterLoopPlaying = true;
                jitterCutoutEnd = 0L;
                jitterNextCutout = System.currentTimeMillis() + 600L + rand.nextInt(1400);
            } else if (!shouldLoop && jitterLoopPlaying) {
                crow.client.utils.SoundUtils.stopLoop();
                jitterLoopPlaying = false;
                jitterCutoutEnd = 0L;
                return;
            }
            if (!jitterLoopPlaying) return;

            long now = System.currentTimeMillis();
            if (jitterCutoutEnd > 0L && now < jitterCutoutEnd) return;
            if (jitterCutoutEnd > 0L) {
                crow.client.utils.SoundUtils.setLoopMuted(false);
                jitterCutoutEnd = 0L;
                jitterNextCutout = now + 400L + rand.nextInt(1200);
            }
            if (now >= jitterNextCutout) {
                if (rand.nextInt(100) < 30) {
                    jitterCutoutEnd = now + 40L + rand.nextInt(140);
                    crow.client.utils.SoundUtils.setLoopMuted(true);
                }
                jitterNextCutout = now + 400L + rand.nextInt(1200);
            }
        } catch (Throwable ignored) {
            try { crow.client.utils.SoundUtils.stopLoop(); } catch (Throwable ignored2) {}
            jitterLoopPlaying = false;
            jitterCutoutEnd = 0L;
        }
    }
}
