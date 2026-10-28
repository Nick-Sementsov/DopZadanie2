package com.nick.prestol.decree;

import com.minecolonies.api.research.IResearchEffect;
import com.minecolonies.api.research.IResearchEffectManager;
import com.minecolonies.core.research.GlobalResearchEffect;
import com.nick.prestol.Prestol;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class EffectBoost {
   public static volatile EffectBoost.Factory factory = (id, strength) -> new GlobalResearchEffect(
         id, "prestol.effect", "prestol.effect.sub", strength, strength
      );
   private static Field mapField;
   private static boolean checked;
   private static boolean available;

   private EffectBoost() {
   }

   public static synchronized boolean isAvailable() {
      if (!checked) {
         checked = true;

         try {
            Field f = Class.forName("com.minecolonies.core.research.ResearchEffectManager").getDeclaredField("effectMap");
            f.setAccessible(true);
            mapField = f;
            available = Map.class.isAssignableFrom(f.getType());
         } catch (RuntimeException | ReflectiveOperationException var1) {
            Prestol.LOGGER.error("Prestol: cannot reach the MineColonies effect map, decrees are off", var1);
            available = false;
         }
      }

      return available;
   }

   private static Map<ResourceLocation, IResearchEffect> map(IResearchEffectManager manager) {
      try {
         return (Map<ResourceLocation, IResearchEffect>)mapField.get(manager);
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException(var2);
      }
   }

   private static IResearchEffect stronger(IResearchEffect a, IResearchEffect b) {
      if (a == null) {
         return b;
      } else if (b == null) {
         return a;
      } else {
         return Math.abs(b.getEffect()) > Math.abs(a.getEffect()) ? b : a;
      }
   }

   private static IResearchEffect safe(EffectBoost.Lookup lookup, ResourceLocation id) {
      if (lookup == null) {
         return null;
      } else {
         try {
            return lookup.strongest(id);
         } catch (RuntimeException var3) {
            return null;
         }
      }
   }

   public static boolean sync(IResearchEffectManager manager, EffectBoost.Applied applied, Map<ResourceLocation, Double> desired, EffectBoost.Lookup research) {
      Map<ResourceLocation, IResearchEffect> map = map(manager);
      Set<ResourceLocation> ids = new HashSet<>(applied.slots.keySet());
      ids.addAll(desired.keySet());
      boolean changed = false;

      for (ResourceLocation id : ids) {
         double bonus = desired.getOrDefault(id, 0.0);
         EffectBoost.Slot slot = applied.slots.get(id);
         IResearchEffect current = map.get(id);
         boolean stillOurs = slot != null && current == slot.ours();
         IResearchEffect base = stronger(safe(research, id), stillOurs ? slot.base() : current);
         if (bonus > 0.0) {
            double strength = (base == null ? 0.0 : base.getEffect()) + bonus;
            if (!stillOurs || !(Math.abs(slot.ours().getEffect() - strength) < 1.0E-9)) {
               IResearchEffect ours = factory.create(id, strength);
               map.put(id, ours);
               applied.slots.put(id, new EffectBoost.Slot(base, ours));
               changed = true;
            }
         } else if (slot != null) {
            if (stillOurs) {
               if (base == null) {
                  map.remove(id);
               } else {
                  map.put(id, base);
               }

               changed = true;
            }

            applied.slots.remove(id);
         }
      }

      return changed;
   }

   public static final class Applied {
      private final Map<ResourceLocation, EffectBoost.Slot> slots = new HashMap<>();

      public boolean isEmpty() {
         return this.slots.isEmpty();
      }
   }

   @FunctionalInterface
   public interface Factory {
      IResearchEffect create(ResourceLocation var1, double var2);
   }

   @FunctionalInterface
   public interface Lookup {
      IResearchEffect strongest(ResourceLocation var1);
   }

   private static record Slot(IResearchEffect base, IResearchEffect ours) {
   }
}
