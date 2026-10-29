package com.nick.prestol.army;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.research.util.ResearchConstants;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.decree.DecreeData;
import com.nick.prestol.decree.Decrees;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * War decrees and laws (Mobilize, Levy, Medic) were written for MineColonies guards. With garrisons the
 * guards are Hundred Years War soldiers, so the same bonuses go onto them as attribute modifiers.
 */
final class DecreeBonus {
   private static final UUID HEALTH = UUID.fromString("7d0f0b8e-5a5c-4f43-9f2e-3a1d9c6e1a01");
   private static final UUID MELEE = UUID.fromString("7d0f0b8e-5a5c-4f43-9f2e-3a1d9c6e1a02");
   private static final UUID RANGED = UUID.fromString("7d0f0b8e-5a5c-4f43-9f2e-3a1d9c6e1a03");
   private static final UUID ARMOR = UUID.fromString("7d0f0b8e-5a5c-4f43-9f2e-3a1d9c6e1a04");

   private DecreeBonus() {
   }

   /** The bonuses one settlement's decrees and laws give its soldiers. */
   static Map<ResourceLocation, Double> of(MinecraftServer server, IColony colony) {
      return Decrees.desired(server, DecreeData.get(server), colony);
   }

   static void apply(Entity e, Map<ResourceLocation, Double> bonus) {
      if (e instanceof LivingEntity unit) {
         set(unit, Attributes.MAX_HEALTH, HEALTH, "prestol_decree_health", bonus.getOrDefault(ResearchConstants.HEALTH_BOOST, 0.0), Operation.ADDITION);
         set(unit, Attributes.ATTACK_DAMAGE, MELEE, "prestol_decree_melee", bonus.getOrDefault(ResearchConstants.MELEE_DAMAGE, 0.0), Operation.ADDITION);
         set(unit, Attributes.ARMOR, ARMOR, "prestol_decree_armor", bonus.getOrDefault(ResearchConstants.MELEE_ARMOR, 0.0), Operation.MULTIPLY_BASE);
         Attribute ranged = Hyw.rangedDamage();
         if (ranged != null) {
            set(unit, ranged, RANGED, "prestol_decree_ranged", bonus.getOrDefault(ResearchConstants.ARCHER_DAMAGE, 0.0), Operation.ADDITION);
         }

         if (unit.getHealth() > unit.getMaxHealth()) {
            unit.setHealth(unit.getMaxHealth());
         }
      }
   }

   static void set(LivingEntity unit, Attribute attribute, UUID id, String name, double amount, Operation op) {
      AttributeInstance inst = unit.getAttribute(attribute);
      if (inst != null) {
         AttributeModifier cur = inst.getModifier(id);
         if (cur == null || Math.abs(cur.getAmount() - amount) >= 1.0E-6) {
            if (cur != null) {
               inst.removeModifier(id);
            }

            if (amount != 0.0) {
               inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
            }
         }
      }
   }
}
