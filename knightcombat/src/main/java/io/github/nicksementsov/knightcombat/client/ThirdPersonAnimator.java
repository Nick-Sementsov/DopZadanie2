package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.Grip;
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
 */
@Mod.EventBusSubscriber(modid = KnightCombat.MODID, value = Dist.CLIENT)
public final class ThirdPersonAnimator {
    private static HumanoidModel.ArmPose armPose;
    private static float partialTick;

    // The player being drawn: weapons taken out of their hands and the blade and edge directions of their pose (model space).
    private static Player posed;
    private static WeaponStats posedStats;
    private static int hiddenSlot;
    private static ItemStack hiddenMain = ItemStack.EMPTY;
    private static ItemStack hiddenOff = ItemStack.EMPTY;
    private static final Vector3f BLADE = new Vector3f();
    private static final Vector3f EDGE = new Vector3f();

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
        if (armPose == null || weapon == null || player.isUsingItem() || player.isSleeping() || player.isFallFlying()
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
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        restore();
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
        ThirdPersonPose pose = ThirdPersonPoses.current(ClientCombat.anim(player), weapon.grip(), partialTick);

        float yaw = left ? -pose.bodyYaw : pose.bodyYaw;
        model.body.yRot = yaw;
        model.rightArm.x = -Mth.cos(yaw) * 5;
        model.rightArm.z = Mth.sin(yaw) * 5;
        model.leftArm.x = Mth.cos(yaw) * 5;
        model.leftArm.z = -Mth.sin(yaw) * 5;
        // Minecraft resets the tilt of the body after the arms are posed, so a lean moves the shoulders and the head instead.
        model.rightArm.z -= pose.lean * 10;
        model.leftArm.z -= pose.lean * 10;
        main.z -= pose.lunge;

        Quaternionf body = PoseMath.yaw(pose.bodyYaw);
        Vector3f armDir = mirrored(body.transform(new Vector3f(pose.arm)), left);
        directions(pose, left, BLADE, EDGE);
        // The fist faces along the blade it holds.
        Quaternionf mainRotation = PoseMath.armFrame(armDir, BLADE);
        set(main, mainRotation);
        main.xRot += walkMain * 0.15f;

        if (weapon.grip().twoHanded()) {
            reachForGrip(main, off, weapon.grip(), left);
        } else {
            Vector3f offDir = mirrored(body.transform(new Vector3f(pose.off)), left);
            set(off, PoseMath.armFrame(offDir, new Vector3f(0, 0, -1)));
            off.xRot += walkOff * 0.5f;
        }

        if (pose.legWeight > 0) {
            ModelPart leg = left ? model.leftLeg : model.rightLeg;
            leg.xRot = Mth.lerp(pose.legWeight, leg.xRot, pose.leg);
        }
        model.head.xRot += pose.head + pose.lean * 0.5f;
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
