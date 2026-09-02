package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.RtsVillagerEntity.WorkState;
import com.hyrrx.forgottenrealmsrts.network.MineStatusPayload;
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
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/** Server-authoritative worker assignments and timed output for the authored mine structures. */
public final class RtsMineOrders {
    private static final int WORK_RADIUS = 128;
    private static final int THINK_INTERVAL = 5;
    private static final double DROP_OFF_DISTANCE_SQUARED = 36.0D;
    private static final double MINE_ARRIVAL_DISTANCE_SQUARED = 4.0D;
    private static final int COAL_INTERVAL_TICKS = 100;
    private static final int STONE_INTERVAL_TICKS = 100;
    private static final int IRON_INTERVAL_TICKS = 160;
    private static final int GOLD_INTERVAL_TICKS = 200;
    private static final int STOCKPILE_CAP = 999_999;

    /** The production clock belongs to a mine, so adding workers increases one payout. */
    private static final Map<MineKey, Long> NEXT_OUTPUT_TICK = new HashMap<>();

    private RtsMineOrders() {
    }

    /** Recognizes the authored mine families, including generated legacy IDs. */
    public static boolean isMineStructure(Identifier structure) {
        if (structure == null || !structure.getPath().contains("/mines/")) {
            return false;
        }
        String building = ModPayloads.buildingOf(structure);
        return "coal".equals(building) || "stone".equals(building)
                || "iron".equals(building) || "gold".equals(building);
    }

