package com.garous.pandora.module.setting;

/**
 * Two-handle slider (min + max within an overall range). Used for the auto-
 * guess delay where the bot picks a random value in [low, high] each send.
 */
public class NumberRangeSetting extends ModuleSetting<int[]> {
    private final int min;
    private final int max;
    private int low;
    private int high;
    private final String unitSuffix;

    public NumberRangeSetting(String id, String label, int min, int max,
                              int defaultLow, int defaultHigh, String unitSuffix) {
        super(id, label);
        this.min = min;
        this.max = max;
        this.unitSuffix = unitSuffix == null ? "" : unitSuffix;
        this.low = clamp(defaultLow);
        this.high = clamp(defaultHigh);
        if (this.high < this.low) this.high = this.low;
    }

    @Override
    public int[] getValue() {
        return new int[]{low, high};
    }

    public int getLow() {
        return low;
    }

    public int getHigh() {
        return high;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public void setLow(int newLow) {
        this.low = clamp(newLow);
        if (this.high < this.low) this.high = this.low;
    }

    public void setHigh(int newHigh) {
        this.high = clamp(newHigh);
        if (this.high < this.low) this.low = this.high;
    }

    public String getUnitSuffix() {
        return unitSuffix;
    }

    private int clamp(int v) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    @Override
    public String getDisplayValue() {
        return low + "-" + high + unitSuffix;
    }
}
