package io.github.nicksementsov.druzhina.entity;

import io.github.nicksementsov.knightcombat.api.KnightCombatApi;
import io.github.nicksementsov.knightcombat.combat.Lances;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Arms and armour of the soldiers, all of Epic Knights and its addons: Epic Knights itself (required), and Epic
 * Knights: Addon, Slavic Armory, Dark Ages and Antique Legacy when they are installed, which add more swords, axes,
 * spears, maces, helmets, the mail and scale of the Rus, the lamellar of Byzantium and of the steppe and the sabres
 * and armour of the Saracens. Men of the West wear the arms of the Latin kingdoms, men of the East those of the Rus,
 * Byzantium, the Saracens and the steppe (an eastern piece missing without its addon is made up from Epic Knights).
 * The common western roles have their kits in three grades: levies (0), men at arms (1) and picked men (2); the
 * others have kits of their own.
 */
public final class Kits {
    public static final int LEVY = 0;
    public static final int REGULAR = 1;
    public static final int PICKED = 2;

    private static final String EK = "magistuarmory:";
    private static final String ADDON = "magistuarmoryaddon:";
    private static final String RUS = "slavicarmory:";
    private static final String DARK = "darkagesarmory:";
    private static final String ANTIQUE = "antiquelegacy:";

    // ------------------------------------------------------------------ weapons

    private static final String[][] SWORDS = {
        {EK + "iron_shortsword", EK + "iron_katzbalger", EK + "stone_shortsword"},
        {EK + "iron_shortsword", EK + "iron_katzbalger", EK + "messer_sword", ADDON + "steel_arming_sword_type_xiii", ADDON + "steel_sidesword"},
        {EK + "steel_katzbalger", EK + "steel_shortsword", EK + "noble_sword", ADDON + "steel_arming_sword_type_xv", ADDON + "steel_saxon_sword"},
    };
    private static final String[][] SPEARS = {
        {EK + "wood_pike", EK + "iron_pike"},
        {EK + "iron_pike", EK + "iron_ranseur", EK + "iron_guisarme", ADDON + "steel_boar_spear"},
        {EK + "steel_pike", EK + "steel_ranseur", ADDON + "steel_partisan", ADDON + "steel_welsh_guisarme"},
    };
    private static final String[][] AXES = {
        {EK + "iron_lochaberaxe", EK + "stone_lochaberaxe"},
        {EK + "iron_lochaberaxe", ADDON + "steel_daneaxe", ADDON + "steel_broadaxe"},
        {EK + "steel_lochaberaxe", ADDON + "steel_daneaxe", ADDON + "steel_gallowglass_axe"},
    };
    private static final String[][] GREATSWORDS = {
        {EK + "iron_bastardsword", EK + "stone_claymore", EK + "rusted_bastardsword"},
        {EK + "iron_bastardsword", EK + "iron_claymore", EK + "iron_zweihander", ADDON + "steel_longsword", ADDON + "steel_two_handed_messer"},
        {EK + "steel_claymore", EK + "steel_zweihander", EK + "steel_flamebladedsword", ADDON + "steel_german_greatsword", ADDON + "steel_longsword"},
    };
    private static final String[] LEVY_ARMS = {EK + "pitchfork", EK + "club", EK + "wood_shortsword", EK + "stone_heavymace", EK + "wood_pike"};
    private static final String[] BANDIT_ARMS = {EK + "rusted_bastardsword", EK + "rusted_heavymace", EK + "club", EK + "barbedclub",
        EK + "stone_morgenstern", EK + "stone_shortsword"};
    private static final String[] CHIEFTAIN_ARMS = {EK + "rusted_bastardsword", EK + "iron_morgenstern", EK + "iron_heavymace",
        EK + "iron_chainmorgenstern", RUS + "steel_rus_axe"};

    private static final String[][] SHIELDS = {
        {EK + "wood_heatershield", EK + "wood_buckler", EK + "wood_roundshield"},
        {EK + "iron_heatershield", EK + "iron_kiteshield", EK + "iron_rondache"},
        {EK + "steel_heatershield", EK + "steel_kiteshield", EK + "steel_tartsche"},
    };
    private static final String[] BANDIT_SHIELDS = {EK + "wood_roundshield", EK + "wood_buckler", EK + "wood_target"};

