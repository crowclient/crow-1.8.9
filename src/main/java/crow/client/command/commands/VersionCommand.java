package crow.client.command.commands;

import crow.client.command.Command;
import crow.client.main.Crow;
import crow.client.utils.version.Version;

import static crow.client.clickgui.crow.Terminal.print;

public class VersionCommand extends Command {
    public VersionCommand() {
        super("version", "tells you what build of B++ you are using", 0, 0, new String[] {},
                new String[] { "v", "ver", "which", "build", "b" });
    }

    @Override
    public void onCall(String[] args) {
        Version clientVersion = Crow.versionManager.getClientVersion();
        Version latestVersion = Crow.versionManager.getLatestVersion();

        print("Your build: " + clientVersion);
        print("Latest version: " + latestVersion);

    }
}
