package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.NaturalTreeClassifier;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.camera.IsometricCameraController;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Fades only natural trees that sit in the short close-zoom camera/player corridor. */
public final class NaturalTreeTransparencyController {
    public static final int CLOSE_ZOOM_DISTANCE = 32;
    public static final float TREE_OPACITY = 0.33F;

    private static final double CORRIDOR_RADIUS = 1.65D;
    private static final double SAMPLE_STEP = 0.65D;
    private static final int MAX_SAMPLES = 64;
    private static final int MAX_AGGREGATE_FADED_POSITIONS = 8192;
    static final int CORRIDOR_SCAN_INTERVAL_TICKS = 2;
    private static final int FADE_COLOR = ARGB.colorFromFloat(
            TREE_OPACITY, 1.0F, 1.0F, 1.0F);

    private static volatile ClientLevel activeLevel;
    private static volatile Set<Long> fadedPositions = Set.of();
    private static ClientLevel observedLevel;
    private static boolean closeZoom;
    private static ScanQuery lastQuery;
    private static ScanQuery pendingQuery;
    private static int scanCooldownTicks;
    private static final ThreadLocal<Boolean> FADED_MESH =
            ThreadLocal.withInitial(() -> false);

    /** Immutable camera/player query key used to coalesce movement during the scan cooldown. */
    record ScanQuery(Object level,
                     double fromX, double fromY, double fromZ,
                     double toX, double toY, double toZ) {
        static ScanQuery of(ClientLevel level, Vec3 from, Vec3 to) {
            return new ScanQuery(level, from.x, from.y, from.z, to.x, to.y, to.z);
        }

        boolean samePosition(ScanQuery other) {
            return other != null && level == other.level
                    && Double.compare(fromX, other.fromX) == 0
                    && Double.compare(fromY, other.fromY) == 0
                    && Double.compare(fromZ, other.fromZ) == 0
                    && Double.compare(toX, other.toX) == 0
                    && Double.compare(toY, other.toY) == 0
                    && Double.compare(toZ, other.toZ) == 0;
        }
    }