    // the retinue of a Rus prince: sword or axe and a round or kite shield, mail and scale of the Rus
    private static final String[] RUS_ARMS = {RUS + "steel_rus_sword", RUS + "steel_rus_axe", DARK + "steel_viking_sword", DARK + "steel_jarls_sword",
        DARK + "steel_crayke_axe", EK + "iron_shortsword"};
    private static final String[] RUS_SHIELDS = {EK + "iron_roundshield", EK + "steel_roundshield", EK + "iron_kiteshield", EK + "steel_kiteshield"};
    private static final String[] RUS_HELMETS = {RUS + "gnezdovo_helmet", RUS + "nikolskoe_helmet", RUS + "shater_helmet", RUS + "tagancha_helmet",
        RUS + "ozernoe_helmet", RUS + "varangian_helmet", DARK + "gjermundbu_helmet", EK + "norman_helmet"};
    private static final String[] RUS_BODIES = {RUS + "rus_chainmail_chestplate", RUS + "lamellar_mail_chestplate", RUS + "rus_scale_chestplate",
        RUS + "kuyak_chestplate", DARK + "common_hauberk", EK + "chainmail_chestplate"};
    private static final String[] RUS_LEGS = {RUS + "rus_chainmail_leggings", EK + "chainmail_leggings"};
    private static final String[] RUS_FEET = {RUS + "rus_chainmail_boots", RUS + "lamellar_mail_boots", RUS + "rus_scale_boots", EK + "chainmail_boots"};
    // a vityaz: the best of the retinue in a yushman or heavy lamellar
    private static final String[] VITYAZ_ARMS = {RUS + "steel_rus_sword", RUS + "steel_rus_axe", EK + "noble_sword", ADDON + "steel_arming_sword_type_xv"};
    private static final String[] VITYAZ_HELMETS = {RUS + "andreevski_helmet", RUS + "shater_helmet", RUS + "raikovec_helmet", RUS + "tagancha_helmet",
        EK + "bascinet"};
    private static final String[] VITYAZ_BODIES = {RUS + "yushman_chestplate", RUS + "bahteretz_chestplate", RUS + "rus_heavy_lamellar_chestplate",
        EK + "knight_chestplate"};
    private static final String[] VITYAZ_FEET = {RUS + "bahteretz_boots", RUS + "rus_heavy_lamellar_boots", RUS + "rus_scale_boots", EK + "knight_boots"};
    // a knight of the West: arming sword and heater shield, or a poleaxe in both hands; plate and a great helm
    private static final String[] KNIGHTLY_SWORDS = {EK + "noble_sword", ADDON + "steel_king_sword", ADDON + "steel_arming_sword_type_xv",
        ADDON + "steel_arming_sword_type_xiv", EK + "steel_shortsword"};
    private static final String[] KNIGHTLY_POLEARMS = {EK + "steel_lucernhammer", ADDON + "steel_italian_poleaxe", ADDON + "steel_english_poleaxe",
        EK + "steel_bastardsword"};
    private static final String[] WESTERN_HELMETS = {EK + "greathelm", EK + "armet", EK + "grand_bascinet", EK + "sallet", ADDON + "sugarloaf_helmet"};
    private static final String[] WESTERN_BODIES = {EK + "knight_chestplate", EK + "xivcenturyknight_chestplate", ADDON + "xiii_century_knight_chestplate"};
    private static final String[] WESTERN_LEGS = {EK + "knight_leggings", EK + "xivcenturyknight_leggings", ADDON + "xiii_century_knight_leggings"};
    private static final String[] WESTERN_FEET = {EK + "knight_boots", EK + "xivcenturyknight_boots", ADDON + "xiii_century_knight_boots"};
    // a short spear for one hand, behind a great shield
    private static final String[] SHORT_SPEARS = {ADDON + "steel_short_spear", DARK + "steel_harnham_spear", DARK + "steel_kingston_spear",
        DARK + "steel_decorated_spear", ANTIQUE + "iron_doru", ADDON + "steel_boar_spear", EK + "iron_shortsword"};
    private static final String[] GREAT_SHIELDS = {EK + "iron_pavese", EK + "steel_pavese", ANTIQUE + "bronze_imperial_scutum", EK + "wood_pavese"};
    private static final String[] MACES = {ADDON + "steel_round_mace", ADDON + "steel_bar_mace", ADDON + "steel_war_hammer", EK + "iron_morgenstern",
        EK + "steel_morgenstern", EK + "iron_heavymace", EK + "iron_heavywarhammer"};
    private static final String[] LANCES = {EK + "steel_chivalrylance", ADDON + "steel_lance", EK + "iron_chivalrylance"};
    // a rider of the East: a Rus sword or a sabre
    private static final String[] RIDER_SWORDS = {RUS + "steel_rus_sword", DARK + "steel_magyar_sabre", ADDON + "steel_sabre",
        ADDON + "steel_cavalry_sabre"};
    // the steppe: a caftan or lamellar, a fur-rimmed hat or a light helmet
    private static final String[] STEPPE_HELMETS = {DARK + "eastern_hat", DARK + "patterned_eastern_hat", DARK + "red_patterned_eastern_hat",
        ADDON + "light_cuman_helmet", RUS + "shapka", RUS + "eastern_kettlehat"};
    private static final String[] STEPPE_BODIES = {DARK + "eastern_rich_kaftan", DARK + "red_eastern_rich_kaftan", DARK + "green_eastern_rich_kaftan",
        DARK + "balyk_sook_lamellar_chestplate", RUS + "kaftan"};
    private static final String[] STEPPE_FEET = {DARK + "eastern_rich_kaftan_boots", RUS + "kaftan_boots"};
    private static final String SHORT_BOW = "minecraft:bow";

