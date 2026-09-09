package com.hyrrx.forgottenrealmsrts;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The world around the realm: the <em>echoes of the past</em> made physical.
 *
 * <p>Ambient wild herds roam the same ground, kept to a cap so the world feels alive without
 * overrunning the player's settlement. RTS structures are tracked separately by
 * {@link RtsBuildingStore}, which lets a confirmed replacement town remove only the old RTS builds.
 */
public final class RtsWorld {
    private static final int WILDLIFE_CAP = 8;
    private static final int WILDLIFE_RADIUS = 52;
    private static final int WILDLIFE_INTERVAL_TICKS = 600;
    private static final long FLOOR_ITEM_CLEANUP_INTERVAL_TICKS = 6000L;
    private static final double FLOOR_ITEM_CLEANUP_RADIUS = 128.0D;
    private static final Map<ServerLevel, Long> LAST_FLOOR_ITEM_CLEANUP = new WeakHashMap<>();
    private static final int TOWN_HALL_TREE_CLEAR_RADIUS = 25;
    private static final int TREE_SCAN_BELOW_SURFACE = 48;

    private static final EntityType<?>[] WILDLIFE = {
            EntityType.HORSE, EntityType.COW, EntityType.PIG, EntityType.SHEEP,
    };

    private RtsWorld() {
    }

