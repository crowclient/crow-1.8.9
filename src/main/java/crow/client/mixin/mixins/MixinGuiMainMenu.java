package crow.client.mixin.mixins;

import crow.client.gui.CrowMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import crow.client.module.modules.client.SelfDestruct;
import crow.client.module.modules.client.GuiModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;

@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu extends GuiScreen implements GuiYesNoCallback {
    private static final int CROW_MENU_BUTTON_ID = 16391;

    @Shadow
    public String splashText;

    @Inject(method = "initGui", at = @At("RETURN"))
    public void initGui(CallbackInfo ci) {
        if (SelfDestruct.selfDestructed || CrowMainMenu.redirecting || !GuiModule.customMainMenu()) {
            return;
        }

        CrowMainMenu.redirecting = true;
        Minecraft.getMinecraft().displayGuiScreen(new CrowMainMenu());
        CrowMainMenu.redirecting = false;
    }

    @Inject(method = "initGui", at = @At("RETURN"))
    private void crow$addToggleButton(CallbackInfo ci) {
        if (SelfDestruct.selfDestructed || GuiModule.customMainMenu()) {
            return;
        }

        this.buttonList.add(new GuiButton(CROW_MENU_BUTTON_ID, this.width - 74, 8, 66, 20, "Crow Menu"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void crow$handleToggleButton(GuiButton button, CallbackInfo ci) {
        if (button == null || button.id != CROW_MENU_BUTTON_ID) {
            return;
        }

        GuiModule.setCustomMainMenu(true);
        CrowMainMenu.redirecting = true;
        Minecraft.getMinecraft().displayGuiScreen(new CrowMainMenu());
        CrowMainMenu.redirecting = false;
        ci.cancel();
    }

}
