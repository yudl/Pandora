package com.garous.pandora.module.modules;

import com.garous.pandora.Pandora;
import com.garous.pandora.config.PandoraConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Guess The Build solver: hint matching + lightweight build scanner.
 *
 * Block scanning is fully event-driven (per-block-update packets routed via the
 * Block/Chunk delta mixins). Plot detection no longer scans large world volumes
 * every tick; it anchors once per round to the player's position with a tiny
 * 6-block downward floor probe.
 */
public class GTBSolverEngine {

    private static final char FORMAT_CODE = (char) 0x00A7;
    private static final Pattern LEGACY_YELLOW_HINT = Pattern.compile("(?<=" + FORMAT_CODE + "e).*$");
    private static final Pattern FORMATTING_CODE = Pattern.compile(Pattern.quote(String.valueOf(FORMAT_CODE)) + "[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern THEME_HINT = Pattern.compile("(?i).*\\b(?:theme|hint|word)\\b(?:\\s+(?:is|starts\\s+with|contains))?\\s*:?[\\s-]*(.+)$");
    private static final Pattern THEME_REVEAL = Pattern.compile("(?i).*theme was\\s*:?[\\s-]*(.+?)(?:[!.]|$)");
    private static final Pattern ROUND_OF = Pattern.compile("(?i)round\\s*:?\\s*(\\d+)\\s*/\\s*(\\d+)");

    private static final int MAX_DISPLAYED_MATCHES = 100;
    private static final long AUTO_GUESS_INTERVAL_MS = 3_000L;
    // Scanner re-scoring runs frequently for HUD freshness, but local 'scanner: X'
    // chat notices are throttled to match the chat cooldown.
    private static final long SCAN_INTERVAL_MS = 750L;
    private static final long SCANNER_NOTICE_INTERVAL_MS = 3_000L;
    private static final long ROUND_RE_ANCHOR_INTERVAL_MS = 750L;
    private static final long GAME_SIGNAL_GRACE_MS = 30_000L;
    private static final long ROUND_SIGNAL_GRACE_MS = 25_000L;

    private static final int PLOT_HALF_SIZE = 13;       // 27x27 plot
    private static final int PLOT_SCAN_BELOW = 2;       // scan 2 below floor (signs/buttons)
    private static final int PLOT_SCAN_ABOVE = 30;      // 30 above floor
    private static final int FLOOR_PROBE_RADIUS = 2;    // 5x5 floor lookup
    private static final int FLOOR_PROBE_DEPTH = 6;     // search up to 6 below player
    private static final int MAX_PLACED_BLOCKS = 1500;
    private static final int MIN_SCAN_BLOCKS = 1;
    private static final double MIN_SCAN_SCORE = 4.0;
    private static final double SCAN_LEAD_THRESHOLD = 1.8;
    private static final double SINGLE_BLOCK_LEAD_BONUS = 12.0;

    private static final int HUD_X = 6;
    private static final int HUD_Y = 18;

    private static final Map<String, Double> COMMON_THEME_PRIORS = Map.ofEntries(
            Map.entry("tree", 8.0),
            Map.entry("house", 7.0),
            Map.entry("car", 6.5),
            Map.entry("dog", 6.0),
            Map.entry("cat", 6.0),
            Map.entry("flower", 5.5),
            Map.entry("pizza", 5.5),
            Map.entry("cake", 5.0),
            Map.entry("calculator", 4.5),
            Map.entry("book", 4.5),
            Map.entry("cup", 4.5),
            Map.entry("beach", 4.5),
            Map.entry("bridge", 4.5),
            Map.entry("castle", 4.5),
            Map.entry("treehouse", 4.0),
            Map.entry("traffic light", 4.0),
            Map.entry("cloud", 4.0),
            Map.entry("train", 3.5),
            Map.entry("computer", 3.5),
            Map.entry("orange juice", 3.5),
            Map.entry("crafting table", 3.5),
            Map.entry("swimming pool", 3.5),
            Map.entry("anvil", 3.5),
            Map.entry("tnt", 3.5),
            Map.entry("snowman", 3.5),
            Map.entry("cactus", 3.0),
            Map.entry("bookshelf", 3.0),
            Map.entry("furnace", 3.0)
    );

    private static GTBSolverEngine instance;

    private final List<String> themeWords = new ArrayList<>();
    private final Map<String, String> shortestTranslationMap = new HashMap<>();
    private final Map<String, Set<String>> themeTokenIndex = new HashMap<>();
    private final Map<String, Set<String>> tokenToThemeKeys = new HashMap<>();
    private final Map<String, String> themeKeyToOriginal = new HashMap<>();

    // Hint state
    private String lastHint = "";
    private List<String> lastResults = new ArrayList<>();
    private List<String> pendingResults;

    // Auto-guess state
    private final List<String> autoGuessQueue = new ArrayList<>();
    private final List<String> guessHistory = new ArrayList<>();
    private int autoGuessIndex;
    private long lastAutoGuessAt;

    // Round / game signals
    private String activeRoundKey = "";
    private long lastGameSignalAt;
    private long lastRoundSignalAt;
    private long lastBuilderSignalAt;
    private long lastHintSignalAt;
    private long lastThemeSignalAt;
    private long lastActiveRoundAt;
    private String lastRoundLabel = "";
    private String lastBuilderLabel = "";
    private String lastThemeLabel = "";

    // Scanner state
    // ConcurrentHashMaps because Fabric's BlockUpdate/ChunkDeltaUpdate mixin runs
    // on the network thread (the @At HEAD injection point fires before
    // NetworkThreadUtils.forceMainThread reschedules the rest to the main thread).
    private volatile PlotRegion plotRegion;
    private final ConcurrentHashMap<Long, String> baseline = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> placed = new ConcurrentHashMap<>();
    private long lastScanAt;
    private long lastReanchorAt;
    private String lastScannerGuess = "";
    private String lastScannerTheme = "";
    private long lastScannerNoticeAt;
    private List<ScoredTheme> lastScannerScores = List.of();

    // HUD
    private String hudStatus = "waiting for GTB round";
    private int hudPlacedCount;

    public static GTBSolverEngine getInstance() {
        if (instance == null) {
            instance = new GTBSolverEngine();
        }
        return instance;
    }

    private GTBSolverEngine() {
        loadTranslationData();
        buildThemeTokenIndex();
    }

    // ===================== Public API =====================

    public void processActionBar(Text message) {
        noteGameSignal(message.getString());
        extractHint(message, true).ifPresent(this::processHint);
    }

    public void processMessage(Text message) {
        noteGameSignal(message.getString());
        handleThemeReveal(message.getString());
        clearAutoGuessOnRoundMessage(message.getString());
        extractHint(message, true).ifPresent(this::processHint);
    }

    public void tick(String guessMode, boolean rotateMatches, boolean activeRoundOnly) {
        flushPendingSuggestions();

        long now = System.currentTimeMillis();
        GameContext context = readGameContext();

        if (!context.inGuessTheBuild()) {
            hudStatus = "not in GTB";
            if (now - lastGameSignalAt > GAME_SIGNAL_GRACE_MS) {
                clearAutomationState();
            }
            return;
        }

        if (context.activeRound() && !context.roundKey().equals(activeRoundKey)) {
            beginRound(context);
        }
        if (context.activeRound()) {
            lastActiveRoundAt = now;
        }

        boolean hasRecentRound = context.activeRound() || now - lastActiveRoundAt < ROUND_SIGNAL_GRACE_MS;
        if (activeRoundOnly && !hasRecentRound) {
            hudStatus = "waiting for active round";
            if (now - lastGameSignalAt > GAME_SIGNAL_GRACE_MS) {
                clearAutomationState();
            }
            return;
        }

        boolean autoHints = !GTBSolverModule.MODE_MANUAL.equalsIgnoreCase(guessMode);
        boolean useScanner = GTBSolverModule.MODE_AUTO_HINTS_SCANNER.equalsIgnoreCase(guessMode);

        if (!autoHints) {
            hudStatus = "manual mode";
            autoGuessQueue.clear();
            autoGuessIndex = 0;
            return;
        }

        if (context.revealedThemeVisible()) {
            autoGuessQueue.clear();
            autoGuessIndex = 0;
            hudStatus = "theme visible";
            return;
        }

        if (useScanner && hasRecentRound) {
            ensurePlotAnchor(now);
            if (now - lastScanAt >= SCAN_INTERVAL_MS) {
                lastScanAt = now;
                runScannerScan();
            }
        }

        if (!hudStatus.startsWith("scanner")
                && !hudStatus.startsWith("auto")
                && !hudStatus.startsWith("queued")
                && !hudStatus.startsWith("sent")) {
            hudStatus = autoGuessQueue.isEmpty() ? "collecting guesses" : "auto guessing";
        }
        sendQueuedAutoGuess(rotateMatches);
    }

    public void flushPendingSuggestions() {
        List<String> results;
        synchronized (this) {
            if (pendingResults == null) {
                return;
            }
            results = pendingResults;
            pendingResults = null;
        }
        sendSuggestions(results);
    }

    public void onBlockUpdate(BlockPos pos, BlockState state) {
        if (plotRegion == null || !plotRegion.contains(pos)) {
            return;
        }
        long packed = pos.asLong();
        String newId = state.isAir() ? "air" : Registries.BLOCK.getId(state.getBlock()).getPath();

        // If we never sampled this position, treat the very first update as baseline.
        // (Happens when chunks stream in after the round started.)
        baseline.putIfAbsent(packed, newId);
        String base = baseline.get(packed);

        if (newId.equals(base) || GTBBlockSignatures.isLobbyBlock(newId)) {
            placed.remove(packed);
            return;
        }
        if (placed.size() >= MAX_PLACED_BLOCKS) {
            return;
        }
        placed.put(packed, newId);
    }

    public void renderGuessHistoryHud(DrawContext context) {
        int width = 144;
        int headerColor = 0xE014141A;
        int bodyColor = 0xD8101014;
        int accentColor = 0xFFFF4FD8;
        int textColor = 0xFFFFFFFF;
        int mutedColor = 0xFFB7B7C6;
        int hintColor = 0xFFFFD050;
        int scannerColor = 0xFF7DE38B;

        // Build the lines we want to render, in order, then render them.
        List<HudLine> lines = new ArrayList<>();
        lines.add(new HudLine(hudStatus, mutedColor));

        if (!lastRoundLabel.isBlank() || !lastBuilderLabel.isBlank()) {
            String round = lastRoundLabel.isBlank() ? "?" : lastRoundLabel;
            String builder = lastBuilderLabel.isBlank() ? "" : " · " + truncate(lastBuilderLabel, 14);
            lines.add(new HudLine("round " + round + builder, textColor));
        }
        if (!lastHint.isBlank()) {
            lines.add(new HudLine("hint: " + truncate(lastHint, 22), hintColor));
        }
        for (int i = 0; i < Math.min(lastScannerScores.size(), 3); i++) {
            ScoredTheme st = lastScannerScores.get(i);
            String label = (i == 0 ? "» " : "  ") + truncate(st.theme(), 18);
            lines.add(new HudLine(label, i == 0 ? scannerColor : mutedColor));
        }
        if (!guessHistory.isEmpty()) {
            lines.add(new HudLine("--- recent ---", 0xFF55555F));
            int historySize = Math.min(guessHistory.size(), 4);
            for (int index = 0; index < historySize; index++) {
                String guess = guessHistory.get(guessHistory.size() - 1 - index);
                lines.add(new HudLine(truncate(guess, 22), mutedColor));
            }
        }

        int height = 16 + lines.size() * 10;
        context.fill(HUD_X, HUD_Y, HUD_X + width, HUD_Y + height, bodyColor);
        context.fill(HUD_X, HUD_Y, HUD_X + width, HUD_Y + 14, headerColor);
        context.fill(HUD_X, HUD_Y, HUD_X + 3, HUD_Y + 14, accentColor);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "gtb scanner", HUD_X + 6, HUD_Y + 3, textColor);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, hudPlacedCount + " blocks", HUD_X + width - 56, HUD_Y + 3, mutedColor);

