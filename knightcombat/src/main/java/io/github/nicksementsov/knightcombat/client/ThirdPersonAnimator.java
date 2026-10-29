package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.Combo;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Grip;
import io.github.nicksementsov.knightcombat.combat.Lances;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.combat.Shields;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Poses the player model while a weapon is in hand. Minecraft asks the arm pose of the main hand to position the
 * arms; this mod's arm pose places both arms, the body, a leg and the head from {@link ThirdPersonPoses}.
 * <p>
 * Minecraft would draw the weapon at a fixed angle to the hand. To let the blade turn freely, the weapon is taken
 * out of the hand while the player is drawn and {@link WeaponLayer} draws it at the angle of the pose. Weapons with
 * their own renderer stay in the hand and are drawn by Minecraft.
 * <p>
 * The whole model turns too: it spins with a whirlwind and leans into a dodge.
 * <p>
 * A shield in the other hand is held before the body and thrust out in a bash; while it is raised, Minecraft poses
 * that arm and the weapon stays ready behind it.
 */
@Mod.EventBusSubscriber(modid = KnightCombat.MODID, value = Dist.CLIENT)
public final class ThirdPersonAnimator {
    private static HumanoidModel.ArmPose armPose;
    private static float partialTick;

    // The player being drawn: weapons taken out of their hands and the blade and edge directions of their pose (model space).
    private static Player posed;
    private static WeaponStats posedStats;
    /** The player being drawn holds a lance (drawn like a weapon, but not one of the melee rules). */
    private static boolean posedLance;
    private static int hiddenSlot;
    private static ItemStack hiddenMain = ItemStack.EMPTY;
    private static ItemStack hiddenOff = ItemStack.EMPTY;
    private static final Vector3f BLADE = new Vector3f();
    private static final Vector3f EDGE = new Vector3f();
    /** Share of a turn a whirlwind spins during its release; the rest comes in the recovery. */
    private static final float SPIN_IN_RELEASE = 0.7f;
    /** Whether the pose stack of the player being drawn was turned, to be put back afterwards. */
    private static boolean turned;

    private ThirdPersonAnimator() {
    }

