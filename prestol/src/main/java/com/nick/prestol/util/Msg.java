package com.nick.prestol.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Msg {
   private Msg() {
   }

   public static MutableComponent t(String key, Object... args) {
      Object[] a = new Object[args.length];

      for (int i = 0; i < args.length; i++) {
         a[i] = args[i] instanceof Component c ? c : String.valueOf(args[i]);
      }

      return Component.translatable("prestol." + key, a);
   }

   public static MutableComponent good(String key, Object... args) {
      return t(key, args).withStyle(ChatFormatting.GREEN);
   }

   public static MutableComponent bad(String key, Object... args) {
      return t(key, args).withStyle(ChatFormatting.RED);
   }

   public static MutableComponent info(String key, Object... args) {
      return t(key, args).withStyle(ChatFormatting.GOLD);
   }

   public static MutableComponent war(String key, Object... args) {
      return t(key, args).withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD});
   }

   public static MutableComponent lit(String s) {
      return Component.literal(s);
   }
}
