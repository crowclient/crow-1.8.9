package crow.keystroke;

import crow.client.main.Crow;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "crow", name = Crow.CLIENT_NAME, version = "1.3", acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true)

public class KeyStrokeMod {
    private static KeyStroke keyStroke = new KeyStroke();
    private static final KeyStrokeRenderer keyStrokeRenderer = new KeyStrokeRenderer();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new KeyStrokeCommand());
        MinecraftForge.EVENT_BUS.register(keyStrokeRenderer);
        Crow.init();
        Crow.clientConfig.applyKeyStrokeSettingsFromConfigFile();
    }

    public static KeyStroke getKeyStroke() {
        return keyStroke;
    }

    public static KeyStrokeRenderer getKeyStrokeRenderer() {
        return keyStrokeRenderer;
    }
}
