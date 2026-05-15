package com.garous.pandora.module.modules;

import com.garous.pandora.Pandora;
import com.garous.pandora.config.PandoraConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent learning store for the GTB solver.
 *
 * Records, per theme, the block tokens observed in past builds and the
 * guesses the solver attempted. The data feeds the scanner's reverse
 * index (token -> themes) and its scoring (per-theme token affinity)
 * so each round makes the next round smarter.
 *
 * Saved to config/pandora-gtb-history.json on every record(); load() is
 * called once at engine startup.
 */
public final class GTBLearningStore {

    private static final GTBLearningStore INSTANCE = new GTBLearningStore();

    /**
     * Per-theme history is unbounded so the bot keeps learning indefinitely;
     * every reveal contributes a new snapshot. Storage budget is enforced by
     * disk only - the JSON grows linearly with rounds played per theme.
     */

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    // Lives next to pandora.json under .minecraft/pandora/. The first-run
    // migration below copies the file from the old .minecraft/config/
    // location if it exists there.
    private final Path filePath = PandoraConfig.getInstance().getConfigDirectory().resolve("gtb-history.json");
    private final Path legacyFilePath = FabricLoader.getInstance().getConfigDir().resolve("pandora-gtb-history.json");

    // theme (lowercase) -> { rounds: int, tokens: {token -> count}, guesses: [...], lastSeen: epochSec }
    private final Map<String, ThemeEntry> themes = new ConcurrentHashMap<>();
    // token -> set of themes that have used the token at least once
    private final Map<String, Set<String>> tokenIndex = new ConcurrentHashMap<>();

    private GTBLearningStore() {}

    public static GTBLearningStore getInstance() {
        return INSTANCE;
    }

