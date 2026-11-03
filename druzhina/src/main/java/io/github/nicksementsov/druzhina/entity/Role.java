package io.github.nicksementsov.druzhina.entity;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What a soldier is: his weapon and armour, his health and how well he fights. Hired roles serve a player; the
 * bandits roam the land on their own.
 */
public enum Role {
    //            emeralds  health  skill   ranged  bandit
    SWORDSMAN(8, 24, 0.5f, false, false),
    SPEARMAN(8, 24, 0.5f, false, false),
    AXEMAN(8, 26, 0.45f, false, false),
    ARCHER(10, 20, 0.45f, true, false),
    KNIGHT(28, 32, 0.75f, false, false),
    BANDIT(0, 18, 0.25f, false, true),
    BANDIT_ARCHER(0, 16, 0.25f, true, true),
    CHIEFTAIN(0, 30, 0.5f, false, true);

    /** Price of hiring one, in emeralds. */
    public final int cost;
    public final float health;
    /** 0 to 1: how quickly and how often he guards, parries, chains and feints. */
    public final float skill;
    public final boolean ranged;
    public final boolean bandit;

    Role(int cost, float health, float skill, boolean ranged, boolean bandit) {
        this.cost = cost;
        this.health = health;
        this.skill = skill;
        this.ranged = ranged;
        this.bandit = bandit;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static Role byId(int ordinal) {
        Role[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SWORDSMAN;
    }

    public static Role byName(String id) {
        for (Role r : values()) {
            if (r.id().equals(id)) {
                return r;
            }
        }

        return null;
    }

    /** The roles a player may hire. */
    public static Role[] forHire() {
        return new Role[]{SWORDSMAN, SPEARMAN, AXEMAN, ARCHER, KNIGHT};
    }

    /** Whether a player may hire one. */
    public boolean hired() {
        return !this.bandit;
    }

    /** What he fights with, to show him by. */
    public ItemStack icon() {
        return switch (this) {
            case SWORDSMAN -> pick(null, "magistuarmory:iron_arming_sword", "minecraft:iron_sword");
            case SPEARMAN -> pick(null, "magistuarmory:iron_pike", "druzhina:spear");
            case AXEMAN, CHIEFTAIN -> pick(null, "magistuarmory:iron_bearded_axe", "minecraft:iron_axe");
            case ARCHER, BANDIT_ARCHER -> new ItemStack(Items.BOW);
            case KNIGHT -> pick(null, "magistuarmory:steel_longsword", "minecraft:diamond_sword");
            case BANDIT -> new ItemStack(Items.STONE_AXE);
        };
    }

    /** Arms and armour of a new soldier of this role. Weapons of Epic Knights are taken when the mod is there. */
    public void equip(Soldier s, RandomSource rnd) {
        switch (this) {
            case SWORDSMAN -> {
                s.setItemSlot(EquipmentSlot.MAINHAND, pick(rnd, "magistuarmory:iron_arming_sword", "minecraft:iron_sword"));
                s.setItemSlot(EquipmentSlot.OFFHAND, pick(rnd, "magistuarmory:heater_shield", "minecraft:shield"));
                s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                s.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                s.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                s.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            }
            case SPEARMAN -> {
                s.setItemSlot(EquipmentSlot.MAINHAND, pick(rnd, "magistuarmory:iron_pike", "druzhina:spear"));
                s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                s.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                s.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                s.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            }
            case AXEMAN -> {
                s.setItemSlot(EquipmentSlot.MAINHAND, pick(rnd, "magistuarmory:iron_bearded_axe", "minecraft:iron_axe"));
                s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                s.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                s.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                s.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            }
            case ARCHER -> {
                s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                s.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                s.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            }
            case KNIGHT -> {
                s.setItemSlot(EquipmentSlot.MAINHAND, pick(rnd, "magistuarmory:steel_longsword", "minecraft:diamond_sword"));
                s.setItemSlot(EquipmentSlot.OFFHAND, pick(rnd, "magistuarmory:kite_shield", "minecraft:shield"));
                s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                s.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                s.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                s.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            }
            case BANDIT -> {
                s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(rnd.nextBoolean() ? Items.STONE_AXE : rnd.nextInt(4) == 0 ? Items.IRON_SWORD : Items.STONE_SWORD));
                s.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                if (rnd.nextInt(3) == 0) {
                    s.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                }
            }
            case BANDIT_ARCHER -> {
                s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                s.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            }
            case CHIEFTAIN -> {
                s.setItemSlot(EquipmentSlot.MAINHAND, pick(rnd, "magistuarmory:iron_bearded_axe", "minecraft:iron_axe"));
                s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                s.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                s.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                s.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            }
        }
    }

    /** The first item that exists: a weapon of Epic Knights when it is installed, else the plain one. */
    private static ItemStack pick(RandomSource rnd, String... ids) {
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != Items.AIR && ForgeRegistries.ITEMS.containsKey(rl)) {
                return new ItemStack(item);
            }
        }

        return ItemStack.EMPTY;
    }
}
