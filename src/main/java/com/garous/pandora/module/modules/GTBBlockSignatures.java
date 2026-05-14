package com.garous.pandora.module.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 1.8.9-aware block tokenization and curated single-block theme hints for the
 * GTB build scanner. The Pandora client may receive either modern flat IDs
 * (oak_log, orange_terracotta) or legacy 1.8.9-mapped IDs (log, stained_hardened_clay)
 * depending on which translation layer the server uses, so this class normalizes both.
 */
public final class GTBBlockSignatures {

    private GTBBlockSignatures() {
    }

    public static final Set<String> LOBBY_BLOCKS = Set.of(
            "air", "cave_air", "void_air",
            "white_terracotta",
            "hardened_clay",
            "white_stained_hardened_clay",
            "barrier",
            "structure_void",
            "light",
            "bedrock"
    );

    public static final Set<String> COLOR_TOKENS = Set.of(
            "white", "orange", "magenta", "yellow", "lime", "pink",
            "gray", "grey", "silver", "cyan", "purple", "blue", "brown",
            "green", "red", "black", "light"
    );

    public static final Set<String> WOOD_SPECIES = Set.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "warped", "crimson", "mangrove", "cherry", "bamboo"
    );

    private static final Map<String, List<String>> SINGLE_BLOCK_THEMES = buildSingleBlockThemes();

    private static Map<String, List<String>> buildSingleBlockThemes() {
        Map<String, List<String>> map = new HashMap<>();
        // Utility / functional blocks
        map.put("crafting_table", List.of("crafting table", "table", "workbench"));
        map.put("workbench", List.of("crafting table", "workbench"));
        map.put("anvil", List.of("anvil"));
        map.put("chipped_anvil", List.of("anvil"));
        map.put("damaged_anvil", List.of("anvil"));
        map.put("furnace", List.of("furnace"));
        map.put("lit_furnace", List.of("furnace"));
        map.put("blast_furnace", List.of("furnace", "blast furnace"));
        map.put("smoker", List.of("smoker", "furnace"));
        map.put("brewing_stand", List.of("brewing stand", "potion"));
        map.put("enchanting_table", List.of("enchanting table", "enchantment table"));
        map.put("ender_chest", List.of("ender chest", "chest"));
        map.put("chest", List.of("chest", "treasure chest"));
        map.put("trapped_chest", List.of("chest"));
        map.put("dispenser", List.of("dispenser"));
        map.put("dropper", List.of("dropper"));
        map.put("hopper", List.of("hopper", "funnel"));
        map.put("jukebox", List.of("jukebox", "music"));
        map.put("note_block", List.of("note block", "music note"));
        map.put("noteblock", List.of("note block"));
        map.put("bookshelf", List.of("bookshelf", "book", "library"));
        map.put("beacon", List.of("beacon", "lighthouse"));
        map.put("piston", List.of("piston"));
        map.put("sticky_piston", List.of("piston"));
        map.put("daylight_detector", List.of("daylight sensor", "sensor"));

        // Lighting
        map.put("torch", List.of("torch"));
        map.put("redstone_torch", List.of("torch", "redstone torch"));
        map.put("glowstone", List.of("glowstone", "lamp", "light", "sun"));
        map.put("redstone_lamp", List.of("lamp", "light"));
        map.put("lit_redstone_lamp", List.of("lamp", "light"));
        map.put("sea_lantern", List.of("sea lantern", "lantern"));
        map.put("lantern", List.of("lantern"));
        map.put("jack_o_lantern", List.of("jack o lantern", "pumpkin", "halloween"));
        map.put("carved_pumpkin", List.of("jack o lantern", "pumpkin"));

        // Food / produce
        map.put("cake", List.of("cake", "birthday cake"));
        map.put("pumpkin", List.of("pumpkin"));
        map.put("melon", List.of("melon", "watermelon"));
        map.put("melon_block", List.of("melon", "watermelon"));
        map.put("hay_block", List.of("hay", "hay bale", "haystack"));
        map.put("hay_bale", List.of("hay", "hay bale"));

        // Plants
        map.put("cactus", List.of("cactus"));
        map.put("dandelion", List.of("dandelion", "flower"));
        map.put("yellow_flower", List.of("dandelion", "flower"));
        map.put("poppy", List.of("poppy", "flower", "rose"));
        map.put("red_flower", List.of("poppy", "rose", "flower"));
        map.put("rose", List.of("rose", "flower"));
        map.put("sunflower", List.of("sunflower", "flower"));
        map.put("brown_mushroom", List.of("mushroom"));
        map.put("red_mushroom", List.of("mushroom"));
        map.put("vine", List.of("vine"));
        map.put("lily_pad", List.of("lily pad", "lilypad"));
        map.put("waterlily", List.of("lily pad"));

        // Resource blocks - exact theme matches
        map.put("iron_block", List.of("iron", "iron block"));
        map.put("gold_block", List.of("gold", "gold block"));
        map.put("diamond_block", List.of("diamond"));
        map.put("emerald_block", List.of("emerald"));
        map.put("redstone_block", List.of("redstone"));
        map.put("lapis_block", List.of("lapis", "lapis lazuli"));
        map.put("coal_block", List.of("coal"));

        // Stone family / iconic
        map.put("obsidian", List.of("obsidian"));
        map.put("netherrack", List.of("netherrack", "nether"));
        map.put("end_stone", List.of("end stone", "endstone"));
        map.put("nether_brick", List.of("nether brick"));
        map.put("quartz_block", List.of("quartz"));
        map.put("prismarine", List.of("prismarine"));
        map.put("magma_block", List.of("magma"));
        map.put("magma", List.of("magma"));
        map.put("nether_wart_block", List.of("nether wart"));
        map.put("snow_block", List.of("snow"));
        map.put("snow", List.of("snow"));
        map.put("ice", List.of("ice"));
        map.put("packed_ice", List.of("ice"));
        map.put("blue_ice", List.of("ice"));
        map.put("clay", List.of("clay"));
        map.put("slime_block", List.of("slime"));
        map.put("sponge", List.of("sponge"));
        map.put("wet_sponge", List.of("sponge"));

        // Hostile / iconic
        map.put("tnt", List.of("tnt", "dynamite", "bomb"));
        map.put("dragon_egg", List.of("dragon egg", "egg"));
        map.put("end_portal_frame", List.of("end portal", "portal"));
        map.put("mob_spawner", List.of("spawner", "monster spawner"));
        map.put("spawner", List.of("spawner"));
        map.put("soul_sand", List.of("soul sand", "quicksand"));
        map.put("web", List.of("cobweb", "web", "spider web"));
        map.put("cobweb", List.of("cobweb", "web", "spider web"));

        // Heads
        map.put("skeleton_skull", List.of("skull", "skeleton"));
        map.put("skeleton_head", List.of("skull", "skeleton"));
        map.put("wither_skeleton_skull", List.of("wither", "skull"));
        map.put("zombie_head", List.of("zombie head", "head", "zombie"));
        map.put("creeper_head", List.of("creeper head", "creeper"));
        map.put("player_head", List.of("head", "player head", "face"));
        map.put("dragon_head", List.of("dragon head", "dragon"));

        // Misc placeable
        map.put("cauldron", List.of("cauldron", "pot"));
        map.put("flower_pot", List.of("flower pot", "plant pot"));
        map.put("ladder", List.of("ladder"));
        map.put("rail", List.of("rail", "track"));
        map.put("powered_rail", List.of("rail"));
        map.put("detector_rail", List.of("rail"));
        map.put("activator_rail", List.of("rail"));
        map.put("end_rod", List.of("end rod"));
        map.put("painting", List.of("painting"));
        map.put("item_frame", List.of("item frame", "frame"));
        map.put("armor_stand", List.of("armor stand"));

        return Map.copyOf(map);
    }

    public static List<String> singleBlockThemes(String blockId) {
        return SINGLE_BLOCK_THEMES.getOrDefault(blockId, List.of());
    }

    public static boolean isLobbyBlock(String blockId) {
        return blockId != null && LOBBY_BLOCKS.contains(blockId);
    }

    public static boolean isColorToken(String token) {
        return COLOR_TOKENS.contains(token);
    }

    /**
     * Decompose a raw block ID into all keyword tokens a theme might match against.
     * Handles 1.8.9 names (stained_hardened_clay, log, log2, leaves, leaves2,
     * yellow_flower, red_flower, web) and modern names (orange_terracotta, oak_log,
     * dandelion, poppy, cobweb).
     */
    public static List<String> tokenize(String rawBlockId) {
        if (rawBlockId == null || rawBlockId.isEmpty()) {
            return List.of();
        }
        String id = rawBlockId.toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        tokens.add(id);

        for (String part : id.split("_")) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }

        // Terracotta / stained clay (modern + 1.8.9)
        if (id.endsWith("_stained_hardened_clay") || id.equals("stained_hardened_clay")
                || id.endsWith("_terracotta") || id.equals("hardened_clay") || id.equals("terracotta")) {
            tokens.add("terracotta");
            tokens.add("clay");
        }
        if (id.endsWith("_glazed_terracotta")) {
            tokens.add("terracotta");
            tokens.add("glazed");
        }
        // Glass (regular, stained, panes, 1.8.9 thin_glass)
        if (id.contains("glass") || id.equals("thin_glass")) {
            tokens.add("glass");
        }
        if (id.endsWith("_wool") || id.equals("wool")) {
            tokens.add("wool");
            tokens.add("cloth");
        }
        if (id.endsWith("_carpet") || id.equals("carpet")) {
            tokens.add("carpet");
            tokens.add("wool");
        }
        if (id.endsWith("_concrete") || id.endsWith("_concrete_powder")) {
            tokens.add("concrete");
        }
        // Wood / planks / log (1.8.9 had log, log2, planks variants)
        if (id.contains("planks") || id.endsWith("_log") || id.endsWith("_wood")
                || id.equals("log") || id.equals("log2") || id.equals("planks")
                || id.endsWith("_stem") || id.endsWith("_hyphae")) {
            tokens.add("wood");
            tokens.add("plank");
        }
        if (id.contains("leaves") || id.equals("leaves") || id.equals("leaves2")) {
            tokens.add("leaves");
            tokens.add("green");
            tokens.add("tree");
            tokens.add("plant");
        }
        if (id.contains("sapling")) {
            tokens.add("sapling");
            tokens.add("tree");
            tokens.add("plant");
        }
        if (id.equals("water") || id.equals("flowing_water")) {
            tokens.add("water");
            tokens.add("blue");
            tokens.add("liquid");
        }
        if (id.equals("lava") || id.equals("flowing_lava")) {
            tokens.add("lava");
            tokens.add("orange");
            tokens.add("red");
            tokens.add("liquid");
            tokens.add("fire");
        }
        if (id.equals("fire") || id.equals("soul_fire")) {
            tokens.add("fire");
            tokens.add("orange");
            tokens.add("red");
        }
        if (id.equals("snow") || id.equals("snow_layer") || id.equals("snow_block")) {
            tokens.add("snow");
            tokens.add("white");
        }
        if (id.equals("ice") || id.equals("packed_ice") || id.equals("blue_ice")
                || id.equals("frosted_ice")) {
            tokens.add("ice");
            tokens.add("blue");
            tokens.add("white");
        }
        if (id.equals("sand") || id.contains("sandstone")) {
            tokens.add("sand");
            if (id.contains("red_") || id.equals("red_sand") || id.contains("red_sandstone")) {
                tokens.add("red");
            } else {
                tokens.add("yellow");
            }
        }
        if (id.equals("red_sand")) {
            tokens.add("sand");
            tokens.add("red");
        }
        if (id.equals("grass") || id.equals("grass_block") || id.equals("tall_grass")
                || id.equals("fern") || id.equals("large_fern")) {
            tokens.add("grass");
            tokens.add("green");
            tokens.add("plant");
        }
        if (id.equals("dirt") || id.equals("coarse_dirt") || id.equals("podzol")
                || id.equals("rooted_dirt") || id.equals("mud")) {
            tokens.add("dirt");
            tokens.add("brown");
        }
        if ((id.contains("stone") && !id.contains("redstone")) || id.equals("cobblestone")
                || id.contains("cobble") || id.contains("andesite")
                || id.contains("granite") || id.contains("diorite")) {
            tokens.add("stone");
            tokens.add("gray");
        }
        if (id.contains("brick")) {
            tokens.add("brick");
        }
        if (id.contains("stairs")) {
            tokens.add("stairs");
        }
        if (id.contains("slab")) {
            tokens.add("slab");
        }
        if (id.contains("fence")) {
            tokens.add("fence");
        }
        if (id.contains("gate")) {
            tokens.add("gate");
        }
        if (id.contains("door") && !id.contains("trapdoor")) {
            tokens.add("door");
        }
        if (id.contains("trapdoor")) {
            tokens.add("trapdoor");
        }
        if (id.contains("button")) {
            tokens.add("button");
        }
        if (id.contains("pressure_plate")) {
            tokens.add("plate");
        }
        if (id.contains("redstone")) {
            tokens.add("redstone");
        }
        if (id.contains("repeater")) {
            tokens.add("repeater");
        }
        if (id.contains("comparator")) {
            tokens.add("comparator");
        }
        if (id.contains("sign")) {
            tokens.add("sign");
        }
        if (id.contains("banner")) {
            tokens.add("banner");
        }
        if (id.endsWith("_bed") || id.equals("bed")) {
            tokens.add("bed");
        }
        if (id.contains("skull") || id.contains("head")) {
            tokens.add("skull");
            tokens.add("head");
        }
        if (id.contains("rail")) {
            tokens.add("rail");
        }
        if (id.contains("netherrack")) {
            tokens.add("nether");
            tokens.add("red");
        }
        if (id.contains("nether_brick") || id.equals("nether_bricks")) {
            tokens.add("nether");
            tokens.add("brick");
        }
        if (id.contains("end_stone") || id.contains("endstone") || id.contains("end_brick")) {
            tokens.add("end");
        }
        if (id.contains("quartz")) {
            tokens.add("quartz");
            tokens.add("white");
        }
        if (id.contains("prismarine")) {
            tokens.add("prismarine");
            tokens.add("cyan");
            tokens.add("blue");
        }
        if (id.contains("purpur")) {
            tokens.add("purpur");
            tokens.add("purple");
        }
        if (id.contains("obsidian")) {
            tokens.add("obsidian");
            tokens.add("black");
        }
        if (id.contains("glowstone")) {
            tokens.add("glowstone");
            tokens.add("yellow");
            tokens.add("light");
        }
        // Wood species → token alias
        for (String wood : WOOD_SPECIES) {
            if (id.startsWith(wood + "_") || id.equals(wood)) {
                tokens.add(wood);
                tokens.add("wood");
                break;
            }
        }
        // 1.8.9 yellow_flower / red_flower → flower
        if (id.endsWith("_flower") || id.equals("dandelion") || id.equals("poppy")
                || id.equals("rose") || id.equals("sunflower") || id.equals("tulip")) {
            tokens.add("flower");
        }
        // 1.8.9 web → cobweb canonical
        if (id.equals("web")) {
            tokens.add("cobweb");
        }
        // Map silver → gray for 1.8.9 light_gray equivalents
        if (tokens.remove("silver")) {
            tokens.add("gray");
            tokens.add("light");
        }
        // 1.8.9: stained_hardened_clay alone has no color in id; the color is metadata.
        // Modern always carries it as a prefix. We rely on the prefix path.

        return new ArrayList<>(tokens);
    }
}
