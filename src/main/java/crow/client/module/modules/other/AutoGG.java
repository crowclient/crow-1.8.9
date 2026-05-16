package crow.client.module.modules.other;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoGG extends Module {

    private enum GGMessage { gg, GG, Good_Game, gg_wp }

    private final ComboSetting<GGMessage> message;
    private final SliderSetting delay;
    private final TickSetting randomDelay;

    private final Random random = new Random();
    private ScheduledExecutorService scheduler;
    private long lastGGTime = 0;

    private static final String[] GAME_END_PATTERNS = {

            "1st Killer -",
            "1st Place -",
            "Winner:",
            "Winners:",
            "Winner -",
            "Winners -",
            " - Loss",
            " - Win",
            "Top Seeker:",
            "1st -",
            "2nd -",
            "3rd -",
            "Winning Team -",
            "Game Over!",
            "GAME OVER!",

            "BedWars Experience Summary",
            "Bed Wars Experience (",

            "SkyWars Experience Summary",
            "coins! (Win)",

            "Duels Experience Summary",

            "Murder Mystery Experience Summary",

            "Build Battle Experience Summary",

            "You won!",
            "You lost!",
            "The game has ended!",
            "has won the game!",
            "game over",
            "VICTORY!",
            "DEFEAT!",

            "+50 coins! (Win)",
            "+25 coins! (Kill)",
            "Reward Summary",
    };

    public AutoGG() {
        super("AutoGG", ModuleCategory.other);
        this.withDescription("Sends GG in chat when a game ends.");
        this.registerSetting(message = new ComboSetting<>("Message", GGMessage.gg));
        this.registerSetting(delay = new SliderSetting("Delay", 500, 0, 3000, 50));
        this.registerSetting(randomDelay = new TickSetting("Rand delay", true));
        randomDelay.visibleWhen(() -> delay != null && delay.getInput() > 0);
    }

    @Override
    public void onEnable() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        lastGGTime = 0;
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    @Override
    public String getHudSuffix() {
        return formatMessage((GGMessage) message.getMode());
    }

    @Subscribe
    public void onChat(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof ClientChatReceivedEvent)) return;
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return;

        ClientChatReceivedEvent event = (ClientChatReceivedEvent) fe.getEvent();
        if (event.type == 2) return;

        String msg = event.message.getUnformattedText();
        if (msg == null || msg.isEmpty()) return;

        if (isGameEndMessage(msg)) {

            long now = System.currentTimeMillis();
            if (now - lastGGTime < 5000) return;
            lastGGTime = now;
            scheduleMessage();
        }
    }

    private boolean isGameEndMessage(String msg) {
        for (String pattern : GAME_END_PATTERNS) {
            if (msg.contains(pattern)) {
                return true;
            }
        }

        String lower = msg.toLowerCase();
        if (lower.contains("game over") || lower.contains("victory!") || lower.contains("defeat!")) {
            return true;
        }
        return false;
    }

    private void scheduleMessage() {
        long baseDelay = (long) delay.getInput();
        long extra = randomDelay.isToggled() ? random.nextInt(Math.max(1, (int) (baseDelay * 0.4 + 100))) : 0;
        long totalDelay = baseDelay + extra;

        scheduler.schedule(() -> {
            if (mc.thePlayer != null && isEnabled()) {
                mc.thePlayer.sendChatMessage(formatMessage((GGMessage) message.getMode()));
            }
        }, totalDelay, TimeUnit.MILLISECONDS);
    }

    private String formatMessage(GGMessage m) {
        switch (m) {
            case gg: return "gg";
            case GG: return "GG";
            case Good_Game: return "Good Game";
            case gg_wp: return "gg wp";
            default: return "gg";
        }
    }
}
