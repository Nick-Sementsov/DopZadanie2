package io.github.nicksementsov.druzhina.entity;

import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** The horses of the riders and the elephants of the drivers, given with them when they are hired. */
public final class Mounts {
    private Mounts() {
    }

    /**
     * Puts a new soldier on the beast his role rides (nothing for men on foot), already in the world beside him. For an
     * elephant, an archer is raised for its howdah: he is returned with the others raised (the crew), so that he can be
     * counted and given orders.
     */
    public static List<Soldier> mount(ServerLevel level, Soldier s, RandomSource rnd) {
        List<Soldier> crew = new ArrayList<>();
        switch (s.role().mount) {
            case HORSE -> {
                Horse horse = horse(level, s, rnd);
                if (horse != null) {
                    s.startRiding(horse, true);
                    s.setMount(horse.getUUID());
                }
            }
            case ELEPHANT -> {
                WarElephant beast = DruzhinaRegistry.WAR_ELEPHANT.get().create(level);
                if (beast == null) {
                    return crew;
                }
                Vec3 at = spot(level, DruzhinaRegistry.WAR_ELEPHANT.get(), s.position());
                beast.moveTo(at.x, at.y, at.z, s.getYRot(), 0);
                beast.setOwnerUUID(s.getOwnerUUID());
                beast.setPersistenceRequired();
                beast.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null, null);
                level.addFreshEntity(beast);
                s.startRiding(beast, true);
                s.setMount(beast.getUUID());
                Soldier archer = DruzhinaRegistry.SOLDIER.get().create(level);
                if (archer != null) {
                    archer.moveTo(at.x, at.y, at.z, s.getYRot(), 0);
                    archer.enlist(Role.ARCHER, s.getOwnerUUID(), Kits.REGULAR, rnd);
                    archer.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null, null);
                    archer.order(s.orders(), s.post(), s.postYaw());
                    level.addFreshEntity(archer);
                    archer.startRiding(beast, true);
                    archer.setMount(beast.getUUID());
                    crew.add(archer);
                }
            }
            default -> {
            }
        }
        return crew;
    }

    @Nullable
    private static Horse horse(ServerLevel level, Soldier s, RandomSource rnd) {
        Horse horse = EntityType.HORSE.create(level);
        if (horse == null) {
            return null;
        }
        Vec3 at = spot(level, EntityType.HORSE, s.position());
        horse.moveTo(at.x, at.y, at.z, s.getYRot(), 0);
        horse.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null, null);
        horse.setTamed(true);
        horse.setOwnerUUID(s.getOwnerUUID());
        horse.setPersistenceRequired();
        horse.equipSaddle(null);
        // a war horse: strong, quick, not afraid
        boolean heavy = s.role() == Role.LANCER;
        set(horse, Attributes.MAX_HEALTH, heavy ? 30 + rnd.nextInt(5) : 24 + rnd.nextInt(5));
        set(horse, Attributes.MOVEMENT_SPEED, (heavy ? 0.25 : 0.27) + rnd.nextDouble() * 0.03);
        set(horse, Attributes.JUMP_STRENGTH, 0.7 + rnd.nextDouble() * 0.1);
        horse.setHealth(horse.getMaxHealth());
        ItemStack barding = barding(s.role());
        if (!barding.isEmpty() && horse.isArmor(barding)) {
            horse.getSlot(401).set(barding);
        }
        level.addFreshEntity(horse);
        return horse;
    }

    private static void set(Horse horse, net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        var a = horse.getAttribute(attribute);
        if (a != null) {
            a.setBaseValue(value);
        }
    }

    /** The armour of a rider's horse: Epic Knights' barding and mail when installed, else Minecraft's. */
    private static ItemStack barding(Role role) {
        return switch (role) {
            case LANCER -> first("magistuarmory:barding", "minecraft:diamond_horse_armor");
            case HORSEMAN -> first("magistuarmory:chainmail_horse_armor", "minecraft:iron_horse_armor");
            case HORSE_ARCHER -> new ItemStack(Items.LEATHER_HORSE_ARMOR);
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack first(String... ids) {
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** A place near {@code near} where a beast of {@code type} fits. */
    public static Vec3 spot(ServerLevel level, EntityType<?> type, Vec3 near) {
        for (int r = 0; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = 1; dy >= -2; dy--) {
                        Vec3 at = new Vec3(Math.floor(near.x) + dx + 0.5, Math.floor(near.y) + dy, Math.floor(near.z) + dz + 0.5);
                        BlockPos below = BlockPos.containing(at).below();
                        if (level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
                                && level.noCollision(type.getAABB(at.x, at.y, at.z))) {
                            return at;
                        }
                    }
                }
            }
        }
        return near;
    }
}
