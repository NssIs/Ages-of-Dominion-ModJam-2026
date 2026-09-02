package com.hyrrx.forgottenrealmsrts.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;

/** Short-lived client feedback for the destinations issued to selected units. */
public final class RtsUnitPathState {
    private static final long PATH_VISIBLE_MILLIS = 9_000L;
    private static final Map<Integer, Path> PATHS = new HashMap<>();

    private RtsUnitPathState() {
    }

    public static void show(List<? extends LivingEntity> units, BlockPos target) {
        if (units == null || target == null) {
            return;
        }
        long until = Util.getMillis() + PATH_VISIBLE_MILLIS;
        for (LivingEntity unit : units) {
            if (unit != null && unit.isAlive()) {
                PATHS.put(unit.getId(), new Path(target.immutable(), until));
            }
        }
    }

    public static Map<Integer, Path> snapshot() {
        return Map.copyOf(PATHS);
    }

    public static void remove(int entityId) {
        PATHS.remove(entityId);
    }

    public static void clear() {
        PATHS.clear();
    }

    public record Path(BlockPos target, long visibleUntil) {
    }
}
