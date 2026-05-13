package crow.client.module.modules.player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.input.Mouse;

public class AutoArmour extends Module {

    private final DoubleSliderSetting startDelay;
    private final DoubleSliderSetting actionDelay;
    private final ComboSetting mode;
    private final TickSetting antiVoid;
    private final TickSetting preferProt;
    private final SliderSetting actionCap;

    public enum ArmorMode { Inventory, Always }

    private boolean inInv;
    private final CoolDown timer = new CoolDown(0);

    private final CoolDown rescanCooldown = new CoolDown(0);
    private boolean rescanArmed;
    private final List<ArmorAction> actionQueue = new ArrayList<>();
    private int actionsThisTick;

    /**
     * Last time the player did anything click-like (attack, use-item,
     * mouse held). Action execution defers until this window has elapsed
     * so a windowClick packet never lands adjacent to an AttackInteract
     * or PlayerDigging — that adjacency is a common anticheat heuristic
     * for "inventory bot" flags.
     */
    private long lastClickMs;
    private static final long POST_CLICK_WINDOW_MS = 320L;

    private static final int[] ARMOR_SLOTS = { 5, 6, 7, 8 };

    private static final int HELMET = 0, CHESTPLATE = 1, LEGGINGS = 2, BOOTS = 3;

    public AutoArmour() {
        super("AutoArmor", ModuleCategory.player);
        this.registerSetting(mode = new ComboSetting("Mode", ArmorMode.Inventory));
        this.registerSetting(startDelay = new DoubleSliderSetting("Start delay (ms)", 50, 150, 0, 500, 1));
        this.registerSetting(actionDelay = new DoubleSliderSetting("Action delay (ms)", 50, 120, 0, 500, 1));
        this.registerSetting(preferProt = new TickSetting("Prefer Protection", true));
        this.registerSetting(antiVoid = new TickSetting("Don't remove current", false));
        this.registerSetting(actionCap = new SliderSetting("Actions per open", 0, 0, 20, 1));
    }

    @Override
    public String getHudSuffix() {
        return toTitleCase(((ArmorMode) mode.getMode()).name());
    }

    @Subscribe
    public void onAttackOrUse(ForgeEvent fe) {
        // Catches the two highest-flag-risk events for back-to-back inventory
        // actions: AttackInteract (left-click on entity) and PlayerInteract
        // (right-click world/block/entity, item use). Updating lastClickMs
        // here covers cases where the player taps the button so briefly that
        // the onRender2D Mouse.isButtonDown poll misses it.
        Object event = fe.getEvent();
        if (event instanceof AttackEntityEvent
                || event instanceof PlayerInteractEvent) {
            lastClickMs = System.currentTimeMillis();
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;

        // Poll mouse state every frame so held-down clicks (auto-clicker,
        // sustained sword swing) keep the post-click window armed for as
        // long as the player is interacting.
        if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) {
            lastClickMs = System.currentTimeMillis();
        }

        ArmorMode currentMode = (ArmorMode) mode.getMode();

        boolean shouldRun;
        if (currentMode == ArmorMode.Always) {
            shouldRun = mc.thePlayer.openContainer instanceof ContainerPlayer;
        } else {
            shouldRun = Utils.Player.isPlayerInInventory();
        }

        if (shouldRun && mc.thePlayer.openContainer instanceof ContainerPlayer) {
            ContainerPlayer inv = (ContainerPlayer) mc.thePlayer.openContainer;

            if (!inInv) {
                inInv = true;
                actionsThisTick = 0;
                buildActionQueue(inv);
                timer.setCooldown(randomBetween(startDelay));
                timer.start();
                rescanArmed = false;
            }

            // Gate action execution on the post-click window. Action stays
            // queued; we simply skip this frame and resume on a later one
            // once the player has been idle long enough.
            if (System.currentTimeMillis() - lastClickMs < POST_CLICK_WINDOW_MS) {
                return;
            }

            if (!actionQueue.isEmpty() && timer.hasFinished()) {
                if ((int) actionCap.getInput() > 0 && actionsThisTick >= (int) actionCap.getInput()) {
                    actionQueue.clear();
                    return;
                }

                ArmorAction action = actionQueue.remove(0);
                executeAction(inv, action);
                actionsThisTick++;

                timer.setCooldown(randomBetween(actionDelay));
                timer.start();
            } else if (actionQueue.isEmpty() && currentMode == ArmorMode.Always) {

                if (!rescanArmed) {
                    long delay = 800L + ThreadLocalRandom.current().nextInt(700);
                    rescanCooldown.setCooldown(delay);
                    rescanCooldown.start();
                    rescanArmed = true;
                } else if (rescanCooldown.hasFinished()) {
                    actionsThisTick = 0;
                    buildActionQueue(inv);
                    if (!actionQueue.isEmpty()) {
                        timer.setCooldown(randomBetween(startDelay));
                        timer.start();
                    }
                    rescanArmed = false;
                }
            }
        } else {
            if (inInv) {
                inInv = false;
                actionQueue.clear();
                rescanArmed = false;
            }
        }
    }

