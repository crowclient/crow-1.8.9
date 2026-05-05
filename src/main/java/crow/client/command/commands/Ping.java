package crow.client.command.commands;

import crow.client.command.Command;
import crow.client.utils.PingChecker;

public class Ping extends Command {
    public Ping() {
        super("ping", "Gets your ping", 0, 0, new String[] {}, new String[] { "p", "connection", "lag" });
    }

    @Override
    public void onCall(String[] args) {
        PingChecker.checkPing();
    }
}
