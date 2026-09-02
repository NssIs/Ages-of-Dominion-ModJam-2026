package com.hyrrx.forgottenrealmsrts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Whether a building may stand at a given spot.
 *
 * <p><strong>Shared by the client ghost and the server placement handler on purpose.</strong> The
 * ghost decides what colour to draw and the server decides what actually happens; if those two used
 * different rules the preview would lie, which is worse than having no preview. The client's answer
 * is a courtesy — the server's is the one that counts, and it is re-run on every placement request
 * because a client can send whatever it likes.
 */
public final class BuildingPlacement {
    /** Maximum number of path tiles or wall segments one request may place. */
    public static final int MAX_LINEAR_PIECES = 256;

    /** The result of expanding a path or wall from one anchor to the current cursor cell. */
    public record LinearLayout(Rotation rotation, List<BlockPos> origins,
                               int columns, int rows) {
        public int pieces() {
            return origins.size();
        }
    }

    /** Why a placement was refused, or {@link #OK}. */
    public enum Result {
        OK,
        /** A base-layer column has nothing solid under it — the building would float. */
        UNSUPPORTED,
        /** Something that is not air and not replaceable is in the way. */
        OBSTRUCTED,
        /** The town hall has to be founded first. */
        TOWN_HALL_REQUIRED,
        /** The player must finish naming the civilization first. */
        FOUNDING_REQUIRED,
        /** The onboarding Coal Mine must be placed before other buildings. */
        COAL_MINE_REQUIRED,
        /** The stockpiles do not cover the price. */
        UNAFFORDABLE,
        /** The structure could not be read at all. */
        UNKNOWN_STRUCTURE;

        public boolean ok() {
            return this == OK;
        }

        /**
         * Whether this is a *geometry* failure, i.e. the sort that should turn the ghost red.
         * Cost and ordering failures are reported as text instead — the building fits fine, you
         * just cannot have it yet, and colouring it red for that would be misleading.
         */
        public boolean geometric() {
            return this == UNSUPPORTED || this == OBSTRUCTED;
        }

        /** Kept short on purpose: this has to fit a narrow HUD panel and the action bar. */
        public String message() {
            return switch (this) {
                case OK -> "";
                case UNSUPPORTED -> "Needs solid ground";
                case OBSTRUCTED -> "Blocked";
                case TOWN_HALL_REQUIRED -> "Town Hall first";
                case FOUNDING_REQUIRED -> "Found your civilization first";
                case COAL_MINE_REQUIRED -> "Place the free Coal Mine first";
                case UNAFFORDABLE -> "Not enough resources";
                case UNKNOWN_STRUCTURE -> "Could not load building";
            };
        }
    }

    private BuildingPlacement() {
    }

    /**
     * Checks the ground and the space, given the structure's own solid-block footprint.
     *
     * <p>{@code solid} is asked whether the structure has a non-air block at a local position. It is
     * a callback rather than a collection so the client can answer from its shell-only preview cache
     * and the server from the real template, without either having to build the other's data shape.
     *
     * @param origin world position of the structure's local (0,0,0) after rotation
     * @param size   the structure's size, already rotated
     */
    public static Result checkGeometry(BlockGetter level, BlockPos origin, Vec3i size, Footprint solid) {
        return checkGeometry(level, origin, size, solid, Set.of());
    }

    /**
     * Same check as {@link #checkGeometry(BlockGetter, BlockPos, Vec3i, Footprint)}, but ignores
     * collision at the supplied world positions. Move and upgrade use this for the source building:
     * the source is cleared only after the destination has passed every validation check, immediately
     * before the transactional structure write.
     */
    public static Result checkGeometry(BlockGetter level, BlockPos origin, Vec3i size, Footprint solid,
                                       Set<Long> ignoredPositions) {
        return checkGeometry(level, origin, size, solid, ignoredPositions, false);
    }

    /**
     * Same check as the ordinary footprint test, with an explicit allowance for a path's surface
     * replacement. Paths are deliberately the only structures allowed to remove a surface block:
     * a wall or a building must never silently eat terrain that happens to be in its footprint.
     */
    public static Result checkGeometry(BlockGetter level, BlockPos origin, Vec3i size, Footprint solid,
                                       Set<Long> ignoredPositions, boolean allowPathSurface) {
        boolean anySupported = false;

        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                // The lowest solid block in this column, if the structure has one here at all.
                int lowest = -1;
                for (int y = 0; y < size.getY(); y++) {
                    if (solid.has(x, y, z)) {
                        lowest = y;
                        break;
                    }
                }
                if (lowest < 0) {
                    continue;
                }

                // Support: only the columns that actually reach the structure's base layer stand on
                // the ground, and only those need something solid under them.
                //
                // This used to test `lowest - 1` for *every* column, which meant a column starting
                // higher up — a roof overhang, an arch, a cantilevered walkway — looked one block
                // below its own lowest block, i.e. into the structure's own airspace. That is air,
                // so a single overhang made the whole building UNSUPPORTED, which is why every site
                // on flat grass came back red. A block held up by the rest of the building does not
                // need ground beneath it; a block on the base layer does.
                //
                // It also settles a client/server disagreement: the preview drops barriers and the
                // real footprint keeps them, so a barrier column had lowest == 1 on the server and
                // did not exist at all on the client. Neither side asks about it now.
                if (lowest == 0) {
                    BlockPos below = origin.offset(x, -1, z);
                    // Collision shape is the gameplay definition of support. `isSolid()` is a
                    // rendering/material shortcut and rejects some perfectly walkable terrain,
                    // which made the path ghost say Blocked while the cursor was over grass.
                    if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                        return Result.UNSUPPORTED;
                    }
                    anySupported = true;
                }

