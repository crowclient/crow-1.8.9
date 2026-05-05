package crow.client.command.commands;

import crow.client.clickgui.crow.Terminal;
import crow.client.command.Command;
import crow.client.module.modules.other.NameHider;

public class Nick extends Command {
    public Nick() {
        super("nick", "Like nickhider mod", 1, 1, new String[] { "the new name" }, new String[] { "nk", "nickhider" });
    }

    @Override
    public void onCall(String[] args) {
        if (args.length == 0) {
            this.incorrectArgs();
            return;
        }

        NameHider.setPlayerNick(args[0]);
        Terminal.print("&aNick has been set to: " + args[0]);
    }
}
