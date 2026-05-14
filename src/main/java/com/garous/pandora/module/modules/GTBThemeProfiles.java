package com.garous.pandora.module.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Curated shape/color/material profiles for common GTB themes.
 *
 * Each profile expresses what a build for that theme typically looks like:
 * dominant colors, material families, expected size class, and a shape hint.
 * This complements the generic token-overlap scoring with high-confidence
 * signals for popular themes (pizza, orange juice, traffic light, snowman, etc.).
 */
public final class GTBThemeProfiles {

    public enum Shape {
        FLAT,        // mostly 1-3 layers tall
        FLAT_ROUND,  // flat AND roughly circular footprint
        TALL,        // height >= max(width, depth) and >= 6
        TALL_NARROW, // tall + narrow footprint
        WIDE,        // long horizontally
        SYMMETRIC,   // mirror symmetry along X or Z
        BIG          // simply large (>= 60 placed blocks)
    }

    public record Profile(
            Set<String> colors,        // expected dominant colors (any match counts)
            Set<String> materials,     // expected material/keyword tokens
            List<Shape> shapes,        // expected shape hints (any match counts)
            int minBlocks,             // minimum placed-block count to fully credit
            int maxBlocks,             // 0 = no upper limit; otherwise penalize larger builds
            String signatureBlock      // optional exact block ID that single-handedly indicates this theme
    ) {
        public static Profile of(Set<String> colors, Set<String> materials, List<Shape> shapes) {
            return new Profile(colors, materials, shapes, 0, 0, null);
        }

        public static Profile signature(String blockId) {
            return new Profile(Set.of(), Set.of(), List.of(), 0, 6, blockId);
        }

        public static Profile signatureWith(String blockId, Set<String> colors, Set<String> materials) {
            return new Profile(colors, materials, List.of(), 0, 0, blockId);
        }

        public static Profile colors(Set<String> colors) {
            return new Profile(colors, Set.of(), List.of(), 0, 0, null);
        }

        public static Profile shape(Shape... shapes) {
            return new Profile(Set.of(), Set.of(), List.of(shapes), 0, 0, null);
        }
    }

    private static final Map<String, Profile> PROFILES = build();

    public static Profile lookup(String themeName) {
        if (themeName == null) {
            return null;
        }
        return PROFILES.get(themeName.toLowerCase(Locale.ROOT));
    }

