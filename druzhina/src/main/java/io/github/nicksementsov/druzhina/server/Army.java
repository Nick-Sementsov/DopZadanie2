package io.github.nicksementsov.druzhina.server;

import io.github.nicksementsov.druzhina.DruzhinaConfig;
import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Role;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A player's soldiers: hiring them and giving them orders. */
public final class Army {
    /** How far the commander's voice (and his baton) carries. */
    public static final double VOICE = 64;
    /** Soldiers stand this far apart in a line, and the ranks this far behind each other. */
    private static final double FILE = 1.4;
    private static final double RANK = 1.7;
    private static final int WIDTH = 8;

    private Army() {
    }

    /** Every soldier of the player that is in the loaded world. */
    public static List<Soldier> of(ServerPlayer player) {
        List<Soldier> out = new ArrayList<>();
        UUID id = player.getUUID();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Soldier s && s.isAlive() && id.equals(s.getOwnerUUID())) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** The player's soldiers within earshot. */
    public static List<Soldier> near(Player player) {
        return player.level().getEntitiesOfClass(Soldier.class, player.getBoundingBox().inflate(VOICE),
                s -> s.isAlive() && player.getUUID().equals(s.getOwnerUUID()) && !s.managed());
    }

    public static int price(Role role) {
        return Math.max(0, (int) Math.round(role.cost * DruzhinaConfig.PRICE.get()));
    }

