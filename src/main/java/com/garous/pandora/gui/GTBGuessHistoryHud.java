package com.garous.pandora.gui;

import com.garous.pandora.module.Module;
import com.garous.pandora.module.ModuleManager;
import com.garous.pandora.module.modules.GTBSolverEngine;
import com.garous.pandora.module.modules.GTBSolverModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class GTBGuessHistoryHud {
    private GTBGuessHistoryHud() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        Module module = ModuleManager.getInstance().getModule("gtb solver");
        if (!(module instanceof GTBSolverModule gtbSolverModule) || !gtbSolverModule.isEnabled() || !gtbSolverModule.isGuessHistoryHudEnabled()) {
            return;
        }

        GTBSolverEngine.getInstance().renderGuessHistoryHud(context);
    }
}