    // ------------------------------------------------------------------ the kits of the East (and the new ones of the West)

    /**
     * A kit: the weapons (one is picked; {@code twoHands} when it is held in both), the shields (when a hand is free)
     * and the pieces of armour, each a list of what fits (an empty list: none of that piece).
     */
    private record Kit(String[] arms, String[] shields, String[] heads, String[] bodies, String[] legs, String[] feet) {
    }

    private static final String[] NONE = {};
    private static final String[] EAST_ROUND = {EK + "iron_target", EK + "steel_target", EK + "iron_roundshield", EK + "steel_roundshield"};
    private static final String[] BYZANTINE_SHIELDS = {EK + "iron_kiteshield", EK + "steel_kiteshield", EK + "iron_ellipticalshield",
        EK + "steel_ellipticalshield"};

    private static final Kit SARACEN_KIT = new Kit(
        new String[]{ADDON + "steel_scimitar", ADDON + "steel_sabre", DARK + "steel_magyar_sabre"},
        EAST_ROUND,
        new String[]{ADDON + "saracen_helmet", ADDON + "light_cuman_helmet", DARK + "eastern_hat", DARK + "patterned_eastern_hat"},
        new String[]{ADDON + "saracen_chestplate", EK + "lamellar_chestplate", DARK + "eastern_rich_kaftan"},
        new String[]{EK + "chainmail_leggings"},
        new String[]{ADDON + "saracen_boots", EK + "lamellar_boots"});
    private static final Kit SKUTATOS_KIT = new Kit(
        new String[]{ADDON + "steel_short_spear", DARK + "steel_kingston_spear", DARK + "steel_harnham_spear", ANTIQUE + "iron_doru"},
        BYZANTINE_SHIELDS,
        new String[]{ADDON + "scale_helmet", ANTIQUE + "iron_scythian_scale_helmet", RUS + "nikolskoe_helmet"},
        new String[]{RUS + "byzantine_scale_chestplate", RUS + "byzantine_lamellar_chestplate"},
        new String[]{EK + "chainmail_leggings", RUS + "rus_chainmail_leggings"},
        new String[]{RUS + "lamellar_mail_boots", EK + "lamellar_boots"});
    private static final Kit BERDYSH_KIT = new Kit(
        new String[]{RUS + "steel_bardiche"},
        NONE,
        new String[]{RUS + "eastern_kettlehat", RUS + "shapka", RUS + "shater_helmet"},
        new String[]{RUS + "kuyak_chestplate", RUS + "kaftan", RUS + "rus_gambeson"},
        new String[]{RUS + "rus_chainmail_leggings"},
        new String[]{RUS + "kaftan_boots", RUS + "rus_gambeson_boots"});
    private static final Kit ROGATINA_KIT = new Kit(
        new String[]{RUS + "steel_rogatina", RUS + "steel_winged_rogatina"},
        NONE,
        new String[]{RUS + "gnezdovo_helmet", RUS + "nikolskoe_helmet", RUS + "ozernoe_helmet", DARK + "desjatinna_helmet"},
        new String[]{RUS + "rus_chainmail_chestplate", RUS + "lamellar_mail_chestplate"},
        new String[]{RUS + "rus_chainmail_leggings"},
        new String[]{RUS + "rus_chainmail_boots"});
    private static final Kit VARANGIAN_KIT = new Kit(
        new String[]{ADDON + "steel_daneaxe", DARK + "steel_byzantine_axe"},
        NONE,
        new String[]{RUS + "varangian_helmet", DARK + "gjermundbu_helmet", DARK + "birka_helmet"},
        new String[]{RUS + "byzantine_lamellar_chestplate", DARK + "rich_byrnie_chestplate", RUS + "byzantine_scale_chestplate"},
        new String[]{RUS + "rus_chainmail_leggings"},
        new String[]{RUS + "lamellar_mail_boots", RUS + "rus_chainmail_boots"});
    private static final Kit STEPPE_ARCHER_KIT = new Kit(
        new String[]{SHORT_BOW}, NONE, STEPPE_HELMETS, STEPPE_BODIES, NONE, STEPPE_FEET);
    private static final Kit SAMOSTREL_KIT = new Kit(
        new String[]{EK + "heavy_crossbow"},
        NONE,
        new String[]{RUS + "eastern_kettlehat", RUS + "shater_helmet"},
        new String[]{RUS + "kuyak_chestplate", RUS + "rus_scale_chestplate"},
        new String[]{RUS + "rus_chainmail_leggings"},
        new String[]{RUS + "rus_chainmail_boots", RUS + "rus_scale_boots"});
    private static final Kit MAMLUK_KIT = new Kit(
        new String[]{ADDON + "steel_cavalry_sabre", ADDON + "steel_scimitar"},
        new String[]{EK + "steel_target", EK + "iron_target", EK + "steel_roundshield"},
        new String[]{ADDON + "mamluk_helmet", ADDON + "saracen_helmet"},
        new String[]{ADDON + "saracen_chestplate", DARK + "balyk_sook_lamellar_chestplate", EK + "lamellar_chestplate"},
        new String[]{EK + "chainmail_leggings"},
        new String[]{ADDON + "saracen_boots", EK + "lamellar_boots"});
    // a cataphract's lance and sabre are put on by hand (see equip): this kit is his armour and shield
    private static final Kit CATAPHRACT_KIT = new Kit(
        NONE,
        new String[]{EK + "steel_kiteshield", EK + "iron_kiteshield"},
        new String[]{ADDON + "cuman_captain_helmet", ADDON + "scale_helmet", RUS + "shater_helmet"},
        new String[]{RUS + "rus_heavy_lamellar_chestplate", RUS + "byzantine_scale_chestplate", DARK + "balyk_sook_lamellar_chestplate"},
        new String[]{RUS + "rus_chainmail_leggings"},
        new String[]{RUS + "rus_heavy_lamellar_boots", RUS + "rus_scale_boots"});
    private static final String[] CATAPHRACT_SABRES = {ADDON + "steel_cavalry_sabre", DARK + "steel_magyar_sabre", RUS + "steel_rus_sword"};
    private static final Kit HORSE_ARCHER_KIT = new Kit(
        new String[]{SHORT_BOW},
        NONE,
        STEPPE_HELMETS,
        new String[]{DARK + "balyk_sook_lamellar_chestplate", DARK + "eastern_rich_kaftan", RUS + "kaftan"},
        NONE,
        STEPPE_FEET);

