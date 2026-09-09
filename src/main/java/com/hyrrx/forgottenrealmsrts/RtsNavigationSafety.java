package com.hyrrx.forgottenrealmsrts;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.Path;

/** Shared terrain checks for every route and worker work-point search. */
public final class RtsNavigationSafety {
    private RtsNavigationSafety() {
    }

    /** A two-block-tall villager can stand here without entering a fluid or falling through it. */
    public static boolean isSafeFeet(BlockGetter level, BlockPos feet) {
        if (level == null || feet == null
                || feet.getY() <= level.getMinY() || feet.getY() + 1 >= level.getMaxY()) {
            return false;
        }
        BlockPos head = feet.above();
        BlockPos support = feet.below();
        return level.getBlockState(feet).getFluidState().isEmpty()
                && level.getBlockState(head).getFluidState().isEmpty()
                && level.getBlockState(support).getFluidState().isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
    }

    /** Finds a safe floor in the same bounded vertical probe used by the town spawn logic. */
    public static Optional<BlockPos> findSafeFeet(BlockGetter level, int x, int z,
                                                   int preferredFeetY, int verticalRange) {
        int range = Math.max(0, verticalRange);
        for (int distance = 0; distance <= range; distance++) {
            int below = preferredFeetY - distance;
            BlockPos belowFeet = new BlockPos(x, below, z);
            if (isSafeFeet(level, belowFeet)) {
                return Optional.of(belowFeet);
            }
            if (distance > 0) {
                int above = preferredFeetY + distance;
                BlockPos aboveFeet = new BlockPos(x, above, z);
                if (isSafeFeet(level, aboveFeet)) {
                    return Optional.of(aboveFeet);
                }
            }
        }
        return Optional.empty();
    }

    /** Allows level terrain and a single-block step, including a guarded diagonal corner. */
    public static boolean isSafeStep(BlockGetter level, BlockPos from, BlockPos to) {
        if (!isSafeFeet(level, from) || !isSafeFeet(level, to)) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dy = to.getY() - from.getY();
        int dz = Math.abs(to.getZ() - from.getZ());
        if (dx > 1 || dz > 1 || Math.abs(dy) > 1) {
            return false;
        }
        if (dx == 0 && dz == 0 && dy == 0) {
            return true;
        }
        if (dx == 1 && dz == 1) {
            BlockPos xCorner = new BlockPos(to.getX(), to.getY(), from.getZ());
            BlockPos zCorner = new BlockPos(from.getX(), to.getY(), to.getZ());
            return isSafeFeet(level, xCorner) && isSafeFeet(level, zCorner);
        }
        return true;
    }

    /** Validates every remaining vanilla path node against the live terrain. */
    public static boolean isSafePath(BlockGetter level, BlockPos start, Path path) {
        if (level == null || start == null || path == null || !path.canReach()) {
            return false;
        }
        int count = path.getNodeCount();
        int next = Math.max(0, path.getNextNodeIndex());
        if (next >= count) {
            return true;
        }

        BlockPos previous = start;
        for (int index = next; index < count; index++) {
            BlockPos current = path.getNodePos(index);
            if (!isSafeStep(level, previous, current)) {
                return false;
            }
            previous = current;
        }
        return true;
    }
}
