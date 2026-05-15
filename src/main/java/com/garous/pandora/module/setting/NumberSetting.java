package com.garous.pandora.module.setting;

/**
 * Integer-valued setting rendered as a draggable slider in the ClickGUI.
 * Use for things like delay seconds, animation speed, offsets.
 */
public class NumberSetting extends ModuleSetting<Integer> {
    private final int min;
    private final int max;
    private int value;

    public NumberSetting(String id, String label, int min, int max, int defaultValue) {
        super(id, label);
        this.min = min;
        this.max = max;
        this.value = clamp(defaultValue);
    }

    @Override
    public Integer getValue() {
        return value;
    }

    public void setValue(int newValue) {
        this.value = clamp(newValue);
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    private int clamp(int v) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    @Override
    public String getDisplayValue() {
        return Integer.toString(value);
    }
}
