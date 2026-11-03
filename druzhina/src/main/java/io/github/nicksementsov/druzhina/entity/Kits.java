package io.github.nicksementsov.druzhina.entity;

import io.github.nicksementsov.knightcombat.api.KnightCombatApi;
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
 * Knights: Addon and Slavic Armory when they are installed, which add more swords, axes, spears, helmets and the
 * mail and scale of the Rus. Every role has its kit in three grades: levies (0), men at arms (1) and picked men (2).
 */
public final class Kits {
    public static final int LEVY = 0;
    public static final int REGULAR = 1;
    public static final int PICKED = 2;

    private static final String EK = "magistuarmory:";
    private static final String ADDON = "magistuarmoryaddon:";
    private static final String RUS = "slavicarmory:";

    // ------------------------------------------------------------------ weapons

    private static final String[][] SWORDS = {
        {EK + "iron_shortsword", EK + "iron_katzbalger", EK + "stone_shortsword"},
        {EK + "iron_shortsword", EK + "iron_katzbalger", EK + "messer_sword", ADDON + "steel_arming_sword_type_xiii", ADDON + "steel_sidesword",
            RUS + "steel_rus_sword"},
        {EK + "steel_katzbalger", EK + "steel_shortsword", EK + "noble_sword", ADDON + "steel_arming_sword_type_xv", ADDON + "steel_saxon_sword",
            RUS + "steel_rus_sword"},
    };
    private static final String[][] SPEARS = {
        {EK + "wood_pike", EK + "iron_pike"},
        {EK + "iron_pike", EK + "iron_ranseur", EK + "iron_guisarme", RUS + "steel_rogatina", ADDON + "steel_boar_spear"},
        {EK + "steel_pike", EK + "steel_ranseur", RUS + "steel_winged_rogatina", ADDON + "steel_partisan"},
    };
    private static final String[][] AXES = {
        {EK + "iron_lochaberaxe", EK + "stone_lochaberaxe"},
        {EK + "iron_lochaberaxe", EK + "iron_concavehalberd", RUS + "steel_bardiche", ADDON + "steel_daneaxe", ADDON + "steel_broadaxe"},
        {EK + "steel_lochaberaxe", EK + "steel_concavehalberd", RUS + "steel_bardiche", ADDON + "steel_daneaxe", ADDON + "steel_gallowglass_axe"},
    };
    private static final String[][] GREATSWORDS = {
        {EK + "iron_bastardsword", EK + "stone_claymore", EK + "rusted_bastardsword"},
        {EK + "iron_bastardsword", EK + "iron_claymore", EK + "iron_zweihander", ADDON + "steel_longsword", ADDON + "steel_two_handed_messer"},
        {EK + "steel_claymore", EK + "steel_zweihander", EK + "steel_flamebladedsword", ADDON + "steel_german_greatsword", ADDON + "steel_longsword"},
    };
    private static final String[] KNIGHT_SWORDS = {EK + "steel_bastardsword", EK + "noble_sword", ADDON + "steel_king_sword",
        ADDON + "steel_arming_sword_type_xv", RUS + "steel_rus_sword"};
    private static final String[] LEVY_ARMS = {EK + "pitchfork", EK + "club", EK + "wood_shortsword", EK + "stone_heavymace", EK + "wood_pike"};
    private static final String[] BANDIT_ARMS = {EK + "rusted_bastardsword", EK + "rusted_heavymace", EK + "club", EK + "barbedclub",
        EK + "stone_morgenstern", EK + "stone_shortsword"};
    private static final String[] CHIEFTAIN_ARMS = {EK + "rusted_bastardsword", EK + "iron_morgenstern", EK + "iron_heavymace",
        EK + "iron_chainmorgenstern", RUS + "steel_rus_axe"};

