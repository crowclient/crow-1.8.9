package crow.client.command.commands;

import crow.client.clickgui.crow.Terminal;
import crow.client.command.Command;
import crow.client.main.Crow;
import crow.client.utils.Utils;

public class Discord extends Command {
    public Discord() {
        super("discord", "Allows you to join the Crow discord", 0, 3, new String[] { "copy", "open", "print" },
                new String[] { "dc", "chat" });
    }

    @Override
    public void onCall(String[] args) {
        boolean opened = false;
        boolean copied = false;
        boolean showed = false;
        int argCurrent = 0;
        if (args.length == 0) {
            Terminal.print("§3Opening " + Crow.discord);
            Utils.Client.openWebpage(Crow.discord);
            opened = true;
            return;
        }

        for (String argument : args) {
            if (argument.equalsIgnoreCase("copy")) {
                if (!copied) {
                    Utils.Client.copyToClipboard(Crow.discord);
                    copied = true;
                    Terminal.print("Copied " + Crow.discord + " to clipboard!");
                }
            } else if (argument.equalsIgnoreCase("open")) {
                if (!opened) {
                    Utils.Client.openWebpage(Crow.discord);
                    opened = true;
                    Terminal.print("Opened invite link!");
                }
            } else if (argument.equalsIgnoreCase("print")) {
                if (!showed) {
                    Terminal.print(Crow.discord);
                    showed = true;
                }
            } else {
                if (argCurrent != 0)
                    this.incorrectArgs();
            }
            argCurrent++;
        }
    }
}
