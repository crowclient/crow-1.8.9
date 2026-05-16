package crow.client.module.modules.render;

import crow.client.module.Module;
import crow.client.module.setting.impl.SliderSetting;

public class Viewmodel extends Module {
    public static SliderSetting translateX;
    public static SliderSetting translateY;
    public static SliderSetting translateZ;
    public static SliderSetting rotateX;
    public static SliderSetting rotateY;
    public static SliderSetting rotateZ;
    public static SliderSetting scale;

    private static Viewmodel instance;

    public Viewmodel() {
        super("Viewmodel", ModuleCategory.render);
        instance = this;
        this.withDescription("Adjusts first-person item position, rotation, and scale.");
        this.registerSetting(translateX = new SliderSetting("X", 0.0D, -2.0D, 2.0D, 0.01D));
        this.registerSetting(translateY = new SliderSetting("Y", 0.0D, -2.0D, 2.0D, 0.01D));
        this.registerSetting(translateZ = new SliderSetting("Z", 0.0D, -2.0D, 2.0D, 0.01D));
        this.registerSetting(rotateX = new SliderSetting("Rot X", 0.0D, -180.0D, 180.0D, 1.0D));
        this.registerSetting(rotateY = new SliderSetting("Rot Y", 0.0D, -180.0D, 180.0D, 1.0D));
        this.registerSetting(rotateZ = new SliderSetting("Rot Z", 0.0D, -180.0D, 180.0D, 1.0D));
        this.registerSetting(scale = new SliderSetting("Scale", 1.0D, 0.1D, 2.5D, 0.01D));
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static float getTranslateX() {
        return translateX == null ? 0.0F : (float) translateX.getInput();
    }

    public static float getTranslateY() {
        return translateY == null ? 0.0F : (float) translateY.getInput();
    }

    public static float getTranslateZ() {
        return translateZ == null ? 0.0F : (float) translateZ.getInput();
    }

    public static float getRotateX() {
        return rotateX == null ? 0.0F : (float) rotateX.getInput();
    }

    public static float getRotateY() {
        return rotateY == null ? 0.0F : (float) rotateY.getInput();
    }

    public static float getRotateZ() {
        return rotateZ == null ? 0.0F : (float) rotateZ.getInput();
    }

    public static float getScale() {
        return scale == null ? 1.0F : (float) scale.getInput();
    }
}
