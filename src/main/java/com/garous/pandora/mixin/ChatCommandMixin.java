package com.garous.pandora.mixin;

import com.garous.pandora.command.PandoraCommands;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts outgoing chat commands. When the player types '/pandora ...'
 * we route it through PandoraCommands instead of forwarding it to the
 * server. We hook the network handler (more stable across versions than
 * ClientPlayerEntity.sendCommand).
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ChatCommandMixin {

    @Inject(method = "sendChatCommand(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void interceptPandoraCommand(String command, CallbackInfo ci) {
        if (command == null) return;
        String lower = command.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("pandora")) {
            String tail = command.length() > "pandora".length() ? command.substring("pandora".length()).trim() : "";
            if (PandoraCommands.handle(tail)) {
                ci.cancel();
            }
        }
    }
}
