package com.hyrrx.forgottenrealmsrts.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.ValueInput;

/** Small, shared rules for entity variants that cross the save/network boundary. */
public final class RtsEntityVariants {
   private RtsEntityVariants() {
   }

   public static int clamp(int variant, int count) {
      if (count <= 1) {
         return 0;
      }

      return Math.max(0, Math.min(count - 1, variant));
   }

   public static int unsignedByte(byte variant, int count) {
      return clamp(variant & 0xFF, count);
   }

   public static int random(RandomSource random, int count) {
      return count <= 1 ? 0 : random.nextInt(count);
   }

   public static int read(ValueInput input, String key, int count) {
      return clamp(input.getIntOr(key, 0), count);
   }
}
