package crow.client.clickgui.kv.components;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import crow.client.clickgui.kv.KvComponent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.setting.Setting;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

public class KvModuleComponent extends KvComponent{
    private static final ResourceLocation defaultModuleIcon = RenderUtils.getResourcePath("/assets/crow/crow.png");

    private Module module;

    private final static ResourceLocation settingIcon = RenderUtils.getResourcePath("/assets/crow/kvclickgui/gear.png");;
    private ResourceLocation moduleIcon;
    private int toggleX, toggleY, toggleWidth, toggleHeight,
    			settingX, settingY, settingWidth, settingHeight,
    			settingX2, settingY2, settingWidth2, settingHeight2,
    			titleBoxX, titleBoxY, titleBoxWidth, titleBoxHeight,
    			settingsBoxX, settingsBoxY, settingsBoxWidth, settingsBoxHeight,
    			nameHeight, bindBoxY, bindBoxHeight, halfSettingsBoxWidth,
    			rx, ry;
    private List<KvComponent> settings = new ArrayList<KvComponent>();
    private List<Setting> settingModels = new ArrayList<Setting>();
    private KvBindComponent bindComponent;

    public KvModuleComponent(Module module) {
        this.module = module;
        bindComponent = new KvBindComponent(module);
        moduleIcon = resolveModuleIcon(module);
        for(Setting setting : module.getSettings())
			try {
				Class<? extends KvComponent> clazz = setting.getComponentType();
				settings.add(clazz.getDeclaredConstructor(Setting.class).newInstance(setting));
				settingModels.add(setting);
        	} catch(Exception e)  {
				settingModels.add(null);
			}
    }

    @Override
    public void draw(int mouseX, int mouseY) {

    	x = rx;
    	y = ry + KvModuleSection.moduleScroll;
        toggleX = x;
        toggleY = y + (int) ((3 * height) / 3.8);
        toggleWidth = width - (int) (width/3.8);
        toggleHeight = (int) (height - ((3 * height) / 3.8));
        settingX = x + toggleWidth;
        settingY = toggleY;
        settingWidth = width - toggleWidth;
        settingHeight = toggleHeight + 1;
        nameHeight = height - toggleHeight - FontUtil.normal.getHeight() - 1;

        RenderUtils.drawRoundedRect(x, y, x + width, y + height, 12, 0xA0000000);
        RenderUtils.drawRoundedRect(toggleX, toggleY + 1, toggleX + toggleWidth, toggleY + toggleHeight + 1,12, module.isEnabled() ? 0xFF00FF00 : 0xFFFF0000, new boolean[] {false, true, false, false});
        RenderUtils.drawRoundedOutline(x, y, x + width, y + height, 12, 2, Utils.Client.rainbowDraw(1, 0));

        if (moduleIcon != null) {
            crow.client.utils.RenderUtils.bindSmoothIcon(moduleIcon);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1f);
            Gui.drawModalRectWithCustomSizedTexture(x + (FontUtil.normal.getHeight()/2), y, 0, 0, width - FontUtil.normal.getHeight(), nameHeight, width - FontUtil.normal.getHeight(), nameHeight);
        }

        Gui.drawRect(toggleX, toggleY, toggleX + width, toggleY + 1, Utils.Client.rainbowDraw(1, 0));
        Gui.drawRect(settingX, settingY, settingX + 1, settingY + settingHeight, Utils.Client.rainbowDraw(1, 0));

        FontUtil.normal.drawCenteredString(module.getName(), x + (width / 2), y + nameHeight, 0xFFFFFFFF);
        FontUtil.two.drawCenteredString(module.isEnabled() ? "Enabled" : "Disabled", toggleX + (toggleWidth / 2), toggleY + (toggleHeight / 2), 0xFF000000);

