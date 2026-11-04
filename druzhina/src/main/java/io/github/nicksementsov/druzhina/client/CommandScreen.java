package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nicksementsov.druzhina.entity.Formation;
import io.github.nicksementsov.druzhina.entity.Role;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.network.CommandPacket;
import io.github.nicksementsov.druzhina.network.DruzhinaNetwork;
import io.github.nicksementsov.druzhina.server.Army;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The commander's view (see {@link CommandView}): a screen without a background over the field, with a panel at the
 * bottom. The left mouse button picks out men (alone, with Shift one more, a frame of them, a double click all of one
 * role in sight, a card of the panel all of one role); the right one sends them: to a place, where they march in
 * formation and stand (dragged, it draws the front: how wide and which way), or at an enemy, or aboard a ship. With
 * Shift the order waits its turn after the others, with Ctrl they fight their way there, with Alt they patrol there.
 * Ctrl+1..9 make groups, 1..9 call them up again.
 */
public class CommandScreen extends Screen {
    private static final int DRAG = 4;
    private static final int GREEN = 0xFF7CE07C;
    private static final int GOLD = 0xFFE8C766;
    private static final int PANEL = 44;
    private static final int CARD_W = 26;
    private static final int CARD_H = 36;
    private static final int BUTTON_H = 15;
    private static final long DOUBLE = 350;

    /** What the next right click on the ground does, when it is not a plain march. */
    private enum Mode { MARCH, FIGHT, PATROL }

    private Mode mode = Mode.MARCH;
    private boolean help;

    private double leftX = -1;
    private double leftY;
    private double mouseNowX;
    private double mouseNowY;
    private boolean boxing;
    private boolean turning;

    @Nullable
    private Vec3 rightFrom;
    @Nullable
    private LivingEntity rightFoe;
    /** A boat or ship the right button went down on. */
    @Nullable
    private Entity rightShip;
    private double rightX;
    private double rightY;
    private boolean rightDown;
    private boolean rightDragged;

    private int lastClickId = -1;
    private long lastClickAt;
    private int lastGroup = -1;
    private long lastGroupAt;
    @Nullable
    private Role lastCard;
    private long lastCardAt;

    /** The buttons and cards of the panel, as last drawn. */
    private final List<Button> buttons = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final List<int[]> groupBoxes = new ArrayList<>();

    private record Button(int x, int y, int w, Component label, Component tip, Runnable action, boolean lit) {
        boolean over(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + BUTTON_H;
        }
    }

    private record Card(int x, int y, Role role, List<Soldier> men, int picked) {
        boolean over(double mx, double my) {
            return mx >= this.x && mx < this.x + CARD_W && my >= this.y && my < this.y + CARD_H;
        }
    }

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

    /** Whether a mouse button is held down with a drag going on (then the edges of the window do not move the camera). */
    boolean dragging() {
        return this.boxing || this.turning || this.rightDragged;
    }

    private int panelTop() {
        return this.height - PANEL;
    }

