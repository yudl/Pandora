package com.garous.pandora.module.modules;

import com.garous.pandora.module.Category;
import com.garous.pandora.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

/**
 * Fullbright: cranks the client gamma so caves and night look fully lit. Saves
 * the player's original gamma on enable and restores it on disable.
 */
public class FullbrightModule extends Module {

    private static final double FULLBRIGHT_GAMMA = 16.0;
    private Double savedGamma;

    public FullbrightModule() {
        super("fullbright", "Maxes out client gamma so caves and night are fully lit.", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        applyGamma(FULLBRIGHT_GAMMA, true);
    }

    @Override
    protected void onDisable() {
        if (savedGamma != null) {
            applyGamma(savedGamma, false);
            savedGamma = null;
        }
    }

    private void applyGamma(double value, boolean saveCurrent) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        GameOptions options = client.options;
        if (options == null) return;
        SimpleOption<Double> gamma = options.getGamma();
        if (gamma == null) return;
        if (saveCurrent && savedGamma == null) {
            savedGamma = gamma.getValue();
        }
        gamma.setValue(value);
    }
}
