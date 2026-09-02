package com.hyrrx.forgottenrealmsrts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Server-authoritative player orders for the RTS army.
 *
 * <p>Orders intentionally live outside the autonomous town director. A regroup, attack, or stop
 * command is a strategic commitment: the director will not pull that unit back into its patrol
 * ring or choose a different target behind the player's back. Orders are runtime state, so a world
 * reload safely returns units to autonomous town defense instead of persisting stale entity refs.</p>
 */
public final class RtsUnitOrders {
    public enum Mode {
        MOVE,
        ATTACK,
        HOLD
    }

    private static final double MOVE_SPEED = 1.1D;
    private static final double ATTACK_SPEED = 1.15D;
    /** Keep an active route intact; re-planning every director tick made groups stutter. */
    private static final long NAVIGATION_RETRY_TICKS = 40L;
    /** Failed destinations get a longer back-off so an obstructed group cannot burn the server. */
    private static final long FAILED_NAVIGATION_RETRY_TICKS = 100L;
    /** A bad structure-center target needs a nearby floor, not a path search for every ring cell. */
    private static final int MAX_FALLBACK_PATH_PROBES = 4;
    private static final Map<UUID, Order> ORDERS = new HashMap<>();
    private static final Map<UUID, NavigationRequest> NAVIGATION = new HashMap<>();

    private RtsUnitOrders() {
    }

    /**
     * Starts a real ground path and falls back to a nearby walkable cell when the requested block is
     * inside a wall, on a roof, or otherwise not a valid feet position. The vanilla navigation
     * system still owns the actual path; this only gives it a better destination and a fresh route
     * when an RTS unit has become stuck.
     */
    public static boolean moveToSmart(Mob unit, ServerLevel level, BlockPos requested, double speed) {
        if (unit == null || level == null || requested == null || !unit.isAlive()) {
            return false;
        }

        long now = level.getGameTime();
        NavigationRequest previous = NAVIGATION.get(unit.getUUID());
        if (previous != null && previous.requested().equals(requested)) {
            boolean arrived = previous.goal() != null
                    && unit.distanceToSqr(previous.goal().getX() + 0.5D,
                    previous.goal().getY(), previous.goal().getZ() + 0.5D) <= 2.25D;
            if (arrived || !unit.getNavigation().isDone() && !unit.getNavigation().isStuck()
                    || now < previous.retryAt()) {
                return true;
            }
        }

        NavigationRoute route = findReachableRoute(unit, level, requested);
        if (route.path() != null) {
            boolean moved = unit.getNavigation().moveTo(route.path(), speed);
            NAVIGATION.put(unit.getUUID(), new NavigationRequest(requested.immutable(), route.goal(),
                    now + (moved ? NAVIGATION_RETRY_TICKS : FAILED_NAVIGATION_RETRY_TICKS)));
            return moved;
        }

        // An interior mine cell can be reachable through a door even when the pathfinder's
        // destination probe does not mark the final node as reachable. Preserve that direct attempt.
        BlockPos goal = route.goal();
        boolean moved = unit.getNavigation().moveTo(goal.getX() + 0.5D, goal.getY(), goal.getZ() + 0.5D,
                speed);
        NAVIGATION.put(unit.getUUID(), new NavigationRequest(requested.immutable(), goal,
                now + FAILED_NAVIGATION_RETRY_TICKS));
        return moved;
    }