    /** Hires a soldier of {@code role} at the muster table at {@code table}. Returns whether he was hired. */
    public static boolean hire(ServerPlayer player, BlockPos table, Role role) {
        ServerLevel level = player.serverLevel();
        if (!role.hired() || !level.getBlockState(table).is(DruzhinaRegistry.MUSTER_TABLE.get())
                || player.distanceToSqr(Vec3.atCenterOf(table)) > 8 * 8) {
            return false;
        }
        int count = of(player).size();
        int max = DruzhinaConfig.MAX_SOLDIERS.get();
        if (count >= max) {
            player.displayClientMessage(Component.translatable("druzhina.msg.army_full", max).withStyle(ChatFormatting.RED), false);
            return false;
        }
        int price = price(role);
        if (!player.getAbilities().instabuild) {
            if (emeralds(player) < price) {
                player.displayClientMessage(Component.translatable("druzhina.msg.no_emeralds", price).withStyle(ChatFormatting.RED), false);
                return false;
            }
            takeEmeralds(player, price);
        }

        Soldier s = DruzhinaRegistry.SOLDIER.get().create(level);
        if (s == null) {
            return false;
        }
        BlockPos at = freeSpot(level, table);
        s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180, 0);
        s.enlist(role, player.getUUID(), level.random);
        s.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null, null);
        s.order(Orders.FOLLOW, null, 0);
        level.addFreshEntity(s);
        s.spawnAnim();
        level.playSound(null, at, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.NEUTRAL, 1.0f, 0.9f);
        level.playSound(null, at, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.6f, 0.8f);
        player.displayClientMessage(Component.translatable("druzhina.msg.hired", s.getDisplayName(), count + 1, max).withStyle(ChatFormatting.GREEN), false);

        // the first soldier comes with the baton to lead him
        if (!player.getPersistentData().getBoolean("druzhina.baton") && !hasBaton(player)) {
            player.getPersistentData().putBoolean("druzhina.baton", true);
            ItemStack baton = new ItemStack(DruzhinaRegistry.BATON.get());
            if (!player.getInventory().add(baton)) {
                player.drop(baton, false);
            }
            player.displayClientMessage(Component.translatable("druzhina.msg.baton_given").withStyle(ChatFormatting.GOLD), false);
        }
        return true;
    }

    private static boolean hasBaton(Player player) {
        return player.getInventory().contains(new ItemStack(DruzhinaRegistry.BATON.get()));
    }

    public static int emeralds(Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.EMERALD)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static void takeEmeralds(Player player, int amount) {
        for (ItemStack stack : player.getInventory().items) {
            if (amount <= 0) {
                break;
            }
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(amount, stack.getCount());
                stack.shrink(take);
                amount -= take;
            }
        }
    }

    /** A free place to stand next to {@code pos}. */
    private static BlockPos freeSpot(Level level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = ground(level, pos.relative(d));
            if (p != null) {
                return p;
            }
        }
        return pos.above();
    }

    /** The place to stand at {@code pos}: on the ground a few blocks up or down from it, or null. */
    @Nullable
    public static BlockPos ground(Level level, BlockPos pos) {
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos p = pos.above(dy);
            if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
                    && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- orders

    /** «За мной!» or «В атаку!» to every soldier within earshot. */
    public static int order(ServerPlayer player, Orders orders) {
        List<Soldier> army = near(player);
        for (Soldier s : army) {
            s.order(orders, null, 0);
        }
        announce(player, orders, army.size());
        return army.size();
    }

    /**
     * «Стоять здесь!»: the soldiers form up at {@code at}, facing {@code yaw}: the men with blades in the front ranks,
     * the archers behind them, eight to a rank.
     */
    public static int formLine(ServerPlayer player, BlockPos at, float yaw) {
        List<Soldier> army = near(player);
        if (army.isEmpty()) {
            announce(player, Orders.HOLD, 0);
            return 0;
        }
        // facing: the way the commander looks, squared to the nearest eighth of a turn
        float face = Math.round(yaw / 45f) * 45f;
        double fx = -Mth.sin(face * Mth.DEG_TO_RAD);
        double fz = Mth.cos(face * Mth.DEG_TO_RAD);
        double rx = -fz;
        double rz = fx;
        List<Soldier> blades = new ArrayList<>(army.stream().filter(s -> !s.role().ranged).toList());
        List<Soldier> bows = new ArrayList<>(army.stream().filter(s -> s.role().ranged).toList());
        int rank = 0;
        rank = place(player.serverLevel(), blades, at, fx, fz, rx, rz, face, rank);
        place(player.serverLevel(), bows, at, fx, fz, rx, rz, face, rank);
        announce(player, Orders.HOLD, army.size());
        return army.size();
    }

    private static int place(ServerLevel level, List<Soldier> men, BlockPos at, double fx, double fz, double rx, double rz, float face, int rank) {
        for (int i = 0; i < men.size(); i += WIDTH, rank++) {
            List<Soldier> line = new ArrayList<>(men.subList(i, Math.min(men.size(), i + WIDTH)));
            // whoever stands further to the right takes a place further to the right: nobody crosses the line
            line.sort(Comparator.comparingDouble(s -> (s.getX() - at.getX()) * rx + (s.getZ() - at.getZ()) * rz));
            int n = line.size();
            for (int k = 0; k < n; k++) {
                double side = (k - (n - 1) / 2.0) * FILE;
                double back = rank * RANK;
                double x = at.getX() + 0.5 + rx * side - fx * back;
                double z = at.getZ() + 0.5 + rz * side - fz * back;
                BlockPos spot = BlockPos.containing(x, at.getY(), z);
                BlockPos ground = ground(level, spot);
                line.get(k).order(Orders.HOLD, new Vec3(x, (ground != null ? ground : spot).getY(), z), face);
            }
        }
        return rank;
    }

    private static void announce(ServerPlayer player, Orders orders, int men) {
        Component text = men == 0
                ? Component.translatable("druzhina.msg.nobody_hears").withStyle(ChatFormatting.GRAY)
                : Component.translatable("druzhina.cry." + orders.id(), men).withStyle(ChatFormatting.GOLD);
        player.displayClientMessage(text, true);
        if (men > 0) {
            SoundEvent sound = switch (orders) {
                case CHARGE -> SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value();
                case HOLD, PATROL -> SoundEvents.ARMOR_EQUIP_CHAIN;
                case FOLLOW -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            };
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS,
                    orders == Orders.CHARGE ? 2.5f : 1.0f, orders == Orders.FOLLOW ? 0.7f : 1.0f);
        }
    }
}