    private void buildActionQueue(ContainerPlayer inv) {
        actionQueue.clear();

        ArmorCandidate[] best = new ArmorCandidate[4];
        ArmorCandidate[] current = new ArmorCandidate[4];

        for (int i = 0; i < 4; i++) {
            ItemStack equipped = inv.getSlot(ARMOR_SLOTS[i]).getStack();
            if (equipped != null && equipped.getItem() instanceof ItemArmor) {
                current[i] = new ArmorCandidate(ARMOR_SLOTS[i], equipped);
                best[i] = current[i];
            }
        }

        for (int slot = 9; slot < 45; slot++) {
            ItemStack stack = inv.getSlot(slot).getStack();
            if (stack == null || !(stack.getItem() instanceof ItemArmor)) continue;

            ItemArmor armor = (ItemArmor) stack.getItem();
            int type = armor.armorType;
            if (type < 0 || type > 3) continue;

            ArmorCandidate candidate = new ArmorCandidate(slot, stack);

            if (best[type] == null || candidate.score > best[type].score) {
                best[type] = candidate;
            }
        }

        for (int i = 0; i < 4; i++) {
            if (best[i] == null) continue;
            if (best[i].slot == ARMOR_SLOTS[i]) continue;

            if (current[i] != null) {
                if (antiVoid.isToggled()) continue;

                actionQueue.add(new ArmorAction(ARMOR_SLOTS[i], ActionType.SHIFT_CLICK));
            }

            actionQueue.add(new ArmorAction(best[i].slot, ActionType.SHIFT_CLICK));
        }
    }

    private void executeAction(ContainerPlayer inv, ArmorAction action) {
        switch (action.type) {
            case SHIFT_CLICK:
                mc.playerController.windowClick(inv.windowId, action.slot, 0, 1, mc.thePlayer);
                break;
        }
    }

    private float getArmorScore(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemArmor)) return 0;
        ItemArmor armor = (ItemArmor) stack.getItem();

        float score = armor.damageReduceAmount;

        int protLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
        int projProtLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.projectileProtection.effectId, stack);
        int blastProtLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.blastProtection.effectId, stack);
        int fireProtLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.fireProtection.effectId, stack);
        int thornsLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.thorns.effectId, stack);
        int unbreakingLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);

        if (preferProt.isToggled()) {
            score += protLevel * 1.5F;
        } else {
            score += protLevel * 0.8F;
        }
        score += projProtLevel * 0.4F;
        score += blastProtLevel * 0.3F;
        score += fireProtLevel * 0.3F;
        score += thornsLevel * 0.2F;
        score += unbreakingLevel * 0.1F;

        float durabilityFraction = 1.0F - ((float) stack.getItemDamage() / (float) stack.getMaxDamage());
        score += durabilityFraction * 0.5F;

        return score;
    }

    private long randomBetween(DoubleSliderSetting setting) {
        double min = setting.getInputMin();
        double max = setting.getInputMax();
        if (min >= max) return (long) min;
        return (long) ThreadLocalRandom.current().nextDouble(min, max + 0.01);
    }

    private class ArmorCandidate {
        final int slot;
        final float score;

        ArmorCandidate(int slot, ItemStack stack) {
            this.slot = slot;
            this.score = getArmorScore(stack);
        }
    }

    private enum ActionType { SHIFT_CLICK }

    private static class ArmorAction {
        final int slot;
        final ActionType type;

        ArmorAction(int slot, ActionType type) {
            this.slot = slot;
            this.type = type;
        }
    }
}
