package io.github.nicksementsov.druzhina.server;

import io.github.nicksementsov.druzhina.DruzhinaConfig;
import io.github.nicksementsov.druzhina.api.Hiring;
import io.github.nicksementsov.druzhina.compat.Vessels;
import io.github.nicksementsov.druzhina.entity.Formation;
import io.github.nicksementsov.druzhina.entity.March;
import io.github.nicksementsov.druzhina.entity.Mounts;
import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Role;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import net.minecraft.world.phys.Vec3;

/** A player's soldiers: hiring them and giving them orders. */
public final class Army {
    /** How far the commander's voice (and his baton) carries. */
    public static final double VOICE = 64;

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
        Component refusal = Hiring.refusal(player, table);
        if (refusal != null) {
            player.displayClientMessage(refusal, false);
            return false;
        }
        int count = of(player).size();
        int max = DruzhinaConfig.MAX_SOLDIERS.get();
        if (count + role.crew() > max) {
            player.displayClientMessage(Component.translatable("druzhina.msg.army_full", max).withStyle(ChatFormatting.RED), false);
            return false;
        }
        int price = price(role);
        if (!player.getAbilities().instabuild) {
            if (gold(player) < price) {
                player.displayClientMessage(Component.translatable("druzhina.msg.no_gold", price).withStyle(ChatFormatting.RED), false);
                return false;
            }
            takeGold(player, price);
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
        // riders come with their horses, drivers with their elephants (and the archer for its howdah)
        int men = 1 + Mounts.mount(level, s, level.random).size();
        s.spawnAnim();
        level.playSound(null, at, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.NEUTRAL, 1.0f, 0.9f);
        level.playSound(null, at, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.6f, 0.8f);
        player.displayClientMessage(Component.translatable("druzhina.msg.hired", s.getDisplayName(), count + men, max).withStyle(ChatFormatting.GREEN), false);

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

    /** Soldiers are paid in gold ingots (Minecraft's, or any mod's that count as gold ingots). */
    public static boolean isGold(ItemStack stack) {
        return stack.is(Tags.Items.INGOTS_GOLD);
    }

    /** How many gold ingots the player carries. */
    public static int gold(Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (isGold(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static void takeGold(Player player, int amount) {
        for (ItemStack stack : player.getInventory().items) {
            if (amount <= 0) {
                break;
            }
            if (isGold(stack)) {
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
        form(player.serverLevel(), army, Vec3.atBottomCenterOf(at), face, 0, Formation.LINE);
        announce(player, Orders.HOLD, army.size());
        return army.size();
    }

    /**
     * Sends {@code men} to stand in {@code formation} at {@code center} (the middle of the front), facing {@code yaw}
     * (see {@link #assign}).
     */
    public static void form(ServerLevel level, List<Soldier> men, Vec3 center, float yaw, double width, Formation formation) {
        List<Soldier> walking = new ArrayList<>();
        for (Soldier s : men) {
            if (!sendCrew(s, center, yaw)) {
                walking.add(s);
            }
        }
        assign(level, walking, center, yaw, width, formation).forEach((s, p) -> s.order(Orders.HOLD, p, yaw));
    }

    /**
     * A man who does not walk where he is sent: at a ship's helm he sails her there, in a howdah or aboard a ship he
     * goes where she goes. Returns whether he was such a man (and has had his orders).
     */
    public static boolean sendCrew(Soldier s, Vec3 center, float yaw) {
        if (Vessels.isHelmsman(s.getVehicle(), s)) {
            s.order(Orders.HOLD, center, yaw);
            return true;
        }
        if (s.isCrew()) {
            s.order(Orders.HOLD, null, yaw);
            return true;
        }
        return false;
    }

    /**
     * Each man's place in {@code formation} at {@code center} (the middle of the front), facing {@code yaw}: the men
     * with blades in front, the archers and crossbowmen behind them, the riders in ranks of their own at the back; and
     * in every rank whoever stands further to the right takes a place further to the right, so that nobody crosses the
     * others on the way. With a level, the places are put on the ground.
     */
    public static Map<Soldier, Vec3> assign(@Nullable ServerLevel level, List<Soldier> men, Vec3 center, float yaw, double width, Formation formation) {
        Map<Soldier, Vec3> out = new LinkedHashMap<>();
        if (men.isEmpty()) {
            return out;
        }
        List<Soldier> foot = new ArrayList<>();
        List<Soldier> horse = new ArrayList<>();
        for (Soldier s : men) {
            (s.steed() != null ? horse : foot).add(s);
        }
        Comparator<Soldier> blades = Comparator.comparingInt((Soldier s) -> s.shooter() ? 1 : 0);
        foot.sort(blades);
        horse.sort(blades);
        List<Soldier> order = new ArrayList<>(foot);
        order.addAll(horse);
        List<Vec3> slots = formation.slots(foot.size(), horse.size(), center, yaw, width);
        double rx = -Mth.cos(yaw * Mth.DEG_TO_RAD);
        double rz = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        Comparator<Vec3> across = Comparator.comparingDouble(v -> (v.x - center.x) * rx + (v.z - center.z) * rz);
        int i = 0;
        for (int n : formation.ranks(foot.size(), horse.size(), width)) {
            List<Soldier> rankMen = new ArrayList<>(order.subList(i, i + n));
            List<Vec3> rankSlots = new ArrayList<>(slots.subList(i, i + n));
            rankMen.sort(Comparator.comparing(Soldier::position, across));
            rankSlots.sort(across);
            for (int k = 0; k < n; k++) {
                Vec3 v = rankSlots.get(k);
                double y = center.y;
                if (level != null) {
                    BlockPos spot = BlockPos.containing(v.x, center.y, v.z);
                    BlockPos ground = ground(level, spot);
                    y = (ground != null ? ground : spot).getY();
                }
                out.put(rankMen.get(k), new Vec3(v.x, y, v.z));
            }
            i += n;
        }
        return out;
    }

    // ---------------------------------------------------------------- the commander's view

    /** Orders given from the commander's view (see the client's command screen). */
    public enum Command {
        /** Go there (on a march, in formation) and stand in formation. */
        MOVE,
        /** Fall upon that one (or go aboard that ship). */
        ATTACK,
        FOLLOW,
        /** Stop and stand where you are. */
        HOLD,
        CHARGE,
        /** Walk back and forth between where you are and there. */
        PATROL,
        /** Keep your places in the ranks (target 1) or fight freely (target 0). */
        STANCE;

        public static Command byId(int ordinal) {
            Command[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : HOLD;
        }
    }

    /** Order flags: after the orders already given (Shift), and falling upon the enemies met on the way. */
    public static final int QUEUE = 1;
    public static final int AGGRESSIVE = 2;

    /** How far from the commander his men still hear him in his commander's view. */
    public static final double FIELD = 160;

    /** Carries out an order of the commander's view for the men picked out by their entity ids. */
    public static void command(ServerPlayer player, int[] ids, Command command, Vec3 at, float yaw, double width, Formation formation, int target,
                               int flags) {
        ServerLevel level = player.serverLevel();
        boolean queue = (flags & QUEUE) != 0;
        boolean aggressive = (flags & AGGRESSIVE) != 0;
        List<Soldier> men = new ArrayList<>();
        for (int id : ids) {
            if (level.getEntity(id) instanceof Soldier s && s.isAlive() && player.getUUID().equals(s.getOwnerUUID()) && !s.managed()
                    && s.distanceToSqr(player) < FIELD * FIELD && !men.contains(s)) {
                men.add(s);
            }
        }
        if (men.isEmpty()) {
            return;
        }
        String said = command.name().toLowerCase(java.util.Locale.ROOT);
        switch (command) {
            case MOVE -> {
                if (at.distanceToSqr(player.position()) > FIELD * FIELD) {
                    return;
                }
                March.Leg leg = new March.Leg(at, yaw, Math.min(width, 64), formation, aggressive);
                March shared = queue ? shared(men) : null;
                if (shared != null && shared.members().size() == men.size()) {
                    shared.queue(leg);
                } else {
                    List<March.Leg> legs = shared != null ? shared.legsLeft() : new ArrayList<>();
                    legs.add(leg);
                    March.start(level, player, men, legs);
                }
                said = queue ? "queued" : aggressive ? "attack_move" : "move";
            }
            case ATTACK -> {
                Entity picked = Vessels.resolve(level.getEntity(target));
                if (Vessels.isVessel(picked)) {
                    // a ship: they go aboard her
                    sendAboard(player, men, picked);
                    March.echoNone(player, men, List.of());
                    return;
                }
                if (!(picked instanceof LivingEntity foe) || !foe.isAlive()) {
                    return;
                }
                for (Soldier s : men) {
                    if (s.canAttack(foe)) {
                        if (!s.isCrew()) {
                            // they fall upon him, and then stand where the fight ends
                            s.order(Orders.HOLD, s.position(), s.getYRot());
                        }
                        s.hunt(foe);
                    }
                }
                March.echoNone(player, men, List.of());
            }
            case FOLLOW -> {
                men.forEach(s -> s.follow(null));
                March.echoNone(player, men, List.of());
            }
            case HOLD -> {
                men.forEach(s -> s.order(Orders.HOLD, s.position(), s.getYRot()));
                March.echoNone(player, men, List.of());
            }
            case CHARGE -> {
                men.forEach(s -> s.order(Orders.CHARGE, null, 0));
                March.echoNone(player, men, List.of());
            }
            case PATROL -> {
                if (at.distanceToSqr(player.position()) > FIELD * FIELD) {
                    return;
                }
                patrol(level, men, at, queue);
                March.echoNone(player, men, List.of(at));
            }
            case STANCE -> {
                men.forEach(s -> s.setKeepRanks(target != 0));
                said = target != 0 ? "keep_ranks" : "fight_freely";
            }
        }
        player.displayClientMessage(Component.translatable("druzhina.command." + said, men.size()).withStyle(ChatFormatting.GOLD), true);
        SoundEvent sound = switch (command) {
            case ATTACK, CHARGE -> SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value();
            case FOLLOW -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            case MOVE -> aggressive ? SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value() : SoundEvents.ARMOR_EQUIP_CHAIN;
            default -> SoundEvents.ARMOR_EQUIP_CHAIN;
        };
        boolean loud = command == Command.ATTACK || command == Command.CHARGE;
        player.playNotifySound(sound, SoundSource.PLAYERS, loud ? 1.2f : queue ? 0.5f : 0.8f, 1.0f);
    }

    /** The march all of {@code men} are on, or null when they are not all on one. */
    @Nullable
    private static March shared(List<Soldier> men) {
        March m = men.get(0).march();
        if (m == null) {
            return null;
        }
        for (Soldier s : men) {
            if (s.march() != m) {
                return null;
            }
        }
        return m;
    }

    /**
     * A patrol: every man walks between where he is and the same place shifted to {@code at} (the group keeps its
     * shape). Queued, the round goes on from its last place to {@code at}.
     */
    private static void patrol(ServerLevel level, List<Soldier> men, Vec3 at, boolean queue) {
        List<Soldier> walking = new ArrayList<>();
        for (Soldier s : men) {
            if (!sendCrew(s, at, s.getYRot())) {
                walking.add(s);
            }
        }
        if (walking.isEmpty()) {
            return;
        }
        boolean extend = queue && walking.stream().allMatch(s -> s.orders() == Orders.PATROL && !s.patrol().isEmpty());
        double mx = 0;
        double mz = 0;
        for (Soldier s : walking) {
            Vec3 from = extend ? s.patrol().get(s.patrol().size() - 1) : s.position();
            mx += from.x / walking.size();
            mz += from.z / walking.size();
        }
        for (Soldier s : walking) {
            List<Vec3> points = new ArrayList<>();
            Vec3 from = s.position();
            if (extend) {
                points.addAll(s.patrol());
                from = points.get(points.size() - 1);
            } else {
                points.add(from);
            }
            double x = from.x + at.x - mx;
            double z = from.z + at.z - mz;
            BlockPos spot = BlockPos.containing(x, at.y, z);
            BlockPos ground = ground(level, spot);
            points.add(new Vec3(x, (ground != null ? ground : spot).getY(), z));
            s.patrol(points);
        }
    }

    // ---------------------------------------------------------------- ships

    /** The baton on a ship: the commander's men within earshot (not those aboard some other vessel) go aboard her. */
    public static int board(ServerPlayer player, Entity ship) {
        List<Soldier> men = new ArrayList<>();
        for (Soldier s : near(player)) {
            if (!s.isPassenger() || s.steed() != null && !(s.steed() instanceof io.github.nicksementsov.druzhina.entity.WarElephant)) {
                men.add(s);
            }
        }
        sendAboard(player, men, ship);
        return men.size();
    }

    private static void sendAboard(ServerPlayer player, List<Soldier> men, Entity ship) {
        Vessels.allowSoldiersAtHelm();
        int n = 0;
        for (Soldier s : men) {
            if (s.getVehicle() != ship && !s.isCrew() && !(s.steed() instanceof io.github.nicksementsov.druzhina.entity.WarElephant)) {
                s.sendAboard(ship);
                n++;
            }
        }
        player.displayClientMessage(n == 0 ? Component.translatable("druzhina.msg.nobody_hears").withStyle(ChatFormatting.GRAY)
                : Component.translatable("druzhina.msg.boarding", n).withStyle(ChatFormatting.GOLD), true);
        if (n > 0) {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    /**
     * Aboard a ship that one of his men steers, the baton gives the course: she sails to where the commander looks
     * (the water or the shore he looks at, or far ahead that way). Returns whether there was such a helmsman.
     */
    public static boolean course(ServerPlayer player) {
        Entity ship = Vessels.vesselOf(player);
        Soldier captain = Vessels.captainOf(ship);
        if (ship == null || captain == null || !player.getUUID().equals(captain.getOwnerUUID())) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(160));
        var hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.ANY, player));
        Vec3 to;
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            to = hit.getLocation();
        } else {
            Vec3 flat = new Vec3(look.x, 0, look.z);
            to = flat.lengthSqr() < 1.0E-4 ? ship.position() : ship.position().add(flat.normalize().scale(96));
        }
        captain.order(Orders.HOLD, new Vec3(to.x, ship.getY(), to.z), player.getYRot());
        int dist = (int) Math.round(Math.sqrt(to.distanceToSqr(ship.getX(), to.y, ship.getZ())));
        player.displayClientMessage(Component.translatable("druzhina.msg.course", captain.plainName(), dist).withStyle(ChatFormatting.GOLD), true);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 0.8f, 0.9f);
        return true;
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