    private static final Kit HALBERDIER_KIT = new Kit(
        new String[]{ADDON + "steel_swiss_halberd", ADDON + "steel_french_halberd", ADDON + "steel_voulge", ADDON + "steel_billhook",
            ADDON + "steel_glaive", EK + "steel_concavehalberd"},
        NONE,
        new String[]{ADDON + "morion", ADDON + "late_kettlehat", ADDON + "burgundian_kettlehat", EK + "kettlehat", ADDON + "opened_sallet"},
        new String[]{ADDON + "pikeman_chestplate", EK + "brigandine_chestplate", ADDON + "heavy_brigandine_chestplate"},
        new String[]{EK + "chainmail_leggings", ADDON + "splint_leggings"},
        new String[]{ADDON + "pikeman_boots", EK + "chainmail_boots"});
    private static final Kit CRUSADER_KIT = new Kit(
        new String[]{EK + "noble_sword", ADDON + "steel_arming_sword_type_xiii", ADDON + "steel_arming_sword_type_xiv", ADDON + "steel_king_sword"},
        new String[]{EK + "steel_heatershield", EK + "steel_kiteshield"},
        new String[]{EK + "greathelm", ADDON + "early_greathelm", ADDON + "late_greathelm", ADDON + "sugarloaf_helmet"},
        new String[]{EK + "crusader_chestplate", ADDON + "english_knight_chestplate", ADDON + "xiii_century_knight_chestplate"},
        new String[]{EK + "crusader_leggings", EK + "chainmail_leggings"},
        new String[]{EK + "crusader_boots", EK + "chainmail_boots"});
    private static final Kit SERGEANT_KIT = new Kit(
        new String[]{EK + "iron_shortsword", EK + "messer_sword", ADDON + "steel_arming_sword_type_xiii", ADDON + "steel_sidesword"},
        new String[]{EK + "iron_heatershield", EK + "iron_kiteshield"},
        new String[]{EK + "kettlehat", EK + "norman_helmet", EK + "chainmail_helmet", ADDON + "chapel"},
        new String[]{EK + "chainmail_chestplate", ADDON + "coat_of_plates_chestplate", EK + "brigandine_chestplate"},
        new String[]{EK + "chainmail_leggings"},
        new String[]{EK + "chainmail_boots"});

