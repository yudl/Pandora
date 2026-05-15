package com.garous.pandora;

import com.garous.pandora.gui.ClickGUIScreen;
import com.garous.pandora.gui.GTBGuessHistoryHud;
import com.garous.pandora.gui.HudEditScreen;
import com.garous.pandora.config.PandoraConfig;
import com.garous.pandora.module.Module;
import com.garous.pandora.module.ModuleManager;
import com.garous.pandora.module.modules.HudModule;
import com.garous.pandora.net.HypixelApiClient;
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
    private static KeyBinding hudEditKey;
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
        hudEditKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pandora.hudedit",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_CONTROL,
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
            while (hudEditKey.wasPressed()) {
                if (client.currentScreen instanceof HudEditScreen) {
                    client.setScreen(null);
                } else if (client.currentScreen == null) {
                    client.setScreen(new HudEditScreen());
                }
            }

            // Tick all enabled modules
            ModuleManager.getInstance().onTick();
            HypixelApiClient.getInstance().tick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PandoraConfig.getInstance().save());
        HudRenderCallback.EVENT.register(GTBGuessHistoryHud::render);
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            Module hud = ModuleManager.getInstance().getModule("hud");
            if (hud instanceof HudModule hudModule) hudModule.render(context);
        });

        Pandora.LOGGER.info("[Pandora] Client initialized. Press Right Shift to open ClickGUI.");
    }
}
