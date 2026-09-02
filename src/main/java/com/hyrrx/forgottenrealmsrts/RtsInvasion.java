package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.entity.RtsEnemyEntity;
import com.hyrrx.forgottenrealmsrts.network.BuildingActionPayload;
import com.hyrrx.forgottenrealmsrts.network.BuildingEffectPayload;
import com.hyrrx.forgottenrealmsrts.network.BuildingSelectionPayload;
import com.hyrrx.forgottenrealmsrts.network.MoonStatePayload;
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RtsInvasion {
   private static final int CYCLE_TICKS = 20;
   private static final int WAVE_BASE = 3;
   private static final int SPAWN_RADIUS = 28;
   private static final int THREAT_SCAN_RADIUS = 40;
   private static final double HALL_DAMAGE_RADIUS = 6.0;
   private static final double BUILDING_DAMAGE_RADIUS = 3.5;
   private static final int REGEN_PER_CYCLE = 1;
   private static final int TOWER_RANGE = 16;
   private static final float TOWER_DAMAGE = 6.0F;
   private static final double HERD_SPEED = 1.15;
   private static final long CAMPAIGN_DAYS = 10L;
   private static final int MAX_WAVE = 40;
   private static final int BLOOD_MOON_MULTIPLIER = 3;
   private static final long NIGHT_START = 13000L;
   private static final long NIGHT_END = 23000L;
   /** 32x reaches the next night in seconds while still letting the sky visibly transition. */
   private static final float EVENT_APPROACH_RATE = 32.0F;
   private static final double DAYLIGHT_PURGE_RADIUS = 128.0D;
   private static final float ZEUS_DAYLIGHT_DAMAGE = 6.0F;
   private static final Map<UUID, long[]> STATE = new HashMap<>();
   private static final Map<UUID, ForcedMoon> FORCED_MOONS = new HashMap<>();

   private static final class ForcedMoon {
      private final RtsMoons.Moon moon;
      private long startedDay = -1L;

      private ForcedMoon(RtsMoons.Moon moon) {
         this.moon = moon;
      }
   }

   private RtsInvasion() {
   }

   /** Starts a named moon test event and accelerates the clock toward the next night. */
   public static boolean startForcedMoon(ServerPlayer player, RtsMoons.Moon moon) {
      if (moon == RtsMoons.Moon.NONE || !RtsCivilization.isFounded(player)
            || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
            || FORCED_MOONS.containsKey(player.getUUID())) {
         return false;
      }

      ServerLevel level = player.level();
      ForcedMoon forced = new ForcedMoon(moon);
      FORCED_MOONS.put(player.getUUID(), forced);
      sendMoonState(player, moon);
      if (isNightPhase(level.getOverworldClockTime())) {
         // Force a fresh trigger even if the ordinary calendar moon already fired this night.
         forced.startedDay = -1L;
         STATE.computeIfAbsent(player.getUUID(), key -> new long[]{-1L})[0] = -1L;
         RtsDayCycle.apply(level);
      } else {
         RtsDayCycle.setRate(level, EVENT_APPROACH_RATE);
      }
      return true;
   }

   /** Ends a forced moon only during its active night. */
   public static boolean endForcedMoon(ServerPlayer player, RtsMoons.Moon moon) {
      ServerLevel level = player.level();
      long day = level.getOverworldClockTime() / 24000L;
      ForcedMoon forced = FORCED_MOONS.get(player.getUUID());
      if (!isNightPhase(level.getOverworldClockTime()) || forced == null
            || forced.moon != moon || forced.startedDay != day) {
         return false;
      }
      FORCED_MOONS.remove(player.getUUID());
      sendMoonState(player, RtsMoons.Moon.NONE);
      RtsDayCycle.apply(level);
      return true;
   }

   /** The requested forced event, including one that is still approaching night. */
   public static RtsMoons.Moon forcedMoon(ServerPlayer player) {
      ForcedMoon forced = FORCED_MOONS.get(player.getUUID());
      return forced == null ? RtsMoons.Moon.NONE : forced.moon;
   }

   public static boolean isNight(ServerPlayer player) {
      return isNightPhase(player.level().getOverworldClockTime());
   }

   /** Moon rules used by both invasion and production, including an active forced event. */
   public static RtsMoons.Moon moonFor(ServerPlayer player, long day) {
      ForcedMoon forced = FORCED_MOONS.get(player.getUUID());
      return forced != null && forced.startedDay == day
            ? forced.moon : RtsMoons.forDay(day);
   }

   /** Clears an unfinished command event when its owner leaves the server. */
   public static void clearForcedMoon(ServerPlayer player) {
      if (FORCED_MOONS.remove(player.getUUID()) != null) {
         sendMoonState(player, RtsMoons.Moon.NONE);
         RtsDayCycle.apply(player.level());
      }
   }

   /** Clears transient invasion state when a defeated player confirms a replacement town. */
   public static void resetPlayer(ServerPlayer player) {
      STATE.remove(player.getUUID());
      clearForcedMoon(player);
   }

   private static boolean isNightPhase(long time) {
      long phase = Math.floorMod(time, 24000L);
      return phase >= NIGHT_START && phase < NIGHT_END;
   }

   public static void tick(ServerPlayer player) {
      if (RtsCivilization.isFounded(player)
            && RtsBattle.outcome(player) == RtsBattle.OUTCOME_ONGOING
            && player.tickCount % CYCLE_TICKS == 0) {
         ServerLevel level = player.level();
         Optional<BlockPos> found = RtsEntities.townHallCenter(player);
         if (!found.isEmpty()) {
            BlockPos center = found.get();
            Vec3 centerVec = Vec3.atCenterOf(center);
            long time = level.getOverworldClockTime();
            long day = time / 24000L;
            boolean night = isNightPhase(time);
            long[] state = STATE.computeIfAbsent(player.getUUID(), key -> new long[]{-1L});
            ForcedMoon forced = FORCED_MOONS.get(player.getUUID());
            if (forced != null) {
               if (night) {
                  RtsDayCycle.apply(level);
               } else if (forced.startedDay < 0L) {
                  RtsDayCycle.setRate(level, EVENT_APPROACH_RATE);
               } else {
                  // A forced moon naturally lasts for one night if the user does not end it early.
                  FORCED_MOONS.remove(player.getUUID());
                  forced = null;
                  sendMoonState(player, RtsMoons.Moon.NONE);
                  RtsDayCycle.apply(level);
               }
            }
            if (night && (state[0] != day || forced != null && forced.startedDay != day)) {
               state[0] = day;
               if (forced != null) {
                  forced.startedDay = day;
               }
               // Day 1 is the onboarding night (the internal clock day is zero). Give the player
               // one quiet night to learn the controls; an explicit forced moon remains an opt-in
               // override for testing or challenge runs.
               if (day == 0L && forced == null) {
                  player.sendSystemMessage(Component.literal(
                        "The first night is quiet — gather your strength before the echoes arrive."));
               } else {
                  RtsMoons.Moon moon = moonFor(player, day);
                  if (moon == RtsMoons.Moon.BLUE) {
                     player.sendSystemMessage(Component.literal(
                           "A SLUMBER MOON rises — the dead sleep, and the realm rests."));
                  } else {
                     int multiplier = moon == RtsMoons.Moon.BLOOD ? BLOOD_MOON_MULTIPLIER : 1;
                     spawnWave(level, center, Math.min(MAX_WAVE, (int)(3L + day) * multiplier),
                        moon == RtsMoons.Moon.BLOOD);

                     player.sendSystemMessage(Component.literal(switch (moon) {
                        case BLOOD -> "THE BLOOD MOON RISES — the echoes of the past come in force.";
                        case GOLDEN -> "A GOLDEN MOON rises — completed buildings yield rich, but the dead still march.";
                        default -> "Night falls — the echoes of the past march on " + RtsCivilization.name(player) + ".";
                     }));
                  }
               }
            }

            if (!night) {
               purgeEnemiesByDaylight(level, center);
            }

            List<Monster> threats = level.getEntitiesOfClass(Monster.class,
               new AABB(center).inflate(THREAT_SCAN_RADIUS),
               entity -> entity instanceof RtsEnemyEntity && entity.isAlive());
            RtsEntities.reconcilePopulation(player, center);
            RtsEconomy.setMilitary(
               player, RtsEntities.alliedCombatUnits(level, center, RtsEntities.POPULATION_SCAN_RADIUS).size()
            );
            int onHall = 0;
            Map<Long, Integer> buildingAttackers = new HashMap<>();

            for (Monster monster : threats) {
               if (monster instanceof RtsEnemyEntity enemy) {
                  RtsBuildingStore.Entry target = nearestNonTownHall(level, player, monster);
                  if (target != null) {
                     BlockPos targetCenter = buildingCenter(level, target);
                     enemy.setBuildingTarget(target.id(), targetCenter);
                     if (monster.distanceToSqr(targetCenter.getX() + 0.5D,
                           targetCenter.getY(), targetCenter.getZ() + 0.5D)
                           <= BUILDING_DAMAGE_RADIUS * BUILDING_DAMAGE_RADIUS) {
                        buildingAttackers.merge(target.id(), 1, Integer::sum);
                     }
                  } else {
                     enemy.setTownHallTarget(center);
                     if (monster.distanceToSqr(centerVec) <= HALL_DAMAGE_RADIUS * HALL_DAMAGE_RADIUS) {
                        onHall++;
                     }
                  }
               } else {
                  RtsUnitOrders.moveToSmart(monster, level, center, HERD_SPEED);
               }
            }

            for (Map.Entry<Long, Integer> attack : buildingAttackers.entrySet()) {
               damageBuilding(player, level, attack.getKey(), attack.getValue());
            }
            if (!buildingAttackers.isEmpty()) {
               ModPayloads.sendBuildingHealth(player);
            }

            if (onHall > 0) {
               RtsBattle.setIntegrity(player, RtsBattle.integrity(player) - onHall);
               player.sendOverlayMessage(Component.literal("The Town Hall is under attack! Integrity "
                     + RtsBattle.integrity(player) + "/" + RtsBattle.maxIntegrity(player)));
               double hx = center.getX() + 0.5;
               double hy = center.getY() + 1.2;
               double hz = center.getZ() + 0.5;
               level.sendParticles(ParticleTypes.LARGE_SMOKE, hx, hy, hz, 6 + onHall * 2, 0.7, 0.5, 0.7, 0.02);
               level.sendParticles(ParticleTypes.FLAME, hx, hy, hz, 3 + onHall, 0.5, 0.4, 0.5, 0.01);
            } else if (!night) {
               RtsBattle.setIntegrity(player, RtsBattle.integrity(player) + 1);
            }

            defendWithTowers(player, level, threats);
            if (RtsBattle.integrity(player) <= 0) {
               releaseTownHallRepairers(level, player);
               RtsBattle.setOutcome(player, 2);
               clearForcedMoon(player);
               RtsDayCycle.resetToDay(level);
               Component fallen = Component.literal("YOUR CIVILIZATION HAS FALLEN.");
               player.sendSystemMessage(fallen);
               player.sendOverlayMessage(fallen);
            } else if (day >= 10L) {
               RtsBattle.setOutcome(player, 1);
               Component endures = Component.literal("YOUR CIVILIZATION ENDURES.");
               player.sendSystemMessage(endures);
               player.sendOverlayMessage(endures);
            }
         }
      }
   }

   /** Returns the nearest live owned non-Town-Hall entry, with stable ID tie-breaking. */
   private static RtsBuildingStore.Entry nearestNonTownHall(ServerLevel level, ServerPlayer player,
                                                             Monster monster) {
      RtsBuildingStore.Entry best = null;
      double bestDistance = Double.MAX_VALUE;
      for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
         if (!entry.owner().equals(player.getUUID()) || entry.health() <= 0
               || entry.maxHealth() <= 0 || BuildingCosts.get(entry.structure()).townHall()) {
            continue;
         }
         BlockPos center = buildingCenter(level, entry);
         double distance = monster.distanceToSqr(center.getX() + 0.5D,
               center.getY(), center.getZ() + 0.5D);
         if (distance < bestDistance || distance == bestDistance && best != null
               && entry.id() < best.id()) {
            best = entry;
            bestDistance = distance;
         }
      }
      return best;
   }

   private static BlockPos buildingCenter(ServerLevel level, RtsBuildingStore.Entry entry) {
      BlockPos origin = RtsBuildingDurability.normalizedOrigin(level, entry);
      Vec3i size = RtsBuildingDurability.rotatedSize(level, entry);
      return new BlockPos(origin.getX() + size.getX() / 2,
            origin.getY() + size.getY() / 2,
            origin.getZ() + size.getZ() / 2);
   }

   /** Applies one point of siege damage per attacker and permanently removes a dead structure. */
   private static void damageBuilding(ServerPlayer player, ServerLevel level,
                                      long buildingId, int amount) {
      RtsBuildingStore store = RtsBuildingStore.get(level);
      RtsBuildingStore.Entry entry = store.find(buildingId, player.getUUID()).orElse(null);
      if (entry == null || entry.health() <= 0 || amount <= 0) {
         return;
      }
      int health = Math.max(0, entry.health() - amount);
      if (health > 0) {
         store.update(RtsBuildingDurability.withHealth(entry, health, entry.maxHealth()));
         return;
      }

      for (BlockPos block : RtsBuildingDurability.trackedBlocks(level, entry)) {
         level.setBlock(block, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
               net.minecraft.world.level.block.Block.UPDATE_ALL);
      }
      BlockPos origin = RtsBuildingDurability.normalizedOrigin(level, entry);
      Vec3i size = RtsBuildingDurability.rotatedSize(level, entry);
      BlockPos scanCenter = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
      RtsMineOrders.releaseWorkers(level, scanCenter, entry.id());
      RtsFarmOrders.releaseWorkers(level, scanCenter, entry.id());
      RtsRepairOrders.releaseWorkers(level, entry.id());
      store.remove(entry.id(), player.getUUID());
      BlockPos center = buildingCenter(level, entry);
      level.sendParticles(ParticleTypes.LARGE_SMOKE, center.getX() + 0.5D,
            center.getY() + 0.5D, center.getZ() + 0.5D, 18, 0.5D, 0.5D, 0.5D, 0.03D);
      player.sendOverlayMessage(Component.literal("A building has been destroyed."));
      PacketDistributor.sendToPlayer(player, new BuildingSelectionPayload(Optional.empty()));
      PacketDistributor.sendToPlayer(player, new BuildingEffectPayload(origin, size.getX(),
            size.getY(), size.getZ(),
            BuildingActionPayload.Action.DEMOLISH));
      ModPayloads.sendBuildingHealth(player);
   }

   private static void releaseTownHallRepairers(ServerLevel level, ServerPlayer player) {
      for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
         if (entry.owner().equals(player.getUUID())
               && BuildingCosts.get(entry.structure()).townHall()) {
            RtsRepairOrders.releaseWorkers(level, entry.id());
         }
      }
   }

   /** Daylight is a focused purge for this mod's echoes only. */
   private static void purgeEnemiesByDaylight(ServerLevel level, BlockPos center) {
      List<RtsEnemyEntity> enemies = level.getEntitiesOfClass(RtsEnemyEntity.class,
         new AABB(center).inflate(DAYLIGHT_PURGE_RADIUS), LivingEntity::isAlive);
      for (RtsEnemyEntity enemy : enemies) {
         enemy.setTarget(null);
         enemy.getNavigation().stop();
         Vec3 velocity = enemy.getDeltaMovement();
         enemy.setDeltaMovement(velocity.x * 0.72D,
            Math.max(0.10D, velocity.y * 0.2D + 0.10D), velocity.z * 0.72D);
         spawnZeusStrike(level, enemy);
         enemy.hurtServer(level, level.damageSources().magic(), ZEUS_DAYLIGHT_DAMAGE);
      }
   }

   /**
    * Uses the real vanilla bolt for its flash, branching geometry, sound, and timing. Visual-only
    * prevents the vanilla entity from damaging anything or setting fire; the explicit magic hit in
    * {@link #purgeEnemiesByDaylight} is restricted to this one hostile echo.
    */
   private static void spawnZeusStrike(ServerLevel level, RtsEnemyEntity enemy) {
      spawnVisualStrike(level, enemy.getX(), enemy.getY(), enemy.getZ());
   }

   /** Spawns a harmless yellow lightning presentation for a scripted realm transition. */
   static void spawnVisualStrike(ServerLevel level, double x, double y, double z) {
      LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
      if (bolt != null) {
         if (bolt instanceof RtsScriptedLightning marker) {
            marker.forgottenRealmsRts$setScripted(true);
         }
         bolt.setVisualOnly(true);
         bolt.setDamage(0.0F);
         bolt.setPos(x, y, z);
         level.addFreshEntity(bolt);
      }
   }

   /** Strikes the top of an old building before the replacement-town cleanup removes it. */
   static void spawnRealmCollapseStrike(ServerLevel level, BlockPos origin, Vec3i size) {
      spawnVisualStrike(level, origin.getX() + size.getX() * 0.5D,
         origin.getY() + Math.max(1.0D, size.getY()), origin.getZ() + size.getZ() * 0.5D);
   }

   private static void sendMoonState(ServerPlayer player, RtsMoons.Moon moon) {
      PacketDistributor.sendToPlayer(player, new MoonStatePayload(moon));
   }

   private static void defendWithTowers(ServerPlayer player, ServerLevel level, List<Monster> threats) {
      for (BlockPos tower : RtsEntities.buildingCenters(player, "watchtower")) {
         Vec3 towerVec = Vec3.atCenterOf(tower);
         Monster nearest = null;
         double best = 256.0;

         for (Monster monster : threats) {
            if (monster.isAlive()) {
               double distance = monster.distanceToSqr(towerVec);
               if (distance < best) {
                  best = distance;
                  nearest = monster;
               }
            }
         }

         if (nearest != null) {
            nearest.hurtServer(level, level.damageSources().magic(), 6.0F);
            spawnBolt(level, towerVec.add(0.0, 2.5, 0.0), nearest.position().add(0.0, nearest.getBbHeight() * 0.5, 0.0));
         }
      }
   }

   private static void spawnBolt(ServerLevel level, Vec3 from, Vec3 to) {
      Vec3 delta = to.subtract(from);
      int steps = Math.max(4, (int)(delta.length() * 2.0));

      for (int i = 0; i <= steps; i++) {
         double f = (double)i / steps;
         level.sendParticles(ParticleTypes.END_ROD, from.x + delta.x * f, from.y + delta.y * f, from.z + delta.z * f, 1, 0.0, 0.0, 0.0, 0.0);
      }

      level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0.1);
   }

   private static void spawnWave(ServerLevel level, BlockPos center, int count, boolean bloodMoon) {
      long day = level.getOverworldClockTime() / 24000L;
      level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.RAID_HORN, SoundSource.HOSTILE, 6.0F, 1.0F);

      for (int index = 0; index < count; index++) {
         BlockPos spawn = findDryEnemySpawn(level, center);
         if (spawn == null) {
            // Never strand an echo swimming toward the town. A wave may be smaller than requested
            // when the entire ring is water, but every mob that does spawn has solid ground.
            continue;
         }
         int x = spawn.getX();
         int y = spawn.getY();
         int z = spawn.getZ();
         boolean brute = shouldSpawnBrute(level, day, bloodMoon, index);
         EntityType<? extends Mob> echoType = brute
            ? (EntityType<? extends Mob>)RtsEntities.FALLEN_BRUTE.get()
            : pickEcho(level, day);
         Mob echo = (Mob)echoType.create(level, EntitySpawnReason.EVENT);
         if (echo != null) {
            echo.setPersistenceRequired();
            echo.setPos(x + 0.5, y, z + 0.5);
            echo.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), EntitySpawnReason.EVENT, null);
            level.addFreshEntity(echo);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x + 0.5, y + 0.1, z + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, y + 0.2, z + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
            RtsUnitOrders.moveToSmart(echo, level, center, 1.15D);
         }
      }
   }

   /** Picks a dry floor cell on the invasion ring; heightmap water surfaces are rejected. */
   private static BlockPos findDryEnemySpawn(ServerLevel level, BlockPos center) {
      for (int attempt = 0; attempt < 12; attempt++) {
         double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
         double radius = SPAWN_RADIUS * (0.7 + 0.3 * level.getRandom().nextDouble());
         int x = center.getX() + (int)Math.round(Math.cos(angle) * radius);
         int z = center.getZ() + (int)Math.round(Math.sin(angle) * radius);
         level.getChunk(x >> 4, z >> 4);
         int y = RtsEntities.findBottomFloorY(level, x, z,
               level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z));
         BlockPos candidate = new BlockPos(x, y, z);
         if (isDryEnemySpawn(level, candidate)) {
            return candidate;
         }
      }
      return null;
   }

   private static boolean isDryEnemySpawn(ServerLevel level, BlockPos feet) {
      return level.getBlockState(feet).isAir()
            && level.getBlockState(feet.above()).isAir()
            && !level.getFluidState(feet).is(FluidTags.WATER)
            && !level.getFluidState(feet.above()).is(FluidTags.WATER)
            && !level.getFluidState(feet.below()).is(FluidTags.WATER)
            && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
   }

   /** Brutes are a late-campaign threat, with a guaranteed cadence in a Blood Moon wave. */
   private static boolean shouldSpawnBrute(ServerLevel level, long day, boolean bloodMoon, int index) {
      if (bloodMoon) {
         return index % 5 == 0;
      }
      if (day < 5L) {
         return false;
      }
      return level.getRandom().nextInt(100) < Math.min(12L, day - 3L);
   }

   private static EntityType<? extends Mob> pickEcho(ServerLevel level, long day) {
      int knightChance = (int)Math.min(18L, 4L + day);
      if (level.getRandom().nextInt(100) < knightChance) {
         return (EntityType<? extends Mob>)RtsEntities.FALLEN_KNIGHT.get();
      } else {
         int roll = level.getRandom().nextInt(100);
         if (roll < 25) {
            return (EntityType<? extends Mob>)RtsEntities.SKELETAL_ARCHER.get();
         } else {
            return roll < 45 ? (EntityType)RtsEntities.SAMURAI_ZOMBIE.get() : (EntityType)RtsEntities.FALLEN_SOLDIER.get();
         }
      }
   }
}
