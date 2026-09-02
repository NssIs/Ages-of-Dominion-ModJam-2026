package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.RtsVillagerEntity.WorkState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Server-authoritative manual and dawn-only automatic building repairs. */
public final class RtsRepairOrders {
    public static final int MAX_REPAIRERS = 4;
    public static final int HP_PER_WORKER = 2;

    private static final int THINK_INTERVAL = 5;
    private static final int WORK_RADIUS = 128;
    private static final double ARRIVAL_DISTANCE_SQUARED = 16.0D;
    private static final double LEAVE_DISTANCE_SQUARED = 36.0D;
    private static final int SITE_RING_RADIUS = 4;
    private static final Map<UUID, Long> LAST_DAWN_DAY = new HashMap<>();

    private RtsRepairOrders() {
    }

    /** Assigns selected workers immediately; the order is allowed at any time of day. */
    public static void assignRepair(ServerPlayer player, List<Integer> entityIds, long buildingId) {
        if (!validPlayer(player) || entityIds == null || entityIds.isEmpty()
                || entityIds.size() > 64 || buildingId <= 0L) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        RtsBuildingStore.Entry entry = RtsBuildingStore.get(level)
                .find(buildingId, player.getUUID()).orElse(null);
        if (entry == null || !isDamaged(entry)) {
            player.sendOverlayMessage(Component.literal("That building does not need repair."));
            return;
        }
        BlockPos townCenter = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        if (horizontalDistanceSquared(entry.origin(), townCenter) > (double) WORK_RADIUS * WORK_RADIUS) {
            player.sendOverlayMessage(Component.literal("That building lies beyond the realm's work radius."));
            return;
        }

        List<RtsVillagerEntity> candidates = validWorkers(level, townCenter, entityIds);
        List<RtsVillagerEntity> alreadyAssigned = new ArrayList<>();
        List<RtsVillagerEntity> newlyAssigned = new ArrayList<>();
        int available = Math.max(0, MAX_REPAIRERS - countRepairers(level, entry));
        Set<Integer> seen = new HashSet<>();
        for (RtsVillagerEntity worker : candidates) {
            if (!seen.add(worker.getId())) {
                continue;
            }
            if (worker.isRepairWorker() && worker.getRepairBuildingId() == entry.id()) {
                alreadyAssigned.add(worker);
            } else if (available > 0) {
                newlyAssigned.add(worker);
                available--;
            }
        }

        if (newlyAssigned.isEmpty()) {
            if (!alreadyAssigned.isEmpty()) {
                player.sendOverlayMessage(Component.literal("Those workers are already repairing this building."));
            } else {
                player.sendOverlayMessage(Component.literal("That building already has four repairers."));
            }
            return;
        }

        int[] cost = repairCost(entry);
        if (!RtsEconomy.canAfford(player, cost)) {
            player.sendOverlayMessage(Component.literal("Not enough resources to assign repair."));
            return;
        }
        RtsEconomy.spend(player, cost);
        for (RtsVillagerEntity worker : newlyAssigned) {
            assignWorker(level, worker, entry, false);
        }
        int total = newlyAssigned.size() + alreadyAssigned.size();
        player.sendOverlayMessage(Component.literal(total + " worker"
                + (total == 1 ? " is " : "s are ") + "repairing ("
                + countRepairers(level, entry) + " / " + MAX_REPAIRERS + ")."));
    }

