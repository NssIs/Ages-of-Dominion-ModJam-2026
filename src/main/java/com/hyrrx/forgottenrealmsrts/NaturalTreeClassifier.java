package com.hyrrx.forgottenrealmsrts;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bounded recognition of naturally grown trees.
 *
 * <p>A block is only a natural-tree part when it belongs to a connected log group rooted in
 * natural ground and that group has a nearby leaf canopy. The conservative shape is intentional:
 * a single player-placed log, a timber frame, or a pumpkin field must remain an obstruction. The
 * scan is bounded both horizontally/vertically and by log count so it is safe to use from client
 * previews and render-thread raycasts as well as from server placement validation.</p>
 */
public final class NaturalTreeClassifier {
    /** Extension point for mod/datapack-registered blocks that are decorations on natural trees. */
    public static final TagKey<Block> TREE_DECORATIONS = BlockTags.create(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "tree_decorations"));

    public static final int COMPONENT_HORIZONTAL_RADIUS = 12;
    public static final int COMPONENT_BELOW_RADIUS = 8;
    public static final int COMPONENT_ABOVE_RADIUS = 32;
    public static final int CANOPY_MARGIN = 3;
    public static final int MAX_LOGS = 1024;
    private static final int MAX_CANOPY_BLOCKS = 32_768;

    /** Caller-owned protection, used for tracked buildings and active foundations. */
    @FunctionalInterface
    public interface Protection {
        boolean protects(BlockPos position);
    }

    public static final Protection NONE = position -> false;

    /** An exclusive world-space box used to protect a known structure from tree recognition. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Bounds from(BlockPos origin, net.minecraft.core.Vec3i size) {
            return new Bounds(origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + size.getX(), origin.getY() + size.getY(),
                    origin.getZ() + size.getZ());
        }

        public boolean contains(BlockPos position) {
            return position.getX() >= minX && position.getX() < maxX
                    && position.getY() >= minY && position.getY() < maxY
                    && position.getZ() >= minZ && position.getZ() < maxZ;
        }
    }

    /** The recognised logs and the nearby canopy/decorations belonging to them. */
    public record Component(Set<Long> logs, Set<Long> parts,
                            int minX, int minY, int minZ,
                            int maxX, int maxY, int maxZ) {
    }

    private NaturalTreeClassifier() {
    }

    /** Builds an immutable protection predicate from structure boxes. */
    public static Protection protectionOf(Iterable<Bounds> bounds) {
        List<Bounds> copy = new ArrayList<>();
        if (bounds != null) {
            for (Bounds bound : bounds) {
                if (bound != null) {
                    copy.add(bound);
                }
            }
        }
        List<Bounds> protectedBounds = List.copyOf(copy);
        return position -> {
            for (Bounds bound : protectedBounds) {
                if (bound.contains(position)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Convenience query for one block; repeated queries should use a {@link Scanner}. */
    public static boolean isNaturalTreePart(BlockGetter level, BlockPos position,
                                            Protection protection) {
        return new Scanner(level, protection).isNaturalTreePart(position);
    }

    /** Whether a state can be a trunk/branch in a recognised natural tree. */
    public static boolean isLog(BlockState state) {
        return state != null && state.is(BlockTags.LOGS);
    }

    /** Whether a state is a tree canopy or one of the supported tree decorations. */
    public static boolean isTreePart(BlockState state) {
        if (state == null) {
            return false;
        }
        return isLog(state) || state.is(BlockTags.LEAVES)
                || state.is(TREE_DECORATIONS)
                || state.is(Blocks.VINE)
                || state.is(Blocks.WEEPING_VINES)
                || state.is(Blocks.WEEPING_VINES_PLANT)
                || state.is(Blocks.TWISTING_VINES)
                || state.is(Blocks.TWISTING_VINES_PLANT)
                || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT)
                || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.JACK_O_LANTERN)
                || state.is(Blocks.COCOA);
    }

    /** Ground on which a naturally generated trunk may be rooted. */
    public static boolean isNaturalGround(BlockState state) {
        return state != null && (state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK));
    }

    /** A per-query cache so a footprint or a ray does not flood-fill the same tree repeatedly. */
    public static final class Scanner {
        private final BlockGetter level;
        private final Protection protection;
        /** The active foundation may serve as the temporary support after its base replaces soil. */
        private final Protection rootSupport;
        private final Map<Long, Component> componentsByPart = new HashMap<>();
        private final Set<Long> rejectedParts = new HashSet<>();

        public Scanner(BlockGetter level, Protection protection) {
            this(level, protection, NONE);
        }

        /**
         * Creates a scanner with an optional bounded support predicate for incremental builds.
         * This does not make arbitrary logs natural: the connected group still needs nearby leaves,
         * and only the caller's current foundation may provide the temporary root support.
         */
        public Scanner(BlockGetter level, Protection protection, Protection rootSupport) {
            this.level = level;
            this.protection = protection == null ? NONE : protection;
            this.rootSupport = rootSupport == null ? NONE : rootSupport;
        }

        public boolean isNaturalTreePart(BlockPos seed) {
            return componentAt(seed) != null;
        }

        /** Finds the bounded natural-tree component containing the supplied tree-like block. */
        public Component componentAt(BlockPos seed) {
            if (level == null || seed == null) {
                return null;
            }
            long seedKey = seed.asLong();
            if (componentsByPart.containsKey(seedKey)) {
                return componentsByPart.get(seedKey);
            }
            if (rejectedParts.contains(seedKey) || protectedOrUnavailable(seed)) {
                rejectedParts.add(seedKey);
                return null;
            }
            BlockState seedState = level.getBlockState(seed);
            if (!isTreePart(seedState)) {
                rejectedParts.add(seedKey);
                return null;
            }

            if (isLog(seedState)) {
                Component component = fromLogSeed(seed);
                remember(component);
                if (component == null) {
                    // The world and protection predicate are stable for one Scanner lifetime, so
                    // a failed seed cannot become valid later in the same bounded query. Caching
                    // this negative result prevents each nearby canopy candidate from repeating
                    // the same flood-fill for an unrooted/player-built log group.
                    rejectedParts.add(seedKey);
                }
                return component != null && component.parts().contains(seedKey) ? component : null;
            }

            // A leaf/decorative block is not enough by itself. Search the bounded volume for a
            // trunk, then require the component's canopy envelope to contain this seed.
            int minX = seed.getX() - COMPONENT_HORIZONTAL_RADIUS;
            int maxX = seed.getX() + COMPONENT_HORIZONTAL_RADIUS;
            int minY = seed.getY() - COMPONENT_ABOVE_RADIUS;
            int maxY = seed.getY() + COMPONENT_BELOW_RADIUS;
            int minZ = seed.getZ() - COMPONENT_HORIZONTAL_RADIUS;
            int maxZ = seed.getZ() + COMPONENT_HORIZONTAL_RADIUS;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos candidate = new BlockPos(x, y, z);
                        if (!isLog(level.getBlockState(candidate)) || protectedOrUnavailable(candidate)) {
                            continue;
                        }
                        long candidateKey = candidate.asLong();
                        if (componentsByPart.containsKey(candidateKey)) {
                            Component component = componentsByPart.get(candidateKey);
                            if (component.parts().contains(seedKey)) {
                                return component;
                            }
                            continue;
                        }
                        if (rejectedParts.contains(candidateKey)) {
                            continue;
                        }
                        Component component = fromLogSeed(candidate);
                        remember(component);
                        if (component == null) {
                            rejectedParts.add(candidateKey);
                        }
                        if (component != null && component.parts().contains(seedKey)) {
                            return component;
                        }
                    }
                }
            }
            rejectedParts.add(seedKey);
            return null;
        }

        private Component fromLogSeed(BlockPos seed) {
            ArrayDeque<BlockPos> open = new ArrayDeque<>();
            Set<Long> visited = new HashSet<>();
            Set<Long> logs = new HashSet<>();
            open.add(seed.immutable());
            int minX = seed.getX();
            int maxX = seed.getX();
            int minY = seed.getY();
            int maxY = seed.getY();
            int minZ = seed.getZ();
            int maxZ = seed.getZ();
            boolean rooted = false;

            while (!open.isEmpty()) {
                BlockPos current = open.removeFirst();
                if (!visited.add(current.asLong())
                        || current.getX() < seed.getX() - COMPONENT_HORIZONTAL_RADIUS
                        || current.getX() > seed.getX() + COMPONENT_HORIZONTAL_RADIUS
                        || current.getY() < seed.getY() - COMPONENT_BELOW_RADIUS
                        || current.getY() > seed.getY() + COMPONENT_ABOVE_RADIUS
                        || current.getZ() < seed.getZ() - COMPONENT_HORIZONTAL_RADIUS
                        || current.getZ() > seed.getZ() + COMPONENT_HORIZONTAL_RADIUS
                        || protectedOrUnavailable(current)
                        || !isLog(level.getBlockState(current))) {
                    continue;
                }
                logs.add(current.asLong());
                if (logs.size() > MAX_LOGS) {
                    return null;
                }
                rooted |= isNaturalGround(level.getBlockState(current.below()))
                        || rootSupport.protects(current.below());
                minX = Math.min(minX, current.getX());
                maxX = Math.max(maxX, current.getX());
                minY = Math.min(minY, current.getY());
                maxY = Math.max(maxY, current.getY());
                minZ = Math.min(minZ, current.getZ());
                maxZ = Math.max(maxZ, current.getZ());
                for (Direction direction : Direction.values()) {
                    open.addLast(current.relative(direction));
                }
            }

            if (logs.isEmpty() || !rooted) {
                return null;
            }

            int canopyMinX = minX - CANOPY_MARGIN;
            int canopyMaxX = maxX + CANOPY_MARGIN;
            int canopyMinY = minY - CANOPY_MARGIN;
            int canopyMaxY = maxY + CANOPY_MARGIN + 1;
            int canopyMinZ = minZ - CANOPY_MARGIN;
            int canopyMaxZ = maxZ + CANOPY_MARGIN;
            long volume = (long) canopyMaxX - canopyMinX + 1L;
            volume *= (long) canopyMaxY - canopyMinY + 1L;
            volume *= (long) canopyMaxZ - canopyMinZ + 1L;
            if (volume > MAX_CANOPY_BLOCKS) {
                return null;
            }

            Set<Long> parts = new HashSet<>(logs);
            boolean nearbyLeaves = false;
            for (int x = canopyMinX; x <= canopyMaxX; x++) {
                for (int y = canopyMinY; y <= canopyMaxY; y++) {
                    for (int z = canopyMinZ; z <= canopyMaxZ; z++) {
                        BlockPos candidate = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(candidate);
                        if (state.is(BlockTags.LEAVES)) {
                            nearbyLeaves = true;
                        }
                        if (isTreePart(state) && !protectedOrUnavailable(candidate)) {
                            parts.add(candidate.asLong());
                        }
                    }
                }
            }
            if (!nearbyLeaves) {
                return null;
            }
            return new Component(Set.copyOf(logs), Set.copyOf(parts),
                    minX, minY, minZ, maxX, maxY, maxZ);
        }

        private boolean protectedOrUnavailable(BlockPos position) {
            BlockState state = level.getBlockState(position);
            return protection.protects(position) || level.getBlockEntity(position) != null
                    || !state.getFluidState().isEmpty();
        }

        private void remember(Component component) {
            if (component == null) {
                return;
            }
            for (long part : component.parts()) {
                componentsByPart.put(part, component);
            }
        }
    }
}
