package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.entity.FallenBruteEntity;
import com.hyrrx.forgottenrealmsrts.entity.FallenKnightEntity;
import com.hyrrx.forgottenrealmsrts.entity.FallenSoldierEntity;
import com.hyrrx.forgottenrealmsrts.entity.RtsArcherEntity;
import com.hyrrx.forgottenrealmsrts.entity.RtsCrossbowmanEntity;
import com.hyrrx.forgottenrealmsrts.entity.RtsRealmDefender;
import com.hyrrx.forgottenrealmsrts.entity.RtsSoldierEntity;
import com.hyrrx.forgottenrealmsrts.entity.RtsSpearmanEntity;
import com.hyrrx.forgottenrealmsrts.entity.SamuraiZombieEntity;
import com.hyrrx.forgottenrealmsrts.entity.SkeletalArcherEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Entities;

public final class RtsEntities {
   public static final int BASE_POPULATION_CAP = 8;
   public static final int HOUSE_POPULATION_BONUS = 5;
   public static final int VILLAGER_FOOD_COST = 20;
   public static final int GUARDIAN_IRON_COST = 15;
   public static final int GUARDIAN_FOOD_COST = 10;
   public static final int CROSSBOWMAN_IRON_COST = 20;
   public static final int CROSSBOWMAN_FOOD_COST = 25;
   public static final int CROSSBOWMAN_WOOD_COST = 10;
   /** A trained fighter occupies two population slots; workers occupy one. */
   public static final int FIGHTER_POPULATION_COST = 2;
   /** The broad bookkeeping radius keeps units near a forward rally point in the population. */
   public static final double POPULATION_SCAN_RADIUS = 256.0D;
   public static final String BARRACKS_PATH = "military/space";
   private static final Entities ENTITY_TYPES = DeferredRegister.createEntities("forgotten_realms_rts");
   public static final DeferredHolder<EntityType<?>, EntityType<RtsVillagerEntity>> RTS_VILLAGER = ENTITY_TYPES.registerEntityType(
      "rts_villager", RtsVillagerEntity::new, MobCategory.CREATURE, builder -> builder.sized(0.6F, 1.95F).clientTrackingRange(10).updateInterval(3)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<RtsSoldierEntity>> RTS_SOLDIER = ENTITY_TYPES.registerEntityType(
      "rts_soldier", RtsSoldierEntity::new, MobCategory.CREATURE, builder -> builder.sized(0.7F, 2.0F).clientTrackingRange(12).updateInterval(3)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<RtsArcherEntity>> RTS_ARCHER = ENTITY_TYPES.registerEntityType(
      "rts_archer", RtsArcherEntity::new, MobCategory.CREATURE, builder -> builder.sized(0.6F, 1.95F).clientTrackingRange(12).updateInterval(3)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<RtsSpearmanEntity>> RTS_SPEARMAN = ENTITY_TYPES.registerEntityType(
      "rts_spearman", RtsSpearmanEntity::new, MobCategory.CREATURE, builder -> builder.sized(0.7F, 2.0F).clientTrackingRange(12).updateInterval(3)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<RtsCrossbowmanEntity>> RTS_CROSSBOWMAN = ENTITY_TYPES.registerEntityType(
      "rts_crossbowman", RtsCrossbowmanEntity::new, MobCategory.CREATURE,
      builder -> builder.sized(0.65F, 1.95F).clientTrackingRange(12).updateInterval(3)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<FallenSoldierEntity>> FALLEN_SOLDIER = ENTITY_TYPES.registerEntityType(
      "fallen_soldier", FallenSoldierEntity::new, MobCategory.MONSTER, builder -> builder.sized(0.6F, 1.95F).clientTrackingRange(10).updateInterval(3)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<SkeletalArcherEntity>> SKELETAL_ARCHER = ENTITY_TYPES.registerEntityType(
      "skeletal_archer", SkeletalArcherEntity::new, MobCategory.MONSTER, builder -> builder.sized(0.6F, 1.95F).clientTrackingRange(10).updateInterval(3)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<SamuraiZombieEntity>> SAMURAI_ZOMBIE = ENTITY_TYPES.registerEntityType(
      "samurai_zombie", SamuraiZombieEntity::new, MobCategory.MONSTER, builder -> builder.sized(0.6F, 1.98F).clientTrackingRange(10).updateInterval(3)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<FallenKnightEntity>> FALLEN_KNIGHT = ENTITY_TYPES.registerEntityType(
      "fallen_knight", FallenKnightEntity::new, MobCategory.MONSTER, builder -> builder.sized(0.7F, 2.0F).clientTrackingRange(10).updateInterval(3)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<FallenBruteEntity>> FALLEN_BRUTE = ENTITY_TYPES.registerEntityType(
      "fallen_brute", FallenBruteEntity::new, MobCategory.MONSTER,
      builder -> builder.sized(1.15F, 2.35F).clientTrackingRange(12).updateInterval(3)
   );

   private RtsEntities() {
   }

   public static void register(IEventBus modEventBus) {
      ENTITY_TYPES.register(modEventBus);
      modEventBus.addListener(RtsEntities::registerAttributes);
   }

   private static void registerAttributes(EntityAttributeCreationEvent event) {
      AttributeSupplier attributes = Villager.createAttributes().build();
      event.put((EntityType)RTS_VILLAGER.get(), attributes);
      event.put((EntityType)RTS_SOLDIER.get(), RtsSoldierEntity.createAttributes().build());
      event.put((EntityType)RTS_ARCHER.get(), RtsArcherEntity.createAttributes().build());
      event.put((EntityType)RTS_SPEARMAN.get(), RtsSpearmanEntity.createAttributes().build());
      event.put((EntityType)RTS_CROSSBOWMAN.get(), RtsCrossbowmanEntity.createAttributes().build());
      event.put((EntityType)FALLEN_SOLDIER.get(), FallenSoldierEntity.createAttributes().build());
      event.put((EntityType)SKELETAL_ARCHER.get(), SkeletalArcherEntity.createAttributes().build());
      event.put((EntityType)SAMURAI_ZOMBIE.get(), SamuraiZombieEntity.createAttributes().build());
      event.put((EntityType)FALLEN_KNIGHT.get(), FallenKnightEntity.createAttributes().build());
      event.put((EntityType)FALLEN_BRUTE.get(), FallenBruteEntity.createAttributes().build());
   }

   /**
    * One source of truth for player-controlled units. The marker covers every recovered custom
    * defender (and future allied defenders), while the explicit Iron Golem check preserves the
    * original guardian path.
    */
   public static boolean isAlliedUnit(Entity entity) {
      return entity instanceof RtsRealmDefender
         || entity instanceof IronGolem golem && golem.isPlayerCreated();
   }

   /** An RTS enemy is a valid cursor target and receives the red health-bar treatment. */
   public static boolean isEnemyUnit(Entity entity) {
      return entity instanceof com.hyrrx.forgottenrealmsrts.entity.RtsEnemyEntity;
   }

   /** The complete mob set owned by this RTS overlay, excluding ordinary wildlife. */
   public static boolean isRtsUnit(Entity entity) {
      return isAlliedUnit(entity) || isEnemyUnit(entity);
   }

   /**
    * The custom RTS units that receive tactical palette treatments. Player-created vanilla Iron
    * Golems remain valid allied units for population and orders, but their normal stone appearance
    * must not be recoloured by the RTS presentation.
    */
   public static boolean isStyledRtsUnit(Entity entity) {
      return isRtsUnit(entity) && !(entity instanceof IronGolem);
   }

   /** Whether a living allied entity belongs to the army rather than the worker population. */
   public static boolean isAlliedCombatUnit(Entity entity) {
      return isAlliedUnit(entity) && !(entity instanceof RtsVillagerEntity);
   }

   /** Identifies the dedicated Crossbowman entity instead of inferring a role from held items. */
   public static boolean isCrossbowman(Entity entity) {
      return entity instanceof RtsCrossbowmanEntity;
   }

   /** Returns the combat units that the RTS command payloads are allowed to order. */
   public static List<Mob> alliedCombatUnits(ServerLevel level, BlockPos center, double radius) {
      return level.getEntitiesOfClass(
         Mob.class,
         new AABB(center).inflate(radius),
         unit -> unit.isAlive() && isAlliedCombatUnit(unit)
      );
   }

   /** Counts all living allied workers and combat units in the settlement's bookkeeping area. */
   public static int countAlliedUnits(ServerLevel level, BlockPos center, double radius) {
      return level.getEntitiesOfClass(
         LivingEntity.class,
         new AABB(center).inflate(radius),
         unit -> unit.isAlive() && isAlliedUnit(unit)
      ).size();
   }

   /** Population slots consumed by one living allied unit. */
   public static int populationCost(Entity entity) {
      if (!isAlliedUnit(entity)) {
         return 0;
      }
      return isAlliedCombatUnit(entity) ? FIGHTER_POPULATION_COST : 1;
   }

   /** Counts population slots rather than bodies, so trained fighters consume two slots. */
   public static int countPopulationUnits(ServerLevel level, BlockPos center, double radius) {
      int population = 0;
      for (LivingEntity unit : level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(radius),
            entity -> entity.isAlive() && isAlliedUnit(entity))) {
         population += populationCost(unit);
      }
      return population;
   }

   /** Whether a tracked structure is one of the small, single-level population houses. */
   public static boolean isHouseStructure(net.minecraft.resources.Identifier structure) {
      return structure != null
         && structure.getPath().contains("villagers/town/house")
         && "house".equals(com.hyrrx.forgottenrealmsrts.network.ModPayloads.buildingOf(structure));
   }

   /** Town Hall capacity plus five population slots per placed House. */
   public static int populationCap(ServerPlayer player) {
      if (!RtsEconomy.townHallPlaced(player)) {
         return 0;
      }
      int houses = 0;
      for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(player.level()).entries()) {
         if (entry.owner().equals(player.getUUID()) && isHouseStructure(entry.structure())) {
            houses++;
         }
      }
      return BASE_POPULATION_CAP + houses * HOUSE_POPULATION_BONUS;
   }

   /**
    * Keeps the synced population attachment honest after deaths or old-world migrations. Training
    * still performs the immediate increment; this periodic reconciliation catches removals without
    * introducing a second population ledger.
    */
   public static void reconcilePopulation(ServerPlayer player, BlockPos center) {
      RtsEconomy.setPopulation(
         player,
         countPopulationUnits(player.level(), center, POPULATION_SCAN_RADIUS),
         populationCap(player)
      );
   }

   /** Removes the player's tracked workers, soldiers, and player-created golems from a fallen town. */
   public static int removeOwnedUnits(ServerLevel level, BlockPos center) {
      List<LivingEntity> units = level.getEntitiesOfClass(
         LivingEntity.class,
         new AABB(center).inflate(POPULATION_SCAN_RADIUS),
         unit -> unit.isAlive() && isAlliedUnit(unit)
      );
      for (LivingEntity unit : units) {
         RtsInvasion.spawnVisualStrike(level, unit.getX(), unit.getY(), unit.getZ());
         unit.discard();
      }
      return units.size();
   }

   public static String unitName(LivingEntity unit) {
      if (unit instanceof RtsVillagerEntity) {
         return "Realm Villager";
      }
      if (unit instanceof IronGolem) {
         return "Stone Guardian";
      }
      if (unit instanceof RtsSoldierEntity soldier) {
         return soldier.isKnight() ? "Knight" : "Man-at-Arms";
      }
      if (unit instanceof RtsArcherEntity) {
         return "Archer";
      }
      if (unit instanceof RtsCrossbowmanEntity) {
         return "Crossbowman";
      }
      if (unit instanceof RtsSpearmanEntity) {
         return "Spearman";
      }
      return unit.getName().getString();
   }

   public static String unitRole(LivingEntity unit) {
      if (unit instanceof RtsVillagerEntity) {
         return "Worker — gathers for the realm";
      }
      if (unit instanceof IronGolem) {
         return "Guardian — defends the realm";
      }
      if (isCrossbowman(unit)) {
         return "Ranged — armored crossbow support";
      }
      if (unit instanceof RtsArcherEntity) {
         return "Ranged — bow support";
      }
      if (unit instanceof RtsSpearmanEntity) {
         return "Melee — holds the line";
      }
      if (unit instanceof RtsSoldierEntity) {
         return unitName(unit).equals("Knight")
            ? "Melee — heavy cavalry" : "Melee — holds the line";
      }
      return "Realm defender";
   }

   /** Applies the approved late-wave Cairn Brute profile to the existing Fallen Knight type. */
   public static void configureCairnBrute(Mob brute) {
      brute.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80.0D);
      brute.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.16D);
      brute.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(9.0D);
      brute.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.9D);
      brute.getAttribute(Attributes.ARMOR).setBaseValue(8.0D);
      brute.setCustomName(Component.literal("Cairn Brute"));
      brute.setCustomNameVisible(false);
      brute.setHealth(brute.getMaxHealth());
   }

   public static boolean ensureTownWorker(ServerPlayer player) {
      if (RtsEconomy.townWorkerSpawned(player)) {
         return true;
      }

      if (!RtsCivilization.isFounded(player)) {
         return false;
      }

      Optional<RtsEntities.TownHallSite> site = locateTownHall(player);
      if (site.isEmpty()) {
         return false;
      }

      RtsEconomy.setTownHallPlaced(player, true);
      ServerLevel level = player.level();
      boolean spawned = placeVillager(level, site.get());
      if (!spawned) {
         return false;
      }

      RtsEconomy.setPopulation(player, 1, BASE_POPULATION_CAP);
      RtsEconomy.setTownWorkerSpawned(player, true);
      player.sendOverlayMessage(Component.literal("Realm Villager spawned"));
      return true;
   }

   /** Gives a newly founded realm its guaranteed worker, swordsman, and archer opening roster. */
   public static int spawnStartingArmy(ServerPlayer player) {
      Optional<TownHallSite> site = locateTownHall(player);
      if (site.isEmpty()) {
         ForgottenRealmsRTS.LOGGER.warn("Could not find a Town Hall for the starting army.");
         return 0;
      }

      ServerLevel level = player.level();
      int teamVariant = teamVariantForFlag(RtsCivilization.flag(player));
      int spawned = 0;

      RtsSoldierEntity soldier = (RtsSoldierEntity)((EntityType)RTS_SOLDIER.get())
         .create(level, EntitySpawnReason.EVENT);
      if (soldier != null) {
         soldier.setVariant(teamVariant);
         if (placeStartingUnit(level, soldier, site.get(), -2,
               new ItemStack(Items.IRON_SWORD))) {
            spawned++;
         }
      }

      RtsArcherEntity archer = (RtsArcherEntity)((EntityType)RTS_ARCHER.get())
         .create(level, EntitySpawnReason.EVENT);
      if (archer != null) {
         archer.setVariant(teamVariant);
         if (placeStartingUnit(level, archer, site.get(), 2,
               new ItemStack(Items.BOW))) {
            spawned++;
         }
      }
      return spawned;
   }

   private static boolean placeStartingUnit(ServerLevel level, Mob unit, TownHallSite site,
                                             int lateralOffset, ItemStack weapon) {
      SpawnPoint point = frontSpawn(site, lateralOffset);
      int x = point.x();
      int z = point.z();
      int y = findBottomFloorY(level, x, z, site.origin().getY() + 1);
      BlockPos spawnPosition = new BlockPos(x, y, z);
      unit.setPersistenceRequired();
      unit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
      unit.setPos(x + 0.5D, y, z + 0.5D);
      unit.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPosition),
         EntitySpawnReason.EVENT, null);
      return level.addFreshEntity(unit);
   }

   public static boolean trainVillager(ServerPlayer player) {
      return trainVillager(player, 0L);
   }

   /** Trains at the selected Town Hall when the request came from its contextual card. */
   public static boolean trainVillager(ServerPlayer player, long requestedTownHallId) {
      if (!RtsCivilization.isFounded(player)) {
         player.sendOverlayMessage(Component.literal("Found your civilization first."));
         return false;
      }

      if (RtsEconomy.population(player) + 1 > RtsEconomy.populationCap(player)) {
         player.sendOverlayMessage(Component.literal("Population is full — you need more housing."));
         return false;
      }

      int[] cost = new int[Resource.COUNT];
      cost[Resource.FOOD.ordinal()] = 20;
      if (!RtsEconomy.canAfford(player, cost)) {
         player.sendOverlayMessage(Component.literal("Not enough food (20) to train a villager."));
         return false;
      }

      Optional<RtsEntities.TownHallSite> site = locateTownHall(player, requestedTownHallId);
      if (site.isEmpty()) {
         player.sendOverlayMessage(Component.literal(requestedTownHallId > 0L
               ? "Select your Town Hall first." : "No Town Hall to train at."));
         return false;
      }

      ServerLevel level = player.level();
      if (!placeVillager(level, site.get())) {
         return false;
      }

      RtsEconomy.spend(player, cost);
      RtsEconomy.setPopulation(player, RtsEconomy.population(player) + 1, RtsEconomy.populationCap(player));
      player.sendOverlayMessage(Component.literal("Villager trained."));
      return true;
   }

   public static Optional<BlockPos> townHallCenter(ServerPlayer player) {
      return locateTownHall(player).map(site -> {
         int cx = site.origin().getX() + site.rotatedSize().getX() / 2;
         int cz = site.origin().getZ() + site.rotatedSize().getZ() / 2;
         int cy = site.origin().getY() + site.rotatedSize().getY() / 2;
         return new BlockPos(cx, cy, cz);
      });
   }

   /**
    * Returns the Town Hall's ground-floor anchor rather than its visual centre. Entity placement and
    * the town patrol director use this anchor so a multi-block hall cannot turn its roof into the
    * unit spawn floor.
    */
   public static Optional<BlockPos> townHallGroundCenter(ServerPlayer player) {
      return locateTownHall(player).map(site -> new BlockPos(
         site.origin().getX() + site.rotatedSize().getX() / 2,
         site.origin().getY() + 1,
         site.origin().getZ() + site.rotatedSize().getZ() / 2
      ));
   }

   /**
    * Finds a walkable feet position close to a known structure floor. The search is deliberately
    * limited to four blocks around the expected floor: a heightmap can select a balcony or roof,
    * which is precisely what made the opening roster appear on the upper level.
    */
   public static int findBottomFloorY(ServerLevel level, int x, int z, int preferredFeetY) {
      level.getChunk(x >> 4, z >> 4);
      for (int distance = 0; distance <= 4; distance++) {
         // Prefer the expected floor and then lower layers. Searching upward first is how a roof
         // or balcony wins when the preferred column is briefly occupied during structure placement.
         int below = preferredFeetY - distance;
         if (isWalkableColumn(level, x, z, below)) {
            return below;
         }
         if (distance > 0) {
            int above = preferredFeetY + distance;
            if (isWalkableColumn(level, x, z, above)) {
               return above;
            }
         }
      }
      return preferredFeetY;
   }

   private static boolean isWalkableColumn(ServerLevel level, int x, int z, int feetY) {
      if (feetY <= level.getMinY() || feetY + 1 >= level.getMaxY()) {
         return false;
      }
      BlockPos feet = new BlockPos(x, feetY, z);
      BlockPos head = feet.above();
      BlockPos support = feet.below();
      return level.getBlockState(feet).isAir()
         && level.getBlockState(head).isAir()
         && !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
   }

   public static List<BlockPos> buildingCenters(ServerPlayer player, String needle) {
      ServerLevel level = player.level();
      List<BlockPos> centers = new ArrayList<>();

      for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
         if (entry.owner().equals(player.getUUID()) && entry.structure().getPath().contains(needle)) {
            Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
            if (!found.isEmpty() && !found.get().palettes.isEmpty()) {
               BlockPos origin = entry.origin();
               StructureTemplate template = found.get();
               if (!entry.normalizedOrigin()) {
                  BlockPos offset = template.getZeroPositionWithTransform(BlockPos.ZERO, Mirror.NONE, entry.rotation());
                  origin = origin.offset(-offset.getX(), -offset.getY(), -offset.getZ());
               }

               Vec3i size = BuildingPlacement.isLinearStructure(entry.structure())
                     ? new Vec3i(1, 1, 1)
                     : BuildingPlacement.rotateSize(template.getSize(), entry.rotation());
               centers.add(new BlockPos(origin.getX() + size.getX() / 2, origin.getY() + size.getY() / 2, origin.getZ() + size.getZ() / 2));
            }
         }
      }

      return centers;
   }

   public static boolean trainGuardian(ServerPlayer player) {
      return trainGuardian(player, false);
   }

   /**
    * Trains through the existing Fighter Cabin path. The boolean is deliberately a small route choice,
    * not a second economy or command system: legacy callers still get the original mixed guardian
    * roll, while the new payload can request the gameplay-only Crossbowman profile.
    */
   public static boolean trainGuardian(ServerPlayer player, boolean crossbowman) {
      if (!RtsCivilization.isFounded(player)) {
         player.sendOverlayMessage(Component.literal("Found your civilization first."));
         return false;
      }

      if (RtsCivilization.age(player) < 1) {
         player.sendOverlayMessage(Component.literal("Reach Feudal Age before training soldiers."));
         return false;
      }

      List<BlockPos> barracks = buildingCenters(player, "military/space");
      if (barracks.isEmpty()) {
         player.sendOverlayMessage(Component.literal("Build a Fighter Cabin first (Feudal Age)."));
         return false;
      }

      if (RtsEconomy.population(player) + FIGHTER_POPULATION_COST > RtsEconomy.populationCap(player)) {
         player.sendOverlayMessage(Component.literal("Population is full — you need more housing."));
         return false;
      }

      int[] cost = new int[Resource.COUNT];
      cost[Resource.IRON.ordinal()] = crossbowman ? CROSSBOWMAN_IRON_COST : GUARDIAN_IRON_COST;
      cost[Resource.FOOD.ordinal()] = crossbowman ? CROSSBOWMAN_FOOD_COST : GUARDIAN_FOOD_COST;
      if (crossbowman) {
         cost[Resource.WOOD.ordinal()] = CROSSBOWMAN_WOOD_COST;
      }
      if (!RtsEconomy.canAfford(player, cost)) {
         player.sendOverlayMessage(Component.literal(crossbowman
            ? "Not enough resources (25 food, 20 iron, 10 wood) to train a Crossbowman."
            : "Not enough resources (15 iron, 10 food) to train a fighter."));
         return false;
      }

      ServerLevel level = player.level();
      BlockPos barracksCenter = barracks.get(0);
      int x = barracksCenter.getX();
      int z = barracksCenter.getZ() + 2;
      int y = findBottomFloorY(level, x, z, barracksCenter.getY() - 1);
      BlockPos spawnPos = new BlockPos(x, y, z);
      RtsSoldierEntity trainedSoldier = null;
      int trainedVariant = 0;
      Mob fighter;
      String announce = "A soldier answers the call.";
      if (crossbowman) {
            RtsCrossbowmanEntity trainedCrossbowman = (RtsCrossbowmanEntity)((EntityType)RTS_CROSSBOWMAN.get()).create(level, EntitySpawnReason.EVENT);
            if (trainedCrossbowman != null) {
               trainedCrossbowman.setVariant(teamVariantForFlag(RtsCivilization.flag(player)));
            }

            fighter = trainedCrossbowman;
         announce = "A crossbowman answers the call.";
      } else {
         int roll = level.getRandom().nextInt(100);
         if (roll < 25) {
            RtsArcherEntity archer = (RtsArcherEntity)((EntityType)RTS_ARCHER.get()).create(level, EntitySpawnReason.EVENT);
            if (archer != null) {
               archer.setVariant(teamVariantForFlag(RtsCivilization.flag(player)));
            }

            fighter = archer;
            announce = "An archer answers the call.";
         } else if (roll < 45) {
            RtsSpearmanEntity spearman = (RtsSpearmanEntity)((EntityType)RTS_SPEARMAN.get()).create(level, EntitySpawnReason.EVENT);
            if (spearman != null) {
               spearman.setVariant(teamVariantForFlag(RtsCivilization.flag(player)));
            }

            fighter = spearman;
            announce = "A spearman answers the call.";
         } else {
            RtsSoldierEntity soldier = (RtsSoldierEntity)((EntityType)RTS_SOLDIER.get()).create(level, EntitySpawnReason.EVENT);
            if (soldier != null) {
               boolean knight = level.getRandom().nextInt(100) < 12;
               trainedVariant = knight ? RtsSoldierEntity.VARIANT_KNIGHT
                  : teamVariantForFlag(RtsCivilization.flag(player));
               soldier.setVariant(trainedVariant);
               trainedSoldier = soldier;
               announce = knight ? "A knight answers the call." : "A man-at-arms answers the call.";
            } else {
               announce = "A soldier answers the call.";
            }

            fighter = soldier;
         }
      }

      if (fighter == null) {
         return false;
      }

      fighter.setPersistenceRequired();
      fighter.setPos(x + 0.5, y, z + 0.5);
      fighter.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.EVENT, null);
      if (trainedSoldier != null) {
         trainedSoldier.setVariant(trainedVariant);
      }

      if (!level.addFreshEntity(fighter)) {
         return false;
      }

      RtsEconomy.spend(player, cost);
      RtsEconomy.setPopulation(player, RtsEconomy.population(player) + FIGHTER_POPULATION_COST,
         RtsEconomy.populationCap(player));
      player.sendOverlayMessage(Component.literal(announce));
      return true;
   }

