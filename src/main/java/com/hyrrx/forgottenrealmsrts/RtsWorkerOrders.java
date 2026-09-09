package com.hyrrx.forgottenrealmsrts;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import com.hyrrx.forgottenrealmsrts.RtsVillagerEntity.WorkState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.tags.BlockTags;

/** Server-authoritative physical worker orders for the first real resource loop. */
public final class RtsWorkerOrders {
    public static final int WOOD_CAPACITY = 16;

    private static final int WOOD_PER_LOG = 4;
    /** Four visible swings keep the worker readable instead of deleting a log on one invisible tick. */
    private static final int CHOP_INTERVAL_TICKS = 5;
    private static final int CHOP_SWINGS_REQUIRED = 4;
    private static final int WORK_RADIUS = 128;
    private static final int LOG_SEARCH_RADIUS = 8;
    private static final int NEXT_TREE_SEARCH_RADIUS = 32;
    /** The bounded natural-tree envelope reaches 32 blocks above its stable worksite anchor. */
    private static final int TREE_SCAN_HEIGHT = 32;
    /** Initial assignment/recovery can start at a branch; find the rooted component from either side. */
    private static final int TREE_SCAN_BELOW = 32;
    /** The rooted envelope is allowed a small amount below its worksite for roots and leaf cleanup. */
    private static final int ROOT_BELOW = 4;
    /** A reservation never captures more than this many connected log blocks. */
    private static final int MAX_RESERVED_LOGS = 256;
    private static final int LARGE_TREE_LOGS = 12;
    private static final int LARGE_TREE_CAPACITY = 3;
    /** Leaves must not turn an ordinary tree into a tall navigation route. */
    private static final int GROUND_LOG_VERTICAL_REACH = 5;
    /** A route that has not moved for four thinking seconds is considered genuinely blocked. */
    private static final long ROUTE_STALL_TICKS = 80L;
    private static final int THINK_INTERVAL = 5;
    private static final int WAITING_SEARCH_INTERVAL_TICKS = 40;
    /** Lets a lumberjack work the upper logs of a normal tree from the cleared ground pocket. */
    private static final double WORKSITE_DISTANCE_SQUARED = 36.0D;
    /** A worker can swing from the ground pocket without needing to path into the canopy. */
    private static final double GROUND_CHOP_HORIZONTAL_DISTANCE_SQUARED = 16.0D;
    private static final double DROP_OFF_DISTANCE_SQUARED = 36.0D;
    private static final int STOCKPILE_CAP = 999_999;
    /** Every log scan re-checks this cache; a raw tick recompute would price a placed-block edit into every scan. */
    private static final int FOOTPRINT_CACHE_TICKS = 20;
    private static final Map<UUID, Long> NEXT_CHOP_TICK = new HashMap<>();
    private static final Map<UUID, Integer> CHOP_SWINGS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_WOOD_SEARCH_TICK = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_ROUTE_POSITION = new HashMap<>();
    private static final Map<UUID, Long> ROUTE_STATIONARY_SINCE = new HashMap<>();
    /** Runtime-only component captured at assignment; it is deliberately not rebuilt every chop. */
    private static final Map<UUID, WoodReservation> WOOD_RESERVATIONS = new HashMap<>();
    private static final Map<ServerLevel, List<Footprint>> FOOTPRINT_CACHE = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> FOOTPRINT_CACHE_TICK = new WeakHashMap<>();

    private RtsWorkerOrders() {
    }

