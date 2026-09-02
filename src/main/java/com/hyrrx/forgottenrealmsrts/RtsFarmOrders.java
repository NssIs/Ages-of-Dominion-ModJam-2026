package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.RtsVillagerEntity.WorkState;
import com.hyrrx.forgottenrealmsrts.network.FarmStatusPayload;
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/** Server-authoritative one-worker farms and their timed food output. */
public final class RtsFarmOrders {
    public static final int WORKERS_PER_FARM = 1;
    public static final int FOOD_PER_WORKER = 3;

    private static final int WORK_RADIUS = 128;
    private static final int THINK_INTERVAL = 5;
    private static final int FOOD_INTERVAL_TICKS = 100;
    private static final double DROP_OFF_DISTANCE_SQUARED = 36.0D;
    private static final double FARM_ARRIVAL_DISTANCE_SQUARED = 4.0D;
    private static final int STOCKPILE_CAP = 999_999;
    private static final Map<FarmKey, Long> NEXT_OUTPUT_TICK = new HashMap<>();

    private RtsFarmOrders() {
    }

    public static boolean isFarmStructure(Identifier structure) {
        return structure != null
                && structure.getPath().contains("villagers/town/farm")
                && "farm".equals(ModPayloads.buildingOf(structure));
    }

    /** Builds the live billboard state shown above a selected farm. */
    public static FarmStatusPayload statusFor(ServerLevel level, RtsBuildingStore.Entry entry) {
        if (level == null || entry == null || !isFarmStructure(entry.structure())) {
            return FarmStatusPayload.clear();
        }

        Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
        if (found.isEmpty() || found.get().palettes.isEmpty()) {
            return FarmStatusPayload.clear();
        }

        FarmSite site = farmSite(level, entry, found.get());
        int workersInside = level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                site.bounds(),
                worker -> worker.isAlive()
                        && worker.getFarmBuildingId() == entry.id()
                        && worker.getWorkState() == WorkState.FARMING).size();
        net.minecraft.core.Vec3i size = BuildingPlacement.rotateSize(
                found.get().getSize(), entry.rotation());
        BlockPos origin = normalizedOrigin(found.get(), entry);
        BlockPos displayPos = new BlockPos(
                (int) Math.floor(origin.getX() + size.getX() * 0.5D),
                origin.getY() + size.getY() + 1,
                (int) Math.floor(origin.getZ() + size.getZ() * 0.5D));
        return new FarmStatusPayload(entry.id(), displayPos, workersInside, WORKERS_PER_FARM,
                workersInside * FOOD_PER_WORKER,
                Math.max(1, (FOOD_INTERVAL_TICKS + 19) / 20));
    }

    /** Assigns selected workers to a farm, never exceeding the one-worker limit. */
    public static void assignFarm(ServerPlayer player, List<Integer> entityIds, long buildingId) {
        if (!RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || entityIds == null || entityIds.isEmpty() || buildingId <= 0L) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Optional<RtsBuildingStore.Entry> found = RtsBuildingStore.get(level)
                .find(buildingId, player.getUUID());
        if (found.isEmpty() || !isFarmStructure(found.get().structure())) {
            player.sendOverlayMessage(Component.literal("That is not one of your realm's farms."));
            return;
        }

        RtsBuildingStore.Entry entry = found.get();
        Optional<StructureTemplate> template = RtsStructureTemplates.get(level, entry.structure());
        if (template.isEmpty() || template.get().palettes.isEmpty()) {
            player.sendOverlayMessage(Component.literal("That farm's blueprint cannot be found."));
            return;
        }

        BlockPos townCenter = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        if (horizontalDistanceSquared(entry.origin(), townCenter) > (double) WORK_RADIUS * WORK_RADIUS) {
            player.sendOverlayMessage(Component.literal("That farm lies beyond the realm's work radius."));
            return;
        }

        Set<Integer> selectedIds = new HashSet<>();
        for (Integer entityId : entityIds) {
            if (entityId != null) {
                selectedIds.add(entityId);
            }
        }
        int occupied = countWorkers(level, townCenter, buildingId, selectedIds);
        int available = Math.max(0, WORKERS_PER_FARM - occupied);
        int assigned = 0;
        int alreadyAssigned = 0;

        for (Integer entityId : selectedIds) {
            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof RtsVillagerEntity worker) || !worker.isAlive()
                    || horizontalDistanceSquared(worker.blockPosition(), townCenter)
                    > RtsEntities.POPULATION_SCAN_RADIUS * RtsEntities.POPULATION_SCAN_RADIUS) {
                continue;
            }
            if (worker.getFarmBuildingId() == buildingId
                    && isActiveFarmState(worker.getWorkState())) {
                alreadyAssigned++;
                available = Math.max(0, available - 1);
                continue;
            }
            if (available <= 0) {
                break;
            }

            RtsUnitOrders.clear(worker);
            RtsWorkerOrders.cancel(worker);
            worker.wearFarmerKit();
            worker.setFarmBuildingId(buildingId);
            worker.setWorkState(WorkState.GOING_TO_FARM);
            navigateToFarm(worker, farmInterior(level, entry, template.get()));
            available--;
            assigned++;
        }

        if (assigned > 0 || alreadyAssigned > 0) {
            player.sendOverlayMessage(Component.literal((assigned + alreadyAssigned)
                    + " worker" + (assigned + alreadyAssigned == 1 ? " is " : "s are ")
                    + "assigned to the Farm (1 place)."));
        } else if (occupied >= WORKERS_PER_FARM) {
            player.sendOverlayMessage(Component.literal("The Farm already has its worker."));
        }
    }

    /** Releases the farm worker and walks them back to the Town Hall. */
    public static void recallFarmWorkers(ServerPlayer player, long buildingId) {
        if (!RtsCivilization.isFounded(player) || buildingId <= 0L) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Optional<RtsBuildingStore.Entry> found = RtsBuildingStore.get(level)
                .find(buildingId, player.getUUID());
        if (found.isEmpty() || !isFarmStructure(found.get().structure())) {
            return;
        }
        BlockPos townCenter = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        int recalled = 0;
        for (RtsVillagerEntity worker : level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(townCenter).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                candidate -> candidate.isAlive()
                        && candidate.getFarmBuildingId() == buildingId
                        && isActiveFarmState(candidate.getWorkState()))) {
            beginReturn(level, worker, townCenter);
            recalled++;
        }
        if (recalled > 0) {
            player.sendOverlayMessage(Component.literal(recalled + " worker"
                    + (recalled == 1 ? " is " : "s are ") + "leaving the Farm."));
        } else {
            player.sendOverlayMessage(Component.literal("No worker is tending this Farm."));
        }
    }

    /**
     * Frees every worker assigned to one farm so a demolition does not read as an abandoned
     * building. {@link #tick} re-resolves the entry every pass and would otherwise send these
     * workers marching back to the Town Hall the instant the store entry disappears; this instead
     * drops them to {@code IDLE} exactly where they stand.
     */
    public static void releaseWorkers(ServerLevel level, BlockPos scanCenter, long buildingId) {
        if (buildingId <= 0L) {
            return;
        }
        for (RtsVillagerEntity worker : level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(scanCenter).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                candidate -> candidate.isAlive() && candidate.getFarmBuildingId() == buildingId)) {
            worker.clearWorkAssignment();
        }
    }

    /** Advances farm movement and food production on the same five-tick cadence as mines. */
    public static void tick(ServerPlayer player) {
        if (!RtsMode.isActive(player) || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || player.tickCount % THINK_INTERVAL != 0) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        BlockPos townCenter = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        List<RtsVillagerEntity> workers = level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(townCenter).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                worker -> worker.isAlive() && worker.isFarmWorker());
        Map<FarmKey, Integer> producingWorkers = new HashMap<>();

        for (RtsVillagerEntity worker : workers) {
            if (worker.getWorkState() == WorkState.RETURNING_FARM) {
                tickReturning(level, worker, townCenter);
                continue;
            }

            Optional<RtsBuildingStore.Entry> found = RtsBuildingStore.get(level)
                    .find(worker.getFarmBuildingId(), player.getUUID());
            if (found.isEmpty() || !isFarmStructure(found.get().structure())) {
                beginReturn(level, worker, townCenter);
                continue;
            }

            Optional<StructureTemplate> template = RtsStructureTemplates.get(level, found.get().structure());
            if (template.isEmpty() || template.get().palettes.isEmpty()) {
                beginReturn(level, worker, townCenter);
                continue;
            }

            FarmSite site = farmSite(level, found.get(), template.get());
            if (worker.getWorkState() == WorkState.GOING_TO_FARM) {
                if (site.bounds().contains(worker.getX(), worker.getY(), worker.getZ())
                        && worker.distanceToSqr(site.interior().getX() + 0.5D,
                        site.interior().getY(), site.interior().getZ() + 0.5D)
                        <= FARM_ARRIVAL_DISTANCE_SQUARED) {
                    worker.getNavigation().stop();
                    worker.setWorkState(WorkState.FARMING);
                } else if (worker.getNavigation().isDone() || worker.getNavigation().isStuck()) {
                    navigateToFarm(worker, site.interior());
                }
            }

            if (worker.getWorkState() != WorkState.FARMING) {
                continue;
            }
            if (!site.bounds().contains(worker.getX(), worker.getY(), worker.getZ())) {
                worker.setWorkState(WorkState.GOING_TO_FARM);
                navigateToFarm(worker, site.interior());
                continue;
            }

            worker.getNavigation().stop();
            producingWorkers.merge(new FarmKey(player.getUUID(), found.get().id()), 1, Integer::sum);
        }

        long now = level.getGameTime();
        Set<FarmKey> activeFarms = producingWorkers.keySet();
        for (Map.Entry<FarmKey, Integer> production : producingWorkers.entrySet()) {
            FarmKey key = production.getKey();
            long next = NEXT_OUTPUT_TICK.computeIfAbsent(key, ignored -> now + FOOD_INTERVAL_TICKS);
            if (now < next) {
                continue;
            }
            int output = production.getValue() * FOOD_PER_WORKER;
            int stock = Math.min(STOCKPILE_CAP, RtsEconomy.stock(player, Resource.FOOD) + output);
            RtsEconomy.setStock(player, Resource.FOOD, stock);
            NEXT_OUTPUT_TICK.put(key, now + FOOD_INTERVAL_TICKS);
        }
        NEXT_OUTPUT_TICK.keySet().removeIf(key -> key.owner().equals(player.getUUID())
                && !activeFarms.contains(key));
    }

    public static void clear() {
        NEXT_OUTPUT_TICK.clear();
    }

    private static int countWorkers(ServerLevel level, BlockPos center, long buildingId,
                                    Set<Integer> selectedIds) {
        return level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(center).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                worker -> worker.isAlive()
                        && worker.getFarmBuildingId() == buildingId
                        && isActiveFarmState(worker.getWorkState())
                        && !selectedIds.contains(worker.getId())).size();
    }

    private static boolean isActiveFarmState(WorkState state) {
        return state == WorkState.GOING_TO_FARM || state == WorkState.FARMING;
    }

    private static void beginReturn(ServerLevel level, RtsVillagerEntity worker, BlockPos center) {
        worker.setWorkState(WorkState.RETURNING_FARM);
        navigateToTownHall(level, worker, center);
    }

    private static void tickReturning(ServerLevel level, RtsVillagerEntity worker, BlockPos center) {
        if (worker.distanceToSqr(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D)
                > DROP_OFF_DISTANCE_SQUARED || worker.getNavigation().isStuck()) {
            navigateToTownHall(level, worker, center);
            return;
        }
        worker.getNavigation().stop();
        worker.clearWorkAssignment();
    }

    private static FarmSite farmSite(ServerLevel level, RtsBuildingStore.Entry entry,
                                     StructureTemplate template) {
        BlockPos origin = normalizedOrigin(template, entry);
        net.minecraft.core.Vec3i size = BuildingPlacement.rotateSize(template.getSize(), entry.rotation());
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(),
                origin.getZ() + size.getZ());
        return new FarmSite(bounds, farmInterior(level, entry, template));
    }

    private static BlockPos farmInterior(ServerLevel level, RtsBuildingStore.Entry entry,
                                         StructureTemplate template) {
        BlockPos origin = normalizedOrigin(template, entry);
        net.minecraft.core.Vec3i size = BuildingPlacement.rotateSize(template.getSize(), entry.rotation());
        double centreX = origin.getX() + size.getX() * 0.5D;
        double centreZ = origin.getZ() + size.getZ() * 0.5D;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int y = 0; y < size.getY(); y++) {
            for (int x = 0; x < size.getX(); x++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!isWalkable(level, candidate)) {
                        continue;
                    }
                    double dx = candidate.getX() + 0.5D - centreX;
                    double dz = candidate.getZ() + 0.5D - centreZ;
                    double distance = dx * dx + dz * dz;
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }
        return origin.offset(size.getX() / 2, Math.min(1, Math.max(0, size.getY() - 1)), size.getZ() / 2);
    }

    private static boolean isWalkable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    private static void navigateToFarm(RtsVillagerEntity worker, BlockPos target) {
        if (worker.level() instanceof ServerLevel level) {
            RtsUnitOrders.moveToSmart(worker, level, target, 0.9D);
        }
    }

    private static void navigateToTownHall(ServerLevel level, RtsVillagerEntity worker, BlockPos center) {
        RtsUnitOrders.moveToSmart(worker, level, center, 0.9D);
    }

    private static BlockPos normalizedOrigin(StructureTemplate template, RtsBuildingStore.Entry entry) {
        if (entry.normalizedOrigin()) {
            return entry.origin();
        }
        BlockPos offset = template.getZeroPositionWithTransform(BlockPos.ZERO, Mirror.NONE,
                entry.rotation());
        return entry.origin().offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    private static double horizontalDistanceSquared(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private record FarmKey(UUID owner, long id) {
    }

    private record FarmSite(AABB bounds, BlockPos interior) {
    }
}
