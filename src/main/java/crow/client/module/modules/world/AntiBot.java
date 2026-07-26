package crow.client.module.modules.world;

import java.util.HashMap;
import java.util.Locale;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.TickEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.player.Freecam;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

public class AntiBot extends Module {
    private static final HashMap<EntityPlayer, Long> newEnt = new HashMap<>();
    private final long ms = 4000L;
    public static TickSetting waitTicks, dead;

    public AntiBot() {
        super("AntiBot", ModuleCategory.world);
        withEnabled(true);

        this.registerSetting(waitTicks = new TickSetting("Wait 80t", false));
        this.registerSetting(dead = new TickSetting("Hide dead", true));
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
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null || en == null) {
            return false;
        }
        if (Freecam.en != null && Freecam.en == en) {
            return true;
        }
        Module antiBot = Crow.moduleManager.getModuleByClazz(AntiBot.class);
        if (antiBot == null || !antiBot.isEnabled()) {
            return false;
        }

        if (isWithinJoinGrace(en)) {
            return true;
        }
        if (en.isDead && dead.isToggled()) {
            return true;
        }
        return hasNpcTagOrEmptyName(en);
    }

    public static boolean renderBot(Entity en) {
        if (!Utils.Player.isPlayerInGame() || en == null) {
            return false;
        }
        if (Freecam.en != null && Freecam.en == en) {
            return true;
        }
        Module antiBot = Crow.moduleManager.getModuleByClazz(AntiBot.class);
        if (antiBot == null || !antiBot.isEnabled()) {
            return false;
        }

        if (isWithinJoinGrace(en)) {
            return true;
        }
        if (en.isDead && dead.isToggled()) {
            return true;
        }
        return hasNpcTagOrEmptyName(en);
    }

    private static boolean isWithinJoinGrace(Entity en) {
        return en instanceof EntityPlayer
                && waitTicks.isToggled()
                && newEnt.containsKey(en);
    }

    private static boolean hasNpcTagOrEmptyName(Entity en) {
        String entityName = en.getName();
        if (en.getDisplayName() == null) {
            return isEmpty(entityName);
        }

        String displayName = en.getDisplayName().getUnformattedText();
        if (displayName != null
                && displayName.toLowerCase(Locale.ROOT).contains("[npc]")) {
            return true;
        }
        return isEmpty(displayName) && isEmpty(entityName);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