        crow.client.utils.RenderUtils.bindSmoothIcon(settingIcon);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1f);
        Gui.drawModalRectWithCustomSizedTexture(settingX, settingY, 0, 0, settingWidth, settingHeight, settingWidth, settingHeight);
    }

    public void drawOpen(int mouseX, int mouseY) {

        RenderUtils.drawRoundedRect(
                settingsBoxX,
                bindBoxY,
                settingsBoxX + halfSettingsBoxWidth,
                bindBoxY + bindBoxHeight,
                8,
                module.isEnabled() ? 0xFF00FF00 : 0xFFFF0000,
                new boolean[] {false, true, false, false});
        Gui.drawRect(settingsBoxX, bindBoxY, settingsBoxX + settingsBoxWidth, bindBoxY + 1, Utils.Client.rainbowDraw(1, 0));
        Minecraft.getMinecraft().fontRendererObj.drawString(module.isEnabled() ? "Enabled" : "Disabled", settingsBoxX + (halfSettingsBoxWidth/4), (bindBoxY + (bindBoxHeight/2)) - (Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT/2), 0xFF000000);
        bindComponent.draw(mouseX, mouseY);

        RenderUtils.drawBorderedRoundedRect(
                titleBoxX,
                titleBoxY,
                titleBoxX + titleBoxWidth,
                titleBoxY + titleBoxHeight,
                8,
                2,
                Utils.Client.rainbowDraw(1, 0), 0x00FFFFFF);
        FontUtil.normal.drawString(module.getName(), titleBoxX + 2, titleBoxY + (titleBoxHeight/2), 0xFFFFFFFF);
        crow.client.utils.RenderUtils.bindSmoothIcon(settingIcon);

        Gui.drawModalRectWithCustomSizedTexture(settingX2, settingY2, 0, 0, settingWidth2, settingHeight2, settingWidth2, settingHeight2);

        RenderUtils.drawBorderedRoundedRect(
                settingsBoxX,
                settingsBoxY,
                settingsBoxX + settingsBoxWidth,
                settingsBoxY + settingsBoxHeight,
                8,
                2,
                Utils.Client.rainbowDraw(1, 0), 0x30000000,
                new boolean[] {false, true, true, false});

        for (int idx = 0; idx < settings.size(); idx++) {
            Setting sm = idx < settingModels.size() ? settingModels.get(idx) : null;
            settings.get(idx).kvVisible = sm == null || sm.shouldBeVisible();
        }

        int yOffset = KvModuleSection.padding;
        int xOffset = KvModuleSection.padding;
        int visCount = 0;
        int totalVisible = 0;
        for (KvComponent c : settings) if (c.kvVisible) totalVisible++;
        for (int idx = 0; idx < settings.size(); idx++) {
            KvComponent component = settings.get(idx);
            if (!component.kvVisible) continue;
        	visCount++;
        	component.setCoords(settingsBoxX + xOffset, settingsBoxY + yOffset + KvModuleSection.moduleScroll);
        	component.setDimensions((settingsBoxWidth/2) - (KvModuleSection.padding * 2), 12);
        	yOffset += component.getHeight() + 2;
        	if(visCount == (totalVisible/2)) {
        		yOffset = KvModuleSection.padding;
        		xOffset = settingsBoxWidth/2;
        	}
        }
        int sf = new ScaledResolution(Crow.mc).getScaleFactor();
        GL11.glScissor(settingsBoxX * sf, (titleBoxY - ((titleBoxHeight - bindBoxHeight) + (KvModuleSection.padding * 3)))* sf, settingsBoxWidth * sf, (settingsBoxHeight - bindBoxHeight) * sf);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        for(KvComponent component : settings)
            if (component.kvVisible) component.draw(mouseX, mouseY);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
	public void clicked(int mouseButton, int x, int y) {
    	if(KvModuleSection.moduleSec.openModule != null) {
    		if ((x > settingX2) && (x < (settingX2 + settingWidth2)) && (y > settingY2) && (y < (settingY2 + settingHeight2))) {
    			KvModuleSection.moduleSec.setOpenmodule(null);
    			return;
    		}
    		if ((x > settingsBoxX) && (x < (settingsBoxX + halfSettingsBoxWidth)) && (y > bindBoxY) && (y < (bindBoxY + bindBoxHeight)))
    			module.toggle();
    		bindComponent.mouseDown(x, y, mouseButton);
    		for(KvComponent component : settings)
				if (component.kvVisible) component.mouseDown(x, y, mouseButton);
        }

		else if ((x > settingX) && (x < (settingX + settingWidth)) && (y > settingY) && (y < (settingY + settingHeight)))
			KvModuleSection.moduleSec.setOpenmodule(this);
		else if ((x > toggleX) && (x < (toggleX + toggleWidth)) && (y > bindBoxY) && (y < (bindBoxY + bindBoxHeight)))
			module.toggle();
    }

    @Override
    public void setCoords(int x, int y) {
        rx = x;
        ry = y;
    }

    public void setBoxCoords(int x, int y, int width, int height) {
        titleBoxX = x + KvModuleSection.padding;
        titleBoxY = y + KvModuleSection.padding;
        titleBoxWidth = width - (KvModuleSection.padding * 2);
        titleBoxHeight = FontUtil.normal.getHeight() + 12;

        settingX2 = (titleBoxX + titleBoxWidth) - titleBoxHeight - 1;
        settingY2 = titleBoxY;
        settingWidth2 = titleBoxHeight;
        settingHeight2 = titleBoxHeight;

        settingsBoxX = titleBoxX + KvModuleSection.padding;
        settingsBoxY = titleBoxY + titleBoxHeight;
        settingsBoxWidth = titleBoxWidth - (KvModuleSection.padding * 2);
        settingsBoxHeight = height - titleBoxHeight - (KvModuleSection.padding * 2);

        bindBoxHeight = height/7;
        bindBoxY = (settingsBoxY + settingsBoxHeight) - bindBoxHeight;
        halfSettingsBoxWidth = settingsBoxWidth/2;

        bindComponent.setCoords(settingsBoxX + halfSettingsBoxWidth, bindBoxY);
        bindComponent.setDimensions(halfSettingsBoxWidth, bindBoxHeight);

    }

    @Override
    public void mouseReleased(int x, int y, int button) {
    	for(KvComponent component : settings)
			if (component.kvVisible) component.mouseReleased(x, y, button);
    }

    @Override
    public void keyTyped(char t, int k) {
    	bindComponent.keyTyped(t, k);
    }

    public int maxScroll() {
    	return settings.isEmpty() ? 0 : (int) -(settings.size()/2) * settings.get(0).getHeight();
    }

    private ResourceLocation resolveModuleIcon(Module module) {
        String category = module.moduleCategory().getName().toLowerCase();
        String moduleName = module.getName().toLowerCase().replace(" ", "");

        ResourceLocation resourceLocation = RenderUtils.getResourcePath("/assets/crow/kvclickgui/" + category + "/" + moduleName + ".png");
        if (resourceLocation != null) {
            return resourceLocation;
        }

        resourceLocation = RenderUtils.getResourcePath("/assets/crow/kvclickgui/categories/" + category + ".png");
        if (resourceLocation != null) {
            return resourceLocation;
        }

        return defaultModuleIcon;
    }

}