    /** Builds the live world label shown above a selected mine. */
    public static MineStatusPayload statusFor(ServerLevel level, RtsBuildingStore.Entry entry) {
        if (level == null || entry == null || !isMineStructure(entry.structure())) {
            return MineStatusPayload.clear();
        }

        Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
        if (found.isEmpty() || found.get().palettes.isEmpty()) {
            return MineStatusPayload.clear();
        }

        MineSite site = mineSite(level, entry, found.get());
        int workersInside = level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                site.bounds(),
                worker -> worker.isAlive()
                        && worker.getMineBuildingId() == entry.id()
                        && worker.getWorkState() == WorkState.MINING).size();
        net.minecraft.core.Vec3i size = BuildingPlacement.rotateSize(
                found.get().getSize(), entry.rotation());
        BlockPos origin = normalizedOrigin(found.get(), entry);
        BlockPos displayPos = new BlockPos(
                (int) Math.floor(origin.getX() + size.getX() * 0.5D),
                origin.getY() + size.getY() + 1,
                (int) Math.floor(origin.getZ() + size.getZ() * 0.5D));
        Resource resource = resourceFor(entry.structure());
        MineYield yield = yieldFor(resource);
        return new MineStatusPayload(entry.id(), displayPos, resource.key(), workersInside,
                capacityFor(entry.structure()), workersInside * yield.primaryOutput(),
                yield.bonusResource().key(), workersInside * yield.bonusOutput(),
                Math.max(1, (yield.intervalTicks() + 19) / 20));
    }

    /** Assigns eligible selected workers to the chosen mine, respecting its tier capacity. */
    public static void assignMine(ServerPlayer player, List<Integer> entityIds, long buildingId) {
        if (!RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || entityIds == null || entityIds.isEmpty() || buildingId <= 0L) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Optional<RtsBuildingStore.Entry> found = RtsBuildingStore.get(level)
                .find(buildingId, player.getUUID());
        if (found.isEmpty() || !isMineStructure(found.get().structure())) {
            player.sendOverlayMessage(Component.literal("That is not one of your realm's mines."));
            return;
        }

        RtsBuildingStore.Entry entry = found.get();
        Optional<StructureTemplate> template = RtsStructureTemplates.get(level, entry.structure());
        if (template.isEmpty() || template.get().palettes.isEmpty()) {
            player.sendOverlayMessage(Component.literal("That mine's blueprint cannot be found."));
            return;
        }

        BlockPos townCenter = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        if (horizontalDistanceSquared(entry.origin(), townCenter) > (double) WORK_RADIUS * WORK_RADIUS) {
            player.sendOverlayMessage(Component.literal("That mine lies beyond the realm's work radius."));
            return;
        }

        Set<Integer> selectedIds = new HashSet<>();
        for (Integer entityId : entityIds) {
            if (entityId != null) {
                selectedIds.add(entityId);
            }
        }
        int occupied = countWorkers(level, townCenter, buildingId, selectedIds);
        int capacity = capacityFor(entry.structure());
        int available = Math.max(0, capacity - occupied);
        int assigned = 0;
        int alreadyAssigned = 0;

        for (Integer entityId : selectedIds) {
            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof RtsVillagerEntity worker) || !worker.isAlive()
                    || horizontalDistanceSquared(worker.blockPosition(), townCenter)
                    > RtsEntities.POPULATION_SCAN_RADIUS * RtsEntities.POPULATION_SCAN_RADIUS) {
                continue;
            }
            if (worker.getMineBuildingId() == buildingId && isActiveMineState(worker.getWorkState())) {
                alreadyAssigned++;
                continue;
            }
            if (available <= 0) {
                break;
            }

            // A mine assignment is a deliberate order and cancels woodcutting or a prior move.
            RtsUnitOrders.clear(worker);
            RtsWorkerOrders.cancel(worker);
            worker.wearMinerKit();
            worker.setMineBuildingId(buildingId);
            worker.setWorkState(WorkState.GOING_TO_MINE);
            worker.leaveMine();
            navigateToMine(worker, mineInterior(level, entry, template.get()));
            available--;
            assigned++;
        }

        if (assigned > 0 || alreadyAssigned > 0) {
            String name = resourceFor(entry.structure()).label();
            player.sendOverlayMessage(Component.literal(
                    (assigned + alreadyAssigned) + " worker"
                            + (assigned + alreadyAssigned == 1 ? " is " : "s are ")
                            + "assigned to the " + name + " Mine (" + capacity + " places)."));
        } else if (capacity <= occupied) {
            player.sendOverlayMessage(Component.literal(
                    "The " + resourceFor(entry.structure()).label() + " Mine is full (" + capacity + ")."));
        }
    }

    /** Sends every worker assigned to one mine back toward the Town Hall and clears its mine job. */
    public static void recallMineWorkers(ServerPlayer player, long buildingId) {
        if (!RtsCivilization.isFounded(player) || buildingId <= 0L) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Optional<RtsBuildingStore.Entry> found = RtsBuildingStore.get(level)
                .find(buildingId, player.getUUID());
        if (found.isEmpty() || !isMineStructure(found.get().structure())) {
            return;
        }
        BlockPos townCenter = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        int recalled = 0;
        for (RtsVillagerEntity worker : level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(townCenter).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                candidate -> candidate.isAlive()
                        && candidate.getMineBuildingId() == buildingId
                        && isActiveMineState(candidate.getWorkState()))) {
            beginReturn(level, worker, townCenter);
            recalled++;
        }
        if (recalled > 0) {
            player.sendOverlayMessage(Component.literal(recalled + " worker"
                    + (recalled == 1 ? " is " : "s are ") + "leaving the mine."));
        } else {
            player.sendOverlayMessage(Component.literal("No workers are inside this mine."));
        }
    }

    /**
     * Frees every worker assigned to one mine so a demolition does not read as an abandoned
     * building. {@link #tick} re-resolves the entry every pass and would otherwise send these
     * workers marching back to the Town Hall the instant the store entry disappears; this instead
     * drops them to {@code IDLE} exactly where they stand. {@code leaveMine()} is required here even
     * though {@link RtsVillagerEntity#clearWorkAssignment()} also calls it, because a worker left
     * inside a demolished mine with its invisibility effect still applied would otherwise stay
     * invisible.
     */
    public static void releaseWorkers(ServerLevel level, BlockPos scanCenter, long buildingId) {
        if (buildingId <= 0L) {
            return;
        }
        for (RtsVillagerEntity worker : level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(scanCenter).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                candidate -> candidate.isAlive() && candidate.getMineBuildingId() == buildingId)) {
            worker.leaveMine();
            worker.clearWorkAssignment();
        }
    }

    /** Advances movement, mine entry/exit, and mine-wide production on a short thinking cadence. */
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
                worker -> worker.isAlive() && worker.isMineWorker());
        Map<MineKey, Integer> producingWorkers = new HashMap<>();

        for (RtsVillagerEntity worker : workers) {
            if (worker.getWorkState() == WorkState.RETURNING_MINE) {
                tickReturning(level, worker, townCenter);
                continue;
            }

            Optional<RtsBuildingStore.Entry> found = RtsBuildingStore.get(level)
                    .find(worker.getMineBuildingId(), player.getUUID());
            if (found.isEmpty() || !isMineStructure(found.get().structure())) {
                beginReturn(level, worker, townCenter);
                continue;
            }

            RtsBuildingStore.Entry entry = found.get();
            Optional<StructureTemplate> template = RtsStructureTemplates.get(level, entry.structure());
            if (template.isEmpty() || template.get().palettes.isEmpty()) {
                beginReturn(level, worker, townCenter);
                continue;
            }

            MineSite site = mineSite(level, entry, template.get());
            if (worker.getWorkState() == WorkState.GOING_TO_MINE) {
                worker.leaveMine();
                if (site.bounds().contains(worker.getX(), worker.getY(), worker.getZ())
                        && worker.distanceToSqr(site.interior().getX() + 0.5D,
                        site.interior().getY(), site.interior().getZ() + 0.5D)
                        <= MINE_ARRIVAL_DISTANCE_SQUARED) {
                    worker.getNavigation().stop();
                    worker.enterMine();
                    worker.setWorkState(WorkState.MINING);
                } else {
                    navigateToMine(worker, site.interior());
                }
            }

            if (worker.getWorkState() != WorkState.MINING) {
                continue;
            }
            if (!site.bounds().contains(worker.getX(), worker.getY(), worker.getZ())) {
                worker.leaveMine();
                worker.setWorkState(WorkState.GOING_TO_MINE);
                navigateToMine(worker, site.interior());
                continue;
            }

            // Refresh the effect only if this assignment owns it; other effects are untouched.
            worker.enterMine();
            worker.getNavigation().stop();
            MineKey key = new MineKey(player.getUUID(), entry.id());
            producingWorkers.merge(key, 1, Integer::sum);
        }

        long now = level.getGameTime();
        Set<MineKey> activeMines = producingWorkers.keySet();
        for (Map.Entry<MineKey, Integer> production : producingWorkers.entrySet()) {
            MineKey key = production.getKey();
            int workersAtMine = production.getValue();
            Optional<RtsBuildingStore.Entry> found = RtsBuildingStore.get(level).find(key.id(), key.owner());
            if (found.isEmpty()) {
                continue;
            }
            Resource resource = resourceFor(found.get().structure());
            MineYield yield = yieldFor(resource);
            int interval = yield.intervalTicks();
            long next = NEXT_OUTPUT_TICK.computeIfAbsent(key, ignored -> now + interval);
            if (now < next) {
                continue;
            }
            addStock(player, resource, workersAtMine * yield.primaryOutput());
            addStock(player, yield.bonusResource(), workersAtMine * yield.bonusOutput());
            NEXT_OUTPUT_TICK.put(key, now + interval);
        }
        NEXT_OUTPUT_TICK.keySet().removeIf(key -> key.owner().equals(player.getUUID())
                && !activeMines.contains(key));
    }

    /** Returns the worker capacity supplied by the three visually identical mine tiers. */
    public static int capacityFor(Identifier structure) {
        int level = Math.max(1, Math.min(3, ModPayloads.levelOf(structure)));
        return level * 2;
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
                        && worker.getMineBuildingId() == buildingId
                        && isActiveMineState(worker.getWorkState())
                        && !selectedIds.contains(worker.getId())).size();
    }

    private static boolean isActiveMineState(WorkState state) {
        return state == WorkState.GOING_TO_MINE || state == WorkState.MINING;
    }

    private static void beginReturn(ServerLevel level, RtsVillagerEntity worker, BlockPos center) {
        worker.leaveMine();
        worker.setWorkState(WorkState.RETURNING_MINE);
        navigateToTownHall(level, worker, center);
    }

    private static void tickReturning(ServerLevel level, RtsVillagerEntity worker, BlockPos center) {
        worker.leaveMine();
        if (worker.distanceToSqr(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D)
                > DROP_OFF_DISTANCE_SQUARED) {
            navigateToTownHall(level, worker, center);
            return;
        }
        worker.getNavigation().stop();
        worker.clearWorkAssignment();
    }

    private static MineSite mineSite(ServerLevel level, RtsBuildingStore.Entry entry,
                                     StructureTemplate template) {
        BlockPos origin = normalizedOrigin(template, entry);
        net.minecraft.core.Vec3i size = BuildingPlacement.rotateSize(template.getSize(), entry.rotation());
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ());
        return new MineSite(bounds, mineInterior(level, entry, template));
    }

    /** Finds a real walkable air cell inside the placed shell, preferring the visual centre. */
    private static BlockPos mineInterior(ServerLevel level, RtsBuildingStore.Entry entry,
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

    private static void navigateToMine(RtsVillagerEntity worker, BlockPos target) {
        if (worker.level() instanceof ServerLevel level) {
            RtsUnitOrders.moveToSmart(worker, level, target, 0.9D);
        }
    }

    private static void navigateToTownHall(ServerLevel level, RtsVillagerEntity worker, BlockPos center) {
        RtsUnitOrders.moveToSmart(worker, level, center, 0.9D);
    }

    private static Resource resourceFor(Identifier structure) {
        return switch (ModPayloads.buildingOf(structure)) {
            case "stone" -> Resource.STONE;
            case "iron" -> Resource.IRON;
            case "gold" -> Resource.GOLD;
            default -> Resource.COAL;
        };
    }

    private static MineYield yieldFor(Resource resource) {
        return switch (resource) {
            case STONE -> new MineYield(Resource.STONE, 3, Resource.COAL, 1,
                    STONE_INTERVAL_TICKS);
            case IRON -> new MineYield(Resource.IRON, 1, Resource.STONE, 1,
                    IRON_INTERVAL_TICKS);
            case GOLD -> new MineYield(Resource.GOLD, 1, Resource.STONE, 1,
                    GOLD_INTERVAL_TICKS);
            default -> new MineYield(Resource.COAL, 3, Resource.STONE, 1,
                    COAL_INTERVAL_TICKS);
        };
    }

    private static void addStock(ServerPlayer player, Resource resource, int amount) {
        if (amount <= 0) {
            return;
        }
        int stock = Math.min(STOCKPILE_CAP, RtsEconomy.stock(player, resource) + amount);
        RtsEconomy.setStock(player, resource, stock);
    }

    private static double horizontalDistanceSquared(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static BlockPos normalizedOrigin(StructureTemplate template, RtsBuildingStore.Entry entry) {
        if (entry.normalizedOrigin()) {
            return entry.origin();
        }
        BlockPos offset = template.getZeroPositionWithTransform(BlockPos.ZERO, Mirror.NONE,
                entry.rotation());
        return entry.origin().offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    private record MineKey(UUID owner, long id) {
    }

    private record MineYield(Resource primaryResource, int primaryOutput,
                              Resource bonusResource, int bonusOutput, int intervalTicks) {
    }

    private record MineSite(AABB bounds, BlockPos interior) {
    }

}
