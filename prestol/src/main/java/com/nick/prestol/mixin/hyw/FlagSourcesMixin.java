package com.nick.prestol.mixin.hyw;

import com.nick.prestol.flag.Flags;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.war.Sieges;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.extras.flags.FlagSources;

/**
 * Hundred Years War army standards look for the realm of the old "Derzhava" mod. Prestol is that realm now:
 * a soldier of a realm carries the realm's flag, and the realm's name is used for him.
 */
@Mixin(value = FlagSources.class, remap = false)
public abstract class FlagSourcesMixin {
   private static Realm prestol$realm(MinecraftServer server, UUID owner) {
      return server == null || owner == null ? null : Sieges.realmOfPlayer(server, RealmData.get(server), owner);
   }

   @Inject(method = "nationFlag", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$realmFlag(MinecraftServer server, UUID owner, String except, CallbackInfoReturnable<ItemStack> info) {
      Realm realm = prestol$realm(server, owner);
      if (realm != null) {
         info.setReturnValue(Flags.standard(server, realm));
      }
   }

   @Inject(method = "nameOf(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$realmName(MinecraftServer server, UUID owner, String taken, CallbackInfoReturnable<String> info) {
      Realm realm = prestol$realm(server, owner);
      if (realm != null) {
         info.setReturnValue(realm.name());
      }
   }
}