    private static Map<String, Profile> build() {
        Map<String, Profile> map = new HashMap<>();

        // === Single-block / signature themes ===
        map.put("crafting table", Profile.signature("crafting_table"));
        map.put("workbench", Profile.signature("crafting_table"));
        map.put("anvil", Profile.signature("anvil"));
        map.put("furnace", Profile.signature("furnace"));
        map.put("chest", Profile.signature("chest"));
        map.put("ender chest", Profile.signature("ender_chest"));
        map.put("bookshelf", Profile.signature("bookshelf"));
        map.put("jukebox", Profile.signature("jukebox"));
        map.put("note block", Profile.signature("note_block"));
        map.put("dispenser", Profile.signature("dispenser"));
        map.put("dropper", Profile.signature("dropper"));
        map.put("hopper", Profile.signature("hopper"));
        map.put("cake", Profile.signature("cake"));
        map.put("tnt", Profile.signature("tnt"));
        map.put("dynamite", Profile.signature("tnt"));
        map.put("pumpkin", Profile.signature("pumpkin"));
        map.put("jack o lantern", Profile.signature("jack_o_lantern"));
        map.put("hay bale", Profile.signature("hay_block"));
        map.put("hay", Profile.signature("hay_block"));
        map.put("haystack", Profile.signature("hay_block"));
        map.put("melon", Profile.signature("melon"));
        map.put("watermelon", Profile.signatureWith("melon", Set.of("green", "red"), Set.of()));
        map.put("brewing stand", Profile.signature("brewing_stand"));
        map.put("enchanting table", Profile.signature("enchanting_table"));
        map.put("beacon", Profile.signature("beacon"));
        map.put("piston", Profile.signature("piston"));
        map.put("ladder", Profile.signature("ladder"));
        map.put("cobweb", Profile.signature("cobweb"));
        map.put("spider web", Profile.signature("cobweb"));
        map.put("flower pot", Profile.signature("flower_pot"));
        map.put("cauldron", Profile.signature("cauldron"));
        map.put("torch", Profile.signature("torch"));
        map.put("redstone torch", Profile.signature("redstone_torch"));
        map.put("dragon egg", Profile.signature("dragon_egg"));
        map.put("end portal", Profile.signature("end_portal_frame"));
        map.put("painting", Profile.signature("painting"));
        map.put("item frame", Profile.signature("item_frame"));
        map.put("armor stand", Profile.signature("armor_stand"));

        // Resource block themes
        map.put("iron", Profile.signature("iron_block"));
        map.put("gold", Profile.signature("gold_block"));
        map.put("diamond", Profile.signature("diamond_block"));
        map.put("emerald", Profile.signature("emerald_block"));
        map.put("redstone", Profile.signatureWith("redstone_block", Set.of("red"), Set.of("redstone")));
        map.put("lapis", Profile.signature("lapis_block"));
        map.put("lapis lazuli", Profile.signature("lapis_block"));
        map.put("coal", Profile.signature("coal_block"));
        map.put("obsidian", Profile.signature("obsidian"));
        map.put("netherrack", Profile.signature("netherrack"));
        map.put("end stone", Profile.signature("end_stone"));
        map.put("quartz", Profile.signature("quartz_block"));
        map.put("prismarine", Profile.signature("prismarine"));
        map.put("magma", Profile.signature("magma_block"));
        map.put("nether wart", Profile.signature("nether_wart_block"));
        map.put("snow", Profile.signature("snow_block"));
        map.put("ice", Profile.signature("ice"));
        map.put("clay", Profile.signature("clay"));
        map.put("slime", Profile.signature("slime_block"));
        map.put("sponge", Profile.signature("sponge"));
        map.put("cactus", Profile.signature("cactus"));
        map.put("mushroom", Profile.signature("red_mushroom"));
        map.put("sunflower", Profile.signature("sunflower"));
        map.put("dandelion", Profile.signature("dandelion"));
        map.put("poppy", Profile.signature("poppy"));
        map.put("rose", Profile.signature("poppy"));
        map.put("flower", Profile.signature("dandelion"));

        // Glowstone / lighting
        map.put("glowstone", Profile.signature("glowstone"));
        map.put("lamp", new Profile(Set.of("yellow", "white"), Set.of("glowstone", "redstone_lamp", "lamp", "light"), List.of(), 1, 0, null));
        map.put("lantern", Profile.signature("lantern"));
        map.put("sea lantern", Profile.signature("sea_lantern"));

        // === Food / drinks ===
        map.put("pizza", new Profile(
                Set.of("red", "orange", "yellow", "brown"),
                Set.of("wool", "terracotta", "clay"),
                List.of(Shape.FLAT_ROUND, Shape.FLAT),
                4, 0, null));
        map.put("orange juice", new Profile(
                Set.of("orange"),
                Set.of("glass", "terracotta", "wool", "concrete"),
                List.of(Shape.TALL_NARROW, Shape.TALL),
                4, 0, null));
        map.put("apple juice", new Profile(
                Set.of("red", "green"),
                Set.of("glass"),
                List.of(Shape.TALL_NARROW, Shape.TALL),
                4, 0, null));
        map.put("juice", new Profile(
                Set.of("orange", "yellow", "red", "green"),
                Set.of("glass"),
                List.of(Shape.TALL_NARROW, Shape.TALL),
                4, 0, null));
        map.put("milk", new Profile(
                Set.of("white"),
                Set.of("glass", "wool"),
                List.of(Shape.TALL_NARROW, Shape.TALL),
                4, 0, null));
        map.put("water", Profile.colors(Set.of("blue", "cyan")));
        map.put("burger", new Profile(
                Set.of("brown", "green", "red", "yellow"),
                Set.of("wool", "terracotta"),
                List.of(Shape.FLAT, Shape.WIDE),
                6, 0, null));
        map.put("hamburger", new Profile(
                Set.of("brown", "green", "red", "yellow"),
                Set.of("wool", "terracotta"),
                List.of(Shape.FLAT, Shape.WIDE),
                6, 0, null));
        map.put("hotdog", new Profile(
                Set.of("brown", "red", "yellow"),
                Set.of("wool", "terracotta"),
                List.of(Shape.WIDE),
                4, 0, null));
        map.put("hot dog", new Profile(
                Set.of("brown", "red", "yellow"),
                Set.of("wool", "terracotta"),
                List.of(Shape.WIDE),
                4, 0, null));
        map.put("taco", new Profile(
                Set.of("yellow", "brown", "green", "red"),
                Set.of("wool", "terracotta"),
                List.of(Shape.WIDE, Shape.SYMMETRIC),
                4, 0, null));
        map.put("french fries", new Profile(
                Set.of("yellow", "orange", "red"),
                Set.of("wool", "terracotta"),
                List.of(Shape.TALL),
                4, 0, null));
        map.put("fries", new Profile(
                Set.of("yellow", "orange", "red"),
                Set.of("wool", "terracotta"),
                List.of(Shape.TALL),
                4, 0, null));
        map.put("ice cream", new Profile(
                Set.of("white", "pink", "brown"),
                Set.of("wool", "snow", "concrete"),
                List.of(Shape.TALL),
                5, 0, null));
        map.put("popsicle", new Profile(
                Set.of("red", "pink", "blue", "orange"),
                Set.of("wool", "concrete"),
                List.of(Shape.TALL),
                4, 0, null));
        map.put("apple", new Profile(
                Set.of("red", "green", "brown"),
                Set.of("wool", "terracotta"),
                List.of(Shape.FLAT_ROUND),
                3, 25, null));
        map.put("banana", new Profile(
                Set.of("yellow"),
                Set.of("wool", "terracotta"),
                List.of(Shape.WIDE),
                3, 0, null));
        map.put("strawberry", new Profile(
                Set.of("red", "green"),
                Set.of("wool", "terracotta"),
                List.of(Shape.FLAT_ROUND),
                3, 0, null));
        map.put("orange", new Profile(
                Set.of("orange"),
                Set.of("wool", "terracotta", "concrete"),
                List.of(Shape.FLAT_ROUND),
                3, 0, null));
        map.put("lemon", new Profile(
                Set.of("yellow"),
                Set.of("wool", "terracotta", "concrete"),
                List.of(Shape.FLAT_ROUND),
                3, 0, null));
        map.put("egg", new Profile(
                Set.of("white"),
                Set.of("wool", "concrete", "snow"),
                List.of(Shape.FLAT_ROUND),
                3, 0, null));

        // === Nature / weather ===
        map.put("tree", new Profile(
                Set.of("green", "brown"),
                Set.of("log", "wood", "leaves"),
                List.of(Shape.TALL),
                10, 0, null));
        map.put("treehouse", new Profile(
                Set.of("green", "brown"),
                Set.of("log", "wood", "leaves", "planks"),
                List.of(Shape.TALL),
                25, 0, null));
        map.put("sun", new Profile(
                Set.of("yellow", "orange"),
                Set.of("wool", "glowstone", "concrete"),
                List.of(Shape.FLAT_ROUND),
                4, 0, null));
        map.put("moon", new Profile(
                Set.of("white", "gray"),
                Set.of("wool", "concrete", "snow"),
                List.of(Shape.FLAT_ROUND),
                4, 0, null));
        map.put("cloud", new Profile(
                Set.of("white"),
                Set.of("wool", "snow", "glass"),
                List.of(Shape.FLAT, Shape.WIDE),
                4, 0, null));
        map.put("rainbow", new Profile(
                Set.of("red", "orange", "yellow", "green", "blue", "purple"),
                Set.of("wool", "terracotta", "concrete"),
                List.of(Shape.WIDE),
                10, 0, null));
        map.put("fire", new Profile(
                Set.of("red", "orange", "yellow"),
                Set.of("netherrack", "fire", "glowstone", "wool"),
                List.of(Shape.TALL),
                4, 0, null));
        map.put("snowman", new Profile(
                Set.of("white", "orange"),
                Set.of("snow", "wool"),
                List.of(Shape.TALL),
                10, 0, null));
        map.put("snow man", new Profile(
                Set.of("white", "orange"),
                Set.of("snow", "wool"),
                List.of(Shape.TALL),
                10, 0, null));
        map.put("volcano", new Profile(
                Set.of("red", "orange", "gray", "brown"),
                Set.of("netherrack", "stone", "lava"),
                List.of(Shape.TALL),
                25, 0, null));
        map.put("waterfall", new Profile(
                Set.of("blue", "cyan", "gray"),
                Set.of("water", "stone"),
                List.of(Shape.TALL),
                10, 0, null));
        map.put("ocean", Profile.colors(Set.of("blue", "cyan")));
        map.put("sea", Profile.colors(Set.of("blue", "cyan")));
        map.put("beach", new Profile(
                Set.of("yellow", "blue"),
                Set.of("sand", "water"),
                List.of(Shape.FLAT, Shape.WIDE),
                10, 0, null));
        map.put("desert", new Profile(
                Set.of("yellow"),
                Set.of("sand", "sandstone", "cactus"),
                List.of(Shape.FLAT, Shape.WIDE),
                10, 0, null));
        map.put("mountain", new Profile(
                Set.of("gray", "white", "brown"),
                Set.of("stone", "snow", "dirt"),
                List.of(Shape.TALL),
                30, 0, null));
        map.put("rain", new Profile(
                Set.of("blue", "cyan"),
                Set.of("glass", "water"),
                List.of(Shape.TALL),
                4, 0, null));
        map.put("lightning", new Profile(
                Set.of("yellow", "white"),
                Set.of("wool", "glowstone"),
                List.of(Shape.TALL),
                4, 0, null));
        map.put("star", new Profile(
                Set.of("yellow", "white"),
                Set.of("wool", "glowstone"),
                List.of(Shape.SYMMETRIC, Shape.FLAT),
                5, 0, null));

        // === Buildings ===
        map.put("house", new Profile(
                Set.of("brown", "white", "red"),
                Set.of("planks", "wood", "log", "brick", "glass", "door", "stairs"),
                List.of(),
                25, 0, null));
        map.put("home", new Profile(
                Set.of("brown", "white", "red"),
                Set.of("planks", "wood", "log", "brick", "glass", "door", "stairs"),
                List.of(),
                20, 0, null));
        map.put("hut", new Profile(
                Set.of("brown"),
                Set.of("planks", "wood", "log"),
                List.of(),
                15, 0, null));
        map.put("cabin", new Profile(
                Set.of("brown"),
                Set.of("log", "planks", "wood"),
                List.of(),
                25, 0, null));
        map.put("castle", new Profile(
                Set.of("gray", "white"),
                Set.of("stone", "brick", "cobblestone", "quartz"),
                List.of(Shape.TALL, Shape.SYMMETRIC, Shape.BIG),
                40, 0, null));
        map.put("tower", new Profile(
                Set.of("gray", "white"),
                Set.of("stone", "brick", "cobblestone"),
                List.of(Shape.TALL, Shape.TALL_NARROW),
                15, 0, null));
        map.put("skyscraper", new Profile(
                Set.of("gray", "white", "black"),
                Set.of("glass", "quartz", "stone", "concrete"),
                List.of(Shape.TALL, Shape.TALL_NARROW),
                30, 0, null));
        map.put("bridge", new Profile(
                Set.of("brown", "gray"),
                Set.of("wood", "stone", "planks"),
                List.of(Shape.WIDE),
                15, 0, null));
        map.put("wall", new Profile(
                Set.of("gray", "brown"),
                Set.of("stone", "cobblestone", "brick"),
                List.of(Shape.WIDE),
                10, 0, null));
        map.put("statue", new Profile(
                Set.of("gray", "white"),
                Set.of("quartz", "stone"),
                List.of(Shape.TALL, Shape.SYMMETRIC),
                15, 0, null));
        map.put("well", new Profile(
                Set.of("gray", "brown"),
                Set.of("cobblestone", "stone", "wood"),
                List.of(Shape.SYMMETRIC),
                8, 0, null));
        map.put("fountain", new Profile(
                Set.of("blue", "white", "gray"),
                Set.of("quartz", "stone", "water"),
                List.of(Shape.SYMMETRIC),
                8, 0, null));
        map.put("igloo", new Profile(
                Set.of("white"),
                Set.of("snow", "ice"),
                List.of(),
                10, 0, null));
        map.put("pyramid", new Profile(
                Set.of("yellow"),
                Set.of("sand", "sandstone"),
                List.of(Shape.SYMMETRIC),
                15, 0, null));
        map.put("swimming pool", new Profile(
                Set.of("blue", "cyan", "white"),
                Set.of("water", "wool", "concrete", "quartz"),
                List.of(Shape.FLAT, Shape.WIDE),
                10, 0, null));
        map.put("pool", new Profile(
                Set.of("blue", "cyan"),
                Set.of("water", "wool", "concrete"),
                List.of(Shape.FLAT),
                10, 0, null));

        // === Vehicles ===
        map.put("car", new Profile(
                Set.of("red", "blue", "black", "gray", "yellow"),
                Set.of("wool", "terracotta", "concrete", "glass"),
                List.of(Shape.WIDE),
                10, 0, null));
        map.put("truck", new Profile(
                Set.of("red", "blue", "white"),
                Set.of("wool", "terracotta", "concrete"),
                List.of(Shape.WIDE),
                15, 0, null));
        map.put("airplane", new Profile(
                Set.of("white", "gray"),
                Set.of("wool", "iron", "quartz"),
                List.of(Shape.WIDE, Shape.SYMMETRIC),
                15, 0, null));
        map.put("plane", new Profile(
                Set.of("white", "gray"),
                Set.of("wool", "iron", "quartz"),
                List.of(Shape.WIDE, Shape.SYMMETRIC),
                10, 0, null));
        map.put("rocket", new Profile(
                Set.of("white", "red", "gray"),
                Set.of("wool", "concrete", "quartz"),
                List.of(Shape.TALL, Shape.SYMMETRIC),
                10, 0, null));
        map.put("submarine", new Profile(
                Set.of("yellow", "gray"),
                Set.of("wool", "iron"),
                List.of(Shape.WIDE),
                10, 0, null));
        map.put("ship", new Profile(
                Set.of("brown", "white"),
                Set.of("wood", "planks", "wool"),
                List.of(Shape.WIDE),
                20, 0, null));
        map.put("boat", new Profile(
                Set.of("brown"),
                Set.of("wood", "planks"),
                List.of(Shape.WIDE),
                6, 0, null));
        map.put("train", new Profile(
                Set.of("black", "gray", "red"),
                Set.of("wool", "iron", "rail"),
                List.of(Shape.WIDE),
                10, 0, null));
        map.put("bike", new Profile(
                Set.of("gray", "black"),
                Set.of("iron", "wool"),
                List.of(Shape.WIDE),
                6, 0, null));
        map.put("bicycle", new Profile(
                Set.of("gray", "black"),
                Set.of("iron", "wool"),
                List.of(Shape.WIDE),
                6, 0, null));
        map.put("helicopter", new Profile(
                Set.of("gray", "black"),
                Set.of("iron", "wool"),
                List.of(Shape.WIDE),
                10, 0, null));
        map.put("ambulance", new Profile(
                Set.of("white", "red"),
                Set.of("wool", "concrete"),
                List.of(Shape.WIDE),
                10, 0, null));
        map.put("airship", new Profile(
                Set.of("white", "gray"),
                Set.of("wool"),
                List.of(Shape.WIDE),
                10, 0, null));
        map.put("blimp", new Profile(
                Set.of("white", "gray"),
                Set.of("wool"),
                List.of(Shape.WIDE),
                10, 0, null));

        // === Animals (rough hints) ===
        map.put("dog", new Profile(Set.of("brown", "white", "black"), Set.of("wool"), List.of(), 8, 0, null));
        map.put("cat", new Profile(Set.of("black", "white", "orange", "gray"), Set.of("wool"), List.of(), 8, 0, null));
        map.put("pig", new Profile(Set.of("pink"), Set.of("wool", "concrete"), List.of(), 6, 0, null));
        map.put("cow", new Profile(Set.of("white", "black", "brown"), Set.of("wool"), List.of(), 8, 0, null));
        map.put("sheep", new Profile(Set.of("white"), Set.of("wool"), List.of(), 6, 0, null));
        map.put("chicken", new Profile(Set.of("white", "yellow"), Set.of("wool"), List.of(), 6, 0, null));
        map.put("duck", new Profile(Set.of("yellow", "orange"), Set.of("wool"), List.of(), 6, 0, null));
        map.put("frog", new Profile(Set.of("green"), Set.of("wool"), List.of(), 6, 0, null));
        map.put("fish", new Profile(Set.of("blue", "gray", "orange"), Set.of("wool", "glass"), List.of(Shape.WIDE), 5, 0, null));
        map.put("shark", new Profile(Set.of("gray", "blue"), Set.of("wool"), List.of(Shape.WIDE), 8, 0, null));
        map.put("snake", new Profile(Set.of("green", "brown", "yellow"), Set.of("wool"), List.of(Shape.WIDE), 6, 0, null));
        map.put("bee", new Profile(Set.of("yellow", "black"), Set.of("wool"), List.of(), 6, 0, null));
        map.put("spider", new Profile(Set.of("black", "brown"), Set.of("wool", "cobweb"), List.of(), 6, 0, null));
        map.put("butterfly", new Profile(Set.of("orange", "yellow", "red", "purple"), Set.of("wool"), List.of(Shape.SYMMETRIC, Shape.FLAT), 6, 0, null));
        map.put("creeper", new Profile(Set.of("green"), Set.of("wool"), List.of(Shape.TALL), 6, 0, null));
        map.put("zombie", new Profile(Set.of("green", "blue"), Set.of("wool"), List.of(Shape.TALL), 6, 0, null));
        map.put("skeleton", new Profile(Set.of("white", "gray"), Set.of("wool", "quartz"), List.of(Shape.TALL), 6, 0, null));
        map.put("enderman", new Profile(Set.of("black", "purple"), Set.of("wool"), List.of(Shape.TALL), 6, 0, null));
        map.put("dragon", new Profile(Set.of("black", "purple"), Set.of("wool"), List.of(Shape.WIDE), 15, 0, null));
        map.put("horse", new Profile(Set.of("brown", "white", "black"), Set.of("wool"), List.of(Shape.WIDE), 10, 0, null));

        // === Common objects ===
        map.put("calculator", new Profile(
                Set.of("gray", "black"),
                Set.of("button", "stone", "wool"),
                List.of(Shape.FLAT),
                4, 0, null));
        map.put("computer", new Profile(
                Set.of("gray", "black", "white"),
                Set.of("glass", "quartz", "stone"),
                List.of(),
                10, 0, null));
        map.put("phone", new Profile(
                Set.of("black", "gray", "white"),
                Set.of("glass", "quartz"),
                List.of(Shape.TALL_NARROW),
                4, 0, null));
        map.put("cellphone", new Profile(
                Set.of("black", "gray", "white"),
                Set.of("glass", "quartz"),
                List.of(Shape.TALL_NARROW),
                4, 0, null));
        map.put("tv", new Profile(
                Set.of("black", "gray"),
                Set.of("glass", "quartz", "stone"),
                List.of(Shape.FLAT, Shape.SYMMETRIC),
                6, 0, null));
        map.put("television", new Profile(
                Set.of("black", "gray"),
                Set.of("glass", "quartz", "stone"),
                List.of(Shape.FLAT, Shape.SYMMETRIC),
                10, 0, null));
        map.put("clock", new Profile(
                Set.of("white", "black", "gray", "brown"),
                Set.of("wool", "quartz", "stone"),
                List.of(Shape.FLAT_ROUND, Shape.SYMMETRIC),
                6, 0, null));
        map.put("alarm clock", new Profile(
                Set.of("red", "white", "black"),
                Set.of("wool", "quartz"),
                List.of(Shape.FLAT_ROUND, Shape.SYMMETRIC),
                6, 0, null));
        map.put("traffic light", new Profile(
                Set.of("red", "yellow", "green", "black"),
                Set.of("wool", "concrete", "terracotta", "glass"),
                List.of(Shape.TALL_NARROW),
                5, 0, null));
        map.put("stop sign", new Profile(
                Set.of("red", "white"),
                Set.of("wool", "terracotta"),
                List.of(Shape.FLAT, Shape.TALL),
                4, 0, null));
        map.put("book", new Profile(
                Set.of("brown", "red", "blue"),
                Set.of("wool", "planks", "bookshelf"),
                List.of(Shape.FLAT),
                4, 0, null));
        map.put("envelope", new Profile(
                Set.of("white"),
                Set.of("wool", "concrete"),
                List.of(Shape.FLAT, Shape.SYMMETRIC),
                6, 0, null));
        map.put("present", new Profile(
                Set.of("red", "green", "blue"),
                Set.of("wool"),
                List.of(),
                6, 0, null));
        map.put("gift", new Profile(
                Set.of("red", "green"),
                Set.of("wool"),
                List.of(),
                6, 0, null));
        map.put("heart", new Profile(
                Set.of("red", "pink"),
                Set.of("wool", "concrete", "terracotta"),
                List.of(Shape.SYMMETRIC, Shape.FLAT),
                6, 0, null));
        map.put("smile", new Profile(
                Set.of("yellow"),
                Set.of("wool"),
                List.of(Shape.FLAT_ROUND, Shape.SYMMETRIC),
                6, 0, null));
        map.put("smiley", new Profile(
                Set.of("yellow"),
                Set.of("wool"),
                List.of(Shape.FLAT_ROUND, Shape.SYMMETRIC),
                6, 0, null));
        map.put("face", new Profile(
                Set.of("yellow", "white", "brown"),
                Set.of("wool"),
                List.of(Shape.FLAT_ROUND, Shape.SYMMETRIC),
                6, 0, null));
        map.put("eye", new Profile(
                Set.of("white", "blue", "brown", "green"),
                Set.of("wool"),
                List.of(Shape.FLAT_ROUND, Shape.SYMMETRIC),
                4, 0, null));
        map.put("balloon", new Profile(
                Set.of("red", "blue", "yellow", "pink"),
                Set.of("wool"),
                List.of(Shape.TALL),
                5, 0, null));
        map.put("umbrella", new Profile(
                Set.of("red", "blue", "black"),
                Set.of("wool"),
                List.of(Shape.TALL, Shape.SYMMETRIC),
                6, 0, null));
        map.put("guitar", new Profile(
                Set.of("brown", "red"),
                Set.of("wool", "wood", "planks"),
                List.of(Shape.TALL),
                10, 0, null));
        map.put("piano", new Profile(
                Set.of("black", "white"),
                Set.of("wool", "quartz"),
                List.of(Shape.WIDE),
                10, 0, null));
        map.put("drum", new Profile(
                Set.of("red", "white", "yellow"),
                Set.of("wool"),
                List.of(Shape.SYMMETRIC),
                6, 0, null));
        map.put("microphone", new Profile(
                Set.of("black", "gray"),
                Set.of("wool", "iron"),
                List.of(Shape.TALL_NARROW),
                4, 0, null));
        map.put("crown", new Profile(
                Set.of("yellow"),
                Set.of("gold", "wool"),
                List.of(Shape.WIDE, Shape.SYMMETRIC),
                6, 0, null));
        map.put("sword", new Profile(
                Set.of("gray", "white", "brown"),
                Set.of("iron", "diamond", "wool"),
                List.of(Shape.TALL_NARROW),
                6, 0, null));
        map.put("bow", new Profile(
                Set.of("brown"),
                Set.of("wood", "planks", "wool"),
                List.of(Shape.TALL),
                4, 0, null));
        map.put("shield", new Profile(
                Set.of("red", "blue", "white", "gray"),
                Set.of("wool", "iron", "wood"),
                List.of(Shape.SYMMETRIC),
                8, 0, null));
        map.put("shoe", new Profile(
                Set.of("black", "white", "red"),
                Set.of("wool"),
                List.of(Shape.WIDE),
                4, 0, null));
        map.put("hat", new Profile(
                Set.of("black", "brown"),
                Set.of("wool"),
                List.of(Shape.WIDE),
                4, 0, null));
        map.put("clothes", new Profile(
                Set.of("blue", "red", "white"),
                Set.of("wool"),
                List.of(),
                6, 0, null));
        map.put("flag", new Profile(
                Set.of("red", "white", "blue"),
                Set.of("wool", "banner"),
                List.of(Shape.FLAT, Shape.TALL),
                6, 0, null));
        map.put("key", new Profile(
                Set.of("yellow", "gray"),
                Set.of("gold", "iron"),
                List.of(Shape.WIDE),
                4, 0, null));
        map.put("door", Profile.signature("oak_door"));
        map.put("window", new Profile(
                Set.of(),
                Set.of("glass"),
                List.of(Shape.FLAT, Shape.SYMMETRIC),
                4, 0, null));

        // === Body parts / signs ===
        map.put("arrow", new Profile(
                Set.of("black", "red", "yellow"),
                Set.of("wool", "concrete"),
                List.of(Shape.WIDE),
                5, 0, null));
        map.put("plus", new Profile(
                Set.of("red", "white", "green"),
                Set.of("wool", "concrete"),
                List.of(Shape.SYMMETRIC, Shape.FLAT),
                5, 0, null));
        map.put("cross", new Profile(
                Set.of("white", "red", "brown"),
                Set.of("wool", "wood"),
                List.of(Shape.SYMMETRIC, Shape.TALL),
                4, 0, null));
        map.put("checkmark", new Profile(
                Set.of("green"),
                Set.of("wool", "concrete"),
                List.of(),
                4, 0, null));

        // === Emoji-likes ===
        map.put("ghost", new Profile(
                Set.of("white"),
                Set.of("wool"),
                List.of(Shape.TALL),
                10, 0, null));
        map.put("alien", new Profile(
                Set.of("green"),
                Set.of("wool"),
                List.of(),
                8, 0, null));
        map.put("ufo", new Profile(
                Set.of("gray", "green"),
                Set.of("wool", "iron", "glass"),
                List.of(Shape.WIDE, Shape.SYMMETRIC),
                10, 0, null));
        map.put("robot", new Profile(
                Set.of("gray", "white"),
                Set.of("iron", "quartz", "wool"),
                List.of(Shape.TALL),
                10, 0, null));

        return Map.copyOf(map);
    }
}
