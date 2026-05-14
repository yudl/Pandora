package com.garous.pandora;

import com.garous.pandora.gui.ClickGUIScreen;
import com.garous.pandora.gui.GTBGuessHistoryHud;
import com.garous.pandora.config.PandoraConfig;
import com.garous.pandora.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side entrypoint for Pandora.
 * Registers keybindings and tick event handlers.
 */
public class PandoraClient implements ClientModInitializer {

    private static KeyBinding clickGuiKey;
    private static final KeyBinding.Category PANDORA_CATEGORY =
            KeyBinding.Category.create(Identifier.of(Pandora.MOD_ID, "controls"));

    @Override
    public void onInitializeClient() {
        // Register ClickGUI keybind (Right Shift by default)
        clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pandora.clickgui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                PANDORA_CATEGORY
        ));

        // Initialize the module manager (loads translation data etc.)
        ModuleManager.getInstance();

        // Register tick handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Handle ClickGUI keybind
            while (clickGuiKey.wasPressed()) {
                if (client.currentScreen instanceof ClickGUIScreen clickGUI) {
                    clickGUI.requestClose();
                } else if (client.currentScreen == null) {
                    client.setScreen(new ClickGUIScreen());
                }
            }

            // Tick all enabled modules
            ModuleManager.getInstance().onTick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PandoraConfig.getInstance().save());
        HudRenderCallback.EVENT.register(GTBGuessHistoryHud::render);

        Pandora.LOGGER.info("[Pandora] Client initialized. Press Right Shift to open ClickGUI.");
    }
}
