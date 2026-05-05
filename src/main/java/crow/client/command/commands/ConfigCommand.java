package crow.client.command.commands;

import crow.client.clickgui.crow.Terminal;
import crow.client.command.Command;
import crow.client.config.Config;
import crow.client.main.Crow;

public class ConfigCommand extends Command {
    public ConfigCommand() {
        super("config", "Manages configs", 0, 3, new String[] { "load,save,list,remove,clear", "config's name" },
                new String[] { "cfg", "profiles" });
    }

    @Override
    public void onCall(String[] args) {
        if (Crow.clientConfig != null) {
            Crow.clientConfig.saveConfig();
            Crow.configManager.save();
        }

        if (args.length == 0) {
            Terminal.print("Current config: " + Crow.configManager.getConfig().getName());
        } else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("list")) {
                this.listConfigs();
            } else if (args[0].equalsIgnoreCase("clear")) {
                Terminal.print("Are you sure you want to reset the config " + Crow.configManager.getConfig().getName()
                        + "? If so, run \"config clear confirm\"");
            } else {
                this.incorrectArgs();
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("list")) {
                this.listConfigs();
            } else if (args[0].equalsIgnoreCase("load")) {
                boolean found = false;
                for (Config config : Crow.configManager.getConfigs()) {
                    if (config.getName().equalsIgnoreCase(args[1])) {
                        found = true;
                        Terminal.print("Found config with the name " + args[1] + "!");
                        Crow.configManager.setConfig(config);
                        Terminal.print("Loaded config!");
                    }
                }

                if (!found) {
                    Terminal.print("Unable to find a config with the name " + args[1]);
                }

            } else if (args[0].equalsIgnoreCase("save")) {
                Terminal.print("Saving...");
                Crow.configManager.copyConfig(Crow.configManager.getConfig(), args[1]);
                Terminal.print("Saved as \"" + args[1] + "\"! To load the config, run \"config load " + args[1] + "\"");
                Crow.configManager.discoverConfigs();
            } else if (args[0].equalsIgnoreCase("remove")) {
                boolean found = false;
                Terminal.print("Removing " + args[1] + "...");
                for (Config config : Crow.configManager.getConfigs()) {
                    if (config.getName().equalsIgnoreCase(args[1])) {
                        Crow.configManager.deleteConfig(config);
                        found = true;
                        Terminal.print("Removed " + args[1] + " successfully! Current config: "
                                + Crow.configManager.getConfig().getName());
                        break;
                    }
                }

                if (!found) {
                    Terminal.print("Failed to delete " + args[1]
                            + ". Unable to find a config with the name or an error occurred during removal");
                }

            } else if (args[0].equalsIgnoreCase("clear")) {
                if (args[1].equalsIgnoreCase("confirm")) {
                    Crow.configManager.resetConfig();
                    Crow.configManager.save();
                    Terminal.print("Cleared config!");
                } else {
                    Terminal.print("It is confirm, not " + args[1]);
                }

            } else {
                this.incorrectArgs();
            }
        }
    }

    public void listConfigs() {
        Terminal.print("Available configs: ");
        for (Config config : Crow.configManager.getConfigs()) {
            if (Crow.configManager.getConfig().getName().equals(config.getName()))
                Terminal.print("Current config: " + config.getName());
            else
                Terminal.print(config.getName());
        }
    }
}
