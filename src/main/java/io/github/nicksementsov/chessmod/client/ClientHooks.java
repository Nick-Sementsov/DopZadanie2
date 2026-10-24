package io.github.nicksementsov.chessmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only entry points called from common code through {@code DistExecutor}. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openChessScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ChessScreen(pos));
    }
}
