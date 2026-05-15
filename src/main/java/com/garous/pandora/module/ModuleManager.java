package com.garous.pandora.module;

import com.garous.pandora.config.PandoraConfig;
import com.garous.pandora.module.modules.FullbrightModule;
import com.garous.pandora.module.modules.GTBSolverModule;
import com.garous.pandora.module.modules.HudModule;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private static ModuleManager instance;
    private final List<Module> modules = new ArrayList<>();

    public static ModuleManager getInstance() {
        if (instance == null) {
            instance = new ModuleManager();
        }
        return instance;
    }

    private ModuleManager() {
        // Register modules
        register(new GTBSolverModule());
        register(new FullbrightModule());
        register(new HudModule());
        loadSavedModuleStates();
    }

    private void register(Module module) {
        modules.add(module);
    }

    private void loadSavedModuleStates() {
        PandoraConfig config = PandoraConfig.getInstance();
        config.load();
        for (Module module : modules) {
            module.onConfigLoaded();
            module.applySavedEnabledState(config.isModuleEnabled(module.getName()));
        }
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesByCategory(Category category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    public Module getModule(String name) {
        return modules.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public void onTick() {
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick();
            }
        }
    }
}
