package crow.client.mixin.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import crow.client.event.impl.GameLoopEvent;
import crow.client.main.Crow;
import crow.client.module.modules.other.NameHider;
import crow.client.utils.SilentAim;
import net.minecraft.client.Minecraft;

@Mixin(priority = 1005, value = Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "runTick", at = @At("HEAD"))
    public void onTick(CallbackInfo ci) {
        SilentAim.beginCycle();
        NameHider.clearFrameCache();
        Crow.eventBus.post(new GameLoopEvent());
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    public void crow$onShutdown(CallbackInfo ci) {
        try { Minecraft.getMinecraft().gameSettings.saveOptions(); } catch (Exception ignored) {}
    }

}
