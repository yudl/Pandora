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
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Guess The Build solver and heuristic build reader for modern Fabric.
 */
public class GTBSolverEngine {

    private static final char FORMAT_CODE = (char) 0x00A7;
    private static final Pattern LEGACY_YELLOW_HINT = Pattern.compile("(?<=" + FORMAT_CODE + "e).*$");
    private static final Pattern FORMATTING_CODE = Pattern.compile(Pattern.quote(String.valueOf(FORMAT_CODE)) + "[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern THEME_HINT = Pattern.compile("(?i).*\\b(?:theme|hint|word)\\b(?:\\s+(?:is|starts\\s+with|contains))?\\s*:?[\\s-]*(.+)$");
    private static final Pattern THEME_REVEAL = Pattern.compile("(?i).*theme was\\s*:?[\\s-]*(.+?)(?:[!.]|$)");

    private static final int MAX_DISPLAYED_MATCHES = 100;
    private static final long AUTO_GUESS_INTERVAL_MS = 3_000L;
    private static final long PRE_HINT_SCAN_INTERVAL_MS = 700L;
    private static final long GAME_SIGNAL_GRACE_MS = 20_000L;
    private static final long ROUND_SIGNAL_GRACE_MS = 15_000L;
    private static final long PLOT_SEARCH_INTERVAL_MS = 1_500L;
    private static final int PLOT_HALF_SIZE = 13;
    private static final int PLOT_SCAN_MIN_Y = -3;
    private static final int PLOT_SCAN_MAX_Y = 32;
    private static final int PRE_HINT_MIN_BLOCKS = 1;
    private static final double PRE_HINT_MIN_SCORE = 5.0;
    private static final double PRE_HINT_MIN_LEAD = 1.5;
    private static final int PRE_HINT_MAX_CHANGED_BLOCKS = 700;
    private static final int PLOT_SEARCH_RADIUS = 48;
    private static final int PLOT_SEARCH_VERTICAL = 28;
    private static final int MIN_WHITE_TERRACOTTA_FLOOR_BLOCKS = 20;
    private static final int MAX_WHITE_TERRACOTTA_FLOOR_BLOCKS = 1_100;
    private static final int MAX_PLOT_SPAN = 34;
    private static final int HUD_X = 6;
    private static final int HUD_Y = 18;

    private static final Set<String> COLOR_WORDS = Set.of(
            "white", "orange", "magenta", "light", "blue", "yellow", "lime", "pink", "gray", "grey",
            "silver", "cyan", "purple", "brown", "green", "red", "black"
    );
    private static final Set<String> LEGACY_BLOCK_KEYWORDS = Set.of(
            "stone", "grass", "dirt", "cobblestone", "wood", "planks", "sapling", "bedrock", "water",
            "lava", "sand", "gravel", "gold", "iron", "coal", "log", "leaves", "sponge", "glass",
            "lapis", "dispenser", "sandstone", "note", "bed", "rail", "detector", "piston", "wool",
            "flower", "dandelion", "rose", "mushroom", "slab", "brick", "tnt", "bookshelf", "mossy",
            "obsidian", "torch", "fire", "spawner", "stairs", "chest", "redstone", "diamond",
            "crafting", "furnace", "ladder", "lever", "pressure", "button", "snow", "ice", "cactus",
            "clay", "jukebox", "fence", "pumpkin", "netherrack", "soul", "glowstone", "trapdoor",
            "stonebrick", "bars", "pane", "melon", "vine", "gate", "mycelium", "lily", "pad",
            "nether", "cauldron", "enchanting", "brewing", "end", "dragon", "emerald", "beacon",
            "anvil", "quartz", "hopper", "terracotta", "hay", "carpet", "packed", "slime",
            "prismarine", "lantern", "sea"
    );
    private static final Set<String> ABSOLUTE_SCAN_IGNORES = Set.of(
            "white_terracotta", "hardened_clay", "white_stained_hardened_clay", "stonebrick", "mossy_stonebrick",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks"
    );
    private static final Map<String, Double> COMMON_THEME_PRIORS = Map.ofEntries(
            Map.entry("tree", 10.0),
            Map.entry("house", 9.5),
            Map.entry("car", 9.0),
            Map.entry("dog", 8.5),
            Map.entry("cat", 8.5),
            Map.entry("flower", 8.0),
            Map.entry("pizza", 7.8),
            Map.entry("cake", 7.6),
            Map.entry("calculator", 7.3),
            Map.entry("book", 7.1),
            Map.entry("cup", 7.0),
            Map.entry("beach", 6.9),
            Map.entry("bridge", 6.7),
            Map.entry("castle", 6.6),
            Map.entry("treehouse", 6.4),
            Map.entry("traffic light", 6.2),
            Map.entry("cloud", 6.0),
            Map.entry("train", 5.8),
            Map.entry("computer", 5.7),
            Map.entry("orange juice", 5.5),
            Map.entry("crafting table", 5.4),
            Map.entry("swimming pool", 5.2)
    );

    private static GTBSolverEngine instance;

    private final List<String> themeWords = new ArrayList<>();
    private final Map<String, String> shortestTranslationMap = new HashMap<>();

    private String lastHint = "";
    private List<String> lastResults = new ArrayList<>();
    private List<String> pendingResults;
    private final List<String> autoGuessQueue = new ArrayList<>();
    private final List<String> guessHistory = new ArrayList<>();
    private final Map<String, Double> latestPreHintScores = new HashMap<>();
    private final Map<Long, String> pendingPlotUpdates = new HashMap<>();
    private final Map<Long, String> trackedPlotBlocks = new HashMap<>();
    private String activeRoundKey = "";
    private String lastPreHintGuess = "";
    private String lastPreHintTheme = "";
    private String lastHudStatus = "waiting for GTB round";
    private String lastRoundLabel = "";
    private String lastBuilderLabel = "";
    private String lastThemeLabel = "";
    private int lastChangedBlockCount;
    private long lastAutoGuessAt;
    private long lastPreHintScanAt;
    private long lastGameSignalAt;
    private long lastHintSignalAt;
    private long lastRoundSignalAt;
    private long lastBuilderSignalAt;
    private long lastThemeSignalAt;
    private long lastActiveRoundAt;
    private long lastPlotSearchAt;
    private int autoGuessIndex;
    private Map<Long, String> roundBaselineBlocks = new HashMap<>();
    private PlotAnchor lastPlotAnchor = PlotAnchor.unknown();

    public static GTBSolverEngine getInstance() {
        if (instance == null) {
            instance = new GTBSolverEngine();
        }
        return instance;
    }

    private GTBSolverEngine() {
        loadTranslationData();
    }

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
            lastHudStatus = "not in GTB";
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
            if (context.plotAnchor().isValid() && roundBaselineBlocks.isEmpty()) {
                captureRoundBaseline(context.plotAnchor());
            }
        }

