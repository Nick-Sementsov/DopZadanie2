package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nicksementsov.druzhina.entity.Formation;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.network.CommandPacket;
import io.github.nicksementsov.druzhina.network.DruzhinaNetwork;
import io.github.nicksementsov.druzhina.server.Army;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The commander's view (see {@link CommandView}): a screen without a background over the field. The left mouse
 * button picks out men (alone, with Shift one more, a drag a whole frame of them, a double click all of one role in
 * sight); the right one sends them: to a place, where they stand in formation (dragged, it draws the front: how wide
 * and which way), or at an enemy. Keys give the other orders and choose the formation.
 */
public class CommandScreen extends Screen {
    private static final int DRAG = 4;
    private static final int GREEN = 0xFF7CE07C;
    private static final int GOLD = 0xFFE8C766;

    private double leftX = -1;
    private double leftY;
    private double mouseNowX;
    private double mouseNowY;
    private boolean boxing;

    @Nullable
    private Vec3 rightFrom;
    @Nullable
    private LivingEntity rightFoe;
    private double rightX;
    private double rightY;
    private boolean rightDragged;

    private int lastClickId = -1;
    private long lastClickAt;

    public CommandScreen() {
        super(Component.translatable("druzhina.view.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        CommandView.leave();
    }

    // ---------------------------------------------------------------- the mouse

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            this.leftX = mx;
            this.leftY = my;
            this.boxing = false;
            return true;
        }
        if (button == 1) {
            CommandView.Pick pick = CommandView.pick(mx, my);
            this.rightX = mx;
            this.rightY = my;
            this.rightDragged = false;
            this.rightFoe = pick != null && pick.entity() != null && this.foe(pick.entity()) ? pick.entity() : null;
            this.rightFrom = pick == null ? null : pick.point();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && this.leftX >= 0 && (Math.abs(mx - this.leftX) > DRAG || Math.abs(my - this.leftY) > DRAG)) {
            this.boxing = true;
        } else if (button == 2) {
            CommandView.turn(dx * 0.4, dy * 0.4);
        } else if (button == 1 && this.rightFrom != null && (Math.abs(mx - this.rightX) > DRAG || Math.abs(my - this.rightY) > DRAG)) {
            this.rightDragged = true;
            CommandView.Pick pick = CommandView.pick(mx, my);
            if (pick != null && pick.point() != null) {
                Front front = this.front(this.rightFrom, pick.point());
                CommandView.preview = CommandView.formation.slots(Math.max(1, CommandView.selected().size()), front.center, front.yaw, front.width);
                CommandView.previewFrom = this.rightFrom;
                CommandView.previewTo = pick.point();
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && this.leftX >= 0) {
            if (this.boxing) {
                this.selectBox(Math.min(this.leftX, mx), Math.min(this.leftY, my), Math.max(this.leftX, mx), Math.max(this.leftY, my));
            } else {
                this.selectAt(mx, my);
            }
            this.leftX = -1;
            this.boxing = false;
            return true;
        }
        if (button == 1) {
            this.order(mx, my);
            CommandView.preview = List.of();
            CommandView.previewFrom = null;
            CommandView.previewTo = null;
            this.rightFrom = null;
            this.rightFoe = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        CommandView.zoom(delta);
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        this.mouseNowX = mx;
        this.mouseNowY = my;
    }

    private boolean foe(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        return !CommandView.mine(e) && mc.player != null && e != mc.player && !e.isAlliedTo(mc.player) && !mc.player.isAlliedTo(e)
                && !(e instanceof Soldier s && mc.player.getUUID().equals(s.getOwnerUUID()));
    }

    // ---------------------------------------------------------------- picking men

    private void selectAt(double mx, double my) {
        boolean add = hasShiftDown();
        CommandView.Pick pick = CommandView.pick(mx, my);
        Entity e = pick == null ? null : pick.entity();
        if (e != null && CommandView.mine(e)) {
            long now = System.currentTimeMillis();
            if (e.getId() == this.lastClickId && now - this.lastClickAt < 350) {
                // a double click: all of his role in sight
                Soldier one = (Soldier) e;
                if (!add) {
                    CommandView.SELECTED.clear();
                }
                for (Soldier s : this.inSight()) {
                    if (s.role() == one.role()) {
                        CommandView.SELECTED.add(s.getId());
                    }
                }
            } else if (add) {
                if (!CommandView.SELECTED.remove(e.getId())) {
                    CommandView.SELECTED.add(e.getId());
                }
            } else {
                CommandView.SELECTED.clear();
                CommandView.SELECTED.add(e.getId());
            }
            this.lastClickId = e.getId();
            this.lastClickAt = now;
        } else if (!add) {
            CommandView.SELECTED.clear();
        }
    }

    private void selectBox(double x0, double y0, double x1, double y1) {
        if (!hasShiftDown()) {
            CommandView.SELECTED.clear();
        }
        for (Soldier s : this.inSight()) {
            double[] p = CommandView.project(s.position().add(0, s.getBbHeight() / 2, 0));
            if (p != null && p[0] >= x0 && p[0] <= x1 && p[1] >= y0 && p[1] <= y1) {
                CommandView.SELECTED.add(s.getId());
            }
        }
    }

    /** The player's own men on the screen. */
    private List<Soldier> inSight() {
        Minecraft mc = Minecraft.getInstance();
        List<Soldier> out = new ArrayList<>();
        if (mc.level == null) {
            return out;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (CommandView.mine(e) && e instanceof Soldier s) {
                double[] p = CommandView.project(s.position().add(0, s.getBbHeight() / 2, 0));
                if (p != null && p[0] >= 0 && p[0] <= this.width && p[1] >= 0 && p[1] <= this.height) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private void selectAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        CommandView.SELECTED.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (CommandView.mine(e) && e.distanceToSqr(mc.player) < Army.FIELD * Army.FIELD) {
                CommandView.SELECTED.add(e.getId());
            }
        }
    }

    // ---------------------------------------------------------------- orders

    /** The front a dragged line draws: its middle, its width, and the way it faces (away from the camera). */
    private record Front(Vec3 center, float yaw, double width) {
    }

    private Front front(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) {
            return new Front(a, CommandView.cameraYaw(), 0);
        }
        double nx = -dz / len;
        double nz = dx / len;
        float camYaw = CommandView.cameraYaw();
        double fx = -Mth.sin(camYaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(camYaw * Mth.DEG_TO_RAD);
        if (nx * fx + nz * fz < 0) {
            nx = -nx;
            nz = -nz;
        }
        float yaw = (float) (Mth.atan2(-nx, nz) * Mth.RAD_TO_DEG);
        return new Front(new Vec3((a.x + b.x) / 2, a.y, (a.z + b.z) / 2), yaw, len);
    }

    private void order(double mx, double my) {
        List<Soldier> men = CommandView.selected();
        if (men.isEmpty()) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("druzhina.view.pick_first").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        int[] ids = men.stream().mapToInt(Entity::getId).toArray();
        if (this.rightFoe != null && !this.rightDragged) {
            send(new CommandPacket(ids, Army.Command.ATTACK.ordinal(), this.rightFoe.position(), 0, 0, 0, this.rightFoe.getId()));
            return;
        }
        if (this.rightFrom == null) {
            return;
        }
        Front front;
        CommandView.Pick end = this.rightDragged ? CommandView.pick(mx, my) : null;
        if (end != null && end.point() != null && horizontal(this.rightFrom, end.point()) > 2) {
            front = this.front(this.rightFrom, end.point());
        } else {
            // a click: the men face the way from where they are to where they go
            Vec3 middle = Vec3.ZERO;
            for (Soldier s : men) {
                middle = middle.add(s.position());
            }
            middle = middle.scale(1.0 / men.size());
            double dx = this.rightFrom.x - middle.x;
            double dz = this.rightFrom.z - middle.z;
            float yaw = dx * dx + dz * dz > 4 ? (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) : CommandView.cameraYaw();
            front = new Front(this.rightFrom, yaw, 0);
        }
        send(new CommandPacket(ids, Army.Command.MOVE.ordinal(), front.center, front.yaw, (float) front.width, CommandView.formation.ordinal(), -1));
        CommandView.showOrder(CommandView.formation.slots(men.size(), front.center, front.yaw, front.width));
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void send(CommandPacket packet) {
        DruzhinaNetwork.CHANNEL.sendToServer(packet);
    }

    private void simple(Army.Command command) {
        List<Soldier> men = CommandView.selected();
        if (men.isEmpty()) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("druzhina.view.pick_first").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        send(new CommandPacket(men.stream().mapToInt(Entity::getId).toArray(), command.ordinal(), men.get(0).position(), 0, 0, 0, -1));
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (DruzhinaKeys.COMMAND.matches(key, scan)) {
            this.onClose();
            return true;
        }
        switch (key) {
            case InputConstants.KEY_F -> this.simple(Army.Command.FOLLOW);
            case InputConstants.KEY_H -> this.simple(Army.Command.HOLD);
            case InputConstants.KEY_C -> this.simple(Army.Command.CHARGE);
            case InputConstants.KEY_1 -> CommandView.formation = Formation.LINE;
            case InputConstants.KEY_2 -> CommandView.formation = Formation.COLUMN;
            case InputConstants.KEY_3 -> CommandView.formation = Formation.WEDGE;
            case InputConstants.KEY_A -> {
                if (hasControlDown()) {
                    this.selectAll();
                }
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void tick() {
        CommandView.Pick pick = CommandView.pick(this.mouseNowX, this.mouseNowY);
        CommandView.hovered = pick == null ? null : pick.entity();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseNowX = mouseX;
        this.mouseNowY = mouseY;
        List<Soldier> men = CommandView.selected();

        // health over the men picked out, and over the one under the mouse
        for (Soldier s : men) {
            this.healthBar(g, s, partialTick);
        }
        if (CommandView.hovered != null && !men.contains(CommandView.hovered)) {
            this.healthBar(g, CommandView.hovered, partialTick);
            double[] p = CommandView.project(CommandView.hovered.getPosition(partialTick).add(0, CommandView.hovered.getBbHeight() + 0.75, 0));
            if (p != null) {
                g.drawCenteredString(this.font, CommandView.hovered.getDisplayName(), (int) p[0], (int) p[1] - 10,
                        this.foe(CommandView.hovered) ? 0xFFFF7070 : 0xFFFFFFFF);
            }
        }

        if (this.boxing && this.leftX >= 0) {
            int x0 = (int) Math.min(this.leftX, mouseX);
            int y0 = (int) Math.min(this.leftY, mouseY);
            int x1 = (int) Math.max(this.leftX, mouseX);
            int y1 = (int) Math.max(this.leftY, mouseY);
            g.fill(x0, y0, x1, y1, 0x3044FF44);
            g.renderOutline(x0, y0, x1 - x0, y1 - y0, 0xC044FF44);
        }

        // the bar over the field
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        Component state = Component.translatable("druzhina.view.selected", men.size()).append("   ")
                .append(Component.translatable("druzhina.view.formation", Component.translatable("druzhina.formation." + CommandView.formation.id())));
        int w = Math.max(this.font.width(title), this.font.width(state)) + 24;
        g.fill(this.width / 2 - w / 2, 4, this.width / 2 + w / 2, 30, 0xA0201812);
        g.drawCenteredString(this.font, title, this.width / 2, 8, GOLD);
        g.drawCenteredString(this.font, state, this.width / 2, 19, GREEN);
        if (this.mine() == 0) {
            g.drawCenteredString(this.font, Component.translatable("druzhina.view.none"), this.width / 2, 36, 0xFFE0A0A0);
        }

        String[] hints = {"druzhina.view.hint1", "druzhina.view.hint2", "druzhina.view.hint3"};
        int y = this.height - 12 * hints.length - 4;
        g.fill(0, y - 3, this.width, this.height, 0x90000000);
        for (String h : hints) {
            g.drawCenteredString(this.font, Component.translatable(h), this.width / 2, y, 0xFFD8D0C0);
            y += 12;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int mine() {
        Minecraft mc = Minecraft.getInstance();
        int n = 0;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (CommandView.mine(e)) {
                    n++;
                }
            }
        }
        return n;
    }

    private void healthBar(GuiGraphics g, LivingEntity e, float partialTick) {
        double[] p = CommandView.project(e.getPosition(partialTick).add(0, e.getBbHeight() + 0.35, 0));
        if (p == null) {
            return;
        }
        int x = (int) p[0] - 10;
        int y = (int) p[1];
        float f = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0, 1);
        int color = f > 0.6f ? 0xFF55DD55 : f > 0.3f ? 0xFFE8C040 : 0xFFE04040;
        g.fill(x - 1, y - 1, x + 21, y + 3, 0xC0000000);
        g.fill(x, y, x + Math.round(20 * f), y + 2, color);
    }
}
