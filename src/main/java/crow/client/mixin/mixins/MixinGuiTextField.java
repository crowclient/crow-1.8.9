package crow.client.mixin.mixins;

import crow.client.module.modules.HUD;
import crow.client.utils.font.ChatFontContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextField.class)
public class MixinGuiTextField {

    @Inject(method = "drawTextBox", at = @At("HEAD"))
    private void crow$beginChatInputFont(CallbackInfo ci) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiChat
                && HUD.customChat != null && HUD.customChat.isToggled()) {
            ChatFontContext.pushChatInput();
        }
    }

    @Inject(method = "drawTextBox", at = @At("RETURN"))
    private void crow$endChatInputFont(CallbackInfo ci) {
        ChatFontContext.popChatInput();
    }
}
