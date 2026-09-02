package com.hyrrx.forgottenrealmsrts.client.build;

import com.hyrrx.forgottenrealmsrts.BuildingPlacement;
import com.hyrrx.forgottenrealmsrts.network.BuildingInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Draws the pending building in the world as a translucent massing model.
 *
 * <p><strong>Deliberately not real block models.</strong> Minecraft 26.1 deleted
 * {@code BlockRenderDispatcher}; rendering actual block models means the deferred submit-node
 * pipeline ({@code ModelBlockRenderer.tesselateBlock} + {@code BlockQuadOutput} + a fake
 * {@code BlockAndTintGetter}), the same spike this project has already declined for the tray icons.
 * A translucent box per block, coloured by that block's {@link net.minecraft.world.level.material.MapColor},
 * reads clearly as a blueprint, makes the invalid state a one-line colour swap, and costs nothing to
 * keep working across a NeoForge bump.
 *
 * <p>The whole ghost turns <strong>red</strong> when the site is bad — and only when the site is
 * bad. Being unable to afford it, or not having founded the town yet, is reported as text instead:
 * the building fits fine there, so colouring the geometry red for it would say the wrong thing.
 */
public final class BuildGhostRenderer {
    /** How much of the box's own colour survives; the rest is alpha. Low enough to see through a
     *  whole building, high enough to read its shape. */
    private static final int GHOST_ALPHA = 0x66;
    private static final int INVALID_COLOR = 0xFFD03A2A;
    /** Boxes are drawn slightly inside their block so adjacent faces do not z-fight into a solid
     *  block of colour. */
    private static final float INSET = 0.03F;
    /** Bright ground rails make a long path/wall footprint readable over grass and existing blocks. */
    private static final float LINE_OUTLINE_PADDING = 0.08F;
    private static final float LINE_OUTLINE_BAR = 0.11F;

    private BuildGhostRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BuildGhostRenderer::onRenderLevel);
    }

    private static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (!BuildGhost.isActive()) {
            return;
        }
        BuildingInfo building = BuildGhost.building();
        BuildingPreviewShape shape = building == null ? null : BuildingPreviewShape.of(building.id());
        if (shape == null && building != null) {
            shape = BuildingPreviewShape.fallback(building.id());
        }
        if (shape == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        Vec3i size = shape.size();
        boolean pathPreview = BuildingPlacement.isPathStructure(building.id());
        // Red only for a geometry failure — see the class doc.
        boolean invalid = BuildGhost.validity().geometric();

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer builder = bufferSource.getBuffer(RenderTypes.debugFilledBox());
        PoseStack.Pose pose = event.getPoseStack().last();

        if (BuildGhost.isLinearSession()) {
            int colour = invalid ? INVALID_COLOR
                    : shape.voxels().length == 0 ? 0xFF9E9E9E : shape.voxels()[0].argb();
            int argb = (GHOST_ALPHA << 24) | (colour & 0x00FFFFFF);
            int edge = invalid ? 0xE8F04A3A
                    : BuildingPlacement.isPathStructure(building.id())
                    ? 0xE8F1C24D : 0xE8D6A8FF;
            for (BlockPos origin : BuildGhost.previewOrigins()) {
                float x0 = (float) (origin.getX() - camera.x) + INSET;
                float y0 = (float) (origin.getY() - camera.y)
                        + (pathPreview ? 1.01F : INSET);
                float z0 = (float) (origin.getZ() - camera.z) + INSET;
                if (pathPreview) {
                    BuildingSelectionRenderer.prism(builder, pose, x0, y0, z0,
                            1.0F - 2.0F * INSET, 0.08F, 1.0F - 2.0F * INSET, argb);
                } else {
                    box(builder, pose, x0, y0, z0, 1.0F - 2.0F * INSET, argb);
                }
                outline(builder, pose,
                        (float) (origin.getX() - camera.x) - LINE_OUTLINE_PADDING,
                        (float) (origin.getY() - camera.y) + (pathPreview ? 1.01F : 0.02F),
                        (float) (origin.getZ() - camera.z) - LINE_OUTLINE_PADDING,
                        1.0F + LINE_OUTLINE_PADDING * 2.0F,
                        1.0F + LINE_OUTLINE_PADDING * 2.0F, edge);
            }
        } else {
            for (BlockPos origin : BuildGhost.previewOrigins()) {
                for (BuildingPreviewShape.Voxel voxel : shape.voxels()) {
                    BlockPos rotated = BuildingPlacement.rotateOffset(
                            voxel.x(), voxel.y(), voxel.z(), size, BuildGhost.rotation());
                    int colour = invalid ? INVALID_COLOR : voxel.argb();
                    int argb = (GHOST_ALPHA << 24) | (colour & 0x00FFFFFF);

                    float x0 = (float) (origin.getX() + rotated.getX() - camera.x) + INSET;
                    float y0 = (float) (origin.getY() + rotated.getY() - camera.y) + INSET;
                    float z0 = (float) (origin.getZ() + rotated.getZ() - camera.z) + INSET;
                    box(builder, pose, x0, y0, z0, 1.0F - 2.0F * INSET, argb);
                }
            }
        }

        bufferSource.endBatch(RenderTypes.debugFilledBox());
    }

    /** Four low rails around each preview tile/segment; the full span stays visible at RTS zoom. */
    private static void outline(VertexConsumer builder, PoseStack.Pose pose,
                                float x, float y, float z, float width, float depth, int argb) {
        float bar = Math.min(LINE_OUTLINE_BAR, Math.min(width, depth));
        BuildingSelectionRenderer.prism(builder, pose, x, y, z, width, bar, bar, argb);
        BuildingSelectionRenderer.prism(builder, pose, x, y, z + depth - bar,
                width, bar, bar, argb);
        BuildingSelectionRenderer.prism(builder, pose, x, y, z, bar, bar, depth, argb);
        BuildingSelectionRenderer.prism(builder, pose, x + width - bar, y, z,
                bar, bar, depth, argb);
    }

    /** Six quads, camera-relative, following the vertex order vanilla's own gizmo quads use. */
    private static void box(VertexConsumer builder, PoseStack.Pose pose,
                            float x, float y, float z, float edge, int argb) {
        float x1 = x + edge;
        float y1 = y + edge;
        float z1 = z + edge;

        // down
        quad(builder, pose, argb, x, y, z, x, y, z1, x1, y, z1, x1, y, z);
        // up
        quad(builder, pose, argb, x, y1, z, x1, y1, z, x1, y1, z1, x, y1, z1);
        // north
        quad(builder, pose, argb, x, y, z, x1, y, z, x1, y1, z, x, y1, z);
        // south
        quad(builder, pose, argb, x1, y, z1, x, y, z1, x, y1, z1, x1, y1, z1);
        // west
        quad(builder, pose, argb, x, y, z1, x, y, z, x, y1, z, x, y1, z1);
        // east
        quad(builder, pose, argb, x1, y, z, x1, y, z1, x1, y1, z1, x1, y1, z);
    }

    private static void quad(VertexConsumer builder, PoseStack.Pose pose, int argb,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        builder.addVertex(pose, ax, ay, az).setColor(argb);
        builder.addVertex(pose, bx, by, bz).setColor(argb);
        builder.addVertex(pose, cx, cy, cz).setColor(argb);
        builder.addVertex(pose, dx, dy, dz).setColor(argb);
    }
}
