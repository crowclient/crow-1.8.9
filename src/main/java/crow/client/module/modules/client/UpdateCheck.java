package crow.client.module.modules.client;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.GameLoopEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import crow.client.utils.version.Version;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class UpdateCheck extends Module {
    public static DescriptionSetting howToUse;
    public static TickSetting copyToClipboard;
    public static TickSetting openLink;

    private Future<?> f;
    private final ExecutorService executor;
    private final Runnable task;

    public UpdateCheck() {
        super("Update", ModuleCategory.client);

        this.registerSetting(howToUse = new DescriptionSetting(Utils.Java.capitalizeWord("command") + ": update"));
        this.registerSetting(copyToClipboard = new TickSetting("Copy to clipboard", true));
        this.registerSetting(openLink = new TickSetting("Open dl in browser", true));

        executor = Executors.newFixedThreadPool(1);
        task = () -> {
            Version latest = Crow.versionManager.getLatestVersion();
            Version current = Crow.versionManager.getClientVersion();
            if (latest.isNewerThan(current)) {
                Utils.Player.sendMessageToSelf("The current version of Crow is outdated.");
            }

            if (current.isNewerThan(latest)) {
                Utils.Player.sendMessageToSelf("You are on a beta build of Crow");
            } else {
                Utils.Player.sendMessageToSelf("You are on the latest public version!");
            }

            if (copyToClipboard.isToggled())
                if (Utils.Client.copyToClipboard(Crow.downloadLocation))
                    Utils.Player.sendMessageToSelf("Successfully copied download link to clipboard!");
            Utils.Player.sendMessageToSelf(Crow.sourceLocation);

            if (openLink.isToggled()) {
                try {
                    URL url = new URL(Crow.sourceLocation);
                    Utils.Client.openWebpage(url);
                    Utils.Client.openWebpage(new URL(Crow.downloadLocation));
                } catch (MalformedURLException bruh) {
                    bruh.printStackTrace();
                    Utils.Player
                            .sendMessageToSelf("&cFailed to open page! Please report this bug in Crow's discord");
                }
            }

            this.disable();
        };
    }

    @Subscribe
    public void onGameLoop(GameLoopEvent e) {
        if (f == null) {
            f = executor.submit(task);
            Utils.Player.sendMessageToSelf("Update check started!");
        } else if (f.isDone()) {
            f = executor.submit(task);
            Utils.Player.sendMessageToSelf("Update check started!");
        }
    }
}
