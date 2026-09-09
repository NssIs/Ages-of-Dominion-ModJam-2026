package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.RtsVillagerEntity.WorkState;
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

/** Server-authoritative assignment and progress for paid and supernatural foundations. */
public final class RtsConstructionOrders {
    public static final int MAX_BUILDERS = 32;

    private static final int PAID_THINK_INTERVAL = 5;
    private static final int AUTONOMOUS_THINK_INTERVAL = 20;
    private static final int WORK_UNITS_PER_BLOCK = 3;
    /** Legacy palette-order foundations retain their old unattended base budget. */
    private static final int BASE_WORK_UNITS = 1;
    /** Each builder contributes two work units while standing at the site. */
    private static final int WORKER_WORK_UNITS = 2;
    private static final int MAX_BLOCKS_PER_THINK = 4;
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

        if (isAutonomous(player, entry)) {
            // An old save may still contain a worker assignment from before free sites became
            // supernatural. Drop it before returning so it can never accelerate this foundation.
            releaseWorkers(level, entry.id());
            player.sendOverlayMessage(Component.literal("BUILDING AUTOMATICALLY"));
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
        RtsConstructionStore store = RtsConstructionStore.get(level);
        for (RtsConstructionStore.Entry entry : store.entries()) {
            if (entry.owner().equals(player.getUUID()) && isAutonomous(player, entry)) {
                // Clear even an old attached construction id whose worker state was saved as idle.
                releaseWorkers(level, entry.id());
            }
        }
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
            if (isAutonomous(player, entry)) {
                RtsUnitOrders.clear(worker);
                RtsWorkerOrders.cancel(worker);
                continue;
            }
            RtsUnitOrders.clear(worker);
            worker.wearBuilderKit();
            navigateToSite(level, worker, entry, template.template());
        }
    }

    /** Advances paid foundations every five ticks and fresh layered foundations every twenty. */
    public static void tick(ServerPlayer player) {
        if (!RtsMode.isActive(player) || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        boolean paidThink = player.tickCount % PAID_THINK_INTERVAL == 0;
        boolean autonomousThink = player.tickCount % AUTONOMOUS_THINK_INTERVAL == 0;
        if (!paidThink && !autonomousThink) {
            return;
        }

        for (RtsConstructionStore.Entry entry : RtsConstructionStore.get(level).entries()) {
            if (!entry.owner().equals(player.getUUID())) {
                continue;
            }
            OptionalTemplate template = templateFor(level, entry.structure());
            if (template.template() == null) {
                releaseWorkers(level, entry.id());
                continue;
            }

            boolean autonomous = isAutonomous(player, entry);
            if (autonomous) {
                // Keep the autonomy decision independent from the saved ordering flag. This also
                // repairs legacy entries that were saved with workers attached to a now-free site.
                releaseWorkers(level, entry.id());
                if (entry.layered()) {
                    if (autonomousThink) {
                        tickLayeredAutonomousFoundation(player, level, entry, template.template());
                    }
                } else if (paidThink) {
                    // A legacy free record must not be reordered merely because it now resolves as
                    // zero-cost. It also keeps its old palette-order base budget for compatibility.
                    advancePaletteFoundation(player, level, entry, template.template(), 0);
                }
                continue;
            }
            if (!paidThink) {
                continue;
            }
            tickPaidFoundation(player, level, entry, template.template());
        }
    }

    /** Keeps paid construction's five-tick navigation, staffing, and work budget unchanged. */
    private static void tickPaidFoundation(ServerPlayer player, ServerLevel level,
                                            RtsConstructionStore.Entry entry,
                                            StructureTemplate template) {
        List<RtsVillagerEntity> workers = workersFor(level, entry);
        int activeBuilders = 0;
        for (RtsVillagerEntity worker : workers) {
            if (worker.getWorkState() == WorkState.GOING_TO_BUILD) {
                BlockPos target = buildTarget(level, entry, template, worker);
                if (target != null && atBuildTarget(level, worker, target)) {
                    worker.getNavigation().stop();
                    worker.setWorkState(WorkState.BUILDING);
                } else if (worker.getNavigation().isDone() || worker.getNavigation().isStuck()) {
                    navigateToSite(level, worker, entry, template);
                }
            }
            if (worker.getWorkState() == WorkState.BUILDING) {
                BlockPos target = buildTarget(level, entry, template, worker);
                if (target == null || !atBuildTarget(level, worker, target)
                        || worker.distanceToSqr(target.getX() + 0.5D, target.getY(),
                        target.getZ() + 0.5D) > BUILD_LEAVE_DISTANCE_SQUARED) {
                    worker.setWorkState(WorkState.GOING_TO_BUILD);
                    navigateToSite(level, worker, entry, template);
                } else {
                    worker.getNavigation().stop();
                    worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    activeBuilders++;
                }
            }
        }

        int total = Math.min(entry.totalBlocks(), constructionBlocks(template, false).size());
        if (activeBuilders == 0 && entry.placedBlocks() < total) {
            // Stored work is deliberately retained, but never consumed without a builder.
            return;
        }
        advancePaletteFoundation(player, level, entry, template, activeBuilders);
    }

    /** Advances the saved palette order for paid sites and pre-layered legacy free saves. */
    private static void advancePaletteFoundation(ServerPlayer player, ServerLevel level,
                                                 RtsConstructionStore.Entry entry,
                                                 StructureTemplate template,
                                                 int activeBuilders) {
        List<StructureTemplate.StructureBlockInfo> blocks = constructionBlocks(template, false);
        int total = Math.min(entry.totalBlocks(), blocks.size());
        NaturalTreeClassifier.Scanner trees = naturalTreeScanner(level, entry, template);
        boolean allowNaturalTrees = trees != null;
        int work = entry.workProgress() + BASE_WORK_UNITS
                + activeBuilders * WORKER_WORK_UNITS;
        RtsConstructionStore.Entry current = entry;
        int placedThisThink = 0;
        while (current.placedBlocks() < total && work >= WORK_UNITS_PER_BLOCK
                && placedThisThink < MAX_BLOCKS_PER_THINK) {
            StructureTemplate.StructureBlockInfo block = blocks.get(current.placedBlocks());
            BlockPos local = BuildingPlacement.rotateOffset(block.pos().getX(), block.pos().getY(),
                    block.pos().getZ(), template.getSize(), current.rotation());
            BlockPos world = current.origin().offset(local.getX(), local.getY(), local.getZ());
            BlockState existing = level.getBlockState(world);
            BlockState placedState = block.state().rotate(current.rotation());
            if (!existing.equals(placedState)
                    && !BuildingPlacement.canReplaceExisting(level, world, existing,
                    local.getY() == 0, BuildingPlacement.isPathStructure(current.structure()),
                    allowNaturalTrees, trees)) {
                // A player or another world process changed a reserved cell. Never overwrite it;
                // leave the foundation paused until the obstruction is gone.
                break;
            }
            boolean newlyPlaced = !existing.equals(placedState);
            if (newlyPlaced && !level.setBlock(world, placedState, Block.UPDATE_ALL)) {
                break;
            }
            if (newlyPlaced) {
                // Server-level sound broadcasting already applies Minecraft's distance attenuation.
                var sound = placedState.getSoundType();
                level.playSound(null, world, sound.getPlaceSound(), SoundSource.BLOCKS,
                        sound.volume, sound.pitch);
            }
            work -= WORK_UNITS_PER_BLOCK;
            current = new RtsConstructionStore.Entry(current.id(), current.owner(),
                    current.structure(), current.origin(), current.rotation(), current.totalBlocks(),
                    current.placedBlocks() + 1, work, current.layered());
            RtsConstructionStore.get(level).update(current);
            placedThisThink++;
        }

        if (current.placedBlocks() >= total) {
            ModPayloads.completeConstruction(player, current);
        } else if (placedThisThink == 0 && work != current.workProgress()) {
            RtsConstructionStore.get(level).update(new RtsConstructionStore.Entry(
                    current.id(), current.owner(), current.structure(), current.origin(),
                    current.rotation(), current.totalBlocks(), current.placedBlocks(), work,
                    current.layered()));
        }
    }

    /** Places one complete local-Y layer for a fresh autonomous foundation. */
    private static void tickLayeredAutonomousFoundation(ServerPlayer player, ServerLevel level,
                                                        RtsConstructionStore.Entry entry,
                                                        StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> blocks = constructionBlocks(template, true);
        int total = Math.min(entry.totalBlocks(), blocks.size());
        if (total <= 0) {
            return;
        }

        int firstRemaining = firstMissingBlock(level, entry, template, blocks, total);
        if (firstRemaining < total) {
            int layerY = blocks.get(firstRemaining).pos().getY();
            NaturalTreeClassifier.Scanner trees = naturalTreeScanner(level, entry, template);
            boolean allowNaturalTrees = trees != null;
            for (int index = firstRemaining; index < total; index++) {
                StructureTemplate.StructureBlockInfo block = blocks.get(index);
                if (block.pos().getY() != layerY) {
                    break;
                }

                BlockPos local = BuildingPlacement.rotateOffset(block.pos().getX(), block.pos().getY(),
                        block.pos().getZ(), template.getSize(), entry.rotation());
                BlockPos world = entry.origin().offset(local.getX(), local.getY(), local.getZ());
                BlockState existing = level.getBlockState(world);
                BlockState placedState = block.state().rotate(entry.rotation());
                if (existing.equals(placedState)
                        || !BuildingPlacement.canReplaceExisting(level, world, existing,
                        local.getY() == 0, BuildingPlacement.isPathStructure(entry.structure()),
                        allowNaturalTrees, trees)) {
                    // Matching blocks are already complete. Obstructed blocks stay untouched and
                    // remain the first missing prefix, so this exact layer is retried next pass.
                    continue;
                }
                if (!level.setBlock(world, placedState, Block.UPDATE_ALL)) {
                    continue;
                }
                var sound = placedState.getSoundType();
                level.playSound(null, world, sound.getPlaceSound(), SoundSource.BLOCKS,
                        sound.volume, sound.pitch);
            }
        }

        int placedBlocks = firstMissingBlock(level, entry, template, blocks, total);
        RtsConstructionStore.Entry current = new RtsConstructionStore.Entry(
                entry.id(), entry.owner(), entry.structure(), entry.origin(), entry.rotation(),
                entry.totalBlocks(), placedBlocks, 0, true);
        if (!current.equals(entry)) {
            RtsConstructionStore.get(level).update(current);
        }
        if (placedBlocks >= total) {
            ModPayloads.completeConstruction(player, current);
        }
    }

    /** Returns the first saved-order block whose live world state is not its expected state. */
    private static int firstMissingBlock(ServerLevel level, RtsConstructionStore.Entry entry,
                                         StructureTemplate template,
                                         List<StructureTemplate.StructureBlockInfo> blocks,
                                         int total) {
        for (int index = 0; index < total; index++) {
            StructureTemplate.StructureBlockInfo block = blocks.get(index);
            BlockPos local = BuildingPlacement.rotateOffset(block.pos().getX(), block.pos().getY(),
                    block.pos().getZ(), template.getSize(), entry.rotation());
            BlockPos world = entry.origin().offset(local.getX(), local.getY(), local.getZ());
            if (!level.getBlockState(world).equals(block.state().rotate(entry.rotation()))) {
                return index;
            }
        }
        return total;
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
                candidate -> candidate.isAlive()
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
                List<StructureTemplate.StructureBlockInfo> blocks = constructionBlocks(template.template(),
                        entry.layered());
                if (entry.layered()) {
                    // A blocked cell can leave later cells in the same layer already assembled.
                    // The saved count is intentionally the completed prefix (it is what prevents
                    // the next layer from starting), so scan expected states here to clean every
                    // partial block without touching the obstruction itself.
                    for (StructureTemplate.StructureBlockInfo block : blocks) {
                        BlockPos local = BuildingPlacement.rotateOffset(block.pos().getX(), block.pos().getY(),
                                block.pos().getZ(), template.template().getSize(), entry.rotation());
                        BlockPos world = entry.origin().offset(local.getX(), local.getY(), local.getZ());
                        if (level.getBlockState(world).equals(block.state().rotate(entry.rotation()))) {
                            level.setBlock(world, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                            removed++;
                        }
                    }
                } else {
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
        BlockPos target = buildTarget(level, entry, template, worker);
        if (target == null) {
            worker.getNavigation().stop();
            RtsUnitOrders.clearNavigation(worker);
            return;
        }
        RtsUnitOrders.moveToSmart(worker, level, target, 0.9D);
    }

    private static BlockPos buildTarget(ServerLevel level, RtsConstructionStore.Entry entry,
                                        StructureTemplate template, RtsVillagerEntity worker) {
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
                best = closerWalkable(level, worker, best, x, minZ, entry.origin().getY(), centerX, centerZ);
                best = closerWalkable(level, worker, best, x, maxZ, entry.origin().getY(), centerX, centerZ);
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                best = closerWalkable(level, worker, best, minX, z, entry.origin().getY(), centerX, centerZ);
                best = closerWalkable(level, worker, best, maxX, z, entry.origin().getY(), centerX, centerZ);
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static BlockPos closerWalkable(ServerLevel level, RtsVillagerEntity worker,
                                           BlockPos current, int x, int z,
                                           int preferredY, double centerX, double centerZ) {
        int y = RtsEntities.findBottomFloorY(level, x, z, preferredY);
        BlockPos candidate = new BlockPos(x, y, z);
        if (!RtsNavigationSafety.isSafeFeet(level, candidate)) {
            return current;
        }
        if (worker != null) {
            var path = worker.getNavigation().createPath(candidate, 1);
            if (path == null || !path.canReach()
                    || !RtsNavigationSafety.isSafePath(level, worker.blockPosition(), path)) {
                return current;
            }
        }
        if (current != null && !RtsNavigationSafety.isSafeFeet(level, current)) {
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

    private static boolean atBuildTarget(ServerLevel level, RtsVillagerEntity worker, BlockPos target) {
        return target != null && RtsNavigationSafety.isSafeFeet(level, worker.blockPosition())
                && worker.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D)
                <= BUILD_ARRIVAL_DISTANCE_SQUARED;
    }

    /** Effective zero cost, including the dynamic free first Coal Mine price. */
    private static boolean isAutonomous(ServerPlayer player, RtsConstructionStore.Entry entry) {
        return Arrays.stream(BuildingCosts.placementCost(entry.structure(), player))
                .allMatch(cost -> cost <= 0);
    }

    /**
     * Builds the same bounded tree scanner for both construction modes. Once the first foundation
     * layer replaces a tree's soil, the active foundation's own box is allowed as temporary root
     * support; other tracked buildings and active foundations remain hard protection boundaries.
     */
    private static NaturalTreeClassifier.Scanner naturalTreeScanner(
            ServerLevel level, RtsConstructionStore.Entry entry, StructureTemplate template) {
        if (level == null || entry == null || template == null
                || BuildingPlacement.isLinearStructure(entry.structure())) {
            return null;
        }
        NaturalTreeClassifier.Bounds foundation = NaturalTreeClassifier.Bounds.from(entry.origin(),
                BuildingPlacement.rotateSize(template.getSize(), entry.rotation()));
        return new NaturalTreeClassifier.Scanner(level,
                RtsWorld.naturalTreeProtection(level, entry.id()), foundation::contains);
    }

    private static List<StructureTemplate.StructureBlockInfo> constructionBlocks(
            StructureTemplate template) {
        return constructionBlocks(template, false);
    }

    /** Returns palette order for paid work, or stable bottom-up layers for zero-cost work. */
    private static List<StructureTemplate.StructureBlockInfo> constructionBlocks(
            StructureTemplate template, boolean layered) {
        if (template == null || template.palettes.isEmpty()) {
            return List.of();
        }
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.palettes.get(0).blocks()) {
            if (!info.state().isAir() && !StructureSanitizer.isTechnicalMarker(info.state())) {
                blocks.add(info);
            }
        }
        if (layered) {
            blocks.sort(Comparator.comparingInt(info -> info.pos().getY()));
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
