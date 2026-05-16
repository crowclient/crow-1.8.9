package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class WTap extends Module {
    public ComboSetting eventType;
    public SliderSetting range, chance, tapMultiplier;
    public TickSetting onlyPlayers;
    public TickSetting onlySword;
    public TickSetting dynamic;
    public DoubleSliderSetting waitMs;
    public DoubleSliderSetting actionMs;
    public DoubleSliderSetting hitPer;

    private int hits = 0;
    private int rhit = 1;
    private boolean call;
    private WtapState state = WtapState.NONE;
    private final CoolDown timer = new CoolDown(0);
    private Entity target;
    private boolean wasPressed;

    public WTap() {
        super("WTap", ModuleCategory.combat);

        this.registerSetting(eventType = new ComboSetting("Event", EventType.Attack));
        this.registerSetting(onlyPlayers = new TickSetting("Players only", true));
        this.registerSetting(onlySword = new TickSetting("Sword only", false));

        this.registerSetting(waitMs = new DoubleSliderSetting("Release ms", 30, 40, 1, 300, 1));
        this.registerSetting(actionMs = new DoubleSliderSetting("Tap after", 20, 30, 1, 300, 1));
        this.registerSetting(hitPer = new DoubleSliderSetting("Every N hits", 1, 1, 1, 10, 1));
        this.registerSetting(chance = new SliderSetting("Chance %", 100, 0, 100, 1));
        this.registerSetting(range = new SliderSetting("Range", 3, 1, 6, 0.05));

        this.registerSetting(dynamic = new TickSetting("Dynamic", false));
        this.registerSetting(tapMultiplier = new SliderSetting("Wait sens", 1F, 0F, 5F, 0.1F));
    }

    @Override
    public void onDisable() {
        if (state != WtapState.NONE) {
            finishCombo();
        }
        hits = 0;
        rhit = 1;
        call = false;
        target = null;
        state = WtapState.NONE;
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (state == WtapState.NONE) return;
        if (state == WtapState.WAITINGTOTAP && timer.hasFinished()) {
            startCombo();
        } else if (state == WtapState.TAPPING && timer.hasFinished()) {
            finishCombo();
        }
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof AttackEntityEvent) {
            AttackEntityEvent e = ((AttackEntityEvent) fe.getEvent());
            target = e.target;
            if (isSecondCall() && eventType.getMode() == EventType.Attack)
                wTap();
        } else if (fe.getEvent() instanceof LivingUpdateEvent) {
            LivingUpdateEvent e = ((LivingUpdateEvent) fe.getEvent());
            if (eventType.getMode() == EventType.Hurt && e.entityLiving.hurtTime > 0
                    && e.entityLiving.hurtTime == e.entityLiving.maxHurtTime && e.entity == this.target)
                wTap();
        }
    }

    private void wTap() {
        if (target == null || mc.thePlayer == null) return;
        if (state != WtapState.NONE) return;
        if (mc.thePlayer.getDistanceToEntity(target) > range.getInput()) return;
        if (onlyPlayers.isToggled() && !(target instanceof EntityPlayer)) return;
        if (onlySword.isToggled() && !Utils.Player.isPlayerHoldingSword()) return;
        if (!Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) return;

        hits++;

        if (chance.getInput() < 100 && Math.random() * 100 >= chance.getInput()) return;

        if (hits < rhit) return;

        trystartCombo();
    }

    private void finishCombo() {
        if (wasPressed || Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        }
        state = WtapState.NONE;
        hits = 0;
        rerollHitTarget();
    }

    private void startCombo() {
        state = WtapState.TAPPING;
        wasPressed = Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode());
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        double cd = ThreadLocalRandom.current().nextDouble(waitMs.getInputMin(), waitMs.getInputMax() + 0.01);

        if (dynamic.isToggled() && mc.thePlayer != null && target != null) {
            double dist = mc.thePlayer.getDistanceToEntity(target);
            if (dist < 3.0) {
                double closeness = 3.0 - dist;
                cd += closeness * tapMultiplier.getInput() * 10.0;
            }
        }

        timer.setCooldown(Math.max(1L, (long) cd));
        timer.start();
    }

    private void trystartCombo() {
        state = WtapState.WAITINGTOTAP;
        timer.setCooldown(
                (long) ThreadLocalRandom.current().nextDouble(actionMs.getInputMin(), actionMs.getInputMax() + 0.01));
        timer.start();
    }

    private void rerollHitTarget() {
        int range = (int) (hitPer.getInputMax() - hitPer.getInputMin()) + 1;
        rhit = ThreadLocalRandom.current().nextInt(range) + (int) hitPer.getInputMin();
    }

    private boolean isSecondCall() {
        if (call) {
            call = false;
            return true;
        } else {
            call = true;
            return false;
        }
    }

    public enum EventType {
        Attack, Hurt,
    }

    public enum WtapState {
        NONE, WAITINGTOTAP, TAPPING
    }
}
