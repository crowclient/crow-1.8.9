package crow.client.clickgui.kv.components;

import org.lwjgl.opengl.GL11;

import crow.client.clickgui.kv.KvComponent;
import crow.client.module.setting.Setting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import net.minecraft.client.Minecraft;

public class KvSliderComponent extends KvComponent {

	private SliderSetting setting;
	private boolean mouseDown;

	public KvSliderComponent(Setting setting) {
		this.setting = (SliderSetting) setting;
	}

    @Override
	public void draw(int mouseX, int mouseY) {

    	if(mouseDown) {
    		float p = (mouseX - x) / (float) (width);
			setting.setValue((float) ((p * (setting.getMax())) + (setting.getMin() * (1 - p))));
    	}

    	GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        Minecraft.getMinecraft().fontRendererObj.drawString(
                setting.getName() + ": " + setting.getInput(),
                (float) (x * 2),
                (float) (y * 2),
                0xFEFFFFFF,
                false);
        GL11.glPopMatrix();

        int percentWidth = (int) (width * ((setting.getInput() - setting.getMin())/(setting.getMax() - setting.getMin())));
        int boxY = y + (Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT/2) + 1;
        int boxHeight = height - (boxY - y);
    	RenderUtils.drawBorderedRoundedRect(x, boxY , x + width, (boxY + boxHeight) , 4, 2, Utils.Client.rainbowDraw(1, 0), 0x20FFFFFF);
    	RenderUtils.drawBorderedRoundedRect(x, boxY , x + percentWidth, (boxY + boxHeight), percentWidth > 4 ? 4 : percentWidth, 2, 0xFFA020F0, 0x90FFFFFF);
    }

    @Override
	public void clicked(int button, int x, int y) {
    	mouseDown = true;
	}

    @Override
    public void mouseReleased(int x, int y, int button) {
    	mouseDown = false;
    }
}
