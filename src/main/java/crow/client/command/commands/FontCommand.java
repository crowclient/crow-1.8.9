package crow.client.command.commands;

import crow.client.command.Command;
import crow.client.main.Crow;
import crow.client.notifications.Notification;
import crow.client.notifications.NotificationManager;
import crow.client.notifications.NotificationType;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;

public class FontCommand extends Command {
    public FontCommand() {
        super("font",
                "Changes the custom font. Use quotes for multi-word names.",
                1, 10,
                new String[]{"font name"},
                new String[]{"font Arial", "font \"Google Sans Regular\"", "font Consolas"});
    }

    @Override
    public void onCall(String[] args) {
        if (args.length == 0) {
            this.incorrectArgs();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i]);
        }

        String font = sb.toString().trim().replace("\"", "").replace("'", "");

        if (font.isEmpty()) {
            this.incorrectArgs();
            return;
        }

        String resolved = FontUtil.changeFonts(font);
        if (resolved == null) {
            Utils.Player.sendMessageToSelf("&cCould not find font: &f" + font);
            NotificationManager.show(new Notification(NotificationType.ERROR, "Font Not Found", font, 2));
            return;
        }

        Utils.Player.sendMessageToSelf("&aFont set to &f" + resolved);
        NotificationManager.show(new Notification(NotificationType.INFO, "Font Changed", resolved, 1));
    }
}