    // ---------------------------------------------------------------- the mouse

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.help) {
            this.help = false;
            return true;
        }
        if (my >= this.panelTop() - 14 && this.clickPanel(mx, my, button)) {
            return true;
        }
        if (my >= this.panelTop()) {
            return true;
        }
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
            this.rightDown = true;
            this.rightDragged = false;
            this.rightFoe = pick != null && pick.entity() != null && this.foe(pick.entity()) ? pick.entity() : null;
            this.rightShip = pick != null && this.rightFoe == null ? pick.vessel() : null;
            this.rightFrom = pick == null ? null : pick.point();
            if (this.rightFrom != null && this.rightFoe == null && this.rightShip == null && !this.patrolling()) {
                // where they would stand if he let go now
                List<Soldier> men = CommandView.selected();
                if (!men.isEmpty()) {
                    Front front = this.clickFront(men, this.rightFrom);
                    CommandView.preview = layout(men, front);
                }
            }
            return true;
        }
        if (button == 2) {
            this.turning = true;
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
        } else if (button == 1 && this.rightDown && this.rightFrom != null && this.rightFoe == null && this.rightShip == null
                && !this.patrolling() && (Math.abs(mx - this.rightX) > DRAG || Math.abs(my - this.rightY) > DRAG)) {
            this.rightDragged = true;
            CommandView.Pick pick = CommandView.pick(mx, my);
            if (pick != null && pick.point() != null) {
                Front front = this.front(this.rightFrom, pick.point());
                CommandView.preview = layout(CommandView.selected(), front);
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
        if (button == 1 && this.rightDown) {
            this.order(mx, my);
            CommandView.preview = List.of();
            CommandView.previewFrom = null;
            CommandView.previewTo = null;
            this.rightFrom = null;
            this.rightFoe = null;
            this.rightShip = null;
            this.rightDown = false;
            this.rightDragged = false;
            return true;
        }
        if (button == 2) {
            this.turning = false;
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

    /**
     * Whom a right click attacks: monsters, bandits, other commanders' soldiers and players not allied to him. His own
     * men, their horses and elephants, his tamed beasts, animals and villagers are not foes (a click on them is a
     * click on the ground where they stand).
     */
    private boolean foe(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e == mc.player || CommandView.mine(e) || e.isAlliedTo(mc.player) || mc.player.isAlliedTo(e)) {
            return false;
        }
        if (e.getPassengers().stream().anyMatch(CommandView::mine)
                || e instanceof net.minecraft.world.entity.OwnableEntity own && mc.player.getUUID().equals(own.getOwnerUUID())) {
            return false;
        }
        if (e instanceof Soldier s) {
            return s.isBandit() || !mc.player.getUUID().equals(s.getOwnerUUID());
        }
        return e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof net.minecraft.world.entity.player.Player;
    }

    private boolean patrolling() {
        return this.mode == Mode.PATROL || hasAltDown();
    }

    private boolean fighting() {
        return this.mode == Mode.FIGHT || hasControlDown();
    }

    // ---------------------------------------------------------------- the panel

    private boolean clickPanel(double mx, double my, int button) {
        for (Button b : this.buttons) {
            if (b.over(mx, my)) {
                if (button == 0) {
                    b.action().run();
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        for (Card c : this.cards) {
            if (c.over(mx, my)) {
                long now = System.currentTimeMillis();
                if (button == 0) {
                    if (c.role() == this.lastCard && now - this.lastCardAt < DOUBLE) {
                        CommandView.focus(CommandView.middle(c.men()));
                    } else if (hasShiftDown()) {
                        // Shift: those of this role in, or out if all are in already
                        boolean all = c.picked() == c.men().size();
                        for (Soldier s : c.men()) {
                            if (all) {
                                CommandView.SELECTED.remove(s.getId());
                            } else {
                                CommandView.SELECTED.add(s.getId());
                            }
                        }
                    } else {
                        CommandView.SELECTED.clear();
                        c.men().forEach(s -> CommandView.SELECTED.add(s.getId()));
                    }
                    this.lastCard = c.role();
                    this.lastCardAt = now;
                } else if (button == 1) {
                    // right click on a card: only these out of the men picked out
                    c.men().forEach(s -> CommandView.SELECTED.remove(s.getId()));
                }
                return true;
            }
        }
        for (int[] g : this.groupBoxes) {
            if (mx >= g[1] && mx < g[1] + g[3] && my >= g[2] && my < g[2] + 12) {
                if (button == 0) {
                    this.callGroup(g[0]);
                }
                return true;
            }
        }
        return false;
    }

    private void layoutPanel(List<Soldier> army, List<Soldier> picked) {
        this.buttons.clear();
        this.cards.clear();
        int top = this.panelTop();
        // the orders and the formations, at the right
        List<Button> orders = new ArrayList<>();
        boolean any = !picked.isEmpty();
        long keeping = picked.stream().filter(Soldier::keepRanks).count();
        orders.add(this.button("hold", () -> this.simple(Army.Command.HOLD), false));
        orders.add(this.button("follow", () -> this.simple(Army.Command.FOLLOW), false));
        orders.add(this.button("charge", () -> this.simple(Army.Command.CHARGE), false));
        orders.add(this.button("fight", () -> this.mode = this.mode == Mode.FIGHT ? Mode.MARCH : Mode.FIGHT, this.mode == Mode.FIGHT));
        orders.add(this.button("patrol", () -> this.mode = this.mode == Mode.PATROL ? Mode.MARCH : Mode.PATROL, this.mode == Mode.PATROL));
        orders.add(this.button("ranks", this::toggleRanks, any && keeping == picked.size()));
        List<Button> forms = new ArrayList<>();
        for (Formation f : Formation.values()) {
            forms.add(this.formationButton(f));
        }
        forms.add(this.button("all", this::selectAll, false));
        forms.add(this.button("help", () -> this.help = !this.help, this.help));
        int rowW = Math.max(width(orders), width(forms));
        int x0 = this.width - rowW - 4;
        this.place(orders, x0, top + 5);
        this.place(forms, x0, top + 5 + BUTTON_H + 4);

        // a card for each kind of man in the army
        Map<Role, List<Soldier>> byRole = new EnumMap<>(Role.class);
        for (Soldier s : army) {
            byRole.computeIfAbsent(s.role(), r -> new ArrayList<>()).add(s);
        }
        Set<Integer> ids = new LinkedHashSet<>();
        picked.forEach(s -> ids.add(s.getId()));
        int x = 4;
        for (Map.Entry<Role, List<Soldier>> e : byRole.entrySet()) {
            if (x + CARD_W > x0 - 6) {
                break;
            }
            int n = (int) e.getValue().stream().filter(s -> ids.contains(s.getId())).count();
            this.cards.add(new Card(x, top + 4, e.getKey(), e.getValue(), n));
            x += CARD_W + 2;
        }
    }

    private static int width(List<Button> row) {
        int w = 0;
        for (Button b : row) {
            w += b.w() + 2;
        }
        return w;
    }

    private void place(List<Button> row, int x, int y) {
        for (Button b : row) {
            this.buttons.add(new Button(x, y, b.w(), b.label(), b.tip(), b.action(), b.lit()));
            x += b.w() + 2;
        }
    }

    private Button button(String id, Runnable action, boolean lit) {
        Component label = Component.translatable("druzhina.view.button." + id);
        return new Button(0, 0, this.font.width(label) + 8, label, Component.translatable("druzhina.view.button." + id + ".tip"), action, lit);
    }

    private Button formationButton(Formation f) {
        Component label = Component.translatable("druzhina.view.form." + f.id());
        return new Button(0, 0, this.font.width(label) + 8, label, Component.translatable("druzhina.formation." + f.id() + ".tip"),
                () -> CommandView.formation = f, CommandView.formation == f);
    }

    // ---------------------------------------------------------------- picking men

    private void selectAt(double mx, double my) {
        boolean add = hasShiftDown();
        CommandView.Pick pick = CommandView.pick(mx, my);
        Entity e = pick == null ? null : pick.entity();
        if (e != null && CommandView.mine(e)) {
            Soldier one = (Soldier) e;
            long now = System.currentTimeMillis();
            if (hasControlDown()) {
                // Ctrl: every man of his role in the army
                if (!add) {
                    CommandView.SELECTED.clear();
                }
                CommandView.army().stream().filter(s -> s.role() == one.role()).forEach(s -> CommandView.SELECTED.add(s.getId()));
            } else if (e.getId() == this.lastClickId && now - this.lastClickAt < DOUBLE) {
                // a double click: all of his role in sight
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
        List<Soldier> out = new ArrayList<>();
        for (Soldier s : CommandView.army()) {
            double[] p = CommandView.project(s.position().add(0, s.getBbHeight() / 2, 0));
            if (p != null && p[0] >= 0 && p[0] <= this.width && p[1] >= 0 && p[1] <= this.panelTop()) {
                out.add(s);
            }
        }
        return out;
    }

    private void selectAll() {
        CommandView.SELECTED.clear();
        CommandView.army().forEach(s -> CommandView.SELECTED.add(s.getId()));
    }

    private void callGroup(int n) {
        Set<Integer> group = CommandView.GROUPS.get(n);
        if (group == null || group.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!hasShiftDown()) {
            CommandView.SELECTED.clear();
        }
        CommandView.SELECTED.addAll(group);
        List<Soldier> men = CommandView.selected();
        if (n == this.lastGroup && now - this.lastGroupAt < DOUBLE && !men.isEmpty()) {
            // twice: the camera goes to them
            CommandView.focus(CommandView.middle(men));
        }
        this.lastGroup = n;
        this.lastGroupAt = now;
    }

    private void makeGroup(int n) {
        Set<Integer> ids = new LinkedHashSet<>();
        CommandView.selected().forEach(s -> ids.add(s.getId()));
        if (ids.isEmpty()) {
            CommandView.GROUPS.remove(n);
            return;
        }
        // a man is in one group at a time
        CommandView.GROUPS.values().forEach(g -> g.removeAll(ids));
        CommandView.GROUPS.put(n, ids);
        Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("druzhina.view.group_made", n, ids.size()).withStyle(ChatFormatting.GOLD), false);
    }

    private static int groupOf(int id) {
        for (Map.Entry<Integer, Set<Integer>> e : CommandView.GROUPS.entrySet()) {
            if (e.getValue().contains(id)) {
                return e.getKey();
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- orders

    /** The front a line is drawn for: its middle, its width, and the way it faces. */
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

    /** A click without a drag: they face the way they go there (from where they are, or from their last stage). */
    private Front clickFront(List<Soldier> men, Vec3 to) {
        Vec3 from = CommandView.middle(men);
        if (hasShiftDown()) {
            for (CommandView.Route r : CommandView.routes().keySet()) {
                from = r.points().get(r.points().size() - 1);
            }
        }
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        float yaw = dx * dx + dz * dz > 4 ? (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) : CommandView.cameraYaw();
        return new Front(to, yaw, 0);
    }

    /** Where {@code men} would stand: on foot in front, riders behind (as the server places them). */
    private static List<Vec3> layout(List<Soldier> men, Front front) {
        int horse = (int) men.stream().filter(s -> s.steed() != null).count();
        int foot = Math.max(0, men.size() - horse);
        if (foot + horse == 0) {
            foot = 1;
        }
        return CommandView.formation.slots(foot, horse, front.center, front.yaw, front.width);
    }

    private void order(double mx, double my) {
        List<Soldier> men = CommandView.selected();
        if (men.isEmpty()) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("druzhina.view.pick_first").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        int[] ids = men.stream().mapToInt(Entity::getId).toArray();
        int flags = (hasShiftDown() ? Army.QUEUE : 0) | (this.fighting() ? Army.AGGRESSIVE : 0);
        if (this.rightFoe != null && !this.rightDragged) {
            send(new CommandPacket(ids, Army.Command.ATTACK.ordinal(), this.rightFoe.position(), 0, 0, 0, this.rightFoe.getId(), 0));
            return;
        }
        if (this.rightShip != null && !this.rightDragged) {
            // a ship: they go aboard her
            send(new CommandPacket(ids, Army.Command.ATTACK.ordinal(), this.rightShip.position(), 0, 0, 0, this.rightShip.getId(), 0));
            return;
        }
        if (this.rightFrom == null) {
            return;
        }
        if (this.patrolling()) {
            send(new CommandPacket(ids, Army.Command.PATROL.ordinal(), this.rightFrom, 0, 0, 0, -1, flags & Army.QUEUE));
            CommandView.showOrder(List.of(this.rightFrom));
        } else {
            Front front;
            CommandView.Pick end = this.rightDragged ? CommandView.pick(mx, my) : null;
            if (end != null && end.point() != null && horizontal(this.rightFrom, end.point()) > 2) {
                front = this.front(this.rightFrom, end.point());
            } else {
                front = this.clickFront(men, this.rightFrom);
            }
            send(new CommandPacket(ids, Army.Command.MOVE.ordinal(), front.center, front.yaw, (float) front.width, CommandView.formation.ordinal(), -1, flags));
            CommandView.showOrder(layout(men, front));
        }
        if (!hasShiftDown()) {
            this.mode = Mode.MARCH;
        }
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
        send(new CommandPacket(men.stream().mapToInt(Entity::getId).toArray(), command.ordinal(), men.get(0).position(), 0, 0, 0, -1, 0));
    }

    /** Close the ranks, or (when all of them keep them already) let them fight freely. */
    private void toggleRanks() {
        List<Soldier> men = CommandView.selected();
        if (men.isEmpty()) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("druzhina.view.pick_first").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        boolean keep = !men.stream().allMatch(Soldier::keepRanks);
        send(new CommandPacket(men.stream().mapToInt(Entity::getId).toArray(), Army.Command.STANCE.ordinal(), men.get(0).position(), 0, 0, 0,
                keep ? 1 : 0, 0));
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (DruzhinaKeys.COMMAND.matches(key, scan)) {
            this.onClose();
            return true;
        }
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
            int n = key - InputConstants.KEY_0;
            if (hasControlDown()) {
                this.makeGroup(n);
            } else {
                this.callGroup(n);
            }
            return true;
        }
        switch (key) {
            case InputConstants.KEY_H -> this.simple(Army.Command.HOLD);
            case InputConstants.KEY_F -> this.simple(Army.Command.FOLLOW);
            case InputConstants.KEY_C -> this.simple(Army.Command.CHARGE);
            case InputConstants.KEY_B -> this.toggleRanks();
            case InputConstants.KEY_R -> this.mode = this.mode == Mode.FIGHT ? Mode.MARCH : Mode.FIGHT;
            case InputConstants.KEY_P -> this.mode = this.mode == Mode.PATROL ? Mode.MARCH : Mode.PATROL;
            case InputConstants.KEY_Z -> CommandView.formation = hasShiftDown() ? Formation.byId((CommandView.formation.ordinal() + Formation.values().length - 1)
                    % Formation.values().length) : CommandView.formation.next();
            case InputConstants.KEY_TAB -> {
                List<Soldier> men = CommandView.selected();
                Minecraft mc = Minecraft.getInstance();
                if (!men.isEmpty()) {
                    CommandView.focus(CommandView.middle(men));
                } else if (mc.player != null) {
                    CommandView.focus(mc.player.position());
                }
            }
            case InputConstants.KEY_SLASH -> this.help = !this.help;
            case InputConstants.KEY_A -> {
                if (hasControlDown()) {
                    this.selectAll();
                }
            }
            case InputConstants.KEY_ESCAPE -> {
                if (this.help) {
                    this.help = false;
                } else if (this.mode != Mode.MARCH) {
                    this.mode = Mode.MARCH;
                } else {
                    this.onClose();
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
        if (this.mouseNowY >= this.panelTop() - 14) {
            CommandView.hovered = null;
            CommandView.hoveredVessel = null;
            return;
        }
        CommandView.Pick pick = CommandView.pick(this.mouseNowX, this.mouseNowY);
        CommandView.hovered = pick == null ? null : pick.entity();
        CommandView.hoveredVessel = pick == null ? null : pick.vessel();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseNowX = mouseX;
        this.mouseNowY = mouseY;
        List<Soldier> army = CommandView.army();
        List<Soldier> men = CommandView.selected();
        this.layoutPanel(army, men);

        // health over the men picked out (and their group), and over the one under the mouse
        for (Soldier s : men) {
            this.healthBar(g, s, partialTick, groupOf(s.getId()));
        }
        LivingEntity hover = CommandView.hovered;
        if (hover != null && !men.contains(hover)) {
            this.healthBar(g, hover, partialTick, 0);
        }
        if (hover != null) {
            double[] p = CommandView.project(hover.getPosition(partialTick).add(0, hover.getBbHeight() + 0.75, 0));
            if (p != null) {
                g.drawCenteredString(this.font, hover.getDisplayName(), (int) p[0], (int) p[1] - 10, this.foe(hover) ? 0xFFFF7070 : 0xFFFFFFFF);
            }
        }
        // the numbers of the stages of the marches
        for (CommandView.Route r : CommandView.routes().keySet()) {
            int i = 1;
            for (Vec3 v : r.points()) {
                double[] p = CommandView.project(v.add(0, 0.6, 0));
                if (p != null && r.points().size() > 1) {
                    g.drawCenteredString(this.font, String.valueOf(i), (int) p[0], (int) p[1] - 4, GOLD);
                }
                i++;
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

        this.topBar(g, army, men);
        this.panel(g, mouseX, mouseY);
        if (mouseY < this.panelTop() - 14) {
            this.cursorHint(g, mouseX, mouseY, men);
        }
        if (this.help) {
            this.helpBox(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void topBar(GuiGraphics g, List<Soldier> army, List<Soldier> men) {
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        Component state = Component.translatable("druzhina.view.selected", men.size(), army.size()).append("  ·  ")
                .append(Component.translatable("druzhina.view.formation", Component.translatable("druzhina.formation." + CommandView.formation.id())));
        if (!men.isEmpty()) {
            long keeping = men.stream().filter(Soldier::keepRanks).count();
            String ranks = keeping == 0 ? "free" : keeping == men.size() ? "keep" : "some";
            state = state.copy().append("  ·  ").append(Component.translatable("druzhina.view.ranks." + ranks));
        }
        int w = Math.max(this.font.width(title), this.font.width(state)) + 16;
        g.fill(4, 4, 4 + w, 28, 0xA0201812);
        g.renderOutline(4, 4, w, 24, 0x60E8C766);
        g.drawString(this.font, title, 12, 7, GOLD);
        g.drawString(this.font, state, 12, 17, GREEN);
        if (army.isEmpty()) {
            g.drawString(this.font, Component.translatable("druzhina.view.none"), 8, 32, 0xFFE0A0A0);
        }
        Component hint = Component.translatable("druzhina.view.help_hint");
        g.drawString(this.font, hint, this.width - this.font.width(hint) - 6, 6, 0xB0D8D0C0);
    }

    private void panel(GuiGraphics g, int mouseX, int mouseY) {
        int top = this.panelTop();
        g.fill(0, top, this.width, this.height, 0xC0181410);
        g.fill(0, top, this.width, top + 1, 0xA0E8C766);

        // the groups, over the panel
        this.groupBoxes.clear();
        int gx = 4;
        for (int n = 1; n <= 9; n++) {
            Set<Integer> group = CommandView.GROUPS.get(n);
            if (group == null || group.isEmpty()) {
                continue;
            }
            Minecraft mc = Minecraft.getInstance();
            int alive = 0;
            for (int id : group) {
                if (mc.level != null && mc.level.getEntity(id) instanceof Soldier s && CommandView.mine(s)) {
                    alive++;
                }
            }
            if (alive == 0) {
                continue;
            }
            String text = n + "·" + alive;
            int w = this.font.width(text) + 6;
            boolean picked = CommandView.SELECTED.containsAll(group);
            g.fill(gx, top - 13, gx + w, top - 1, picked ? 0xD0405828 : 0xC0282018);
            g.renderOutline(gx, top - 13, w, 12, picked ? 0xFF7CE07C : 0x80E8C766);
            g.drawString(this.font, text, gx + 3, top - 11, picked ? 0xFFFFFFFF : 0xFFD8D0C0);
            this.groupBoxes.add(new int[]{n, gx, top - 13, w});
            gx += w + 2;
        }

        Card tipCard = null;
        for (Card c : this.cards) {
            boolean some = c.picked() > 0;
            boolean all = c.picked() == c.men().size();
            g.fill(c.x(), c.y(), c.x() + CARD_W, c.y() + CARD_H, all ? 0xC0405828 : some ? 0xC0353020 : 0xC0242018);
            g.renderOutline(c.x(), c.y(), CARD_W, CARD_H, all ? 0xFF7CE07C : some ? 0xFFE8C766 : 0x60A09080);
            ItemStack icon = c.men().get(0).getMainHandItem();
            g.renderItem(icon.isEmpty() ? new ItemStack(Items.IRON_SWORD) : icon, c.x() + 5, c.y() + 2);
            String count = some ? c.picked() + "/" + c.men().size() : String.valueOf(c.men().size());
            g.drawCenteredString(this.font, count, c.x() + CARD_W / 2, c.y() + 20, some ? 0xFFFFFFFF : 0xFFB0A898);
            float health = 0;
            for (Soldier s : c.men()) {
                health += Mth.clamp(s.getHealth() / s.getMaxHealth(), 0, 1);
            }
            health /= c.men().size();
            int color = health > 0.6f ? 0xFF55DD55 : health > 0.3f ? 0xFFE8C040 : 0xFFE04040;
            g.fill(c.x() + 3, c.y() + CARD_H - 5, c.x() + CARD_W - 3, c.y() + CARD_H - 3, 0xC0000000);
            g.fill(c.x() + 3, c.y() + CARD_H - 5, c.x() + 3 + Math.round((CARD_W - 6) * health), c.y() + CARD_H - 3, color);
            if (c.over(mouseX, mouseY)) {
                tipCard = c;
            }
        }
        Button tipButton = null;
        for (Button b : this.buttons) {
            boolean over = b.over(mouseX, mouseY);
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + BUTTON_H, b.lit() ? 0xE0506030 : over ? 0xE0484030 : 0xE02C261E);
            g.renderOutline(b.x(), b.y(), b.w(), BUTTON_H, b.lit() ? 0xFF9CE07C : over ? 0xFFE8C766 : 0x80A09080);
            g.drawCenteredString(this.font, b.label(), b.x() + b.w() / 2, b.y() + 4, b.lit() ? 0xFFFFFFFF : 0xFFE0D8C8);
            if (over) {
                tipButton = b;
            }
        }
        if (tipCard != null) {
            Soldier one = tipCard.men().get(0);
            int pct = 0;
            for (Soldier s : tipCard.men()) {
                pct += Math.round(100 * Mth.clamp(s.getHealth() / s.getMaxHealth(), 0, 1));
            }
            pct /= tipCard.men().size();
            List<Component> lines = List.of(
                Component.translatable("druzhina.role." + one.role().id()).withStyle(ChatFormatting.GOLD),
                Component.translatable("druzhina.view.card.count", tipCard.picked(), tipCard.men().size()),
                Component.translatable("druzhina.view.card.health", pct),
                Component.translatable("druzhina.view.card.tip").withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        } else if (tipButton != null) {
            List<FormattedCharSequence> lines = this.font.split(tipButton.tip(), 220);
            g.renderTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private void cursorHint(GuiGraphics g, int mouseX, int mouseY, List<Soldier> men) {
        if (men.isEmpty()) {
            return;
        }
        Component hint = null;
        int color = 0xFFFFFFFF;
        if (CommandView.hovered != null && this.foe(CommandView.hovered)) {
            hint = Component.translatable("druzhina.view.cursor.attack");
            color = 0xFFFF7070;
        } else if (CommandView.hoveredVessel != null) {
            hint = Component.translatable("druzhina.view.cursor.board");
            color = 0xFF80C0FF;
        } else if (this.patrolling()) {
            hint = Component.translatable("druzhina.view.cursor.patrol");
            color = 0xFF80E0E0;
        } else if (this.fighting()) {
            hint = Component.translatable("druzhina.view.cursor.fight");
            color = 0xFFFFA060;
        }
        if (hasShiftDown()) {
            Component queued = Component.translatable("druzhina.view.cursor.queue");
            hint = hint == null ? queued : hint.copy().append(" ").append(queued);
        }
        if (hint != null) {
            g.drawString(this.font, hint, mouseX + 10, mouseY + 8, color);
        }
    }

    private void helpBox(GuiGraphics g) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        int wrap = Math.min(560, this.width - 40);
        for (int i = 1; i <= 12; i++) {
            String key = "druzhina.view.help." + i;
            lines.addAll(this.font.split(Component.translatable(key), wrap));
        }
        int h = lines.size() * 10 + 20;
        int w = wrap + 16;
        int x = (this.width - w) / 2;
        int y = Mth.clamp((this.panelTop() - h) / 2, 4, Math.max(4, this.panelTop() - h - 4));
        g.fill(x, y, x + w, y + h, 0xE0181410);
        g.renderOutline(x, y, w, h, 0xFFE8C766);
        int ty = y + 10;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 8, ty, 0xFFE8E0D0);
            ty += 10;
        }
    }

    private void healthBar(GuiGraphics g, LivingEntity e, float partialTick, int group) {
        double[] p = CommandView.project(e.getPosition(partialTick).add(0, e.getBbHeight() + 0.35, 0));
        if (p == null || p[1] > this.panelTop() - 16) {
            return;
        }
        int x = (int) p[0] - 10;
        int y = (int) p[1];
        float f = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0, 1);
        int color = f > 0.6f ? 0xFF55DD55 : f > 0.3f ? 0xFFE8C040 : 0xFFE04040;
        g.fill(x - 1, y - 1, x + 21, y + 3, 0xC0000000);
        g.fill(x, y, x + Math.round(20 * f), y + 2, color);
        if (group > 0) {
            g.drawString(this.font, String.valueOf(group), x + 23, y - 3, GOLD);
        }
        if (e instanceof Soldier s && s.keepRanks()) {
            // a small bar under the health: he keeps the ranks
            g.fill(x, y + 4, x + 20, y + 5, 0xC080B0FF);
        }
    }
}
