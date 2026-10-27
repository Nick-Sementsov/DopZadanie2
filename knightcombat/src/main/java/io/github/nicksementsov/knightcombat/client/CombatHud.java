package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.AttackStats;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import io.github.nicksementsov.knightcombat.network.Feedback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

/** Stamina bar, hit marker and short messages around the crosshair, and the combat lines of weapon tooltips. */
@Mod.EventBusSubscriber(modid = KnightCombat.MODID, value = Dist.CLIENT)
public final class CombatHud {
    private static final int BAR_WIDTH = 40;
    private static final int MESSAGE_TICKS = 24;
    private static final int HIT_MARKER_TICKS = 6;

    private CombatHud() {
    }

    /** Drawn above the crosshair layer of the HUD. */
    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) {
            return;
        }
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        int cx = width / 2;
        int cy = height / 2;
        long ticks = ClientCombat.ticks();
        boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON;

        float hit = ticks - ClientCombat.hitAt() + partialTick;
        if (hit < HIT_MARKER_TICKS && firstPerson) {
            int alpha = (int) (255 * (1 - hit / HIT_MARKER_TICKS));
            int color = alpha << 24 | 0xFFFFFF;
            for (int i = 3; i <= 5; i++) {
                graphics.fill(cx - i - 1, cy - i - 1, cx - i, cy - i, color);
                graphics.fill(cx + i, cy - i - 1, cx + i + 1, cy - i, color);
                graphics.fill(cx - i - 1, cy + i, cx - i, cy + i + 1, color);
                graphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
            }
        }

        if (weapon != null && CombatConfig.STAMINA_BAR.get()) {
            drawStamina(graphics, cx, cy + 10, ticks, partialTick);
        }

        if (!CombatConfig.HIT_MESSAGES.get()) {
            return;
        }
        Font font = mc.font;
        if (weapon != null && ClientCombat.riposteReady()) {
            Component text = Component.translatable("hud.knightcombat.riposte_ready");
            graphics.drawString(font, text, cx - font.width(text) / 2, cy - 20, 0xFFE9C46A, true);
        }
        Feedback feedback = ClientCombat.lastFeedback();
        float age = ticks - ClientCombat.feedbackAt() + partialTick;
        if (feedback != null && age < MESSAGE_TICKS) {
            int alpha = Mth.clamp((int) (255 * Math.min(1, (MESSAGE_TICKS - age) / 8f)), 8, 255);
            Component text = Component.translatable("hud.knightcombat." + feedback.name().toLowerCase(Locale.ROOT));
            int y = cy - 32 - (int) (age * 0.4f);
            graphics.drawString(font, text, cx - font.width(text) / 2, y, alpha << 24 | color(feedback), true);
        }
    }

    private static void drawStamina(GuiGraphics graphics, int cx, int y, long ticks, float partialTick) {
        float stamina = ClientCombat.stamina();
        float max = ClientCombat.maxStamina();
        if (stamina < 0 || max <= 0) {
            return;
        }
        float share = Mth.clamp(stamina / max, 0, 1);
        // A full bar fades out a second after it filled up.
        float sinceChange = ticks - ClientCombat.staminaChangedAt() + partialTick;
        float visibility = share < 0.999f ? 1 : 1 - Mth.clamp((sinceChange - 20) / 10f, 0, 1);
        if (visibility <= 0) {
            return;
        }
        int alpha = (int) (visibility * 255);
        int left = cx - BAR_WIDTH / 2;
        graphics.fill(left - 1, y - 1, left + BAR_WIDTH + 1, y + 3, (int) (alpha * 0.6f) << 24);
        int fill = Math.round(BAR_WIDTH * share);
        int color = share < 0.3f ? 0xD9443A : 0xE8B84A;
        if (fill > 0) {
            graphics.fill(left, y, left + fill, y + 2, alpha << 24 | color);
            graphics.fill(left, y, left + fill, y + 1, alpha << 24 | brighter(color));
        }
    }

    private static int brighter(int color) {
        int r = Math.min(255, (color >> 16 & 0xFF) + 40);
        int g = Math.min(255, (color >> 8 & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return r << 16 | g << 8 | b;
    }

    private static int color(Feedback feedback) {
        return switch (feedback) {
            case PARRIED, COUNTERED, BROKE_GUARD, KICK_BREAK -> 0xE9C46A;
            case BLOCKED, BATTLE_CRY -> 0xFFFFFF;
            case GOT_PARRIED, GOT_COUNTERED, GUARD_BROKEN, KICKED -> 0xE76F51;
            default -> 0xC8C8C8;
        };
    }

    // ---------------------------------------------------------------- tooltips

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        WeaponStats weapon = Weapons.of(event.getItemStack());
        if (weapon == null) {
            return;
        }
        List<Component> lines = event.getToolTip();
        lines.add(Component.translatable("tooltip.knightcombat.grip." + weapon.grip().name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GOLD));
        for (AttackType type : new AttackType[]{AttackType.SLASH, AttackType.OVERHEAD, AttackType.STAB}) {
            AttackStats attack = weapon.attack(type);
            lines.add(Component.translatable("tooltip.knightcombat.attack",
                            Component.translatable("tooltip.knightcombat.attack." + type.name().toLowerCase(Locale.ROOT)),
                            number(attack.damage(), 1), number(attack.windup() / 20f, 2))
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        lines.add(Component.translatable("tooltip.knightcombat.reach", number(weapon.reach(), 1)).withStyle(ChatFormatting.DARK_GREEN));
        String block = weapon.blockCost() <= 0.85f ? "good" : weapon.blockCost() <= 1.15f ? "normal" : "poor";
        lines.add(Component.translatable("tooltip.knightcombat.block." + block).withStyle(ChatFormatting.DARK_GREEN));
        if (weapon.armorPiercing() >= 0.3f) {
            lines.add(Component.translatable("tooltip.knightcombat.armor_piercing").withStyle(ChatFormatting.BLUE));
        }
        if (weapon.breaksShields()) {
            lines.add(Component.translatable("tooltip.knightcombat.breaks_shields").withStyle(ChatFormatting.BLUE));
        }
        if (Screen.hasShiftDown()) {
            Options options = Minecraft.getInstance().options;
            lines.add(controls("slash", options.keyAttack.getTranslatedKeyMessage()));
            lines.add(controls("overhead", KeyBindings.OVERHEAD.getTranslatedKeyMessage()));
            lines.add(controls("stab", KeyBindings.STAB.getTranslatedKeyMessage()));
            lines.add(controls("block", options.keyUse.getTranslatedKeyMessage()));
            lines.add(controls("other", KeyBindings.FEINT.getTranslatedKeyMessage(), KeyBindings.KICK.getTranslatedKeyMessage(),
                    KeyBindings.BATTLE_CRY.getTranslatedKeyMessage()));
            lines.add(controls("mine", options.keyAttack.getTranslatedKeyMessage()));
        } else {
            lines.add(Component.translatable("tooltip.knightcombat.controls_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component controls(String key, Object... keys) {
        return Component.translatable("tooltip.knightcombat.controls." + key, keys).withStyle(ChatFormatting.GRAY);
    }

    /** A number with a decimal comma in Russian and a decimal point otherwise; whole numbers without decimals. */
    private static String number(float value, int decimals) {
        String text;
        if (decimals == 1 && Math.abs(value - Math.round(value)) < 0.05f) {
            text = Integer.toString(Math.round(value));
        } else {
            text = String.format(Locale.ROOT, "%." + decimals + "f", value);
        }
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        return language.startsWith("ru") || language.startsWith("uk") || language.startsWith("be") ? text.replace('.', ',') : text;
    }
}
