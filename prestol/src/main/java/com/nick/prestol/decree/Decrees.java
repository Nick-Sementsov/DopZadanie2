package com.nick.prestol.decree;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.research.IResearchEffect;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Decrees {
   private static final Map<String, EffectBoost.Applied> APPLIED = new HashMap<>();

   private Decrees() {
   }

   public static String time(long ticks) {
      long s = Math.max(0L, ticks / 20L);
      return s / 60L + ":" + String.format("%02d", s % 60L);
   }

   private static Component name(DecreeType t) {
      return Component.translatable(t.key());
   }

   private static Component name(LawType t) {
      return Component.translatable(t.key());
   }

   private static double scale(boolean capital) {
      return 1.0 + (capital ? (double)((Integer)PrestolConfig.CAPITAL_BONUS.get()).intValue() / 100.0 : 0.0);
   }

   public static boolean mayIssue(MinecraftServer server, ServerPlayer player, IColony colony) {
      Realm realm = RealmData.get(server).byMember(Settlements.key(colony));
      return realm == null ? false : Realms.isRuler(realm, player) || (Boolean)PrestolConfig.OWNERS_DECREE.get() && Settlements.isOwner(player, colony);
   }

   public static Component blocker(MinecraftServer server, IColony colony, DecreeType type) {
      if (!EffectBoost.isAvailable()) {
         return Msg.bad("decree.unavailable");
      } else {
         DecreeData data = DecreeData.get(server);
         String key = Settlements.key(colony);
         long now = Realms.now(server);
         DecreeData.Active a = data.active(key, type);
         if (a != null) {
            return Msg.bad("decree.already", colony.getName(), name(type), time(a.end() - now));
         } else {
            long ready = data.readyAt(key, type);
            return ready > now ? Msg.bad("decree.cooldown", colony.getName(), name(type), time(ready - now)) : null;
         }
      }
   }

   public static Realms.Result issue(MinecraftServer server, ServerPlayer player, String key, DecreeType type) {
      IColony colony = Settlements.byKey(key);
      if (colony == null) {
         return Realms.Result.no(Msg.bad("err.no_settlement"));
      } else {
         Realm realm = RealmData.get(server).byMember(key);
         if (realm == null) {
            return Realms.Result.no(Msg.bad("err.not_in_realm"));
         } else if (!mayIssue(server, player, colony)) {
            return Realms.Result.no(Msg.bad("decree.not_allowed"));
         } else {
            Component blocked = blocker(server, colony, type);
            if (blocked != null) {
               return Realms.Result.no(blocked);
            } else {
               DecreeData data = DecreeData.get(server);
               long now = Realms.now(server);
               boolean capital = key.equals(realm.capital());
               long duration = PrestolConfig.durationTicks(type);
               data.putActive(key, type, new DecreeData.Active(now + duration, scale(capital)));
               data.putReadyAt(key, type, now + duration + PrestolConfig.cooldownTicks(type));
               recompute(server, data, colony);
               Component msg = Msg.good(capital ? "decree.issued_capital" : "decree.issued", name(type), colony.getName(), time(duration));
               Realms.tellColony(server, colony, msg);
               Realms.chronicle(server, "decree", List.of("@" + type.key(), colony.getName()), realm);
               return Realms.Result.yes(msg);
            }
         }
      }
   }

   public static Realms.Result revoke(MinecraftServer server, ServerPlayer player, String key, DecreeType type) {
      IColony colony = Settlements.byKey(key);
      if (colony != null && mayIssue(server, player, colony)) {
         DecreeData data = DecreeData.get(server);
         if (data.active(key, type) == null) {
            return Realms.Result.no(Msg.bad("decree.not_active", colony.getName(), name(type)));
         } else {
            data.removeActive(key, type);
            recompute(server, data, colony);
            Component msg = Msg.info("decree.revoked", name(type), colony.getName());
            Realms.tellColony(server, colony, msg);
            return Realms.Result.yes(msg);
         }
      } else {
         return Realms.Result.no(Msg.bad("decree.not_allowed"));
      }
   }

   public static int lawSlots(Realm realm) {
      return Realms.rank(realm).lawSlots;
   }

   public static List<LawType> activeLaws(DecreeData data, Realm realm) {
      return LawType.effective(data.lawsOf(realm.id()), lawSlots(realm));
   }

   public static Realms.Result enact(MinecraftServer server, ServerPlayer player, LawType law) {
      Realm realm = RealmData.get(server).byRuler(player.getUUID());
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (!EffectBoost.isAvailable()) {
         return Realms.Result.no(Msg.bad("decree.unavailable"));
      } else {
         DecreeData data = DecreeData.get(server);
         UUID id = realm.id();
         if (data.lawsOf(id).contains(law)) {
            return Realms.Result.no(Msg.bad("law.already", name(law)));
         } else {
            int slots = lawSlots(realm);
            if (data.lawsOf(id).size() >= slots) {
               return Realms.Result.no(Msg.bad("law.full", slots));
            } else {
               long lock = data.lawLockUntil(id) - Realms.now(server);
               if (lock > 0L) {
                  return Realms.Result.no(Msg.bad("law.locked", time(lock)));
               } else {
                  data.enact(id, law);
                  recomputeRealm(server, data, realm);
                  Component msg = Msg.good("law.enacted", name(law), data.lawsOf(id).size(), slots);
                  Realms.tell(server, realm, msg);
                  Realms.chronicle(server, "law", List.of("@" + law.key(), realm.name()), realm);
                  return Realms.Result.yes(msg);
               }
            }
         }
      }
   }

   public static Realms.Result repeal(MinecraftServer server, ServerPlayer player, LawType law) {
      Realm realm = RealmData.get(server).byRuler(player.getUUID());
      if (realm == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else {
         DecreeData data = DecreeData.get(server);
         if (!data.lawsOf(realm.id()).contains(law)) {
            return Realms.Result.no(Msg.bad("law.not_in_force", name(law)));
         } else {
            data.repeal(realm.id(), law);
            data.setLawLock(realm.id(), Realms.now(server) + (long)((Integer)PrestolConfig.LAW_LOCK_MINUTES.get()).intValue() * 1200L);
            recomputeRealm(server, data, realm);
            Component msg = Msg.info("law.repealed", name(law));
            Realms.tell(server, realm, msg);
            Realms.chronicle(server, "law_repealed", List.of("@" + law.key(), realm.name()), realm);
            return Realms.Result.yes(msg);
         }
      }
   }

   public static void tick(MinecraftServer server) {
      DecreeData data = DecreeData.get(server);
      RealmData realms = RealmData.get(server);
      if (data.laws.keySet().removeIf(id -> realms.byId(id) == null)) {
         data.setDirty();
      }

      long now = Realms.now(server);
      data.prune(now, id -> realms.byId(id) != null);
      Set<String> keys = new LinkedHashSet<>(data.active.keySet());
      keys.addAll(APPLIED.keySet());

      for (UUID id : data.laws.keySet()) {
         Realm r = realms.byId(id);
         if (r != null) {
            keys.addAll(r.members());
         }
      }

      for (String key : keys) {
         IColony colony = Settlements.byKey(key);
         if (colony == null) {
            data.active.remove(key);
            APPLIED.remove(key);
            data.setDirty();
         } else {
            Map<DecreeType, DecreeData.Active> byType = data.active.get(key);
            if (byType != null) {
               for (Entry<DecreeType, DecreeData.Active> e : new EnumMap<>(byType).entrySet()) {
                  if (now >= e.getValue().end()) {
                     data.removeActive(key, e.getKey());
                     Realms.tellColony(server, colony, Msg.info("decree.ended", name(e.getKey()), colony.getName()));
                  }
               }
            }

            recompute(server, data, colony);
         }
      }
   }

   private static void recomputeRealm(MinecraftServer server, DecreeData data, Realm realm) {
      for (IColony c : Realms.members(realm)) {
         recompute(server, data, c);
      }
   }

   public static Map<ResourceLocation, Double> desired(MinecraftServer server, DecreeData data, IColony colony) {
      String key = Settlements.key(colony);
      Map<ResourceLocation, Double> out = new HashMap<>();
      Map<DecreeType, DecreeData.Active> byType = data.active.get(key);
      if (byType != null) {
         byType.forEach((type, a) -> {
            for (DecreeType.Boost bx : type.boosts) {
               out.merge(bx.effect(), bx.amount() * a.scale(), Double::sum);
            }
         });
      }

      Realm realm = RealmData.get(server).byMember(key);
      if (realm != null) {
         double s = scale(key.equals(realm.capital()));

         for (LawType law : activeLaws(data, realm)) {
            for (DecreeType.Boost b : law.boosts) {
               out.merge(b.effect(), b.amount() * s, Double::sum);
            }
         }
      }

      return out;
   }

   private static void recompute(MinecraftServer server, DecreeData data, IColony colony) {
      if (EffectBoost.isAvailable()) {
         String key = Settlements.key(colony);
         Map<ResourceLocation, Double> want = desired(server, data, colony);
         EffectBoost.Applied applied = APPLIED.computeIfAbsent(key, k -> new EffectBoost.Applied());
         EffectBoost.sync(colony.getResearchManager().getResearchEffects(), applied, want, id -> researchEffect(colony, id));
         if (want.isEmpty() && applied.isEmpty()) {
            APPLIED.remove(key);
         }
      }
   }

   static IResearchEffect researchEffect(IColony colony, ResourceLocation id) {
      ILocalResearchTree tree = colony.getResearchManager().getResearchTree();
      IGlobalResearchTree global = IGlobalResearchTree.getInstance();
      IResearchEffect best = null;

      for (ResourceLocation research : tree.getCompletedList()) {
         for (IResearchEffect e : global.getEffectsForResearch(research)) {
            if (e.getId().equals(id) && (best == null || Math.abs(e.getEffect()) > Math.abs(best.getEffect()))) {
               best = e;
            }
         }
      }

      return best;
   }

   public static void clear() {
      APPLIED.clear();
   }
}
