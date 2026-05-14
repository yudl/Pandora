package com.garous.pandora.mixin;

import com.garous.pandora.module.ModuleManager;
import com.garous.pandora.module.modules.GTBSolverEngine;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Streams multi-block chunk delta updates into the GTB solver for batch placements/fills.
 *
 * Runs on the network thread (@HEAD); we collect all positions first and
 * forward them to the main thread in one scheduled task.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ChunkDeltaUpdateMixin {

    @Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"))
    private void onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        var gtbModule = ModuleManager.getInstance().getModule("gtb solver");
        if (gtbModule == null || !gtbModule.isEnabled()) return;

        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        packet.visitUpdates((pos, state) -> {
            positions.add(pos.toImmutable());
            states.add(state);
        });
        if (positions.isEmpty()) return;

        MinecraftClient.getInstance().execute(() -> {
            GTBSolverEngine engine = GTBSolverEngine.getInstance();
            for (int i = 0; i < positions.size(); i++) {
                engine.onBlockUpdate(positions.get(i), states.get(i));
            }
        });
    }
}