    // what a piece of the East is made up from when its addon is missing: Epic Knights' own
    private static final String[] FALLBACK_ARMS = {EK + "messer_sword", EK + "iron_shortsword"};
    private static final String[] FALLBACK_SHIELDS = {EK + "iron_roundshield", EK + "wood_roundshield"};
    private static final String[] FALLBACK_HEADS = {EK + "chainmail_helmet", EK + "kettlehat"};
    private static final String[] FALLBACK_BODIES = {EK + "lamellar_chestplate", EK + "chainmail_chestplate"};
    private static final String[] FALLBACK_LEGS = {EK + "chainmail_leggings"};
    private static final String[] FALLBACK_FEET = {EK + "lamellar_boots", EK + "chainmail_boots"};

    private static final String LONGBOW = EK + "longbow";
    private static final String CROSSBOW = EK + "heavy_crossbow";

    // ------------------------------------------------------------------ armour, by grade

    private static final String[][] HELMETS = {
        {EK + "coif", ADDON + "linen_coif", EK + "kettlehat"},
        {EK + "kettlehat", EK + "norman_helmet", EK + "chainmail_helmet", ADDON + "cervelliere", ADDON + "chapel", ADDON + "late_kettlehat"},
        {EK + "bascinet", EK + "barbute", EK + "sallet", ADDON + "visored_kettlehat", ADDON + "late_sallet", ADDON + "german_bascinet"},
        // knights
        {EK + "greathelm", EK + "armet", EK + "grand_bascinet", ADDON + "sugarloaf_helmet"},
    };
    private static final String[][] BODIES = {
        {EK + "gambeson_chestplate", ADDON + "chained_gambeson"},
        {EK + "chainmail_chestplate", ADDON + "chained_gambeson", DARK + "common_hauberk"},
        {EK + "brigandine_chestplate", ADDON + "coat_of_plates_chestplate", ADDON + "heavy_brigandine_chestplate"},
        {EK + "knight_chestplate", EK + "xivcenturyknight_chestplate", ADDON + "xiii_century_knight_chestplate"},
    };
    private static final String[][] LEGS = {
        {EK + "pantyhose"},
        {EK + "chainmail_leggings"},
        {EK + "chainmail_leggings", ADDON + "splint_leggings"},
        {EK + "knight_leggings", EK + "xivcenturyknight_leggings", ADDON + "xiii_century_knight_leggings"},
    };
    private static final String[][] FEET = {
        {EK + "gambeson_boots", ADDON + "chained_gambeson_boots"},
        {EK + "chainmail_boots", DARK + "common_hauberk_boots"},
        {EK + "chainmail_boots", ADDON + "coat_of_plates_boots"},
        {EK + "knight_boots", EK + "xivcenturyknight_boots", ADDON + "xiii_century_knight_boots"},
    };
    private static final String[] BANDIT_HEADS = {EK + "rustedkettlehat", EK + "rustednorman_helmet", EK + "rustedchainmail_helmet", EK + "coif"};
    private static final String[] BANDIT_BODIES = {EK + "gambeson_chestplate", EK + "rustedchainmail_chestplate", RUS + "kaftan"};
    private static final String[] BANDIT_FEET = {EK + "gambeson_boots", EK + "rustedchainmail_boots"};

    private Kits() {
    }

