package io.github.nicksementsov.druzhina.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only calls made from common code (through DistExecutor). */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openMuster(BlockPos table) {
        Minecraft.getInstance().setScreen(new MusterScreen(table));
    }
}