   private static int teamVariantForFlag(FlagDesign flag) {
      int rgb = FlagDesign.PALETTE[flag.sanitized().primary()] & 16777215;
      int[] anchors = new int[]{2771853, 8003371, 3046726};
      int best = 0;
      long bestDist = Long.MAX_VALUE;

      for (int i = 0; i < anchors.length; i++) {
         long dr = (rgb >> 16 & 0xFF) - (anchors[i] >> 16 & 0xFF);
         long dg = (rgb >> 8 & 0xFF) - (anchors[i] >> 8 & 0xFF);
         long db = (rgb & 0xFF) - (anchors[i] & 0xFF);
         long dist = dr * dr + dg * dg + db * db;
         if (dist < bestDist) {
            bestDist = dist;
            best = i;
         }
      }

      return best;
   }

   private static Optional<RtsEntities.TownHallSite> locateTownHall(ServerPlayer player) {
      return locateTownHall(player, 0L);
   }

   private static Optional<RtsEntities.TownHallSite> locateTownHall(ServerPlayer player,
                                                                      long requestedId) {
      ServerLevel level = player.level();

      for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
         if (entry.owner().equals(player.getUUID())
               && (requestedId <= 0L || entry.id() == requestedId)
               && BuildingCosts.get(entry.structure()).townHall()) {
            Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
            if (!found.isEmpty() && !found.get().palettes.isEmpty()) {
               StructureTemplate template = found.get();
               BlockPos origin = entry.origin();
               if (!entry.normalizedOrigin()) {
                  BlockPos offset = template.getZeroPositionWithTransform(BlockPos.ZERO, Mirror.NONE, entry.rotation());
                  origin = origin.offset(-offset.getX(), -offset.getY(), -offset.getZ());
               }

               Vec3i rotatedSize = BuildingPlacement.rotateSize(template.getSize(), entry.rotation());
               return Optional.of(new RtsEntities.TownHallSite(origin, rotatedSize, entry.rotation()));
            }
         }
      }