    /** The blocks a player can identify as a tree with the RTS cursor. */
    public static boolean isTreeBlock(BlockState state) {
        return state != null && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || isSingleBlockWorksite(state));
    }

    /** Full pumpkins and melons are the only crop blocks that lumberjacks can harvest. */
    static boolean isSingleBlockWorksite(BlockState state) {
        return state != null && (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON));
    }

    static boolean isHarvestableWorksite(BlockState state) {
        return isLog(state) || isSingleBlockWorksite(state);
    }

    /** The deterministic resource amount used by both pumpkin and melon worksite handling. */
    static int woodYield(BlockState state) {
        return isHarvestableWorksite(state) ? WOOD_PER_LOG : 0;
    }

    static int foodYield(BlockState state) {
        return isSingleBlockWorksite(state) ? 1 : 0;
    }

    /** Keeps the one/three-worker tree capacity rule in one place for runtime and focused checks. */
    static int capacityForLogCount(int logs) {
        return logs >= LARGE_TREE_LOGS ? LARGE_TREE_CAPACITY : 1;
    }

    /** Assigns the selected workers to the nearest log around the clicked tree block. */
    public static void assignWood(ServerPlayer player, List<Integer> entityIds, BlockPos clicked) {
        if (!RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || entityIds == null || entityIds.isEmpty()) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        BlockPos target = resolveLog(level, clicked);
        if (target == null || horizontalDistanceSquared(target, center) > WORK_RADIUS * WORK_RADIUS) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "That tree is outside the realm's work radius."));
            return;
        }

        boolean singleBlock = isSingleBlockWorksite(level, target);
        TreeInfo tree = singleBlock ? new TreeInfo(target, 0, Set.of()) : treeInfo(level, target);
        if (!singleBlock && (tree.logs() <= 0 || !isNaturalTree(level, tree.root()))) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "That log is not part of a natural tree."));
            return;
        }
        BlockPos worksite = singleBlock ? target : tree.root();
        int capacity = singleBlock ? 1 : capacityForLogCount(tree.logs());
        WoodReservation reservation = singleBlock
                ? WoodReservation.single(target)
                : new WoodReservation(worksite, tree.component(), false);
        int assigned = 0;
        int alreadyAssigned = 0;
        int occupied = countWorkersForTree(level, center, worksite, entityIds);
        Set<Integer> seen = new HashSet<>();
        List<RtsVillagerEntity> eligibleWorkers = new ArrayList<>();
        for (Integer entityId : entityIds) {
            if (entityId == null || !seen.add(entityId)) {
                continue;
            }
            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof RtsVillagerEntity worker) || !worker.isAlive()
                    || horizontalDistanceSquared(worker.blockPosition(), center)
                    > RtsEntities.POPULATION_SCAN_RADIUS * RtsEntities.POPULATION_SCAN_RADIUS) {
                continue;
            }
            eligibleWorkers.add(worker);
            if ((worker.getWorkState() == WorkState.GATHERING_WOOD
                    || worker.getWorkState() == WorkState.RETURNING_WOOD)
                    && worksite.equals(worker.getWoodWorksite())) {
                alreadyAssigned++;
            }
        }

        // `occupied` excludes every selected id so re-issuing the same order is idempotent. Count
        // selected workers that already hold this worksite before assigning new ones; doing it in
        // the same pass as assignment would make the result depend on selection order.
        int available = Math.max(0, capacity - occupied - alreadyAssigned);
        for (RtsVillagerEntity worker : eligibleWorkers) {

            if ((worker.getWorkState() == WorkState.GATHERING_WOOD
                    || worker.getWorkState() == WorkState.RETURNING_WOOD)
                    && worksite.equals(worker.getWoodWorksite())) {
                continue;
            }
            if (available <= 0) {
                break;
            }

            RtsUnitOrders.clear(worker);
            cancel(worker);
            worker.setWoodcutterAssigned(true);
            worker.wearWoodcutterKit();
            worker.setWoodTarget(target);
            worker.setWoodWorksite(worksite);
            WOOD_RESERVATIONS.put(worker.getUUID(), reservation);
            worker.setWorkState(WorkState.GATHERING_WOOD);
            NEXT_CHOP_TICK.put(worker.getUUID(), 0L);
            CHOP_SWINGS.put(worker.getUUID(), 0);
            navigateToWorksite(level, worker, target);
            available--;
            assigned++;
        }

        if (assigned == 0 && alreadyAssigned == 0 && occupied >= capacity) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "That tree is already being felled by " + occupied + " / " + capacity
                            + " lumberjack" + (capacity == 1 ? "." : "s.")));
        }
    }

    /** Rebuilds navigation after a world reload without changing any specialist assignment. */
    public static void restoreAssignments(ServerPlayer player) {
        if (!RtsMode.isActive(player)) {
            return;
        }

        RtsConstructionOrders.restoreAssignments(player);
        if (!RtsCivilization.isFounded(player)) {
            return;
        }

        RtsRepairOrders.restoreAssignments(player);

        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        List<RtsVillagerEntity> workers = level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                worker -> worker.isAlive() && (worker.isWorking() || worker.isWoodcutterAssigned()));
        for (RtsVillagerEntity worker : workers) {
            switch (worker.getWorkState()) {
                case GATHERING_WOOD -> {
                    worker.setWoodcutterAssigned(true);
                    RtsUnitOrders.clear(worker);
                    BlockPos target = worker.getWoodTarget();
                    // A save/reload (or a building placed since) must not resume a chop into a
                    // now-protected beam; the harvest predicate re-validates both facts, not just the tag.
                    if (target != null && isHarvestableTarget(level, target)
                            && ensureReservation(level, worker, target) != null) {
                        worker.wearWoodcutterKit();
                        NEXT_CHOP_TICK.put(worker.getUUID(), 0L);
                        navigateToWorksite(level, worker, target);
                    } else {
                        // A missing reservation is rebuilt only when the persisted target/worksite
                        // still validates. If that proof is unavailable, stop/return safely rather
                        // than scanning the world and admitting a newly placed beam.
                        finishOrReturn(level, worker, center);
                    }
                }
                case RETURNING_WOOD -> {
                    worker.setWoodcutterAssigned(true);
                    RtsUnitOrders.clear(worker);
                    worker.wearWoodcutterKit();
                    navigateToDropOff(level, worker, center);
                }
                case IDLE -> {
                    if (worker.isWoodcutterAssigned()) {
                        RtsUnitOrders.clear(worker);
                        resumeWaitingWoodcutter(level, worker, center, true);
                    }
                }
                default -> {
                    // Mine and farm state is persistent; their five-tick directors validate the
                    // building and issue a fresh route on the next cadence. Manual move/attack
                    // orders intentionally remain runtime orders and are not stale-save restored.
                }
            }
        }
    }

    /** Advances every assigned worker in the player's settlement. */
    public static void tick(ServerPlayer player) {
        if (!RtsMode.isActive(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || player.tickCount % THINK_INTERVAL != 0) {
            return;
        }

        // Foundations can begin before the Town Hall's founding form is confirmed. Keep this ahead
        // of the founded gate so the first Town Hall never deadlocks, while the established resource
        // directors below retain their existing founded-only behavior.
        RtsConstructionOrders.tick(player);
        if (!RtsCivilization.isFounded(player)) {
            return;
        }

        RtsRepairOrders.tick(player);

        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        List<RtsVillagerEntity> workers = level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                worker -> worker.isAlive() && (worker.isWorking() || worker.isWoodcutterAssigned()));
        for (RtsVillagerEntity worker : workers) {
            WorkState state = worker.getWorkState();
            if (state != WorkState.GATHERING_WOOD) {
                // A reload or a cancelled order must never leave a temporary column in the world.
                worker.clearScaffolding();
            }
            if (state == WorkState.RETURNING_WOOD) {
                tickReturning(player, level, worker, center);
            } else if (state == WorkState.GATHERING_WOOD) {
                tickGathering(level, worker, center);
            } else if (state == WorkState.IDLE && worker.isWoodcutterAssigned()) {
                tickWaitingWoodcutter(level, worker, center);
            }
        }
        RtsMineOrders.tick(player);
        RtsFarmOrders.tick(player);
    }

    /** Stops a worker's assignment when the commander issues a normal move or hold order. */
    public static void cancel(RtsVillagerEntity worker) {
        clearChopProgress(worker);
        NEXT_CHOP_TICK.remove(worker.getUUID());
        NEXT_WOOD_SEARCH_TICK.remove(worker.getUUID());
        clearRouteProgress(worker);
        WOOD_RESERVATIONS.remove(worker.getUUID());
        worker.clearWorkAssignment();
    }

    public static void clear() {
        NEXT_CHOP_TICK.clear();
        CHOP_SWINGS.clear();
        NEXT_WOOD_SEARCH_TICK.clear();
        LAST_ROUTE_POSITION.clear();
        ROUTE_STATIONARY_SINCE.clear();
        WOOD_RESERVATIONS.clear();
        FOOTPRINT_CACHE.clear();
        FOOTPRINT_CACHE_TICK.clear();
        RtsMineOrders.clear();
        RtsFarmOrders.clear();
        RtsConstructionOrders.clear();
        RtsRepairOrders.clear();
    }

    private static void tickGathering(ServerLevel level, RtsVillagerEntity worker, BlockPos center) {
        BlockPos target = worker.getWoodTarget();
        WoodReservation reservation = ensureReservation(level, worker, target);
        if (reservation == null) {
            finishOrReturn(level, worker, center);
            return;
        }

        BlockPos worksite = reservation.anchor();
        if (!worksite.equals(worker.getWoodWorksite())) {
            worker.setWoodWorksite(worksite);
        }
        if (target == null || !isReservedWorksiteTarget(level, reservation, target)) {
            BlockPos next = findNextLog(level, target == null ? worksite : target, worksite, center, worker);
            if (next == null) {
                clearLeavesAround(level, worksite);
                finishOrReturn(level, worker, center);
                return;
            }
            clearChopProgress(worker);
            setReservedTarget(worker, next);
            target = next;
            worksite = worker.getWoodWorksite();
        }
        if (target == null || horizontalDistanceSquared(target, center) > WORK_RADIUS * WORK_RADIUS) {
            finishOrReturn(level, worker, center);
            return;
        }

        // Re-checked every tick, not just on assignment: a building finished mid-chop must knock
        // the target out from under a worker instead of letting it destroy the new wall.
        if (!isReservedWorksiteTarget(level, reservationForWorker(level, worker), target)
                || !isHarvestableTarget(level, target)) {
            BlockPos next = findNextLog(level, target, worksite, center, worker);
            if (next == null) {
                clearLeavesAround(level, worksite);
                finishOrReturn(level, worker, center);
                return;
            }
            clearChopProgress(worker);
            setReservedTarget(worker, next);
            target = next;
            worksite = worker.getWoodWorksite();
        }

        // Do not make a woodcutter walk into leaves just because the selected log is above the
        // trunk base. The cleared ground pocket is the intended work position for ordinary trees.
        boolean groundChopRange = canChopFromGround(level, worker, target, worksite);
        if (groundChopRange) {
            worker.getNavigation().stop();
            clearRouteProgress(worker);
        } else if (worker.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D)
                > WORKSITE_DISTANCE_SQUARED) {
            boolean stalled = navigationStalled(level, worker);
            if (stalled || worker.getNavigation().isStuck()
                    || worker.getNavigation().isDone()) {
                if (stalled || worker.getNavigation().isStuck()) {
                    recoverStalledRoute(level, worker, target, worksite, center);
                    return;
                }
                navigateToWorksite(level, worker, target);
            }
            return;
        }
        clearRouteProgress(worker);
        if (!groundChopRange && worker.getNavigation().isStuck()) {
            navigateToWorksite(level, worker, target);
            return;
        }

        long now = level.getGameTime();
        if (now < NEXT_CHOP_TICK.getOrDefault(worker.getUUID(), 0L)) {
            return;
        }

        worker.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D);
        worker.swing(InteractionHand.MAIN_HAND);
        int swings = CHOP_SWINGS.merge(worker.getUUID(), 1, Integer::sum);
        level.destroyBlockProgress(worker.getId(), target,
                Math.min(9, swings * 10 / CHOP_SWINGS_REQUIRED));
        if (swings < CHOP_SWINGS_REQUIRED) {
            NEXT_CHOP_TICK.put(worker.getUUID(), now + CHOP_INTERVAL_TICKS);
            return;
        }

        BlockState choppedState = level.getBlockState(target);
        level.levelEvent(2001, target, Block.getId(choppedState));
        level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.destroyBlockProgress(worker.getId(), target, -1);
        CHOP_SWINGS.remove(worker.getUUID());
        worker.setCarriedWood(Math.min(WOOD_CAPACITY,
                worker.getCarriedWood() + woodYield(choppedState)));
        int food = foodYield(choppedState);
        if (food > 0) {
            worker.setCarriedFood(worker.getCarriedFood() + food);
        }
        NEXT_CHOP_TICK.put(worker.getUUID(), now + CHOP_INTERVAL_TICKS);

        if (worker.getCarriedWood() >= WOOD_CAPACITY) {
            worker.clearScaffolding();
            worker.setWorkState(WorkState.RETURNING_WOOD);
            navigateToDropOff(level, worker, center);
            return;
        }

        // A full pumpkin or melon is a single-block worksite, unlike a connected tree. Once it is
        // picked, return the one worker with both resources instead of reserving a neighbour.
        if (food > 0) {
            finishOrReturn(level, worker, center);
            return;
        }

        BlockPos next = findNextLog(level, target, worksite, center, worker);
        if (next == null) {
            clearLeavesAround(level, worksite);
            finishOrReturn(level, worker, center);
        } else {
            setReservedTarget(worker, next);
            navigateToWorksite(level, worker, next);
        }
    }

    private static void tickReturning(ServerPlayer player, ServerLevel level,
                                      RtsVillagerEntity worker, BlockPos center) {
        worker.clearScaffolding();
        clearRouteProgress(worker);
        if (worker.distanceToSqr(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D)
                > DROP_OFF_DISTANCE_SQUARED) {
            if (worker.getNavigation().isDone() || worker.getNavigation().isStuck()) {
                navigateToDropOff(level, worker, center);
            }
            return;
        }
        if (worker.getNavigation().isStuck()) {
            navigateToDropOff(level, worker, center);
            return;
        }

        int carriedWood = worker.getCarriedWood();
        int carriedFood = worker.getCarriedFood();
        if (carriedWood > 0) {
            int next = Math.min(STOCKPILE_CAP,
                    RtsEconomy.stock(player, Resource.WOOD) + carriedWood);
            RtsEconomy.setStock(player, Resource.WOOD, next);
        }
        if (carriedFood > 0) {
            int next = Math.min(STOCKPILE_CAP,
                    RtsEconomy.stock(player, Resource.FOOD) + carriedFood);
            RtsEconomy.setStock(player, Resource.FOOD, next);
        }

        BlockPos previousTarget = worker.getWoodTarget();
        BlockPos worksite = worker.getWoodWorksite();
        worker.setCarriedWood(0);
        worker.setCarriedFood(0);
        if (previousTarget != null && worksite != null && carriedFood <= 0) {
            BlockPos next = findNextLog(level, previousTarget, worksite, center, worker);
            if (next != null && horizontalDistanceSquared(next, center) <= WORK_RADIUS * WORK_RADIUS) {
                setReservedTarget(worker, next);
                worker.setWorkState(WorkState.GATHERING_WOOD);
                navigateToWorksite(level, worker, next);
                return;
            }
            clearLeavesAround(level, worksite == null ? previousTarget : worksite);
        }
        worker.setWoodTarget(null);
        worker.setWoodWorksite(null);
        worker.setWorkState(WorkState.IDLE);
        NEXT_CHOP_TICK.remove(worker.getUUID());
        WOOD_RESERVATIONS.remove(worker.getUUID());
        NEXT_WOOD_SEARCH_TICK.put(worker.getUUID(), level.getGameTime() + WAITING_SEARCH_INTERVAL_TICKS);
    }

    /** Keeps an assigned lumberjack at the hall until a nearby tree becomes available. */
    private static void tickWaitingWoodcutter(ServerLevel level, RtsVillagerEntity worker,
                                               BlockPos center) {
        long now = level.getGameTime();
        if (now < NEXT_WOOD_SEARCH_TICK.getOrDefault(worker.getUUID(), 0L)) {
            return;
        }
        NEXT_WOOD_SEARCH_TICK.put(worker.getUUID(), now + WAITING_SEARCH_INTERVAL_TICKS);
        resumeWaitingWoodcutter(level, worker, center, false);
    }

    private static void resumeWaitingWoodcutter(ServerLevel level, RtsVillagerEntity worker,
                                                BlockPos center, boolean routeNow) {
        worker.setWoodcutterAssigned(true);
        worker.wearWoodcutterKit();
        worker.clearScaffolding();
        clearRouteProgress(worker);
        if (worker.getCarriedWood() > 0 || worker.getCarriedFood() > 0) {
            worker.setWorkState(WorkState.RETURNING_WOOD);
            navigateToDropOff(level, worker, center);
            return;
        }

        BlockPos next = findNextLog(level, worker.blockPosition(), null, center, worker);
        if (next != null) {
            setReservedTarget(worker, next);
            worker.setWorkState(WorkState.GATHERING_WOOD);
            NEXT_CHOP_TICK.put(worker.getUUID(), level.getGameTime());
            NEXT_WOOD_SEARCH_TICK.remove(worker.getUUID());
            navigateToWorksite(level, worker, next);
            return;
        }

        worker.setWoodTarget(null);
        worker.setWoodWorksite(null);
        worker.setWorkState(WorkState.IDLE);
        WOOD_RESERVATIONS.remove(worker.getUUID());
        NEXT_WOOD_SEARCH_TICK.put(worker.getUUID(),
                level.getGameTime() + WAITING_SEARCH_INTERVAL_TICKS);
        if (routeNow || worker.distanceToSqr(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D)
                > DROP_OFF_DISTANCE_SQUARED || worker.getNavigation().isDone()
                || worker.getNavigation().isStuck()) {
            navigateToDropOff(level, worker, center);
        }
    }

    /** Returns a worker to the hall when its assigned grove is exhausted, even with empty hands. */
    private static void finishOrReturn(ServerLevel level, RtsVillagerEntity worker, BlockPos center) {
        clearChopProgress(worker);
        worker.clearScaffolding();
        clearRouteProgress(worker);
        if (worker.getCarriedWood() > 0 || worker.getCarriedFood() > 0) {
            worker.setWorkState(WorkState.RETURNING_WOOD);
            navigateToDropOff(level, worker, center);
        } else {
            worker.setWoodTarget(null);
            worker.setWoodWorksite(null);
            worker.setWorkState(WorkState.IDLE);
            NEXT_CHOP_TICK.remove(worker.getUUID());
            WOOD_RESERVATIONS.remove(worker.getUUID());
            NEXT_WOOD_SEARCH_TICK.put(worker.getUUID(),
                    level.getGameTime() + WAITING_SEARCH_INTERVAL_TICKS);
            navigateToDropOff(level, worker, center);
        }
    }

    /** Removes the client-visible block crack when a worker receives a new order or the tree ends. */
    private static void clearChopProgress(RtsVillagerEntity worker) {
        if (worker == null) {
            return;
        }
        BlockPos target = worker.getWoodTarget();
        if (target != null && worker.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(worker.getId(), target, -1);
        }
        CHOP_SWINGS.remove(worker.getUUID());
    }

    /** Detects a real navigation stall without treating a worker's chopping animation as one. */
    private static boolean navigationStalled(ServerLevel level, RtsVillagerEntity worker) {
        long now = level.getGameTime();
        BlockPos current = worker.blockPosition();
        BlockPos previous = LAST_ROUTE_POSITION.put(worker.getUUID(), current);
        if (previous == null || !previous.equals(current)) {
            ROUTE_STATIONARY_SINCE.put(worker.getUUID(), now);
            return false;
        }
        long stationarySince = ROUTE_STATIONARY_SINCE.computeIfAbsent(
                worker.getUUID(), ignored -> now);
        return now - stationarySince >= ROUTE_STALL_TICKS;
    }

    private static void rememberRouteProgress(ServerLevel level, RtsVillagerEntity worker) {
        BlockPos current = worker.blockPosition();
        BlockPos previous = LAST_ROUTE_POSITION.put(worker.getUUID(), current);
        if (previous == null || !previous.equals(current)) {
            ROUTE_STATIONARY_SINCE.put(worker.getUUID(), level.getGameTime());
        } else {
            // Re-issuing the same finished/blocked route must not reset the stall timer forever.
            ROUTE_STATIONARY_SINCE.putIfAbsent(worker.getUUID(), level.getGameTime());
        }
    }

    private static void clearRouteProgress(RtsVillagerEntity worker) {
        if (worker != null) {
            LAST_ROUTE_POSITION.remove(worker.getUUID());
            ROUTE_STATIONARY_SINCE.remove(worker.getUUID());
        }
    }

    /** Clears a small working pocket so foliage cannot strand the worker or trigger a tower build. */
    private static void clearLeavesForAccess(ServerLevel level, BlockPos worksite, BlockPos log) {
        clearLeavesAt(level, worksite);
        if (log != null && !log.equals(worksite)) {
            clearLeavesAt(level, log);
        }
    }

    private static void clearLeavesAt(ServerLevel level, BlockPos center) {
        if (center == null) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos position = center.offset(dx, dy, dz);
                    // A garden or a roof can wear real leaf blocks; clearing a work pocket is not a
                    // license to bulldoze a neighbour's decoration.
                    if (level.getBlockState(position).is(BlockTags.LEAVES)
                            && !isProtected(level, position)) {
                        level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /** A blocked upper-log route falls back to the lowest remaining log instead of looping forever. */
    private static void recoverStalledRoute(ServerLevel level, RtsVillagerEntity worker,
                                            BlockPos target, BlockPos worksite, BlockPos center) {
        worker.clearScaffolding();
        RtsUnitOrders.clearNavigation(worker);
        clearRouteProgress(worker);

        WoodReservation reservation = reservationForWorker(level, worker);
        BlockPos lowest = reservation == null || reservation.singleBlock()
                ? null
                : findLowestReservedLog(level, reservation, target);
        if (lowest != null) {
            worker.setWoodTarget(lowest);
            worker.setWoodWorksite(reservation.anchor());
            worker.setWorkState(WorkState.GATHERING_WOOD);
            navigateToWorksite(level, worker, lowest);
            return;
        }

        finishOrReturn(level, worker, center);
    }

    private static void navigateToWorksite(ServerLevel level, RtsVillagerEntity worker, BlockPos log) {
        // Leaves are cleared only as part of the existing tree harvest; temporary scaffolding is
        // never placed as a navigation fallback.
        WoodReservation reservation = reservationForWorker(level, worker);
        if (reservation == null) {
            worker.getNavigation().stop();
            RtsUnitOrders.clearNavigation(worker);
            return;
        }
        BlockPos worksite = reservation.anchor();
        worker.setWoodWorksite(worksite);

        clearLeavesForAccess(level, worksite, log);
        BlockPos approach = approachPosition(level, worksite);
        worker.clearScaffolding();
        if (approach == null) {
            worker.getNavigation().stop();
            RtsUnitOrders.clearNavigation(worker);
            return;
        }
        RtsUnitOrders.moveToSmart(worker, level, approach, 0.9D);
        rememberRouteProgress(level, worker);
    }

    private static void navigateToDropOff(ServerLevel level, RtsVillagerEntity worker, BlockPos center) {
        BlockPos approach = approachPosition(level, center);
        if (approach == null) {
            worker.getNavigation().stop();
            RtsUnitOrders.clearNavigation(worker);
            return;
        }
        RtsUnitOrders.moveToSmart(worker, level, approach, 0.9D);
    }

    private static BlockPos approachPosition(ServerLevel level, BlockPos target) {
        if (target == null) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = target.getX() + dx;
                    int z = target.getZ() + dz;
                    int y = RtsEntities.findBottomFloorY(level, x, z, target.getY());
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (RtsNavigationSafety.isSafeFeet(level, candidate)) {
                        double distance = candidate.distSqr(target);
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
        }
        return best;
    }

    private static boolean canChopFromGround(ServerLevel level, RtsVillagerEntity worker,
                                             BlockPos target,
                                             BlockPos worksite) {
        if (target == null || worksite == null
                || !RtsNavigationSafety.isSafeFeet(level, worker.blockPosition())) {
            return false;
        }
        int targetRise = target.getY() - worksite.getY();
        int workerRise = worker.blockPosition().getY() - worksite.getY();
        return targetRise >= 0 && targetRise <= GROUND_LOG_VERTICAL_REACH
                && workerRise >= -1 && workerRise <= GROUND_LOG_VERTICAL_REACH
                && horizontalDistanceSquared(worker.blockPosition(), target)
                <= GROUND_CHOP_HORIZONTAL_DISTANCE_SQUARED;
    }

    private static BlockPos resolveLog(ServerLevel level, BlockPos clicked) {
        // The manual click path stays permissive about what counts as a harvest target (the player
        // picked it, after all), but a building beam is never legal regardless of who clicked it.
        if (clicked == null || !isTreeBlock(level.getBlockState(clicked)) || isProtected(level, clicked)) {
            return null;
        }
        if (isSingleBlockWorksite(level, clicked)) {
            return clicked;
        }
        return isLog(level.getBlockState(clicked)) ? clicked : findNearbyLog(level, clicked, null);
    }

    private static BlockPos findNearbyLog(ServerLevel level, BlockPos origin, BlockPos worksite) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -LOG_SEARCH_RADIUS; dx <= LOG_SEARCH_RADIUS; dx++) {
            for (int dy = -TREE_SCAN_BELOW; dy <= TREE_SCAN_HEIGHT; dy++) {
                for (int dz = -LOG_SEARCH_RADIUS; dz <= LOG_SEARCH_RADIUS; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isChoppableLog(level, candidate) || !isNaturalTree(level, candidate)) {
                        continue;
                    }
                    if (worksite != null && !withinSameTreeEnvelope(candidate, worksite)) {
                        continue;
                    }
                    double distance = candidate.distSqr(origin);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Chooses a deterministic nearest member of an already captured component. The predicate is
     * evaluated against the live world, so a protected or removed log is never chopped.
     */
    static BlockPos chooseNearestReservedLog(Set<BlockPos> reserved, BlockPos origin,
                                             Predicate<BlockPos> available) {
        if (reserved == null || reserved.isEmpty() || available == null) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : reserved) {
            if (candidate == null || !available.test(candidate)) {
                continue;
            }
            double distance = origin == null ? 0.0D : candidate.distSqr(origin);
            if (best == null || distance < bestDistance
                    || distance == bestDistance && comparePositions(candidate, best) < 0) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Chooses the lowest captured log for route recovery, with stable tie-breaking. */
    static BlockPos chooseLowestReservedLog(Set<BlockPos> reserved, BlockPos origin,
                                            Predicate<BlockPos> available) {
        if (reserved == null || reserved.isEmpty() || available == null) {
            return null;
        }
        BlockPos best = null;
        for (BlockPos candidate : reserved) {
            if (candidate == null || !available.test(candidate)) {
                continue;
            }
            if (best == null || candidate.getY() < best.getY()) {
                best = candidate;
                continue;
            }
            if (candidate.getY() == best.getY()) {
                double candidateDistance = origin == null ? 0.0D : candidate.distSqr(origin);
                double bestDistance = origin == null ? 0.0D : best.distSqr(origin);
                if (candidateDistance < bestDistance
                        || candidateDistance == bestDistance
                        && comparePositions(candidate, best) < 0) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static BlockPos findLowestReservedLog(ServerLevel level, WoodReservation reservation,
                                                  BlockPos origin) {
        return chooseLowestReservedLog(reservation.component(), origin,
                candidate -> isChoppableLog(level, candidate));
    }

    private static int comparePositions(BlockPos first, BlockPos second) {
        int y = Integer.compare(first.getY(), second.getY());
        if (y != 0) {
            return y;
        }
        int x = Integer.compare(first.getX(), second.getX());
        return x != 0 ? x : Integer.compare(first.getZ(), second.getZ());
    }

    /** Finds another tree after the current one ends, respecting the one-worker/large-tree slots. */
    private static BlockPos findNextLog(ServerLevel level, BlockPos origin, BlockPos worksite,
                                        BlockPos center, RtsVillagerEntity worker) {
        if (origin == null) {
            return null;
        }

        WoodReservation reservation = reservationForWorker(level, worker);
        if (worksite != null && reservation == null) {
            // A persisted assignment whose component cannot be reconstructed is intentionally a
            // safe stop. Never replace that proof with a fresh world scan.
            return null;
        }
        if (reservation != null) {
            if (reservation.singleBlock()) {
                return null;
            }
            BlockPos reserved = chooseNearestReservedLog(reservation.component(), origin,
                    candidate -> isChoppableLog(level, candidate));
            if (reserved != null) {
                return reserved;
            }
        }

        BlockPos best = null;
        WoodReservation bestReservation = null;
        double bestDistance = Double.MAX_VALUE;
        Set<Long> inspectedTrees = new HashSet<>();
        for (int dx = -NEXT_TREE_SEARCH_RADIUS; dx <= NEXT_TREE_SEARCH_RADIUS; dx++) {
            for (int dy = -TREE_SCAN_BELOW; dy <= TREE_SCAN_HEIGHT; dy++) {
                for (int dz = -NEXT_TREE_SEARCH_RADIUS; dz <= NEXT_TREE_SEARCH_RADIUS; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isChoppableLog(level, candidate)) {
                        continue;
                    }
                    // Only inspect likely tree bases here. A dense canopy can contain hundreds of
                    // branch logs; running a connected-tree flood fill for every one made a worker
                    // search expensive enough to show up as movement lag.
                    if (isLog(level.getBlockState(candidate.below()))) {
                        continue;
                    }
                    // A structure beam standing clear of any footprint (an overhang, a doorway
                    // lintel) still is not a tree unless it plausibly grew there.
                    if (!isNaturalTree(level, candidate)) {
                        continue;
                    }
                    TreeInfo tree = treeInfo(level, candidate);
                    if (tree.logs() <= 0 || !isNaturalTree(level, tree.root())
                            || worksite != null && (worksite.equals(tree.root())
                            || withinSameTreeEnvelope(tree.root(), worksite))
                            || !inspectedTrees.add(tree.root().asLong())) {
                        continue;
                    }
                    if (horizontalDistanceSquared(tree.root(), center) > WORK_RADIUS * WORK_RADIUS) {
                        continue;
                    }
                    int capacity = capacityForLogCount(tree.logs());
                    if (countWorkersForTree(level, center, tree.root(), List.of(worker.getId()))
                            >= capacity) {
                        continue;
                    }
                    WoodReservation candidateReservation = new WoodReservation(tree.root(),
                            tree.component(), false);
                    BlockPos remaining = chooseNearestReservedLog(candidateReservation.component(), origin,
                            position -> isChoppableLog(level, position));
                    if (remaining == null) {
                        continue;
                    }
                    double distance = remaining.distSqr(origin);
                    if (distance < bestDistance) {
                        best = remaining;
                        bestReservation = candidateReservation;
                        bestDistance = distance;
                    }
                }
            }
        }
        if (bestReservation != null) {
            WOOD_RESERVATIONS.put(worker.getUUID(), bestReservation);
        }
        return best;
    }

    /**
     * Vanilla leaf decay waits for its normal distance pass. A felled RTS tree should read as felled
     * immediately, so clear the canopy that grew from this trunk. A flat box scan (the old approach)
     * deleted every leaf block in range, including a garden or roof leaf that just happened to sit
     * near a stump; walking the connected leaf blob keeps the clear to this tree's own canopy, and
     * the footprint check keeps it out of a building even if a leaf is directly adjacent to one.
     */
    private static void clearLeavesAround(ServerLevel level, BlockPos trunk) {
        if (trunk == null) {
            return;
        }
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        for (Direction direction : Direction.values()) {
            open.add(trunk.relative(direction));
        }
        while (!open.isEmpty() && visited.size() < 256) {
            BlockPos current = open.removeFirst();
            if (!visited.add(current.asLong())) {
                continue;
            }
            if (Math.abs(current.getX() - trunk.getX()) > LOG_SEARCH_RADIUS
                    || Math.abs(current.getZ() - trunk.getZ()) > LOG_SEARCH_RADIUS
                    || current.getY() < trunk.getY() - ROOT_BELOW
                    || current.getY() > trunk.getY() + TREE_SCAN_HEIGHT) {
                continue;
            }
            if (!level.getBlockState(current).is(BlockTags.LEAVES) || isProtected(level, current)) {
                continue;
            }
            level.setBlock(current, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            for (Direction direction : Direction.values()) {
                open.addLast(current.relative(direction));
            }
        }
    }

    private static boolean isLog(BlockState state) {
        return state != null && state.is(BlockTags.LOGS);
    }

    private static boolean isSingleBlockWorksite(ServerLevel level, BlockPos pos) {
        return pos != null && isSingleBlockWorksite(level.getBlockState(pos))
                && !isProtected(level, pos);
    }

    private static boolean isHarvestableTarget(ServerLevel level, BlockPos pos) {
        return pos != null && !isProtected(level, pos)
                && isHarvestableWorksite(level.getBlockState(pos));
    }

    /** Returns the worker's captured component, rebuilding it only when a save/reload lost runtime state. */
    private static WoodReservation reservationForWorker(ServerLevel level, RtsVillagerEntity worker) {
        if (worker == null) {
            return null;
        }
        WoodReservation existing = WOOD_RESERVATIONS.get(worker.getUUID());
        if (existing != null) {
            BlockPos worksite = worker.getWoodWorksite();
            if (worksite != null && !existing.anchor().equals(worksite)) {
                return null;
            }
            if (worksite == null) {
                worker.setWoodWorksite(existing.anchor());
            }
            return existing;
        }

        WoodReservation rebuilt = rebuildReservation(level, worker.getWoodTarget(),
                worker.getWoodWorksite());
        if (rebuilt != null) {
            WOOD_RESERVATIONS.put(worker.getUUID(), rebuilt);
            worker.setWoodWorksite(rebuilt.anchor());
        }
        return rebuilt;
    }

    private static WoodReservation ensureReservation(ServerLevel level, RtsVillagerEntity worker,
                                                     BlockPos target) {
        WoodReservation reservation = reservationForWorker(level, worker);
        if (reservation != null) {
            return reservation;
        }
        // A present-but-invalid runtime reservation is a safety failure, not a reload miss. Do not
        // bypass it by rebuilding from the mutable target/worksite pair.
        if (WOOD_RESERVATIONS.containsKey(worker.getUUID())) {
            return null;
        }
        if (target == null) {
            return null;
        }
        WoodReservation rebuilt = rebuildReservation(level, target, worker.getWoodWorksite());
        if (rebuilt == null) {
            return null;
        }
        WOOD_RESERVATIONS.put(worker.getUUID(), rebuilt);
        worker.setWoodWorksite(rebuilt.anchor());
        return rebuilt;
    }

    /**
     * A reload can recover an active order only from the persisted target/worksite pair. A rooted,
     * still-present anchor and a natural connected component are required; otherwise the worker
     * takes the existing safe return/stop path rather than scanning for a replacement log.
     */
    private static WoodReservation rebuildReservation(ServerLevel level, BlockPos target,
                                                      BlockPos persistedWorksite) {
        if (level == null || target == null || !isHarvestableTarget(level, target)) {
            return null;
        }
        if (isSingleBlockWorksite(level, target)) {
            if (persistedWorksite != null && !persistedWorksite.equals(target)) {
                return null;
            }
            return WoodReservation.single(target);
        }
        if (!isChoppableLog(level, target)) {
            return null;
        }

        TreeInfo tree = treeInfo(level, target);
        if (tree.logs() <= 0 || !isNaturalTree(level, tree.root())) {
            return null;
        }
        BlockPos anchor = persistedWorksite == null ? tree.root() : persistedWorksite;
        // A persisted anchor must still be a natural component member. If the stump is gone after a
        // reload, its old coordinate is not enough evidence to re-admit whatever log is nearby.
        if (!isChoppableLog(level, anchor) || !anchor.equals(tree.root())
                || !tree.component().contains(anchor)) {
            return null;
        }
        return new WoodReservation(anchor, tree.component(), false);
    }

    private static boolean isReservedWorksiteTarget(ServerLevel level, WoodReservation reservation,
                                                    BlockPos target) {
        if (reservation == null || target == null || !reservation.component().contains(target)) {
            return false;
        }
        BlockState state = level.getBlockState(target);
        return reservation.singleBlock() ? isSingleBlockWorksite(state) : isLog(state);
    }

    /** Sets only the target; the reservation's original anchor remains untouched. */
    private static void setReservedTarget(RtsVillagerEntity worker, BlockPos target) {
        worker.setWoodTarget(target);
        WoodReservation reservation = WOOD_RESERVATIONS.get(worker.getUUID());
        if (reservation != null) {
            worker.setWoodWorksite(reservation.anchor());
        }
    }

    /**
     * {@code BlockTags.LOGS} matches every vanilla log AND the beams the mod's own structure
     * templates are built from, so the tag alone cannot tell a tree from a house. This is the
     * spatial half of that distinction: an axis-aligned box per tracked building, covering every
     * owner so a worker cannot eat a neighbour's town either.
     */
    private record Footprint(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    /**
     * Rebuilds the footprint list from {@link RtsBuildingStore} at most once every
     * {@link #FOOTPRINT_CACHE_TICKS}. The 65x24x65 next-tree scan calls into this on almost every
     * candidate block, so recomputing per candidate (instead of per cache window) would turn a
     * cheap tag check into a template lookup per block.
     */
    private static List<Footprint> protectedFootprints(ServerLevel level) {
        long now = level.getGameTime();
        Long lastTick = FOOTPRINT_CACHE_TICK.get(level);
        if (lastTick != null && now - lastTick < FOOTPRINT_CACHE_TICKS) {
            List<Footprint> cached = FOOTPRINT_CACHE.get(level);
            if (cached != null) {
                return cached;
            }
        }

        List<Footprint> footprints = new ArrayList<>();
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
            if (found.isEmpty() || found.get().palettes.isEmpty()) {
                continue;
            }
            // Use the same normalized footprint as durability, selection, and destruction. Linear
            // entries are deliberately one block even though their authored templates are larger.
            BlockPos origin = RtsBuildingDurability.normalizedOrigin(level, entry);
            Vec3i size = RtsBuildingDurability.rotatedSize(level, entry);
            footprints.add(new Footprint(
                    origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + size.getX() - 1,
                    origin.getY() + size.getY() - 1,
                    origin.getZ() + size.getZ() - 1));
        }
        FOOTPRINT_CACHE.put(level, footprints);
        FOOTPRINT_CACHE_TICK.put(level, now);
        return footprints;
    }

    /** True when a block sits inside any tracked building's footprint, on any owner's town. */
    private static boolean isProtected(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        for (Footprint footprint : protectedFootprints(level)) {
            if (footprint.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A log inside a footprint is never a legal target, protection aside. Grown trees that predate
     * a building and now poke through its footprint are an acceptable loss here; the alternative is
     * letting a worker treat any beam it can reach as fair game.
     */
    private static boolean isChoppableLog(ServerLevel level, BlockPos pos) {
        return pos != null && isLog(level.getBlockState(pos)) && !isProtected(level, pos);
    }

    /**
     * Even outside a footprint, a beam is not a tree unless it plausibly grew there: standing on
     * natural soil, or wearing leaves nearby. Manufactured beams (walls, scaffolding decor, floors)
     * are rarely planted on dirt and never grow a canopy.
     */
    private static boolean isNaturalTree(ServerLevel level, BlockPos trunkBase) {
        BlockState below = level.getBlockState(trunkBase.below());
        if (below.is(BlockTags.DIRT) || below.is(Blocks.GRASS_BLOCK)
                || below.is(Blocks.PODZOL) || below.is(Blocks.MYCELIUM)
                || below.is(Blocks.MOSS_BLOCK)) {
            return true;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (level.getBlockState(trunkBase.offset(dx, dy, dz)).is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Counts existing assignments against one tree's capacity, excluding the new selection. */
    private static int countWorkersForTree(ServerLevel level, BlockPos center, BlockPos worksite,
                                            List<Integer> selectedIds) {
        Set<Integer> selected = new HashSet<>();
        if (selectedIds != null) {
            for (Integer id : selectedIds) {
                if (id != null) {
                    selected.add(id);
                }
            }
        }
        return level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                worker -> worker.isAlive()
                        && (worker.getWorkState() == WorkState.GATHERING_WOOD
                        || worker.getWorkState() == WorkState.RETURNING_WOOD)
                        && !selected.contains(worker.getId())
                        && worksite.equals(worker.getWoodWorksite())).size();
    }

    /** A bounded envelope keeps a lumberjack on its assigned tree rather than a neighbouring grove. */
    static boolean withinSameTreeEnvelope(BlockPos candidate, BlockPos worksite) {
        return candidate != null && worksite != null
                && Math.abs(candidate.getX() - worksite.getX()) <= LOG_SEARCH_RADIUS
                && Math.abs(candidate.getZ() - worksite.getZ()) <= LOG_SEARCH_RADIUS
                && candidate.getY() >= worksite.getY() - ROOT_BELOW
                && candidate.getY() <= worksite.getY() + TREE_SCAN_HEIGHT;
    }

    /** Finds the lowest connected log and captures the bounded component for this assignment. */
    private static TreeInfo treeInfo(ServerLevel level, BlockPos seed) {
        if (seed == null || !isChoppableLog(level, seed)) {
            return new TreeInfo(seed == null ? BlockPos.ZERO : seed, 0, Set.of());
        }

        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> component = new HashSet<>();
        open.add(seed.immutable());
        BlockPos root = seed.immutable();
        while (!open.isEmpty() && component.size() < MAX_RESERVED_LOGS) {
            BlockPos current = open.removeFirst();
            // A protected beam stops the flood fill from crossing into it rather than being
            // skipped-over: this is exactly how one exterior log next to a building must not drag
            // the whole timber frame in as "the same tree".
            if (!withinCaptureEnvelope(current, seed) || !visited.add(current)
                    || !isChoppableLog(level, current)) {
                continue;
            }
            BlockPos captured = current.immutable();
            component.add(captured);
            if (captured.getY() < root.getY()
                    || captured.getY() == root.getY()
                    && captured.distSqr(seed) < root.distSqr(seed)) {
                root = captured;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (withinCaptureEnvelope(next, seed)) {
                    open.addLast(next);
                }
            }
        }

        List<BlockPos> bounded = new ArrayList<>();
        for (BlockPos position : component) {
            if (withinSameTreeEnvelope(position, root)) {
                bounded.add(position);
            }
        }
        return new TreeInfo(root, bounded.size(), Set.copyOf(bounded));
    }

    private static boolean withinCaptureEnvelope(BlockPos candidate, BlockPos seed) {
        return candidate != null && seed != null
                && Math.abs(candidate.getX() - seed.getX()) <= LOG_SEARCH_RADIUS
                && Math.abs(candidate.getZ() - seed.getZ()) <= LOG_SEARCH_RADIUS
                && candidate.getY() >= seed.getY() - TREE_SCAN_BELOW
                && candidate.getY() <= seed.getY() + TREE_SCAN_HEIGHT;
    }

    private static double horizontalDistanceSquared(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private record TreeInfo(BlockPos root, int logs, Set<BlockPos> component) {
    }

    private record WoodReservation(BlockPos anchor, Set<BlockPos> component, boolean singleBlock) {
        private WoodReservation {
            anchor = anchor == null ? null : anchor.immutable();
            component = component == null ? Set.of() : Set.copyOf(component);
        }

        private static WoodReservation single(BlockPos target) {
            return new WoodReservation(target, Set.of(target), true);
        }
    }
}
