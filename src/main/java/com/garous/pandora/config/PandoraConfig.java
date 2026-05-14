package com.garous.pandora.config;

import com.garous.pandora.Pandora;
import com.garous.pandora.module.Category;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PandoraConfig {
    private static final PandoraConfig INSTANCE = new PandoraConfig();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("pandora.json");
    private ConfigData data = new ConfigData();

    public static PandoraConfig getInstance() {
        return INSTANCE;
    }

    public void load() {
        if (!Files.exists(configPath)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            ConfigData loaded = gson.fromJson(reader, ConfigData.class);
            data = loaded == null ? new ConfigData() : loaded;
            data.ensureMaps();
        } catch (Exception exception) {
            Pandora.LOGGER.warn("[Pandora] Failed to load config, using defaults.", exception);
            data = new ConfigData();
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
        } catch (IOException exception) {
            Pandora.LOGGER.error("[Pandora] Failed to save config.", exception);
        }
    }

    public boolean isModuleEnabled(String moduleName) {
        ModuleState state = data.modules.get(normalize(moduleName));
        return state != null && state.enabled;
    }

    public void setModuleEnabled(String moduleName, boolean enabled) {
        ModuleState state = data.modules.computeIfAbsent(normalize(moduleName), key -> new ModuleState());
        state.enabled = enabled;
        save();
    }

    public boolean getModuleOption(String moduleName, String optionName, boolean defaultValue) {
        ModuleState state = data.modules.get(normalize(moduleName));
        if (state == null) {
            return defaultValue;
        }
        state.ensureOptions();
        return state.options.getOrDefault(normalize(optionName), defaultValue);
    }

    public void setModuleOption(String moduleName, String optionName, boolean enabled) {
        ModuleState state = data.modules.computeIfAbsent(normalize(moduleName), key -> new ModuleState());
        state.ensureOptions();
        state.options.put(normalize(optionName), enabled);
        save();
    }

    public String getModuleTextOption(String moduleName, String optionName, String defaultValue) {
        ModuleState state = data.modules.get(normalize(moduleName));
        if (state == null) {
            return defaultValue;
        }
        state.ensureOptions();
        return state.textOptions.getOrDefault(normalize(optionName), defaultValue);
    }

    public void setModuleTextOption(String moduleName, String optionName, String value) {
        ModuleState state = data.modules.computeIfAbsent(normalize(moduleName), key -> new ModuleState());
        state.ensureOptions();
        state.textOptions.put(normalize(optionName), normalize(value));
        save();
    }

    public int getThemeFrequency(String theme) {
        return data.themeFrequency.getOrDefault(normalize(theme), 0);
    }

    public void incrementThemeFrequency(String theme) {
        String key = normalize(theme);
        data.themeFrequency.merge(key, 1, Integer::sum);
        save();
    }

    public PanelState getPanelState(Category category, int defaultX, int defaultY) {
        PanelState state = data.panels.computeIfAbsent(categoryKey(category), key -> new PanelState(defaultX, defaultY, true));
        if (state.x == null) {
            state.x = defaultX;
        }
        if (state.y == null) {
            state.y = defaultY;
        }
        return state.copy();
    }

    public void setPanelState(Category category, int x, int y, boolean expanded) {
        data.panels.put(categoryKey(category), new PanelState(x, y, expanded));
        save();
    }

    private static String categoryKey(Category category) {
        return category.getDisplayName().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).trim();
    }

    private static class ConfigData {
        private Map<String, ModuleState> modules = new HashMap<>();
        private Map<String, PanelState> panels = new HashMap<>();
        private Map<String, Integer> themeFrequency = new HashMap<>();

        private void ensureMaps() {
            if (modules == null) {
                modules = new HashMap<>();
            }
            modules.values().forEach(ModuleState::ensureOptions);
            if (panels == null) {
                panels = new HashMap<>();
            }
            if (themeFrequency == null) {
                themeFrequency = new HashMap<>();
            }
        }
    }

    private static class ModuleState {
        private boolean enabled;
        private Map<String, Boolean> options = new HashMap<>();
        private Map<String, String> textOptions = new HashMap<>();

        private void ensureOptions() {
            if (options == null) {
                options = new HashMap<>();
            }
            if (textOptions == null) {
                textOptions = new HashMap<>();
            }
        }
    }

    public static class PanelState {
        private Integer x;
        private Integer y;
        private boolean expanded;

        private PanelState() {
        }

        private PanelState(int x, int y, boolean expanded) {
            this.x = x;
            this.y = y;
            this.expanded = expanded;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public boolean expanded() {
            return expanded;
        }

        private PanelState copy() {
            return new PanelState(x, y, expanded);
        }
    }
}
