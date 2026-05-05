

package crow.keystroke;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public class KeyStrokeCommand extends CommandBase {
    public String getCommandName() {
        return "crow";
    }

    public void processCommand(ICommandSender sender, String[] args) {

    }

    public String getCommandUsage(ICommandSender sender) {
        return "/crow";
    }

    public int getRequiredPermissionLevel() {
        return 0;
    }

    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
