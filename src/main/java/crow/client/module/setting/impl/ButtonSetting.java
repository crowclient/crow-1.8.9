package crow.client.module.setting.impl;

import com.google.gson.JsonObject;

import crow.client.clickgui.kv.KvComponent;
import crow.client.clickgui.kv.components.KvTickComponent;
import crow.client.clickgui.crow.Component;
import crow.client.clickgui.crow.components.ButtonComponent;
import crow.client.clickgui.crow.components.ModuleComponent;
import crow.client.clickgui.crow.components.SettingComponent;
import crow.client.module.setting.Setting;

public class ButtonSetting extends Setting {

    private final String name;
    private final Runnable action;

    public ButtonSetting(String name) {
        this(name, null);
    }

    public ButtonSetting(String name, Runnable action) {
        super(name);
        this.name = name;
        this.action = action;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void resetToDefaults() {

    }

    @Override
    public JsonObject getConfigAsJson() {
        JsonObject data = new JsonObject();
        data.addProperty("type", getSettingType());
        return data;
    }

    @Override
    public String getSettingType() {
        return "button";
    }

    @Override
    public void applyConfigFromJson(JsonObject data) {

    }

    @Override
    public Component createComponent(ModuleComponent moduleComponent) {
        return null;
    }

    public void press() {
        if (action != null) {
            action.run();
        }
    }

    @Override
    public Class<? extends SettingComponent> getCrowComponentType() {
        return ButtonComponent.class;
    }

    @Override
    public Class<? extends KvComponent> getComponentType() {
        return KvTickComponent.class;
    }

}
