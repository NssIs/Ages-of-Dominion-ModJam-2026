package com.hyrrx.forgottenrealmsrts.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
   private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, "forgotten_realms_rts");
   public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLICK = register("ui_click");
   public static final DeferredHolder<SoundEvent, SoundEvent> PEASANT_AMBIENT = register("peasant_ambient");
   public static final DeferredHolder<SoundEvent, SoundEvent> PEASANT_HURT = register("peasant_hurt");
   public static final DeferredHolder<SoundEvent, SoundEvent> PEASANT_DEATH = register("peasant_death");
   public static final DeferredHolder<SoundEvent, SoundEvent> PEASANT_STEP = register("peasant_step");
   public static final DeferredHolder<SoundEvent, SoundEvent> SOLDIER_AMBIENT = register("soldier_ambient");
   public static final DeferredHolder<SoundEvent, SoundEvent> SOLDIER_HURT = register("soldier_hurt");
   public static final DeferredHolder<SoundEvent, SoundEvent> SOLDIER_DEATH = register("soldier_death");
   public static final DeferredHolder<SoundEvent, SoundEvent> SOLDIER_STEP = register("soldier_step");
   public static final DeferredHolder<SoundEvent, SoundEvent> RANGED_AMBIENT = register("ranged_ambient");
   public static final DeferredHolder<SoundEvent, SoundEvent> RANGED_HURT = register("ranged_hurt");
   public static final DeferredHolder<SoundEvent, SoundEvent> RANGED_DEATH = register("ranged_death");
   public static final DeferredHolder<SoundEvent, SoundEvent> RANGED_STEP = register("ranged_step");
   public static final DeferredHolder<SoundEvent, SoundEvent> RANGED_SHOOT = register("ranged_shoot");
   public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_AMBIENT = register("echo_ambient");
   public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_HURT = register("echo_hurt");
   public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_DEATH = register("echo_death");
   public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_STEP = register("echo_step");
   public static final DeferredHolder<SoundEvent, SoundEvent> BRUTE_AMBIENT = register("brute_ambient");
   public static final DeferredHolder<SoundEvent, SoundEvent> BRUTE_HURT = register("brute_hurt");
   public static final DeferredHolder<SoundEvent, SoundEvent> BRUTE_DEATH = register("brute_death");
   public static final DeferredHolder<SoundEvent, SoundEvent> BRUTE_STEP = register("brute_step");
   /** Each campaign track has its own streamed event so the controller can choose one exactly. */
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_CASTLE_OF_THE_ANCIENT_KINGS =
      register("music_castle_of_the_ancient_kings");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FINAL_BATTLE_OF_THE_LOST_KINGDOM_1 =
      register("music_final_battle_of_the_lost_kingdom_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FINAL_BATTLE_OF_THE_LOST_KINGDOM_2 =
      register("music_final_battle_of_the_lost_kingdom_2");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FINAL_BOSS =
      register("music_final_boss");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_HEROIC_BATTLE_ON_THE_PLAINS =
      register("music_heroic_battle_on_the_plains");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MEDIEVAL_VILLAGE_IN_PEACE =
      register("music_medieval_village_in_peace");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SAILORS_OF_THE_NORTH_1 =
      register("music_sailors_of_the_north_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SONG_OF_THE_VALLEY_TAVERN_1 =
      register("music_song_of_the_valley_tavern_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_THE_HEROS_JOURNEY_1 =
      register("music_the_heros_journey_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_THE_LIGHT_OF_THE_KINGDOM_1 =
      register("music_the_light_of_the_kingdom_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_TRIBUTE_TO_THE_HERO =
      register("music_tribute_to_the_hero");

   private ModSounds() {
   }

   private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
      return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("forgotten_realms_rts", name)));
   }

   public static void register(IEventBus modEventBus) {
      SOUND_EVENTS.register(modEventBus);
   }
}
