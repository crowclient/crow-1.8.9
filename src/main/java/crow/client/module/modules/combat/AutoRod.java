package crow.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.DoubleSliderSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.CoolDown;
import crow.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import java.util.concurrent.ThreadLocalRandom;

public class AutoRod extends Module {

    private final SliderSetting range;
    private final SliderSetting chance;
    private final DoubleSliderSetting delay;
    private final TickSetting onlyPlayers;
    private final TickSetting onlySword;
    private final TickSetting requireSprint;

    private Entity target;
    private long lastTargetHitTime;
    private final CoolDown rodCooldown = new CoolDown(0);
    private final CoolDown switchBackDelay = new CoolDown(0);
    private RodState state = RodState.IDLE;
    private int previousSlot = -1;
    private int rodSlot = -1;
    private int reelTicks = 0;

    private static final long COMBAT_TIMEOUT_MS = 3000;
    private static final int REEL_WAIT_TICKS = 8;

    private enum RodState {
        IDLE,
        SWITCHING,
        THROWING,
        WAITING_REEL,
        REELING,
        SWITCHING_BACK
    }

    public AutoRod() {
        super("AutoRod", ModuleCategory.combat);
        this.registerSetting(range = new SliderSetting("Range", 4.0D, 2.0D, 8.0D, 0.5D));
        this.registerSetting(delay = new DoubleSliderSetting("Delay", 800, 1500, 300, 5000, 50));
        this.registerSetting(chance = new SliderSetting("Chance %", 100, 0, 100, 1));
        this.registerSetting(onlyPlayers = new TickSetting("Players only", true));
        this.registerSetting(onlySword = new TickSetting("Sword only", true));
        this.registerSetting(requireSprint = new TickSetting("Need sprint", false));
    }

    @Override
    public void onEnable() {
        state = RodState.IDLE;
        target = null;
        previousSlot = -1;
        rodSlot = -1;
    }

    @Override
    public void onDisable() {

        if (state != RodState.IDLE && previousSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        state = RodState.IDLE;
        target = null;
        previousSlot = -1;
        rodSlot = -1;
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof AttackEntityEvent)) return;
        AttackEntityEvent e = (AttackEntityEvent) fe.getEvent();
        if (!Utils.Player.isPlayerInGame()) return;

        target = e.target;
        lastTargetHitTime = System.currentTimeMillis();
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;

        if (target != null && System.currentTimeMillis() - lastTargetHitTime > COMBAT_TIMEOUT_MS) {
            target = null;
        }

        switch (state) {
            case IDLE:
                handleIdle();
                break;
            case SWITCHING:
                handleSwitching();
                break;
            case THROWING:
                handleThrowing();
                break;
            case WAITING_REEL:
                handleWaitingReel();
                break;
            case REELING:
                handleReeling();
                break;
            case SWITCHING_BACK:
                handleSwitchingBack();
                break;
        }
    }

    private void handleIdle() {
        if (!rodCooldown.hasFinished()) return;
        if (target == null || !target.isEntityAlive()) return;
        if (onlyPlayers.isToggled() && !(target instanceof EntityPlayer)) return;
        if (mc.thePlayer.getDistanceToEntity(target) > range.getInput()) return;
        if (requireSprint.isToggled() && !mc.thePlayer.isSprinting()) return;

        if (onlySword.isToggled() && !Utils.Player.isPlayerHoldingSword()) return;

        if (ThreadLocalRandom.current().nextDouble(100) >= chance.getInput()) {

            rodCooldown.setCooldown((long) ThreadLocalRandom.current().nextDouble(
                    delay.getInputMin(), delay.getInputMax() + 1));
            rodCooldown.start();
            return;
        }

        rodSlot = findRodSlot();
        if (rodSlot == -1) return;

        previousSlot = mc.thePlayer.inventory.currentItem;

        if (previousSlot == rodSlot) {
            state = RodState.THROWING;
        } else {
            mc.thePlayer.inventory.currentItem = rodSlot;
            state = RodState.SWITCHING;
            switchBackDelay.setCooldown(50);
            switchBackDelay.start();
        }
    }

    private void handleSwitching() {
        if (!switchBackDelay.hasFinished()) return;
        state = RodState.THROWING;
    }

    private void handleThrowing() {

        if (mc.thePlayer.fishEntity == null) {

            ItemStack rodStack = mc.thePlayer.getHeldItem();
        if (rodStack != null) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, rodStack);
        }
        }
        reelTicks = 0;
        state = RodState.WAITING_REEL;
    }

    private void handleWaitingReel() {
        reelTicks++;
        if (reelTicks >= REEL_WAIT_TICKS) {
            state = RodState.REELING;
        }
    }

    private void handleReeling() {

        if (mc.thePlayer.fishEntity != null) {
            ItemStack rodStack = mc.thePlayer.getHeldItem();
        if (rodStack != null) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, rodStack);
        }
        }

        state = RodState.SWITCHING_BACK;
        switchBackDelay.setCooldown(50);
        switchBackDelay.start();
    }

    private void handleSwitchingBack() {
        if (!switchBackDelay.hasFinished()) return;

        if (previousSlot != -1 && previousSlot != rodSlot) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }

        rodCooldown.setCooldown((long) ThreadLocalRandom.current().nextDouble(
                delay.getInputMin(), delay.getInputMax() + 1));
        rodCooldown.start();

        state = RodState.IDLE;
        previousSlot = -1;
        rodSlot = -1;
    }

    private int findRodSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFishingRod) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String getHudSuffix() {
        if (target != null && target.isEntityAlive() && state != RodState.IDLE) {
            return "Active";
        }
        return "";
    }
}
