package io.github.nicksementsov.druzhina.client;

import io.github.nicksementsov.druzhina.entity.Role;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.network.DruzhinaNetwork;
import io.github.nicksementsov.druzhina.network.HirePacket;
import io.github.nicksementsov.druzhina.server.Army;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The muster table: who can be hired, what each costs, and a button to hire him, in tabs: foot, the retinue and the
 * knights, archers, horse, elephants.
 */
public class MusterScreen extends Screen {
    private static final int ROW = 33;
    private static final int TABS_Y = 40;
    private static final int TAB_H = 22;
    private static final int LIST_Y = TABS_Y + TAB_H + 4;
    /** The tab last looked at, kept while the game runs. */
    private static Role.Branch lastTab = Role.Branch.INFANTRY;
    /** What the server says: how many men the player leads, how many he may (-1: no limit, -2: not told yet), and why. */
    private static int men;
    private static int max = -2;
    @javax.annotation.Nullable
    private static Component why;
    private static final int BG = 0xE0201812;
    private static final int FRAME = 0xFF8B6B3E;
    private static final int GOLD = 0xFFE8C766;
    /** The colour of the purse and of prices the player can pay. */
    private static final int COIN = 0xFFF5D255;

    private final BlockPos table;
    private boolean asked;
    private final Role.Branch[] tabs = Role.Branch.tabs();
    private Role.Branch tab = lastTab;
    private Role[] roles = Role.forHire(lastTab);
    private final List<Button> buttons = new ArrayList<>();
    private int panelWidth;
    private int left;
    private int top;
    private int panelHeight;

