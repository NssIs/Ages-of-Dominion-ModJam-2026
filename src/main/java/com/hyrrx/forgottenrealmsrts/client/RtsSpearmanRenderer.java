package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.client.model.RtsUnitModel;
import com.hyrrx.forgottenrealmsrts.client.state.RtsUnitRenderState;
import com.hyrrx.forgottenrealmsrts.entity.RtsSpearmanEntity;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

public final class RtsSpearmanRenderer extends HumanoidMobRenderer<RtsSpearmanEntity, RtsUnitRenderState, RtsUnitModel> {
   private static final Identifier[] TEXTURES = new Identifier[]{
      RtsMobTextures.texture("spearman"),
      RtsMobTextures.texture("spearman_crimson"),
      RtsMobTextures.texture("spearman_green")
   };

   public RtsSpearmanRenderer(Context context) {
      super(context, new RtsUnitModel(context.bakeLayer(RtsUnitLayers.SPEARMAN), RtsUnitModel.Profile.SPEAR), 0.5F);
   }

   public RtsUnitRenderState createRenderState() {
      return new RtsUnitRenderState();
   }

   public void extractRenderState(RtsSpearmanEntity entity, RtsUnitRenderState state, float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.variant = entity.getVariant();
   }

   public Identifier getTextureLocation(RtsUnitRenderState state) {
      return RtsMobTextures.select(TEXTURES, state.variant);
   }

   public static void register(IEventBus modEventBus) {
      modEventBus.addListener(RtsSpearmanRenderer::onRegisterRenderers);
      modEventBus.addListener(RtsSpearmanRenderer::onRegisterLayers);
   }

   private static void onRegisterRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)RtsEntities.RTS_SPEARMAN.get(), RtsSpearmanRenderer::new);
   }

   private static void onRegisterLayers(RegisterLayerDefinitions event) {
      event.registerLayerDefinition(RtsUnitLayers.SPEARMAN, RtsUnitLayers::createSpearmanLayer);
   }
}
