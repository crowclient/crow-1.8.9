package crow.client.module.modules.client;

import java.util.Comparator;
import java.util.List;

import com.google.common.eventbus.Subscribe;

import crow.client.event.impl.ForgeEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.combat.AimAssist;
import crow.client.module.modules.world.AntiBot;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

public class Targets extends Module {

    private static TickSetting players, mobs, friends, teams, invis, bots, naked;
    private static SliderSetting fov, distance, lockDist;
    private static ComboSetting<SortMode> sortMode;

    public static EntityPlayer lockedTarget;

    public Targets() {
        super("Targets", ModuleCategory.client);
        this.registerSettings(
                        players = new TickSetting("Players", true),
                        mobs = new TickSetting("Mobs", false),
                        friends = new TickSetting("Friends", false),
                        teams = new TickSetting("Teammates", false),
                        invis = new TickSetting("Invis", false),
                        bots = new TickSetting("Bots", false),
                        naked = new TickSetting("Naked", false),
                        fov = new SliderSetting("Fov", 30, 0, 360, 1),
                        distance = new SliderSetting("Distance", 3.5, 0, 10, 0.1),
                        sortMode = new ComboSetting("Sort", SortMode.Distance),
                        lockDist = new SliderSetting("Lock dist", 4, 0, 10, 0.1)
                        );
        this.withEnabled(true);
    }

    @Override
    public boolean canBeEnabled() {
        return true;
    }

    @Override
    public void postApplyConfig() {
        guiButtonToggled(sortMode);
    }

