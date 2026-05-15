package com.garous.pandora.net;

import com.garous.pandora.Pandora;
import com.garous.pandora.command.PandoraCommands;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight Hypixel /v2/status poller. Used to authoritatively confirm we
 * are in a Guess The Build session. Polls at most once every 30s to respect
 * the API's per-key rate limit; results are cached in {@link #latest}.
 *
 * Behaviour without an API key: latest stays null and callers should fall
 * back to scoreboard / chat detection.
 */
public final class HypixelApiClient {

    private static final HypixelApiClient INSTANCE = new HypixelApiClient();
    private static final long POLL_INTERVAL_MS = 30_000L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final AtomicReference<StatusSnapshot> latest = new AtomicReference<>();
    private volatile long nextPollAt;
    private volatile boolean pollInFlight;

    private HypixelApiClient() {}

    public static HypixelApiClient getInstance() {
        return INSTANCE;
    }

    public StatusSnapshot getLatest() {
        return latest.get();
    }

    public boolean isInGuessTheBuild() {
        StatusSnapshot s = latest.get();
        return s != null && s.online && "BUILD_BATTLE".equalsIgnoreCase(s.gameType)
                && s.mode != null && s.mode.toLowerCase().contains("guess");
    }

    /**
     * Called once per tick. No-op unless we have an API key, a player UUID,
     * and the poll interval has elapsed since the last call.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        if (now < nextPollAt) return;
        if (pollInFlight) return;
        String apiKey = PandoraCommands.getStoredApiKey();
        if (apiKey == null || apiKey.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        UUID uuid = client.player.getUuid();
        if (uuid == null) return;

        nextPollAt = now + POLL_INTERVAL_MS;
        pollInFlight = true;
        sendPollAsync(uuid, apiKey);
    }

    private void sendPollAsync(UUID uuid, String apiKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "https://api.hypixel.net/v2/status?uuid=" + uuid.toString().replace("-", "")))
                .header("API-Key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    pollInFlight = false;
                    if (throwable != null) {
                        Pandora.LOGGER.debug("[Pandora] Hypixel status poll failed: {}", throwable.toString());
                        return;
                    }
                    if (response.statusCode() != 200) {
                        if (response.statusCode() == 403) {
                            Pandora.LOGGER.warn("[Pandora] Hypixel API rejected the key (403). Clearing cached state.");
                            latest.set(null);
                        }
                        return;
                    }
                    parseAndStore(response.body());
                });
    }

    private void parseAndStore(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("session")) return;
            JsonObject session = root.getAsJsonObject("session");
            StatusSnapshot snapshot = new StatusSnapshot(
                    session.has("online") && session.get("online").getAsBoolean(),
                    session.has("gameType") ? session.get("gameType").getAsString() : "",
                    session.has("mode") ? session.get("mode").getAsString() : "",
                    session.has("map") ? session.get("map").getAsString() : ""
            );
            latest.set(snapshot);
        } catch (Exception exception) {
            Pandora.LOGGER.debug("[Pandora] Failed to parse Hypixel status: {}", exception.toString());
        }
    }

    public record StatusSnapshot(boolean online, String gameType, String mode, String map) {}
}
