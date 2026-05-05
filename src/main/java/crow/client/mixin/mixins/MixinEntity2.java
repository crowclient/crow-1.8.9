package crow.client.mixin.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import crow.client.event.impl.LookEvent;
import crow.client.main.Crow;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

@Mixin(priority = 1005, value = Entity.class)
public abstract class MixinEntity2 {

    @Shadow
    public float rotationYaw;

    @Shadow
    public float rotationPitch;

    @Shadow
    public float prevRotationPitch;

    @Shadow
    public float prevRotationYaw;

   @Overwrite
   public Vec3 getVectorForRotation(float pitch, float yaw) {
       if((Object) this == Minecraft.getMinecraft().thePlayer) {
           LookEvent e = new LookEvent(pitch, yaw);
           Crow.eventBus.post(e);
           pitch = e.getPitch();
           yaw = e.getYaw();
       }
       float f = MathHelper.cos(-yaw * 0.017453292F - (float)Math.PI);
       float f1 = MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
       float f2 = -MathHelper.cos(-pitch * 0.017453292F);
       float f3 = MathHelper.sin(-pitch * 0.017453292F);
       return new Vec3((double)(f1 * f2), (double)f3, (double)(f * f2));
   }

}