    private static final String[][] SHIELDS = {
        {EK + "wood_roundshield", EK + "wood_heatershield", EK + "wood_buckler"},
        {EK + "iron_heatershield", EK + "iron_kiteshield", EK + "iron_roundshield", EK + "iron_rondache"},
        {EK + "steel_heatershield", EK + "steel_kiteshield", EK + "steel_tartsche"},
    };
    private static final String[] BANDIT_SHIELDS = {EK + "wood_roundshield", EK + "wood_buckler", EK + "wood_target"};

    private static final String LONGBOW = EK + "longbow";
    private static final String CROSSBOW = EK + "heavy_crossbow";

    // ------------------------------------------------------------------ armour, by grade

    private static final String[][] HELMETS = {
        {EK + "coif", ADDON + "linen_coif", RUS + "shapka", EK + "kettlehat"},
        {EK + "kettlehat", EK + "norman_helmet", EK + "chainmail_helmet", RUS + "gnezdovo_helmet", RUS + "nikolskoe_helmet", ADDON + "cervelliere",
            ADDON + "chapel"},
        {EK + "bascinet", EK + "barbute", EK + "sallet", RUS + "shater_helmet", RUS + "raikovec_helmet", RUS + "tagancha_helmet",
            ADDON + "visored_kettlehat"},
        // knights
        {EK + "greathelm", EK + "armet", EK + "grand_bascinet", RUS + "andreevski_helmet", ADDON + "sugarloaf_helmet"},
    };
    private static final String[][] BODIES = {
        {EK + "gambeson_chestplate", RUS + "rus_gambeson", ADDON + "chained_gambeson"},
        {EK + "chainmail_chestplate", RUS + "rus_chainmail_chestplate", RUS + "lamellar_mail_chestplate", RUS + "kuyak_chestplate"},
        {EK + "brigandine_chestplate", EK + "lamellar_chestplate", RUS + "rus_scale_chestplate", ADDON + "coat_of_plates_chestplate"},
        {EK + "knight_chestplate", EK + "xivcenturyknight_chestplate", RUS + "yushman_chestplate", RUS + "bahteretz_chestplate",
            ADDON + "xiii_century_knight_chestplate"},
    };
    private static final String[][] LEGS = {
        {EK + "pantyhose"},
        {EK + "chainmail_leggings", RUS + "rus_chainmail_leggings"},
        {EK + "chainmail_leggings", RUS + "rus_chainmail_leggings", ADDON + "splint_leggings"},
        {EK + "knight_leggings", EK + "xivcenturyknight_leggings", ADDON + "xiii_century_knight_leggings"},
    };
    private static final String[][] FEET = {
        {EK + "gambeson_boots", RUS + "rus_gambeson_boots", RUS + "kaftan_boots"},
        {EK + "chainmail_boots", RUS + "rus_chainmail_boots"},
        {EK + "lamellar_boots", RUS + "rus_scale_boots", RUS + "lamellar_mail_boots", ADDON + "coat_of_plates_boots"},
        {EK + "knight_boots", EK + "xivcenturyknight_boots", RUS + "bahteretz_boots", ADDON + "xiii_century_knight_boots"},
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
                hand(s, pick(rnd, KNIGHT_SWORDS, Kits::melee), pick(rnd, SHIELDS[PICKED], Kits::shield));
                put(s, pick(rnd, HELMETS[3], Kits::armour));
                put(s, pick(rnd, BODIES[3], Kits::armour));
                put(s, pick(rnd, LEGS[3], Kits::armour));
                put(s, pick(rnd, FEET[3], Kits::armour));
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
        String[] ids = switch (role) {
            case SWORDSMAN -> SWORDS[REGULAR];
            case SPEARMAN -> SPEARS[REGULAR];
            case AXEMAN -> AXES[REGULAR];
            case WARRIOR -> GREATSWORDS[REGULAR];
            case ARCHER, BANDIT_ARCHER -> new String[]{LONGBOW};
            case CROSSBOWMAN -> new String[]{CROSSBOW};
            case KNIGHT -> KNIGHT_SWORDS;
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
