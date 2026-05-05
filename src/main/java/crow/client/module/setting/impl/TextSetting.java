package crow.client.module.setting.impl;

import com.google.gson.JsonObject;

import crow.client.clickgui.kv.KvComponent;
import crow.client.clickgui.kv.components.KvDescriptionComponent;
import crow.client.clickgui.crow.Component;
import crow.client.clickgui.crow.components.ModuleComponent;
import crow.client.clickgui.crow.components.SettingComponent;
import crow.client.clickgui.crow.components.TextInputComponent;
import crow.client.module.setting.Setting;

public class TextSetting extends Setting {
    private String value;
    private final String defaultValue;
    private final String placeholder;

    public TextSetting(String name, String defaultValue, String placeholder) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.placeholder = placeholder;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    @Override
    public void resetToDefaults() {
        this.value = defaultValue;
    }

    @Override
    public JsonObject getConfigAsJson() {
        JsonObject data = new JsonObject();
        data.addProperty("type", getSettingType());
        data.addProperty("value", value);
        return data;
    }

    @Override
    public String getSettingType() {
        return "text";
    }

    @Override
    public void applyConfigFromJson(JsonObject data) {
        if (!data.get("type").getAsString().equals(getSettingType()))
            return;
        if (data.has("value")) {
            value = data.get("value").getAsString();
        }
    }

    @Override
    public Component createComponent(ModuleComponent moduleComponent) {
        return null;
    }

    @Override
    public Class<? extends SettingComponent> getCrowComponentType() {
        return TextInputComponent.class;
    }

    @Override
    public Class<? extends KvComponent> getComponentType() {
        return KvDescriptionComponent.class;
    }
}
