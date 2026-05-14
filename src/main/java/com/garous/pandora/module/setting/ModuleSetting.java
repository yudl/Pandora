package com.garous.pandora.module.setting;

public abstract class ModuleSetting<T> {
    private final String id;
    private final String label;

    protected ModuleSetting(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public abstract T getValue();

    public abstract String getDisplayValue();
}

