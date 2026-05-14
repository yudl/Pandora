package com.garous.pandora.mixin;

import com.garous.pandora.module.ModuleManager;
import com.garous.pandora.module.modules.GTBSolverEngine;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts action-bar messages, which Hypixel uses for GTB hints.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class OverlayMessageMixin {

    @Inject(method = "onOverlayMessage", at = @At("HEAD"))
    private void onOverlayMessage(OverlayMessageS2CPacket packet, CallbackInfo ci) {
        var gtbModule = ModuleManager.getInstance().getModule("gtb solver");
        if (gtbModule != null && gtbModule.isEnabled()) {
            GTBSolverEngine.getInstance().processActionBar(packet.text());
        }
    }
}
