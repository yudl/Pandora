package com.garous.pandora.module.setting;

import java.util.List;
import java.util.Locale;

public class ModeSetting extends ModuleSetting<String> {
    private final List<String> options;
    private int selectedIndex;

    public ModeSetting(String id, String label, List<String> options, String defaultValue) {
        super(id, label);
        this.options = List.copyOf(options);
        setValue(defaultValue);
    }

    public List<String> getOptions() {
        return options;
    }

    @Override
    public String getValue() {
        return options.get(selectedIndex);
    }

    public void setValue(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).equalsIgnoreCase(normalized)) {
                selectedIndex = index;
                return;
            }
        }
        selectedIndex = 0;
    }

    public void setIndex(int index) {
        if (index >= 0 && index < options.size()) {
            selectedIndex = index;
        }
    }

    public boolean is(String value) {
        return getValue().equalsIgnoreCase(value);
    }

    @Override
    public String getDisplayValue() {
        return getValue();
    }
}

