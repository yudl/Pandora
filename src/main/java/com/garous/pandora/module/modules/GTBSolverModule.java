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
    public static final String MODE_AUTO_HINTS = "auto hints";
    public static final String MODE_AUTO_HINTS_PREHINT = "auto hints + prehint";

    private final ModeSetting guessMode = new ModeSetting(
            "guess_mode",
            "guess mode",
            List.of(MODE_MANUAL, MODE_AUTO_HINTS, MODE_AUTO_HINTS_PREHINT),
            MODE_MANUAL
    );
    private final BooleanSetting rotateMatches = new BooleanSetting("rotate_matches", "rotate matches", true);
    private final BooleanSetting activeRoundOnly = new BooleanSetting("active_round_only", "active round only", true);
    private final BooleanSetting guessHistoryHud = new BooleanSetting("guess_history_hud", "guess history hud", true);

    private final List<ModuleSetting<?>> settings = List.of(
            guessMode,
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
        guessMode.setValue(config.getModuleTextOption(getName(), guessMode.getId(), MODE_MANUAL));
        rotateMatches.setValue(config.getModuleOption(getName(), rotateMatches.getId(), true));
        activeRoundOnly.setValue(config.getModuleOption(getName(), activeRoundOnly.getId(), true));
        guessHistoryHud.setValue(config.getModuleOption(getName(), guessHistoryHud.getId(), true));
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
        GTBSolverEngine.getInstance().tick(
                guessMode.getValue(),
                rotateMatches.getValue(),
                activeRoundOnly.getValue()
        );
    }

    @Override
    public String getStatusText() {
        if (!isEnabled()) {
            return "-";
        }
        if (guessMode.is(MODE_AUTO_HINTS_PREHINT)) {
            return "a+p";
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
        if (guessMode.is(MODE_AUTO_HINTS_PREHINT)) {
            return "Auto rotates hint guesses and pre-hint plot reads.";
        }
        if (guessMode.is(MODE_AUTO_HINTS)) {
            return "Auto rotates hint guesses.";
        }
        return "Manual clickable suggestions.";
    }
}
