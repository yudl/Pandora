package com.garous.pandora.module;

public enum Category {
    COMBAT("combat", "c"),
    RENDER("render", "r"),
    MINIGAMES("minigames", "m");

    private final String name;
    private final String icon;

    Category(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getDisplayName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }
}
