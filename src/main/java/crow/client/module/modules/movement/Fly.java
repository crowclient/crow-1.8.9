package crow.client.module.modules.movement;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.TickEvent;
import crow.client.module.Module;
import crow.client.module.setting.impl.ComboSetting;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.utils.Utils;
import org.lwjgl.input.Keyboard;

public class Fly extends Module {

    public static ComboSetting<FlyMode> mode;
    public static SliderSetting speed;
    public static SliderSetting verticalSpeed;

    private boolean glideStarted;

    public Fly() {
        super("Fly", ModuleCategory.movement);
        this.registerSetting(mode = new ComboSetting<>("Mode", FlyMode.Vanilla));
        this.registerSetting(speed = new SliderSetting("Speed", 2.0D, 0.5D, 6.0D, 0.1D));
        this.registerSetting(verticalSpeed = new SliderSetting("Vertical", 1.0D, 0.2D, 3.0D, 0.1D));
    }

    @Override
    public String getHudSuffix() {
        return mode.getMode().name();
    }

    @Override
    public void onEnable() {
        glideStarted = false;
    }

    @Override
    public void onDisable() {
        glideStarted = false;
        if (!Utils.Player.isPlayerInGame()) {
            return;
        }

        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.capabilities.setFlySpeed(0.05F);
        mc.thePlayer.noClip = false;
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame()) {
            return;
        }

        switch ((FlyMode) mode.getMode()) {
            case Vanilla:
                updateVanilla();
                break;
            case Glide:
                updateGlide();
                break;
            case AirWalk:
                updateAirWalk();
                break;
        }
    }

    private void updateVanilla() {
        double horizontalSpeed = 0.28D * speed.getInput();
        double vSpeed = 0.20D * verticalSpeed.getInput();

        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.motionY = 0.0D;

        if (Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
            mc.thePlayer.motionY += vSpeed;
        }
        if (Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            mc.thePlayer.motionY -= vSpeed;
        }

        if (Utils.Player.isMoving()) {
            Utils.Player.bop(horizontalSpeed);
        } else {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
        }
    }

    private void updateGlide() {
        if (!glideStarted) {
            glideStarted = true;
            if (mc.thePlayer.onGround) {
                mc.thePlayer.jump();
            }
        }

        if (mc.thePlayer.onGround && glideStarted) {
            mc.thePlayer.jump();
        }

        mc.thePlayer.motionY = -0.03D * verticalSpeed.getInput();
        if (Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
            mc.thePlayer.motionY = 0.12D * verticalSpeed.getInput();
        } else if (Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            mc.thePlayer.motionY = -0.14D * verticalSpeed.getInput();
        }

        if (Utils.Player.isMoving()) {
            Utils.Player.bop(0.33D * speed.getInput());
        } else {
            mc.thePlayer.motionX *= 0.91D;
            mc.thePlayer.motionZ *= 0.91D;
        }
    }

    private void updateAirWalk() {
        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.onGround = true;
        mc.thePlayer.fallDistance = 0.0F;

        if (Utils.Player.isMoving()) {
            Utils.Player.bop(0.24D * speed.getInput());
        } else {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
        }
    }

    public enum FlyMode {
        Vanilla, Glide, AirWalk
    }
}