    /** Reissues routes for repair jobs saved with a world. */
    public static void restoreAssignments(ServerPlayer player) {
        if (!RtsMode.isActive(player) || !RtsCivilization.isFounded(player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        for (RtsVillagerEntity worker : level.getEntitiesOfClass(
                RtsVillagerEntity.class, new AABB(center).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                candidate -> candidate.isAlive() && candidate.isRepairWorker())) {
            RtsBuildingStore.Entry entry = RtsBuildingStore.get(level)
                    .find(worker.getRepairBuildingId(), player.getUUID()).orElse(null);
            if (entry == null || !isDamaged(entry)) {
                RtsWorkerOrders.cancel(worker);
                continue;
            }
            worker.wearBuilderKit();
            navigateToRepair(level, worker, entry);
        }
    }

    /** Advances movement and restores two HP per active worker every five ticks. */
    public static void tick(ServerPlayer player) {
        if (!validPlayer(player) || player.tickCount % THINK_INTERVAL != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        assignAtDawn(player, level, center);

        Map<Long, Integer> active = new HashMap<>();
        List<RtsVillagerEntity> workers = level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(center).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                worker -> worker.isAlive() && worker.isRepairWorker());
        for (RtsVillagerEntity worker : workers) {
            RtsBuildingStore.Entry entry = RtsBuildingStore.get(level)
                    .find(worker.getRepairBuildingId(), player.getUUID()).orElse(null);
            if (entry == null || !isDamaged(entry)) {
                RtsWorkerOrders.cancel(worker);
                continue;
            }

            if (worker.getWorkState() == WorkState.GOING_TO_REPAIR) {
                BlockPos target = repairTarget(level, entry);
                if (atTarget(worker, target)) {
                    worker.getNavigation().stop();
                    worker.setWorkState(WorkState.REPAIRING);
                } else if (worker.getNavigation().isDone() || worker.getNavigation().isStuck()) {
                    navigateToRepair(level, worker, entry);
                }
            }

            if (worker.getWorkState() != WorkState.REPAIRING) {
                continue;
            }
            if (worker.distanceToSqr(repairTarget(level, entry).getX() + 0.5D,
                    repairTarget(level, entry).getY(), repairTarget(level, entry).getZ() + 0.5D)
                    > LEAVE_DISTANCE_SQUARED) {
                worker.setWorkState(WorkState.GOING_TO_REPAIR);
                navigateToRepair(level, worker, entry);
                continue;
            }
            if (worker.isRepairAutomatic() && !isDaylight(level)) {
                continue;
            }
            worker.getNavigation().stop();
            worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            active.merge(entry.id(), 1, Integer::sum);
        }

        RtsBuildingStore store = RtsBuildingStore.get(level);
        for (Map.Entry<Long, Integer> progress : active.entrySet()) {
            RtsBuildingStore.Entry entry = store.find(progress.getKey(), player.getUUID()).orElse(null);
            if (entry == null || !isDamaged(entry)) {
                continue;
            }
            int health = Math.min(entry.maxHealth(), entry.health()
                    + progress.getValue() * HP_PER_WORKER);
            store.update(RtsBuildingDurability.withHealth(entry, health, entry.maxHealth()));
            if (health >= entry.maxHealth()) {
                releaseWorkers(level, entry.id());
            }
        }
    }

    /** Frees workers when a building is destroyed, leaving their builder designation intact. */
    public static void releaseWorkers(ServerLevel level, long buildingId) {
        if (buildingId <= 0L) {
            return;
        }
        for (RtsVillagerEntity worker : level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(BlockPos.ZERO).inflate(30_000),
                candidate -> candidate.isAlive() && candidate.isRepairWorker()
                        && candidate.getRepairBuildingId() == buildingId)) {
            RtsUnitOrders.clear(worker);
            RtsWorkerOrders.cancel(worker);
        }
    }

    public static void clear() {
        LAST_DAWN_DAY.clear();
    }

    private static void assignAtDawn(ServerPlayer player, ServerLevel level, BlockPos center) {
        if (!isDaylight(level)) {
            return;
        }
        long day = level.getOverworldClockTime() / 24_000L;
        Long previous = LAST_DAWN_DAY.put(player.getUUID(), day);
        if (previous != null && previous == day) {
            return;
        }

        Map<Long, List<RtsVillagerEntity>> jobs = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        List<RtsBuildingStore.Entry> damaged = RtsBuildingStore.get(level).entries().stream()
                .filter(entry -> entry.owner().equals(player.getUUID()) && isDamaged(entry))
                .toList();
        for (RtsVillagerEntity worker : level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(center).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                candidate -> candidate.isAlive() && candidate.getWorkState() == WorkState.IDLE
                        && candidate.isBuilderDesignated())) {
            RtsBuildingStore.Entry nearest = nearestDamaged(worker, damaged, counts);
            if (nearest == null) {
                continue;
            }
            jobs.computeIfAbsent(nearest.id(), ignored -> new ArrayList<>()).add(worker);
            counts.merge(nearest.id(), 1, Integer::sum);
        }

        for (Map.Entry<Long, List<RtsVillagerEntity>> jobEntry : jobs.entrySet()) {
            List<RtsVillagerEntity> job = jobEntry.getValue();
            if (job.isEmpty()) {
                continue;
            }
            RtsBuildingStore.Entry entry = RtsBuildingStore.get(level)
                    .find(jobEntry.getKey(), player.getUUID()).orElse(null);
            if (entry == null) {
                continue;
            }
            int[] cost = repairCost(entry);
            if (!RtsEconomy.canAfford(player, cost)) {
                continue;
            }
            RtsEconomy.spend(player, cost);
            for (RtsVillagerEntity worker : job) {
                assignWorker(level, worker, entry, true);
            }
        }
    }

