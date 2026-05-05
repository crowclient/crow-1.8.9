package crow.client.module.modules.render;

import crow.client.module.Module;
import crow.client.module.setting.impl.DescriptionSetting;
import crow.client.utils.Utils;

public class AntiShuffle extends Module {
    public static DescriptionSetting a;
    private static final String c = "§k";

    public AntiShuffle() {
        super("AntiShuffle", ModuleCategory.render);
        this.registerSetting(a = new DescriptionSetting(Utils.Java.capitalizeWord("remove") + " &k"));
    }

    public static String getUnformattedTextForChat(String s) {
        return s.replace(c, "");
    }
}
