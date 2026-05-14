package com.garous.pandora.mixin;

import com.garous.pandora.module.ModuleManager;
import com.garous.pandora.module.modules.GTBSolverEngine;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Streams multi-block chunk delta updates into the GTB solver for batch placements/fills.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ChunkDeltaUpdateMixin {

    @Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"))
    private void onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        var gtbModule = ModuleManager.getInstance().getModule("gtb solver");
        if (gtbModule != null && gtbModule.isEnabled()) {
            packet.visitUpdates(GTBSolverEngine.getInstance()::onBlockUpdate);
        }
    }
}
