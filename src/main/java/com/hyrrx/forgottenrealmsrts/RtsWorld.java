package com.hyrrx.forgottenrealmsrts;

import java.util.HashSet;
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
