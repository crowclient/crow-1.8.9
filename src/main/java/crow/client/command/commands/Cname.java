package crow.client.command.commands;

import crow.client.clickgui.crow.Terminal;
import crow.client.command.Command;
import crow.client.module.modules.other.NameHider;

public class Cname extends Command {
    public Cname() {
        super("cname", "Hides your name client-side", 1, 1, new String[] { "New name" },
                new String[] { "cn", "changename" });
    }

    @Override
    public void onCall(String[] args) {
        if (args.length == 0) {
            this.incorrectArgs();
            return;
        }

        NameHider.setSelfAlias(args[0]);
        Terminal.print("Hidden name has been set to: " + NameHider.getSelfAlias());
    }
}
