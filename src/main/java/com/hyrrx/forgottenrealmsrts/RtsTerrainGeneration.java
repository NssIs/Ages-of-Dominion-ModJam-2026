package com.hyrrx.forgottenrealmsrts;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Applies the RTS presentation terrain while the generator still owns a new proto chunk.
 *
 * <p>The old implementation did this from a post-promotion load event. That event is emitted while a
 * {@link net.minecraft.world.level.chunk.LevelChunk} is being promoted to FULL, so reading or
 * changing the level there can race block-entity registration. Mutating the {@link ProtoChunk}
 * during feature generation keeps the work on the world-generation path and avoids that lifecycle
 * boundary entirely.
 */
public final class RtsTerrainGeneration {
    /** Direct proto-chunk writes need no neighbor, client, or block-entity side effects. */
    private static final int GENERATION_UPDATE_FLAGS = Block.UPDATE_NONE;

    private RtsTerrainGeneration() {
    }

    /**
     * Prepares one newly generated Overworld chunk for decoration.
     *
     * <p>Only a plain {@link ProtoChunk} is eligible. A saved full chunk reaches later generation
     * code through an {@link ImposterProtoChunk}, so existing worlds are not retrofitted when they
     * are loaded. Structure bounds and block-entity columns are left entirely alone.
     */
    public static void prepareOverworldDecoration(WorldGenLevel level, ChunkAccess chunk,
                                                  StructureManager structureManager) {
        if (level.isClientSide()
                || !level.getLevel().dimension().equals(Level.OVERWORLD)
                || !(chunk instanceof ProtoChunk)
                || chunk instanceof ImposterProtoChunk
                || chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        List<BoundingBox> structures = structureBounds(structureManager, chunkPos);
        Set<BlockPos> blockEntities = chunk.getBlockEntitiesPos();
        int seaLevel = level.getSeaLevel();
        int minY = chunk.getMinY();
        int maxY = chunk.getMaxY();
        boolean changed = false;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = chunkPos.getMinBlockX() + localX;
                int z = chunkPos.getMinBlockZ() + localZ;
                if (inStructure(structures, x, z) || hasBlockEntity(blockEntities, x, z)) {
                    continue;
                }

                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE,
                        localX, localZ);
                if (surfaceY < minY || surfaceY >= maxY
                        || !isDry(chunk, x, z, surfaceY, seaLevel)
                        || !canFlattenColumn(chunk, x, z, surfaceY, seaLevel)) {
                    continue;
                }

                changed |= flattenColumn(chunk, x, z, surfaceY, seaLevel);
            }
        }

        if (changed) {
            chunk.markUnsaved();
        }
    }

    /** Reads structure starts already available to this generation region before they are placed. */
    private static List<BoundingBox> structureBounds(StructureManager structureManager,
                                                     ChunkPos chunkPos) {
        if (!structureManager.shouldGenerateStructures()) {
            return List.of();
        }

        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        return structureManager.startsForStructure(chunkPos, structure -> true).stream()
                .map(StructureStart::getBoundingBox)
                .filter(bounds -> bounds.intersects(minX, minZ, maxX, maxZ))
                .toList();
    }

    private static boolean inStructure(List<BoundingBox> structures, int x, int z) {
        for (BoundingBox bounds : structures) {
            if (bounds.intersects(x, z, x, z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBlockEntity(Set<BlockPos> blockEntities, int x, int z) {
        for (BlockPos position : blockEntities) {
            if (position.getX() == x && position.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    /** A water, lava, or other-fluid column is preserved rather than turned into a dry plateau. */
    private static boolean isDry(ChunkAccess chunk, int x, int z, int surfaceY, int seaLevel) {
        int from = Math.min(surfaceY, seaLevel);
        int to = Math.max(surfaceY, seaLevel);
        for (int y = from; y <= to; y++) {
            if (!chunk.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Validates the fill side first so a non-air feature is never half-overwritten. */
    private static boolean canFlattenColumn(ChunkAccess chunk, int x, int z,
                                            int surfaceY, int seaLevel) {
        if (surfaceY >= seaLevel) {
            return true;
        }

        for (int y = surfaceY + 1; y <= seaLevel; y++) {
            BlockPos position = new BlockPos(x, y, z);
            if (!chunk.getBlockState(position).isAir()
                    || !chunk.getFluidState(position).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean flattenColumn(ChunkAccess chunk, int x, int z,
                                         int surfaceY, int seaLevel) {
        boolean changed = false;
        if (surfaceY > seaLevel) {
            for (int y = surfaceY; y > seaLevel; y--) {
                BlockPos position = new BlockPos(x, y, z);
                BlockState oldState = chunk.setBlockState(position, Blocks.AIR.defaultBlockState(),
                        GENERATION_UPDATE_FLAGS);
                changed |= oldState != null && !oldState.isAir();
            }
        } else if (surfaceY < seaLevel) {
            for (int y = surfaceY + 1; y < seaLevel; y++) {
                chunk.setBlockState(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(),
                        GENERATION_UPDATE_FLAGS);
                changed = true;
            }
        }

        BlockPos top = new BlockPos(x, seaLevel, z);
        BlockState oldTop = chunk.setBlockState(top, Blocks.GRASS_BLOCK.defaultBlockState(),
                GENERATION_UPDATE_FLAGS);
        return changed || oldTop != null && !oldTop.is(Blocks.GRASS_BLOCK);
    }
}