    private static RtsBuildingStore.Entry nearestDamaged(RtsVillagerEntity worker,
                                                          List<RtsBuildingStore.Entry> damaged,
                                                          Map<Long, Integer> counts) {
        RtsBuildingStore.Entry best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RtsBuildingStore.Entry entry : damaged) {
            if (counts.getOrDefault(entry.id(), countRepairers((ServerLevel) worker.level(), entry))
                    >= MAX_REPAIRERS) {
                continue;
            }
            double distance = horizontalDistanceSquared(worker.blockPosition(), entry.origin());
            if (distance <= (double) WORK_RADIUS * WORK_RADIUS
                    && (best == null || distance < bestDistance
                    || distance == bestDistance && entry.id() < best.id())) {
                best = entry;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void assignWorker(ServerLevel level, RtsVillagerEntity worker,
                                     RtsBuildingStore.Entry entry, boolean automatic) {
        RtsUnitOrders.clear(worker);
        RtsWorkerOrders.cancel(worker);
        worker.wearBuilderKit();
        worker.setBuilderDesignated(true);
        worker.setRepairBuildingId(entry.id());
        worker.setRepairAutomatic(automatic);
        worker.setWorkState(WorkState.GOING_TO_REPAIR);
        navigateToRepair(level, worker, entry);
    }

    private static List<RtsVillagerEntity> validWorkers(ServerLevel level, BlockPos center,
                                                         List<Integer> ids) {
        List<RtsVillagerEntity> workers = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Integer id : ids) {
            if (id == null || !seen.add(id)) {
                continue;
            }
            Entity entity = level.getEntity(id);
            if (entity instanceof RtsVillagerEntity worker && worker.isAlive()
                    && horizontalDistanceSquared(worker.blockPosition(), center)
                    <= (double) RtsEntities.POPULATION_SCAN_RADIUS
                    * RtsEntities.POPULATION_SCAN_RADIUS) {
                workers.add(worker);
            }
        }
        return workers;
    }

    private static int countRepairers(ServerLevel level, RtsBuildingStore.Entry entry) {
        return level.getEntitiesOfClass(RtsVillagerEntity.class,
                new AABB(entry.origin()).inflate(RtsEntities.POPULATION_SCAN_RADIUS + WORK_RADIUS),
                worker -> worker.isAlive() && worker.isRepairWorker()
                        && worker.getRepairBuildingId() == entry.id()).size();
    }

    private static int[] repairCost(RtsBuildingStore.Entry entry) {
        int[] base = BuildingCosts.get(entry.structure()).costs();
        int[] cost = new int[Resource.COUNT];
        long denominator = Math.max(1L, 2L * entry.maxHealth());
        for (Resource resource : Resource.VALUES) {
            long numerator = (long) Math.max(0, base[resource.ordinal()])
                    * Math.max(0, entry.maxHealth() - entry.health());
            cost[resource.ordinal()] = (int) Math.min(Integer.MAX_VALUE,
                    (numerator + denominator - 1L) / denominator);
        }
        return cost;
    }

    private static BlockPos repairTarget(ServerLevel level, RtsBuildingStore.Entry entry) {
        BlockPos origin = RtsBuildingDurability.normalizedOrigin(level, entry);
        Vec3i size = RtsBuildingDurability.rotatedSize(level, entry);
        double centerX = origin.getX() + size.getX() * 0.5D;
        double centerZ = origin.getZ() + size.getZ() * 0.5D;
        for (int radius = 1; radius <= SITE_RING_RADIUS; radius++) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            int minX = origin.getX() - radius;
            int maxX = origin.getX() + size.getX() - 1 + radius;
            int minZ = origin.getZ() - radius;
            int maxZ = origin.getZ() + size.getZ() - 1 + radius;
            for (int x = minX; x <= maxX; x++) {
                best = closerWalkable(level, best, x, minZ, origin.getY(), centerX, centerZ);
                best = closerWalkable(level, best, x, maxZ, origin.getY(), centerX, centerZ);
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                best = closerWalkable(level, best, minX, z, origin.getY(), centerX, centerZ);
                best = closerWalkable(level, best, maxX, z, origin.getY(), centerX, centerZ);
            }
            if (best != null) {
                return best;
            }
        }
        return origin.offset(size.getX() / 2, 0, size.getZ() / 2);
    }

    private static BlockPos closerWalkable(ServerLevel level, BlockPos current, int x, int z,
                                           int preferredY, double centerX, double centerZ) {
        int y = RtsEntities.findBottomFloorY(level, x, z, preferredY);
        BlockPos candidate = new BlockPos(x, y, z);
        if (level.getBlockState(candidate).isAir()
                && level.getBlockState(candidate.above()).isAir()
                && !level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()) {
            double dx = candidate.getX() + 0.5D - centerX;
            double dz = candidate.getZ() + 0.5D - centerZ;
            double distance = dx * dx + dz * dz;
            if (current == null) {
                return candidate;
            }
            double currentDx = current.getX() + 0.5D - centerX;
            double currentDz = current.getZ() + 0.5D - centerZ;
            return distance < currentDx * currentDx + currentDz * currentDz ? candidate : current;
        }
        return current;
    }

    private static void navigateToRepair(ServerLevel level, RtsVillagerEntity worker,
                                         RtsBuildingStore.Entry entry) {
        RtsUnitOrders.moveToSmart(worker, level, repairTarget(level, entry), 0.9D);
    }

    private static boolean atTarget(RtsVillagerEntity worker, BlockPos target) {
        return worker.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D)
                <= ARRIVAL_DISTANCE_SQUARED;
    }

    private static boolean isDamaged(RtsBuildingStore.Entry entry) {
        return entry.maxHealth() > 0 && entry.health() > 0 && entry.health() < entry.maxHealth();
    }

    private static boolean validPlayer(ServerPlayer player) {
        return player != null && RtsMode.isActive(player) && RtsCivilization.isFounded(player)
                && RtsBattle.outcome(player) == RtsBattle.OUTCOME_ONGOING;
    }

    private static boolean isDaylight(ServerLevel level) {
        long phase = Math.floorMod(level.getOverworldClockTime(), 24_000L);
        return phase < 13_000L;
    }

    private static double horizontalDistanceSquared(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }
}
