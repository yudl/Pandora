package com.garous.pandora.module.modules;

import com.garous.pandora.config.PandoraConfig;
import com.garous.pandora.module.Category;
import com.garous.pandora.module.Module;
import com.garous.pandora.module.setting.BooleanSetting;
import com.garous.pandora.module.setting.ModeSetting;
import com.garous.pandora.module.setting.ModuleSetting;
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

    // Auto-guess delay presets. The bot picks a random delay in the given
    // [min, max] range each send so the cadence looks human.
    public static final String DELAY_FAST = "1-2s";
    public static final String DELAY_QUICK = "2-3s";
    public static final String DELAY_NATURAL = "3-5s";
    public static final String DELAY_CASUAL = "3-6s";
    public static final String DELAY_RELAXED = "4-7s";
    public static final String DELAY_SLOW = "5-10s";

    private final ModeSetting guessMode = new ModeSetting(
            "guess_mode",
            "mode",
            List.of(MODE_MANUAL, MODE_AUTO_HINTS, MODE_AUTO_HINTS_SCANNER),
            MODE_MANUAL
    );
    private final ModeSetting autoGuessDelay = new ModeSetting(
            "auto_guess_delay",
            "delay",
            List.of(DELAY_FAST, DELAY_QUICK, DELAY_NATURAL, DELAY_CASUAL, DELAY_RELAXED, DELAY_SLOW),
            DELAY_NATURAL
    );
    private final BooleanSetting rotateMatches = new BooleanSetting("rotate_matches", "rotate", true);
    private final BooleanSetting activeRoundOnly = new BooleanSetting("active_round_only", "round-only", true);
    private final BooleanSetting guessHistoryHud = new BooleanSetting("guess_history_hud", "history hud", true);

    private final List<ModuleSetting<?>> settings = List.of(
            guessMode,
            autoGuessDelay,
            rotateMatches,
            activeRoundOnly,
            guessHistoryHud
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
        autoGuessDelay.setValue(config.getModuleTextOption(getName(), autoGuessDelay.getId(), DELAY_NATURAL));
        rotateMatches.setValue(config.getModuleOption(getName(), rotateMatches.getId(), true));
        activeRoundOnly.setValue(config.getModuleOption(getName(), activeRoundOnly.getId(), true));
        guessHistoryHud.setValue(config.getModuleOption(getName(), guessHistoryHud.getId(), true));
    }

    public int[] getAutoGuessDelayMillis() {
        String value = autoGuessDelay.getValue();
        // Format "<min>-<max>s"; default to natural if parse fails.
        try {
            String[] parts = value.replace("s", "").split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return new int[]{min * 1000, max * 1000};
        } catch (Exception ignored) {
            return new int[]{3_000, 5_000};
        }
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
        GTBSolverEngine.getInstance().tick(
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
