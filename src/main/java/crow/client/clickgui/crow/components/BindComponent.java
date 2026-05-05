package crow.client.clickgui.crow.components;

import org.lwjgl.input.Keyboard;
import crow.client.clickgui.crow.Component;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule;
import crow.client.utils.Utils;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

public class BindComponent extends Component {

    private final ModuleComponent module;
    private final Module mod;
    private boolean isBinding;

    public BindComponent(ModuleComponent moduleComponent) {
        this.module = moduleComponent;
        this.mod    = moduleComponent.mod;
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, 24);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        setDimensions(CategoryComponent.PANEL_WIDTH - 10, 24);
        int sx = this.x + 5;

        String label = isBinding ? "Press a key..." : "Keybind: " + mod.getBindAsString();
        RenderUtils.drawRoundedRect(x + 4, y + 4, x2 - 4, y + height - 4, 4,
                isBinding ? 0x5528D7FF : 0x332B2B31);
        FontUtil.normal.drawSmoothString(label, sx + 4, y + 7,
                isBinding ? 0xFFFFFFFF : 0xFFCCCCCC);
    }

    @Override
    public void clicked(int x, int y, int button) {
        if (button == 0)
            isBinding = true;
    }

    @Override
    public void keyTyped(char t, int k) {
        if (!isBinding) return;
        if (k == Keyboard.KEY_0 || k == Keyboard.KEY_ESCAPE) {
            if (mod instanceof GuiModule) mod.setbind(54);
            else                          mod.setbind(0);
            Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX + "&e" + mod.getName() + " bind cleared.");
        } else {
            mod.setbind(k);
            Utils.Player.sendMessageToSelf(Crow.CHAT_PREFIX + "&aBound &f" + mod.getName() + " &ato &f" + mod.getBindAsString() + "&a.");
        }
        isBinding = false;
    }
}
