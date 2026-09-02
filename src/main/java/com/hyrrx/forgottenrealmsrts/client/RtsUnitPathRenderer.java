package com.hyrrx.forgottenrealmsrts.client;

import java.util.Map;

import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Draws compact tactical breadcrumbs from selected units to their last issued destination. */
public final class RtsUnitPathRenderer {
    /** Fewer, larger markers stay readable at RTS zoom without flooding the render buffer. */
    private static final double WAYPOINT_SPACING = 2.4D;
    private static final int MAX_WAYPOINTS = 64;
    private static final double ARRIVAL_DISTANCE_SQUARED = 4.0D;

    private RtsUnitPathRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsUnitPathRenderer::onRenderLevel);
    }

    private static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            RtsUnitPathState.clear();
            return;
        }

        Map<Integer, RtsUnitPathState.Path> paths = RtsUnitPathState.snapshot();
        if (paths.isEmpty()) {
            return;
        }

        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer builder = buffers.getBuffer(RenderTypes.debugFilledBox());
        PoseStack.Pose pose = event.getPoseStack().last();
        long now = net.minecraft.util.Util.getMillis();

        for (Map.Entry<Integer, RtsUnitPathState.Path> entry : paths.entrySet()) {
            Entity entity = minecraft.level.getEntity(entry.getKey());
            RtsUnitPathState.Path path = entry.getValue();
            if (!(entity instanceof LivingEntity unit) || !unit.isAlive()
                    || !RtsEntities.isAlliedUnit(unit)
                    || now >= path.visibleUntil()
                    || unit.distanceToSqr(path.target().getX() + 0.5D, path.target().getY(),
                            path.target().getZ() + 0.5D) <= ARRIVAL_DISTANCE_SQUARED) {
                RtsUnitPathState.remove(entry.getKey());
                continue;
            }
            drawPath(builder, pose, camera, unit, path.target());
        }

        buffers.endBatch(RenderTypes.debugFilledBox());
    }

    private static void drawPath(VertexConsumer builder, PoseStack.Pose pose, Vec3 camera,
                                 LivingEntity unit, net.minecraft.core.BlockPos target) {
        double startX = unit.getX();
        double startY = unit.getY() + 0.24D;
        double startZ = unit.getZ();
        double endX = target.getX() + 0.5D;
        double endY = target.getY() + 0.26D;
        double endZ = target.getZ() + 0.5D;
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(2, Math.min(MAX_WAYPOINTS,
                (int) Math.ceil(length / WAYPOINT_SPACING)));
        int accent = RtsUnitOverlayColors.accent(unit) & 0x00FFFFFF;
        int shadow = (0xD8 << 24) | 0x0010141B;
        int color = (0xF0 << 24) | accent;

        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            float x = (float) (startX + dx * progress - camera.x - 0.22D);
            float y = (float) (startY + dy * progress - camera.y - 0.03D);
            float z = (float) (startZ + dz * progress - camera.z - 0.22D);
            // The dark keyline separates a role-coloured route from grass, stone, and snow.
            prism(builder, pose, x, y, z, 0.44F, 0.11F, 0.44F, shadow);
            prism(builder, pose, x + 0.06F, y + 0.03F, z + 0.06F,
                    0.32F, 0.12F, 0.32F, color);
        }

        float targetX = (float) (endX - camera.x - 0.38D);
        float targetY = (float) (endY - camera.y - 0.02D);
        float targetZ = (float) (endZ - camera.z - 0.38D);
        prism(builder, pose, targetX, targetY, targetZ, 0.76F, 0.18F, 0.76F, shadow);
        prism(builder, pose, targetX + 0.10F, targetY + 0.04F, targetZ + 0.10F,
                0.56F, 0.20F, 0.56F, color);
    }

    private static void prism(VertexConsumer builder, PoseStack.Pose pose,
                              float x, float y, float z, float width, float height,
                              float depth, int argb) {
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
