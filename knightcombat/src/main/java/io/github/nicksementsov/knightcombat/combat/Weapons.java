package io.github.nicksementsov.knightcombat.combat;

import io.github.nicksementsov.knightcombat.CombatConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns items into weapons for the combat rules. The mod has no weapons of its own: swords and axes of Minecraft,
 * the weapons of Epic Knights and its addons, and swords, axes and other weapons of other mods fight with it.
 * <p>
 * The family of a weapon comes from Epic Knights' weapon data and from its name (a "halberd" is a polearm,
 * a "mace" is blunt); its speed, damage and reach come from the item's own attributes, so the timings follow
 * the attack speed the weapon was made with.
 */
public final class Weapons {
    private static final Map<Item, Optional<WeaponStats>> CACHE = new ConcurrentHashMap<>();

    private static final String[] LANCES = {"lance"};
    private static final String[] DAGGERS = {"dagger", "stiletto", "stylet", "knife", "dirk", "kunai", "tanto", "sica",
            "shortseax", "short_seax", "small_seax", "sittingbourne_seax", "sickle", "kukri", "karambit", "misericorde", "baselard"};
    private static final String[] POLEAXES = {"halberd", "poleaxe", "pollaxe", "bardiche", "glaive", "voulge", "guisarme",
            "lochaber", "fauchard", "billhook", "bill_hook", "scythe", "lucern", "goedendag", "naginata", "hammerspear", "hammer_spear"};
    private static final String[] SPEARS = {"pike", "spear", "ranseur", "partisan", "rogatina", "doru", "dory", "sarissa",
            "ahlspiess", "fork", "trident", "yari", "standard", "assegai", "hasta", "javelin", "pilum", "angon", "sulica"};
    private static final String[] AXES = {"axe", "tabar", "hatchet", "francisca"};
    private static final String[] BLUNT = {"mace", "hammer", "club", "flail", "morgenstern", "morningstar", "morning_star",
            "eveningstar", "evening_star", "beak", "maul", "cudgel", "warpick", "war_pick", "pernach", "shestopyor", "kanabo"};
    private static final String[] THRUSTING = {"estoc", "rapier", "smallsword", "small_sword", "tuck", "koncerz"};
    /** Weapons made to be thrown: javelins and throwing axes. */
    private static final String[] THROWING = {"javelin", "pilum", "angon", "sulica", "francisca", "throwing"};
    /** A spear this much longer than an arm's reach (a sarissa, a pike) is held in both hands, whatever its mod says. */
    private static final float LONG_SPEAR_REACH = 3.0f;
    /** Names that mean a two-handed weapon when the mod does not say how the weapon is held. */
    private static final String[] GREAT = {"great", "zweihander", "claymore", "longsword", "long_sword", "bastard",
            "flamberge", "nodachi", "odachi", "two_handed", "twohanded", "executioner", "grand"};

    private Weapons() {
    }

