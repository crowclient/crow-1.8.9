package crow.client.mixin;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.8.9")
public class MixinLoader implements IFMLLoadingPlugin {
    public MixinLoader() {
        ensureOwnJarOnSystemClasspath();
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.crow.json");
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
    }

    private static void ensureOwnJarOnSystemClasspath() {
        try {
            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
            if (!(classLoader instanceof URLClassLoader)) {
                return;
            }

            URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
            URL ownJar = MixinLoader.class.getProtectionDomain().getCodeSource().getLocation();
            for (URL url : urlClassLoader.getURLs()) {
                if (url.equals(ownJar)) {
                    return;
                }
            }

            Method addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);
            addUrl.invoke(urlClassLoader, ownJar);
        } catch (Throwable ignored) {
        }
    }

    @NotNull
    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Nullable
    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Nullable
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
