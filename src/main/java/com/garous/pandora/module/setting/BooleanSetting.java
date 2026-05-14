package com.garous.pandora.module.setting;

public class BooleanSetting extends ModuleSetting<Boolean> {
    private boolean value;

    public BooleanSetting(String id, String label, boolean defaultValue) {
        super(id, label);
        this.value = defaultValue;
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    public void toggle() {
        value = !value;
    }

    @Override
    public String getDisplayValue() {
        return value ? "on" : "off";
    }
}

