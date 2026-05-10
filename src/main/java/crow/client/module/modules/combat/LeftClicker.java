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
    public static TickSetting weaponOnly, breakBlocks;
    public static TickSetting jitterSound;

    public static boolean autoClickerEnabled;
    public EntityLivingBase target;

    private final Random rand = new Random();

    private long nextClickAt;

    private final Deque<Long> clickHistory = new ArrayDeque<>();

    private double measuredCps;

    private int lastClickTick = -1;

    private boolean breakHeld;

    private float jitterPhaseYaw, jitterPhasePitch;

    private boolean jitterLoopPlaying;
    private long jitterCutoutEnd;
    private long jitterNextCutout;

    private long lastAttackMs;

    public LeftClicker() {
        super("Auto Clicker", ModuleCategory.combat);
        this.registerSetting(leftCPS     = new DoubleSliderSetting("Left CPS", 9, 13, 1, 60, 0.5));
        this.registerSetting(jitterLeft  = new SliderSetting("Jitter left", 0.0D, 0.0D, 3.0D, 0.1D));
        this.registerSetting(weaponOnly  = new TickSetting("Weapon only", false));
        this.registerSetting(breakBlocks = new TickSetting("Break blocks", false));
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
        breakHeld = false;
        target = null;
        jitterLoopPlaying = false;
        jitterCutoutEnd = 0L;
        jitterNextCutout = 0L;
        lastAttackMs = 0L;
        autoClickerEnabled = true;
    }

    @Override
    public void onDisable() {
        nextClickAt = 0L;
        clickHistory.clear();
        measuredCps = 0.0;
        lastClickTick = -1;
        breakHeld = false;
        target = null;
        jitterLoopPlaying = false;
        lastAttackMs = 0L;
        autoClickerEnabled = false;
        try { crow.client.utils.SoundUtils.stopLoop(); } catch (Throwable ignored) {}
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        }
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        Object event = fe.getEvent();

        if (event instanceof AttackEntityEvent) {
            target = ((AttackEntityEvent) event).entityLiving;
            lastAttackMs = System.currentTimeMillis();
            return;
        }

        if (event instanceof TickEvent.RenderTickEvent) {
            TickEvent.RenderTickEvent ev = (TickEvent.RenderTickEvent) event;
            if (ev.phase == TickEvent.Phase.START) {
                tickJitterLoop();
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
            lastClickTick = -1;
            return;
        }

        long now = System.currentTimeMillis();

        if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) return;
        if (handleBreakBlockHold()) return;
        applySmoothJitter();

        if (nextClickAt != 0L && now < nextClickAt) return;

        // Tick-gate only when configured CPS is at or below 20 — above 20 is
        // jitter-click territory and needs multiple clicks per game tick.
        if (leftCPS.getInputMax() <= 20.0
                && lastClickTick == mc.thePlayer.ticksExisted) {
            return;
        }

        fireClick();
        lastClickTick = mc.thePlayer.ticksExisted;
        clickHistory.addLast(now);
        nextClickAt = now + sampleClickIntervalMs();
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

    /**
     * Sample the next click interval so that the long-run measured CPS
     * matches the slider exactly. CPS is drawn uniformly from [min, max] each
     * click; a small Gaussian wobble (±~6%) on the resulting interval keeps
     * consecutive intervals from being identical without biasing the mean.
     */
    private long sampleClickIntervalMs() {
        double lo = leftCPS.getInputMin();
        double hi = leftCPS.getInputMax();
        if (lo > hi) { double t = lo; lo = hi; hi = t; }
        if (lo < 0.5) lo = 0.5;
        if (hi < lo) hi = lo;

        double cps = lo + rand.nextDouble() * (hi - lo);
        double meanDelayMs = 1000.0 / cps;

        // Light symmetric jitter — log-normal with small sigma so E[delay] ≈ mean.
        double sigma = 0.06;
        double delayMs = meanDelayMs * Math.exp(sigma * rand.nextGaussian() - 0.5 * sigma * sigma);

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
