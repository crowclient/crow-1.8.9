package crow.client.utils.font;

import crow.client.module.modules.HUD;

public final class ChatFontContext {
    private static int chatHudDepth;
    private static int chatMeasureDepth;
    private static int chatInputDepth;

    private ChatFontContext() {
    }

    public static void pushChatHud() {
        chatHudDepth++;
    }

    public static void popChatHud() {
        if (chatHudDepth > 0) {
            chatHudDepth--;
        }
    }

    public static void pushChatMeasure() {
        chatMeasureDepth++;
    }

    public static void popChatMeasure() {
        if (chatMeasureDepth > 0) {
            chatMeasureDepth--;
        }
    }

    public static void pushChatInput() {
        chatInputDepth++;
    }

    public static void popChatInput() {
        if (chatInputDepth > 0) {
            chatInputDepth--;
        }
    }

    public static boolean shouldUseCustomFont() {
        return (chatHudDepth > 0 || chatMeasureDepth > 0 || chatInputDepth > 0)
                && HUD.customChat != null
                && HUD.customChat.isToggled()
                && FontUtil.hasLoaded()
                && FontUtil.semiBold != null;
    }
}
