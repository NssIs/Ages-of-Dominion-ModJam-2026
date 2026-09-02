package com.hyrrx.forgottenrealmsrts.client.build;

import com.hyrrx.forgottenrealmsrts.client.ui.RtsHudState;
import com.hyrrx.forgottenrealmsrts.network.PlacedBuildingInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.phys.Vec3;

/** Pulsing footprint and corner posts for the server-confirmed world selection. */
public final class BuildingSelectionRenderer {
    private BuildingSelectionRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BuildingSelectionRenderer::onRenderLevel);
    }

    private static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        if (selected == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        BlockPos origin = selected.origin();
        float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() * 0.006D);
        int fill = ((int) (0x18 + pulse * 0x20) << 24) | 0xF3C04D;
        int edge = ((int) (0x7A + pulse * 0x45) << 24) | 0xFFD76A;

        float x = (float) (origin.getX() - camera.x) - 0.04F;
        float y = (float) (origin.getY() - camera.y) - 0.04F;
        float z = (float) (origin.getZ() - camera.z) - 0.04F;
        float width = selected.sizeX() + 0.08F;
        float height = selected.sizeY() + 0.08F;
        float depth = selected.sizeZ() + 0.08F;

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer builder = buffers.getBuffer(RenderTypes.debugFilledBox());
        PoseStack.Pose pose = event.getPoseStack().last();
        prism(builder, pose, x, y, z, width, height, depth, fill);

        float bar = 0.075F;
        prism(builder, pose, x, y, z, width, bar, bar, edge);
        prism(builder, pose, x, y, z + depth - bar, width, bar, bar, edge);
        prism(builder, pose, x, y, z, bar, bar, depth, edge);
        prism(builder, pose, x + width - bar, y, z, bar, bar, depth, edge);
        prism(builder, pose, x, y, z, bar, height, bar, edge);
        prism(builder, pose, x + width - bar, y, z, bar, height, bar, edge);
        prism(builder, pose, x, y, z + depth - bar, bar, height, bar, edge);
        prism(builder, pose, x + width - bar, y, z + depth - bar, bar, height, bar, edge);
        prism(builder, pose, x, y + height - bar, z, width, bar, bar, edge);
        prism(builder, pose, x, y + height - bar, z + depth - bar, width, bar, bar, edge);
        prism(builder, pose, x, y + height - bar, z, bar, bar, depth, edge);
        prism(builder, pose, x + width - bar, y + height - bar, z, bar, bar, depth, edge);
        buffers.endBatch(RenderTypes.debugFilledBox());
    }

    static void prism(VertexConsumer builder, PoseStack.Pose pose,
                      float x, float y, float z, float width, float height, float depth, int argb) {
        float x1 = x + width;
        float y1 = y + height;
        float z1 = z + depth;
        quad(builder, pose, argb, x, y, z, x, y, z1, x1, y, z1, x1, y, z);
        quad(builder, pose, argb, x, y1, z, x1, y1, z, x1, y1, z1, x, y1, z1);
        quad(builder, pose, argb, x, y, z, x1, y, z, x1, y1, z, x, y1, z);
        quad(builder, pose, argb, x1, y, z1, x, y, z1, x, y1, z1, x1, y1, z1);
        quad(builder, pose, argb, x, y, z1, x, y, z, x, y1, z, x, y1, z1);
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
