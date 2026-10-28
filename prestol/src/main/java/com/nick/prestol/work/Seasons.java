package com.nick.prestol.work;

import java.lang.reflect.Method;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public final class Seasons {
   private static Method getState;
   private static boolean absent;

   private Seasons() {
   }

   public static CompoundTag describe(Level level) {
      if (absent) {
         return null;
      } else {
         try {
            if (getState == null) {
               getState = Class.forName("sereneseasons.api.season.SeasonHelper").getMethod("getSeasonState", Level.class);
            }

            Object state = getState.invoke(null, level);
            Object sub = state.getClass().getMethod("getSubSeason").invoke(state);
            int dayLength = (Integer)state.getClass().getMethod("getDayDuration").invoke(state);
            int subLength = (Integer)state.getClass().getMethod("getSubSeasonDuration").invoke(state);
            int ticks = (Integer)state.getClass().getMethod("getSeasonCycleTicks").invoke(state);
            int subDays = Math.max(1, subLength / Math.max(1, dayLength));
            int day = ticks / Math.max(1, dayLength);
            int dayInSub = day % subDays + 1;
            int ordinal = ((Enum)sub).ordinal();
            CompoundTag t = new CompoundTag();
            t.putInt("sub", ordinal);
            t.putInt("day", dayInSub);
            t.putInt("of", subDays);
            t.putInt("toNext", (3 - ordinal % 3) * subDays - (dayInSub - 1));
            return t;
         } catch (ClassNotFoundException var11) {
            absent = true;
            return null;
         } catch (RuntimeException | ReflectiveOperationException var12) {
            return null;
         }
      }
   }
}
