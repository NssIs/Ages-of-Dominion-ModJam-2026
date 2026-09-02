package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.entity.RtsEnemyEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lightweight town-defense behavior for units without explicit player orders.
 *
 * <p>Autonomous units occupy stable patrol points around the Town Hall. When a worker or soldier
 * is hit by an RTS enemy, at most three nearby combat units temporarily investigate that attacker.
 * The director never changes a unit with a player order, so a deliberate stop, move, or attack can
 * still be a deliberate strategic mistake.</p>
 */
public final class RtsDefenseDirector {
    private static final int THINK_INTERVAL = 10;
    private static final int RECENT_ATTACK_TICKS = 80;
    private static final int RESPONSE_TICKS = 120;
    private static final int MAX_RESPONDERS_PER_ATTACK = 3;
    private static final double UNIT_SCAN_RADIUS = 64.0D;
    private static final double THREAT_RADIUS = 52.0D;
    private static final double RESPONSE_SPEED = 1.15D;
    private static final double WORKER_RING_RADIUS = 8.0D;
    private static final double SOLDIER_RING_RADIUS = 12.0D;
    private static final double RING_TOLERANCE_SQUARED = 9.0D;

    private static final Map<UUID, Response> RESPONSES = new HashMap<>();

    private RtsDefenseDirector() {
    }

    public static void tick(ServerPlayer player) {
        if (!RtsMode.isActive(player) || !RtsCivilization.isFounded(player)
                || player.tickCount % THINK_INTERVAL != 0) {
            return;
        }

        var groundCenter = RtsEntities.townHallGroundCenter(player);
        if (groundCenter.isEmpty()) {
            return;
        }

        ServerLevel level = player.level();
        BlockPos center = groundCenter.get();
        Vec3 centerVec = Vec3.atCenterOf(center);
        List<Mob> units = level.getEntitiesOfClass(
                Mob.class,
                new AABB(center).inflate(UNIT_SCAN_RADIUS),
                unit -> unit.isAlive() && RtsEntities.isAlliedUnit(unit));

        if (units.isEmpty()) {
            return;
        }

        collectResponses(level, centerVec, units);
        long now = level.getGameTime();
        for (Mob unit : units) {
            if (RtsUnitOrders.apply(unit, level)) {
                RESPONSES.remove(unit.getUUID());
                continue;
            }
            if (unit instanceof RtsVillagerEntity worker
                    && (worker.isWorking() || worker.isWoodcutterAssigned())) {
                RESPONSES.remove(unit.getUUID());
                continue;
            }

            Response response = RESPONSES.get(unit.getUUID());
            if (response != null && response.expiresAt() > now
                    && response.attacker().isAlive()
                    && response.attacker().distanceToSqr(centerVec) <= THREAT_RADIUS * THREAT_RADIUS) {
                unit.setTarget(response.attacker());
                if (unit.distanceToSqr(response.attacker()) > 16.0D) {
                    RtsUnitOrders.moveToSmart(unit, level, response.attacker(), RESPONSE_SPEED);
                } else {
                    unit.getNavigation().stop();
                }
                continue;
            }

            RESPONSES.remove(unit.getUUID());
            unit.setTarget(null);
            holdAtPatrolPoint(level, center, unit);
        }

        pruneResponses(now);
    }

    private static void collectResponses(ServerLevel level, Vec3 center, List<Mob> units) {
        long now = level.getGameTime();
        for (Mob victim : units) {
            LivingEntity attacker = victim.getLastHurtByMob();
            if (!(attacker instanceof RtsEnemyEntity enemy) || !enemy.isAlive()
                    || !isRecent(victim) || enemy.distanceToSqr(center) > THREAT_RADIUS * THREAT_RADIUS) {
                continue;
            }

            List<Mob> candidates = new ArrayList<>(units.stream()
                    .filter(candidate -> candidate.isAlive()
                            && RtsEntities.isAlliedCombatUnit(candidate)
                            && !RtsUnitOrders.hasPlayerOrder(candidate))
                    .sorted(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(victim)))
                    .toList());

            int responders = Math.min(MAX_RESPONDERS_PER_ATTACK, candidates.size());
            for (int index = 0; index < responders; index++) {
                Mob responder = candidates.get(index);
                RESPONSES.put(responder.getUUID(), new Response(enemy, now + RESPONSE_TICKS));
            }
        }
    }

    private static boolean isRecent(Mob victim) {
        int age = victim.tickCount - victim.getLastHurtByMobTimestamp();
        return age >= 0 && age <= RECENT_ATTACK_TICKS;
    }

    private static void holdAtPatrolPoint(ServerLevel level, BlockPos center, Mob unit) {
        double radius = RtsEntities.isAlliedCombatUnit(unit)
                ? SOLDIER_RING_RADIUS : WORKER_RING_RADIUS;
        int angleSeed = Math.floorMod(unit.getUUID().hashCode(), 360);
        double angle = angleSeed * Math.PI / 180.0D;
        int x = (int) Math.floor(center.getX() + 0.5D + Math.cos(angle) * radius);
        int z = (int) Math.floor(center.getZ() + 0.5D + Math.sin(angle) * radius);
        int y = RtsEntities.findBottomFloorY(level, x, z, center.getY());
        double targetX = x + 0.5D;
        double targetZ = z + 0.5D;

        if (unit.distanceToSqr(targetX, y, targetZ) > RING_TOLERANCE_SQUARED) {
            RtsUnitOrders.moveToSmart(unit, level, new BlockPos(x, y, z),
                    RtsEntities.isAlliedCombatUnit(unit) ? 1.0D : 0.8D);
        } else {
            unit.getNavigation().stop();
        }
    }

    private static void pruneResponses(long now) {
        Iterator<Map.Entry<UUID, Response>> iterator = RESPONSES.entrySet().iterator();
        while (iterator.hasNext()) {
            Response response = iterator.next().getValue();
            if (response.expiresAt() <= now || !response.attacker().isAlive()) {
                iterator.remove();
            }
        }
    }

    /** Clears temporary investigation state during a replacement-town reset. */
    public static void clear() {
        RESPONSES.clear();
    }

    private record Response(RtsEnemyEntity attacker, long expiresAt) {
    }
}
