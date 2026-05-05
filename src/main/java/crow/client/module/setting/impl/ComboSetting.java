package crow.client.module.setting.impl;

import com.google.gson.JsonObject;

import crow.client.clickgui.kv.KvComponent;
import crow.client.clickgui.kv.components.KvComboComponent;
import crow.client.clickgui.crow.Component;
import crow.client.clickgui.crow.components.ComboComponent;
import crow.client.clickgui.crow.components.ModuleComponent;
import crow.client.clickgui.crow.components.SettingComponent;
import crow.client.module.setting.Setting;

public class ComboSetting<T extends Enum<?>> extends Setting {
    private T[] options;
    private T currentOption;
    private final T defaultOption;

    public ComboSetting(String settingName, T defaultOption) {
        super(settingName);

        this.currentOption = defaultOption;
        this.defaultOption = defaultOption;

        try {
            this.options = (T[]) defaultOption.getClass().getMethod("values").invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void resetToDefaults() {
        this.currentOption = defaultOption;
    }

    @Override
    public JsonObject getConfigAsJson() {
        JsonObject data = new JsonObject();
        data.addProperty("type", getSettingType());
        data.addProperty("value", getMode().toString());
        return data;
    }

    @Override
    public String getSettingType() {
        return "mode";
    }

    @Override
    public void applyConfigFromJson(JsonObject data) {
        if (!data.get("type").getAsString().equals(getSettingType()))
            return;

        String bruh = data.get("value").getAsString();
        for (T opt : options)
            if (opt.toString().equals(bruh))
                setMode(opt);
    }

    @Override
    public Component createComponent(ModuleComponent moduleComponent) {
        return null;
    }

    public T getMode() {
        ensureValidState();
        return this.currentOption;
    }

    public T[] getOptions() {
        ensureValidState();
        return options;
    }

    public void setMode(T value) {
        ensureOptionsInitialized(value == null ? defaultOption : value);
        this.currentOption = value;
    }

    public void nextMode() {
        ensureValidState();
        if (options == null || options.length == 0 || currentOption == null) {
            return;
        }
        currentOption = options[(currentOption.ordinal() + 1) % (options.length)];
    }

    public void prevMode() {
        ensureValidState();
        if (options == null || options.length == 0 || currentOption == null) {
            return;
        }
        currentOption = options[currentOption.ordinal() == 0 ? options.length - 1 : currentOption.ordinal() - 1];
    }

    public T getPrevMode() {
        ensureValidState();
        if (options == null || options.length == 0 || currentOption == null) {
            return defaultOption;
        }
        return options[currentOption.ordinal() == 0 ? options.length - 1 : currentOption.ordinal() - 1];
    }

    private void ensureValidState() {
        ensureOptionsInitialized(defaultOption);
        if ((currentOption == null) && options != null && options.length > 0) {
            currentOption = defaultOption != null ? defaultOption : options[0];
        }
    }

    @SuppressWarnings("unchecked")
    private void ensureOptionsInitialized(T fallback) {
        if (options != null && options.length > 0) {
            return;
        }
        T seed = fallback != null ? fallback : currentOption;
        if (seed == null) {
            return;
        }
        try {
            this.options = (T[]) seed.getClass().getMethod("values").invoke(null);
        } catch (Exception ignored) {
        }
    }

	@Override
	public Class<? extends KvComponent> getComponentType() {
		return KvComboComponent.class;
	}

    @Override
    public Class<? extends SettingComponent> getCrowComponentType() {
        return ComboComponent.class;
    }
}