    public MusterScreen(BlockPos table) {
        super(Component.translatable("druzhina.gui.title"));
        this.table = table;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(400, this.width - 16);
        int rows = 0;
        for (Role.Branch b : this.tabs) {
            rows = Math.max(rows, Role.forHire(b).length);
        }
        this.panelHeight = LIST_Y + rows * ROW + 40;
        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);
        this.buttons.clear();
        for (int i = 0; i < this.roles.length; i++) {
            Role role = this.roles[i];
            int y = this.top + LIST_Y + i * ROW;
            Button b = Button.builder(Component.translatable("druzhina.gui.hire"), btn -> this.hire(role))
                    .bounds(this.left + this.panelWidth - 68, y + 6, 60, 20)
                    .build();
            this.buttons.add(this.addRenderableWidget(b));
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
                .bounds(this.left + this.panelWidth / 2 - 40, this.top + this.panelHeight - 26, 80, 20).build());
        if (!this.asked) {
            // how many men the server lets him lead (with Prestol: as many as his realm keeps)
            this.asked = true;
            max = -2;
            DruzhinaNetwork.CHANNEL.sendToServer(new HirePacket(this.table, HirePacket.ASK));
        }
        this.refresh();
    }

    /** What a tab is shown by. */
    private static ItemStack icon(Role.Branch branch) {
        return switch (branch) {
            case KNIGHTS -> Role.KNIGHT.icon();
            case RANGED -> Role.ARCHER.icon();
            case CAVALRY -> Role.LANCER.icon();
            case ELEPHANTS -> new ItemStack(io.github.nicksementsov.druzhina.registry.DruzhinaRegistry.WAR_ELEPHANT_EGG.get());
            default -> Role.SWORDSMAN.icon();
        };
    }

    /** The server's word on the size of the army (see {@code MusterInfoPacket}). */
    public static void info(int count, int limit, @javax.annotation.Nullable Component reason) {
        men = count;
        max = limit;
        why = reason;
    }

    /** Whether {@code more} men fit into the army. */
    private static boolean room(int more) {
        return max < 0 || men + more <= max;
    }

    private void select(Role.Branch branch) {
        this.tab = branch;
        lastTab = branch;
        this.roles = Role.forHire(branch);
        this.rebuildWidgets();
    }

    /** The tab under the mouse, or null. */
    private Role.Branch tabAt(double mx, double my) {
        int w = (this.panelWidth - 8) / this.tabs.length;
        int y = this.top + TABS_Y;
        if (my < y || my >= y + TAB_H) {
            return null;
        }
        int i = (int) Math.floor((mx - this.left - 4) / w);
        return i >= 0 && i < this.tabs.length ? this.tabs[i] : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Role.Branch b = this.tabAt(mx, my);
        if (b != null && button == 0) {
            if (b != this.tab) {
                this.select(b);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void hire(Role role) {
        DruzhinaNetwork.CHANNEL.sendToServer(new HirePacket(this.table, role.ordinal()));
    }

    @Override
    public void tick() {
        this.refresh();
        Player player = Minecraft.getInstance().player;
        if (player == null || player.distanceToSqr(this.table.getCenter()) > 8 * 8) {
            this.onClose();
        }
    }

    private void refresh() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int gold = Army.gold(player);
        boolean free = player.getAbilities().instabuild;
        for (int i = 0; i < this.buttons.size(); i++) {
            Role role = this.roles[i];
            this.buttons.get(i).active = room(role.crew()) && (free || gold >= Army.price(role));
        }
    }

    /** The player's soldiers this client knows of (until the server tells). */
    private int army(Player player) {
        if (max != -2) {
            return men;
        }
        int n = 0;
        for (var e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (e instanceof Soldier s && player.getUUID().equals(s.getOwnerUUID()) && !s.managed()) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int w = this.panelWidth;
        int x = this.left;
        int y = this.top;
        g.fill(x - 1, y - 1, x + w + 1, y + this.panelHeight + 1, FRAME);
        g.fill(x, y, x + w, y + this.panelHeight, BG);
        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x + w / 2, y + 8, GOLD);

        Player player = Minecraft.getInstance().player;
        Component tooltip = null;
        int gold = player == null ? 0 : Army.gold(player);
        boolean free = player != null && player.getAbilities().instabuild;
        if (player != null) {
            g.renderItem(new ItemStack(Items.GOLD_INGOT), x + 8, y + 20);
            g.drawString(this.font, Component.translatable("druzhina.gui.purse", gold), x + 28, y + 25, COIN);
            Component army = max == -2 ? Component.translatable("druzhina.gui.army_count", this.army(player))
                    : max < 0 ? Component.translatable("druzhina.gui.army_free", this.army(player))
                    : Component.translatable("druzhina.gui.army", this.army(player), max);
            int ax = x + w - 8 - this.font.width(army);
            g.drawString(this.font, army, ax, y + 25, max >= 0 && men >= max ? 0xFFE06060 : 0xFFD8D0C0);
            if (why != null && mouseX >= ax && mouseX < x + w - 8 && mouseY >= y + 22 && mouseY < y + 36) {
                // what the limit comes from (the realm's barracks and towns, with Prestol)
                tooltip = why;
            }
        }

        // the tabs
        int tw = (w - 8) / this.tabs.length;
        for (int i = 0; i < this.tabs.length; i++) {
            Role.Branch b = this.tabs[i];
            int tx = x + 4 + i * tw;
            int ty = y + TABS_Y;
            boolean on = b == this.tab;
            boolean hover = this.tabAt(mouseX, mouseY) == b;
            g.fill(tx, ty, tx + tw - 2, ty + TAB_H, on ? FRAME : hover ? 0x60FFFFFF : 0x30FFFFFF);
            if (on) {
                g.fill(tx + 1, ty + 1, tx + tw - 3, ty + TAB_H, 0xFF3A2C1C);
            }
            g.renderItem(icon(b), tx + 3, ty + 3);
            Component label = Component.translatable("druzhina.tab." + b.id());
            String text = this.font.plainSubstrByWidth(label.getString(), tw - 26);
            g.drawString(this.font, text, tx + 22, ty + 7, on ? GOLD : 0xFFD8D0C0);
        }

        int textWidth = w - 32 - 116;
        for (int i = 0; i < this.roles.length; i++) {
            Role role = this.roles[i];
            int ry = y + LIST_Y + i * ROW;
            g.fill(x + 4, ry, x + w - 4, ry + ROW - 2, i % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
            g.renderItem(role.icon(), x + 10, ry + 8);
            Component name = Component.translatable(role.crew() > 1 ? "druzhina.unit." + role.id() : "druzhina.role." + role.id())
                    .withStyle(ChatFormatting.BOLD);
            g.drawString(this.font, name, x + 32, ry + 3, GOLD);
            Component stats = role.crew() > 1 ? Component.translatable("druzhina.gui.crew", role.crew())
                    : Component.translatable("druzhina.gui.stats", Math.round(role.health), Math.round(role.skill * 100));
            g.drawString(this.font, stats, x + 32 + this.font.width(name) + 8, ry + 3, 0xFFB0A898);
            List<FormattedCharSequence> lines = this.font.split(Component.translatable("druzhina.role." + role.id() + ".desc"), textWidth);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                g.drawString(this.font, lines.get(l), x + 32, ry + 14 + l * 10, 0xFFD8D0C0);
            }
            // the price in gold ingots, red when the purse is too thin
            int price = Army.price(role);
            String text = String.valueOf(price);
            int px = x + w - 74 - this.font.width(text);
            g.renderItem(new ItemStack(Items.GOLD_INGOT), px - 18, ry + 8);
            g.drawString(this.font, text, px, ry + 12, free || gold >= price ? COIN : 0xFFE06060);
        }

        g.drawCenteredString(this.font, Component.translatable("druzhina.gui.baton_hint"), x + w / 2, y + this.panelHeight - 38, 0xFFA09888);
        super.render(g, mouseX, mouseY, partialTick);
        if (tooltip != null) {
            g.renderTooltip(this.font, this.font.split(tooltip, 240), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
