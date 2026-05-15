package com.garous.pandora.module.modules;

import com.garous.pandora.config.PandoraConfig;
import com.garous.pandora.gui.ClickGUIScreen;
import com.garous.pandora.module.Category;
import com.garous.pandora.module.Module;
import com.garous.pandora.module.ModuleManager;
import com.garous.pandora.module.setting.BooleanSetting;
import com.garous.pandora.module.setting.ModeSetting;
import com.garous.pandora.module.setting.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pandora HUD: renders the on-screen ArrayList of enabled modules with
 * animated gradient / static colouring, optional bar style, and adjustable
 * anchor position. The watermark 'P' colour in {@link ClickGUIScreen}
 * follows whatever colour the ArrayList is currently painting so the logo
 * stays in sync.
 */
public class HudModule extends Module {

    public static final String POS_TOP_LEFT = "top-left";
    public static final String POS_TOP_RIGHT = "top-right";
    public static final String POS_BOTTOM_LEFT = "bottom-left";
    public static final String POS_BOTTOM_RIGHT = "bottom-right";

    public static final String COLOR_GRADIENT = "gradient";
    public static final String COLOR_STATIC = "static";

    public static final String STYLE_NORMAL = "normal";
    public static final String STYLE_BAR = "bar";

    private final BooleanSetting arrayList = new BooleanSetting("arraylist", "arraylist", true);
    private final ModeSetting position = new ModeSetting("position", "position",
            List.of(POS_TOP_LEFT, POS_TOP_RIGHT, POS_BOTTOM_LEFT, POS_BOTTOM_RIGHT), POS_TOP_RIGHT);
    private final ModeSetting colorMode = new ModeSetting("color_mode", "color",
            List.of(COLOR_GRADIENT, COLOR_STATIC), COLOR_GRADIENT);
    private final ModeSetting style = new ModeSetting("style", "style",
            List.of(STYLE_NORMAL, STYLE_BAR), STYLE_NORMAL);
    private final BooleanSetting background = new BooleanSetting("background", "background", true);
    private final BooleanSetting suffixes = new BooleanSetting("suffixes", "suffixes", true);
    /** Hex string editable from config; small/simple stand-in for a full HSV picker. */
    private final ModeSetting staticColor = new ModeSetting("static_color", "static-hex",
            List.of("#ff4fd8", "#ff5050", "#50d0ff", "#5dff80", "#ffd050", "#a060ff"), "#ff4fd8");

    private final List<ModuleSetting<?>> settings = List.of(
            arrayList, position, colorMode, style, background, suffixes, staticColor
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
        style.setValue(config.getModuleTextOption(getName(), style.getId(), STYLE_NORMAL));
        background.setValue(config.getModuleOption(getName(), background.getId(), true));
        suffixes.setValue(config.getModuleOption(getName(), suffixes.getId(), true));
        staticColor.setValue(config.getModuleTextOption(getName(), staticColor.getId(), "#ff4fd8"));
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

        // Compute max width across entries so all rows align on the right edge when
        // anchored to the right.
        int maxLabelWidth = 0;
        for (String entry : entries) maxLabelWidth = Math.max(maxLabelWidth, textRenderer.getWidth(entry));

        boolean barStyle = STYLE_BAR.equalsIgnoreCase(style.getValue());
        int barWidth = barStyle ? 2 : 0;
        int rowFullWidth = maxLabelWidth + padX * 2 + barWidth + (barStyle ? 2 : 0);

        boolean anchorTop = position.getValue().startsWith("top");
        boolean anchorRight = position.getValue().endsWith("right");

        int blockHeight = entries.size() * rowHeight + (entries.size() - 1) * gap + padY * 2;
        int x = anchorRight ? screenW - rowFullWidth - 2 : 2;
        int y = anchorTop ? 2 : screenH - blockHeight - 2;

        long now = System.currentTimeMillis();
        int staticArgb = parseHex(staticColor.getValue());
        boolean gradient = COLOR_GRADIENT.equalsIgnoreCase(colorMode.getValue());

        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            int entryWidth = textRenderer.getWidth(entry);
            int rowX = anchorRight ? x + (rowFullWidth - entryWidth - padX - barWidth - (barStyle ? 2 : 0)) : x + padX;
            int rowY = y + padY + i * (rowHeight + gap);

            int color = gradient ? gradientColor(now, i, entries.size()) : staticArgb;

            if (background.getValue()) {
                int bgX1 = anchorRight ? x : rowX - padX;
                int bgX2 = anchorRight ? x + rowFullWidth : rowX + entryWidth + padX + barWidth + (barStyle ? 2 : 0);
                context.fill(bgX1, rowY - 1, bgX2, rowY + rowHeight - 1, 0x99101015);
            }

            context.drawTextWithShadow(textRenderer, entry, rowX, rowY, color | 0xFF000000);

            if (barStyle) {
                int barX = anchorRight ? rowX + entryWidth + 2 : rowX - 4;
                context.fill(barX, rowY - 1, barX + barWidth, rowY + rowHeight - 1, color | 0xFF000000);
            }
        }

        // Drive the watermark 'P' tint from the top-most arraylist entry so the
        // logo cycles with the gradient instead of staying on the panel accent.
        int leadColor = gradient ? gradientColor(now, 0, entries.size()) : staticArgb;
        ClickGUIScreen.WATERMARK_P_COLOR = leadColor | 0xFF000000;
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

    /** Cycles through HSV hue ~10s per loop with a small per-row offset. */
    private int gradientColor(long now, int index, int total) {
        float hue = ((now % 10_000L) / 10_000.0f) + (index / (float) Math.max(1, total)) * 0.18f;
        hue %= 1.0f;
        return java.awt.Color.HSBtoRGB(hue, 0.65f, 1.0f);
    }

    private int parseHex(String hex) {
        if (hex == null) return 0xFFFF4FD8;
        String s = hex.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("#")) s = s.substring(1);
        try {
            int v = Integer.parseUnsignedInt(s, 16);
            if (s.length() == 6) v |= 0xFF000000;
            return v;
        } catch (NumberFormatException ignored) {
            return 0xFFFF4FD8;
        }
    }
}
