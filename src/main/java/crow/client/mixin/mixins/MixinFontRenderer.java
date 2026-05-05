package crow.client.mixin.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.other.NameHider;
import crow.client.utils.font.ChatFontContext;
import crow.client.utils.font.FontUtil;
import net.minecraft.client.gui.FontRenderer;

@Mixin(priority = 1005, value = FontRenderer.class)
public class MixinFontRenderer {

    private static Module cachedNameHider;
    private static boolean cachedNameHiderResolved;

    @ModifyVariable(method = "renderString", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String crow$hideNamesInRenderString(String text) {
        return shouldHide() ? NameHider.format(text) : text;
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String crow$hideNamesInGetStringWidth(String text) {
        return shouldHide() ? NameHider.format(text) : text;
    }

    @ModifyVariable(method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String crow$hideNamesInTrimWidth(String text) {
        return shouldHide() ? NameHider.format(text) : text;
    }

    @ModifyVariable(method = "wrapFormattedStringToWidth", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String crow$hideNamesInWrap(String text) {
        return shouldHide() ? NameHider.format(text) : text;
    }

    @Inject(method = "drawStringWithShadow(Ljava/lang/String;FFI)I", at = @At("HEAD"), cancellable = true)
    private void crow$customChatShadow(String text, float x, float y, int color, CallbackInfoReturnable<Integer> cir) {
        if (ChatFontContext.shouldUseCustomFont()) {
            FontUtil.semiBold.drawSmoothString(text, x, y, color);

            cir.setReturnValue((int) (x + FontUtil.semiBold.getStringWidth(text)));
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;III)I", at = @At("HEAD"), cancellable = true)
    private void crow$customChatIntDraw(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (ChatFontContext.shouldUseCustomFont()) {
            FontUtil.semiBold.drawSmoothString(text, x, y, color);
            cir.setReturnValue((int) (x + FontUtil.semiBold.getStringWidth(text)));
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), cancellable = true)
    private void crow$customChatFloatDraw(String text, float x, float y, int color, boolean dropShadow,
                                          CallbackInfoReturnable<Integer> cir) {
        if (ChatFontContext.shouldUseCustomFont()) {
            FontUtil.semiBold.drawSmoothString(text, x, y, color);
            cir.setReturnValue((int) (x + FontUtil.semiBold.getStringWidth(text)));
        }
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    private void crow$customChatWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (ChatFontContext.shouldUseCustomFont()) {
            cir.setReturnValue((int) FontUtil.semiBold.getStringWidth(text));
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void crow$customChatTrimWidth(String text, int width, boolean reverse,
                                          CallbackInfoReturnable<String> cir) {
        if (!ChatFontContext.shouldUseCustomFont() || text == null) {
            return;
        }

        String working = reverse ? new StringBuilder(text).reverse().toString() : text;
        StringBuilder trimmed = new StringBuilder();
        int limit = Math.max(0, width);

        for (int i = 0; i < working.length(); i++) {
            char c = working.charAt(i);
            trimmed.append(c);
            if (FontUtil.semiBold.getStringWidth(trimmed.toString()) > limit) {
                trimmed.deleteCharAt(trimmed.length() - 1);
                break;
            }
        }

        String result = trimmed.toString();
        cir.setReturnValue(reverse ? new StringBuilder(result).reverse().toString() : result);
    }

    private boolean shouldHide() {
        if (Crow.moduleManager == null) {
            return false;
        }
        if (!cachedNameHiderResolved) {
            cachedNameHider = Crow.moduleManager.getModuleByClazz(NameHider.class);
            cachedNameHiderResolved = true;
        }
        return (cachedNameHider != null) && cachedNameHider.isEnabled();
    }
}