        boolean hasRecentRound = context.activeRound() || now - lastActiveRoundAt < ROUND_SIGNAL_GRACE_MS;
        if (activeRoundOnly && !hasRecentRound) {
            lastHudStatus = "waiting for active round";
            if (now - lastGameSignalAt > GAME_SIGNAL_GRACE_MS) {
                clearAutomationState();
            }
            return;
        }

        boolean autoHints = !GTBSolverModule.MODE_MANUAL.equalsIgnoreCase(guessMode);
        boolean autoPreHint = GTBSolverModule.MODE_AUTO_HINTS_PREHINT.equalsIgnoreCase(guessMode);

        if (!autoHints) {
            lastHudStatus = "manual mode";
            autoGuessQueue.clear();
            autoGuessIndex = 0;
            return;
        }

        if (context.revealedThemeVisible()) {
            autoGuessQueue.clear();
            autoGuessIndex = 0;
            latestPreHintScores.clear();
            lastHudStatus = "theme visible";
            return;
        }

        if (autoPreHint && context.allowPreHint()) {
            scanPreHintGuess(context);
        }

        if (!lastHudStatus.startsWith("scan")
                && !lastHudStatus.startsWith("no ")
                && !lastHudStatus.startsWith("locking")
                && !lastHudStatus.startsWith("prehint")) {
            lastHudStatus = autoGuessQueue.isEmpty() ? "collecting guesses" : "auto guessing";
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
        if (activeRoundKey.isBlank() || !lastPlotAnchor.isValid()) {
            return;
        }
        if (!isWithinTrackedPlot(pos, lastPlotAnchor)) {
            return;
        }

        long packedPos = pos.asLong();
        String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
        if (roundBaselineBlocks.isEmpty()) {
            pendingPlotUpdates.put(packedPos, state.isAir() ? "" : blockId);
            return;
        }

        String baseline = roundBaselineBlocks.get(packedPos);
        if (state.isAir()
                || baseline == null
                || blockId.equals(baseline)
                || isIgnoredBuildBlock(blockId)) {
            trackedPlotBlocks.remove(packedPos);
            return;
        }

        trackedPlotBlocks.put(packedPos, blockId);
    }

