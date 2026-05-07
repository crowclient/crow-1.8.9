package crow.client.utils;

import crow.client.main.Crow;
import crow.client.event.impl.ForgeEvent;
import crow.client.module.modules.world.AntiBot;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class MouseManager {
    private static final List<Long> leftClicks = new ArrayList<>();
    private static final List<Long> rightClicks = new ArrayList<>();
    public static long leftClickTimer;
    public static long rightClickTimer;
    private static boolean wasLeftDown;
    private static boolean wasRightDown;

    public static void update() {
        boolean leftDown = Mouse.isButtonDown(0);
        boolean rightDown = Mouse.isButtonDown(1);

        if (leftDown && !wasLeftDown) {
            addLeftClick();
            postMouseEvent(0, true);
            if (Crow.debugger && Minecraft.getMinecraft().objectMouseOver != null) {
                Entity en = Minecraft.getMinecraft().objectMouseOver.entityHit;
                if (en != null) {
                    Utils.Player.sendMessageToSelf("&7&m-------------------------");
                    Utils.Player.sendMessageToSelf("n: " + en.getName());
                    Utils.Player.sendMessageToSelf("rn: " + en.getName().replace("§", "%"));
                    Utils.Player.sendMessageToSelf("d: " + en.getDisplayName().getUnformattedText());
                    Utils.Player.sendMessageToSelf("rd: " + en.getDisplayName().getUnformattedText().replace("§", "%"));
                    Utils.Player.sendMessageToSelf("b?: " + AntiBot.bot(en));
                }
            }
        }

        if (rightDown && !wasRightDown) {
            addRightClick();
            postMouseEvent(1, true);
        }

        wasLeftDown = leftDown;
        wasRightDown = rightDown;
    }

    private static void postMouseEvent(int button, boolean state) {
        try {
            MouseEvent event = new MouseEvent();
            ObfuscationReflectionHelper.setPrivateValue(MouseEvent.class, event, button, "button");
            ObfuscationReflectionHelper.setPrivateValue(MouseEvent.class, event, state, "buttonstate");
            Crow.eventBus.post(new ForgeEvent(event));
        } catch (Throwable ignored) {
        }
    }

    public static void addLeftClick() {
        leftClicks.add(leftClickTimer = System.currentTimeMillis());
    }

    public static void addRightClick() {
        rightClicks.add(rightClickTimer = System.currentTimeMillis());
    }

    public static int getLeftClickCounter() {
        if (!Utils.Player.isPlayerInGame())
            return leftClicks.size();
        for (Long lon : leftClicks) {
            if (lon < System.currentTimeMillis() - 1000L) {
                leftClicks.remove(lon);
                break;
            }
        }
        return leftClicks.size();
    }

    public static int getRightClickCounter() {
        if (!Utils.Player.isPlayerInGame())
            return leftClicks.size();
        for (Long lon : rightClicks) {
            if (lon < System.currentTimeMillis() - 1000L) {
                rightClicks.remove(lon);
                break;
            }
        }
        return rightClicks.size();
    }
}
