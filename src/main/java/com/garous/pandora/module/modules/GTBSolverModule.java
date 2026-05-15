package com.garous.pandora.module.modules;

import com.garous.pandora.config.PandoraConfig;
import com.garous.pandora.module.Category;
import com.garous.pandora.module.Module;
import com.garous.pandora.module.setting.BooleanSetting;
import com.garous.pandora.module.setting.ModeSetting;
import com.garous.pandora.module.setting.ModuleSetting;
import com.garous.pandora.module.setting.NumberRangeSetting;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Solves Guess The Build by parsing hints and ranking likely themes.
 */
public class GTBSolverModule extends Module {

    public static final String MODE_MANUAL = "manual";
    public static final String MODE_AUTO_HINTS = "auto";
    public static final String MODE_AUTO_HINTS_SCANNER = "auto + scanner";
    // Back-compat: older configs may still hold these legacy mode strings.
    public static final String LEGACY_MODE_AUTO_HINTS = "auto hints";
    public static final String LEGACY_MODE_AUTO_HINTS_PREHINT = "auto hints + prehint";

    private final ModeSetting guessMode = new ModeSetting(
            "guess_mode",
            "mode",
            List.of(MODE_MANUAL, MODE_AUTO_HINTS, MODE_AUTO_HINTS_SCANNER),
            MODE_MANUAL
    );
    private final NumberRangeSetting autoGuessDelay = new NumberRangeSetting(
            "auto_guess_delay",
            "delay",
            1, 10, 3, 5, "s"
    );
    private final BooleanSetting rotateMatches = new BooleanSetting("rotate_matches", "rotate", true);
    private final BooleanSetting activeRoundOnly = new BooleanSetting("active_round_only", "round-only", true);
    private final BooleanSetting guessHistoryHud = new BooleanSetting("guess_history_hud", "history hud", true);
    private final BooleanSetting automated = new BooleanSetting("automated", "automated", false);

    private final List<ModuleSetting<?>> settings = List.of(
            guessMode,
            autoGuessDelay,
            rotateMatches,
            activeRoundOnly,
            guessHistoryHud,
            automated
    );

    public GTBSolverModule() {
        super("gtb solver", "Solves Hypixel Guess The Build by matching hint patterns to known theme words.", Category.MINIGAMES);
    }

    @Override
    public List<ModuleSetting<?>> getSettings() {
        return settings;
    }

    @Override
    public void onConfigLoaded() {
        PandoraConfig config = PandoraConfig.getInstance();
        String savedMode = config.getModuleTextOption(getName(), guessMode.getId(), MODE_MANUAL);
        guessMode.setValue(migrateLegacyMode(savedMode));
        loadDelayFromConfig(config);
        rotateMatches.setValue(config.getModuleOption(getName(), rotateMatches.getId(), true));
        activeRoundOnly.setValue(config.getModuleOption(getName(), activeRoundOnly.getId(), true));
        guessHistoryHud.setValue(config.getModuleOption(getName(), guessHistoryHud.getId(), true));
        automated.setValue(config.getModuleOption(getName(), automated.getId(), false));
    }

    private void loadDelayFromConfig(PandoraConfig config) {
        // Migrates the historical string presets ("3-5s") and reads back our
        // own slider-encoded "low-high" strings.
        String stored = config.getModuleTextOption(getName(), autoGuessDelay.getId(), "3-5");
        try {
            String trimmed = stored.replace("s", "").trim();
            String[] parts = trimmed.split("-");
            int lo = Integer.parseInt(parts[0].trim());
            int hi = Integer.parseInt(parts[1].trim());
            autoGuessDelay.setLow(lo);
            autoGuessDelay.setHigh(hi);
        } catch (Exception ignored) {
            autoGuessDelay.setLow(3);
            autoGuessDelay.setHigh(5);
        }
    }

    public int[] getAutoGuessDelayMillis() {
        return new int[]{autoGuessDelay.getLow() * 1000, autoGuessDelay.getHigh() * 1000};
    }

    private static String migrateLegacyMode(String value) {
        if (value == null) return MODE_MANUAL;
        String normalized = value.toLowerCase(java.util.Locale.ROOT).trim();
        if (LEGACY_MODE_AUTO_HINTS_PREHINT.equals(normalized)) return MODE_AUTO_HINTS_SCANNER;
        if (LEGACY_MODE_AUTO_HINTS.equals(normalized)) return MODE_AUTO_HINTS;
        return normalized;
    }

    @Override
    protected void onEnable() {
        if (mc() != null && mc().player != null) {
            mc().player.sendMessage(pandoraMessage("GTB Solver enabled. " + modeDescription(), Formatting.GREEN), false);
        }
    }

    @Override
    protected void onDisable() {
        GTBSolverEngine.getInstance().reset();
        if (mc() != null && mc().player != null) {
            mc().player.sendMessage(pandoraMessage("GTB Solver disabled.", Formatting.RED), false);
        }
    }

    @Override
    public void onTick() {
        int[] delay = getAutoGuessDelayMillis();
        GTBSolverEngine engine = GTBSolverEngine.getInstance();
        engine.setAutomatedMode(automated.getValue());
        engine.tick(
                guessMode.getValue(),
                rotateMatches.getValue(),
                activeRoundOnly.getValue(),
                delay[0],
                delay[1]
        );
    }

    @Override
    public String getStatusText() {
        if (!isEnabled()) {
            return "-";
        }
        if (guessMode.is(MODE_AUTO_HINTS_SCANNER)) {
            return "a+s";
        }
        if (guessMode.is(MODE_AUTO_HINTS)) {
            return "a";
        }
        return "m";
    }

    public void setGuessMode(String value) {
        guessMode.setValue(value);
        PandoraConfig.getInstance().setModuleTextOption(getName(), guessMode.getId(), guessMode.getValue());
    }

    public void setRotateMatches(boolean value) {
        rotateMatches.setValue(value);
        PandoraConfig.getInstance().setModuleOption(getName(), rotateMatches.getId(), value);
    }

    public void setActiveRoundOnly(boolean value) {
        activeRoundOnly.setValue(value);
        PandoraConfig.getInstance().setModuleOption(getName(), activeRoundOnly.getId(), value);
    }

    public boolean isGuessHistoryHudEnabled() {
        return guessHistoryHud.getValue();
    }

    private Text pandoraMessage(String message, Formatting statusColor) {
        MutableText prefix = Text.literal("[Pandora] ").formatted(Formatting.LIGHT_PURPLE);
        return prefix.append(Text.literal(message).formatted(Formatting.GRAY, statusColor));
    }

    private String modeDescription() {
        if (guessMode.is(MODE_AUTO_HINTS_SCANNER)) {
            return "Auto-guesses hints and scans the build for pre-hint guesses.";
        }
        if (guessMode.is(MODE_AUTO_HINTS)) {
            return "Auto-guesses hint matches.";
        }
        return "Manual clickable suggestions.";
    }
}
