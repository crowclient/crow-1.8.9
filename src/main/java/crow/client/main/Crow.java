package crow.client.main;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;

import crow.client.clickgui.compact.CompactGui;
import crow.client.clickgui.kv.KvCompactGui;
import crow.client.clickgui.crow.ClickGui;
import crow.client.clickgui.spacious.SpaciousGui;
import crow.client.command.CommandManager;
import crow.client.config.ConfigManager;
import crow.client.event.forge.ForgeEventListener;
import crow.client.module.ModuleManager;
import crow.client.notifications.NotificationRenderer;
import crow.client.utils.DebugInfoRenderer;
import crow.client.utils.MouseManager;
import crow.client.utils.PingChecker;
import crow.client.utils.RenderUtils;
import crow.client.utils.SilentAim;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import crow.client.utils.version.VersionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Crow {
    public static final String CLIENT_NAME = "Crow";
    public static final String CHAT_PREFIX = "&7[" + CLIENT_NAME + "] ";

	public static boolean debugger;
    public static final VersionManager versionManager = new VersionManager();
    public static CommandManager commandManager;
    public static final String sourceLocation = "";
    public static final String downloadLocation = "";
    public static final String discord = "";
    public static String[] updateText = {
            "Your version of " + CLIENT_NAME + " (" + versionManager.getClientVersion().toString() + ") is outdated!",
            "Enter the command update into client CommandLine to open the download page",
            "or just enable the update module to get a message in chat.", "",
            "Newest version: " + versionManager.getLatestVersion().toString() };
    public static ConfigManager configManager;
    public static ClientConfig clientConfig;

    public static final ModuleManager moduleManager = new ModuleManager();

    public static ClickGui clickGui;
    public static KvCompactGui kvCompactGui;
    public static CompactGui compactGui;
    public static SpaciousGui spaciousGui;

    private static final ScheduledExecutorService ex = Executors.newScheduledThreadPool(2);

    public static ResourceLocation mResourceLocation;

    public static final String osName, osArch;
    public static final List<Object> registered = new ArrayList<Object>();

    public static final EventBus eventBus = new EventBus(new SubscriberExceptionHandler() {
        @Override
        public void handleException(Throwable exception, SubscriberExceptionContext context) {
            try {
                System.err.println("[Crow] Subscriber error in "
                        + context.getSubscriber().getClass().getSimpleName()
                        + "#" + context.getSubscriberMethod().getName()
                        + " for event " + context.getEvent().getClass().getSimpleName()
                        + ": " + exception);
                exception.printStackTrace();
            } catch (Throwable ignored) {
            }
        }
    });
    public static final Minecraft mc = Minecraft.getMinecraft();

    static {
        osName = System.getProperty("os.name").toLowerCase();
        osArch = System.getProperty("os.arch").toLowerCase();
    }

    private static java.io.File cachedDataDir;
    public static java.io.File getDataDir() {
        if (cachedDataDir != null) return cachedDataDir;

        java.io.File mcDir = Minecraft.getMinecraft().mcDataDir;
        java.io.File newDir = new java.io.File(mcDir, "crow");
        java.io.File oldDir = new java.io.File(mcDir, "keystrokes");

        if (!newDir.exists() && oldDir.isDirectory()) {
            try {
                if (!oldDir.renameTo(newDir)) {

                    cachedDataDir = oldDir;
                    return oldDir;
                }
            } catch (Throwable ignored) {
                cachedDataDir = oldDir;
                return oldDir;
            }
        }
        if (!newDir.isDirectory()) {

            newDir.mkdirs();
        }
        cachedDataDir = newDir;
        return newDir;
    }

    public static void init() {
        register(new Crow());
        register(new DebugInfoRenderer());
        register(new MouseManager());
        register(new PingChecker());
        register(new ForgeEventListener());
        register(SilentAim.instance());
        eventBus.register(NotificationRenderer.notificationRenderer);

        FontUtil.bootstrap();

        Runtime.getRuntime().addShutdownHook(new Thread(ex::shutdown));

        mResourceLocation = RenderUtils.getResourcePath("/assets/crow/crow.png");

        commandManager = new CommandManager();
        clickGui = new ClickGui();
        kvCompactGui = new KvCompactGui();
        compactGui = new CompactGui();
        spaciousGui = new SpaciousGui();
        configManager = new ConfigManager();
        clientConfig = new ClientConfig();
        clientConfig.applyConfig();

    }

    @SuppressWarnings("unused")
    @SubscribeEvent
    public void onChatMessageReceived(ClientChatReceivedEvent event) {
        if (Utils.Player.isPlayerInGame()) {
            String msg = event.message.getUnformattedText();

            if (msg.startsWith("Your new API key is")) {
                Utils.URLS.hypixelApiKey = msg.replace("Your new API key is ", "");
                Utils.Player.sendMessageToSelf("&aSet api key to " + Utils.URLS.hypixelApiKey + "!");
                clientConfig.saveConfig();
            }
        }
    }

    public static void register(Object obj) {
        registered.add(obj);
        MinecraftForge.EVENT_BUS.register(obj);
    }

    public static ScheduledExecutorService getExecutor() {
        return ex;
    }
}
