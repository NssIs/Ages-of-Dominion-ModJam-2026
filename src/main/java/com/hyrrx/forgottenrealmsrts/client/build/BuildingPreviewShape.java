package com.hyrrx.forgottenrealmsrts.client.build;

import com.hyrrx.forgottenrealmsrts.BuildingPlacement;
import com.hyrrx.forgottenrealmsrts.network.BuildingPreviewPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A building's shape and colours on the client: where its blocks are, how big it is, and what each
 * block looks like from a distance.
 *
 * <p>Built from the same {@link BuildingPreviewPayload} the tray icons use, so siting a building
 * costs no extra network traffic — the blocks are already here by the time it can be selected.
 *
 * <p><strong>It is the shell, not the solid.</strong> The server culls fully enclosed blocks and
 * caps the count before sending, which is right for both uses: a preview only ever shows the
 * outside, and for collision the shell encloses the interior anyway. The server re-checks placement
 * against the real template regardless, so any imprecision here can only affect what colour the
 * ghost is drawn, never what is allowed.
 */
public final class BuildingPreviewShape {
    private static final Map<Identifier, BuildingPreviewShape> CACHE = new HashMap<>();

    /** One drawable block: its position within the structure and its map colour. */
    public record Voxel(int x, int y, int z, int argb) {
    }

    private final Vec3i size;
    private final Voxel[] voxels;
    /** Occupancy per rotation, built on demand — rotating is rare, so this is computed at most four
     *  times per building and never per frame. */
    private final Map<Rotation, Set<Long>> occupancy = new EnumMap<>(Rotation.class);

    private BuildingPreviewShape(Vec3i size, Voxel[] voxels) {
        this.size = size;
        this.voxels = voxels;
    }

    public static void accept(BuildingPreviewPayload payload) {
        int[] colours = new int[payload.palette().size()];
        for (int i = 0; i < colours.length; i++) {
            colours[i] = colourOf(payload.palette().get(i));
        }

        Voxel[] voxels = new Voxel[payload.blocks().size()];
        for (int i = 0; i < voxels.length; i++) {
            int packed = payload.blocks().get(i);
            voxels[i] = new Voxel(
                    BuildingPreviewPayload.unpackX(packed),
                    BuildingPreviewPayload.unpackY(packed),
                    BuildingPreviewPayload.unpackZ(packed),
                    colours[BuildingPreviewPayload.unpackPalette(packed)]);
        }

        CACHE.put(payload.structure(), new BuildingPreviewShape(
                new Vec3i(payload.sizeX(), payload.sizeY(), payload.sizeZ()), voxels));
    }

    public static BuildingPreviewShape of(Identifier structure) {
        return CACHE.get(structure);
    }

    /**
     * Minimal placement-only geometry used while a server preview is still arriving. It is not used
     * for the tray art or the placed structure; it simply keeps a path/wall target visible instead
     * of leaving the commander with a cursor and no indication of where the span will land.
     */
    public static BuildingPreviewShape fallback(Identifier structure) {
        if (BuildingPlacement.isPathStructure(structure)) {
            return new BuildingPreviewShape(new Vec3i(1, 1, 1),
                    new Voxel[]{new Voxel(0, 0, 0, 0xFF9A7958)});
        }
        if (BuildingPlacement.isWallStructure(structure)) {
            return new BuildingPreviewShape(new Vec3i(1, 1, 1),
                    new Voxel[]{new Voxel(0, 0, 0, 0xFF777777)});
        }
        return null;
    }

    public static void clear() {
        CACHE.clear();
    }

    /**
     * A block's colour seen from far enough away that its texture does not matter — which is exactly
     * the situation a ghost is in. {@link MapColor} is the game's own answer to that question, which
     * is why a minimap and a blueprint can share it.
     */
    private static int colourOf(Identifier blockId) {
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) {
            return 0xFF9E9E9E;
        }
        MapColor mapColor = block.defaultBlockState().getMapColor(null, BlockPos.ZERO);
        return mapColor == MapColor.NONE
                ? 0xFF9E9E9E
                : mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
    }

    public Vec3i size() {
        return size;
    }

    public Voxel[] voxels() {
        return voxels;
    }

    /** Whether the structure occupies this position once turned the given way. */
    public boolean occupiedRotated(int x, int y, int z, Rotation rotation) {
        return occupancy(rotation).contains(BlockPos.asLong(x, y, z));
    }

    private Set<Long> occupancy(Rotation rotation) {
        return occupancy.computeIfAbsent(rotation, key -> {
            Set<Long> positions = new HashSet<>(voxels.length);
            for (Voxel voxel : voxels) {
                BlockPos rotated = BuildingPlacement.rotateOffset(
                        voxel.x(), voxel.y(), voxel.z(), size, key);
                positions.add(BlockPos.asLong(rotated.getX(), rotated.getY(), rotated.getZ()));
            }
            return positions;
        });
    }
}
