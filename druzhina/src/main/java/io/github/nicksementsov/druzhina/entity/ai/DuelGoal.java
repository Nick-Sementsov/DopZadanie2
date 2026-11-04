package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.knightcombat.api.KnightCombatApi;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.network.CombatAction;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;

/**
 * A soldier's sword fight. He presses the same keys a player would: he winds up slashes, overheads and thrusts, holds
 * them for heavy blows, chains combos, feints against a raised guard and kicks a guard that stays up too long. When
 * the enemy winds up he reads the blow and raises his guard, the better he is the closer to the blow (a parry), and
 * answers a parried blow with a riposte. Tired, he steps back behind his shield and cries to get his breath back.
 * The skill of the soldier decides how quickly and how often he gets it right.
 */
public class DuelGoal extends Goal {
    private static final int PATH_DELAY = 8;

    private final Soldier soldier;
    @Nullable
    private LivingEntity target;
    private int pathDelay;
    /** Ticks until the next attack is thought of; zero when ready. */
    private int attackDelay;
    /** Strafing: -1 left, 1 right, 0 none, and for how long more. */
    private int strafe;
    private int strafeTicks;

    // reading the enemy's blow
    /** Ticks of the enemy wind-up last seen, to know a new wind-up from the one being read. */
    private int foeTicks = -1;
    private Phase foePhase = Phase.IDLE;
    /** How many ticks before the enemy's blow lands the guard goes up; negative: this blow is not guarded. */
    private int guardLead = -1;
    /** The enemy's blow is to be met with the same blow (a counter) instead of a guard. */
    private boolean counter;
    /** The guard was raised for a blow, and lowered again after it. */
    private boolean guardUp;
    /** How long the enemy has kept his guard up. */
    private int foeGuardTicks;

    // own attack
    /** The attack being wound up is held for a heavy blow. */
    private boolean heavy;
    /** A combo blow follows the current one. */
    private boolean comboNext;
    private int feintChecked;
    /** Ticks left of a riposte to make after the enemy's blow was caught. */
    private int riposteIn = -1;

