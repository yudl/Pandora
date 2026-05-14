package com.garous.pandora.module;

import com.garous.pandora.config.PandoraConfig;
import com.garous.pandora.module.setting.ModuleSetting;
import net.minecraft.client.MinecraftClient;

import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = false;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
            PandoraConfig.getInstance().setModuleEnabled(name, enabled);
        }
    }

    public void applySavedEnabledState(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        }
    }

    public void onConfigLoaded() {
    }

    public void onRightClick() {
    }

    public String getStatusText() {
        return enabled ? "*" : "-";
    }

    public List<ModuleSetting<?>> getSettings() {
        return Collections.emptyList();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public void onTick() {
    }

    protected MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
