package com.garous.pandora.mixin;

import com.garous.pandora.module.ModuleManager;
import com.garous.pandora.module.modules.GTBSolverEngine;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Streams single-block updates into the GTB solver so it can track placed blocks without rescanning the world.
 *
 * The @At("HEAD") injection point runs on the network thread (before
 * NetworkThreadUtils.forceMainThread). We hop to the main thread via
 * client.execute(...) to keep the engine's data structures touched by a
 * single thread.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class BlockUpdateMixin {

    @Inject(method = "onBlockUpdate", at = @At("HEAD"))
    private void onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        var gtbModule = ModuleManager.getInstance().getModule("gtb solver");
        if (gtbModule == null || !gtbModule.isEnabled()) return;
        BlockPos pos = packet.getPos();
        BlockState state = packet.getState();
        MinecraftClient.getInstance().execute(() ->
                GTBSolverEngine.getInstance().onBlockUpdate(pos, state));
    }
}
