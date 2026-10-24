package io.github.nicksementsov.chessmod;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server settings, stored per world in {@code serverconfig/chessmod-server.toml}. */
public final class ChessConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue HARD_THINK_TIME_MS = BUILDER
            .comment("How long the hard computer opponent may think about a move, in milliseconds")
            .defineInRange("hardThinkTimeMs", 2500, 200, 30_000);

    public static final ForgeConfigSpec.IntValue COMPUTER_MOVE_DELAY_TICKS = BUILDER
            .comment("Minimum pause before the computer answers a move, in ticks (20 ticks = 1 second)")
            .defineInRange("computerMoveDelayTicks", 12, 0, 200);

    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_RESULTS = BUILDER
            .comment("Tell nearby players in chat how a game ended")
            .define("announceResults", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ChessConfig() {
    }
}
