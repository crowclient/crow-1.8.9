package crow.client.module;

import crow.client.clickgui.crow.components.CategoryComponent;
import crow.client.main.Crow;

public class GuiModule extends Module {

    private final ModuleCategory moduleCategory;

    public GuiModule(ModuleCategory moduleCategory, ModuleCategory parentCategory) {
        super(moduleCategory.getName(), parentCategory);
        this.moduleCategory = moduleCategory;
        hasBind = false;
        showInHud = false;
    }

    @Override
    public void onEnable() {
        CategoryComponent cc = Crow.clickGui.getCategoryComponent(moduleCategory);
        cc.initGui();
        cc.visable = true;
    }

    @Override
    public void onDisable() {
        Crow.clickGui.getCategoryComponent(moduleCategory).visable = false;
    }

    public ModuleCategory getGuiCategory() {
        return moduleCategory;
    }

}
