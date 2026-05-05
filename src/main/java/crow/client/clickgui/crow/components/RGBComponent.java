package crow.client.clickgui.crow.components;

import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.RGBSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.font.FontUtil;
import net.minecraft.util.EnumChatFormatting;

public class RGBComponent extends SettingComponent {

    private static final int TRACK = 0xFF2A2A2A;

    private RGBSetting setting;
    private boolean mouseDown;
    private Helping helping;

    public RGBComponent(Setting setting, ModuleComponent category) {
        super(setting, category);
        this.setting = (RGBSetting) setting;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        setDimensions(moduleComponent.getWidth() - 10, 30);
        int x = this.x + 5;
        int trackWidth = width - 10;

        if (mouseDown) {
            float percentageAcross = Math.max(0, Math.min(1, (mouseX - x) / (float) trackWidth));
            setting.setColor(helping.id, (int) (percentageAcross * 255f));
        }

        FontUtil.normal.drawSmoothString(
                setting.getName() + ": "
                        + EnumChatFormatting.RED + setting.getRed()
                        + ", " + EnumChatFormatting.GREEN + setting.getGreen()
                        + ", " + EnumChatFormatting.BLUE + setting.getBlue(),
                x, y + 4, 0xFFFFFFFF);

        int boxY = y + 20;
        int boxHeight = 6;
        RenderUtils.drawRoundedRect(x, boxY, x + trackWidth, boxY + boxHeight, 3, TRACK);

        int[] drawColor = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF};
        for (int i = 0; i < 3; i++) {
            int colorX = (int) (((trackWidth * this.setting.getColor(i)) / 255f) + x);
            RenderUtils.drawRoundedRect(Math.max(x, colorX - 2), boxY - 2, Math.min(x + trackWidth, colorX + 2),
                    boxY + boxHeight + 2, 2, drawColor[i]);
        }
    }

    @Override
    public void clicked(int mouseX, int mouseY, int button) {
        mouseDown = true;
        float percentageAcross = (mouseX - x) / (float) width;
        int r = 0;
        float c = 1;
        for (int i = 0; i < 3; i++)
            if (Math.abs((this.setting.getColor(i) / 255f) - percentageAcross) < c) {
                r = i;
                c = Math.abs((this.setting.getColor(i) / 255f) - percentageAcross);
            }
        switch (r) {
            case 0: helping = Helping.RED; break;
            case 1: helping = Helping.GREEN; break;
            case 2: helping = Helping.BLUE; break;
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
        mouseDown = false;
    }

    public enum Helping {
        RED(0), GREEN(1), BLUE(2), NONE(-1);
        private final int id;
        Helping(int id) { this.id = id; }
        public int getId() { return id; }
    }
}
