package crow.client.module.modules.other;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import crow.client.module.Module;
import crow.client.module.setting.impl.TextSetting;
import crow.client.module.setting.impl.TickSetting;
import net.minecraft.entity.player.EntityPlayer;

public class NameHider extends Module {
    public static TextSetting customName;
    public static TickSetting randomizeOthers;
    public static String playerNick = "";

    private static final Map<String, String> FAKE_NAME_CACHE = new HashMap<>();
    private static NameHider instance;

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, String> FRAME_CACHE = new ConcurrentHashMap<>();
    private static long lastFrameCacheCleared = 0;

    public NameHider() {
        super("Name Hider", ModuleCategory.other);
        instance = this;
        this.withDescription("Replaces names client-side for privacy while streaming or recording.");
        this.registerSetting(customName = new TextSetting("Custom name", "You", "Enter replacement name"));
        this.registerSetting(randomizeOthers = new TickSetting("Rand others", true));
    }

    @Override
    public void onDisable() {
        FAKE_NAME_CACHE.clear();
        PATTERN_CACHE.clear();
        FRAME_CACHE.clear();
    }

    public static void clearFrameCache() {
        FRAME_CACHE.clear();
    }

    public static void setSelfAlias(String alias) {
        String cleaned = alias == null ? "" : alias.trim();
        if (customName != null) {
            customName.setValue(cleaned.isEmpty() ? "You" : cleaned);
        }
    }

    public static void setPlayerNick(String nick) {
        playerNick = nick == null ? "" : nick.trim();
    }

    public static boolean shouldRandomizeOthers() {
        return randomizeOthers != null && randomizeOthers.isToggled();
    }

    public static String format(String text) {
        if ((text == null) || text.isEmpty() || (mc == null)) {
            return text;
        }

        String cached = FRAME_CACHE.get(text);
        if (cached != null) {
            return cached;
        }

        if (FRAME_CACHE.size() > 2000) {
            FRAME_CACHE.clear();
        }

        String original = text;

        if (mc.thePlayer != null) {
            String selfAlias = getSelfAlias();
            String selfSource = (playerNick != null && !playerNick.isEmpty()) ? playerNick : mc.thePlayer.getName();
            text = replaceTokenWithFormatting(text, selfSource, selfAlias);
            text = replaceTokenWithFormatting(text, mc.thePlayer.getName(), selfAlias);
            String displayName = mc.thePlayer.getDisplayNameString();
            if (displayName != null && !displayName.equals(mc.thePlayer.getName())) {
                text = replaceTokenWithFormatting(text, displayName, selfAlias);
            }
        }

        if ((mc.theWorld == null) || (mc.theWorld.playerEntities == null) || !shouldRandomizeOthers()) {
            FRAME_CACHE.put(original, text);
            return text;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if ((player == null) || (player == mc.thePlayer)) {
                continue;
            }

            String fakeName = getOrCreateFakeName(player);
            text = replaceTokenWithFormatting(text, player.getName(), fakeName);
            String displayName = player.getDisplayNameString();
            if (displayName != null && !displayName.equals(player.getName())) {
                text = replaceTokenWithFormatting(text, displayName, fakeName);
            }
        }

        FRAME_CACHE.put(original, text);
        return text;
    }

    public static String getSelfAlias() {
        if (customName == null) return "You";
        String val = customName.getValue();
        return (val == null || val.trim().isEmpty()) ? "You" : val.trim();
    }

    private static String getOrCreateFakeName(EntityPlayer player) {
        String key = getPlayerKey(player);
        String cached = FAKE_NAME_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        int suffix = 1 + Math.abs(mixSeed(key)) % 999;
        String fake = "Player" + suffix;
        FAKE_NAME_CACHE.put(key, fake);
        return fake;
    }

    private static String getPlayerKey(EntityPlayer player) {
        UUID uuid = player.getUniqueID();
        if (uuid != null) {
            return uuid.toString();
        }
        return player.getName();
    }

    private static int mixSeed(String key) {
        int seed = key.hashCode();
        seed ^= (seed << 13);
        seed ^= (seed >>> 17);
        seed ^= (seed << 5);
        return seed;
    }

    private static String replaceToken(String input, String from, String to) {
        if ((input == null) || (from == null) || from.isEmpty() || from.equals(to)) {
            return input;
        }
        return input.replace(from, to);
    }

    private static String replaceTokenWithFormatting(String input, String from, String to) {
        if (input == null || from == null || from.isEmpty() || from.equals(to)) {
            return input;
        }

        input = input.replace(from, to);

        Pattern pattern = PATTERN_CACHE.get(from);
        if (pattern == null) {
            StringBuilder regex = new StringBuilder();
            regex.append("(?i)");
            regex.append("(?:\u00a7[0-9a-fk-or])*");
            for (int i = 0; i < from.length(); i++) {
                char c = from.charAt(i);
                if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
                if (i < from.length() - 1) {
                    regex.append("(?:\u00a7[0-9a-fk-or])*");
                }
            }
            try {
                pattern = Pattern.compile(regex.toString());
                PATTERN_CACHE.put(from, pattern);
            } catch (Exception ignored) {
                return input;
            }
        }

        try {
            input = pattern.matcher(input).replaceAll(to);
        } catch (Exception ignored) {
        }

        return input;
    }

}
