package io.github.nicksementsov.druzhina;

import net.minecraftforge.common.ForgeConfigSpec;

/** Settings of the server (serverconfig/druzhina-server.toml). */
public final class DruzhinaConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_SOLDIERS;
    public static final ForgeConfigSpec.DoubleValue PRICE;
    public static final ForgeConfigSpec.BooleanValue RAIDS;
    public static final ForgeConfigSpec.IntValue FIRST_RAID_MINUTES;
    public static final ForgeConfigSpec.IntValue RAID_MINUTES;
    public static final ForgeConfigSpec.IntValue MAX_BAND;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("army");
        MAX_SOLDIERS = b.comment("How many soldiers one player may lead (0: as many as he can pay for). A mod with realms",
                "(Prestol) may set the limit itself, by how far the player's realm has grown").defineInRange("armyLimit", 0, 0, 100000);
        PRICE = b.comment("Hiring prices are multiplied by this").defineInRange("priceMultiplier", 1.0, 0.0, 10.0);
        b.pop();
        b.push("bandits");
        RAIDS = b.comment("Bands of bandits come for the players now and then").define("raids", true);
        FIRST_RAID_MINUTES = b.comment("Minutes of play before the first band comes for a player").defineInRange("firstRaidMinutes", 15, 1, 600);
        RAID_MINUTES = b.comment("Minutes between bands, on average").defineInRange("raidMinutes", 20, 2, 600);
        MAX_BAND = b.comment("The largest band (a band grows with the army of the player it comes for)").defineInRange("maxBand", 10, 2, 32);
        b.pop();
        SPEC = b.build();
    }

    private DruzhinaConfig() {
    }
}
