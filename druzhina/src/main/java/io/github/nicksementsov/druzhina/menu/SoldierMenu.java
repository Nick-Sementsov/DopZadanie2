package io.github.nicksementsov.druzhina.menu;

import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A soldier's things, as his commander sees them: helmet, body armour, leggings and boots, what he holds in either
 * hand, a spare weapon and the armour of his horse, beside the commander's own inventory. What the commander puts on
 * him he may take back; his own kit stays with him (it is put aside while he uses what he was given, and taken up
 * again when that is taken back).
 */
public class SoldierMenu extends AbstractContainerMenu {
    /** Where the gear slots are drawn, by the soldier's gear index (see {@link Soldier#GEAR}). */
    public static final int[][] GEAR_POS = {
        {8, 20}, {8, 38}, {8, 56}, {8, 74},
        {84, 38}, {84, 56},
        {84, 74},
        {84, 20},
    };
    public static final int WIDTH = 240;
    public static final int HEIGHT = 190;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = 108;

    /** The soldier: the real one on the server, the one the client sees on the client. */
    @Nullable
    private final Soldier soldier;
    private final boolean server;
    private final int soldierId;
    private final Container gear;
    /** The soldier's "given" mask, synced to the client to draw his own things as his. */
    private final DataSlot given = DataSlot.standalone();

    /** Client side: opened by the server, for the soldier with this entity id. */
    public SoldierMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readVarInt(), null);
    }

    /** Server side. */
    public SoldierMenu(int id, Inventory inv, Soldier soldier) {
        this(id, inv, soldier.getId(), soldier);
    }

    private SoldierMenu(int id, Inventory inv, int soldierId, @Nullable Soldier soldier) {
        super(DruzhinaRegistry.SOLDIER_MENU.get(), id);
        this.soldierId = soldierId;
        this.server = soldier != null;
        this.soldier = soldier != null ? soldier : inv.player.level().getEntity(soldierId) instanceof Soldier s ? s : null;
        this.gear = soldier != null ? new Gear(soldier) : new SimpleContainer(Soldier.GEAR);
        for (int i = 0; i < Soldier.GEAR; i++) {
            this.addSlot(new GearSlot(this.gear, i, GEAR_POS[i][0], GEAR_POS[i][1]));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
        }
        this.addDataSlot(this.given);
        if (soldier != null) {
            this.given.set(soldier.givenMask());
        }
    }

    public int soldierId() {
        return this.soldierId;
    }

    /** Whether the thing at gear slot {@code i} was given by the commander (and so can be taken back). */
    public boolean given(int i) {
        return (this.given.get() & 1 << i) != 0;
    }

    @Nullable
    public Soldier soldier() {
        return this.soldier;
    }

    @Override
    public void broadcastChanges() {
        if (this.server && this.soldier != null) {
            this.given.set(this.soldier.givenMask());
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        Soldier s = this.soldier;
        return s != null && s.isAlive() && player.getUUID().equals(s.getOwnerUUID()) && s.distanceToSqr(player) < 8 * 8;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId < 0 || slotId >= Soldier.GEAR) {
            super.clicked(slotId, button, type, player);
            return;
        }
        // the soldier's own slots: only a click to put in, take back or swap, and a shift-click to take back
        Soldier s = this.soldier;
        GearSlot slot = (GearSlot) this.slots.get(slotId);
        if (s == null || !slot.isActive()) {
            return;
        }
        ItemStack carried = this.getCarried();
        if (type == ClickType.QUICK_MOVE) {
            if (this.given(slotId) && !slot.getItem().isEmpty()) {
                ItemStack back = this.take(s, slotId);
                if (!this.moveItemStackTo(back, Soldier.GEAR, this.slots.size(), true)) {
                    player.getInventory().placeItemBackInInventory(back);
                }
            }
        } else if (type == ClickType.PICKUP) {
            if (carried.isEmpty()) {
                if (this.given(slotId) && !slot.getItem().isEmpty()) {
                    this.setCarried(this.take(s, slotId));
                }
            } else if (s.fits(slotId, carried)) {
                ItemStack back = this.put(s, slotId, carried.split(1));
                if (!back.isEmpty()) {
                    if (carried.isEmpty()) {
                        this.setCarried(back);
                    } else {
                        player.getInventory().placeItemBackInInventory(back);
                    }
                }
            }
        }
        this.slotsChanged(this.gear);
    }

    /** Puts {@code stack} on him; returns what was given before at that place. */
    private ItemStack put(Soldier s, int i, ItemStack stack) {
        if (this.server) {
            return s.hand(i, stack);
        }
        // the client only guesses; the server's answer follows
        ItemStack old = this.given(i) ? this.gear.getItem(i) : ItemStack.EMPTY;
        this.gear.setItem(i, stack);
        this.given.set(this.given.get() | 1 << i);
        return old;
    }

    private ItemStack take(Soldier s, int i) {
        if (this.server) {
            return s.takeBack(i);
        }
        ItemStack out = this.gear.getItem(i);
        this.gear.setItem(i, ItemStack.EMPTY);
        this.given.set(this.given.get() & ~(1 << i));
        return out;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // shift-click in the inventory: on the soldier, where it goes
        Slot from = this.slots.get(index);
        Soldier s = this.soldier;
        if (index < Soldier.GEAR || s == null || !from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        int to = this.placeFor(s, stack);
        if (to < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack back = this.put(s, to, stack.split(1));
        from.setChanged();
        if (!back.isEmpty()) {
            player.getInventory().placeItemBackInInventory(back);
        }
        this.slotsChanged(this.gear);
        return ItemStack.EMPTY;
    }

    /** The gear slot a shift-clicked item goes to: an empty one or one with his own thing (not one given before). */
    private int placeFor(Soldier s, ItemStack stack) {
        int[] order = {0, 1, 2, 3, Soldier.BARDING, Soldier.OFF, Soldier.MAIN, Soldier.SPARE};
        for (int i : order) {
            boolean shieldLike = i == Soldier.OFF && !(stack.getItem() instanceof net.minecraft.world.item.ShieldItem);
            boolean mainLike = i == Soldier.MAIN && stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
            if (shieldLike || mainLike || !this.slots.get(i).isActive() || !s.fits(i, stack)) {
                continue;
            }
            if (i == Soldier.SPARE && !this.slots.get(Soldier.MAIN).getItem().isEmpty() && !this.given(Soldier.MAIN)) {
                // a weapon goes to his hand before it goes to his belt
                return Soldier.MAIN;
            }
            if (this.slots.get(i).getItem().isEmpty() || !this.given(i)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GearSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GearSlot);
    }

    /** A gear slot: nothing goes in or out of it but by {@link #clicked}. */
    public final class GearSlot extends Slot {
        private final int gearIndex;

        GearSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.gearIndex = index;
        }

        public int gearIndex() {
            return this.gearIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            if (this.gearIndex != Soldier.BARDING) {
                return true;
            }
            Soldier s = SoldierMenu.this.soldier;
            return s != null && s.horse() != null;
        }
    }

    /** The soldier's things as a container, server side. */
    private static final class Gear implements Container {
        private final Soldier soldier;

        Gear(Soldier soldier) {
            this.soldier = soldier;
        }

        @Override
        public int getContainerSize() {
            return Soldier.GEAR;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < Soldier.GEAR; i++) {
                if (!this.soldier.gear(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            return this.soldier.gear(i);
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            this.soldier.setGear(i, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return this.soldier.isAlive();
        }

        @Override
        public void clearContent() {
        }
    }
}
