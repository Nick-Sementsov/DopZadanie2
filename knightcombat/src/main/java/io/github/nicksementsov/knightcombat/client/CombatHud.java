package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.AttackStats;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import io.github.nicksementsov.knightcombat.network.Feedback;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    /** The stamina bar texture: the sword, then the gold, red and white fills of its blade, each 76x11. */
    private static final ResourceLocation STAMINA_BAR = KnightCombat.id("textures/gui/stamina_bar.png");
    private static final int BAR_WIDTH = 76;
    private static final int BAR_HEIGHT = 11;
    /** First column of the blade; left of it are the pommel, the grip and the guard. */
    private static final int BLADE_START = 13;
    private static final int MESSAGE_TICKS = 24;
    private static final int HIT_MARKER_TICKS = 6;

    private static float shownShare = -1;
    private static float spentShare;
    private static long spentAt;
    private static long lastFrame;

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
            drawStamina(graphics, cx, cy + 12, ticks, partialTick);
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

    /**
     * The stamina bar: a sword whose blade fills with gold. Stamina just spent stays white for a moment and then
     * drains away, low stamina turns the blade red and pulses, and an empty bar flashes. A full bar fades out.
     */
    private static void drawStamina(GuiGraphics graphics, int cx, int y, long ticks, float partialTick) {
        float stamina = ClientCombat.stamina();
        float max = ClientCombat.maxStamina();
        if (stamina < 0 || max <= 0) {
            return;
        }
        float share = Mth.clamp(stamina / max, 0, 1);
        long now = Util.getMillis();
        float seconds = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        // The gold follows the stamina quickly; the white part of spent stamina waits, then drains slowly.
        shownShare = shownShare < 0 ? share : shownShare + (share - shownShare) * Math.min(1, seconds * 14);
        if (share >= spentShare) {
            spentShare = share;
            spentAt = now;
        } else if (now - spentAt > 350) {
            spentShare = Math.max(share, spentShare - seconds * 0.9f);
        }
        float sinceChange = ticks - ClientCombat.staminaChangedAt() + partialTick;
        float visibility = share < 0.999f || spentShare > share + 0.001f ? 1 : 1 - Mth.clamp((sinceChange - 20) / 10f, 0, 1);
        if (visibility <= 0) {
            return;
        }
        float time = ticks + partialTick;
        int left = cx - BAR_WIDTH / 2;
        RenderSystem.enableBlend();
        if (share < 0.02f) {
            // Out of stamina: the whole sword flashes red.
            float flash = 0.5f + 0.5f * Mth.sin(time * 0.8f);
            graphics.setColor(1, 1 - 0.6f * flash, 1 - 0.6f * flash, visibility);
        } else {
            graphics.setColor(1, 1, 1, visibility);
        }
        graphics.blit(STAMINA_BAR, left, y, 0, 0, BAR_WIDTH, BAR_HEIGHT, 128, 64);
        graphics.setColor(1, 1, 1, visibility);
        int spentWidth = bladeWidth(spentShare);
        if (spentWidth > bladeWidth(shownShare)) {
            graphics.blit(STAMINA_BAR, left, y, 0, 48, spentWidth, BAR_HEIGHT, 128, 64);
        }
        int width = bladeWidth(shownShare);
        if (width > BLADE_START) {
            boolean low = share < 0.3f;
            if (low) {
                graphics.setColor(1, 1, 1, visibility * (0.75f + 0.25f * Mth.sin(time * 0.5f)));
            }
            graphics.blit(STAMINA_BAR, left, y, 0, low ? 32 : 16, width, BAR_HEIGHT, 128, 64);
            graphics.setColor(1, 1, 1, visibility);
            if (sinceChange < 12 && share > 0.05f && !low) {
                // A glint runs along the blade while stamina comes back.
                int glint = left + BLADE_START + (int) ((time * 3.5f) % (width - BLADE_START + 6)) - 3;
                int from = Math.max(left + BLADE_START, glint);
                int to = Math.min(left + width, glint + 2);
                if (to > from) {
                    graphics.fill(from, y + 4, to, y + 7, (int) (visibility * 150) << 24 | 0xFFFFFF);
                }
            }
        }
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** Width of the bar texture to draw so that the blade is filled up to {@code share}. */
    private static int bladeWidth(float share) {
        return BLADE_START + Math.round((BAR_WIDTH - 1 - BLADE_START) * Mth.clamp(share, 0, 1));
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
        lines.add(Component.translatable("tooltip.knightcombat.weapon",
                Component.translatable("tooltip.knightcombat.kind." + weapon.kind().name().toLowerCase(Locale.ROOT)),
                Component.translatable("tooltip.knightcombat.grip." + weapon.grip().name().toLowerCase(Locale.ROOT)))
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
