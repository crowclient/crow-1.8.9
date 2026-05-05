package crow.client.mixin.mixins;

import crow.client.utils.font.ChatFontContext;
import net.minecraft.client.gui.GuiNewChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiNewChat.class)
public class MixinGuiNewChat {

    @Inject(method = "drawChat", at = @At("HEAD"))
    private void crow$beginCustomChatFont(int updateCounter, CallbackInfo ci) {
        ChatFontContext.pushChatHud();
    }

    @Inject(method = "drawChat", at = @At("RETURN"))
    private void crow$endCustomChatFont(int updateCounter, CallbackInfo ci) {
        ChatFontContext.popChatHud();
    }

    @Inject(method = "getChatComponent", at = @At("HEAD"))
    private void crow$beginChatMeasure(int mouseX, int mouseY, CallbackInfoReturnable<?> cir) {
        ChatFontContext.pushChatMeasure();
    }

    @Inject(method = "getChatComponent", at = @At("RETURN"))
    private void crow$endChatMeasure(int mouseX, int mouseY, CallbackInfoReturnable<?> cir) {
        ChatFontContext.popChatMeasure();
    }
}
