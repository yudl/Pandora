package com.garous.pandora.module.modules;

import com.garous.pandora.config.PandoraConfig;
import com.garous.pandora.gui.ClickGUIScreen;
import com.garous.pandora.module.Category;
import com.garous.pandora.module.Module;
import com.garous.pandora.module.ModuleManager;
import com.garous.pandora.module.setting.BooleanSetting;
import com.garous.pandora.module.setting.ModeSetting;
import com.garous.pandora.module.setting.ModuleSetting;
import com.garous.pandora.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pandora HUD: renders the on-screen ArrayList of enabled modules with
 * animated gradient / static colouring, configurable bar style, and
 * anchor position. The watermark 'P' colour in {@link ClickGUIScreen}
 * follows whatever colour the top ArrayList row is currently painting so
 * the logo stays in sync.
 */
public class HudModule extends Module {

    public static final String POS_TOP_LEFT = "top-left";
    public static final String POS_TOP_RIGHT = "top-right";
    public static final String POS_BOTTOM_LEFT = "bottom-left";
    public static final String POS_BOTTOM_RIGHT = "bottom-right";

    public static final String COLOR_GRADIENT = "gradient";
    public static final String COLOR_STATIC = "static";

    public static final String STYLE_NONE = "none";
    public static final String STYLE_SIDE = "side";
    public static final String STYLE_UNDERLINE = "underline";
    public static final String STYLE_OUTLINE = "outline";
    public static final String STYLE_SOLID = "solid";
    public static final String STYLE_GRADIENT_BAR = "gradient-bar";

    private final BooleanSetting arrayList = new BooleanSetting("arraylist", "arraylist", true);
    private final ModeSetting position = new ModeSetting("position", "position",
            List.of(POS_TOP_LEFT, POS_TOP_RIGHT, POS_BOTTOM_LEFT, POS_BOTTOM_RIGHT), POS_TOP_RIGHT);
    private final ModeSetting colorMode = new ModeSetting("color_mode", "color",
            List.of(COLOR_GRADIENT, COLOR_STATIC), COLOR_GRADIENT);
    private final ModeSetting style = new ModeSetting("style", "style",
            List.of(STYLE_NONE, STYLE_SIDE, STYLE_UNDERLINE, STYLE_OUTLINE, STYLE_SOLID, STYLE_GRADIENT_BAR),
            STYLE_SIDE);
    private final BooleanSetting background = new BooleanSetting("background", "background", true);
    private final BooleanSetting suffixes = new BooleanSetting("suffixes", "suffixes", true);
    // HSV triple, surfaced as 3 sliders. Used when colorMode == STATIC.
    private final NumberSetting hue = new NumberSetting("hue", "hue", 0, 360, 320);
    private final NumberSetting saturation = new NumberSetting("saturation", "sat", 0, 100, 80);
    private final NumberSetting brightness = new NumberSetting("brightness", "bri", 0, 100, 100);
    // Gradient tuning. spread = degrees of hue span between first and last row;
    // speed = seconds for one full hue cycle.
    private final NumberSetting gradientSpread = new NumberSetting("gradient_spread", "spread", 10, 360, 120);
    private final NumberSetting gradientSpeed = new NumberSetting("gradient_speed", "speed", 3, 60, 18);

    private final List<ModuleSetting<?>> settings = List.of(
            arrayList, position, colorMode, style, background, suffixes,
            hue, saturation, brightness, gradientSpread, gradientSpeed
    );

    public HudModule() {
        super("hud", "Pandora HUD: ArrayList with gradient/static colors, configurable position.", Category.RENDER);
    }

    @Override
    public List<ModuleSetting<?>> getSettings() {
        return settings;
    }

    @Override
    public void onConfigLoaded() {
        PandoraConfig config = PandoraConfig.getInstance();
        arrayList.setValue(config.getModuleOption(getName(), arrayList.getId(), true));
        position.setValue(config.getModuleTextOption(getName(), position.getId(), POS_TOP_RIGHT));
        colorMode.setValue(config.getModuleTextOption(getName(), colorMode.getId(), COLOR_GRADIENT));
        style.setValue(config.getModuleTextOption(getName(), style.getId(), STYLE_SIDE));
        background.setValue(config.getModuleOption(getName(), background.getId(), true));
        suffixes.setValue(config.getModuleOption(getName(), suffixes.getId(), true));
        hue.setValue(parseIntOr(config.getModuleTextOption(getName(), hue.getId(), "320"), 320));
        saturation.setValue(parseIntOr(config.getModuleTextOption(getName(), saturation.getId(), "80"), 80));
        brightness.setValue(parseIntOr(config.getModuleTextOption(getName(), brightness.getId(), "100"), 100));
        gradientSpread.setValue(parseIntOr(config.getModuleTextOption(getName(), gradientSpread.getId(), "120"), 120));
        gradientSpeed.setValue(parseIntOr(config.getModuleTextOption(getName(), gradientSpeed.getId(), "18"), 18));
    }

