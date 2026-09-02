package com.hyrrx.forgottenrealmsrts.client.build;

import com.hyrrx.forgottenrealmsrts.network.BuildingActionPayload;
import com.hyrrx.forgottenrealmsrts.network.BuildingEffectPayload;
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

/** Draws a short expanding construction frame alongside the custom particle burst. */
public final class BuildingActionEffectRenderer {
    private BuildingActionEffectRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BuildingActionEffectRenderer::onRenderLevel);
    }

    private static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        long now = Util.getMillis();
        var effects = BuildingActionEffects.active(now);
        if (effects.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer builder = buffers.getBuffer(RenderTypes.debugFilledBox());
        PoseStack.Pose pose = event.getPoseStack().last();

        for (BuildingActionEffects.ActiveEffect active : effects) {
            BuildingEffectPayload effect = active.payload();
            float progress = Math.min(1.0F,
                    (now - active.startedAt()) / (float) BuildingActionEffects.durationMillis());
            float fade = 1.0F - progress;
            float spread = 0.25F + progress * 1.35F;
            int color = colorFor(effect.action());
            int argb = ((int) (fade * 0xB0) << 24) | color;
            BlockPos origin = effect.origin();
            float x = (float) (origin.getX() - camera.x) - spread;
            float y = (float) (origin.getY() - camera.y) + effect.sizeY() * progress * 0.45F;
            float z = (float) (origin.getZ() - camera.z) - spread;
            float width = effect.sizeX() + spread * 2.0F;
            float depth = effect.sizeZ() + spread * 2.0F;
            float bar = 0.08F;
            BuildingSelectionRenderer.prism(builder, pose, x, y, z, width, bar, bar, argb);
            BuildingSelectionRenderer.prism(builder, pose, x, y, z + depth - bar, width, bar, bar, argb);
            BuildingSelectionRenderer.prism(builder, pose, x, y, z, bar, bar, depth, argb);
            BuildingSelectionRenderer.prism(builder, pose, x + width - bar, y, z, bar, bar, depth, argb);

            float beamHeight = effect.sizeY() * (0.2F + progress * 0.8F);
            float centreX = (float) (origin.getX() + effect.sizeX() * 0.5D - camera.x);
            float centreZ = (float) (origin.getZ() + effect.sizeZ() * 0.5D - camera.z);
            BuildingSelectionRenderer.prism(builder, pose, centreX - bar * 0.5F, y,
                    centreZ - bar * 0.5F, bar, beamHeight, bar, argb);
        }
        buffers.endBatch(RenderTypes.debugFilledBox());
    }

    private static int colorFor(BuildingActionPayload.Action action) {
        return switch (action) {
            case PLACE -> 0x68E0A0;
            case MOVE -> 0xF3C04D;
            case UPGRADE -> 0xC792FF;
            // Demolition reads as the destructive action in the same palette the other
            // three use: warm red, distinct from the amber of a move.
            case DEMOLISH -> 0xE0553F;
        };
    }
}
