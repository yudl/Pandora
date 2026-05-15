package com.garous.pandora.gui;

import com.garous.pandora.module.modules.GTBSolverEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Lightweight overlay that lets the user drag the GTB scanner HUD anywhere
 * on the screen. The HUD itself is still drawn by its normal renderer; this
 * screen just lays a mouse listener over it. Open with Right-Ctrl by default.
 */
public class HudEditScreen extends Screen {

    private boolean dragging;
    private boolean mouseWasDown;
    private int dragOffsetX;
    private int dragOffsetY;

    public HudEditScreen() {
        super(Text.literal("Pandora HUD Edit"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x66000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "Pandora HUD edit - drag the GTB HUD - Esc to close", this.width / 2, 8, 0xFFFFD050);

        int[] b = GTBSolverEngine.getInstance().getHudBounds();
        // Outline rectangle around the HUD so the user sees the draggable target.
        int x1 = b[0] - 1, y1 = b[1] - 1, x2 = b[0] + b[2] + 1, y2 = b[1] + b[3] + 1;
        int outline = 0xFFFF4FD8;
        context.fill(x1, y1, x2, y1 + 1, outline);
        context.fill(x1, y2 - 1, x2, y2, outline);
        context.fill(x1, y1, x1 + 1, y2, outline);
        context.fill(x2 - 1, y1, x2, y2, outline);

        handleMouse(mouseX, mouseY);
    }

    private void handleMouse(int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        long window = client.getWindow().getHandle();
        boolean mouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (mouseDown && !mouseWasDown) {
            int[] b = GTBSolverEngine.getInstance().getHudBounds();
            if (mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                dragging = true;
                dragOffsetX = mouseX - b[0];
                dragOffsetY = mouseY - b[1];
            }
        } else if (!mouseDown && mouseWasDown) {
            dragging = false;
        }
        if (mouseDown && dragging) {
            GTBSolverEngine.setHudOrigin(mouseX - dragOffsetX, mouseY - dragOffsetY);
        }
        mouseWasDown = mouseDown;
    }
}
