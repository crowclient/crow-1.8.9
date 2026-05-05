package crow.client.command.commands;

import crow.client.clickgui.crow.Terminal;
import crow.client.command.Command;
import crow.client.main.Crow;
import crow.client.utils.Utils;
import crow.client.utils.version.Version;

import java.net.MalformedURLException;
import java.net.URL;

public class Update extends Command {
    public Update() {
        super("update", "Assists you in updating the client", 0, 0, new String[] {}, new String[] { "upgrade" });
    }

    @Override
    public void onCall(String[] args) {
        Version clientVersion = Crow.versionManager.getClientVersion();
        Version latestVersion = Crow.versionManager.getLatestVersion();

        if (latestVersion.isNewerThan(clientVersion)) {
            Terminal.print("Opening page...");
            URL url = null;
            try {
                url = new URL(Crow.sourceLocation);
                Utils.Client.openWebpage(url);
                Utils.Client.openWebpage(new URL(Crow.downloadLocation));
                Terminal.print("Opened page successfully!");
            } catch (MalformedURLException e) {
                e.printStackTrace();
                Terminal.print("Failed to open page! Please report this bug in Crow b++'s discord!");

            }
        } else {
            Terminal.print("No need to upgrade, You are on the latest build");
        }
    }
}
