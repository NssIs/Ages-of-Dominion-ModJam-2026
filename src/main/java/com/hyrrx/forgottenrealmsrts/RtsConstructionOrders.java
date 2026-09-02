package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.RtsVillagerEntity.WorkState;
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative assignment and progress for buildings assembled by villagers. */
public final class RtsConstructionOrders {
    public static final int MAX_BUILDERS = 32;

    private static final int THINK_INTERVAL = 5;
    private static final int WORK_UNITS_PER_BLOCK = 3;
    /** A foundation still advances slowly before the player has selected any villagers. */
    private static final int BASE_WORK_UNITS = 1;
    /** Each builder contributes twice the unstaffed work rate while standing at the site. */
    private static final int WORKER_WORK_UNITS = 2;
    private static final int MAX_BLOCKS_PER_THINK = 4;
    /** Opening landmarks should feel responsive while remaining visibly block-by-block. */
    private static final int PRIORITY_WORK_UNITS = 18;
    private static final int PRIORITY_MAX_BLOCKS_PER_THINK = 8;
    private static final double BUILD_ARRIVAL_DISTANCE_SQUARED = 16.0D;
    private static final double BUILD_LEAVE_DISTANCE_SQUARED = 36.0D;
    private static final int WORK_RADIUS = 128;
    private static final int SITE_RING_RADIUS = 4;

    private RtsConstructionOrders() {
    }

    /** Counts the real blocks that will be assembled, excluding air and editor markers. */
    public static int blockCount(StructureTemplate template) {
        return constructionBlocks(template).size();
    }

    /** Prevents a new foundation from claiming the empty footprint of another active foundation. */
    public static boolean overlaps(ServerLevel level, BlockPos origin, Vec3i size,
                                   Set<Long> targetSolid) {
        if (targetSolid == null || targetSolid.isEmpty()) {
            return false;
        }

        Set<Long> targetWorld = worldFootprint(origin, targetSolid);
        Set<Long> targetSupports = new HashSet<>();
        for (long packed : targetSolid) {
            BlockPos local = BlockPos.of(packed);
            if (local.getY() == 0) {
                targetSupports.add(origin.offset(local.getX(), -1, local.getZ()).asLong());
            }
        }

        for (RtsConstructionStore.Entry entry : RtsConstructionStore.get(level).entries()) {
            OptionalTemplate active = templateFor(level, entry.structure());
            if (active.template() == null) {
                continue;
            }
            Set<Long> activeWorld = worldFootprint(entry.origin(),
                    solidFootprint(active.template(), active.template().getSize(), entry.rotation()));
            if (!disjoint(targetWorld, activeWorld) || !disjoint(targetSupports, activeWorld)) {
                return true;
            }
        }
        return false;
    }

    /** Assigns selected villagers to a live construction site after checking every ID server-side. */
    public static void assignWorkers(ServerPlayer player, List<Integer> entityIds,
                                     long constructionId) {
        if (!RtsMode.isActive(player) || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || entityIds == null || entityIds.isEmpty() || entityIds.size() > 64
                || constructionId <= 0L) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        RtsConstructionStore store = RtsConstructionStore.get(level);
        RtsConstructionStore.Entry entry = store.find(constructionId, player.getUUID()).orElse(null);
        OptionalTemplate template = entry == null ? new OptionalTemplate(null)
                : templateFor(level, entry.structure());
        if (entry == null || template.template() == null) {
            return;
        }

        BlockPos center = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        if (horizontalDistanceSquared(entry.origin(), center) > (double) WORK_RADIUS * WORK_RADIUS) {
            player.sendOverlayMessage(Component.literal("That build site is outside the realm's work radius."));
            return;
        }

        int assigned = assignedWorkerCount(level, entry);
        int available = Math.max(0, MAX_BUILDERS - assigned);
        int newlyAssigned = 0;
        int alreadyAssigned = 0;
        Set<Integer> seen = new HashSet<>();
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
            if (worker.isConstructionWorker() && worker.getConstructionBuildingId() == entry.id()) {
                alreadyAssigned++;
                continue;
            }
            if (available <= 0) {
                break;
            }

            RtsUnitOrders.clear(worker);
            RtsWorkerOrders.cancel(worker);
            worker.wearBuilderKit();
            worker.setConstructionBuildingId(entry.id());
            worker.setWorkState(WorkState.GOING_TO_BUILD);
            navigateToSite(level, worker, entry, template.template());
            available--;
            assigned++;
            newlyAssigned++;
        }

