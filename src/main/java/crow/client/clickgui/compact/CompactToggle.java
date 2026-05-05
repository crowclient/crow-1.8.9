package crow.client.clickgui.compact;

import crow.client.module.Module;
import crow.client.module.modules.client.GuiModule.CompactPalette;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Animation;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;

public class CompactToggle {

    private static final int TRACK_WIDTH = 34;
    private static final int TRACK_HEIGHT = 16;
    private static final int KNOB_SIZE = 10;
    private static final int INSET = 3;
    private static final int TRAVEL = TRACK_WIDTH - KNOB_SIZE - INSET * 2;

    private final TickSetting setting;
    private final Module mod;

    int x, y, w, h;

    private final Animation toggleAnim = new Animation(200, Animation::easeOutCubic);
    private final Animation hoverAnim = new Animation(150, Animation::easeOutCubic);

    public CompactToggle(TickSetting setting, Module mod) {
        this.setting = setting;
        this.mod = mod;
        toggleAnim.set(setting.isToggled() ? 1.0F : 0.0F);
    }

    public void setPosition(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(int mouseX, int mouseY, CompactPalette palette) {
        toggleAnim.setTarget(setting.isToggled() ? 1.0F : 0.0F);
        toggleAnim.update();
        float toggleAnimation = toggleAnim.get();

        int trackX = x + w - TRACK_WIDTH;
        int trackY = y + (h - TRACK_HEIGHT) / 2;
        boolean hovered = mouseX >= trackX && mouseX <= trackX + TRACK_WIDTH
                && mouseY >= trackY && mouseY <= trackY + TRACK_HEIGHT;
        hoverAnim.setTarget(hovered ? 1.0F : 0.0F);
        hoverAnim.update();
        float hoverAnimation = hoverAnim.get();

        FontUtil.semiBold.drawSmoothString(setting.getName(), x, y + (h - 9) / 2, palette.titleText);

        int themeColor = 0xFF000000 | (crow.client.module.modules.client.GuiModule.getThemeColor(0) & 0x00FFFFFF);
        int trackColor = CompactModuleCard.blendColor(palette.toggleOff, themeColor, toggleAnimation);

        if (hoverAnimation > 0.01F) {
            trackColor = CompactModuleCard.blendColor(trackColor, palette.hoverCard, hoverAnimation * 0.18F);
        }
        RenderUtils.drawRoundedRectAA(trackX, trackY, trackX + TRACK_WIDTH, trackY + TRACK_HEIGHT,
                TRACK_HEIGHT / 2.0F, trackColor);

        if (toggleAnimation > 0.02F) {
            RenderUtils.drawFlowingGradientRoundedRectVertical(
                    trackX, trackY, trackX + TRACK_WIDTH, trackY + TRACK_HEIGHT,
                    TRACK_HEIGHT / 2.0F, (int) (130 * toggleAnimation), 0);
        }

        float offset = toggleAnimation * TRAVEL;
        int knobX = (int) (trackX + INSET + offset);
        int knobY = trackY + (TRACK_HEIGHT - KNOB_SIZE) / 2;

        RenderUtils.drawRoundedRectAA(knobX + 1, knobY + 1, knobX + KNOB_SIZE + 1, knobY + KNOB_SIZE + 1,
                KNOB_SIZE / 2.0F, 0x1A000000);

        int knobColor = 0xFFFFFFFF;
        if (hoverAnimation > 0.01F) {

            knobColor = CompactModuleCard.blendColor(0xFFFFFFFF, 0xFFF8F4F0, hoverAnimation);
        }
        RenderUtils.drawRoundedRectAA(knobX, knobY, knobX + KNOB_SIZE, knobY + KNOB_SIZE,
                KNOB_SIZE / 2.0F, knobColor);
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        setting.toggle();
        mod.guiButtonToggled(setting);
    }
}