    /** Repaths to a moving target first, then uses the same walkable-cell fallback if needed. */
    public static boolean moveToSmart(Mob unit, ServerLevel level, LivingEntity target, double speed) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        return moveToSmart(unit, level, target.blockPosition(), speed);
    }

    public static void issueMove(List<? extends Mob> units, BlockPos target) {
        BlockPos destination = target.immutable();
        for (Mob unit : units) {
            if (!unit.isAlive()) {
                continue;
            }
            NAVIGATION.remove(unit.getUUID());
            if (unit instanceof RtsVillagerEntity worker) {
                RtsWorkerOrders.cancel(worker);
            }
            ORDERS.put(unit.getUUID(), new Order(Mode.MOVE, destination, null));
            unit.setTarget(null);
            if (unit.level() instanceof ServerLevel level) {
                moveToSmart(unit, level, destination, MOVE_SPEED);
            }
        }
    }

    public static void issueAttack(List<? extends Mob> units, LivingEntity target) {
        if (target == null || !target.isAlive() || !RtsEntities.isEnemyUnit(target)) {
            return;
        }
        for (Mob unit : units) {
            if (!unit.isAlive()) {
                continue;
            }
            NAVIGATION.remove(unit.getUUID());
            ORDERS.put(unit.getUUID(), new Order(Mode.ATTACK, null, target));
            unit.setTarget(target);
            if (unit.level() instanceof ServerLevel level) {
                moveToSmart(unit, level, target, ATTACK_SPEED);
            }
        }
    }

    public static void issueHold(List<? extends Mob> units) {
        for (Mob unit : units) {
            if (!unit.isAlive()) {
                continue;
            }
            NAVIGATION.remove(unit.getUUID());
            if (unit instanceof RtsVillagerEntity worker) {
                RtsWorkerOrders.cancel(worker);
            }
            ORDERS.put(unit.getUUID(), new Order(Mode.HOLD, null, null));
            unit.setTarget(null);
            unit.getNavigation().stop();
        }
    }

    /** Reapplies a player's order after vanilla goals have had their normal tick. */
    public static boolean apply(Mob unit, ServerLevel level) {
        Order order = ORDERS.get(unit.getUUID());
        if (order == null) {
            return false;
        }

        switch (order.mode()) {
            case HOLD -> {
                unit.setTarget(null);
                unit.getNavigation().stop();
                return true;
            }
            case MOVE -> {
                unit.setTarget(null);
                if (unit.getNavigation().isDone() || unit.getNavigation().isStuck()) {
                    moveToSmart(unit, level, order.destination(), MOVE_SPEED);
                }
                return true;
            }
            case ATTACK -> {
                LivingEntity target = order.target();
                if (target == null || !target.isAlive() || target.level() != level
                        || !RtsEntities.isEnemyUnit(target)) {
                    ORDERS.remove(unit.getUUID());
                    NAVIGATION.remove(unit.getUUID());
                    unit.setTarget(null);
                    return false;
                }
                unit.setTarget(target);
                if (unit.getNavigation().isDone() || unit.getNavigation().isStuck()
                        || unit.distanceToSqr(target) > 16.0D) {
                    moveToSmart(unit, level, target, ATTACK_SPEED);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static boolean hasPlayerOrder(Mob unit) {
        return ORDERS.containsKey(unit.getUUID());
    }

    /** Removes a previous move/hold order before a specialized worker assignment takes over. */
    public static void clear(Mob unit) {
        if (unit != null) {
            ORDERS.remove(unit.getUUID());
            NAVIGATION.remove(unit.getUUID());
        }
    }

    public static Mode mode(Mob unit) {
        Order order = ORDERS.get(unit.getUUID());
        return order == null ? null : order.mode();
    }

    /** Called when a replacement town is founded so discarded units cannot retain old commands. */
    public static void clear() {
        ORDERS.clear();
        NAVIGATION.clear();
    }

    private static NavigationRoute findReachableRoute(Mob unit, ServerLevel level, BlockPos requested) {
        BlockPos fallback = requested;
        if (isWalkable(level, requested)) {
            Path direct = unit.getNavigation().createPath(requested, 1);
            if (direct != null && direct.canReach()) {
                return new NavigationRoute(requested, direct);
            }
        }

        List<BlockPos> candidates = new ArrayList<>(MAX_FALLBACK_PATH_PROBES);
        search:
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = requested.getX() + dx;
                    int z = requested.getZ() + dz;
                    int y = RtsEntities.findBottomFloorY(level, x, z, requested.getY());
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!isWalkable(level, candidate)) {
                        continue;
                    }
                    if (fallback.equals(requested)) {
                        fallback = candidate;
                    }
                    candidates.add(candidate);
                    if (candidates.size() >= MAX_FALLBACK_PATH_PROBES) {
                        break search;
                    }
                }
            }
        }

        // Probe only a small set of valid floor cells. The previous implementation asked the
        // pathfinder about every ring cell, which multiplied into hundreds of path searches when a
        // whole invasion wave was sent toward the same Town Hall.
        for (BlockPos candidate : candidates) {
            Path path = unit.getNavigation().createPath(candidate, 1);
            if (path != null && path.canReach()) {
                return new NavigationRoute(candidate, path);
            }
        }
        return new NavigationRoute(fallback, null);
    }

    /** Drops only the cached route; the caller remains in charge of the unit's strategic order. */
    public static void clearNavigation(Mob unit) {
        if (unit != null) {
            NAVIGATION.remove(unit.getUUID());
        }
    }

    private static boolean isWalkable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    private record Order(Mode mode, BlockPos destination, LivingEntity target) {
    }

    private record NavigationRequest(BlockPos requested, BlockPos goal, long retryAt) {
    }

    private record NavigationRoute(BlockPos goal, Path path) {
    }
}
