package com.garous.pandora.command;

import com.garous.pandora.Pandora;
import com.garous.pandora.config.PandoraConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/**
 * Tiny in-chat command router. Intercepted by ChatCommandMixin when the
 * player runs '/pandora ...'. Suppresses the server-bound command and
 * applies the action client-side.
 */
public final class PandoraCommands {
    private PandoraCommands() {}

    public static final String CONFIG_KEY_API_KEY = "hypixel_api_key";

    /**
     * @param raw the command tail after stripping the leading '/pandora '.
     * @return true if this was a Pandora command and we handled it (caller
     *         should suppress the server send); false to let the command
     *         flow through normally.
     */
    public static boolean handle(String raw) {
        if (raw == null) return false;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            help();
            return true;
        }
        String[] parts = trimmed.split("\\s+", 2);
        String head = parts[0].toLowerCase(Locale.ROOT);
        String tail = parts.length > 1 ? parts[1].trim() : "";
        switch (head) {
            case "apikey", "key" -> {
                if (tail.isEmpty()) {
                    feedback("Usage: /pandora apikey <hypixel-api-key>", Formatting.YELLOW);
                } else {
                    PandoraConfig.getInstance().setModuleTextOption("pandora", CONFIG_KEY_API_KEY, tail);
                    feedback("Hypixel API key stored.", Formatting.GREEN);
                    Pandora.LOGGER.info("[Pandora] Hypixel API key updated.");
                }
                return true;
            }
            case "help", "?" -> {
                help();
                return true;
            }
            default -> {
                feedback("Unknown subcommand: " + head + ". Try /pandora help", Formatting.RED);
                return true;
            }
        }
    }

    public static String getStoredApiKey() {
        return PandoraConfig.getInstance().getModuleTextOption("pandora", CONFIG_KEY_API_KEY, "");
    }

    private static void help() {
        feedback("Pandora commands:", Formatting.LIGHT_PURPLE);
        feedback("  /pandora apikey <key>   - store your Hypixel API key", Formatting.GRAY);
    }

    private static void feedback(String text, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("[Pandora] ").formatted(Formatting.LIGHT_PURPLE)
                    .append(Text.literal(text).formatted(color)), false);
        }
    }
}