      return Optional.empty();
   }

   private static boolean placeVillager(ServerLevel level, RtsEntities.TownHallSite site) {
      BlockPos spawnPosition = frontDoorSpawnPosition(level, site);
      int spawnX = spawnPosition.getX();
      int spawnZ = spawnPosition.getZ();
      int y = spawnPosition.getY();
      RtsVillagerEntity worker = (RtsVillagerEntity)((EntityType)RTS_VILLAGER.get()).create(level, EntitySpawnReason.STRUCTURE);
      if (worker == null) {
         ForgottenRealmsRTS.LOGGER.warn("Could not create a Realm Villager entity.");
         return false;
      } else {
         worker.setPersistenceRequired();
         worker.setPos(spawnX + 0.5D, y, spawnZ + 0.5D);
         worker.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPosition), EntitySpawnReason.STRUCTURE, null);
         if (!level.addFreshEntity(worker)) {
            ForgottenRealmsRTS.LOGGER.warn("Could not add a Realm Villager to the server level.");
            return false;
         } else {
            ForgottenRealmsRTS.LOGGER.info("Spawned a Realm Villager at {}, {}, {}.",
               new Object[]{spawnX + 0.5D, y, spawnZ + 0.5D});
            return true;
         }
      }
   }

   /** Places trained workers on the door frontage without stacking every new hire in one cell. */
   private static BlockPos frontDoorSpawnPosition(ServerLevel level, TownHallSite site) {
      int[] offsets = {0, 1, -1, 2, -2, 3, -3};
      for (int lateralOffset : offsets) {
         SpawnPoint point = frontSpawn(site, lateralOffset);
         int y = findBottomFloorY(level, point.x(), point.z(), site.origin().getY() + 1);
         if (!isWalkableColumn(level, point.x(), point.z(), y)) {
            continue;
         }
         AABB cell = new AABB(point.x(), y, point.z(), point.x() + 1.0D,
               y + 2.0D, point.z() + 1.0D);
         if (level.getEntitiesOfClass(LivingEntity.class, cell, LivingEntity::isAlive).isEmpty()) {
            return new BlockPos(point.x(), y, point.z());
         }
      }

      SpawnPoint fallback = frontSpawn(site, 0);
      return new BlockPos(fallback.x(),
            findBottomFloorY(level, fallback.x(), fallback.z(), site.origin().getY() + 1),
            fallback.z());
   }

   /** Two soldiers can share the Town Hall frontage without being placed inside its footprint. */
   private static SpawnPoint frontSpawn(TownHallSite site, int lateralOffset) {
      BlockPos origin = site.origin();
      Vec3i size = site.rotatedSize();
      int centerX = origin.getX() + size.getX() / 2;
      int centerZ = origin.getZ() + size.getZ() / 2;
      return switch (site.rotation()) {
         case CLOCKWISE_90 -> new SpawnPoint(origin.getX() + size.getX() + 2, centerZ + lateralOffset);
         case CLOCKWISE_180 -> new SpawnPoint(centerX + lateralOffset, origin.getZ() + size.getZ() + 2);
         case COUNTERCLOCKWISE_90 -> new SpawnPoint(origin.getX() - 2, centerZ + lateralOffset);
         default -> new SpawnPoint(centerX + lateralOffset, origin.getZ() - 2);
      };
   }

   private record TownHallSite(BlockPos origin, Vec3i rotatedSize, Rotation rotation) {
   }

   private record SpawnPoint(int x, int z) {
   }
}