    /**
     * Destroys the blocks recorded for one player's old RTS town and removes their metadata. Natural
     * terrain and unrelated players' structures are left untouched.
     */
    public static int clearOwnedBuildings(ServerLevel level, UUID owner) {
        sanitizeOwnedBuildings(level, owner);
        RtsBuildingStore store = RtsBuildingStore.get(level);
        List<RtsBuildingStore.Entry> owned = store.entries().stream()
                .filter(entry -> entry.owner().equals(owner))
                .toList();
        Set<Long> blocks = new HashSet<>();

        for (RtsBuildingStore.Entry entry : owned) {
            Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
            if (found.isEmpty() || found.get().palettes.isEmpty()) {
                continue;
            }
            StructureTemplate template = found.get();
            BlockPos origin = RtsBuildingDurability.normalizedOrigin(level, entry);
            Vec3i size = RtsBuildingDurability.rotatedSize(level, entry);
            RtsInvasion.spawnRealmCollapseStrike(level, origin,
                    size);
            for (BlockPos block : RtsBuildingDurability.trackedBlocks(level, entry)) {
                blocks.add(block.asLong());
            }
        }

        for (long packed : blocks) {
            level.setBlock(BlockPos.of(packed), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return store.removeOwner(owner);
    }

    /**
     * Clears natural trees from the exact 51x51 horizontal square around a newly placed Town Hall.
     *
     * <p>Only a connected log group rooted in natural soil and carrying a nearby leaf canopy is
     * considered a tree. Tracked buildings and all active construction footprints are protected
     * before the scan begins, so wood used by an RTS structure is never mistaken for a tree. The
     * live block write is deliberately a silent replacement with no loot/drop call: cancellation
     * removes the partial foundation but never restores this cleared terrain.</p>
     */
    public static int clearNaturalTrees(ServerLevel level, BlockPos hallOrigin, Vec3i hallSize) {
        return clearNaturalTrees(level, hallOrigin, hallSize, Long.MIN_VALUE);
    }

    /** Clears the Town Hall square while leaving the new foundation itself available for its build. */
    public static int clearNaturalTrees(ServerLevel level, BlockPos hallOrigin, Vec3i hallSize,
                                        long constructionIdToBuild) {
        if (level == null || hallOrigin == null || hallSize == null
                || hallSize.getX() <= 0 || hallSize.getZ() <= 0) {
            return 0;
        }

        int centerX = hallOrigin.getX() + hallSize.getX() / 2;
        int centerZ = hallOrigin.getZ() + hallSize.getZ() / 2;
        int minX = centerX - TOWN_HALL_TREE_CLEAR_RADIUS;
        int maxX = centerX + TOWN_HALL_TREE_CLEAR_RADIUS;
        int minZ = centerZ - TOWN_HALL_TREE_CLEAR_RADIUS;
        int maxZ = centerZ + TOWN_HALL_TREE_CLEAR_RADIUS;
        NaturalTreeClassifier.Protection protection = naturalTreeProtection(level, constructionIdToBuild);
        NaturalTreeClassifier.Scanner trees = new NaturalTreeClassifier.Scanner(level, protection);
        Set<Long> inspectedLogs = new HashSet<>();
        List<NaturalTreeClassifier.Component> naturalTrees = new ArrayList<>();

        // Load the bounded recognition envelope as well as the exact removal square. No block
        // outside minX..maxX/minZ..maxZ is ever added to the removal set.
        Set<Long> loadedChunks = new HashSet<>();
        for (int x = minX - NaturalTreeClassifier.COMPONENT_HORIZONTAL_RADIUS;
             x <= maxX + NaturalTreeClassifier.COMPONENT_HORIZONTAL_RADIUS; x++) {
            for (int z = minZ - NaturalTreeClassifier.COMPONENT_HORIZONTAL_RADIUS;
                 z <= maxZ + NaturalTreeClassifier.COMPONENT_HORIZONTAL_RADIUS; z++) {
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                long chunkKey = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
                if (loadedChunks.add(chunkKey)) {
                    level.getChunk(chunkX, chunkZ);
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int minY = Math.max(level.getMinY(), surface - TREE_SCAN_BELOW_SURFACE);
                int maxY = Math.min(level.getMaxY(), surface + 1);
                for (int y = minY; y < maxY; y++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!NaturalTreeClassifier.isLog(level.getBlockState(candidate))
                            || protection.protects(candidate)
                            || !inspectedLogs.add(candidate.asLong())) {
                        continue;
                    }
                    NaturalTreeClassifier.Component component = trees.componentAt(candidate);
                    if (component != null) {
                        inspectedLogs.addAll(component.logs());
                        naturalTrees.add(component);
                    }
                }
            }
        }

        Set<Long> removable = new HashSet<>();
        for (NaturalTreeClassifier.Component tree : naturalTrees) {
            for (long packed : tree.parts()) {
                BlockPos part = BlockPos.of(packed);
                if (insideSquare(part, minX, maxX, minZ, maxZ)
                        && !protection.protects(part)) {
                    removable.add(packed);
                }
            }
        }

        int removed = 0;
        for (long packed : removable) {
            BlockPos position = BlockPos.of(packed);
            if (!protection.protects(position)
                    && NaturalTreeClassifier.isTreePart(level.getBlockState(position))
                    && level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Returns the metadata-backed protection used by every server-side natural-tree decision.
     * Unknown/player-authored structures remain protected by the classifier's conservative rooted
     * tree test; known RTS structures are excluded explicitly before the flood fill starts.
     */
    public static NaturalTreeClassifier.Protection naturalTreeProtection(ServerLevel level) {
        return naturalTreeProtection(level, Long.MIN_VALUE);
    }

    /**
     * Returns protection for all known structures except one foundation currently being advanced.
     * The active entry still needs the allowance so a tree can be replaced by its own next block;
     * every other tracked building and foundation remains a hard boundary for the classifier.
     */
    public static NaturalTreeClassifier.Protection naturalTreeProtection(ServerLevel level,
                                                                           long ignoredConstructionId) {
        List<NaturalTreeClassifier.Bounds> result = new ArrayList<>();
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            BlockPos origin = RtsBuildingDurability.normalizedOrigin(level, entry);
            Vec3i size = RtsBuildingDurability.rotatedSize(level, entry);
            result.add(NaturalTreeClassifier.Bounds.from(origin, size));
        }
        for (RtsConstructionStore.Entry entry : RtsConstructionStore.get(level).entries()) {
            if (entry.id() == ignoredConstructionId) {
                continue;
            }
            Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
            if (found.isEmpty() || found.get().palettes.isEmpty()) {
                continue;
            }
            result.add(NaturalTreeClassifier.Bounds.from(entry.origin(),
                    BuildingPlacement.rotateSize(found.get().getSize(), entry.rotation())));
        }
        return NaturalTreeClassifier.protectionOf(result);
    }

    private static boolean insideSquare(BlockPos position, int minX, int maxX,
                                        int minZ, int maxZ) {
        return position.getX() >= minX && position.getX() <= maxX
                && position.getZ() >= minZ && position.getZ() <= maxZ;
    }

    /**
     * Removes only technical editor/test markers from one player's tracked structures. The pass is
     * safe to repeat and never touches an untracked world build.
     */
    public static int sanitizeOwnedBuildings(ServerLevel level, UUID owner) {
        int removed = 0;
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            if (!entry.owner().equals(owner)) {
                continue;
            }
            if (BuildingPlacement.isLinearStructure(entry.structure())) {
                continue;
            }
            Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
            if (found.isEmpty() || found.get().palettes.isEmpty()) {
                continue;
            }
            StructureTemplate template = found.get();
            BlockPos origin = entry.origin();
            if (!entry.normalizedOrigin()) {
                BlockPos offset = template.getZeroPositionWithTransform(BlockPos.ZERO, Mirror.NONE,
                        entry.rotation());
                origin = origin.offset(-offset.getX(), -offset.getY(), -offset.getZ());
            }
            removed += StructureSanitizer.sanitizePlacedStructure(level, template, origin,
                    entry.rotation());
        }
        return removed;
    }

    /** Keeps a small population of wild fauna roaming near a founded settlement. */
    public static void tickWildlife(ServerPlayer player) {
        if (!RtsCivilization.isFounded(player)
                || player.tickCount % WILDLIFE_INTERVAL_TICKS != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = RtsEntities.townHallCenter(player).orElse(player.blockPosition());
        AABB area = new AABB(center).inflate(WILDLIFE_RADIUS);
        int living = level.getEntitiesOfClass(Animal.class, area, Animal::isAlive).size();
        if (living >= WILDLIFE_CAP) {
            return;
        }

        RandomSource random = level.getRandom();
        int toSpawn = Math.min(2, WILDLIFE_CAP - living);
        for (int i = 0; i < toSpawn; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 20.0D + random.nextDouble() * (WILDLIFE_RADIUS - 20.0D);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            Entity beast = WILDLIFE[random.nextInt(WILDLIFE.length)].create(level, EntitySpawnReason.NATURAL);
            if (beast instanceof Animal animal) {
                animal.setPos(x + 0.5D, y, z + 0.5D);
                animal.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                        EntitySpawnReason.NATURAL, null);
                level.addFreshEntity(animal);
            }
        }
    }

    /** Removes only dropped item entities resting on the ground inside the active realm. */
    public static void tickDroppedItems(ServerPlayer player) {
        if (!RtsCivilization.isFounded(player)) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        if (now <= 0L || now % FLOOR_ITEM_CLEANUP_INTERVAL_TICKS != 0L
                || LAST_FLOOR_ITEM_CLEANUP.getOrDefault(level, Long.MIN_VALUE) == now) {
            return;
        }
        LAST_FLOOR_ITEM_CLEANUP.put(level, now);

        BlockPos center = RtsEntities.townHallCenter(player).orElse(player.blockPosition());
        for (ItemEntity item : level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(center).inflate(FLOOR_ITEM_CLEANUP_RADIUS),
                ItemEntity::onGround)) {
            item.discard();
        }
    }
}
