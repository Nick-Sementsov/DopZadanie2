package io.github.nicksementsov.knightcombat;

import net.minecraftforge.common.ForgeConfigSpec;

/** Settings of the combat rules (serverconfig/knightcombat-server.toml) and of the client (config/knightcombat-client.toml). */
public final class CombatConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.BooleanValue VANILLA_WEAPONS;
    private static final ForgeConfigSpec.BooleanValue WALLS_STOP_ATTACKS;
    private static final ForgeConfigSpec.IntValue MAX_STAMINA;
    private static final ForgeConfigSpec.DoubleValue STAMINA_REGEN;
    private static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    private static final ForgeConfigSpec.IntValue BATTLE_CRY_COOLDOWN;

    public static final ForgeConfigSpec.BooleanValue SCROLL_ATTACKS;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SWAY;
    public static final ForgeConfigSpec.BooleanValue STAMINA_BAR;
    public static final ForgeConfigSpec.BooleanValue HIT_MESSAGES;

    static {
        ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();
        VANILLA_WEAPONS = server
                .comment("Swords and axes from Minecraft (and from other mods) fight with slashes, overheads, stabs and blocks too")
                .define("vanillaWeapons", true);
        WALLS_STOP_ATTACKS = server
                .comment("Walls stop slashes and stabs that hit them, like in narrow corridors")
                .define("wallsStopAttacks", true);
        MAX_STAMINA = server
                .comment("Stamina of every player")
                .defineInRange("maxStamina", 100, 20, 1000);
        STAMINA_REGEN = server
                .comment("Stamina restored per second once a player has not spent any for a second")
                .defineInRange("staminaRegenPerSecond", 25.0, 1.0, 500.0);
        DAMAGE_MULTIPLIER = server
                .comment("Multiplier of all weapon damage")
                .defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        BATTLE_CRY_COOLDOWN = server
                .comment("Seconds between two battle cries of a player")
                .defineInRange("battleCryCooldownSeconds", 20, 0, 600);
        SERVER_SPEC = server.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        SCROLL_ATTACKS = client
                .comment("Mouse wheel up makes an overhead and wheel down a stab while a weapon is in hand (instead of changing the hotbar slot)")
                .define("scrollAttacks", true);
        CAMERA_SWAY = client
                .comment("The camera follows the swings a little")
                .define("cameraSway", true);
        STAMINA_BAR = client
                .comment("Show the stamina bar under the crosshair while a weapon is in hand")
                .define("staminaBar", true);
        HIT_MESSAGES = client
                .comment("Show short messages near the crosshair: parry, blocked, guard broken and so on")
                .define("hitMessages", true);
        CLIENT_SPEC = client.build();
    }

    private CombatConfig() {
    }

    // Server values are synced to clients when they join. Before a world is loaded they are not available,
    // so every getter falls back to the default value.

    public static boolean vanillaWeapons() {
        return get(VANILLA_WEAPONS, true);
    }

    public static boolean wallsStopAttacks() {
        return get(WALLS_STOP_ATTACKS, true);
    }

    public static float maxStamina() {
        return get(MAX_STAMINA, 100);
    }

    public static float staminaRegenPerTick() {
        return (float) (double) get(STAMINA_REGEN, 25.0) / 20f;
    }

    public static float damageMultiplier() {
        return (float) (double) get(DAMAGE_MULTIPLIER, 1.0);
    }

    public static int battleCryCooldownTicks() {
        return get(BATTLE_CRY_COOLDOWN, 20) * 20;
    }

    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }
}