        if (newlyAssigned > 0 || alreadyAssigned > 0) {
            int total = newlyAssigned + alreadyAssigned;
            player.sendOverlayMessage(Component.literal(total + " worker"
                    + (total == 1 ? " is " : "s are ") + "assigned to construction ("
                    + assigned + " / " + MAX_BUILDERS + ")."));
        } else if (assigned >= MAX_BUILDERS) {
            player.sendOverlayMessage(Component.literal("That site already has its maximum builder crew."));
        } else {
            player.sendOverlayMessage(Component.literal("Only workers can build."));
        }
    }

    /** Reissues routes for builder jobs that survived a world reload. */
    public static void restoreAssignments(ServerPlayer player) {
        if (!RtsMode.isActive(player)) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        List<RtsVillagerEntity> workers = level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(center).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                worker -> worker.isAlive() && worker.isConstructionWorker());
        for (RtsVillagerEntity worker : workers) {
            RtsConstructionStore.Entry entry = RtsConstructionStore.get(level)
                    .find(worker.getConstructionBuildingId(), player.getUUID()).orElse(null);
            OptionalTemplate template = entry == null ? new OptionalTemplate(null)
                    : templateFor(level, entry.structure());
            if (entry == null || template.template() == null) {
                RtsWorkerOrders.cancel(worker);
                continue;
            }
            RtsUnitOrders.clear(worker);
            worker.wearBuilderKit();
            navigateToSite(level, worker, entry, template.template());
        }
    }

    /** Advances all foundations owned by this player on the normal server think cadence. */
    public static void tick(ServerPlayer player) {
        if (!RtsMode.isActive(player) || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || player.tickCount % THINK_INTERVAL != 0) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        for (RtsConstructionStore.Entry entry : RtsConstructionStore.get(level).entries()) {
            if (!entry.owner().equals(player.getUUID())) {
                continue;
            }
            OptionalTemplate template = templateFor(level, entry.structure());
            if (template.template() == null) {
                releaseWorkers(level, entry.id());
                continue;
            }

            List<RtsVillagerEntity> workers = workersFor(level, entry);
            int activeBuilders = 0;
            for (RtsVillagerEntity worker : workers) {
                if (worker.getWorkState() == WorkState.GOING_TO_BUILD) {
                    BlockPos target = buildTarget(level, entry, template.template());
                    if (atBuildTarget(worker, target)) {
                        worker.getNavigation().stop();
                        worker.setWorkState(WorkState.BUILDING);
                    } else if (worker.getNavigation().isDone() || worker.getNavigation().isStuck()) {
                        navigateToSite(level, worker, entry, template.template());
                    }
                }
                if (worker.getWorkState() == WorkState.BUILDING) {
                    BlockPos target = buildTarget(level, entry, template.template());
                    if (!atBuildTarget(worker, target)
                            || worker.distanceToSqr(target.getX() + 0.5D, target.getY(),
                            target.getZ() + 0.5D) > BUILD_LEAVE_DISTANCE_SQUARED) {
                        worker.setWorkState(WorkState.GOING_TO_BUILD);
                        navigateToSite(level, worker, entry, template.template());
                    } else {
                        worker.getNavigation().stop();
                        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        activeBuilders++;
                    }
                }
            }

            List<StructureTemplate.StructureBlockInfo> blocks = constructionBlocks(template.template());
            int total = Math.min(entry.totalBlocks(), blocks.size());
            boolean priorityConstruction = isPriorityConstruction(player, entry);
            int work = entry.workProgress()
                    + (priorityConstruction ? PRIORITY_WORK_UNITS : BASE_WORK_UNITS)
                    + activeBuilders * WORKER_WORK_UNITS;
            int maxBlocksThisThink = priorityConstruction
                    ? PRIORITY_MAX_BLOCKS_PER_THINK : MAX_BLOCKS_PER_THINK;
            RtsConstructionStore.Entry current = entry;
            int placedThisThink = 0;
            while (current.placedBlocks() < total && work >= WORK_UNITS_PER_BLOCK
                    && placedThisThink < maxBlocksThisThink) {
                StructureTemplate.StructureBlockInfo block = blocks.get(current.placedBlocks());
                BlockPos local = BuildingPlacement.rotateOffset(block.pos().getX(), block.pos().getY(),
                        block.pos().getZ(), template.template().getSize(), current.rotation());
                BlockPos world = current.origin().offset(local.getX(), local.getY(), local.getZ());
                BlockState existing = level.getBlockState(world);
                if (!existing.isAir() && !existing.canBeReplaced()
                        && !existing.is(block.state().getBlock())) {
                    // A player or another world process changed a reserved cell. Never overwrite it;
                    // leave the foundation paused until the obstruction is gone.
                    break;
                }
                BlockState placedState = block.state().rotate(current.rotation());
                level.setBlock(world, placedState, Block.UPDATE_ALL);
                if (placedThisThink == 0) {
                    // Server-level sound broadcasting already applies Minecraft's distance
                    // attenuation, so players hear the work as they approach the site without a
                    // new client packet or a global construction soundtrack.
                    var sound = placedState.getSoundType();
                    level.playSound(null, world, sound.getPlaceSound(), SoundSource.BLOCKS,
                            sound.volume, sound.pitch);
                }
                work -= WORK_UNITS_PER_BLOCK;
                current = new RtsConstructionStore.Entry(current.id(), current.owner(),
                        current.structure(), current.origin(), current.rotation(), current.totalBlocks(),
                        current.placedBlocks() + 1, work);
                RtsConstructionStore.get(level).update(current);
                placedThisThink++;
            }

            if (current.placedBlocks() >= total) {
                if (ModPayloads.completeConstruction(player, current)) {
                    continue;
                }
            } else if (placedThisThink == 0 && work != current.workProgress()) {
                RtsConstructionStore.get(level).update(new RtsConstructionStore.Entry(
                        current.id(), current.owner(), current.structure(), current.origin(),
                        current.rotation(), current.totalBlocks(), current.placedBlocks(), work));
            }
        }
    }

    /** Town Hall and the one free Coal Mine are the onboarding landmarks, so they assemble quickly. */
    private static boolean isPriorityConstruction(ServerPlayer player,
                                                  RtsConstructionStore.Entry entry) {
        return BuildingCosts.get(entry.structure()).townHall()
                || BuildingCosts.isFirstCoalMinePlacement(entry.structure(), player);
    }

    /** Releases every worker assigned to one site, without touching the construction itself. */
    public static void releaseWorkers(ServerLevel level, long constructionId) {
        BlockPos site = RtsConstructionStore.get(level).entries().stream()
                .filter(entry -> entry.id() == constructionId)
                .map(RtsConstructionStore.Entry::origin)
                .findFirst()
                .orElse(BlockPos.ZERO);
        for (RtsVillagerEntity worker : level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(site).inflate(RtsEntities.POPULATION_SCAN_RADIUS + WORK_RADIUS),
                candidate -> candidate.isAlive() && candidate.isConstructionWorker()
                        && candidate.getConstructionBuildingId() == constructionId)) {
            RtsUnitOrders.clear(worker);
            RtsWorkerOrders.cancel(worker);
        }
    }

    /** Removes partial blocks and metadata when a defeated town is replaced. */
    public static int clearOwned(ServerLevel level, UUID owner) {
        RtsConstructionStore store = RtsConstructionStore.get(level);
        List<RtsConstructionStore.Entry> owned = store.entries().stream()
                .filter(entry -> entry.owner().equals(owner))
                .toList();
        int removed = 0;
        for (RtsConstructionStore.Entry entry : owned) {
            OptionalTemplate template = templateFor(level, entry.structure());
            if (template.template() != null) {
                List<StructureTemplate.StructureBlockInfo> blocks = constructionBlocks(template.template());
                int count = Math.min(entry.placedBlocks(), blocks.size());
                for (int index = 0; index < count; index++) {
                    StructureTemplate.StructureBlockInfo block = blocks.get(index);
                    BlockPos local = BuildingPlacement.rotateOffset(block.pos().getX(), block.pos().getY(),
                            block.pos().getZ(), template.template().getSize(), entry.rotation());
                    level.setBlock(entry.origin().offset(local.getX(), local.getY(), local.getZ()),
                            Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    removed++;
                }
            }
            releaseWorkers(level, entry.id());
            store.remove(entry.id(), owner);
        }
        return removed;
    }

    /** Clears transient route state; persistent foundations remain in the saved store. */
    public static void clear() {
    }

    public static int assignedWorkerCount(ServerLevel level, RtsConstructionStore.Entry entry) {
        return workersFor(level, entry).size();
    }

    private static List<RtsVillagerEntity> workersFor(ServerLevel level,
                                                        RtsConstructionStore.Entry entry) {
        return level.getEntitiesOfClass(
                RtsVillagerEntity.class,
                new AABB(entry.origin()).inflate(RtsEntities.POPULATION_SCAN_RADIUS),
                worker -> worker.isAlive() && worker.isConstructionWorker()
                        && worker.getConstructionBuildingId() == entry.id());
    }

    private static void navigateToSite(ServerLevel level, RtsVillagerEntity worker,
                                       RtsConstructionStore.Entry entry,
                                       StructureTemplate template) {
        RtsUnitOrders.moveToSmart(worker, level, buildTarget(level, entry, template), 0.9D);
    }

    private static BlockPos buildTarget(ServerLevel level, RtsConstructionStore.Entry entry,
                                        StructureTemplate template) {
        Vec3i size = BuildingPlacement.rotateSize(template.getSize(), entry.rotation());
        double centerX = entry.origin().getX() + size.getX() * 0.5D;
        double centerZ = entry.origin().getZ() + size.getZ() * 0.5D;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= SITE_RING_RADIUS; radius++) {
            int minX = entry.origin().getX() - radius;
            int maxX = entry.origin().getX() + size.getX() - 1 + radius;
            int minZ = entry.origin().getZ() - radius;
            int maxZ = entry.origin().getZ() + size.getZ() - 1 + radius;
            for (int x = minX; x <= maxX; x++) {
                best = closerWalkable(level, best, x, minZ, entry.origin().getY(), centerX, centerZ);
                best = closerWalkable(level, best, x, maxZ, entry.origin().getY(), centerX, centerZ);
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                best = closerWalkable(level, best, minX, z, entry.origin().getY(), centerX, centerZ);
                best = closerWalkable(level, best, maxX, z, entry.origin().getY(), centerX, centerZ);
            }
            if (best != null) {
                return best;
            }
        }
        return entry.origin().offset(size.getX() / 2, 0, size.getZ() / 2);
    }

    private static BlockPos closerWalkable(ServerLevel level, BlockPos current, int x, int z,
                                           int preferredY, double centerX, double centerZ) {
        int y = RtsEntities.findBottomFloorY(level, x, z, preferredY);
        BlockPos candidate = new BlockPos(x, y, z);
        if (!isWalkable(level, candidate)) {
            return current;
        }
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

    private static boolean atBuildTarget(RtsVillagerEntity worker, BlockPos target) {
        return worker.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D)
                <= BUILD_ARRIVAL_DISTANCE_SQUARED;
    }

    private static boolean isWalkable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    private static List<StructureTemplate.StructureBlockInfo> constructionBlocks(
            StructureTemplate template) {
        if (template == null || template.palettes.isEmpty()) {
            return List.of();
        }
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.palettes.get(0).blocks()) {
            if (!info.state().isAir() && !StructureSanitizer.isTechnicalMarker(info.state())) {
                blocks.add(info);
            }
        }
        return List.copyOf(blocks);
    }

    private static Set<Long> solidFootprint(StructureTemplate template, Vec3i size,
                                            Rotation rotation) {
        Set<Long> solid = new HashSet<>();
        for (StructureTemplate.StructureBlockInfo info : constructionBlocks(template)) {
            BlockPos rotated = BuildingPlacement.rotateOffset(info.pos().getX(), info.pos().getY(),
                    info.pos().getZ(), size, rotation);
            solid.add(rotated.asLong());
        }
        return solid;
    }

    private static Set<Long> worldFootprint(BlockPos origin, Set<Long> localPositions) {
        Set<Long> world = new HashSet<>(localPositions.size());
        for (long packed : localPositions) {
            BlockPos local = BlockPos.of(packed);
            world.add(origin.offset(local.getX(), local.getY(), local.getZ()).asLong());
        }
        return world;
    }

    private static boolean disjoint(Set<Long> first, Set<Long> second) {
        Set<Long> smaller = first.size() <= second.size() ? first : second;
        Set<Long> larger = smaller == first ? second : first;
        for (long value : smaller) {
            if (larger.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private static double horizontalDistanceSquared(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static OptionalTemplate templateFor(ServerLevel level,
                                                net.minecraft.resources.Identifier structure) {
        return new OptionalTemplate(RtsStructureTemplates.get(level, structure).orElse(null));
    }

    private record OptionalTemplate(StructureTemplate template) {
    }
}