                // Collision: everything the structure occupies must be free. The structure's own
                // bottom layer is its foundation (these builds carry their dirt and grass with
                // them), so it is allowed to replace the surface it sits on like any other
                // replaceable block.
                for (int y = lowest; y < size.getY(); y++) {
                    if (!solid.has(x, y, z)) {
                        continue;
                    }
                    BlockPos worldPos = origin.offset(x, y, z);
                    if (ignoredPositions.contains(worldPos.asLong())) {
                        continue;
                    }
                    BlockState existing = level.getBlockState(worldPos);
                    boolean pathSurface = allowPathSurface && y == lowest && isPathSurface(existing);
                    if (!existing.isAir() && !existing.canBeReplaced() && !pathSurface) {
                        return Result.OBSTRUCTED;
                    }
                }
            }
        }

        return anySupported ? Result.OK : Result.UNSUPPORTED;
    }

    /** Whether this structure is one of the two deliberately expandable build-menu entries. */
    public static boolean isLinearStructure(Identifier structure) {
        return isPathStructure(structure) || isWallStructure(structure);
    }

    public static boolean isPathStructure(Identifier structure) {
        return namedStructure(structure, "path");
    }

    public static boolean isWallStructure(Identifier structure) {
        return namedStructure(structure, "wall");
    }

    private static boolean namedStructure(Identifier structure, String name) {
        String path = structure.getPath();
        return path.equals(name) || path.startsWith(name + "/")
                || path.endsWith("/" + name) || path.contains("/" + name + "/");
    }

    /** Expands a drag into a dominant-axis, one-block-wide line of 1×1×1 cells. */
    public static LinearLayout linearLayout(Identifier structure, Vec3i templateSize,
                                            BlockPos anchor, BlockPos cursor) {
        if (!isLinearStructure(structure) || templateSize.getX() <= 0
                || templateSize.getY() <= 0 || templateSize.getZ() <= 0) {
            return new LinearLayout(Rotation.NONE, List.of(), 0, 0);
        }

        int deltaX = cursor.getX() - anchor.getX();
        int deltaZ = cursor.getZ() - anchor.getZ();
        boolean alongX = Math.abs(deltaX) >= Math.abs(deltaZ);
        int pieces = Math.min(MAX_LINEAR_PIECES,
                1 + (alongX ? Math.abs(deltaX) : Math.abs(deltaZ)));
        int step = alongX ? direction(anchor.getX(), cursor.getX())
                : direction(anchor.getZ(), cursor.getZ());
        List<BlockPos> origins = new ArrayList<>(pieces);
        for (int index = 0; index < pieces; index++) {
            origins.add(alongX
                    ? new BlockPos(anchor.getX() + step * index, anchor.getY(), anchor.getZ())
                    : new BlockPos(anchor.getX(), anchor.getY(), anchor.getZ() + step * index));
        }
        return new LinearLayout(Rotation.NONE, List.copyOf(origins),
                alongX ? pieces : 1, alongX ? 1 : pieces);
    }

    private static int direction(int anchor, int cursor) {
        return Integer.compare(cursor, anchor);
    }

    /** Checks a complete line once: one solid-ground anchor is enough for the whole drag. */
    public static Result checkLinearGeometry(BlockGetter level, List<BlockPos> cells,
                                             boolean path, Set<Long> ignoredPositions) {
        if (cells == null || cells.isEmpty()) {
            return Result.UNKNOWN_STRUCTURE;
        }
        boolean anySupported = false;
        for (BlockPos cell : cells) {
            if (!level.getBlockState(cell.below()).getCollisionShape(level, cell.below()).isEmpty()) {
                anySupported = true;
            }
            if (ignoredPositions != null && ignoredPositions.contains(cell.asLong())) {
                continue;
            }
            BlockState existing = level.getBlockState(cell);
            boolean pathSurface = path && isPathSurface(existing);
            boolean existingPath = path && existing.is(Blocks.DIRT_PATH);
            if (!existing.isAir() && !existing.canBeReplaced() && !pathSurface && !existingPath) {
                return Result.OBSTRUCTED;
            }
        }
        return anySupported ? Result.OK : Result.UNSUPPORTED;
    }

    /** Blocks a path may replace at its own surface layer. Stone, wood and structures stay safe. */
    public static boolean isPathSurface(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL);
    }

    /** Whether every occupied block in this footprint is already one of our dirt-path tiles. */
    public static boolean isExistingPathTile(BlockGetter level, BlockPos origin, Vec3i size,
                                             Footprint solid) {
        int occupied = 0;
        int paths = 0;
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    if (!solid.has(x, y, z)) {
                        continue;
                    }
                    occupied++;
                    if (level.getBlockState(origin.offset(x, y, z)).is(Blocks.DIRT_PATH)) {
                        paths++;
                    }
                }
            }
        }
        return occupied > 0 && occupied == paths;
    }

    /** Rotates a structure's size, because a quarter turn swaps its x and z extents. */
    public static Vec3i rotateSize(Vec3i size, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(size.getZ(), size.getY(), size.getX());
            default -> size;
        };
    }

    /** Rotates a local block offset within a structure of the given (unrotated) size. */
    public static BlockPos rotateOffset(int x, int y, int z, Vec3i size, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(size.getZ() - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(size.getX() - 1 - x, y, size.getZ() - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, size.getX() - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    /** "Does the structure have a solid block at this local position?" */
    @FunctionalInterface
    public interface Footprint {
        boolean has(int x, int y, int z);
    }
}
