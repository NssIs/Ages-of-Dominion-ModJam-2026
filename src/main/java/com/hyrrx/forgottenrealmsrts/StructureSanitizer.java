package com.hyrrx.forgottenrealmsrts;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

/** Removes editor/test-only blocks from RTS structures while preserving deliberate decoration. */
public final class StructureSanitizer {
    private StructureSanitizer() {
    }

    /**
     * The allowlist is intentionally explicit. Barriers, light blocks, and decorative purple blocks
     * are authored structure content and must survive; only technical editor/test markers become air.
     */
    public static boolean isTechnicalMarker(BlockState state) {
        return state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.JIGSAW)
                || state.is(Blocks.STRUCTURE_VOID)
                || state.is(Blocks.COMMAND_BLOCK)
                || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK)
                || state.is(Blocks.TEST_BLOCK)
                || state.is(Blocks.TEST_INSTANCE_BLOCK);
    }

    /**
     * Scrubs technical markers from an already placed template. {@code origin} is the normalized
     * rotated-footprint corner used by the RTS store, rather than the raw structure transform point.
     */
    public static int sanitizePlacedStructure(ServerLevel level, StructureTemplate template,
                                              BlockPos origin, Rotation rotation) {
        if (template.palettes.isEmpty()) {
            return 0;
        }

        Set<Long> candidates = new HashSet<>();
        for (var palette : template.palettes) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (!isTechnicalMarker(info.state())) {
                    continue;
                }
                BlockPos rotated = BuildingPlacement.rotateOffset(info.pos().getX(), info.pos().getY(),
                        info.pos().getZ(), template.getSize(), rotation);
                candidates.add(origin.offset(rotated.getX(), rotated.getY(), rotated.getZ()).asLong());
            }
        }

        int removed = 0;
        for (long packed : candidates) {
            BlockPos world = BlockPos.of(packed);
            if (isTechnicalMarker(level.getBlockState(world))) {
                level.setBlock(world, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                removed++;
            }
        }
        return removed;
    }
}
