package crow.client.command.commands;

import crow.client.command.Command;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.notifications.Notification;
import crow.client.notifications.NotificationManager;
import crow.client.notifications.NotificationType;
import crow.client.utils.Utils;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class BindCommand extends Command {

    public BindCommand() {
        super("bind",
                "Binds a module to a key. Use 'none' to clear.",
                1, 3,
                new String[]{"<module> [key]"},
                new String[]{"bind KillAura R", "bind Sprint none", "bind"});
    }

    @Override
    public void onCall(String[] args) {

        if (args.length == 0) {
            printAllBinds();
            return;
        }

        String moduleName = args[0];
        Module mod = Crow.moduleManager.getModuleByName(moduleName);

        if (mod == null) {
            Utils.Player.sendMessageToSelf("&cModule &f" + moduleName + " &cnot found.");
            NotificationManager.show(new Notification(NotificationType.ERROR, "Bind Failed", "Module not found: " + moduleName, 2));
            return;
        }

        if (!mod.isBindable()) {
            Utils.Player.sendMessageToSelf("&c" + mod.getName() + " &ccannot be bound.");
            return;
        }

        if (args.length == 1) {
            Utils.Player.sendMessageToSelf("&f" + mod.getName() + " &7→ &f" + mod.getBindAsString());
            return;
        }

        String keyName = args[1];

        if (keyName.equalsIgnoreCase("none") || keyName.equalsIgnoreCase("clear")
                || keyName.equalsIgnoreCase("0") || keyName.equalsIgnoreCase("unbind")) {
            mod.setbind(0);
            Utils.Player.sendMessageToSelf("&aCleared bind for &f" + mod.getName());
            NotificationManager.show(new Notification(NotificationType.INFO, "Bind Cleared", mod.getName(), 1));
            return;
        }

        int keyCode = resolveKey(keyName);
        if (keyCode == 0) {
            Utils.Player.sendMessageToSelf("&cUnknown key: &f" + keyName + " &7(try R, F, LSHIFT, GRAVE)");
            NotificationManager.show(new Notification(NotificationType.ERROR, "Unknown Key", keyName, 2));
            return;
        }

        for (Module other : Crow.moduleManager.getModules()) {
            if (other != mod && other.isBindable() && other.getKeycode() == keyCode) {
                Utils.Player.sendMessageToSelf("&e" + other.getName() + " &ealso uses &f" + Keyboard.getKeyName(keyCode));
                break;
            }
        }

        mod.setbind(keyCode);
        String keyStr = Keyboard.getKeyName(keyCode);
        Utils.Player.sendMessageToSelf("&aBound &f" + mod.getName() + " &ato &f" + keyStr);
        NotificationManager.show(new Notification(NotificationType.INFO, "Bind Set", mod.getName() + " \u2192 " + keyStr, 1));
    }

    private void printAllBinds() {
        List<Module> modules = Crow.moduleManager.getModules();
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Module mod : modules) {
            if (mod.isBindable() && mod.getKeycode() != 0) {
                if (count > 0) sb.append("&7, ");
                sb.append("&f").append(mod.getName()).append("&7:&a").append(mod.getBindAsString());
                count++;
            }
        }
        if (count == 0) {
            Utils.Player.sendMessageToSelf("&7No binds set. Use &f.bind <module> <key>");
        } else {
            Utils.Player.sendMessageToSelf("&7Binds: " + sb.toString());
        }
    }

    private int resolveKey(String name) {

        String upper = name.toUpperCase();

        int code = Keyboard.getKeyIndex(upper);
        if (code != Keyboard.KEY_NONE) return code;

        switch (upper) {
            case "TILDE":
            case "BACKTICK": return Keyboard.KEY_GRAVE;
            case "MINUS":    return Keyboard.KEY_MINUS;
            case "EQUALS":   return Keyboard.KEY_EQUALS;
            case "LBRACKET": return Keyboard.KEY_LBRACKET;
            case "RBRACKET": return Keyboard.KEY_RBRACKET;
            case "BACKSLASH":return Keyboard.KEY_BACKSLASH;
            case "SEMICOLON":return Keyboard.KEY_SEMICOLON;
            case "QUOTE":
            case "APOSTROPHE": return Keyboard.KEY_APOSTROPHE;
            case "COMMA":    return Keyboard.KEY_COMMA;
            case "PERIOD":
            case "DOT":      return Keyboard.KEY_PERIOD;
            case "SLASH":
            case "FWDSLASH": return Keyboard.KEY_SLASH;
            case "SPACE":    return Keyboard.KEY_SPACE;
            case "CTRL":
            case "LCTRL":    return Keyboard.KEY_LCONTROL;
            case "RCTRL":    return Keyboard.KEY_RCONTROL;
            case "ALT":
            case "LALT":     return Keyboard.KEY_LMENU;
            case "RALT":     return Keyboard.KEY_RMENU;
            case "SHIFT":
            case "LSHIFT":   return Keyboard.KEY_LSHIFT;
            case "RSHIFT":   return Keyboard.KEY_RSHIFT;
            case "ENTER":    return Keyboard.KEY_RETURN;
            case "ESC":      return Keyboard.KEY_ESCAPE;
            case "TAB":      return Keyboard.KEY_TAB;
            case "CAPS":
            case "CAPSLOCK": return Keyboard.KEY_CAPITAL;
            case "INS":      return Keyboard.KEY_INSERT;
            case "DEL":      return Keyboard.KEY_DELETE;
            case "PGUP":     return Keyboard.KEY_PRIOR;
            case "PGDN":     return Keyboard.KEY_NEXT;
            case "HOME":     return Keyboard.KEY_HOME;
            case "END":      return Keyboard.KEY_END;
            case "UP":       return Keyboard.KEY_UP;
            case "DOWN":     return Keyboard.KEY_DOWN;
            case "LEFT":     return Keyboard.KEY_LEFT;
            case "RIGHT":    return Keyboard.KEY_RIGHT;
            case "MOUSE1":
            case "MB1":      return -100;
            default:         return 0;
        }
    }
}