    @Override
    public void guiButtonToggled(Setting b) {
        if(b == sortMode) {
            try {
                lockDist.hideComponent(sortMode.getMode() != SortMode.Lock);
            } catch (NullPointerException ignored) {
            }
        }
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof AttackEntityEvent) {
            AttackEntityEvent e = ((AttackEntityEvent) fe.getEvent());
            lockedTarget = e.target instanceof EntityPlayer ? (EntityPlayer) e.target : lockedTarget;
        }
    }

    public static EntityPlayer getTarget() {
        return getTarget(distance.getInput());
    }

    public static EntityPlayer getTarget(double maxDistance) {
        if (!players.isToggled()) {
            return null;
        }

        List<EntityPlayer> en = Utils.Player.getClosePlayers(maxDistance);
        if (en == null) {
            return null;
        }
        en.removeIf(player -> !isValidTarget(player, maxDistance));
        return en.isEmpty() ? null : en.stream().min(Comparator.comparingDouble(target -> sortMode.getMode().sv.value(target))).orElse(null);
    }

    public static EntityLivingBase getTargetEntity() {
        return getTargetEntity(distance.getInput());
    }

    public static EntityLivingBase getTargetEntity(double maxDistance) {
        EntityPlayer playerTarget = getTarget(maxDistance);
        if (playerTarget != null) {
            return playerTarget;
        }

        if (!mobs.isToggled() || mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }

        return mc.theWorld.loadedEntityList.stream()
                .filter(EntityLivingBase.class::isInstance)
                .map(EntityLivingBase.class::cast)
                .filter(target -> isValidMobTarget(target, maxDistance))
                .min(Comparator.comparingDouble(target -> sortMode.getMode().sv.value(target)))
                .orElse(null);
    }

    public static boolean isValidTarget(EntityPlayer ep) {
        return isValidTarget(ep, distance.getInput());
    }

    public static boolean isValidTarget(EntityPlayer ep, double maxDistance) {
        return (
                (ep != null)
                &&
                (ep != mc.thePlayer)
                && players.isToggled()
                && (bots.isToggled() || !AntiBot.bot(ep))
                && (friends.isToggled() || !isAFriend(ep))
                && (teams.isToggled() || !isATeamMate(ep))
                && (invis.isToggled() || !ep.isInvisible())
                && (naked.isToggled() || !Utils.Player.isPlayerNaked(ep))
                && mc.thePlayer.getDistanceToEntity(ep) <= maxDistance
                && Utils.Player.fov(ep, (float) fov.getInput())
                );
    }

    public static boolean isValidMobTarget(EntityLivingBase entity) {
        return isValidMobTarget(entity, distance.getInput());
    }

    public static boolean isValidMobTarget(EntityLivingBase entity, double maxDistance) {
        return entity != null
                && entity != mc.thePlayer
                && entity.isEntityAlive()
                && entity instanceof IMob
                && !entity.isInvisibleToPlayer(mc.thePlayer)
                && (invis.isToggled() || !entity.isInvisible())
                && mc.thePlayer.getDistanceToEntity(entity) <= maxDistance
                && Utils.Player.fov(entity, (float) fov.getInput());
    }

    public static double getDistanceSetting() {
        return distance == null ? 3.5D : distance.getInput();
    }

    public static boolean isTargetingPlayers() {
        return players != null && players.isToggled();
    }

    public static boolean isTargetingMobs() {
        return mobs != null && mobs.isToggled();
    }

    public static boolean isValidTargetNoFov(EntityLivingBase entity, double maxDistance) {
        if (entity == null || entity == mc.thePlayer || !entity.isEntityAlive()) return false;
        if (mc.thePlayer.getDistanceToEntity(entity) > maxDistance) return false;

        if (entity instanceof EntityPlayer) {
            EntityPlayer ep = (EntityPlayer) entity;
            return players.isToggled()
                    && (bots.isToggled() || !AntiBot.bot(ep))
                    && (friends.isToggled() || !isAFriend(ep))
                    && (teams.isToggled() || !isATeamMate(ep))
                    && (invis.isToggled() || !ep.isInvisible())
                    && (naked.isToggled() || !Utils.Player.isPlayerNaked(ep));
        } else if (entity instanceof IMob) {
            return mobs.isToggled()
                    && (invis.isToggled() || !entity.isInvisible());
        }
        return false;
    }

    public static boolean isAFriend(Entity entity) {
        if (entity == mc.thePlayer)
            return true;

        for (Entity wut : AimAssist.friends)
            if (wut.equals(entity))
                return true;
        return false;
    }

    public static boolean isATeamMate(Entity entity) {
        try {
            EntityPlayer bruhentity = (EntityPlayer) entity;
            if (Crow.debugger) {
                Utils.Player.sendMessageToSelf(
                        "unformatted / " + bruhentity.getDisplayName().getUnformattedText().replace("§", "%"));
            }

            net.minecraft.scoreboard.Team myTeam = mc.thePlayer.getTeam();
            net.minecraft.scoreboard.Team theirTeam = bruhentity.getTeam();
            if (myTeam != null && theirTeam != null) {

                String myTeamName = myTeam.getRegisteredName();
                String theirTeamName = theirTeam.getRegisteredName();
                if (myTeamName != null && !myTeamName.isEmpty()
                        && theirTeamName != null && !theirTeamName.isEmpty()
                        && myTeamName.equals(theirTeamName)) {
                    return true;
                }
            }

            String myName = mc.thePlayer.getDisplayName().getFormattedText();
            String targetName = bruhentity.getDisplayName().getFormattedText();
            if (myName.startsWith("§") && targetName.startsWith("§")) {

                String myPrefix = extractColorPrefix(myName);
                String theirPrefix = extractColorPrefix(targetName);

                if (myPrefix.length() > 2 && myPrefix.equals(theirPrefix)) {
                    return true;
                }
            }
        } catch (Exception fhwhfhwe) {
            if (Crow.debugger)
                Utils.Player.sendMessageToSelf(fhwhfhwe.getMessage());
        }
        return false;
    }

    private static String extractColorPrefix(String formatted) {
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < formatted.length() - 1; i++) {
            if (formatted.charAt(i) == '§') {
                prefix.append('§').append(formatted.charAt(i + 1));
                i++;
            } else {
                break;
            }
        }
        return prefix.toString();
    }

    public static EntityLivingBase getTargetEntityNoFov(double maxDistance) {
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        EntityLivingBase best = null;
        float bestValue = Float.MAX_VALUE;

        for (Object obj : new java.util.ArrayList<>(mc.theWorld.loadedEntityList)) {
            if (!(obj instanceof EntityLivingBase)) continue;
            EntityLivingBase e = (EntityLivingBase) obj;
            if (e == mc.thePlayer || !e.isEntityAlive()) continue;
            double dist = mc.thePlayer.getDistanceToEntity(e);
            if (dist > maxDistance) continue;

            boolean valid;
            if (e instanceof EntityPlayer) {
                EntityPlayer ep = (EntityPlayer) e;
                valid = players.isToggled()
                    && (bots.isToggled()    || !AntiBot.bot(ep))
                    && (friends.isToggled() || !isAFriend(ep))
                    && (teams.isToggled()   || !isATeamMate(ep))
                    && (invis.isToggled()   || !ep.isInvisible())
                    && (naked.isToggled()   || !Utils.Player.isPlayerNaked(ep));
            } else if (e instanceof IMob) {
                valid = mobs.isToggled()
                    && !e.isInvisibleToPlayer(mc.thePlayer)
                    && (invis.isToggled() || !e.isInvisible());
            } else {
                continue;
            }
            if (valid) {
                float sortVal = sortMode.getMode().sv.value(e);
                if (sortVal < bestValue) {
                    best = e;
                    bestValue = sortVal;
                }
            }
        }
        return best;
    }

    public enum SortMode {
        Distance(player -> mc.thePlayer.getDistanceToEntity(player)),
        Health(player -> player.getHealth()),
        Hurttime(player -> (float) player.hurtTime),
        Fov(player -> Math.abs(Utils.Player.fovFromEntityf(player))),
        Lock(player -> player == lockedTarget ? 0f : 1f);

        private final SortValue sv;

        private SortMode(SortValue sv) {
            this.sv = sv;
        }

        public SortValue getSortValue() {
            return sv;
        }
    }

    @FunctionalInterface
    private interface SortValue {
        Float value(EntityLivingBase player);
    }

}