    private static int parseIntOr(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    public boolean isArrayListEnabled() {
        return isEnabled() && arrayList.getValue();
    }

    public void render(DrawContext context) {
        if (!isArrayListEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        TextRenderer textRenderer = client.textRenderer;
        if (textRenderer == null) return;

        List<String> entries = collectEntries();
        if (entries.isEmpty()) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int rowHeight = 11;
        int padX = 4;
        int padY = 2;
        int gap = 1;

        int maxLabelWidth = 0;
        for (String entry : entries) maxLabelWidth = Math.max(maxLabelWidth, textRenderer.getWidth(entry));

        String activeStyle = style.getValue();
        boolean sideBar = STYLE_SIDE.equalsIgnoreCase(activeStyle);
        // Side / gradient-bar add 4 px to the row footprint (bar width + gap).
        int sideBarSlot = (sideBar || STYLE_GRADIENT_BAR.equalsIgnoreCase(activeStyle)) ? 4 : 0;
        int rowFullWidth = maxLabelWidth + padX * 2 + sideBarSlot;

        boolean anchorTop = position.getValue().startsWith("top");
        boolean anchorRight = position.getValue().endsWith("right");

        int blockHeight = entries.size() * rowHeight + Math.max(0, entries.size() - 1) * gap + padY * 2;
        int x = anchorRight ? screenW - rowFullWidth - 2 : 2;
        int y = anchorTop ? 2 : screenH - blockHeight - 2;

        long now = System.currentTimeMillis();
        boolean gradient = COLOR_GRADIENT.equalsIgnoreCase(colorMode.getValue());

        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            int entryWidth = textRenderer.getWidth(entry);
            int rowX = anchorRight
                    ? x + (rowFullWidth - entryWidth - padX - sideBarSlot)
                    : x + padX;
            int rowY = y + padY + i * (rowHeight + gap);

            int bgX1 = anchorRight ? x : rowX - padX;
            int bgX2 = anchorRight ? x + rowFullWidth : rowX + entryWidth + padX + sideBarSlot;
            int rowTop = rowY - 1;
            int rowBot = rowY + rowHeight - 1;

            // Row's "base" colour, sampled once for bars / outlines / underlines.
            int rowColor = gradient ? rowBaseColor(now, i, entries.size()) : staticColor();

            if (background.getValue()) {
                context.fill(bgX1, rowTop, bgX2, rowBot, 0x99101015);
            }

            // Style decorations (rendered before text so text overlays correctly).
            switch (activeStyle.toLowerCase(Locale.ROOT)) {
                case STYLE_SIDE -> {
                    int barX = anchorRight ? bgX2 - 2 : bgX1;
                    context.fill(barX, rowTop, barX + 2, rowBot, rowColor | 0xFF000000);
                }
                case STYLE_UNDERLINE -> {
                    context.fill(bgX1 + 1, rowBot - 1, bgX2 - 1, rowBot, rowColor | 0xFF000000);
                }
                case STYLE_OUTLINE -> {
                    int c = rowColor | 0xFF000000;
                    context.fill(bgX1, rowTop, bgX2, rowTop + 1, c);
                    context.fill(bgX1, rowBot - 1, bgX2, rowBot, c);
                    context.fill(bgX1, rowTop, bgX1 + 1, rowBot, c);
                    context.fill(bgX2 - 1, rowTop, bgX2, rowBot, c);
                }
                case STYLE_SOLID -> {
                    context.fill(bgX1, rowTop, bgX2, rowBot, (rowColor & 0x00FFFFFF) | 0xAA000000);
                }
                case STYLE_GRADIENT_BAR -> {
                    // Horizontal gradient strip along the full row width.
                    int stripWidth = bgX2 - bgX1 - 2;
                    int barX0 = bgX1 + 1;
                    for (int dx = 0; dx < stripWidth; dx++) {
                        float frac = stripWidth <= 1 ? 0f : dx / (float) (stripWidth - 1);
                        int c = stripColor(now, i, entries.size(), frac);
                        context.fill(barX0 + dx, rowBot - 1, barX0 + dx + 1, rowBot, c | 0xFF000000);
                    }
                }
                default -> {
                    // STYLE_NONE - nothing.
                }
            }

            // Per-character text rendering so the colour can flow within a row.
            int penX = rowX;
            int textLen = entry.length();
            for (int ci = 0; ci < textLen; ci++) {
                String ch = entry.substring(ci, ci + 1);
                int chColor;
                if (gradient) {
                    float charFrac = textLen <= 1 ? 0f : ci / (float) (textLen - 1);
                    chColor = rowCharColor(now, i, entries.size(), charFrac);
                } else {
                    chColor = rowColor;
                }
                context.drawTextWithShadow(textRenderer, ch, penX, rowY, chColor | 0xFF000000);
                penX += textRenderer.getWidth(ch);
            }
        }

        int leadColor = gradient ? rowBaseColor(now, 0, entries.size()) : staticColor();
        ClickGUIScreen.WATERMARK_P_COLOR = leadColor | 0xFF000000;
    }

    private int staticColor() {
        return java.awt.Color.HSBtoRGB(hue.getValue() / 360f, saturation.getValue() / 100f, brightness.getValue() / 100f);
    }

    private List<String> collectEntries() {
        List<Module> all = ModuleManager.getInstance().getModules();
        List<EntryPair> rows = new ArrayList<>();
        for (Module m : all) {
            if (!m.isEnabled()) continue;
            if (m == this) continue;
            String suffix = suffixes.getValue() ? m.getStatusText() : "";
            String label = suffix == null || suffix.isEmpty() || "-".equals(suffix) || "*".equals(suffix)
                    ? m.getName().toLowerCase(Locale.ROOT)
                    : m.getName().toLowerCase(Locale.ROOT) + " - " + suffix;
            rows.add(new EntryPair(label, MinecraftClient.getInstance().textRenderer.getWidth(label)));
        }
        rows.sort(Comparator.comparingInt((EntryPair p) -> p.width).reversed());
        List<String> out = new ArrayList<>(rows.size());
        for (EntryPair p : rows) out.add(p.label);
        return out;
    }

    private record EntryPair(String label, int width) {}

    // ===================== Gradient maths =====================
    //
    // Each frame, a global hue phase advances at 1 cycle per gradientSpeed
    // seconds. Per row we add a slice of gradientSpread (in degrees) sized to
    // each row's index, so the spread is evenly distributed top-to-bottom and
    // doesn't bunch up. Per-character offset is a SECOND, smaller slice
    // applied across each row's text so individual characters flow without
    // jumping between distinct row hues.

    private float globalHueFrac(long now) {
        long cycleMs = Math.max(1, gradientSpeed.getValue()) * 1000L;
        return (now % cycleMs) / (float) cycleMs;
    }

    /** Base hue for a row, in fractional [0,1). */
    private float rowHueFrac(long now, int index, int total) {
        float frac = total <= 1 ? 0.5f : index / (float) (total - 1);
        float spread = gradientSpread.getValue() / 360f;
        return wrap01(globalHueFrac(now) + (frac - 0.5f) * spread);
    }

    private int rowBaseColor(long now, int index, int total) {
        float s = Math.max(0.05f, saturation.getValue() / 100f);
        float v = Math.max(0.10f, brightness.getValue() / 100f);
        return java.awt.Color.HSBtoRGB(rowHueFrac(now, index, total), s, v);
    }

    /**
     * Per-character colour: the row hue plus a SMALL extra slice (~10% of the
     * row spread) across the text width. Smooth in-row flow without strong
     * banding.
     */
    private int rowCharColor(long now, int index, int total, float charFrac) {
        float perCharSpread = (gradientSpread.getValue() / 360f) * 0.12f;
        float h = wrap01(rowHueFrac(now, index, total) + (charFrac - 0.5f) * perCharSpread);
        float s = Math.max(0.05f, saturation.getValue() / 100f);
        float v = Math.max(0.10f, brightness.getValue() / 100f);
        return java.awt.Color.HSBtoRGB(h, s, v);
    }

    /** Gradient-bar fill colour at a horizontal fraction along the row strip. */
    private int stripColor(long now, int index, int total, float frac) {
        float perStripSpread = gradientSpread.getValue() / 360f;
        float h = wrap01(rowHueFrac(now, index, total) + (frac - 0.5f) * perStripSpread);
        float s = Math.max(0.05f, saturation.getValue() / 100f);
        float v = Math.max(0.10f, brightness.getValue() / 100f);
        return java.awt.Color.HSBtoRGB(h, s, v);
    }

    private static float wrap01(float v) {
        v %= 1.0f;
        if (v < 0) v += 1.0f;
        return v;
    }
}