        for (int i = 0; i < lines.size(); i++) {
            HudLine line = lines.get(i);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, line.text, HUD_X + 6, HUD_Y + 18 + i * 10, line.color);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private record HudLine(String text, int color) {}

    public void reset() {
        lastHint = "";
        lastResults = new ArrayList<>();
        pendingResults = null;
        autoGuessQueue.clear();
        guessHistory.clear();
        autoGuessIndex = 0;
        lastAutoGuessAt = 0L;
        activeRoundKey = "";
        lastGameSignalAt = 0L;
        lastRoundSignalAt = 0L;
        lastBuilderSignalAt = 0L;
        lastHintSignalAt = 0L;
        lastThemeSignalAt = 0L;
        lastActiveRoundAt = 0L;
        lastRoundLabel = "";
        lastBuilderLabel = "";
        lastThemeLabel = "";
        plotRegion = null;
        baseline.clear();
        placed.clear();
        lastScanAt = 0L;
        lastReanchorAt = 0L;
        lastScannerGuess = "";
        lastScannerTheme = "";
        lastScannerScores = List.of();
        hudStatus = "waiting for GTB round";
        hudPlacedCount = 0;
    }

    public String getShortestTranslation(String englishWord) {
        return shortestTranslationMap.getOrDefault(englishWord.toLowerCase(Locale.ROOT), englishWord);
    }