    public DuelGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = this.soldier.getTarget();
        return t != null && t.isAlive() && this.weapon() != null && this.soldier.canAttack(t) && this.soldier.withinReach(t)
                && !this.soldier.routed() && this.canFightFromWhereHeIs();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = this.soldier.getTarget();
        if (t == null || !t.isAlive() || this.weapon() == null || !this.soldier.canAttack(t) || this.soldier.routed()
                || !this.canFightFromWhereHeIs()) {
            return false;
        }
        if (!this.soldier.withinReach(t)) {
            // too far from his post or his commander: he lets this one go
            this.soldier.setTarget(null);
            return false;
        }
        return true;
    }

    /**
     * On foot or on a horse with a blade; not in an elephant's howdah, not as its driver, and not with a lance on a
     * horse (that is a charge: {@link LanceChargeGoal}).
     */
    private boolean canFightFromWhereHeIs() {
        if (!this.soldier.isPassenger()) {
            return true;
        }
        if (io.github.nicksementsov.druzhina.compat.Vessels.vesselOf(this.soldier) != null) {
            // in a boat or aboard a ship: he strikes at whoever comes within reach of his seat
            return true;
        }
        return this.soldier.steed() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse && !this.soldier.lancer();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.target = this.soldier.getTarget();
        this.pathDelay = 0;
        this.attackDelay = 5 + this.soldier.getRandom().nextInt(10);
        this.strafe = 0;
        this.strafeTicks = 0;
        this.foeTicks = -1;
        this.guardLead = -1;
        this.guardUp = false;
        this.heavy = false;
        this.comboNext = false;
        this.riposteIn = -1;
        this.soldier.setAggressive(true);
    }

    @Override
    public void stop() {
        this.lowerGuard();
        CombatState me = KnightCombatApi.state(this.soldier);
        if (me.phase() == Phase.WINDUP) {
            KnightCombatApi.input(this.soldier, CombatAction.ATTACK_RELEASE);
        }
        this.soldier.setSprinting(false);
        this.soldier.setAggressive(false);
        this.soldier.getNavigation().stop();
        this.target = null;
    }

    @Nullable
    private WeaponStats weapon() {
        return KnightCombatApi.weapon(this.soldier.getMainHandItem());
    }

    private boolean hasShield() {
        WeaponStats weapon = this.weapon();
        return this.soldier.getOffhandItem().getItem() instanceof ShieldItem && (weapon == null || !weapon.twoHanded());
    }

    @Override
    public void tick() {
        LivingEntity t = this.soldier.getTarget();
        WeaponStats weapon = this.weapon();
        if (t == null || weapon == null) {
            return;
        }
        if (t != this.target) {
            this.target = t;
            this.foeTicks = -1;
            this.guardLead = -1;
        }

        RandomSource rnd = this.soldier.getRandom();
        float skill = this.soldier.skill();
        CombatState me = KnightCombatApi.state(this.soldier);
        boolean fighter = KnightCombatApi.isFighter(t);
        CombatState foe = fighter ? KnightCombatApi.state(t) : null;
        double gap = Math.sqrt(this.soldier.distanceToSqr(t)) - t.getBbWidth() / 2;
        float reach = weapon.reach();
        boolean sees = this.soldier.getSensing().hasLineOfSight(t);
        float stamina = me.stamina() / KnightCombatApi.maxStamina();

        this.face(t);

        // ------------------------------------------------------------ feet
        float ideal = Math.max(1.2f, reach * 0.72f);
        boolean winded = stamina < 0.22f && me.phase() != Phase.WINDUP && me.phase() != Phase.RELEASE;
        if (winded) {
            // out of breath: keep away from the blade and get the wind back
            ideal = reach + 1.6f;
        }
        net.minecraft.world.entity.Mob horse = this.soldier.steed();
        if (horse != null) {
            this.ride(horse, t, gap, reach, rnd);
        } else if (this.soldier.isPassenger()) {
            // seated: no footwork
        } else if (gap > reach + 2.5 || !sees) {
            this.soldier.setSprinting(gap > 6 && me.phase() == Phase.IDLE);
            if (--this.pathDelay <= 0) {
                this.pathDelay = PATH_DELAY + rnd.nextInt(5);
                this.soldier.getNavigation().moveTo(t, gap > 6 ? 1.15 : 1.0);
            }
        } else {
            this.soldier.setSprinting(false);
            this.soldier.getNavigation().stop();
            float forward = gap > ideal + 0.35 ? 1.0f : gap < ideal - 0.5 ? -0.7f : 0;
            if (--this.strafeTicks <= 0) {
                this.strafeTicks = 15 + rnd.nextInt(40);
                // the better he is the more he circles
                this.strafe = rnd.nextFloat() < 0.25f + 0.5f * skill ? rnd.nextBoolean() ? 1 : -1 : 0;
            }
            float side = this.strafe * (me.phase() == Phase.IDLE || me.phase() == Phase.BLOCK ? 0.45f : 0.2f);
            if (this.soldier.horizontalCollision && this.strafe != 0 && rnd.nextInt(8) == 0) {
                this.strafe = -this.strafe;
            }
            this.soldier.getMoveControl().strafe(forward, side);
        }

        // ------------------------------------------------------------ the enemy's blow
        boolean threat = this.hasShield() && (me.phase() == Phase.IDLE || me.phase() == Phase.BLOCK) && this.incomingArrow();
        if (foe != null) {
            this.readFoe(t, foe, me, gap, skill, rnd);
            threat |= this.guardLead >= 0 && foe.phase() == Phase.WINDUP && foe.phaseLength() - foe.phaseTicks() <= this.guardLead
                    || this.guardUp && foe.phase() == Phase.RELEASE;
            if (foe.guarding()) {
                this.foeGuardTicks++;
            } else {
                this.foeGuardTicks = 0;
            }
        } else if (this.hasShield() && t instanceof RangedAttackMob) {
            // an archer: behind the shield all the way to him
            threat = me.phase() == Phase.IDLE || me.phase() == Phase.BLOCK;
        } else if (this.hasShield() && gap < 3.5 && this.attackDelay > 2 && me.phase() == Phase.IDLE && rnd.nextFloat() < 0.5f + skill) {
            // a beast or a monster: the shield goes up between blows
            threat = true;
        }

        if (this.counter && foe != null && foe.phase() == Phase.WINDUP && me.phase() == Phase.IDLE
                && foe.phaseLength() - foe.phaseTicks() <= 3) {
            // the same blow, thrown into his: it meets his and throws it back
            this.counter = false;
            this.lowerGuard();
            this.attack(foe.attack(), false);
            return;
        }

        if (threat) {
            if (me.phase() == Phase.WINDUP && foe != null) {
                int mine = me.phaseLength() - me.phaseTicks();
                int his = foe.phaseLength() - foe.phaseTicks();
                // his blow lands first: give up the own attack for the guard, if he is quick enough to see it
                if (mine >= his && rnd.nextFloat() < skill) {
                    this.raiseGuard(me);
                }
            } else {
                this.raiseGuard(me);
            }
        } else if (this.guardUp || this.guarding(me)) {
            boolean stay = winded && this.hasShield() && gap < reach + 1;
            if (!stay && (foe == null || foe.phase() != Phase.WINDUP && foe.phase() != Phase.RELEASE)) {
                boolean caught = foe != null && foe.phase() == Phase.RECOVERY && (foe.flags() & CombatState.REBOUND) != 0;
                this.lowerGuard();
                if (caught && this.riposteIn < 0 && rnd.nextFloat() < 0.35f + 0.6f * skill) {
                    // his blow was caught: strike back while he reels
                    this.riposteIn = rnd.nextInt(Math.max(1, Math.round(5 * (1 - skill))) + 1);
                }
            }
        }

        // ------------------------------------------------------------ own blows
        this.tickAttack(t, foe, me, weapon, gap, reach, skill, stamina, winded, rnd);
    }

    /**
     * A rider's feet are his horse's: he rides up to the enemy and, close enough to strike, turns the horse to have him
     * on his sword side.
     */
    private void ride(net.minecraft.world.entity.Mob horse, LivingEntity t, double gap, float reach, RandomSource rnd) {
        if (gap > reach - 0.4) {
            if (--this.pathDelay <= 0) {
                this.pathDelay = PATH_DELAY / 2 + rnd.nextInt(3);
                this.soldier.getNavigation().moveTo(t, gap > 6 ? 1.2 : 0.8);
            }
        } else {
            this.soldier.getNavigation().stop();
            float toward = (float) (Mth.atan2(t.getZ() - horse.getZ(), t.getX() - horse.getX()) * Mth.RAD_TO_DEG) - 90;
            // the enemy a little to the right, where the sword is
            float yaw = Mth.approachDegrees(horse.getYRot(), toward - 30, 8);
            horse.setYRot(yaw);
            horse.yBodyRot = yaw;
            horse.setYHeadRot(yaw);
        }
    }

    /** Sees a new wind-up of the enemy and decides how to meet it. */
    private void readFoe(LivingEntity t, CombatState foe, CombatState me, double gap, float skill, RandomSource rnd) {
        boolean windup = foe.phase() == Phase.WINDUP;
        boolean fresh = windup && (this.foePhase != Phase.WINDUP || foe.phaseTicks() < this.foeTicks);
        this.foePhase = foe.phase();
        this.foeTicks = windup ? foe.phaseTicks() : -1;
        if (!windup) {
            if (foe.phase() != Phase.RELEASE) {
                this.guardLead = -1;
                this.counter = false;
            }
            return;
        }
        if (!fresh) {
            return;
        }
        WeaponStats his = KnightCombatApi.weapon(t.getMainHandItem());
        float hisReach = his != null ? his.reach() : 3;
        boolean facing = this.facesMe(t);
        this.counter = false;
        if (!facing || gap > hisReach + 1.5) {
            this.guardLead = -1;
            return;
        }
        if (rnd.nextFloat() > 0.3f + 0.65f * skill) {
            // he does not see it coming
            this.guardLead = -1;
            return;
        }
        // a good fighter raises the guard right at the blow (a parry), a poor one too early or too late
        float spread = 6 * (1 - skill);
        this.guardLead = Mth.clamp(Math.round(3 + (rnd.nextFloat() * 2 - 1) * spread + (rnd.nextFloat() < 0.5f ? 0 : spread * 0.5f)), 0, 14);
        if (me.phase() == Phase.IDLE && (foe.flags() & (CombatState.RIPOSTE | CombatState.HEAVY)) == 0
                && rnd.nextFloat() < 0.18f * skill * skill && me.stamina() > 20) {
            // a master meets the blow with the same blow
            this.counter = true;
        } else if (!this.hasShield() && me.stamina() > 30 && rnd.nextFloat() < 0.08f * skill) {
            // or steps out of its way
            this.guardLead = -1;
            KnightCombatApi.input(this.soldier, rnd.nextBoolean() ? CombatAction.DODGE_LEFT : CombatAction.DODGE_RIGHT);
        }
    }

    private void tickAttack(LivingEntity t, @Nullable CombatState foe, CombatState me, WeaponStats weapon, double gap, float reach,
                            float skill, float stamina, boolean winded, RandomSource rnd) {
        Phase phase = me.phase();
        if (this.attackDelay > 0) {
            this.attackDelay--;
        }

        if (phase == Phase.WINDUP) {
            boolean isHeavy = (me.flags() & CombatState.HEAVY) != 0;
            if (this.heavy && isHeavy) {
                this.heavy = false;
                KnightCombatApi.input(this.soldier, CombatAction.ATTACK_RELEASE);
            }
            // a feint against a raised guard: the blow is pulled and another follows at once
            if (foe != null && foe.guarding() && !isHeavy && (me.flags() & CombatState.RIPOSTE) == 0 && this.feintChecked != me.phaseLength()
                    && me.phaseTicks() * 2 >= me.phaseLength()) {
                this.feintChecked = me.phaseLength();
                if (me.stamina() > 25 && rnd.nextFloat() < 0.3f * skill) {
                    KnightCombatApi.input(this.soldier, CombatAction.FEINT);
                    this.attackDelay = 1 + rnd.nextInt(3);
                    this.comboNext = false;
                }
            }
            return;
        }
        if (phase == Phase.RELEASE) {
            if (this.comboNext) {
                this.comboNext = false;
                this.attack(this.pick(weapon, rnd), false);
            }
            return;
        }
        if (phase != Phase.IDLE && phase != Phase.BLOCK && !(phase == Phase.RECOVERY && (me.flags() & CombatState.FEINT) != 0)) {
            return;
        }

        if (this.riposteIn >= 0) {
            if (this.riposteIn-- == 0 && gap <= reach + 0.3) {
                this.riposteIn = -1;
                this.attack(this.pick(weapon, rnd), false);
                this.attackDelay = 10 + rnd.nextInt(10);
                return;
            }
            if (gap > reach + 0.3) {
                this.riposteIn = -1;
            }
        }

        if (phase == Phase.BLOCK && this.guardUp) {
            // guarding against a blow on its way: no attacks until it is over
            return;
        }

        boolean open = foe != null && (foe.phase() == Phase.STAGGER || foe.phase() == Phase.RECOVERY && (foe.flags() & CombatState.FEINT) == 0);
        if (winded && !open) {
            if (gap > reach + 1 && rnd.nextInt(30) == 0) {
                KnightCombatApi.input(this.soldier, CombatAction.BATTLE_CRY);
            }
            return;
        }

        // a guard that stays up is broken with a kick (a shield bash, with a shield)
        if (foe != null && foe.guarding() && this.foeGuardTicks > 12 + (1 - skill) * 30 && gap < 2.1 && me.stamina() > 20
                && rnd.nextInt(6) == 0) {
            this.lowerGuard();
            KnightCombatApi.input(this.soldier, CombatAction.KICK);
            this.foeGuardTicks = 0;
            this.attackDelay = 4;
            return;
        }

        if (gap > reach + 0.15 || this.attackDelay > 0 && !open) {
            return;
        }

        AttackType type = this.pick(weapon, rnd);
        // a heavy blow when he is open, or now and then against someone who cannot guard
        boolean heavy = me.stamina() > 30 && (open && foe.phase() == Phase.STAGGER && rnd.nextFloat() < 0.6f
                || foe == null && rnd.nextFloat() < 0.2f || foe != null && !foe.guarding() && rnd.nextFloat() < 0.12f);
        this.lowerGuard();
        this.attack(type, heavy);
        this.comboNext = me.stamina() > 25 && rnd.nextFloat() < 0.15f + 0.5f * skill;
        float pause = 1 - skill;
        this.attackDelay = Math.round(8 + pause * 22 + rnd.nextInt(Math.max(1, Math.round(10 + pause * 20))));
        if (foe == null) {
            // monsters do not guard: no reason to wait
            this.attackDelay /= 2;
        }
    }

    private void attack(AttackType type, boolean heavy) {
        KnightCombatApi.input(this.soldier, CombatAction.of(type));
        this.heavy = heavy;
        if (!heavy) {
            KnightCombatApi.input(this.soldier, CombatAction.ATTACK_RELEASE);
        }
    }

    /** Which blow: each weapon has those it is made for. */
    private AttackType pick(WeaponStats weapon, RandomSource rnd) {
        float slash;
        float overhead;
        switch (weapon.kind()) {
            case SPEAR -> {
                slash = 0.15f;
                overhead = 0.1f;
            }
            case THRUSTING_SWORD, DAGGER -> {
                slash = 0.3f;
                overhead = 0.15f;
            }
            case AXE, BLUNT -> {
                slash = 0.45f;
                overhead = 0.45f;
            }
            case POLEAXE -> {
                slash = 0.35f;
                overhead = 0.4f;
            }
            default -> {
                slash = 0.45f;
                overhead = 0.3f;
            }
        }
        float r = rnd.nextFloat();
        return r < slash ? AttackType.SLASH : r < slash + overhead ? AttackType.OVERHEAD : AttackType.STAB;
    }

    private boolean guarding(CombatState me) {
        return me.phase() == Phase.BLOCK || this.soldier.isUsingItem() && this.soldier.getUseItem().getItem() instanceof ShieldItem;
    }

    private void raiseGuard(CombatState me) {
        if (this.hasShield()) {
            if (!this.soldier.isUsingItem() && KnightCombatApi.shieldReady(this.soldier)) {
                this.soldier.startUsingItem(InteractionHand.OFF_HAND);
                this.guardUp = true;
            }
        } else if (me.phase() != Phase.BLOCK) {
            KnightCombatApi.input(this.soldier, CombatAction.BLOCK_START);
            this.guardUp = true;
        }
    }

    private void lowerGuard() {
        if (this.soldier.isUsingItem() && this.soldier.getUseItem().getItem() instanceof ShieldItem) {
            this.soldier.stopUsingItem();
        }
        CombatState me = KnightCombatApi.state(this.soldier);
        if (me.phase() == Phase.BLOCK && (me.flags() & CombatState.SHIELD) == 0) {
            KnightCombatApi.input(this.soldier, CombatAction.BLOCK_STOP);
        }
        this.guardUp = false;
    }

    /** Looks straight at the enemy, body and head: the blows go where he looks. */
    private void face(LivingEntity t) {
        double dx = t.getX() - this.soldier.getX();
        double dz = t.getZ() - this.soldier.getZ();
        double dy = t.getEyeY() - 0.25 - this.soldier.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        this.soldier.setYRot(yaw);
        this.soldier.setYHeadRot(yaw);
        this.soldier.yBodyRot = yaw;
        this.soldier.setXRot(Mth.clamp(pitch, -60, 60));
        this.soldier.getLookControl().setLookAt(t.getX(), t.getEyeY() - 0.25, t.getZ(), 360, 360);
    }

    /** Whether {@code t} looks toward the soldier, so that his blow would come at him. */
    private boolean facesMe(LivingEntity t) {
        Vec3 look = t.getViewVector(1);
        Vec3 to = this.soldier.position().subtract(t.position());
        double len = Math.sqrt(to.x * to.x + to.z * to.z);
        double lookLen = Math.sqrt(look.x * look.x + look.z * look.z);
        if (len < 1.0E-3 || lookLen < 1.0E-3) {
            return true;
        }
        return (look.x * to.x + look.z * to.z) / (len * lookLen) > 0.5;
    }

    /** An arrow or bolt flying at him. */
    private boolean incomingArrow() {
        return !this.soldier.level().getEntitiesOfClass(Projectile.class, this.soldier.getBoundingBox().inflate(6),
                p -> p.getOwner() != null && !this.soldier.isAlliedTo(p.getOwner()) && p.getDeltaMovement().lengthSqr() > 0.2
                        && p.getDeltaMovement().dot(this.soldier.position().subtract(p.position())) > 0).isEmpty();
    }
}
