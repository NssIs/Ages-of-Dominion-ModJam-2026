package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.client.model.RtsUnitModel;
import com.hyrrx.forgottenrealmsrts.client.state.RtsUnitRenderState;
import com.hyrrx.forgottenrealmsrts.entity.RtsArcherEntity;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

public final class RtsArcherRenderer extends HumanoidMobRenderer<RtsArcherEntity, RtsUnitRenderState, RtsUnitModel> {
   private static final Identifier[] TEXTURES = new Identifier[]{
      RtsMobTextures.texture("archer"),
      RtsMobTextures.texture("archer_crimson"),
      RtsMobTextures.texture("archer_green")
   };

   public RtsArcherRenderer(Context context) {
      super(context, new RtsUnitModel(context.bakeLayer(RtsUnitLayers.ARCHER), RtsUnitModel.Profile.ARCHER), 0.5F);
   }

   public RtsUnitRenderState createRenderState() {
      return new RtsUnitRenderState();
   }

   public void extractRenderState(RtsArcherEntity entity, RtsUnitRenderState state, float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.variant = entity.getVariant();
   }

   public Identifier getTextureLocation(RtsUnitRenderState state) {
      return RtsMobTextures.select(TEXTURES, state.variant);
   }

   public static void register(IEventBus modEventBus) {
      modEventBus.addListener(RtsArcherRenderer::onRegisterRenderers);
      modEventBus.addListener(RtsArcherRenderer::onRegisterLayers);
   }

   private static void onRegisterRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)RtsEntities.RTS_ARCHER.get(), RtsArcherRenderer::new);
   }

   private static void onRegisterLayers(RegisterLayerDefinitions event) {
      event.registerLayerDefinition(RtsUnitLayers.ARCHER, RtsUnitLayers::createArcherLayer);
   }
}
