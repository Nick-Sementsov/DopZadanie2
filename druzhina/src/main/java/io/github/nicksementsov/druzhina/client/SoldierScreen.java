package io.github.nicksementsov.druzhina.client;

import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.entity.WarElephant;
import io.github.nicksementsov.druzhina.menu.SoldierMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A soldier's things: what he wears and holds, his spare weapon, his horse's armour, and how he fares. */
public class SoldierScreen extends AbstractContainerScreen<SoldierMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int OWN = 0xFF7A5A1E;
    private static final ResourceLocation[] EMPTY = {
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_helmet.png"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_chestplate.png"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_leggings.png"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_boots.png"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_slot_sword.png"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_shield.png"),
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_slot_sword.png"),
        null,
    };

    public SoldierScreen(SoldierMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SoldierMenu.WIDTH;
        this.imageHeight = SoldierMenu.HEIGHT;
        this.inventoryLabelX = SoldierMenu.INV_X;
        this.inventoryLabelY = SoldierMenu.INV_Y - 11;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        panel(g, x, y, this.imageWidth, this.imageHeight);
        // the soldier himself
        int bx = x + 26;
        int by = y + 18;
        g.fill(bx, by, bx + 54, by + 74, 0xFF000000);
        g.fill(bx + 1, by + 1, bx + 53, by + 73, 0xFF1E1A16);
        Soldier s = this.menu.soldier();
        if (s != null) {
            // a long lance or pike stays inside the frame
            g.enableScissor(bx + 1, by + 1, bx + 53, by + 73);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, bx + 27, by + 68, 28, bx + 27 - mouseX, by + 24 - mouseY, s);
            g.disableScissor();
        }
        // the slots
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                slot(g, x + slot.x - 1, y + slot.y - 1);
            }
        }
        for (int i = 0; i < Soldier.GEAR; i++) {
            Slot slot = this.menu.slots.get(i);
            if (!slot.isActive()) {
                continue;
            }
            int sx = x + slot.x;
            int sy = y + slot.y;
            if (slot.getItem().isEmpty()) {
                if (EMPTY[i] != null) {
                    g.blit(EMPTY[i], sx, sy, 0, 0, 16, 16, 16, 16);
                } else {
                    // a faint horse armour where the horse's goes
                    g.renderItem(new ItemStack(Items.IRON_HORSE_ARMOR), sx, sy);
                    g.fill(sx, sy, sx + 16, sy + 16, 0xB08B8B8B);
                }
            } else if (!this.menu.given(i)) {
                // his own: a little mark in the corner
                g.fill(sx - 1, sy - 1, sx + 3, sy, OWN);
                g.fill(sx - 1, sy, sx, sy + 3, OWN);
            }
        }
        this.renderInfo(g, x + 110, y + 18, s);
    }

    /** Who he is and how he fares. */
    private void renderInfo(GuiGraphics g, int x, int y, Soldier s) {
        if (s == null) {
            return;
        }
        int w = this.imageWidth - (x - this.leftPos) - 8;
        g.fill(x - 2, y - 2, x + w + 2, y + 84, 0xFF8B7B63);
        g.fill(x - 1, y - 1, x + w + 1, y + 83, 0xFFE9DFC8);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("druzhina.role." + s.role().id()).withStyle(ChatFormatting.BOLD));
        if (s.rank() > 0) {
            lines.add(Component.translatable("druzhina.rank." + s.rank()).withStyle(ChatFormatting.DARK_GREEN));
        }
        lines.add(Component.translatable("druzhina.gear.health", Mth.ceil(s.getHealth()), Mth.ceil(s.getMaxHealth())));
        lines.add(Component.translatable("druzhina.gear.armor", s.getArmorValue()));
        lines.add(Component.translatable("druzhina.gear.kills", s.kills()));
        lines.add(Component.translatable("druzhina.gear.orders", Component.translatable("druzhina.orders." + s.orders().id())));
        if (s.getVehicle() instanceof Mob beast) {
            String kind = beast instanceof WarElephant ? "elephant" : "horse";
            lines.add(Component.translatable("druzhina.gear." + kind, Mth.ceil(beast.getHealth()), Mth.ceil(beast.getMaxHealth())));
        } else if (s.mountId() != null) {
            lines.add(Component.translatable("druzhina.gear.on_foot").withStyle(ChatFormatting.DARK_RED));
        }
        int ly = y + 2;
        for (Component line : lines) {
            for (FormattedCharSequence part : this.font.split(line, w - 2)) {
                if (ly > y + 74) {
                    return;
                }
                g.drawString(this.font, part, x + 1, ly, TEXT, false);
                ly += 10;
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // his name over the window, cut to fit
        Component name = this.title;
        String text = this.font.plainSubstrByWidth(name.getString(), this.imageWidth - 16);
        g.drawString(this.font, text, this.titleLabelX, this.titleLabelY, TEXT, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        Slot slot = this.hoveredSlot;
        if (slot instanceof SoldierMenu.GearSlot gear && this.menu.getCarried().isEmpty()) {
            int i = gear.gearIndex();
            List<Component> lines = new ArrayList<>();
            if (slot.hasItem()) {
                lines.addAll(getTooltipFromItem(this.minecraft, slot.getItem()));
                lines.add(Component.translatable(this.menu.given(i) ? "druzhina.gear.given" : "druzhina.gear.own")
                        .withStyle(this.menu.given(i) ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            } else {
                lines.add(Component.translatable("druzhina.gear.slot." + i));
                lines.add(Component.translatable("druzhina.gear.put").withStyle(ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    /** A raised grey panel, as Minecraft draws its windows. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
        g.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, DARK);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, DARK);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL);
    }

    /** A sunken slot, 18 by 18. */
    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT);
        g.fill(x, y, x + 17, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 17, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, LIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, LIGHT);
    }
}
