package com.garous.pandora.gui;

import com.garous.pandora.config.PandoraConfig;
import com.garous.pandora.module.Category;
import com.garous.pandora.module.Module;
import com.garous.pandora.module.ModuleManager;
import com.garous.pandora.module.setting.BooleanSetting;
import com.garous.pandora.module.setting.ModeSetting;
import com.garous.pandora.module.setting.ModuleSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pandora's compact panel ClickGUI.
 */
public class ClickGUIScreen extends Screen {

    private static final int PANEL_WIDTH = 150;
    private static final int HEADER_HEIGHT = 19;
    private static final int MODULE_HEIGHT = 17;
    private static final int SETTING_HEIGHT = 14;
    private static final int INDENT = 9;
    private static final float SCREEN_ANIMATION_SPEED = 14.0f;
    // 720 px/s — a 4-row dropdown opens in ~80 ms instead of 2+ seconds.
    private static final float PANEL_ANIMATION_SPEED = 720.0f;

    private static final int COLOR_OVERLAY = 0x88161620;
    private static final int COLOR_PANEL_BG = 0xEE1B1B23;
    private static final int COLOR_PANEL_BORDER = 0xAA3A3A4A;
    private static final int COLOR_HEADER_BG = 0xFA22222B;
    private static final int COLOR_HEADER_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MODULE_ENABLED = 0xFFFF4FD8;
    private static final int COLOR_MODULE_DISABLED = 0xFFE6E6EB;
    private static final int COLOR_MODULE_ENABLED_BG = 0x553A123A;
    private static final int COLOR_MODULE_HOVER = 0x45FF4FD8;
    private static final int COLOR_MODULE_ROW = 0xD021212B;
    private static final int COLOR_SETTING_BG = 0xCC1B1B24;
    private static final int COLOR_SETTING_ALT_BG = 0xCC20202A;
    private static final int COLOR_ACCENT = 0xFFFF4FD8;
    private static final int COLOR_MUTED = 0xFF9292A3;
    private static final int COLOR_CHECKBOX = 0xFF23232E;

    private final List<Panel> panels = new ArrayList<>();
    private final Map<String, ModuleUiState> moduleStates = new HashMap<>();

    private boolean mouseWasDown;
    private boolean mouseDown;
    private boolean rightWasDown;
    private boolean rightDown;
    private boolean closing;
    private float screenProgress;
    private long lastFrameNanos;

    public ClickGUIScreen() {
        super(Text.literal("Pandora ClickGUI"));
    }

    @Override
    protected void init() {
        super.init();
        if (panels.isEmpty()) {
            int x = 12;
            int y = 16;
            for (Category category : Category.values()) {
                panels.add(new Panel(category, x, y));
                x += PANEL_WIDTH + 9;
            }
        }
        lastFrameNanos = System.nanoTime();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float frameDelta = updateScreenAnimation();
        if (closing && screenProgress <= 0.001f) {
            if (this.client != null) {
                this.client.setScreen(null);
            }
            return;
        }

        pollMouse();
        handleInput(mouseX, mouseY, mouseDown && !mouseWasDown, rightDown && !rightWasDown, !mouseDown && mouseWasDown);

        float easedProgress = easeOutCubic(screenProgress);
        int panelOffsetY = Math.round((1.0f - easedProgress) * -10.0f);

        context.fill(0, 0, this.width, this.height, withAlpha(COLOR_OVERLAY, easedProgress));
        renderWatermark(context, easedProgress);

        for (Panel panel : panels) {
            renderPanel(context, panel, mouseX, mouseY, frameDelta, easedProgress, panelOffsetY);
        }

        mouseWasDown = mouseDown;
        rightWasDown = rightDown;
    }

    public void requestClose() {
        panels.forEach(Panel::save);
        closing = true;
        for (Panel panel : panels) {
            panel.dragging = false;
        }
    }

    @Override
    public void close() {
        requestClose();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private float updateScreenAnimation() {
        long now = System.nanoTime();
        float frameDelta = lastFrameNanos == 0L ? 0.0f : Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.05f);
        lastFrameNanos = now;

        float target = closing ? 0.0f : 1.0f;
        screenProgress = approach(screenProgress, target, SCREEN_ANIMATION_SPEED * frameDelta);
        return frameDelta;
    }

