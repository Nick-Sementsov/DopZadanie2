package io.github.nicksementsov.druzhina.client;

import io.github.nicksementsov.druzhina.DruzhinaConfig;
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

/** The muster table: who can be hired, what each costs, and a button to hire him. */
public class MusterScreen extends Screen {
    private static final int ROW = 36;
    private static final int BG = 0xE0201812;
    private static final int FRAME = 0xFF8B6B3E;
    private static final int GOLD = 0xFFE8C766;
    /** The colour of the purse and of prices the player can pay. */
    private static final int COIN = 0xFFF5D255;

    private final BlockPos table;
    private final Role[] roles = Role.forHire();
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
        this.panelWidth = Math.min(380, this.width - 16);
        this.panelHeight = 52 + this.roles.length * ROW + 40;
        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);
        this.buttons.clear();
        for (int i = 0; i < this.roles.length; i++) {
            Role role = this.roles[i];
            int y = this.top + 44 + i * ROW;
            Button b = Button.builder(Component.translatable("druzhina.gui.hire"), btn -> this.hire(role))
                    .bounds(this.left + this.panelWidth - 68, y + 7, 60, 20)
                    .build();
            this.buttons.add(this.addRenderableWidget(b));
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
                .bounds(this.left + this.panelWidth / 2 - 40, this.top + this.panelHeight - 26, 80, 20).build());
        this.refresh();
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
        boolean full = this.army(player) >= DruzhinaConfig.MAX_SOLDIERS.get();
        for (int i = 0; i < this.buttons.size(); i++) {
            this.buttons.get(i).active = !full && (free || gold >= Army.price(this.roles[i]));
        }
    }

    /** The player's soldiers this client knows of. */
    private int army(Player player) {
        if (player.level() == null) {
            return 0;
        }
        int n = 0;
        for (var e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (e instanceof Soldier s && player.getUUID().equals(s.getOwnerUUID())) {
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
        int gold = player == null ? 0 : Army.gold(player);
        boolean free = player != null && player.getAbilities().instabuild;
        if (player != null) {
            g.renderItem(new ItemStack(Items.GOLD_INGOT), x + 8, y + 20);
            g.drawString(this.font, Component.translatable("druzhina.gui.purse", gold), x + 28, y + 25, COIN);
            Component army = Component.translatable("druzhina.gui.army", this.army(player), DruzhinaConfig.MAX_SOLDIERS.get());
            g.drawString(this.font, army, x + w - 8 - this.font.width(army), y + 25, 0xFFD8D0C0);
        }

        int textWidth = w - 32 - 116;
        for (int i = 0; i < this.roles.length; i++) {
            Role role = this.roles[i];
            int ry = y + 44 + i * ROW;
            g.fill(x + 4, ry, x + w - 4, ry + ROW - 2, i % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
            g.renderItem(role.icon(), x + 10, ry + 9);
            Component name = Component.translatable("druzhina.role." + role.id()).withStyle(ChatFormatting.BOLD);
            g.drawString(this.font, name, x + 32, ry + 3, GOLD);
            g.drawString(this.font, Component.translatable("druzhina.gui.stats", Math.round(role.health), Math.round(role.skill * 100)),
                    x + 32 + this.font.width(name) + 8, ry + 3, 0xFFB0A898);
            List<FormattedCharSequence> lines = this.font.split(Component.translatable("druzhina.role." + role.id() + ".desc"), textWidth);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                g.drawString(this.font, lines.get(l), x + 32, ry + 14 + l * 10, 0xFFD8D0C0);
            }
            // the price in gold ingots, red when the purse is too thin
            int price = Army.price(role);
            String text = String.valueOf(price);
            int px = x + w - 74 - this.font.width(text);
            g.renderItem(new ItemStack(Items.GOLD_INGOT), px - 18, ry + 9);
            g.drawString(this.font, text, px, ry + 13, free || gold >= price ? COIN : 0xFFE06060);
        }

        g.drawCenteredString(this.font, Component.translatable("druzhina.gui.baton_hint"), x + w / 2, y + this.panelHeight - 38, 0xFFA09888);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
