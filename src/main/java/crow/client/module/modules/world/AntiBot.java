package crow.client.module.modules.world;

import java.util.HashMap;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.TickEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.player.Freecam;
import crow.client.module.setting.impl.TickSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

public class AntiBot extends Module {
    private static final HashMap<EntityPlayer, Long> newEnt = new HashMap<>();
    private final long ms = 4000L;
    public static TickSetting waitTicks, dead, checkYaw;
    public static SliderSetting yawTolerance;

    public AntiBot() {
        super("AntiBot", ModuleCategory.world);
        withEnabled(true);

        this.registerSetting(waitTicks = new TickSetting("Wait 80 ticks", false));
        this.registerSetting(dead = new TickSetting("Remove dead", true));
        this.registerSetting(checkYaw = new TickSetting("Check bot Yaw", true));
        this.registerSetting(yawTolerance = new SliderSetting("Yaw Tolerance", 1.0D, 0.1D, 5.0D, 0.1D));
    }

    @Override
    public void onDisable() {
        newEnt.clear();
    }

    @Subscribe
    public void onEntityJoinWorld(ForgeEvent fe) {
        if (fe.getEvent() instanceof EntityJoinWorldEvent) {
            EntityJoinWorldEvent event = ((EntityJoinWorldEvent) fe.getEvent());

            if (!Utils.Player.isPlayerInGame())
                return;

            if (waitTicks.isToggled() && event.entity instanceof EntityPlayer && event.entity != mc.thePlayer) {
                newEnt.put((EntityPlayer) event.entity, System.currentTimeMillis());
            }
        }
    }

    @Subscribe
    public void onTick(TickEvent ev) {
        if (waitTicks.isToggled() && !newEnt.isEmpty()) {
            long cutoff = System.currentTimeMillis() - ms;
            newEnt.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
    }

    public static boolean bot(Entity en) {
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null)
            return false;
        if (Freecam.en != null && Freecam.en == en) {
            return true;
        }
        Module antiBot = Crow.moduleManager.getModuleByClazz(AntiBot.class);
        if (antiBot == null || !antiBot.isEnabled()) {
            return false;
        }

        if (en instanceof EntityPlayer && waitTicks.isToggled() && newEnt.containsKey(en)) {
            return true;
        }
        if (en.getName().startsWith("§c")) {
            return true;
        }
        if (en.isDead && dead.isToggled()) {
            return true;
        }

        if (en.getDisplayName() == null) {
            return false;
        }

        if (checkYaw.isToggled()) {
            float yawDiff = Math.abs(en.rotationYaw - mc.thePlayer.rotationYaw) % 360.0F;
            if (yawDiff > 180.0F) yawDiff = 360.0F - yawDiff;
            if (yawDiff <= yawTolerance.getInput()) {
                return true;
            }
        }

        String n = en.getDisplayName().getUnformattedText();

        if (n.contains("§")) {
            return n.contains("[NPC] ");
        }
        if (n.isEmpty() && en.getName().isEmpty()) {
            return true;
        }

        if (n.length() == 10) {
            int num = 0;
            int let = 0;
            for (char c : n.toCharArray()) {
                if (Character.isLetter(c)) {
                    if (Character.isUpperCase(c)) {
                        return false;
                    }
                    ++let;
                } else {
                    if (!Character.isDigit(c)) {
                        return false;
                    }
                    ++num;
                }
            }
            return num >= 2 && let >= 2;
        }

        return false;
    }

    public static boolean renderBot(Entity en) {
        if (!Utils.Player.isPlayerInGame()) {
            return false;
        }
        if (Freecam.en != null && Freecam.en == en) {
            return true;
        }
        Module antiBot = Crow.moduleManager.getModuleByClazz(AntiBot.class);
        if (antiBot == null || !antiBot.isEnabled()) {
            return false;
        }
        if (en == null) {
            return false;
        }
        if (en.isDead && dead.isToggled()) {
            return true;
        }
        if (en.getName().startsWith("Â§c")) {
            return true;
        }
        if (en.getDisplayName() == null) {
            return false;
        }

        String formatted = en.getDisplayName().getFormattedText();
        String unformatted = en.getDisplayName().getUnformattedText();
        String lower = unformatted == null ? "" : unformatted.toLowerCase();

        if ((formatted != null && formatted.contains("[NPC] "))
                || lower.startsWith("npc")
                || lower.contains("[npc]")) {
            return true;
        }

        return unformatted.isEmpty() && en.getName().isEmpty();
    }
}