    public List<String> getThemeWords() {
        return Collections.unmodifiableList(themeWords);
    }

    // ===================== Theme loading =====================

    private void loadTranslationData() {
        Set<String> seenThemes = new HashSet<>();
        try (InputStream stream = getClass().getResourceAsStream("/assets/pandora/translations-data.json")) {
            if (stream == null) {
                Pandora.LOGGER.error("[GTBSolver] translations-data.json not found in resources.");
                return;
            }
            JsonArray array = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonArray.class);
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("theme")) continue;
                String theme = clean(entry.get("theme").getAsString());
                if (theme.isEmpty()) continue;
                String key = theme.toLowerCase(Locale.ROOT);
                if (seenThemes.add(key)) themeWords.add(theme);
                shortestTranslationMap.put(key, findShortestTranslation(entry, theme));
            }
            themeWords.sort(String.CASE_INSENSITIVE_ORDER);
            Pandora.LOGGER.info("[GTBSolver] Loaded {} Guess The Build themes.", themeWords.size());
        } catch (Exception exception) {
            Pandora.LOGGER.error("[GTBSolver] Failed to load translations-data.json.", exception);
        }
    }

    private String findShortestTranslation(JsonObject entry, String englishTheme) {
        String shortest = englishTheme;
        int shortestLength = codePointLength(shortest);
        if (!entry.has("translations") || !entry.get("translations").isJsonObject()) return shortest;
        for (Map.Entry<String, JsonElement> language : entry.getAsJsonObject("translations").entrySet()) {
            if (!language.getValue().isJsonObject()) continue;
            JsonObject translationObject = language.getValue().getAsJsonObject();
            if (!translationObject.has("translation")) continue;
            String translation = clean(translationObject.get("translation").getAsString());
            if (translation.isEmpty()) continue;
            int length = codePointLength(translation);
            if (length < shortestLength) {
                shortest = translation;
                shortestLength = length;
            }
        }
        return shortest;
    }

    private void buildThemeTokenIndex() {
        for (String theme : themeWords) {
            String key = theme.toLowerCase(Locale.ROOT);
            themeKeyToOriginal.put(key, theme);
            Set<String> tokens = themeNameTokens(theme);
            themeTokenIndex.put(key, tokens);
            for (String token : tokens) {
                tokenToThemeKeys.computeIfAbsent(token, t -> new HashSet<>()).add(key);
            }
        }
    }

    private static Set<String> themeNameTokens(String theme) {
        String normalized = theme.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        Set<String> tokens = new HashSet<>();
        for (String part : normalized.split("\\s+")) {
            if (!part.isBlank() && part.length() >= 2) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    // ===================== Hint extraction =====================

    private Optional<String> extractHint(Text message, boolean allowLooseHint) {
        Optional<String> styledHint = extractYellowText(message);
        if (styledHint.isPresent()) return styledHint;

        String plain = message.getString();
        if (!plain.contains("_")) return Optional.empty();

        String plainWithoutFormatting = stripFormatting(plain);
        int colonIndex = plainWithoutFormatting.lastIndexOf(':');
        if (colonIndex >= 0 && plainWithoutFormatting.substring(colonIndex + 1).contains("_")) {
            return normalizeHint(plainWithoutFormatting.substring(colonIndex + 1));
        }
        Matcher themeMatcher = THEME_HINT.matcher(plainWithoutFormatting);
        if (themeMatcher.matches() && themeMatcher.group(1).contains("_")) {
            return normalizeHint(themeMatcher.group(1));
        }
        Matcher legacyMatcher = LEGACY_YELLOW_HINT.matcher(plain);
        if (legacyMatcher.find()) return normalizeHint(legacyMatcher.group());
        return allowLooseHint ? extractLooseHintRun(plainWithoutFormatting) : Optional.empty();
    }

    private Optional<String> extractYellowText(Text message) {
        StringBuilder hint = new StringBuilder();
        message.visit((style, text) -> {
            if (style.getColor() != null && "yellow".equals(style.getColor().getName()) && text.contains("_")) {
                hint.append(text);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return normalizeHint(hint.toString());
    }

    private Optional<String> normalizeHint(String rawHint) {
        String hint = stripFormatting(rawHint);
        hint = hint.replaceAll("[^\\p{L}\\p{N}_ '\\-]", " ");
        hint = hint.replaceAll("\\s+", " ");
        hint = clean(hint).toLowerCase(Locale.ROOT);
        if (!hint.contains("_") || hint.length() > 80) return Optional.empty();
        return Optional.of(hint);
    }

    private Optional<String> extractLooseHintRun(String text) {
        String[] rawTokens = text.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String token : rawTokens) {
            String cleaned = token.replaceAll("^[^\\p{L}\\p{N}_]+|[^\\p{L}\\p{N}_]+$", "");
            if (!cleaned.isEmpty()) tokens.add(cleaned);
        }
        int firstHintToken = -1;
        int lastHintToken = -1;
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).contains("_")) {
                if (firstHintToken == -1) firstHintToken = index;
                lastHintToken = index;
            }
        }
        if (firstHintToken == -1) return Optional.empty();
        return normalizeHint(String.join(" ", tokens.subList(firstHintToken, lastHintToken + 1)));
    }

    private void processHint(String hint) {
        if (hint.equals(lastHint)) return;
        lastHintSignalAt = System.currentTimeMillis();
        lastThemeSignalAt = lastHintSignalAt;
        lastActiveRoundAt = lastHintSignalAt;
        lastHint = hint;

        List<String> matches = rankHintMatches(findHintMatches(hint));
        if (matches.equals(lastResults) && !matches.isEmpty()) return;

        lastResults = matches;
        updateAutoGuessQueue(matches);
        synchronized (this) {
            pendingResults = matches;
        }
    }

    private List<String> findHintMatches(String hint) {
        int length = hint.length();
        long spaces = hint.chars().filter(c -> c == ' ').count();
        List<int[]> revealed = new ArrayList<>();
        for (int i = 0; i < hint.length(); i++) {
            char c = hint.charAt(i);
            if (c != '_' && c != ' ') revealed.add(new int[]{i, c});
        }
        return themeWords.stream()
                .filter(word -> word.length() == length)
                .filter(word -> word.chars().filter(c -> c == ' ').count() == spaces)
                .filter(word -> matchesRevealedCharacters(word, revealed))
                .collect(Collectors.toList());
    }

    private List<String> rankHintMatches(List<String> matches) {
        return matches.stream()
                .sorted(Comparator.comparingDouble(this::hintCandidateScore).reversed()
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private double hintCandidateScore(String theme) {
        String key = theme.toLowerCase(Locale.ROOT);
        double score = 0.0;
        for (ScoredTheme scoredTheme : lastScannerScores) {
            if (scoredTheme.theme().equalsIgnoreCase(key)) {
                score += scoredTheme.score();
                break;
            }
        }
        score += COMMON_THEME_PRIORS.getOrDefault(key, 0.0);
        score += PandoraConfig.getInstance().getThemeFrequency(theme) * 1.15;
        if (theme.equalsIgnoreCase(lastScannerTheme)) score += 8.0;
        return score;
    }

    private boolean matchesRevealedCharacters(String word, List<int[]> revealed) {
        String lowerWord = word.toLowerCase(Locale.ROOT);
        for (int[] r : revealed) {
            if (lowerWord.charAt(r[0]) != (char) r[1]) return false;
        }
        return true;
    }

    private void updateAutoGuessQueue(List<String> englishCandidates) {
        if (englishCandidates.equals(autoGuessQueue)) return;
        autoGuessQueue.clear();
        autoGuessQueue.addAll(englishCandidates);
        autoGuessIndex = 0;
        lastAutoGuessAt = 0L;
        if (!englishCandidates.isEmpty()) hudStatus = "queued " + englishCandidates.size();
    }

    private void sendSuggestions(List<String> matches) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (matches.isEmpty()) {
            client.player.sendMessage(prefix().append(Text.literal("No GTB matches found.").formatted(Formatting.GRAY)), false);
            hudStatus = "no hint matches";
            return;
        }
        client.player.sendMessage(prefix()
                .append(Text.literal("GTB Solver ").formatted(Formatting.GRAY))
                .append(Text.literal("(" + matches.size() + " matches)").formatted(Formatting.YELLOW)), false);
        for (int i = 0; i < Math.min(matches.size(), MAX_DISPLAYED_MATCHES); i++) {
            client.player.sendMessage(createSuggestionEntry(matches.get(i)), false);
        }
        if (matches.size() > MAX_DISPLAYED_MATCHES) {
            client.player.sendMessage(prefix()
                    .append(Text.literal("... and " + (matches.size() - MAX_DISPLAYED_MATCHES) + " more.").formatted(Formatting.DARK_GRAY)), false);
        }
    }

    private void sendQueuedAutoGuess(boolean rotateMatches) {
        if (autoGuessQueue.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoGuessAt < AUTO_GUESS_INTERVAL_MS) return;
        int queueIndex = rotateMatches && autoGuessQueue.size() > 1 ? autoGuessIndex % autoGuessQueue.size() : 0;
        String englishGuess = autoGuessQueue.get(queueIndex);
        String translatedGuess = getShortestTranslation(englishGuess);
        if (sendChatMessage(translatedGuess)) {
            lastAutoGuessAt = now;
            hudStatus = "sent " + translatedGuess;
            if (rotateMatches && autoGuessQueue.size() > 1) {
                autoGuessIndex = (autoGuessIndex + 1) % autoGuessQueue.size();
            }
        }
    }

    private boolean sendChatMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return false;
        client.getNetworkHandler().sendChatMessage(message);
        appendGuessHistory(message);
        return true;
    }

    private void appendGuessHistory(String guess) {
        guessHistory.add(guess);
        while (guessHistory.size() > 12) guessHistory.removeFirst();
    }

    private void noteGameSignal(String message) {
        String plain = stripFormatting(message).trim();
        String lower = plain.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (lower.contains("guess the build") || lower.contains("builder:") || lower.contains("round:")
                || lower.contains("theme:") || lower.contains("you guessed") || lower.contains("_")
                || ROUND_OF.matcher(plain).find()) {
            lastGameSignalAt = now;
        }
        if (lower.contains("round:") || ROUND_OF.matcher(plain).find()) {
            lastRoundSignalAt = now;
            lastRoundLabel = extractValueAfterColon(plain, "round");
            if (lastRoundLabel.isBlank()) {
                Matcher m = ROUND_OF.matcher(plain);
                if (m.find()) lastRoundLabel = m.group(1) + "/" + m.group(2);
            }
        }
        if (lower.contains("builder:")) {
            lastBuilderSignalAt = now;
            lastBuilderLabel = extractValueAfterColon(plain, "builder");
        }
        if (lower.contains("theme:")) {
            lastThemeSignalAt = now;
            lastThemeLabel = extractValueAfterColon(plain, "theme");
        }
    }

    private void handleThemeReveal(String message) {
        String plain = stripFormatting(message).trim();
        Matcher matcher = THEME_REVEAL.matcher(plain);
        if (!matcher.matches()) return;
        String revealed = clean(matcher.group(1));
        if (revealed.isEmpty()) return;
        lastThemeSignalAt = System.currentTimeMillis();
        lastThemeLabel = revealed;
        PandoraConfig config = PandoraConfig.getInstance();
        config.incrementThemeFrequency(revealed);
        if (revealed.equalsIgnoreCase(lastScannerTheme)) config.incrementThemeFrequency(revealed);
    }

    private void clearAutoGuessOnRoundMessage(String message) {
        String lower = stripFormatting(message).toLowerCase(Locale.ROOT);
        if (lower.contains("_")) return;
        if (lower.contains("you guessed") || lower.contains("guessed the theme")
                || lower.contains("the theme was") || lower.contains("round over")
                || lower.contains("game over") || lower.contains("next round")) {
            autoGuessQueue.clear();
            autoGuessIndex = 0;
            lastScannerGuess = "";
            lastScannerTheme = "";
            lastScannerScores = List.of();
            hudPlacedCount = 0;
            lastHint = "";
        }
    }

    // ===================== Scanner =====================

    private void ensurePlotAnchor(long now) {
        if (plotRegion != null && now - lastReanchorAt < ROUND_RE_ANCHOR_INTERVAL_MS) return;
        lastReanchorAt = now;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) return;

        // Keep the current anchor if the player is still standing inside the plot
        // (covers the common case where they walk a few blocks during their build).
        if (plotRegion != null) {
            int dx = Math.abs(player.getBlockX() - plotRegion.centerX);
            int dz = Math.abs(player.getBlockZ() - plotRegion.centerZ);
            int dy = Math.abs(player.getBlockY() - plotRegion.floorY);
            if (dx <= PLOT_HALF_SIZE + 4 && dz <= PLOT_HALF_SIZE + 4 && dy <= 18) {
                return;
            }
            // Player teleported / wandered to a new plot — re-anchor below.
        }

        anchorPlotToPlayer(player, world);
    }

    private void anchorPlotToPlayer(ClientPlayerEntity player, ClientWorld world) {
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        int py = player.getBlockY();

        // Find the white-terracotta floor: probe a small 5x5 cross under the player
        // for up to FLOOR_PROBE_DEPTH blocks down. ~150 lookups, runs at most once
        // per round.
        int floorY = Integer.MIN_VALUE;
        outer:
        for (int dy = 0; dy <= FLOOR_PROBE_DEPTH; dy++) {
            int y = py - dy;
            for (int dx = -FLOOR_PROBE_RADIUS; dx <= FLOOR_PROBE_RADIUS; dx++) {
                for (int dz = -FLOOR_PROBE_RADIUS; dz <= FLOOR_PROBE_RADIUS; dz++) {
                    BlockState state = world.getBlockState(new BlockPos(px + dx, y, pz + dz));
                    if (isWhiteTerracotta(state)) {
                        floorY = y;
                        break outer;
                    }
                }
            }
        }
        if (floorY == Integer.MIN_VALUE) {
            // Fall back to player.Y - 1 so the scanner still functions even if the
            // plot floor isn't in render distance yet.
            floorY = py - 1;
        }

        plotRegion = new PlotRegion(px, floorY, pz);
        baseline.clear();
        placed.clear();
        captureBaseline(world);
        hudStatus = "scanner armed";
    }

    private void captureBaseline(ClientWorld world) {
        if (plotRegion == null) return;
        int xMin = plotRegion.centerX - PLOT_HALF_SIZE;
        int xMax = plotRegion.centerX + PLOT_HALF_SIZE;
        int zMin = plotRegion.centerZ - PLOT_HALF_SIZE;
        int zMax = plotRegion.centerZ + PLOT_HALF_SIZE;
        int yMin = plotRegion.floorY - PLOT_SCAN_BELOW;
        int yMax = plotRegion.floorY + PLOT_SCAN_ABOVE;
        // 27 * 27 * 33 = ~24k single-pass world lookups, executed once per round.
        for (BlockPos pos : BlockPos.iterate(xMin, yMin, zMin, xMax, yMax, zMax)) {
            BlockState state = world.getBlockState(pos);
            String id = Registries.BLOCK.getId(state.getBlock()).getPath();
            baseline.put(pos.asLong(), id);
        }
    }

    private void runScannerScan() {
        hudPlacedCount = placed.size();
        if (plotRegion == null) {
            hudStatus = "scanner: no plot";
            return;
        }
        if (placed.size() < MIN_SCAN_BLOCKS) {
            lastScannerScores = List.of();
            hudStatus = "scanner: waiting";
            return;
        }

        BuildFingerprint fp = BuildFingerprint.fromPlaced(placed, plotRegion);
        List<ScoredTheme> scored = scoreThemes(fp);
        lastScannerScores = scored;

        if (scored.isEmpty()) {
            hudStatus = "scanner: no match";
            return;
        }

        if (lastHint.isBlank()) {
            // No hint yet — feed the auto-guess queue with our top scanner candidates.
            List<String> queue = new ArrayList<>();
            for (int i = 0; i < Math.min(scored.size(), 6); i++) {
                queue.add(scored.get(i).theme());
            }
            updateAutoGuessQueue(queue);
        }

        ScoredTheme best = scored.get(0);
        double secondScore = scored.size() > 1 ? scored.get(1).score() : 0.0;
        if (best.score() - secondScore < SCAN_LEAD_THRESHOLD) {
            hudStatus = "scanner: " + scored.size() + " candidates";
            return;
        }

        String shortestGuess = getShortestTranslation(best.theme());
        if (!shortestGuess.equalsIgnoreCase(lastScannerGuess)) {
            lastScannerGuess = shortestGuess;
            lastScannerTheme = best.theme();
            hudStatus = "scanner: " + shortestGuess;
            long now = System.currentTimeMillis();
            if (now - lastScannerNoticeAt >= SCANNER_NOTICE_INTERVAL_MS) {
                lastScannerNoticeAt = now;
                sendScannerSuggestion(best.theme());
            }
        }
    }

    private List<ScoredTheme> scoreThemes(BuildFingerprint fp) {
        // Restrict scoring to themes whose name overlaps with build tokens, OR have
        // a curated profile, OR receive a single-block hint, OR are common priors.
        Set<String> candidateKeys = new HashSet<>();
        for (String token : fp.tokens()) {
            Set<String> hits = tokenToThemeKeys.get(token);
            if (hits != null) candidateKeys.addAll(hits);
        }
        for (String hint : fp.singleBlockThemeHints()) {
            String key = hint.toLowerCase(Locale.ROOT);
            if (themeKeyToOriginal.containsKey(key)) candidateKeys.add(key);
        }
        for (String prior : COMMON_THEME_PRIORS.keySet()) {
            if (themeKeyToOriginal.containsKey(prior)) candidateKeys.add(prior);
        }

        List<ScoredTheme> scored = new ArrayList<>(candidateKeys.size());
        for (String key : candidateKeys) {
            String original = themeKeyToOriginal.get(key);
            if (original == null) continue;
            double score = scoreTheme(original, fp);
            if (score >= MIN_SCAN_SCORE) scored.add(new ScoredTheme(original, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredTheme::score).reversed());
        if (scored.size() > 32) scored = scored.subList(0, 32);
        return scored;
    }

    private double scoreTheme(String theme, BuildFingerprint fp) {
        String key = theme.toLowerCase(Locale.ROOT);
        Set<String> themeTokens = themeTokenIndex.getOrDefault(key, themeNameTokens(theme));

        double score = 0.0;

        // 1) Direct token overlap (theme word literally appears in placed-block tokens)
        for (String token : themeTokens) {
            int count = fp.tokenCount(token);
            if (count > 0) {
                score += Math.min(count, 25) * 1.4;
                if (GTBBlockSignatures.isColorToken(token)) {
                    score += Math.min(count, 25) * 0.4;
                }
            }
        }

        // 2) Single-block signatures: any placed block ID that flags this exact theme
        int sigHits = fp.singleBlockThemeHitCount(key);
        if (sigHits > 0) {
            score += Math.min(sigHits, 8) * 6.0;
        }

        // 3) Curated theme profile
        GTBThemeProfiles.Profile profile = GTBThemeProfiles.lookup(theme);
        if (profile != null) {
            score += scoreProfile(profile, fp);
        }

        // 4) Frequency / common-theme priors
        score += COMMON_THEME_PRIORS.getOrDefault(key, 0.0);
        score += PandoraConfig.getInstance().getThemeFrequency(theme) * 0.5;

        // 5) Single-block dominance bonus: tiny build of one signature block
        if (fp.totalBlocks() <= 6 && fp.dominantBlockId() != null) {
            for (String hint : GTBBlockSignatures.singleBlockThemes(fp.dominantBlockId())) {
                if (key.equalsIgnoreCase(hint)) {
                    score += SINGLE_BLOCK_LEAD_BONUS;
                    break;
                }
            }
        }

        // 6) Hint-pattern compatibility filter — strict
        if (!lastHint.isBlank() && !hintMatchesTheme(lastHint, theme)) {
            score *= 0.05;
        }

        return score;
    }

    private double scoreProfile(GTBThemeProfiles.Profile profile, BuildFingerprint fp) {
        double score = 0.0;

        if (profile.signatureBlock() != null) {
            int count = fp.blockIdCount(profile.signatureBlock());
            if (count > 0) {
                score += 18.0;
                if (fp.totalBlocks() <= 6) score += 10.0;
                if (count == fp.totalBlocks()) score += 6.0;
            }
        }
        for (String color : profile.colors()) {
            int count = fp.tokenCount(color);
            if (count > 0) {
                double frac = count / (double) Math.max(1, fp.totalBlocks());
                score += 3.0 + frac * 6.0;
            }
        }
        if (!profile.colors().isEmpty()) {
            String dominant = fp.dominantColor();
            if (dominant != null && profile.colors().contains(dominant)) score += 5.0;
        }
        for (String material : profile.materials()) {
            int count = fp.tokenCount(material);
            if (count > 0) score += 2.0 + Math.min(count, 20) * 0.25;
        }
        for (GTBThemeProfiles.Shape shape : profile.shapes()) {
            if (matchesShape(shape, fp)) score += 4.0;
        }
        if (profile.minBlocks() > 0 && fp.totalBlocks() < profile.minBlocks()) {
            score *= 0.6;
        }
        if (profile.maxBlocks() > 0 && fp.totalBlocks() > profile.maxBlocks()) {
            score *= 0.4;
        }
        return score;
    }

    private static boolean matchesShape(GTBThemeProfiles.Shape shape, BuildFingerprint fp) {
        return switch (shape) {
            case FLAT -> fp.isFlat();
            case FLAT_ROUND -> fp.isFlat() && fp.isRoundish();
            case TALL -> fp.isTall();
            case TALL_NARROW -> fp.isTall() && fp.maxFootprint() <= 5;
            case WIDE -> fp.isWide();
            case SYMMETRIC -> fp.symmetryX() > 0.65 || fp.symmetryZ() > 0.65;
            case BIG -> fp.totalBlocks() >= 60;
        };
    }

    private boolean hintMatchesTheme(String hint, String theme) {
        if (hint.length() != theme.length()) return false;
        long hintSpaces = hint.chars().filter(c -> c == ' ').count();
        long themeSpaces = theme.chars().filter(c -> c == ' ').count();
        if (hintSpaces != themeSpaces) return false;
        String lowerTheme = theme.toLowerCase(Locale.ROOT);
        for (int i = 0; i < hint.length(); i++) {
            char h = hint.charAt(i);
            if (h == '_' || h == ' ') continue;
            if (lowerTheme.charAt(i) != h) return false;
        }
        return true;
    }

    private void sendScannerSuggestion(String englishWord) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.sendMessage(prefix()
                .append(Text.literal("scanner: ").formatted(Formatting.GREEN))
                .append(createCopyableAnswer(englishWord, Formatting.YELLOW)), false);
    }

    private Text createSuggestionEntry(String englishWord) {
        String shortestTranslation = getShortestTranslation(englishWord);
        MutableText entry = Text.literal(" > ").formatted(Formatting.DARK_GRAY)
                .append(createCopyableAnswer(englishWord, Formatting.YELLOW));
        if (!shortestTranslation.equalsIgnoreCase(englishWord)) {
            entry.append(Text.literal(" (" + shortestTranslation + ")").formatted(Formatting.GRAY));
        }
        return entry;
    }

    private MutableText createCopyableAnswer(String englishWord, Formatting color) {
        String shortestTranslation = getShortestTranslation(englishWord);
        MutableText answer = Text.literal(englishWord.toLowerCase(Locale.ROOT)).formatted(color);
        return answer.setStyle(answer.getStyle()
                .withClickEvent(new ClickEvent.CopyToClipboard(shortestTranslation))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to copy: " + shortestTranslation).formatted(Formatting.GRAY))));
    }

    private MutableText prefix() {
        return Text.literal("[Pandora] ").formatted(Formatting.LIGHT_PURPLE);
    }

    // ===================== Game context (scoreboard + chat) =====================

    private GameContext readGameContext() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return GameContext.inactive();

        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        String title = "";
        List<String> lines = List.of();
        if (sidebar != null) {
            title = stripFormatting(sidebar.getDisplayName().getString()).toLowerCase(Locale.ROOT).trim();
            lines = scoreboard.getScoreboardEntries(sidebar).stream()
                    .map(this::scoreboardLine)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .toList();
        }

        long now = System.currentTimeMillis();
        boolean recentRound = now - lastRoundSignalAt < ROUND_SIGNAL_GRACE_MS;
        boolean recentBuilder = now - lastBuilderSignalAt < ROUND_SIGNAL_GRACE_MS;
        boolean recentHint = now - lastHintSignalAt < ROUND_SIGNAL_GRACE_MS;

        // Pre-game lobby detection: server may animate the title with per-character
        // colors (yellow→orange→white) which strip cleanly to plain text. Also
        // accept "Mode: Guess The Build" sidebar lines from the main hub, the
        // bare "GTB" abbreviation, and any line containing both "build" + "guess".
        boolean scoreboardGtb = mentionsGtb(title)
                || lines.stream().anyMatch(GTBSolverEngine::mentionsGtb);
        boolean inGuessTheBuild = scoreboardGtb || ((recentRound || recentHint) && recentBuilder)
                || (recentRound && recentHint);

        String builderLine = findLine(lines, "builder");
        String timeLine = findLine(lines, "time");
        String themeLine = findLine(lines, "theme");
        String roundLine = findLine(lines, "round");
        String visibleTheme = themeLine == null ? "" : extractValueAfterColon(themeLine, "theme");

        boolean hasBuilderSignal = builderLine != null || recentBuilder;
        boolean hasRoundSignal = roundLine != null || recentRound;
        boolean hasTimeSignal = timeLine != null;
        boolean activeRound = inGuessTheBuild && ((hasBuilderSignal && hasRoundSignal)
                || (hasBuilderSignal && hasTimeSignal) || recentHint || recentRound);
        boolean revealedThemeVisible = !visibleTheme.isBlank() && !visibleTheme.contains("?") && !visibleTheme.contains("_");

        String roundKey = activeRound
                ? String.join("|",
                roundLine != null ? roundLine : lastRoundLabel,
                builderLine != null ? builderLine : lastBuilderLabel,
                themeLine != null ? themeLine : lastThemeLabel)
                : "";

        return new GameContext(inGuessTheBuild, activeRound, roundKey, revealedThemeVisible);
    }

    private String scoreboardLine(ScoreboardEntry entry) {
        Text display = entry.display();
        Text base = display != null ? display : entry.name();
        return stripFormatting(base.getString()).trim();
    }

    private String findLine(List<String> lines, String prefix) {
        String lp = prefix.toLowerCase(Locale.ROOT);
        for (String line : lines) {
            String compact = line.replace(" ", "");
            if (compact.startsWith(lp + ":")) return line;
        }
        return null;
    }

    private void beginRound(GameContext context) {
        activeRoundKey = context.roundKey();
        lastHint = "";
        lastResults = new ArrayList<>();
        lastScannerGuess = "";
        lastScannerTheme = "";
        lastScannerScores = List.of();
        autoGuessQueue.clear();
        autoGuessIndex = 0;
        lastAutoGuessAt = 0L;
        // Re-anchor the plot at next opportunity for the new round.
        plotRegion = null;
        baseline.clear();
        placed.clear();
        hudPlacedCount = 0;
    }

    private void clearAutomationState() {
        autoGuessQueue.clear();
        autoGuessIndex = 0;
        guessHistory.clear();
        plotRegion = null;
        baseline.clear();
        placed.clear();
        lastScannerGuess = "";
        lastScannerTheme = "";
        lastScannerScores = List.of();
        activeRoundKey = "";
        hudStatus = "waiting for GTB round";
        hudPlacedCount = 0;
        lastAutoGuessAt = 0L;
    }

    // ===================== Helpers =====================

    private String extractValueAfterColon(String message, String prefix) {
        String lm = message.toLowerCase(Locale.ROOT);
        String lp = prefix.toLowerCase(Locale.ROOT) + ":";
        int start = lm.indexOf(lp);
        if (start < 0) return "";
        return clean(message.substring(start + lp.length()));
    }

    private static boolean mentionsGtb(String text) {
        if (text == null || text.isEmpty()) return false;
        if (text.contains("guess the build")) return true;
        // "GTB" as a standalone token (lowercased earlier so check 'gtb').
        if (text.matches(".*\\bgtb\\b.*")) return true;
        // "Mode: Guess..." or "Map: Guess..." style lines that name the game.
        return text.contains("guess") && text.contains("build");
    }

    private static boolean isWhiteTerracotta(BlockState state) {
        String id = Registries.BLOCK.getId(state.getBlock()).getPath();
        return "white_terracotta".equals(id) || "hardened_clay".equals(id) || "white_stained_hardened_clay".equals(id);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String stripFormatting(String value) {
        return FORMATTING_CODE.matcher(value).replaceAll("");
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    // ===================== Inner types =====================

    private record ScoredTheme(String theme, double score) {
    }

    private record GameContext(boolean inGuessTheBuild, boolean activeRound, String roundKey, boolean revealedThemeVisible) {
        private static GameContext inactive() {
            return new GameContext(false, false, "", false);
        }
    }

    private record PlotRegion(int centerX, int floorY, int centerZ) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= centerX - PLOT_HALF_SIZE
                    && pos.getX() <= centerX + PLOT_HALF_SIZE
                    && pos.getZ() >= centerZ - PLOT_HALF_SIZE
                    && pos.getZ() <= centerZ + PLOT_HALF_SIZE
                    && pos.getY() >= floorY - PLOT_SCAN_BELOW
                    && pos.getY() <= floorY + PLOT_SCAN_ABOVE;
        }
    }

    /**
     * Aggregated stats for the currently placed blocks within a plot region.
     */
    private static final class BuildFingerprint {
        private final Map<String, Integer> blockIdCounts = new HashMap<>();
        private final Map<String, Integer> tokenCounts = new HashMap<>();
        private final Map<String, Integer> singleBlockThemeHits = new HashMap<>();
        private final Set<Long> positions = new HashSet<>();
        private final PlotRegion region;
        private int totalBlocks;
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private String dominantBlockId;
        private String dominantColor;

        private BuildFingerprint(PlotRegion region) {
            this.region = region;
        }

        static BuildFingerprint fromPlaced(Map<Long, String> placed, PlotRegion region) {
            BuildFingerprint fp = new BuildFingerprint(region);
            // Snapshot the map first; the source map is concurrently mutated from
            // the network thread when block updates arrive.
            Map<Long, String> snapshot = new HashMap<>(placed);
            for (Map.Entry<Long, String> entry : snapshot.entrySet()) {
                BlockPos pos = BlockPos.fromLong(entry.getKey());
                fp.add(pos, entry.getValue());
            }
            fp.finalizeStats();
            return fp;
        }

        private void add(BlockPos pos, String blockId) {
            totalBlocks++;
            positions.add(pos.asLong());
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());

            blockIdCounts.merge(blockId, 1, Integer::sum);
            for (String token : GTBBlockSignatures.tokenize(blockId)) {
                tokenCounts.merge(token, 1, Integer::sum);
            }
            for (String themeHint : GTBBlockSignatures.singleBlockThemes(blockId)) {
                singleBlockThemeHits.merge(themeHint.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }

        private void finalizeStats() {
            int bestCount = 0;
            for (Map.Entry<String, Integer> e : blockIdCounts.entrySet()) {
                if (e.getValue() > bestCount) {
                    bestCount = e.getValue();
                    dominantBlockId = e.getKey();
                }
            }
            int bestColorCount = 0;
            for (String color : GTBBlockSignatures.COLOR_TOKENS) {
                Integer c = tokenCounts.get(color);
                if (c != null && c > bestColorCount) {
                    bestColorCount = c;
                    dominantColor = color;
                }
            }
            // Collapse silver→gray if both present (1.8.9 light_gray reads as silver).
            if ("silver".equals(dominantColor)) dominantColor = "gray";
        }

        int totalBlocks() {
            return totalBlocks;
        }

        int tokenCount(String token) {
            return tokenCounts.getOrDefault(token, 0);
        }

        int blockIdCount(String id) {
            return blockIdCounts.getOrDefault(id, 0);
        }

        Set<String> tokens() {
            return tokenCounts.keySet();
        }

        Set<String> singleBlockThemeHints() {
            return singleBlockThemeHits.keySet();
        }

        int singleBlockThemeHitCount(String themeKey) {
            return singleBlockThemeHits.getOrDefault(themeKey, 0);
        }

        String dominantBlockId() {
            return dominantBlockId;
        }

        String dominantColor() {
            return dominantColor;
        }

        int width() {
            return maxX >= minX ? maxX - minX + 1 : 0;
        }

        int depth() {
            return maxZ >= minZ ? maxZ - minZ + 1 : 0;
        }

        int height() {
            return maxY >= minY ? maxY - minY + 1 : 0;
        }

        int maxFootprint() {
            return Math.max(width(), depth());
        }

        boolean isFlat() {
            return height() <= 3;
        }

        boolean isTall() {
            return height() >= Math.max(width(), depth()) && height() >= 6;
        }

        boolean isWide() {
            return Math.max(width(), depth()) >= 9 && height() <= 6;
        }

        boolean isRoundish() {
            int w = width();
            int d = depth();
            if (w < 4 || d < 4 || Math.abs(w - d) > 2) return false;
            // Project to XZ plane: count unique (x,z) positions, then check that the
            // 4 corners of the bounding box are NOT placed (a circle inscribed in a
            // square leaves the corners empty).
            Set<Long> xz = new HashSet<>();
            for (long packed : positions) {
                BlockPos p = BlockPos.fromLong(packed);
                xz.add(((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL));
            }
            long c1 = ((long) minX << 32) | (minZ & 0xFFFFFFFFL);
            long c2 = ((long) minX << 32) | (maxZ & 0xFFFFFFFFL);
            long c3 = ((long) maxX << 32) | (minZ & 0xFFFFFFFFL);
            long c4 = ((long) maxX << 32) | (maxZ & 0xFFFFFFFFL);
            int filledCorners = 0;
            if (xz.contains(c1)) filledCorners++;
            if (xz.contains(c2)) filledCorners++;
            if (xz.contains(c3)) filledCorners++;
            if (xz.contains(c4)) filledCorners++;
            return filledCorners <= 1;
        }

        double symmetryX() {
            if (positions.isEmpty()) return 0.0;
            double mirror = (minX + maxX) / 2.0;
            int matches = 0;
            for (long packed : positions) {
                BlockPos p = BlockPos.fromLong(packed);
                int reflectedX = (int) Math.round(2 * mirror - p.getX());
                if (positions.contains(new BlockPos(reflectedX, p.getY(), p.getZ()).asLong())) matches++;
            }
            return matches / (double) positions.size();
        }

        double symmetryZ() {
            if (positions.isEmpty()) return 0.0;
            double mirror = (minZ + maxZ) / 2.0;
            int matches = 0;
            for (long packed : positions) {
                BlockPos p = BlockPos.fromLong(packed);
                int reflectedZ = (int) Math.round(2 * mirror - p.getZ());
                if (positions.contains(new BlockPos(p.getX(), p.getY(), reflectedZ).asLong())) matches++;
            }
            return matches / (double) positions.size();
        }
    }
}
