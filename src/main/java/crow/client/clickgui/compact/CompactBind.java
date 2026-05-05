package crow.client.clickgui.compact;

import org.lwjgl.input.Keyboard;

import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

public class CompactBind {

    private final Module mod;
    private final CompactGui gui;
    private boolean binding = false;

    private int x, y, w, h;

    public CompactBind(Module mod, CompactGui gui) {
        this.mod = mod;
        this.gui = gui;
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        String text = binding ? "Press a key..." : mod.getBindAsString();
        int textColor = binding ? palette.titleText : palette.mutedText;

        FontUtil.semiBold.drawSmoothString("Keybind", x, y + (h - 9) / 2, palette.titleText);

        int badgeW = Math.max(96, (int) FontUtil.small.getStringWidth(text) + 20);
        int badgeX = x + w - badgeW;
        RenderUtils.drawRoundedRectAA(badgeX, y + 2, badgeX + badgeW, y + h - 2, 11,
                CompactModuleCard.blendColor(palette.toggleOff, palette.card, 0.45F));
        RenderUtils.drawFlowingGradientRoundedRect(badgeX + 1, y + 3, badgeX + badgeW - 1, y + h - 3, 10, binding ? 55 : 24, 0);
        FontUtil.small.drawCenteredSmoothString(text, badgeX + badgeW / 2.0F, y + 8, textColor);
    }

    public boolean isMouseOver(int mx, int my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        binding = !binding;
        gui.setActiveBindCard(binding ? this : null);
    }

    public void mouseBind(int mouseButton) {
        if (!binding) {
            return;
        }
        mod.setbind(Module.MOUSE_BIND_OFFSET + mouseButton);
        binding = false;
        gui.setActiveBindCard(null);
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (!binding) {
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
            mod.setbind(0);
        } else {
            mod.setbind(keyCode);
        }
        binding = false;
        gui.setActiveBindCard(null);
    }
}
