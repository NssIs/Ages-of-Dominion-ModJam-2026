package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Shared durability rules for tracked structures and the old SavedData migration. */
public final class RtsBuildingDurability {
    private static final int MIN_HEALTH = 40;
    private static final int MAX_HEALTH = 1_000;

    private RtsBuildingDurability() {
    }

    /** Reconciles entries from the pre-durability store without reviving a genuinely damaged entry. */
    public static void migrate(ServerLevel level) {
        RtsBuildingStore store = RtsBuildingStore.get(level);
        for (RtsBuildingStore.Entry entry : store.entries()) {
            if (entry.maxHealth() > 0) {
                if (entry.health() > entry.maxHealth()) {
                    store.update(withHealth(entry, entry.maxHealth(), entry.maxHealth()));
                }
                continue;
            }
            int maxHealth = maxHealth(level, entry.structure(), solidBlockCount(level, entry.structure()));
            store.update(withHealth(entry, maxHealth, maxHealth));
        }
    }

    /** Computes the non-Town-Hall formula from the authored base cost and real template blocks. */
    public static int maxHealth(ServerLevel level, Identifier structure) {
        return maxHealth(level, structure, solidBlockCount(level, structure));
    }

    /** Computes durability with an explicit physical block count for a linear 1×1×1 entry. */
    public static int maxHealth(ServerLevel level, Identifier structure, int solidBlockCount) {
        BuildingCosts.Definition definition = BuildingCosts.get(structure);
        int levelNumber = Math.max(1, ModPayloads.levelOf(structure));
        if (definition.townHall()) {
            return RtsBattle.maxIntegrityForLevel(levelNumber);
        }

        long baseCost = 0L;
        for (int cost : definition.costs()) {
            baseCost += Math.max(0, cost);
        }
        long value = 40L + 2L * baseCost + 4L * Math.max(0, solidBlockCount)
                + 50L * Math.max(0, levelNumber - 1);
        return (int) Math.max(MIN_HEALTH, Math.min(MAX_HEALTH, value));
    }

    /** Counts actual non-air, non-editor blocks in the first authored palette. */
    public static int solidBlockCount(ServerLevel level, Identifier structure) {
        if (BuildingPlacement.isLinearStructure(structure)) {
            return 1;
        }
        Optional<StructureTemplate> found = RtsStructureTemplates.get(level, structure);
        if (found.isEmpty() || found.get().palettes.isEmpty()) {
            return 1;
        }
        int count = 0;
        for (StructureTemplate.StructureBlockInfo info : found.get().palettes.get(0).blocks()) {
            if (!info.state().isAir() && !StructureSanitizer.isTechnicalMarker(info.state())) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    public static Vec3i rotatedSize(ServerLevel level, RtsBuildingStore.Entry entry) {
        if (BuildingPlacement.isLinearStructure(entry.structure())) {
            return new Vec3i(1, 1, 1);
        }
        Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
        return found.isEmpty() ? new Vec3i(1, 1, 1)
                : BuildingPlacement.rotateSize(found.get().getSize(), entry.rotation());
    }

    /** Returns the normalized origin used by all current structure footprints. */
    public static BlockPos normalizedOrigin(ServerLevel level, RtsBuildingStore.Entry entry) {
        if (entry.normalizedOrigin() || BuildingPlacement.isLinearStructure(entry.structure())) {
            return entry.origin();
        }
        Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
        if (found.isEmpty()) {
            return entry.origin();
        }
        BlockPos offset = found.get().getZeroPositionWithTransform(BlockPos.ZERO, Mirror.NONE,
                entry.rotation());
        return entry.origin().offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    public static boolean contains(ServerLevel level, RtsBuildingStore.Entry entry, BlockPos pos) {
        BlockPos origin = normalizedOrigin(level, entry);
        Vec3i size = rotatedSize(level, entry);
        return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + size.getX()
                && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + size.getY()
                && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + size.getZ();
    }

    public static RtsBuildingStore.Entry withHealth(RtsBuildingStore.Entry entry,
                                                      int health, int maxHealth) {
        return new RtsBuildingStore.Entry(entry.id(), entry.owner(), entry.structure(),
                entry.origin(), entry.rotation(), entry.normalizedOrigin(), health, maxHealth);
    }

    public static RtsBuildingStore.Entry withFullHealth(ServerLevel level,
                                                         RtsBuildingStore.Entry entry) {
        int maxHealth = maxHealth(level, entry.structure(), solidBlockCount(level, entry.structure()));
        return withHealth(entry, maxHealth, maxHealth);
    }

    public static List<BlockPos> trackedBlocks(ServerLevel level, RtsBuildingStore.Entry entry) {
        BlockPos origin = normalizedOrigin(level, entry);
        if (BuildingPlacement.isLinearStructure(entry.structure())) {
            return List.of(origin);
        }
        Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
        if (found.isEmpty() || found.get().palettes.isEmpty()) {
            return List.of(origin);
        }
        StructureTemplate template = found.get();
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.palettes.get(0).blocks()) {
            if (info.state().isAir() || StructureSanitizer.isTechnicalMarker(info.state())) {
                continue;
            }
            BlockPos rotated = BuildingPlacement.rotateOffset(info.pos().getX(), info.pos().getY(),
                    info.pos().getZ(), template.getSize(), entry.rotation());
            positions.add(origin.offset(rotated.getX(), rotated.getY(), rotated.getZ()));
        }
        return List.copyOf(positions);
    }
}