    /** Arms and armours {@code s} as a soldier of {@code role} of the given grade (0 to 2). */
    public static void equip(Soldier s, Role role, int grade, RandomSource rnd) {
        int g = Math.max(LEVY, Math.min(PICKED, grade));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            s.setItemSlot(slot, ItemStack.EMPTY);
        }
        s.setGear(Soldier.SPARE, ItemStack.EMPTY);
        switch (role) {
            case SWORDSMAN -> {
                hand(s, pick(rnd, SWORDS[g], Kits::melee), pick(rnd, SHIELDS[g], Kits::shield));
                armour(s, rnd, g, true);
            }
            case SPEARMAN -> {
                hand(s, pick(rnd, SPEARS[g], Kits::melee), ItemStack.EMPTY);
                armour(s, rnd, g, true);
            }
            case AXEMAN -> {
                hand(s, pick(rnd, AXES[g], Kits::melee), ItemStack.EMPTY);
                armour(s, rnd, g, true);
            }
            case WARRIOR -> {
                hand(s, pick(rnd, GREATSWORDS[g], Kits::melee), ItemStack.EMPTY);
                armour(s, rnd, g, true);
            }
            case ARCHER -> {
                hand(s, pick(rnd, new String[]{LONGBOW}, i -> i.getItem() instanceof BowItem), ItemStack.EMPTY);
                // archers go light: one grade less, and no leggings to run in
                armour(s, rnd, Math.max(LEVY, g - 1), false);
            }
            case CROSSBOWMAN -> {
                hand(s, pick(rnd, new String[]{CROSSBOW}, i -> i.getItem() instanceof CrossbowItem), ItemStack.EMPTY);
                armour(s, rnd, g, true);
            }
            case KNIGHT -> {
                hand(s, pick(rnd, VITYAZ_ARMS, Kits::oneHanded), pick(rnd, RUS_SHIELDS, Kits::shield));
                put(s, pick(rnd, VITYAZ_HELMETS, Kits::armour));
                put(s, pick(rnd, VITYAZ_BODIES, Kits::armour));
                put(s, pick(rnd, RUS_LEGS, Kits::armour));
                put(s, pick(rnd, VITYAZ_FEET, Kits::armour));
            }
            case DRUZHINNIK -> {
                hand(s, pick(rnd, RUS_ARMS, Kits::oneHanded), pick(rnd, RUS_SHIELDS, Kits::shield));
                put(s, pick(rnd, RUS_HELMETS, Kits::armour));
                put(s, pick(rnd, RUS_BODIES, Kits::armour));
                put(s, pick(rnd, RUS_LEGS, Kits::armour));
                put(s, pick(rnd, RUS_FEET, Kits::armour));
            }
            case SHIELDBEARER -> {
                hand(s, pick(rnd, SHORT_SPEARS, Kits::oneHanded), pick(rnd, GREAT_SHIELDS, Kits::shield));
                armour(s, rnd, g, true);
            }
            case MACEMAN -> {
                hand(s, pick(rnd, MACES, Kits::oneHanded), pick(rnd, SHIELDS[g], Kits::shield));
                armour(s, rnd, g, true);
            }
            case FOOT_KNIGHT -> {
                if (rnd.nextBoolean()) {
                    hand(s, pick(rnd, KNIGHTLY_SWORDS, Kits::melee), pick(rnd, SHIELDS[PICKED], Kits::shield));
                } else {
                    hand(s, pick(rnd, KNIGHTLY_POLEARMS, Kits::melee), ItemStack.EMPTY);
                }
                put(s, pick(rnd, WESTERN_HELMETS, Kits::armour));
                put(s, pick(rnd, WESTERN_BODIES, Kits::armour));
                put(s, pick(rnd, WESTERN_LEGS, Kits::armour));
                put(s, pick(rnd, WESTERN_FEET, Kits::armour));
            }
            case HALBERDIER -> wear(s, rnd, HALBERDIER_KIT, Kits::melee);
            case SARACEN -> wear(s, rnd, SARACEN_KIT, Kits::oneHanded);
            case SKUTATOS -> wear(s, rnd, SKUTATOS_KIT, Kits::oneHanded);
            case BERDYSHNIK -> wear(s, rnd, BERDYSH_KIT, Kits::melee);
            case ROGATINSHCHIK -> wear(s, rnd, ROGATINA_KIT, Kits::melee);
            case VARANGIAN -> wear(s, rnd, VARANGIAN_KIT, Kits::melee);
            case CRUSADER -> wear(s, rnd, CRUSADER_KIT, Kits::oneHanded);
            case STEPPE_ARCHER -> wear(s, rnd, STEPPE_ARCHER_KIT, i -> i.getItem() instanceof BowItem);
            case SAMOSTRELSHCHIK -> wear(s, rnd, SAMOSTREL_KIT, i -> i.getItem() instanceof CrossbowItem);
            case SERGEANT -> wear(s, rnd, SERGEANT_KIT, Kits::oneHanded);
            case MAMLUK -> wear(s, rnd, MAMLUK_KIT, Kits::oneHanded);
            case CATAPHRACT -> {
                wear(s, rnd, CATAPHRACT_KIT, Kits::oneHanded);
                // the lance is couched for the charge, the sabre waits at his side
                s.setItemSlot(EquipmentSlot.MAINHAND, pick(rnd, LANCES, i -> Lances.isLance(i)));
                s.setItemSlot(EquipmentSlot.OFFHAND, prefer(rnd, Kits::shield, CATAPHRACT_KIT.shields(), FALLBACK_SHIELDS));
                s.setGear(Soldier.SPARE, prefer(rnd, Kits::oneHanded, CATAPHRACT_SABRES, FALLBACK_ARMS));
            }
            case HORSEMAN -> {
                hand(s, prefer(rnd, Kits::oneHanded, RIDER_SWORDS, FALLBACK_ARMS), pick(rnd, RUS_SHIELDS, Kits::shield));
                put(s, pick(rnd, RUS_HELMETS, Kits::armour));
                put(s, pick(rnd, RUS_BODIES, Kits::armour));
                put(s, pick(rnd, RUS_FEET, Kits::armour));
            }
            case LANCER -> {
                // the lance is no weapon of the melee rules: it is couched for the charge
                s.setItemSlot(EquipmentSlot.MAINHAND, pick(rnd, LANCES, i -> Lances.isLance(i)));
                s.setItemSlot(EquipmentSlot.OFFHAND, pick(rnd, SHIELDS[PICKED], Kits::shield));
                // and a sword at his side for when the lance is no use
                s.setGear(Soldier.SPARE, pick(rnd, KNIGHTLY_SWORDS, Kits::oneHanded));
                put(s, pick(rnd, WESTERN_HELMETS, Kits::armour));
                put(s, pick(rnd, WESTERN_BODIES, Kits::armour));
                put(s, pick(rnd, WESTERN_LEGS, Kits::armour));
                put(s, pick(rnd, WESTERN_FEET, Kits::armour));
            }
            case HORSE_ARCHER -> wear(s, rnd, HORSE_ARCHER_KIT, i -> i.getItem() instanceof BowItem);
            case MAHOUT -> {
                // he drives the beast; the sabre is for when he is down from it
                hand(s, prefer(rnd, Kits::oneHanded, RIDER_SWORDS, FALLBACK_ARMS), ItemStack.EMPTY);
                put(s, prefer(rnd, Kits::armour, STEPPE_HELMETS, FALLBACK_HEADS));
                put(s, prefer(rnd, Kits::armour, STEPPE_BODIES, FALLBACK_BODIES));
                put(s, prefer(rnd, Kits::armour, STEPPE_FEET, FALLBACK_FEET));
            }
            case MILITIA -> {
                hand(s, pick(rnd, LEVY_ARMS, Kits::melee), rnd.nextInt(3) == 0 ? pick(rnd, SHIELDS[LEVY], Kits::shield) : ItemStack.EMPTY);
                if (rnd.nextBoolean()) {
                    put(s, pick(rnd, HELMETS[LEVY], Kits::armour));
                }
                put(s, pick(rnd, BODIES[LEVY], Kits::armour));
            }
            case BANDIT -> {
                hand(s, pick(rnd, BANDIT_ARMS, Kits::melee), rnd.nextInt(3) == 0 ? pick(rnd, BANDIT_SHIELDS, Kits::shield) : ItemStack.EMPTY);
                if (rnd.nextBoolean()) {
                    put(s, pick(rnd, BANDIT_HEADS, Kits::armour));
                }
                put(s, pick(rnd, BANDIT_BODIES, Kits::armour));
                if (rnd.nextBoolean()) {
                    put(s, pick(rnd, BANDIT_FEET, Kits::armour));
                }
            }
            case BANDIT_ARCHER -> {
                hand(s, pick(rnd, new String[]{LONGBOW}, i -> i.getItem() instanceof BowItem), ItemStack.EMPTY);
                put(s, pick(rnd, new String[]{EK + "gambeson_chestplate", RUS + "kaftan"}, Kits::armour));
                if (rnd.nextBoolean()) {
                    put(s, pick(rnd, new String[]{EK + "coif", RUS + "shapka"}, Kits::armour));
                }
            }
            case CHIEFTAIN -> {
                hand(s, pick(rnd, CHIEFTAIN_ARMS, Kits::melee), ItemStack.EMPTY);
                put(s, pick(rnd, new String[]{EK + "rustedgreathelm", EK + "rustedbarbute"}, Kits::armour));
                put(s, pick(rnd, new String[]{EK + "rustedcrusader_chestplate", EK + "rustedhalfarmor_chestplate"}, Kits::armour));
                put(s, pick(rnd, new String[]{EK + "rustedchainmail_leggings"}, Kits::armour));
                put(s, pick(rnd, new String[]{EK + "rustedcrusader_boots", EK + "rustedchainmail_boots"}, Kits::armour));
            }
        }
    }

    /** The weapon he is shown with, of a man at arms of this role. */
    public static ItemStack icon(Role role) {
        if (role == Role.MAHOUT) {
            return new ItemStack(io.github.nicksementsov.druzhina.registry.DruzhinaRegistry.WAR_ELEPHANT_EGG.get());
        }
        String[] ids = switch (role) {
            case SWORDSMAN -> SWORDS[REGULAR];
            case SPEARMAN -> SPEARS[REGULAR];
            case AXEMAN -> AXES[REGULAR];
            case WARRIOR -> GREATSWORDS[REGULAR];
            case ARCHER, BANDIT_ARCHER -> new String[]{LONGBOW};
            case CROSSBOWMAN -> new String[]{CROSSBOW};
            case KNIGHT -> VITYAZ_ARMS;
            case DRUZHINNIK -> RUS_ARMS;
            case SHIELDBEARER -> GREAT_SHIELDS;
            case MACEMAN -> MACES;
            case FOOT_KNIGHT -> KNIGHTLY_POLEARMS;
            case HORSEMAN, MAHOUT -> RIDER_SWORDS;
            case LANCER, CATAPHRACT -> LANCES;
            case HORSE_ARCHER, STEPPE_ARCHER -> new String[]{SHORT_BOW};
            case SAMOSTRELSHCHIK -> new String[]{CROSSBOW};
            case HALBERDIER -> HALBERDIER_KIT.arms();
            case SARACEN -> SARACEN_KIT.arms();
            case SKUTATOS -> BYZANTINE_SHIELDS;
            case BERDYSHNIK -> BERDYSH_KIT.arms();
            case ROGATINSHCHIK -> ROGATINA_KIT.arms();
            case VARANGIAN -> VARANGIAN_KIT.arms();
            case CRUSADER -> new String[]{EK + "crusader_chestplate", EK + "noble_sword"};
            case SERGEANT -> SERGEANT_KIT.shields();
            case MAMLUK -> new String[]{ADDON + "mamluk_helmet", ADDON + "steel_cavalry_sabre", EK + "steel_target"};
            case MILITIA -> LEVY_ARMS;
            case BANDIT -> BANDIT_ARMS;
            case CHIEFTAIN -> CHIEFTAIN_ARMS;
        };
        for (String id : ids) {
            Item item = item(id);
            if (item != null) {
                return new ItemStack(item);
            }
        }
        return new ItemStack(Items.BARRIER);
    }

    /** Puts on a kit: a weapon that fits, a shield when a hand is free, and the armour; Epic Knights' own for what is missing. */
    private static void wear(Soldier s, RandomSource rnd, Kit kit, Predicate<ItemStack> weaponFits) {
        ItemStack weapon = kit.arms().length == 0 ? ItemStack.EMPTY : prefer(rnd, weaponFits, kit.arms(), FALLBACK_ARMS);
        ItemStack shield = kit.shields().length == 0 ? ItemStack.EMPTY : prefer(rnd, Kits::shield, kit.shields(), FALLBACK_SHIELDS);
        hand(s, weapon, shield);
        if (kit.heads().length > 0) {
            put(s, prefer(rnd, Kits::armour, kit.heads(), FALLBACK_HEADS));
        }
        if (kit.bodies().length > 0) {
            put(s, prefer(rnd, Kits::armour, kit.bodies(), FALLBACK_BODIES));
        }
        if (kit.legs().length > 0) {
            put(s, prefer(rnd, Kits::armour, kit.legs(), FALLBACK_LEGS));
        }
        if (kit.feet().length > 0) {
            put(s, prefer(rnd, Kits::armour, kit.feet(), FALLBACK_FEET));
        }
    }

    /** One of {@code wanted} that is installed and fits, or else one of {@code fallback}. */
    private static ItemStack prefer(RandomSource rnd, Predicate<ItemStack> fits, String[] wanted, String[] fallback) {
        ItemStack stack = pick(rnd, wanted, fits);
        return stack.isEmpty() ? pick(rnd, fallback, fits) : stack;
    }

    private static void armour(Soldier s, RandomSource rnd, int g, boolean legs) {
        put(s, pick(rnd, HELMETS[g], Kits::armour));
        put(s, pick(rnd, BODIES[g], Kits::armour));
        if (legs && g > LEVY) {
            put(s, pick(rnd, LEGS[g], Kits::armour));
        }
        put(s, pick(rnd, FEET[g], Kits::armour));
    }

    /** The weapon in the main hand and, when the weapon leaves a hand free, the shield in the other. */
    private static void hand(Soldier s, ItemStack weapon, ItemStack shield) {
        s.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        WeaponStats stats = KnightCombatApi.weapon(weapon);
        boolean free = stats == null ? !(weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem) : !stats.twoHanded();
        s.setItemSlot(EquipmentSlot.OFFHAND, free ? shield : ItemStack.EMPTY);
    }

    private static void put(Soldier s, ItemStack armour) {
        if (armour.getItem() instanceof ArmorItem a) {
            s.setItemSlot(a.getEquipmentSlot(), armour);
        }
    }

    private static boolean melee(ItemStack stack) {
        return KnightCombatApi.weapon(stack) != null;
    }

    /** A weapon for one hand, leaving the other for a shield. */
    private static boolean oneHanded(ItemStack stack) {
        WeaponStats stats = KnightCombatApi.weapon(stack);
        return stats != null && !stats.twoHanded();
    }

    private static boolean shield(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem;
    }

    private static boolean armour(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }

    /** One of the items that are installed and fit, at random; empty when none is. */
    private static ItemStack pick(RandomSource rnd, String[] ids, Predicate<ItemStack> fits) {
        List<ItemStack> there = new ArrayList<>();
        for (String id : ids) {
            Item item = item(id);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                if (fits.test(stack)) {
                    there.add(stack);
                }
            }
        }
        return there.isEmpty() ? ItemStack.EMPTY : there.get(rnd.nextInt(there.size()));
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == Items.AIR ? null : item;
    }
}