    /** Stats of the weapon in the stack, or {@code null} when the item does not fight with the combat rules. */
    public static WeaponStats of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return CACHE.computeIfAbsent(stack.getItem(), Weapons::profile).orElse(null);
    }

    public static boolean isWeapon(ItemStack stack) {
        return of(stack) != null;
    }

    /** Whether the weapon is made to be thrown (a javelin, a throwing axe): it is thrown harder and flies faster. */
    public static boolean madeForThrowing(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && contains(id.getPath().toLowerCase(Locale.ROOT), THROWING);
    }

    /** The settings that decide which items are weapons changed. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static Optional<WeaponStats> profile(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            return Optional.empty();
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (CombatConfig.isExcluded(id) || EpicKnights.isLance(item) || contains(path, LANCES)) {
            return Optional.empty();
        }
        EpicKnights.Info epic = EpicKnights.info(item);
        ItemStack stack = new ItemStack(item);
        float damage = (float) attribute(stack, Attributes.ATTACK_DAMAGE, 1.0);
        boolean weaponItem = item instanceof SwordItem || item instanceof AxeItem || epic != null;
        // Items that are neither swords nor axes count when their name says they are a weapon and they deal damage.
        if (!weaponItem && (damage < 3 || knownName(path) == null)) {
            return Optional.empty();
        }
        boolean vanilla = id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE);
        if (vanilla ? !CombatConfig.vanillaWeapons() : !CombatConfig.moddedWeapons()) {
            return Optional.empty();
        }
        float speed = (float) attribute(stack, Attributes.ATTACK_SPEED, 4.0);
        float bonusReach = epic != null ? epic.bonusReach() : (float) attribute(stack, ForgeMod.ENTITY_REACH.get(), 0);
        WeaponKind kind = kind(item, path, epic);
        Grip grip = grip(kind, path, epic, bonusReach);
        float weight = epic != null ? epic.weight() : Mth.clamp((1.6f - speed) * 4f + 1.5f, 0.6f, 5f);
        return Optional.of(build(kind, grip, damage, speed, bonusReach, weight, epic));
    }

    private static WeaponStats build(WeaponKind kind, Grip grip, float damage, float speed, float bonusReach, float weight,
                                     EpicKnights.Info epic) {
        // The attack speed of an item is attacks per second with vanilla clicks; the three phases of an attack
        // together take a little longer than one vanilla attack.
        float cycle = 20f / Math.max(0.4f, speed);
        int windup = Mth.clamp(Math.round(cycle * 0.55f), 4, 16);
        int release = Mth.clamp(Math.round(cycle * 0.38f), 4, 9);
        int recovery = Mth.clamp(Math.round(cycle * 0.55f), 5, 16);
        float stamina = 10 + 6 * weight;
        AttackStats slash = new AttackStats(damage * kind.slash, windup, release, recovery, stamina);
        AttackStats overhead = new AttackStats(damage * kind.overhead, windup + kind.overheadWindup, release, recovery + 1, stamina * 1.15f);
        AttackStats stab = new AttackStats(damage * kind.stab, Math.max(3, windup + kind.stabWindup), Math.max(3, release - 1), recovery, stamina * 0.8f);

        float baseReach = switch (grip) {
            case ONE_HANDED -> kind == WeaponKind.DAGGER ? 2.6f : 3.0f;
            case TWO_HANDED -> 3.3f;
            case POLEARM -> 3.4f;
        };
        float reach = Math.min(6.5f, baseReach + bonusReach);

        float blockCost = kind.blockCost;
        if (epic != null && epic.maxBlockDamage() > 0) {
            // Epic Knights weapons that can parry on their own block better.
            blockCost = Math.min(blockCost, 0.95f - 0.03f * Math.min(epic.maxBlockDamage(), 10));
        }
        if (grip != Grip.ONE_HANDED) {
            blockCost -= 0.08f;
        }
        // Epic Knights adds its own armour piercing to its weapons; other blunt weapons get some here.
        float armorPiercing = epic != null ? 0 : kind == WeaponKind.BLUNT ? 0.3f : 0;

        float size = switch (grip) {
            case ONE_HANDED -> kind == WeaponKind.DAGGER ? 1.05f : 1.1f;
            case TWO_HANDED -> 1.25f;
            case POLEARM -> 1.15f;
        };
        return new WeaponStats(kind, grip, reach, slash, overhead, stab, blockCost, armorPiercing, kind.breaksShields, size);
    }

    private static WeaponKind kind(Item item, String path, EpicKnights.Info epic) {
        WeaponKind byName = knownName(path);
        if (byName == WeaponKind.DAGGER) {
            return byName;
        }
        if (epic != null && epic.halberd()) {
            return WeaponKind.POLEAXE;
        }
        if (byName != null) {
            return byName;
        }
        if (item instanceof AxeItem) {
            return WeaponKind.AXE;
        }
        if (epic != null && epic.bonusReach() >= 1.0f) {
            return WeaponKind.SPEAR;
        }
        return WeaponKind.SWORD;
    }

    /** The family a weapon name points to, or {@code null}. Checked in this order: "lucernhammer" is a polearm. */
    static WeaponKind knownName(String path) {
        if (contains(path, DAGGERS)) {
            return WeaponKind.DAGGER;
        }
        if (contains(path, POLEAXES)) {
            return WeaponKind.POLEAXE;
        }
        if (contains(path, SPEARS)) {
            return WeaponKind.SPEAR;
        }
        if (contains(path, AXES)) {
            return WeaponKind.AXE;
        }
        if (contains(path, BLUNT)) {
            return WeaponKind.BLUNT;
        }
        if (contains(path, THRUSTING)) {
            return WeaponKind.THRUSTING_SWORD;
        }
        return null;
    }

    private static Grip grip(WeaponKind kind, String path, EpicKnights.Info epic, float bonusReach) {
        if (kind == WeaponKind.DAGGER) {
            return Grip.ONE_HANDED;
        }
        if (epic != null) {
            if (epic.twoHanded() >= 1 && (bonusReach >= 0.8f || kind.polearm())) {
                return Grip.POLEARM;
            }
            if (kind.polearm()) {
                // One-handed spears of Epic Knights are thrown and thrust over the shield; a very long one is not.
                return bonusReach >= 1.0f && epic.twoHanded() >= 1 || bonusReach >= LONG_SPEAR_REACH ? Grip.POLEARM : Grip.ONE_HANDED;
            }
            return epic.twoHanded() >= 1 ? Grip.TWO_HANDED : Grip.ONE_HANDED;
        }
        if (kind.polearm()) {
            return Grip.POLEARM;
        }
        return contains(path, GREAT) ? Grip.TWO_HANDED : Grip.ONE_HANDED;
    }

    /** The base value plus every flat modifier the item adds to the attribute when held in the main hand. */
    private static double attribute(ItemStack stack, Attribute attribute, double base) {
        double value = base;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                value += modifier.getAmount();
            }
        }
        return value;
    }

    private static boolean contains(String path, String[] words) {
        for (String word : words) {
            if (path.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
