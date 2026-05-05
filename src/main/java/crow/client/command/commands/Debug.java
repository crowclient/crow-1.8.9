package crow.client.command.commands;

import crow.client.clickgui.crow.Terminal;
import crow.client.command.Command;
import crow.client.main.Crow;

public class Debug extends Command {
    public Debug() {
        super("debug", "Toggles B++ debbugger", 0, 0, new String[] {}, new String[] { "dbg", "log" });
    }

    @Override
    public void onCall(String[] args) {
        Crow.debugger = !Crow.debugger;
        Terminal.print((Crow.debugger ? "Enabled" : "Disabled") + " debugging.");
    }
}
