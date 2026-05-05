package crow.client.module.setting;

import com.google.gson.JsonObject;

import crow.client.clickgui.kv.KvComponent;
import crow.client.clickgui.crow.Component;
import crow.client.clickgui.crow.components.ModuleComponent;
import crow.client.clickgui.crow.components.SettingComponent;

import java.util.function.BooleanSupplier;

public abstract class Setting {
    public String settingName;
    private SettingComponent component;
    private BooleanSupplier visibilityCondition;

    public Setting(String name) {
        this.settingName = name;
    }

    public Setting visibleWhen(BooleanSupplier condition) {
        this.visibilityCondition = condition;
        return this;
    }

    public boolean shouldBeVisible() {
        return visibilityCondition == null || visibilityCondition.getAsBoolean();
    }

    public String getName() {
        return this.settingName;
    }

    public abstract void resetToDefaults();

    public abstract JsonObject getConfigAsJson();

    public abstract String getSettingType();

    public abstract void applyConfigFromJson(JsonObject data);

    public abstract Component createComponent(ModuleComponent moduleComponent);

    public abstract Class<? extends KvComponent> getComponentType();

    public abstract Class<? extends SettingComponent> getCrowComponentType();

    public void setComponent(SettingComponent component) {
        this.component = component;
    }

    public void hideComponent() {
        component.hideComponent();
    }

    public void hideComponent(boolean hidden) {
        component.hideComponent(hidden);
    }
}