    private void pollMouse() {
        if (this.client == null || this.client.getWindow() == null) {
            return;
        }

        long window = this.client.getWindow().getHandle();
        mouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    private void handleInput(double mouseX, double mouseY, boolean leftClicked, boolean rightClicked, boolean leftReleased) {
        if (closing) {
            return;
        }

        if (leftReleased) {
            panels.forEach(panel -> {
                if (panel.dragging) {
                    panel.dragging = false;
                    panel.save();
                }
            });
        }

        if (mouseDown) {
            for (Panel panel : panels) {
                if (panel.dragging) {
                    panel.x = (int) mouseX - panel.dragOffsetX;
                    panel.y = (int) mouseY - panel.dragOffsetY;
                }
            }
        }

        if (!leftClicked && !rightClicked) {
            return;
        }

        for (int index = panels.size() - 1; index >= 0; index--) {
            Panel panel = panels.get(index);
            if (isInside(mouseX, mouseY, panel.x, panel.y, PANEL_WIDTH, HEADER_HEIGHT)) {
                if (rightClicked) {
                    panel.expanded = !panel.expanded;
                    panel.save();
                    return;
                }

                panel.dragging = true;
                panel.dragOffsetX = (int) mouseX - panel.x;
                panel.dragOffsetY = (int) mouseY - panel.y;
                return;
            }
        }

        clickContent(mouseX, mouseY, leftClicked, rightClicked);
    }

    private void clickContent(double mouseX, double mouseY, boolean leftClicked, boolean rightClicked) {
        for (Panel panel : panels) {
            if (!panel.expanded) {
                continue;
            }

            int currentY = panel.y + HEADER_HEIGHT;
            for (Module module : ModuleManager.getInstance().getModulesByCategory(panel.category)) {
                ModuleUiState state = getModuleState(module);
                if (isInside(mouseX, mouseY, panel.x, currentY, PANEL_WIDTH, MODULE_HEIGHT)) {
                    if (moduleHasSettings(module) && isInside(mouseX, mouseY, panel.x + PANEL_WIDTH - 14, currentY, 14, MODULE_HEIGHT)) {
                        state.expanded = !state.expanded;
                        if (!state.expanded) {
                            state.openModeId = null;
                        }
                    } else if (rightClicked && moduleHasSettings(module)) {
                        state.expanded = !state.expanded;
                        if (!state.expanded) {
                            state.openModeId = null;
                        }
                    } else if (leftClicked) {
                        module.toggle();
                    }
                    return;
                }

                currentY += MODULE_HEIGHT;
                if (!state.expanded) {
                    continue;
                }

                for (ModuleSetting<?> setting : module.getSettings()) {
                    if (setting instanceof BooleanSetting booleanSetting) {
                        if (isInside(mouseX, mouseY, panel.x, currentY, PANEL_WIDTH, SETTING_HEIGHT) && (leftClicked || rightClicked)) {
                            booleanSetting.toggle();
                            PandoraConfig.getInstance().setModuleOption(module.getName(), booleanSetting.getId(), booleanSetting.getValue());
                            return;
                        }
                        currentY += SETTING_HEIGHT;
                        continue;
                    }

                    if (setting instanceof ModeSetting modeSetting) {
                        if (isInside(mouseX, mouseY, panel.x, currentY, PANEL_WIDTH, SETTING_HEIGHT)) {
                            if (leftClicked || rightClicked) {
                                state.openModeId = modeSetting.getId().equals(state.openModeId) ? null : modeSetting.getId();
                            }
                            return;
                        }
                        currentY += SETTING_HEIGHT;

                        if (modeSetting.getId().equals(state.openModeId)) {
                            List<String> options = modeSetting.getOptions();
                            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                                if (isInside(mouseX, mouseY, panel.x, currentY, PANEL_WIDTH, SETTING_HEIGHT) && (leftClicked || rightClicked)) {
                                    modeSetting.setIndex(optionIndex);
                                    PandoraConfig.getInstance().setModuleTextOption(module.getName(), modeSetting.getId(), modeSetting.getValue());
                                    state.openModeId = null;
                                    return;
                                }
                                currentY += SETTING_HEIGHT;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Accent colour used for the leading 'P' in the watermark. The HUD's
     * ArrayList module updates this to its current colour so the logo stays
     * in sync with the user's theme; until that module is enabled it
     * falls back to the panel accent.
     */
    public static volatile int WATERMARK_P_COLOR = COLOR_ACCENT;

    private void renderWatermark(DrawContext context, float alpha) {
        int pColor = withAlpha(WATERMARK_P_COLOR, alpha);
        context.drawTextWithShadow(this.textRenderer, "P", 5, 4, pColor);
        int pWidth = this.textRenderer.getWidth("P");
        context.drawTextWithShadow(this.textRenderer, "andora B1", 5 + pWidth, 4, withAlpha(COLOR_ACCENT, alpha));
    }

    private void renderPanel(DrawContext context, Panel panel, int mouseX, int mouseY, float frameDelta, float alpha, int offsetY) {
        float targetContentHeight = panel.expanded ? getPanelContentHeight(panel.category) : 0.0f;
        panel.visibleContentHeight = approach(panel.visibleContentHeight, targetContentHeight, PANEL_ANIMATION_SPEED * frameDelta);

        int x = panel.x;
        int y = panel.y + offsetY;
        int contentHeight = Math.round(panel.visibleContentHeight);
        int totalHeight = HEADER_HEIGHT + contentHeight;

        context.fill(x - 1, y - 1, x + PANEL_WIDTH + 1, y + totalHeight + 1, withAlpha(COLOR_PANEL_BORDER, alpha));
        context.fill(x, y, x + PANEL_WIDTH, y + totalHeight, withAlpha(COLOR_PANEL_BG, alpha));
        context.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, withAlpha(COLOR_HEADER_BG, alpha));
        context.fill(x, y, x + 3, y + HEADER_HEIGHT, withAlpha(COLOR_ACCENT, alpha));
        context.fill(x, y + HEADER_HEIGHT - 1, x + PANEL_WIDTH, y + HEADER_HEIGHT, withAlpha(COLOR_ACCENT, alpha));

        context.drawTextWithShadow(this.textRenderer, panel.category.getDisplayName(), x + 8, y + 5, withAlpha(COLOR_HEADER_TEXT, alpha));

        String icon = panel.category.getIcon();
        int iconWidth = this.textRenderer.getWidth(icon);
        context.drawTextWithShadow(this.textRenderer, icon, x + PANEL_WIDTH - iconWidth - 7, y + 5, withAlpha(COLOR_ACCENT, alpha));

        if (contentHeight > 0) {
            context.enableScissor(x, y + HEADER_HEIGHT, x + PANEL_WIDTH, y + HEADER_HEIGHT + contentHeight);
            int currentY = y + HEADER_HEIGHT;
            for (Module module : ModuleManager.getInstance().getModulesByCategory(panel.category)) {
                currentY += renderModuleBlock(context, module, x, currentY, mouseX, mouseY, alpha);
            }
            context.disableScissor();
        }

        context.fill(x, y + totalHeight - 1, x + PANEL_WIDTH, y + totalHeight, withAlpha(COLOR_ACCENT, alpha * 0.35f));
    }

    private int renderModuleBlock(DrawContext context, Module module, int x, int y, int mouseX, int mouseY, float alpha) {
        ModuleUiState state = getModuleState(module);
        renderModuleEntry(context, module, state, x, y, mouseX, mouseY, alpha);

        int renderedHeight = MODULE_HEIGHT;
        if (!state.expanded) {
            return renderedHeight;
        }

        int currentY = y + MODULE_HEIGHT;
        for (ModuleSetting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting booleanSetting) {
                renderBooleanSetting(context, booleanSetting, x, currentY, mouseX, mouseY, alpha);
                currentY += SETTING_HEIGHT;
                renderedHeight += SETTING_HEIGHT;
                continue;
            }

            if (setting instanceof ModeSetting modeSetting) {
                renderModeSetting(context, state, modeSetting, x, currentY, mouseX, mouseY, alpha);
                currentY += SETTING_HEIGHT;
                renderedHeight += SETTING_HEIGHT;

                if (modeSetting.getId().equals(state.openModeId)) {
                    for (String option : modeSetting.getOptions()) {
                        renderModeOption(context, modeSetting, option, x, currentY, mouseX, mouseY, alpha);
                        currentY += SETTING_HEIGHT;
                        renderedHeight += SETTING_HEIGHT;
                    }
                }
            }
        }

        return renderedHeight;
    }

    private void renderModuleEntry(DrawContext context, Module module, ModuleUiState state, int x, int y, int mouseX, int mouseY, float alpha) {
        boolean hovered = isInside(mouseX, mouseY, x, y, PANEL_WIDTH, MODULE_HEIGHT);
        context.fill(x, y, x + PANEL_WIDTH, y + MODULE_HEIGHT, withAlpha(COLOR_MODULE_ROW, alpha));

        if (module.isEnabled()) {
            context.fill(x, y, x + PANEL_WIDTH, y + MODULE_HEIGHT, withAlpha(COLOR_MODULE_ENABLED_BG, alpha));
        }
        if (hovered) {
            context.fill(x, y, x + PANEL_WIDTH, y + MODULE_HEIGHT, withAlpha(COLOR_MODULE_HOVER, alpha));
        }
        if (module.isEnabled()) {
            context.fill(x, y + 2, x + 2, y + MODULE_HEIGHT - 2, withAlpha(COLOR_ACCENT, alpha));
        }

        String name = module.getName().toLowerCase(Locale.ROOT);
        int textColor = module.isEnabled() ? COLOR_MODULE_ENABLED : COLOR_MODULE_DISABLED;
        context.drawTextWithShadow(this.textRenderer, name, x + 7, y + 4, withAlpha(textColor, alpha));

        String status = module.getStatusText();
        int statusWidth = this.textRenderer.getWidth(status);
        int statusX = x + PANEL_WIDTH - statusWidth - (moduleHasSettings(module) ? 16 : 7);
        context.drawTextWithShadow(this.textRenderer, status, statusX, y + 4, withAlpha(COLOR_MUTED, alpha));

        if (moduleHasSettings(module)) {
            String arrow = state.expanded ? "^" : "v";
            context.drawTextWithShadow(this.textRenderer, arrow, x + PANEL_WIDTH - 10, y + 4, withAlpha(COLOR_MUTED, alpha));
        }
    }

    private void renderBooleanSetting(DrawContext context, BooleanSetting setting, int x, int y, int mouseX, int mouseY, float alpha) {
        boolean hovered = isInside(mouseX, mouseY, x, y, PANEL_WIDTH, SETTING_HEIGHT);
        context.fill(x, y, x + PANEL_WIDTH, y + SETTING_HEIGHT, withAlpha(COLOR_SETTING_BG, alpha));
        if (hovered) {
            context.fill(x, y, x + PANEL_WIDTH, y + SETTING_HEIGHT, withAlpha(COLOR_MODULE_HOVER, alpha * 0.75f));
        }

        context.drawTextWithShadow(this.textRenderer, setting.getLabel(), x + INDENT, y + 4, withAlpha(COLOR_MODULE_DISABLED, alpha));
        int boxSize = 8;
        int boxX = x + PANEL_WIDTH - 12 - boxSize;
        int boxY = y + 4;
        context.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, withAlpha(COLOR_CHECKBOX, alpha));
        context.fill(boxX - 1, boxY - 1, boxX + boxSize + 1, boxY, withAlpha(COLOR_PANEL_BORDER, alpha));
        context.fill(boxX - 1, boxY + boxSize, boxX + boxSize + 1, boxY + boxSize + 1, withAlpha(COLOR_PANEL_BORDER, alpha));
        context.fill(boxX - 1, boxY, boxX, boxY + boxSize, withAlpha(COLOR_PANEL_BORDER, alpha));
        context.fill(boxX + boxSize, boxY, boxX + boxSize + 1, boxY + boxSize, withAlpha(COLOR_PANEL_BORDER, alpha));
        if (setting.getValue()) {
            context.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, withAlpha(COLOR_ACCENT, alpha));
        }
    }

    private void renderModeSetting(DrawContext context, ModuleUiState state, ModeSetting setting, int x, int y, int mouseX, int mouseY, float alpha) {
        boolean hovered = isInside(mouseX, mouseY, x, y, PANEL_WIDTH, SETTING_HEIGHT);
        context.fill(x, y, x + PANEL_WIDTH, y + SETTING_HEIGHT, withAlpha(COLOR_SETTING_BG, alpha));
        if (hovered) {
            context.fill(x, y, x + PANEL_WIDTH, y + SETTING_HEIGHT, withAlpha(COLOR_MODULE_HOVER, alpha * 0.75f));
        }

        context.drawTextWithShadow(this.textRenderer, setting.getLabel(), x + INDENT, y + 4, withAlpha(COLOR_MODULE_DISABLED, alpha));
        String value = setting.getDisplayValue();
        int valueWidth = this.textRenderer.getWidth(value);
        context.drawTextWithShadow(this.textRenderer, value, x + PANEL_WIDTH - valueWidth - 16, y + 4, withAlpha(COLOR_MUTED, alpha));
        context.drawTextWithShadow(this.textRenderer, setting.getId().equals(state.openModeId) ? "^" : "v", x + PANEL_WIDTH - 10, y + 4, withAlpha(COLOR_MUTED, alpha));
    }

    private void renderModeOption(DrawContext context, ModeSetting setting, String option, int x, int y, int mouseX, int mouseY, float alpha) {
        boolean hovered = isInside(mouseX, mouseY, x, y, PANEL_WIDTH, SETTING_HEIGHT);
        boolean selected = option.equalsIgnoreCase(setting.getValue());
        context.fill(x, y, x + PANEL_WIDTH, y + SETTING_HEIGHT, withAlpha(COLOR_SETTING_ALT_BG, alpha));
        if (selected) {
            context.fill(x, y, x + PANEL_WIDTH, y + SETTING_HEIGHT, withAlpha(COLOR_MODULE_ENABLED_BG, alpha));
        }
        if (hovered) {
            context.fill(x, y, x + PANEL_WIDTH, y + SETTING_HEIGHT, withAlpha(COLOR_MODULE_HOVER, alpha * 0.75f));
        }

        context.drawTextWithShadow(this.textRenderer, option, x + INDENT + 6, y + 4, withAlpha(selected ? COLOR_MODULE_ENABLED : COLOR_MODULE_DISABLED, alpha));
    }

    private float getPanelContentHeight(Category category) {
        int height = 0;
        for (Module module : ModuleManager.getInstance().getModulesByCategory(category)) {
            height += MODULE_HEIGHT;
            ModuleUiState state = getModuleState(module);
            if (state.expanded) {
                for (ModuleSetting<?> setting : module.getSettings()) {
                    height += SETTING_HEIGHT;
                    if (setting instanceof ModeSetting modeSetting && modeSetting.getId().equals(state.openModeId)) {
                        height += modeSetting.getOptions().size() * SETTING_HEIGHT;
                    }
                }
            }
        }
        return height;
    }

    private ModuleUiState getModuleState(Module module) {
        return moduleStates.computeIfAbsent(module.getName().toLowerCase(Locale.ROOT), ignored -> new ModuleUiState());
    }

    private static boolean moduleHasSettings(Module module) {
        return !module.getSettings().isEmpty();
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static float approach(float current, float target, float step) {
        if (Math.abs(target - current) <= step) {
            return target;
        }
        return current + Math.signum(target - current) * step;
    }

    private static float easeOutCubic(float value) {
        float inverted = 1.0f - clamp(value);
        return 1.0f - inverted * inverted * inverted;
    }

    private static int withAlpha(int color, float alphaMultiplier) {
        int alpha = (color >>> 24) & 0xFF;
        int adjustedAlpha = Math.round(alpha * clamp(alphaMultiplier));
        return (adjustedAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static class ModuleUiState {
        private boolean expanded;
        private String openModeId;
    }

    private static class Panel {
        private final Category category;
        private int x;
        private int y;
        private boolean expanded = true;
        private boolean dragging;
        private int dragOffsetX;
        private int dragOffsetY;
        private float visibleContentHeight;

        private Panel(Category category, int x, int y) {
            this.category = category;
            PandoraConfig.PanelState state = PandoraConfig.getInstance().getPanelState(category, x, y);
            this.x = state.x();
            this.y = state.y();
            this.expanded = state.expanded();
        }

        private void save() {
            PandoraConfig.getInstance().setPanelState(category, x, y, expanded);
        }
    }
}
