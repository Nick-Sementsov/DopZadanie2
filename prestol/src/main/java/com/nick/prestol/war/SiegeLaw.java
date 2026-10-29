package com.nick.prestol.war;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.land.Territory;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Where the siege engines of Hundred Years War may break blocks: in the wilderness and in the settlements of a
 * realm their owner's realm is fighting. Peaceful, allied and one's own lands keep their walls.
 */
public final class SiegeLaw {
   private static final Map<UUID, Long> TOLD = new HashMap<>();

   private SiegeLaw() {
   }

   /** The player behind a siege engine or its shot: the engine's owner, or the player who fired. */
   public static UUID ownerOf(Entity e) {
      if (e instanceof Player p) {
         return p.getUUID();
      } else {
         return e instanceof OwnableEntity own ? own.getOwnerUUID() : null;
      }
   }

   public static boolean mayBreak(Level world, BlockPos pos, UUID owner) {
      if (!(world instanceof ServerLevel level) || !PrestolConfig.SIEGE_ENGINES_AT_WAR_ONLY.get()) {
         return true;
      } else {
         int id = Territory.ownerAt(level, pos.getX() >> 4, pos.getZ() >> 4);
         IColony colony = id <= 0 ? null : IColonyManager.getInstance().getColonyByWorld(id, level);
         if (colony == null) {
            return true;
         } else {
            MinecraftServer server = level.getServer();
            RealmData realms = RealmData.get(server);
            Realm land = realms.byMember(Settlements.key(colony));
            Realm attacker = owner == null ? null : Sieges.realmOfPlayer(server, realms, owner);
            boolean ok = land != null && attacker != null && Wars.fighting(server, realms, attacker, land);
            if (!ok && owner != null) {
               tell(server, owner, colony);
            }

            return ok;
         }
      }
   }

   private static void tell(MinecraftServer server, UUID owner, IColony colony) {
      long now = server.overworld().getGameTime();
      Long next = TOLD.get(owner);
      ServerPlayer p = server.getPlayerList().getPlayer(owner);
      if (p != null && (next == null || next <= now)) {
         TOLD.put(owner, now + 600L);
         p.displayClientMessage(Msg.bad("siege.engines_peace", colony.getName()), true);
      }
   }
}