    public void load() {
        migrateLegacyIfNeeded();
        if (!Files.exists(filePath)) return;
        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, ThemeEntry>>() {}.getType();
            Map<String, ThemeEntry> loaded = gson.fromJson(reader, type);
            if (loaded == null) return;
            themes.clear();
            tokenIndex.clear();
            for (Map.Entry<String, ThemeEntry> entry : loaded.entrySet()) {
                String key = normalize(entry.getKey());
                ThemeEntry value = entry.getValue();
                if (value == null) continue;
                value.normalize();
                themes.put(key, value);
                for (String token : value.tokens.keySet()) {
                    tokenIndex.computeIfAbsent(token, t -> ConcurrentHashMap.newKeySet()).add(key);
                }
            }
            Pandora.LOGGER.info("[GTBSolver] loaded {} learned theme entries.", themes.size());
        } catch (Exception exception) {
            Pandora.LOGGER.warn("[GTBSolver] Failed to load learning store; starting fresh.", exception);
        }
    }

    private void migrateLegacyIfNeeded() {
        if (Files.exists(filePath)) return;
        if (!Files.exists(legacyFilePath)) return;
        try {
            Files.createDirectories(filePath.getParent());
            Files.copy(legacyFilePath, filePath);
            Pandora.LOGGER.info("[GTBSolver] Migrated learning store from {} to {}", legacyFilePath, filePath);
        } catch (IOException exception) {
            Pandora.LOGGER.warn("[GTBSolver] Couldn't migrate legacy learning store.", exception);
        }
    }

    public void save() {
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                gson.toJson(themes, writer);
            }
        } catch (Exception exception) {
            Pandora.LOGGER.warn("[GTBSolver] Failed to save learning store.", exception);
        }
    }

    /**
     * Records a completed round: which theme was revealed, what block tokens
     * appeared in the build, and what guesses we sent. Token counts are
     * accumulated across rounds so frequent associations rise to the top.
     */
    public void recordRound(String theme, Map<String, Integer> tokenCounts, List<String> guessesSent) {
        recordRound(theme, tokenCounts, guessesSent, null);
    }

    /**
     * Detailed variant: also stores the build's block layout (relative
     * coordinates -> block id) for the round, capped at the last
     * round of the theme - unbounded so the database keeps getting more
     * accurate the more the bot plays.
     */
    public void recordRound(String theme, Map<String, Integer> tokenCounts, List<String> guessesSent,
                            Map<String, String> blockLayout) {
        if (theme == null || theme.isBlank()) return;
        String key = normalize(theme);
        ThemeEntry entry = themes.computeIfAbsent(key, k -> new ThemeEntry());
        entry.rounds++;
        entry.lastSeen = System.currentTimeMillis() / 1000L;
        if (tokenCounts != null) {
            for (Map.Entry<String, Integer> tc : tokenCounts.entrySet()) {
                String token = tc.getKey();
                if (token == null || token.isBlank()) continue;
                entry.tokens.merge(token, Math.max(1, tc.getValue()), Integer::sum);
                tokenIndex.computeIfAbsent(token, t -> ConcurrentHashMap.newKeySet()).add(key);
            }
        }
        if (guessesSent != null) {
            for (String guess : guessesSent) {
                if (guess == null || guess.isBlank()) continue;
                entry.guesses.add(guess);
                if (entry.guesses.size() > 20) {
                    entry.guesses.remove(0);
                }
            }
        }
        if (blockLayout != null && !blockLayout.isEmpty()) {
            RoundSnapshot snapshot = new RoundSnapshot();
            snapshot.ts = entry.lastSeen;
            snapshot.blocks = new HashMap<>(blockLayout);
            if (guessesSent != null) snapshot.guesses = new ArrayList<>(guessesSent);
            entry.history.add(snapshot);
            // No history cap - the more rounds the bot sees, the better.
        }
        save();
    }

    /**
     * @return recent block layouts the bot has seen for this theme. Useful for
     * shape-based comparison against the current build.
     */
    public List<Map<String, String>> recentLayouts(String theme) {
        ThemeEntry entry = themes.get(normalize(theme));
        if (entry == null || entry.history.isEmpty()) return List.of();
        List<Map<String, String>> out = new ArrayList<>(entry.history.size());
        for (RoundSnapshot snap : entry.history) {
            if (snap.blocks != null && !snap.blocks.isEmpty()) {
                out.add(Collections.unmodifiableMap(snap.blocks));
            }
        }
        return out;
    }

    /**
     * @return themes that have historically used this block token, ranked by
     * how strongly they're associated.
     */
    public Set<String> themesForToken(String token) {
        Set<String> hits = tokenIndex.get(token);
        return hits == null ? Set.of() : Collections.unmodifiableSet(hits);
    }

    /**
     * @return a 0..1 affinity for how often this theme has used this token,
     * relative to its other tokens. 0 if the theme has never been seen.
     */
    public double tokenAffinity(String theme, String token) {
        ThemeEntry entry = themes.get(normalize(theme));
        if (entry == null || entry.tokens.isEmpty()) return 0.0;
        int hits = entry.tokens.getOrDefault(token, 0);
        if (hits <= 0) return 0.0;
        int total = entry.tokens.values().stream().mapToInt(Integer::intValue).sum();
        return total <= 0 ? 0.0 : ((double) hits) / total;
    }

    public int roundsObserved(String theme) {
        ThemeEntry entry = themes.get(normalize(theme));
        return entry == null ? 0 : entry.rounds;
    }

    public int size() {
        return themes.size();
    }

    public int totalRoundsObserved() {
        int total = 0;
        for (ThemeEntry entry : themes.values()) {
            total += entry.rounds;
        }
        return total;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static final class ThemeEntry {
        int rounds;
        long lastSeen;
        Map<String, Integer> tokens = new HashMap<>();
        List<String> guesses = new ArrayList<>();
        List<RoundSnapshot> history = new ArrayList<>();

        void normalize() {
            if (tokens == null) tokens = new HashMap<>();
            if (guesses == null) guesses = new ArrayList<>();
            if (history == null) history = new ArrayList<>();
            for (RoundSnapshot snap : history) {
                if (snap.blocks == null) snap.blocks = new HashMap<>();
                if (snap.guesses == null) snap.guesses = new ArrayList<>();
            }
        }
    }

    private static final class RoundSnapshot {
        long ts;
        Map<String, String> blocks = new HashMap<>();
        List<String> guesses = new ArrayList<>();
    }
}