    /** Creates the arm pose; arm poses are an extensible enum, so this runs once during client setup. */
    static void init() {
        armPose = HumanoidModel.ArmPose.create("KNIGHTCOMBAT_WEAPON", false, ThirdPersonAnimator::pose);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        restore();
        Player player = event.getEntity();
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        boolean lance = weapon == null && CombatConfig.mountedCharges() && Lances.isLance(player.getMainHandItem());
        if (lance) {
            weapon = Lances.RENDER;
        }
        if (armPose == null || weapon == null || player.isUsingItem() && !Shields.raisedInOffHand(player) || player.isSleeping() || player.isFallFlying()
                || player.isVisuallySwimming() || player.isSpectator()) {
            return;
        }
        partialTick = event.getPartialTick();
        PlayerModel<?> model = event.getRenderer().getModel();
        if (player.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = armPose;
        } else {
            model.leftArmPose = armPose;
        }
        Inventory inventory = player.getInventory();
        posed = player;
        posedStats = weapon;
        posedLance = lance;
        hiddenSlot = inventory.selected;
        if (HeldModel.measurable(inventory.items.get(hiddenSlot))) {
            hiddenMain = inventory.items.get(hiddenSlot);
            inventory.items.set(hiddenSlot, ItemStack.EMPTY);
        }
        if (weapon.twoHanded()) {
            // Both hands are on the weapon, so whatever is in the other hand is not drawn.
            hiddenOff = inventory.offhand.get(0);
            inventory.offhand.set(0, ItemStack.EMPTY);
        }
        Quaternionf turn = bodyTurn(player, ClientCombat.anim(player), partialTick);
        if (turn != null) {
            event.getPoseStack().pushPose();
            event.getPoseStack().mulPose(turn);
            turned = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (turned) {
            event.getPoseStack().popPose();
            turned = false;
        }
        restore();
    }

    /**
     * How the whole model is turned, about the feet, or {@code null}. A whirlwind spins the fighter round once
     * (most of the turn in the release, the rest in the recovery); a turn cut short (a blocked whirlwind) unwinds
     * to the nearest whole turn. A dodge leans the body into the step.
     */
    private static Quaternionf bodyTurn(Player player, FighterAnim anim, float partialTick) {
        if (anim == null) {
            return null;
        }
        float p = anim.progress(partialTick);
        if (anim.spinVersion != anim.version) {
            anim.spinVersion = anim.version;
            anim.spinFrom = anim.spinShown;
        }
        float full = Mth.TWO_PI;
        float spin;
        if (anim.phase == Phase.RELEASE && anim.combo == Combo.WHIRLWIND) {
            spin = SPIN_IN_RELEASE * full * PoseMath.smooth(p);
        } else {
            float whole = Math.round(anim.spinFrom / full) * full;
            float t = anim.phase == Phase.RECOVERY ? PoseMath.easeOut(Math.min(1, p * 2)) : PoseMath.smooth(anim.elapsed(partialTick) / 4);
            spin = Mth.lerp(t, anim.spinFrom, whole);
        }
        anim.spinShown = spin;
        Quaternionf turn = null;
        if (Math.abs(spin % full) > 1.0E-3f) {
            turn = new Quaternionf().rotationY(spin);
        }
        if (anim.phase == Phase.DODGE) {
            float k = Mth.sin(Mth.PI * Math.min(1, p * 1.15f));
            float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
            // The body's forward is (-sin yaw, cos yaw) and its right (-cos yaw, -sin yaw); turning about the forward
            // axis leans it to the right, about the right axis leans it back.
            float roll = anim.has(CombatState.DODGE_LEFT) ? -0.32f : anim.has(CombatState.DODGE_RIGHT) ? 0.32f : 0;
            float pitch = anim.has(CombatState.DODGE_FORWARD) ? -0.16f : roll == 0 ? 0.18f : 0;
            Quaternionf lean = new Quaternionf()
                    .rotateAxis(roll * k, -Mth.sin(yaw), 0, Mth.cos(yaw))
                    .rotateAxis(pitch * k, -Mth.cos(yaw), 0, -Mth.sin(yaw));
            turn = turn == null ? lean : turn.mul(lean);
        }
        return turn;
    }

    /** In case something went wrong while drawing, the weapons are put back before the game uses them. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        restore();
    }

    private static void restore() {
        if (posed == null) {
            return;
        }
        Inventory inventory = posed.getInventory();
        if (!hiddenMain.isEmpty()) {
            inventory.items.set(hiddenSlot, hiddenMain);
        }
        if (!hiddenOff.isEmpty()) {
            inventory.offhand.set(0, hiddenOff);
        }
        posed = null;
        posedStats = null;
        hiddenMain = ItemStack.EMPTY;
        hiddenOff = ItemStack.EMPTY;
    }

    /** The weapon taken out of the hand of {@code player} for drawing, or {@code null}. */
    static ItemStack hiddenWeapon(Player player) {
        return player == posed && !hiddenMain.isEmpty() ? hiddenMain : null;
    }

    static WeaponStats posedStats() {
        return posedStats;
    }

    static Vector3f blade() {
        return BLADE;
    }

    static Vector3f edge() {
        return EDGE;
    }

    private static void pose(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        if (!(entity instanceof Player player) || player != posed || posedStats == null) {
            return;
        }
        WeaponStats weapon = posedStats;
        boolean left = arm == HumanoidArm.LEFT;
        ModelPart main = left ? model.leftArm : model.rightArm;
        ModelPart off = left ? model.rightArm : model.leftArm;
        // Before this runs the arms hold the walking swing; keep a little of it.
        float walkMain = main.xRot;
        float walkOff = off.xRot;
        FighterAnim anim = ClientCombat.anim(player);
        ThirdPersonPose pose = ThirdPersonPoses.current(anim, weapon.grip(), posedLance, partialTick);
        // Struck: the body twists away from the blow, the head snaps back, the shoulders are driven back.
        float flinch = anim != null ? anim.flinch(partialTick) : 0;
        float flinchSide = anim != null ? anim.hurtSide : 0;

        float yaw = (left ? -pose.bodyYaw : pose.bodyYaw) + flinchSide * 0.4f * flinch;
        model.body.yRot = yaw;
        model.rightArm.x = -Mth.cos(yaw) * 5;
        model.rightArm.z = Mth.sin(yaw) * 5;
        model.leftArm.x = Mth.cos(yaw) * 5;
        model.leftArm.z = -Mth.sin(yaw) * 5;
        // Minecraft resets the tilt of the body after the arms are posed, so a lean moves the shoulders and the head instead.
        model.rightArm.z -= pose.lean * 10 - 1.5f * flinch;
        model.leftArm.z -= pose.lean * 10 - 1.5f * flinch;
        main.z -= pose.lunge;

        Quaternionf body = PoseMath.yaw(pose.bodyYaw);
        Vector3f armDir = mirrored(body.transform(new Vector3f(pose.arm)), left);
        directions(pose, left, BLADE, EDGE);
        // The fist faces along the blade it holds.
        Quaternionf mainRotation = PoseMath.armFrame(armDir, BLADE);
        set(main, mainRotation);
        main.xRot += walkMain * 0.15f;

        // A raised shield: Minecraft has already posed that arm (it poses the arm of the item in use first).
        boolean shieldUp = Shields.raisedInOffHand(player);
        // A thrown weapon and a couched spear are held in one hand; the other holds the reins.
        boolean oneHand = anim != null && (anim.phase == Phase.THROW || anim.phase == Phase.COUCH
                || anim.phase == Phase.RECOVERY && anim.has(CombatState.LANCE));
        if (weapon.grip().twoHanded() && !oneHand) {
            reachForGrip(main, off, weapon.grip(), left);
        } else if (Shields.isShield(player.getOffhandItem())) {
            if (!shieldUp) {
                // Held before the body, its face to the front, lower than Minecraft's raised shield; a bash thrusts it out.
                float bash = ThirdPersonPoses.bash(anim, partialTick);
                off.xRot = Mth.lerp(bash, -0.8f, -1.3f) + walkOff * 0.15f;
                off.yRot = (left ? -1 : 1) * Mth.lerp(bash, 0.8f, 0.9f);
                off.zRot = (left ? -1 : 1) * Mth.lerp(bash, 0.3f, 0);
            }
        } else {
            Vector3f offDir = mirrored(body.transform(new Vector3f(pose.off)), left);
            set(off, PoseMath.armFrame(offDir, new Vector3f(0, 0, -1)));
            off.xRot += walkOff * 0.5f;
        }

        if (flinch != 0) {
            main.zRot += (left ? -1 : 1) * 0.25f * flinch;
            if (!shieldUp) {
                off.xRot -= 0.4f * flinch;
            }
        }

        // A fencing stance while standing: feet apart, the off-hand foot in front. Attacks step into it, staggers
        // step back. Walking takes over the legs as the fighter starts to move.
        float stance = player.isPassenger() ? 0 : 1 - Mth.clamp(player.walkAnimation.speed(partialTick) * 2.5f, 0, 1);
        if (stance > 0) {
            ModelPart front = left ? model.rightLeg : model.leftLeg;
            ModelPart back = left ? model.leftLeg : model.rightLeg;
            float step = pose.step - 0.25f * flinch;
            front.xRot -= (0.22f + step) * stance;
            back.xRot += (0.2f + step * 0.4f) * stance;
            model.leftLeg.zRot -= 0.07f * stance;
            model.rightLeg.zRot += 0.07f * stance;
        }
        if (pose.legWeight > 0) {
            ModelPart leg = left ? model.leftLeg : model.rightLeg;
            leg.xRot = Mth.lerp(pose.legWeight, leg.xRot, pose.leg);
        }
        model.head.xRot += pose.head + pose.lean * 0.5f - 0.35f * flinch;
        model.head.yRot += flinchSide * 0.25f * flinch;
    }

    /** The blade and edge directions of a pose in model space, turned with the body and mirrored for the left hand. */
    private static void directions(ThirdPersonPose pose, boolean left, Vector3f blade, Vector3f edge) {
        Quaternionf body = PoseMath.yaw(pose.bodyYaw);
        blade.set(mirrored(body.transform(new Vector3f(pose.blade)), left)).normalize();
        edge.set(mirrored(body.transform(new Vector3f(pose.edge)), left));
        edge.fma(-edge.dot(blade), blade);
        if (edge.lengthSquared() < 1.0E-6f) {
            edge.set(PoseMath.perpendicular(blade));
        }
        edge.normalize();
    }

    /**
     * Where a weapon held in a pose is: the transform from the space its model is drawn in (as {@link WeaponLayer}
     * draws it) to model space. The same as the arm pose above, without the small sway of walking; used to draw the
     * trail of a swing from poses the fighter went through.
     */
    static Matrix4f weaponTransform(ThirdPersonPose pose, boolean left, HeldModel.Axes axes, float scale) {
        float yaw = left ? -pose.bodyYaw : pose.bodyYaw;
        float side = left ? 1 : -1;
        Vector3f shoulder = new Vector3f(side * Mth.cos(yaw) * 5, 2, -side * Mth.sin(yaw) * 5 - pose.lean * 10 - pose.lunge);
        Vector3f armDir = mirrored(PoseMath.yaw(pose.bodyYaw).transform(new Vector3f(pose.arm)), left);
        Vector3f blade = new Vector3f();
        Vector3f edge = new Vector3f();
        directions(pose, left, blade, edge);
        Vector3f hand = PoseMath.armFrame(armDir, blade).transform(new Vector3f(left ? PoseMath.LEFT_GRIP : PoseMath.RIGHT_GRIP))
                .add(shoulder).div(16);
        return new Matrix4f().translation(hand).rotate(PoseMath.frame(axes.blade(), axes.edge(), blade, edge)).scale(scale);
    }

    /** Points the free arm at the weapon: below the main hand on a long hilt, or further up the shaft of a polearm. */
    private static void reachForGrip(ModelPart main, ModelPart off, Grip grip, boolean left) {
        Quaternionf mainRotation = new Quaternionf().rotationZYX(main.zRot, main.yRot, main.xRot);
        Vector3f hand = mainRotation.transform(new Vector3f(left ? PoseMath.LEFT_GRIP : PoseMath.RIGHT_GRIP)).add(main.x, main.y, main.z);
        float along = grip == Grip.POLEARM ? 7f : -3.5f;
        Vector3f target = hand.fma(along, BLADE).sub(off.x, off.y, off.z);
        Vector3f offGrip = new Vector3f(left ? PoseMath.RIGHT_GRIP : PoseMath.LEFT_GRIP).normalize();
        set(off, new Quaternionf().rotationTo(offGrip, target.normalize()));
    }

    /** The poses are made for the right hand; for left-handed players they are mirrored. */
    private static Vector3f mirrored(Vector3f v, boolean left) {
        if (left) {
            v.x = -v.x;
        }
        return v;
    }

    private static void set(ModelPart part, Quaternionf rotation) {
        Vector3f euler = PoseMath.eulerZYX(rotation);
        part.xRot = euler.x;
        part.yRot = euler.y;
        part.zRot = euler.z;
    }
}
