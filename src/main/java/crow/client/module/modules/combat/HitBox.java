package crow.client.module.modules.combat;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.world.AntiBot;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.Utils;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;

public class HitBox extends Module {
    public static SliderSetting expand;
    public static TickSetting vertical;

    public HitBox() {
        super("HitBoxes", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Expands entity hitboxes by extra blocks."));

        this.registerSetting(expand = new SliderSetting("Expand", 0.10D, 0.05D, 0.50D, 0.05D));
        this.registerSetting(vertical = new TickSetting("Vertical", false));
    }

    public static double exp(Entity en) {
        Module hitBox = Crow.moduleManager.getModuleByClazz(HitBox.class);
        return ((hitBox != null) && hitBox.isEnabled() && !AntiBot.bot(en)) ? expand.getInput() : 0D;
    }
}