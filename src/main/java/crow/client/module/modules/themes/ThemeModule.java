package crow.client.module.modules.themes;

import java.util.List;

import crow.client.main.Crow;
import crow.client.module.Module;

public abstract class ThemeModule extends Module {

    public ThemeModule(String name) {
        super(name, ModuleCategory.themes);
    }

    @Override
    public void onEnable() {

        List<Module> all = Crow.moduleManager.getModulesInCategory(ModuleCategory.themes);
        for (Module m : all) {
            if (m != this && m.isEnabled()) {
                m.disable();
            }
        }
    }

    public abstract int getColor(int delay);

    protected static int blendARGB(int c1, int c2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return (((int)(a1+(a2-a1)*t)) << 24) | (((int)(r1+(r2-r1)*t)) << 16)
             | (((int)(g1+(g2-g1)*t)) << 8)  |  ((int)(b1+(b2-b1)*t));
    }

    protected static int flowingGradient(int delay, float speed, int... colors) {
        if (colors == null || colors.length == 0) {
            return 0xFFFFFFFF;
        }
        if (colors.length == 1) {
            return colors[0];
        }

        float span = colors.length;

        double travel = ((System.currentTimeMillis() * (double) speed * 0.00115) + ((double) (-delay) / 120.0));
        float wrapped = (float) (travel % span);
        if (wrapped < 0.0F) {
            wrapped += span;
        }

        int index = ((int) Math.floor(wrapped)) % colors.length;
        if (index < 0) index += colors.length;
        int nextIndex = (index + 1) % colors.length;
        float t = wrapped - (float) Math.floor(wrapped);

        float smoothT = (float) ((1.0 - Math.cos(t * Math.PI)) * 0.5);
        return blendARGB(colors[index], colors[nextIndex], smoothT);
    }
}