    public void renderGuessHistoryHud(DrawContext context) {
        int width = 124;
        int headerColor = 0xE014141A;
        int bodyColor = 0xD8101014;
        int accentColor = 0xFFFF4FD8;
        int textColor = 0xFFFFFFFF;
        int mutedColor = 0xFFB7B7C6;
        int historySize = Math.min(Math.max(guessHistory.size(), 1), 6);
        int height = 16 + historySize * 10;

        context.fill(HUD_X, HUD_Y, HUD_X + width, HUD_Y + height, bodyColor);
        context.fill(HUD_X, HUD_Y, HUD_X + width, HUD_Y + 14, headerColor);
        context.fill(HUD_X, HUD_Y, HUD_X + 3, HUD_Y + 14, accentColor);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "gtb guesses", HUD_X + 6, HUD_Y + 3, textColor);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, lastChangedBlockCount + " blocks", HUD_X + width - 44, HUD_Y + 3, mutedColor);

        if (guessHistory.isEmpty()) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, lastHudStatus, HUD_X + 6, HUD_Y + 18, mutedColor);
            return;
        }

        for (int index = 0; index < historySize; index++) {
            String guess = guessHistory.get(guessHistory.size() - 1 - index);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, guess, HUD_X + 6, HUD_Y + 18 + index * 10, mutedColor);
        }
    }

    public void reset() {
        lastHint = "";
        lastResults = new ArrayList<>();
        pendingResults = null;
        autoGuessQueue.clear();
        guessHistory.clear();
        latestPreHintScores.clear();
        activeRoundKey = "";
        lastPreHintGuess = "";
        lastPreHintTheme = "";
        lastHudStatus = "waiting for GTB round";
        lastRoundLabel = "";
        lastBuilderLabel = "";
        lastThemeLabel = "";
        lastChangedBlockCount = 0;
        lastAutoGuessAt = 0L;
        lastPreHintScanAt = 0L;
        lastGameSignalAt = 0L;
        lastHintSignalAt = 0L;
        lastRoundSignalAt = 0L;
        lastBuilderSignalAt = 0L;
        lastThemeSignalAt = 0L;
        lastActiveRoundAt = 0L;
        lastPlotSearchAt = 0L;
        autoGuessIndex = 0;
        roundBaselineBlocks = new HashMap<>();
        pendingPlotUpdates.clear();
        trackedPlotBlocks.clear();
        lastPlotAnchor = PlotAnchor.unknown();
    }

    private void loadTranslationData() {
        Set<String> seenThemes = new HashSet<>();

        try (InputStream stream = getClass().getResourceAsStream("/assets/pandora/translations-data.json")) {
            if (stream == null) {
                Pandora.LOGGER.error("[GTBSolver] translations-data.json not found in resources.");
                return;
            }

            JsonArray array = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonArray.class);
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("theme")) {
                    continue;
                }

                String theme = clean(entry.get("theme").getAsString());
                if (theme.isEmpty()) {
                    continue;
                }

                String key = theme.toLowerCase(Locale.ROOT);
                if (seenThemes.add(key)) {
                    themeWords.add(theme);
                }

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

        if (!entry.has("translations") || !entry.get("translations").isJsonObject()) {
            return shortest;
        }

        for (Map.Entry<String, JsonElement> language : entry.getAsJsonObject("translations").entrySet()) {
            if (!language.getValue().isJsonObject()) {
                continue;
            }

            JsonObject translationObject = language.getValue().getAsJsonObject();
            if (!translationObject.has("translation")) {
                continue;
            }

            String translation = clean(translationObject.get("translation").getAsString());
            if (translation.isEmpty()) {
                continue;
            }

            int length = codePointLength(translation);
            if (length < shortestLength) {
                shortest = translation;
                shortestLength = length;
            }
        }

        return shortest;
    }

    private Optional<String> extractHint(Text message, boolean allowLooseHint) {
        Optional<String> styledHint = extractYellowText(message);
        if (styledHint.isPresent()) {
            return styledHint;
        }

        String plain = message.getString();
        if (!plain.contains("_")) {
            return Optional.empty();
        }

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
        if (legacyMatcher.find()) {
            return normalizeHint(legacyMatcher.group());
        }

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

        if (!hint.contains("_") || hint.length() > 80) {
            return Optional.empty();
        }

        return Optional.of(hint);
    }

    private Optional<String> extractLooseHintRun(String text) {
        String[] rawTokens = text.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String token : rawTokens) {
            String cleaned = token.replaceAll("^[^\\p{L}\\p{N}_]+|[^\\p{L}\\p{N}_]+$", "");
            if (!cleaned.isEmpty()) {
                tokens.add(cleaned);
            }
        }

        int firstHintToken = -1;
        int lastHintToken = -1;
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).contains("_")) {
                if (firstHintToken == -1) {
                    firstHintToken = index;
                }
                lastHintToken = index;
            }
        }

        if (firstHintToken == -1) {
            return Optional.empty();
        }

        return normalizeHint(String.join(" ", tokens.subList(firstHintToken, lastHintToken + 1)));
    }

    private void processHint(String hint) {
        if (hint.equals(lastHint)) {
            return;
        }
        lastHintSignalAt = System.currentTimeMillis();
        lastThemeSignalAt = lastHintSignalAt;
        lastActiveRoundAt = lastHintSignalAt;
        lastHint = hint;

        List<String> matches = rankHintMatches(findHintMatches(hint));

        if (matches.equals(lastResults) && !matches.isEmpty()) {
            return;
        }

        lastResults = matches;
        updateAutoGuessQueue(matches);
        synchronized (this) {
            pendingResults = matches;
        }
    }

    private List<String> findHintMatches(String hint) {
        int length = hint.length();
        long spaces = hint.chars().filter(character -> character == ' ').count();

        List<int[]> revealedCharacters = new ArrayList<>();
        for (int index = 0; index < hint.length(); index++) {
            char character = hint.charAt(index);
            if (character != '_' && character != ' ') {
                revealedCharacters.add(new int[]{index, character});
            }
        }

        return themeWords.stream()
                .filter(word -> word.length() == length)
                .filter(word -> word.chars().filter(character -> character == ' ').count() == spaces)
                .filter(word -> matchesRevealedCharacters(word, revealedCharacters))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.toList());
    }

    private List<String> rankHintMatches(List<String> matches) {
        return matches.stream()
                .sorted(Comparator.comparingDouble(this::hintCandidateScore).reversed()
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private List<String> fallbackHintCandidates() {
        return latestPreHintScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(8)
                .map(entry -> themeWords.stream()
                        .filter(theme -> theme.equalsIgnoreCase(entry.getKey()))
                        .findFirst()
                        .orElse(entry.getKey()))
                .collect(Collectors.toList());
    }

    private double hintCandidateScore(String theme) {
        String key = theme.toLowerCase(Locale.ROOT);
        double score = latestPreHintScores.getOrDefault(key, 0.0);
        score += frequencyBias(theme);
        if (theme.equalsIgnoreCase(lastPreHintTheme)) {
            score += 8.0;
        }
        return score;
    }

    private double frequencyBias(String theme) {
        String normalized = normalizeTheme(theme);
        return COMMON_THEME_PRIORS.getOrDefault(normalized, 0.0)
                + PandoraConfig.getInstance().getThemeFrequency(theme) * 1.15;
    }

    private boolean matchesRevealedCharacters(String word, List<int[]> revealedCharacters) {
        String lowerWord = word.toLowerCase(Locale.ROOT);
        for (int[] revealed : revealedCharacters) {
            if (lowerWord.charAt(revealed[0]) != (char) revealed[1]) {
                return false;
            }
        }
        return true;
    }

    private void updateAutoGuessQueue(List<String> englishCandidates) {
        if (englishCandidates.equals(autoGuessQueue)) {
            return;
        }
        autoGuessQueue.clear();
        autoGuessQueue.addAll(englishCandidates);
        autoGuessIndex = 0;
        lastAutoGuessAt = 0L;
        if (!englishCandidates.isEmpty()) {
            lastHudStatus = "queued " + englishCandidates.size() + " guesses";
        }
    }

    private void sendSuggestions(List<String> matches) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        if (matches.isEmpty()) {
            client.player.sendMessage(prefix().append(Text.literal("No GTB matches found.").formatted(Formatting.GRAY)), false);
            lastHudStatus = "no hint matches";
            return;
        }

        client.player.sendMessage(prefix()
                .append(Text.literal("GTB Solver ").formatted(Formatting.GRAY))
                .append(Text.literal("(" + matches.size() + " matches)").formatted(Formatting.YELLOW)), false);

        for (int index = 0; index < Math.min(matches.size(), MAX_DISPLAYED_MATCHES); index++) {
            client.player.sendMessage(createSuggestionEntry(matches.get(index)), false);
        }

        if (matches.size() > MAX_DISPLAYED_MATCHES) {
            client.player.sendMessage(prefix()
                    .append(Text.literal("... and " + (matches.size() - MAX_DISPLAYED_MATCHES) + " more.").formatted(Formatting.DARK_GRAY)), false);
        }
    }

    private void sendQueuedAutoGuess(boolean rotateMatches) {
        if (autoGuessQueue.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAutoGuessAt < AUTO_GUESS_INTERVAL_MS) {
            return;
        }

        int queueIndex = rotateMatches && autoGuessQueue.size() > 1 ? autoGuessIndex % autoGuessQueue.size() : 0;
        String englishGuess = autoGuessQueue.get(queueIndex);
        String translatedGuess = getShortestTranslation(englishGuess);

        if (sendChatMessage(translatedGuess)) {
            lastAutoGuessAt = now;
            lastHudStatus = "sent " + translatedGuess;
            if (rotateMatches && autoGuessQueue.size() > 1) {
                autoGuessIndex = (autoGuessIndex + 1) % autoGuessQueue.size();
            }
        }
    }

    private boolean sendChatMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            return false;
        }

        client.getNetworkHandler().sendChatMessage(message);
        appendGuessHistory(message);
        return true;
    }

    private void appendGuessHistory(String guess) {
        guessHistory.add(guess);
        while (guessHistory.size() > 12) {
            guessHistory.removeFirst();
        }
    }

    private void noteGameSignal(String message) {
        String plainMessage = stripFormatting(message).trim();
        String lowerMessage = plainMessage.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (lowerMessage.contains("guess the build")
                || lowerMessage.contains("builder:")
                || lowerMessage.contains("round:")
                || lowerMessage.contains("theme:")
                || lowerMessage.contains("you guessed")
                || lowerMessage.contains("_")) {
            lastGameSignalAt = now;
        }

        if (lowerMessage.contains("round:")) {
            lastRoundSignalAt = now;
            lastRoundLabel = extractValueAfterColon(plainMessage, "round");
        }
        if (lowerMessage.contains("builder:")) {
            lastBuilderSignalAt = now;
            lastBuilderLabel = extractValueAfterColon(plainMessage, "builder");
        }
        if (lowerMessage.contains("theme:")) {
            lastThemeSignalAt = now;
            lastThemeLabel = extractValueAfterColon(plainMessage, "theme");
        }
    }

    private void handleThemeReveal(String message) {
        String plain = stripFormatting(message).trim();
        Matcher matcher = THEME_REVEAL.matcher(plain);
        if (!matcher.matches()) {
            return;
        }

        String revealedTheme = clean(matcher.group(1));
        if (revealedTheme.isEmpty()) {
            return;
        }

        lastThemeSignalAt = System.currentTimeMillis();
        lastThemeLabel = revealedTheme;

        PandoraConfig config = PandoraConfig.getInstance();
        config.incrementThemeFrequency(revealedTheme);
        if (revealedTheme.equalsIgnoreCase(lastPreHintTheme)) {
            config.incrementThemeFrequency(revealedTheme);
        }
    }

    private void clearAutoGuessOnRoundMessage(String message) {
        String lowerMessage = stripFormatting(message).toLowerCase(Locale.ROOT);
        if (lowerMessage.contains("_")) {
            return;
        }

        if (lowerMessage.contains("you guessed")
                || lowerMessage.contains("guessed the theme")
                || lowerMessage.contains("the theme was")
                || lowerMessage.contains("round over")
                || lowerMessage.contains("game over")) {
            autoGuessQueue.clear();
            autoGuessIndex = 0;
            lastPreHintGuess = "";
            lastPreHintTheme = "";
            latestPreHintScores.clear();
            lastChangedBlockCount = 0;
            lastHint = "";
        }
    }

    private void scanPreHintGuess(GameContext context) {
        long now = System.currentTimeMillis();
        if (now - lastPreHintScanAt < PRE_HINT_SCAN_INTERVAL_MS) {
            return;
        }
        lastPreHintScanAt = now;

        if (roundBaselineBlocks.isEmpty() && context.plotAnchor().isValid()) {
            captureRoundBaseline(context.plotAnchor());
            lastHudStatus = "locking baseline";
            lastChangedBlockCount = 0;
            return;
        }

        Optional<BlockScan> scan = buildTrackedScan(context.plotAnchor());
        if (scan.isEmpty()) {
            latestPreHintScores.clear();
            lastChangedBlockCount = 0;
            autoGuessQueue.clear();
            autoGuessIndex = 0;
            lastHudStatus = trackedPlotBlocks.isEmpty() ? "waiting for placed blocks" : "no tracked scan";
            return;
        }

        BlockScan blockScan = scan.get();
        if (blockScan.totalBlocks > PRE_HINT_MAX_CHANGED_BLOCKS) {
            latestPreHintScores.clear();
            autoGuessQueue.clear();
            autoGuessIndex = 0;
            lastChangedBlockCount = 0;
            lastHudStatus = "scan rejected";
            return;
        }

        lastChangedBlockCount = blockScan.totalBlocks;
        latestPreHintScores.clear();

        List<ScoredTheme> scores = themeWords.stream()
                .map(theme -> new ScoredTheme(theme, scoreThemeAgainstBlocks(theme, blockScan)))
                .filter(scoredTheme -> scoredTheme.score() >= PRE_HINT_MIN_SCORE)
                .sorted(Comparator.comparingDouble(ScoredTheme::score).reversed())
                .limit(32)
                .toList();

        for (ScoredTheme score : scores) {
            latestPreHintScores.put(score.theme().toLowerCase(Locale.ROOT), score.score());
        }

        if (scores.isEmpty()) {
            autoGuessQueue.clear();
            autoGuessIndex = 0;
            lastHudStatus = "no prehint match";
            return;
        }

        if (lastHint.isBlank()) {
            updateAutoGuessQueue(scores.stream()
                    .map(ScoredTheme::theme)
                    .limit(8)
                    .collect(Collectors.toList()));
        }

        ScoredTheme best = scores.getFirst();
        double secondScore = scores.size() > 1 ? scores.get(1).score() : 0.0;
        if (best.score() - secondScore < PRE_HINT_MIN_LEAD) {
            lastHudStatus = "prehint ambiguous";
            return;
        }

        String shortestGuess = getShortestTranslation(best.theme());
        if (!shortestGuess.equalsIgnoreCase(lastPreHintGuess)) {
            lastPreHintGuess = shortestGuess;
            lastPreHintTheme = best.theme();
            lastHudStatus = "prehint " + shortestGuess;
            sendPreHintSuggestion(best.theme());
        }
    }

    private Optional<BlockScan> buildTrackedScan(PlotAnchor anchor) {
        if (!anchor.isValid() || trackedPlotBlocks.isEmpty()) {
            return Optional.empty();
        }

        BlockPos center = anchor.center();
        BlockScan scan = new BlockScan(center);
        for (Map.Entry<Long, String> entry : trackedPlotBlocks.entrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getKey());
            if (!isWithinTrackedPlot(pos, anchor)) {
                continue;
            }
            scan.add(pos, entry.getValue());
        }

        return scan.totalBlocks >= PRE_HINT_MIN_BLOCKS ? Optional.of(scan) : Optional.empty();
    }

    private double scoreThemeAgainstBlocks(String theme, BlockScan scan) {
        String normalizedTheme = normalizeTheme(theme);
        Set<String> themeTokens = new HashSet<>(List.of(normalizedTheme.split(" ")));
        double score = 0.0;

        for (String token : themeTokens) {
            if (token.isBlank()) {
                continue;
            }
            score += scan.count(token) * 1.35;
            if (COLOR_WORDS.contains(token)) {
                score += scan.count(token) * 0.45;
            }
            if (LEGACY_BLOCK_KEYWORDS.contains(token)) {
                score += scan.count(token) * 1.1;
            }
        }

        score += frequencyBias(theme);
        score += genericComplexityScore(theme, scan);
        score += shapeScore(theme, scan);
        score += categoryScore(normalizedTheme, scan);
        score += directSemanticScore(normalizedTheme, scan);
        return score;
    }

    private double genericComplexityScore(String theme, BlockScan scan) {
        String normalizedTheme = normalizeTheme(theme);
        boolean simpleWord = !normalizedTheme.contains(" ") && normalizedTheme.length() <= 5;
        boolean complexWord = normalizedTheme.contains(" ") || normalizedTheme.length() >= 10;

        double score = 0.0;
        if (scan.totalBlocks <= 4 && simpleWord) {
            score += 4.0;
        }
        if (scan.totalBlocks >= 25 && complexWord) {
            score += 3.0;
        }
        return score;
    }

    private double shapeScore(String theme, BlockScan scan) {
        String normalizedTheme = normalizeTheme(theme);
        double score = 0.0;

        if (scan.symmetryX() > 0.72 || scan.symmetryZ() > 0.72) {
            if (containsAny(normalizedTheme, "airplane", "butterfly", "glasses", "bow", "wings")) {
                score += 6.0;
            }
        }

        if (scan.isTall()) {
            if (containsAny(normalizedTheme, "tree", "treehouse", "rocket", "tower", "cactus", "skyscraper")) {
                score += 5.0;
            }
        }

        if (scan.isFlat() && scan.isSquareish()) {
            if (containsAny(normalizedTheme, "pizza", "coin", "clock", "sun", "ball", "button", "plate")) {
                score += 5.0;
            }
        }

        if (scan.isWide()) {
            if (containsAny(normalizedTheme, "bridge", "airplane", "train", "traffic light")) {
                score += 3.0;
            }
        }

        return score;
    }

    private double categoryScore(String theme, BlockScan scan) {
        double score = 0.0;

        if ((theme.contains("traffic") && theme.contains("light")) || theme.contains("signal")) {
            score += triColorScore(scan, "red", "yellow", "green") * 1.8;
            score += capped(scan.count("wool") + scan.count("terracotta") + scan.count("clay") + scan.count("glass"), 20) * 0.4;
        }

        if (containsAny(theme, "swim", "pool", "ocean", "sea", "water")) {
            score += capped(scan.count("water"), 80) * 0.35;
            score += capped(scan.count("blue") + scan.count("cyan") + scan.count("prismarine") + scan.count("sand"), 40) * 0.2;
        }

        if (containsAny(theme, "fire", "lava", "volcano", "nether")) {
            score += capped(scan.count("lava") + scan.count("fire") + scan.count("netherrack"), 40) * 0.45;
            score += capped(scan.count("red") + scan.count("orange") + scan.count("yellow") + scan.count("glowstone"), 60) * 0.18;
        }

        if (containsAny(theme, "snow", "ice", "winter", "frozen")) {
            score += capped(scan.count("snow") + scan.count("ice") + scan.count("packed"), 60) * 0.35;
            score += capped(scan.count("white") + scan.count("blue"), 40) * 0.15;
        }

        if (containsAny(theme, "treehouse")) {
            score += capped(scan.count("log") + scan.count("planks") + scan.count("wood"), 70) * 0.28;
            score += capped(scan.count("leaves") + scan.count("green") + scan.count("fence"), 50) * 0.08;
        } else if (containsAny(theme, "tree", "forest", "jungle", "plant", "garden")) {
            score += capped(scan.count("log") + scan.count("leaves") + scan.count("sapling"), 60) * 0.3;
            score += capped(scan.count("green") + scan.count("grass") + scan.count("dirt"), 50) * 0.18;
        }

        if (containsAny(theme, "house", "home", "hut", "cabin")) {
            score += capped(scan.count("planks") + scan.count("wood") + scan.count("log") + scan.count("brick"), 60) * 0.2;
            score += capped(scan.count("glass") + scan.count("door") + scan.count("stairs") + scan.count("fence"), 40) * 0.18;
        }

        if (containsAny(theme, "beach", "desert", "sand")) {
            score += capped(scan.count("sand") + scan.count("sandstone"), 80) * 0.28;
            score += capped(scan.count("water") + scan.count("cactus"), 40) * 0.14;
        }

        if (containsAny(theme, "fries", "fry", "chips", "potato")) {
            score += capped(scan.count("yellow") + scan.count("orange"), 60) * 0.3;
            score += capped(scan.count("wool") + scan.count("terracotta") + scan.count("glass"), 40) * 0.16;
        }

        if (containsAny(theme, "pizza", "burger", "hotdog", "taco", "sandwich")) {
            score += capped(scan.count("red") + scan.count("orange") + scan.count("yellow") + scan.count("brown"), 70) * 0.24;
            score += capped(scan.count("wool") + scan.count("terracotta") + scan.count("clay"), 40) * 0.15;
        }

        if (containsAny(theme, "juice", "drink", "soda", "smoothie", "milkshake", "cocktail")) {
            score += capped(scan.count("glass") + scan.count("pane") + scan.count("water"), 60) * 0.26;
            score += capped(scan.count("orange") + scan.count("yellow") + scan.count("red") + scan.count("lime"), 50) * 0.16;
        }

        if (containsAny(theme, "cloud", "smoke")) {
            score += capped(scan.count("white") + scan.count("snow") + scan.count("glass") + scan.count("wool"), 60) * 0.25;
        }

        if (containsAny(theme, "calculator", "computer", "keyboard", "phone", "remote")) {
            score += capped(scan.count("gray") + scan.count("grey") + scan.count("black") + scan.count("stone") + scan.count("button"), 80) * 0.24;
            score += capped(scan.count("glass") + scan.count("quartz") + scan.count("iron"), 40) * 0.12;
        }

        return score;
    }

    private double directSemanticScore(String theme, BlockScan scan) {
        double score = 0.0;

        if (theme.equals("crafting table")) {
            score += capped(scan.count("crafting_table") * 5 + scan.count("crafting") * 3 + scan.count("table") * 2, 30);
        }
        if (theme.equals("calculator")) {
            score += capped(scan.count("button") + scan.count("stone") + scan.count("gray") + scan.count("black"), 30) * 0.7;
        }
        if (theme.equals("cloud")) {
            score += capped(scan.count("white") + scan.count("glass") + scan.count("snow"), 30) * 0.55;
        }

        return score;
    }

    private void sendPreHintSuggestion(String englishWord) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        client.player.sendMessage(prefix()
                .append(Text.literal("GTB pre-hint guess: ").formatted(Formatting.GREEN))
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

    private GameContext readGameContext() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return GameContext.inactive();
        }

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

        boolean scoreboardGtb = title.contains("guess the build") || lines.stream().anyMatch(line -> line.contains("guess the build"));
        boolean inGuessTheBuild = scoreboardGtb || ((recentRound || recentHint) && recentBuilder);

        String builderLine = findLine(lines, "builder");
        String timeLine = findLine(lines, "time");
        String themeLine = findLine(lines, "theme");
        String roundLine = findLine(lines, "round");
        String visibleTheme = themeLine == null ? "" : extractValueAfterColon(themeLine, "theme");

        boolean hasBuilderSignal = builderLine != null || recentBuilder;
        boolean hasRoundSignal = roundLine != null || recentRound;
        boolean hasTimeSignal = timeLine != null;
        boolean activeRound = inGuessTheBuild && ((hasBuilderSignal && hasRoundSignal) || (hasBuilderSignal && hasTimeSignal) || recentHint);
        boolean allowPreHint = activeRound && (themeLine == null || themeLine.contains("???")) && lastHint.isBlank();
        boolean revealedThemeVisible = !visibleTheme.isBlank() && !visibleTheme.contains("?") && !visibleTheme.contains("_");

        String roundKey = activeRound
                ? String.join("|",
                roundLine != null ? roundLine : lastRoundLabel,
                builderLine != null ? builderLine : lastBuilderLabel,
                themeLine != null ? themeLine : lastThemeLabel)
                : "";

        PlotAnchor plotAnchor = lastPlotAnchor;
        if (activeRound && (!plotAnchor.isValid() || now - lastPlotSearchAt >= PLOT_SEARCH_INTERVAL_MS)) {
            plotAnchor = detectPlotAnchor(client).orElse(lastPlotAnchor);
            if (plotAnchor.isValid()) {
                lastPlotAnchor = plotAnchor;
            }
        }

        return new GameContext(
                inGuessTheBuild,
                activeRound,
                allowPreHint,
                roundKey,
                plotAnchor,
                revealedThemeVisible
        );
    }

    private Optional<PlotAnchor> detectPlotAnchor(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        if (lastPlotAnchor.isValid() && now - lastPlotSearchAt < PLOT_SEARCH_INTERVAL_MS) {
            return Optional.of(lastPlotAnchor);
        }
        lastPlotSearchAt = now;

        List<BlockPos> searchOrigins = new ArrayList<>();
        BlockPos playerPos = client.player.getBlockPos();
        searchOrigins.add(playerPos);

        Vec3d look = client.player.getRotationVecClient();
        Vec3d horizontal = new Vec3d(look.x, 0.0, look.z);
        if (horizontal.lengthSquared() > 1.0E-4) {
            Vec3d ahead = horizontal.normalize().multiply(22.0);
            searchOrigins.add(BlockPos.ofFloored(client.player.getX() + ahead.x, client.player.getY(), client.player.getZ() + ahead.z));
        }

        PlotAnchor best = null;
        for (BlockPos origin : searchOrigins) {
            Optional<PlotAnchor> candidate = detectPlotAnchorAround(client, origin);
            if (candidate.isEmpty()) {
                continue;
            }
            if (best == null || candidate.get().confidence() > best.confidence()) {
                best = candidate.get();
            }
        }

        if (best == null) {
            return Optional.ofNullable(lastPlotAnchor.isValid() ? lastPlotAnchor : null);
        }

        lastPlotAnchor = best;
        return Optional.of(best);
    }

    private Optional<PlotAnchor> detectPlotAnchorAround(MinecraftClient client, BlockPos origin) {
        int bestY = Integer.MIN_VALUE;
        int bestCount = 0;
        for (int y = origin.getY() - PLOT_SEARCH_VERTICAL; y <= origin.getY() + PLOT_SEARCH_VERTICAL; y++) {
            int count = 0;
            for (int x = origin.getX() - PLOT_SEARCH_RADIUS; x <= origin.getX() + PLOT_SEARCH_RADIUS; x++) {
                for (int z = origin.getZ() - PLOT_SEARCH_RADIUS; z <= origin.getZ() + PLOT_SEARCH_RADIUS; z++) {
                    if (isWhiteTerracotta(client.world.getBlockState(new BlockPos(x, y, z)))) {
                        count++;
                    }
                }
            }
            if (count > bestCount) {
                bestCount = count;
                bestY = y;
            }
        }

        if (bestCount < MIN_WHITE_TERRACOTTA_FLOOR_BLOCKS) {
            return Optional.empty();
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int totalX = 0;
        int totalZ = 0;
        int totalBlocks = 0;
        for (int x = origin.getX() - PLOT_SEARCH_RADIUS; x <= origin.getX() + PLOT_SEARCH_RADIUS; x++) {
            for (int z = origin.getZ() - PLOT_SEARCH_RADIUS; z <= origin.getZ() + PLOT_SEARCH_RADIUS; z++) {
                if (!isWhiteTerracotta(client.world.getBlockState(new BlockPos(x, bestY, z)))) {
                    continue;
                }
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                totalX += x;
                totalZ += z;
                totalBlocks++;
            }
        }

        if (totalBlocks < MIN_WHITE_TERRACOTTA_FLOOR_BLOCKS) {
            return Optional.empty();
        }

        int spanX = maxX - minX;
        int spanZ = maxZ - minZ;
        if (totalBlocks > MAX_WHITE_TERRACOTTA_FLOOR_BLOCKS || spanX > MAX_PLOT_SPAN || spanZ > MAX_PLOT_SPAN) {
            return Optional.empty();
        }
        int centerX = spanX >= PLOT_HALF_SIZE ? Math.round((minX + maxX) / 2.0f) : Math.round(totalX / (float) totalBlocks);
        int centerZ = spanZ >= PLOT_HALF_SIZE ? Math.round((minZ + maxZ) / 2.0f) : Math.round(totalZ / (float) totalBlocks);
        double confidence = 900.0
                - Math.abs(totalBlocks - 729)
                - Math.abs(spanX - PLOT_HALF_SIZE * 2) * 8.0
                - Math.abs(spanZ - PLOT_HALF_SIZE * 2) * 8.0;
        return Optional.of(new PlotAnchor(new BlockPos(centerX, bestY, centerZ), bestY, confidence));
    }

    private String scoreboardLine(ScoreboardEntry entry) {
        Text display = entry.display();
        Text base = display != null ? display : entry.name();
        return stripFormatting(base.getString()).trim();
    }

    private String findLine(List<String> lines, String prefix) {
        String loweredPrefix = prefix.toLowerCase(Locale.ROOT);
        for (String line : lines) {
            String compact = line.replace(" ", "");
            if (compact.startsWith(loweredPrefix + ":")) {
                return line;
            }
        }
        return null;
    }

    private void beginRound(GameContext context) {
        activeRoundKey = context.roundKey();
        lastHint = "";
        lastResults = new ArrayList<>();
        lastPreHintGuess = "";
        lastPreHintTheme = "";
        latestPreHintScores.clear();
        pendingPlotUpdates.clear();
        trackedPlotBlocks.clear();
        autoGuessQueue.clear();
        guessHistory.clear();
        autoGuessIndex = 0;
        lastAutoGuessAt = 0L;
        captureRoundBaseline(context.plotAnchor());
    }

    private void captureRoundBaseline(PlotAnchor anchor) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !anchor.isValid()) {
            roundBaselineBlocks = new HashMap<>();
            return;
        }

        BlockPos center = anchor.center();
        Map<Long, String> baseline = new HashMap<>();
        int minX = center.getX() - PLOT_HALF_SIZE;
        int maxX = center.getX() + PLOT_HALF_SIZE;
        int minY = anchor.floorY() + PLOT_SCAN_MIN_Y;
        int maxY = anchor.floorY() + PLOT_SCAN_MAX_Y;
        int minZ = center.getZ() - PLOT_HALF_SIZE;
        int maxZ = center.getZ() + PLOT_HALF_SIZE;

        for (BlockPos pos : BlockPos.iterate(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState state = client.world.getBlockState(pos);
            baseline.put(pos.asLong(), Registries.BLOCK.getId(state.getBlock()).getPath());
        }

        roundBaselineBlocks = baseline;
        reconcilePendingPlotUpdates();
    }

    private void reconcilePendingPlotUpdates() {
        if (roundBaselineBlocks.isEmpty() || pendingPlotUpdates.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, String> entry : pendingPlotUpdates.entrySet()) {
            long packedPos = entry.getKey();
            String blockId = entry.getValue();
            String baseline = roundBaselineBlocks.get(packedPos);
            if (blockId.isBlank()
                    || baseline == null
                    || blockId.equals(baseline)
                    || isIgnoredBuildBlock(blockId)) {
                trackedPlotBlocks.remove(packedPos);
            } else {
                trackedPlotBlocks.put(packedPos, blockId);
            }
        }

        pendingPlotUpdates.clear();
    }

    private void clearAutomationState() {
        autoGuessQueue.clear();
        autoGuessIndex = 0;
        guessHistory.clear();
        latestPreHintScores.clear();
        trackedPlotBlocks.clear();
        lastPreHintGuess = "";
        lastPreHintTheme = "";
        activeRoundKey = "";
        lastHudStatus = "waiting for GTB round";
        lastChangedBlockCount = 0;
        roundBaselineBlocks = new HashMap<>();
        pendingPlotUpdates.clear();
        lastAutoGuessAt = 0L;
    }

    public String getShortestTranslation(String englishWord) {
        return shortestTranslationMap.getOrDefault(englishWord.toLowerCase(Locale.ROOT), englishWord);
    }

    public List<String> getThemeWords() {
        return Collections.unmodifiableList(themeWords);
    }

    private String extractValueAfterColon(String message, String prefix) {
        String loweredMessage = message.toLowerCase(Locale.ROOT);
        String loweredPrefix = prefix.toLowerCase(Locale.ROOT) + ":";
        int start = loweredMessage.indexOf(loweredPrefix);
        if (start < 0) {
            return "";
        }
        return clean(message.substring(start + loweredPrefix.length()));
    }

    private boolean isWhiteTerracotta(BlockState state) {
        String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
        return "white_terracotta".equals(blockId)
                || "hardened_clay".equals(blockId)
                || "white_stained_hardened_clay".equals(blockId);
    }

    private boolean isIgnoredBuildBlock(String blockId) {
        return ABSOLUTE_SCAN_IGNORES.contains(blockId);
    }

    private boolean isWithinTrackedPlot(BlockPos pos, PlotAnchor anchor) {
        if (!anchor.isValid()) {
            return false;
        }
        BlockPos center = anchor.center();
        return pos.getX() >= center.getX() - PLOT_HALF_SIZE
                && pos.getX() <= center.getX() + PLOT_HALF_SIZE
                && pos.getZ() >= center.getZ() - PLOT_HALF_SIZE
                && pos.getZ() <= center.getZ() + PLOT_HALF_SIZE
                && pos.getY() >= anchor.floorY() + PLOT_SCAN_MIN_Y
                && pos.getY() <= anchor.floorY() + PLOT_SCAN_MAX_Y;
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

    private static String normalizeTheme(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static double triColorScore(BlockScan scan, String first, String second, String third) {
        return Math.min(capped(scan.count(first), 30), Math.min(capped(scan.count(second), 30), capped(scan.count(third), 30)));
    }

    private static double capped(int value, int cap) {
        return Math.min(value, cap);
    }

    private record ScoredTheme(String theme, double score) {
    }

    private record PlotAnchor(BlockPos center, int floorY, double confidence) {
        private static PlotAnchor unknown() {
            return new PlotAnchor(BlockPos.ORIGIN, 0, Double.NEGATIVE_INFINITY);
        }

        private boolean isValid() {
            return confidence > Double.NEGATIVE_INFINITY / 2.0;
        }

        private PlotAnchor shift(int xShift, int zShift) {
            return new PlotAnchor(center.add(xShift, 0, zShift), floorY, confidence - Math.abs(xShift) - Math.abs(zShift));
        }
    }

    private record GameContext(boolean inGuessTheBuild, boolean activeRound, boolean allowPreHint, String roundKey, PlotAnchor plotAnchor, boolean revealedThemeVisible) {
        private static GameContext inactive() {
            return new GameContext(false, false, false, "", PlotAnchor.unknown(), false);
        }
    }

    private static class BlockScan {
        private final Map<String, Integer> tokenCounts = new HashMap<>();
        private final Set<Long> positions = new HashSet<>();
        private final BlockPos center;
        private int totalBlocks;
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private BlockScan(BlockPos center) {
            this.center = center;
        }

        private void add(BlockPos pos, String blockPath) {
            totalBlocks++;
            positions.add(pack(pos.getX(), pos.getY(), pos.getZ()));
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());

            addToken(blockPath);
            for (String part : blockPath.split("_")) {
                addToken(part);
            }

            if (blockPath.endsWith("_stained_hardened_clay") || blockPath.endsWith("_terracotta")) {
                addToken("terracotta");
                addToken("clay");
            }
            if (blockPath.endsWith("_stained_glass") || blockPath.endsWith("_stained_glass_pane")) {
                addToken("glass");
            }
            if (blockPath.endsWith("_wool") || blockPath.endsWith("_carpet")) {
                addToken("wool");
            }
            if (blockPath.contains("log") || blockPath.contains("planks")) {
                addToken("wood");
            }
            if (blockPath.contains("leaves")) {
                addToken("green");
            }
        }

        private void addToken(String token) {
            tokenCounts.merge(token, 1, Integer::sum);
        }

        private int count(String token) {
            return tokenCounts.getOrDefault(token, 0);
        }

        private int width() {
            return maxX >= minX ? maxX - minX + 1 : 0;
        }

        private int depth() {
            return maxZ >= minZ ? maxZ - minZ + 1 : 0;
        }

        private int height() {
            return maxY >= minY ? maxY - minY + 1 : 0;
        }

        private boolean isTall() {
            return height() >= Math.max(width(), depth()) && height() >= 6;
        }

        private boolean isFlat() {
            return height() <= 4;
        }

        private boolean isWide() {
            return Math.max(width(), depth()) >= 10 && height() <= 8;
        }

        private boolean isSquareish() {
            int width = width();
            int depth = depth();
            return width > 0 && depth > 0 && Math.abs(width - depth) <= 3;
        }

        private double symmetryX() {
            if (positions.isEmpty()) {
                return 0.0;
            }

            double mirror = center.getX();
            int matches = 0;
            for (long packed : positions) {
                int x = unpackX(packed);
                int y = unpackY(packed);
                int z = unpackZ(packed);
                int reflectedX = (int) Math.round(mirror - (x - mirror));
                if (positions.contains(pack(reflectedX, y, z))) {
                    matches++;
                }
            }
            return matches / (double) positions.size();
        }

        private double symmetryZ() {
            if (positions.isEmpty()) {
                return 0.0;
            }

            double mirror = center.getZ();
            int matches = 0;
            for (long packed : positions) {
                int x = unpackX(packed);
                int y = unpackY(packed);
                int z = unpackZ(packed);
                int reflectedZ = (int) Math.round(mirror - (z - mirror));
                if (positions.contains(pack(x, y, reflectedZ))) {
                    matches++;
                }
            }
            return matches / (double) positions.size();
        }

        private static long pack(int x, int y, int z) {
            return (((long) x) & 0x3FFFFFFL) << 38 | ((((long) z) & 0x3FFFFFFL) << 12) | (((long) y) & 0xFFFL);
        }

        private static int unpackX(long packed) {
            return (int) (packed >> 38);
        }

        private static int unpackY(long packed) {
            return (int) (packed << 52 >> 52);
        }

        private static int unpackZ(long packed) {
            return (int) (packed << 26 >> 38);
        }
    }
}