    private NaturalTreeTransparencyController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NaturalTreeTransparencyController::onClientTick);
    }

    /** Called by the section compiler mixin on render/compiler threads. */
    public static boolean isFaded(BlockPos position) {
        ClientLevel level = activeLevel;
        return level != null && position != null && fadedPositions.contains(position.asLong());
    }

    public static boolean isFadedMesh() {
        return FADED_MESH.get();
    }

    /** Re-routes the same vanilla model call through the translucent mesh path. */
    public static void withFadedMesh(Runnable render) {
        boolean previous = FADED_MESH.get();
        FADED_MESH.set(true);
        try {
            render.run();
        } finally {
            FADED_MESH.set(previous);
        }
    }

    /** Applies the alpha multiplier to the compiler's existing quad instance. */
    public static com.mojang.blaze3d.vertex.QuadInstance fadeQuad(
            com.mojang.blaze3d.vertex.QuadInstance instance) {
        if (isFadedMesh() && instance != null) {
            instance.multiplyColor(FADE_COLOR);
        }
        return instance;
    }

    /** Restores normal meshes and drops the client-side world reference on logout. */
    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        clearFaded(minecraft);
        observedLevel = null;
        resetScanSchedule();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != observedLevel) {
            clearFaded(minecraft);
            observedLevel = minecraft.level;
            resetScanSchedule();
        }

        boolean closeZoomNow = IsometricCameraController.cameraDistance() <= CLOSE_ZOOM_DISTANCE;
        if (!(minecraft.level instanceof ClientLevel level)
                || minecraft.player == null
                || !RtsMode.isActive(minecraft.player)
                || !closeZoomNow) {
            clearFaded(minecraft);
            resetScanSchedule();
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 from = camera.position();
        Vec3 to = minecraft.player.position().add(0.0D,
                Math.max(0.5D, minecraft.player.getBbHeight() * 0.5D), 0.0D);
        boolean enteredCloseZoom = enteringCloseZoom(closeZoom, closeZoomNow);
        closeZoom = true;
        scanCooldownTicks = tickScanCooldown(scanCooldownTicks);
        ScanQuery query = ScanQuery.of(level, from, to);
        if (enteredCloseZoom || pendingQuery != null
                || lastQuery == null || !lastQuery.samePosition(query)
                || scanCooldownTicks == 0) {
            pendingQuery = coalesceQuery(pendingQuery, query);
        }
        if (!shouldRunCorridorScan(true, enteredCloseZoom, pendingQuery != null, scanCooldownTicks)) {
            return;
        }

        ScanQuery requested = pendingQuery;
        Set<Long> next = findFadedPositions(level,
                new Vec3(requested.fromX(), requested.fromY(), requested.fromZ()),
                new Vec3(requested.toX(), requested.toY(), requested.toZ()));
        lastQuery = requested;
        pendingQuery = null;
        scanCooldownTicks = CORRIDOR_SCAN_INTERVAL_TICKS;
        if (next.equals(fadedPositions) && activeLevel == level) {
            return;
        }

        Set<Long> dirty = changedFadedPositions(fadedPositions, next);
        activeLevel = level;
        fadedPositions = Set.copyOf(next);
        markDirty(minecraft.levelRenderer, dirty);
    }

    static boolean enteringCloseZoom(boolean wasCloseZoom, boolean isCloseZoom) {
        return !wasCloseZoom && isCloseZoom;
    }

    static boolean leavingCloseZoom(boolean wasCloseZoom, boolean isCloseZoom) {
        return wasCloseZoom && !isCloseZoom;
    }

    static int tickScanCooldown(int cooldownTicks) {
        return Math.max(0, cooldownTicks - 1);
    }

    static boolean shouldRunCorridorScan(boolean isCloseZoom, boolean enteredCloseZoom,
                                         boolean hasPendingQuery, int cooldownTicks) {
        return isCloseZoom && hasPendingQuery && (enteredCloseZoom || cooldownTicks <= 0);
    }

    /** Keeps only the newest camera/player endpoints while an expensive scan is cooling down. */
    static ScanQuery coalesceQuery(ScanQuery pending, ScanQuery latest) {
        return latest == null ? pending : latest;
    }

    /** Returns only blocks whose fade membership changed between two controller snapshots. */
    static Set<Long> changedFadedPositions(Set<Long> previous, Set<Long> next) {
        Set<Long> changed = new HashSet<>();
        if (previous != null) {
            for (Long position : previous) {
                if (position != null) {
                    changed.add(position);
                }
            }
        }
        if (next != null) {
            for (Long position : next) {
                if (position == null) {
                    continue;
                }
                if (!changed.add(position)) {
                    changed.remove(position);
                }
            }
        }
        return changed.isEmpty() ? Set.of() : Set.copyOf(changed);
    }

    private static Set<Long> findFadedPositions(ClientLevel level, Vec3 from, Vec3 to) {
        NaturalTreeClassifier.Scanner trees = new NaturalTreeClassifier.Scanner(
                level, NaturalTreeClient.protection());
        Set<NaturalTreeClassifier.Component> components = new LinkedHashSet<>();
        Set<BlockPos> logCandidates = new LinkedHashSet<>();
        Set<BlockPos> otherTreeCandidates = new LinkedHashSet<>();
        Vec3 travel = to.subtract(from);
        double length = travel.length();
        int samples = Math.max(1, Math.min(MAX_SAMPLES, (int) Math.ceil(length / SAMPLE_STEP)));
        double radiusSquared = CORRIDOR_RADIUS * CORRIDOR_RADIUS;

        for (int sample = 0; sample <= samples; sample++) {
            double fraction = sample / (double) samples;
            Vec3 center = from.add(travel.scale(fraction));
            int centerX = BlockPos.containing(center).getX();
            int centerY = BlockPos.containing(center).getY();
            int centerZ = BlockPos.containing(center).getZ();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos candidate = new BlockPos(centerX + dx, centerY + dy, centerZ + dz);
                        Vec3 candidateCenter = Vec3.atCenterOf(candidate);
                        if (distanceToSegmentSquared(candidateCenter, from, to) > radiusSquared) {
                            continue;
                        }
                        BlockState state = level.getBlockState(candidate);
                        if (NaturalTreeClassifier.isLog(state)) {
                            logCandidates.add(candidate);
                        } else if (NaturalTreeClassifier.isTreePart(state)) {
                            otherTreeCandidates.add(candidate);
                        }
                    }
                }
            }
        }

        // Resolve trunks first. Successful components cache every complete part, which makes the
        // later canopy pass a constant-time membership lookup instead of repeating a 25x41x25
        // search for every leaf block in the same tree.
        for (BlockPos candidate : logCandidates) {
            NaturalTreeClassifier.Component component = trees.componentAt(candidate);
            if (component != null) {
                components.add(component);
            }
        }
        for (BlockPos candidate : otherTreeCandidates) {
            NaturalTreeClassifier.Component component = trees.componentAt(candidate);
            if (component != null) {
                components.add(component);
            }
        }
        return collectCompleteComponentParts(components);
    }

    /**
     * Adds recognized components without ever committing a partial tree when the aggregate
     * render safety budget is exhausted. The optional overlap handling keeps the budget about
     * unique faded blocks if two bounded canopy envelopes touch.
     */
    static Set<Long> collectCompleteComponentParts(
            Iterable<NaturalTreeClassifier.Component> components) {
        return collectCompleteComponentParts(components, MAX_AGGREGATE_FADED_POSITIONS);
    }

    static Set<Long> collectCompleteComponentParts(
            Iterable<NaturalTreeClassifier.Component> components, int aggregateLimit) {
        if (aggregateLimit < 0 || components == null) {
            return Set.of();
        }

        Set<Long> result = new HashSet<>();
        for (NaturalTreeClassifier.Component component : components) {
            if (component == null || component.parts().isEmpty()) {
                continue;
            }

            int remaining = aggregateLimit - result.size();
            int newParts = 0;
            boolean fits = true;
            for (long part : component.parts()) {
                if (!result.contains(part) && ++newParts > remaining) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                result.addAll(component.parts());
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    private static double distanceToSegmentSquared(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared <= 1.0E-7D) {
            return point.distanceToSqr(start);
        }
        double fraction = point.subtract(start).dot(segment) / lengthSquared;
        fraction = Math.max(0.0D, Math.min(1.0D, fraction));
        return point.distanceToSqr(start.add(segment.scale(fraction)));
    }

    private static void clearFaded(Minecraft minecraft) {
        if (fadedPositions.isEmpty() && activeLevel == null) {
            return;
        }
        Set<Long> previous = fadedPositions;
        activeLevel = null;
        fadedPositions = Set.of();
        markDirty(minecraft.levelRenderer, changedFadedPositions(previous, Set.of()));
    }

    private static void resetScanSchedule() {
        closeZoom = false;
        lastQuery = null;
        pendingQuery = null;
        scanCooldownTicks = 0;
    }

    private static void markDirty(LevelRenderer renderer, Set<Long> positions) {
        if (renderer == null || positions == null || positions.isEmpty()) {
            return;
        }
        Set<Long> sections = new HashSet<>();
        for (long packed : positions) {
            sections.add(SectionPos.asLong(BlockPos.of(packed)));
        }
        for (long section : sections) {
            renderer.setSectionDirtyWithNeighbors(
                    SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        }
    }
}
