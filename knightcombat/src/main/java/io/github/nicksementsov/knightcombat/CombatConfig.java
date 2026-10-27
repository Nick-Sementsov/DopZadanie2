package io.github.nicksementsov.knightcombat;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Settings of the combat rules (serverconfig/knightcombat-server.toml) and of the client (config/knightcombat-client.toml). */
public final class CombatConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.BooleanValue VANILLA_WEAPONS;
    private static final ForgeConfigSpec.BooleanValue MODDED_WEAPONS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_WEAPONS;
    private static final ForgeConfigSpec.BooleanValue WALLS_STOP_ATTACKS;
    private static final ForgeConfigSpec.IntValue MAX_STAMINA;
    private static final ForgeConfigSpec.DoubleValue STAMINA_REGEN;
    private static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    private static final ForgeConfigSpec.IntValue BATTLE_CRY_COOLDOWN;

    public static final ForgeConfigSpec.BooleanValue SCROLL_ATTACKS;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SWAY;
    public static final ForgeConfigSpec.BooleanValue STAMINA_BAR;
    public static final ForgeConfigSpec.BooleanValue HIT_MESSAGES;
    public static final ForgeConfigSpec.BooleanValue SWING_TRAILS;
    public static final ForgeConfigSpec.BooleanValue FIRST_PERSON_ARMS;
    public static final ForgeConfigSpec.DoubleValue WEAPON_SCALE;
    public static final ForgeConfigSpec.DoubleValue CAMERA_SHAKE;
    public static final ForgeConfigSpec.BooleanValue HIT_STOP;
    public static final ForgeConfigSpec.BooleanValue ATTACK_LUNGE;
    public static final ForgeConfigSpec.BooleanValue SCREEN_EFFECTS;

    static {
        ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();
        VANILLA_WEAPONS = server
                .comment("Swords and axes from Minecraft fight with slashes, overheads, stabs and blocks")
                .define("vanillaWeapons", true);
        MODDED_WEAPONS = server
                .comment("Weapons of other mods fight with the combat rules too: Epic Knights and its addons, and swords, axes,",
                        "maces, spears and the like of any mod")
                .define("moddedWeapons", true);
        EXCLUDED_WEAPONS = server
                .comment("Items that keep their own way of fighting. Item ids, * matches any text: \"magistuarmory:*\" leaves out",
                        "all of Epic Knights, \"*:wooden_sword\" every wooden sword. Lances of Epic Knights are always left out")
                .defineListAllowEmpty(List.of("excludedWeapons"), () -> List.of("*lance*", "minecraft:trident"), o -> o instanceof String);
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
        SWING_TRAILS = client
                .comment("Swings leave a short trail behind the blade")
                .define("swingTrails", true);
        FIRST_PERSON_ARMS = client
                .comment("Show the arms holding the weapon in the first person view")
                .define("firstPersonArms", true);
        WEAPON_SCALE = client
                .comment("Size of weapons in hand compared to the size the mod picks (two-handed and long weapons are drawn larger)")
                .defineInRange("weaponScale", 1.0, 0.5, 2.0);
        CAMERA_SHAKE = client
                .comment("How hard the camera shakes when blows land, are blocked or parried (0 turns the shaking off)")
                .defineInRange("cameraShake", 1.0, 0.0, 2.0);
        HIT_STOP = client
                .comment("A swing stops for an instant when it hits, as if the blade bit into the target")
                .define("hitStop", true);
        ATTACK_LUNGE = client
                .comment("Attacks carry you a little forward: stabs the most, then overheads and slashes")
                .define("attackLunge", true);
        SCREEN_EFFECTS = client
                .comment("The edges of the screen flash red when you are hit, white on a parry and darken when stamina runs low")
                .define("screenEffects", true);
        CLIENT_SPEC = client.build();
    }

    private CombatConfig() {
    }

    // Server values are synced to clients when they join. Before a world is loaded they are not available,
    // so every getter falls back to the default value.

    public static boolean vanillaWeapons() {
        return get(VANILLA_WEAPONS, true);
    }

    public static boolean moddedWeapons() {
        return get(MODDED_WEAPONS, true);
    }

    /** Whether the item with this id is left out of the combat rules by the server settings. */
    public static boolean isExcluded(ResourceLocation id) {
        String text = id.toString();
        for (String pattern : get(EXCLUDED_WEAPONS, List.<String>of())) {
            if (matches(pattern.toLowerCase(Locale.ROOT).trim(), text)) {
                return true;
            }
        }
        return false;
    }

    /** Glob match where * stands for any text. */
    static boolean matches(String pattern, String text) {
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return text.matches(regex.toString());
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
