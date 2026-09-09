package com.hyrrx.forgottenrealmsrts.mixin;

import com.hyrrx.forgottenrealmsrts.client.NaturalTreeTransparencyController;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Sends the same tree models through the translucent section layer when close-zoom fading is on. */
@Mixin(SectionCompiler.class)
public final class SectionCompilerMixin {
    private static final String COMPILE_METHOD =
            "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;"
                    + "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;"
                    + "Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

    @Redirect(
            method = COMPILE_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"))
    private void forgottenRealmsRts$doNotOccludeFadedTree(
            VisGraph graph, BlockPos position) {
        if (!NaturalTreeTransparencyController.isFaded(position)) {
            graph.setOpaque(position);
        }
    }

    @Redirect(
            method = COMPILE_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock("
                            + "Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;"
                            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
    private void forgottenRealmsRts$compileTreeThroughTranslucentLayer(
            ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
            BlockAndTintGetter region, BlockPos position, BlockState state,
            BlockStateModel model, long seed) {
        if (!NaturalTreeTransparencyController.isFaded(position)) {
            renderer.tesselateBlock(output, x, y, z, region, position, state, model, seed);
            return;
        }
        NaturalTreeTransparencyController.withFadedMesh(() -> renderer.tesselateBlock(
                output, x, y, z, region, position, state, model, seed));
    }

    @ModifyArg(
            method = "lambda$compile$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;getOrBeginLayer("
                            + "Ljava/util/Map;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;"
                            + "Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lcom/mojang/blaze3d/vertex/BufferBuilder;"),
            index = 2)
    private ChunkSectionLayer forgottenRealmsRts$useTranslucentMaterialLayer(
            ChunkSectionLayer layer) {
        return NaturalTreeTransparencyController.isFadedMesh()
                ? ChunkSectionLayer.TRANSLUCENT : layer;
    }

    @ModifyArg(
            method = "lambda$compile$1",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;getOrBeginLayer("
                            + "Ljava/util/Map;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;"
                            + "Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lcom/mojang/blaze3d/vertex/BufferBuilder;"),
            index = 2)
    private ChunkSectionLayer forgottenRealmsRts$useTranslucentOpaqueLayer(
            ChunkSectionLayer layer) {
        return NaturalTreeTransparencyController.isFadedMesh()
                ? ChunkSectionLayer.TRANSLUCENT : layer;
    }

    @ModifyArg(
            method = "lambda$compile$0",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;putBlockBakedQuad("
                            + "FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;"
                            + "Lcom/mojang/blaze3d/vertex/QuadInstance;)V"),
            index = 4)
    private QuadInstance forgottenRealmsRts$fadeQuadColor(QuadInstance instance) {
        return NaturalTreeTransparencyController.fadeQuad(instance);
    }

    @ModifyArg(
            method = "lambda$compile$1",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;putBlockBakedQuad("
                            + "FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;"
                            + "Lcom/mojang/blaze3d/vertex/QuadInstance;)V"),
            index = 4)
    private QuadInstance forgottenRealmsRts$fadeOpaqueQuadColor(QuadInstance instance) {
        return NaturalTreeTransparencyController.fadeQuad(instance);
    }
}
