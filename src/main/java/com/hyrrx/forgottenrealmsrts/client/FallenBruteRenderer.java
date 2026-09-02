package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.client.model.RtsUnitModel;
import com.hyrrx.forgottenrealmsrts.client.state.RtsUnitRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.entity.FallenBruteEntity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

/** Client renderer for the blueprint's heavy later-wave Cairn Brute echo. */
public final class FallenBruteRenderer extends HumanoidMobRenderer<FallenBruteEntity, RtsUnitRenderState, RtsUnitModel> {
   private static final Identifier[] TEXTURES = new Identifier[]{RtsMobTextures.texture("fallen_brute")};

   public FallenBruteRenderer(Context context) {
      super(context, new RtsUnitModel(context.bakeLayer(RtsUnitLayers.FALLEN_BRUTE), RtsUnitModel.Profile.BRUTE), 0.7F);
   }

   public RtsUnitRenderState createRenderState() {
      return new RtsUnitRenderState();
   }

   public void extractRenderState(FallenBruteEntity entity, RtsUnitRenderState state, float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.variant = RtsMobTextures.stableVariant(entity, TEXTURES.length);
   }

   public Identifier getTextureLocation(RtsUnitRenderState state) {
      return RtsMobTextures.select(TEXTURES, state.variant);
   }

   public static void register(IEventBus modEventBus) {
      modEventBus.addListener(FallenBruteRenderer::onRegisterRenderers);
      modEventBus.addListener(FallenBruteRenderer::onRegisterLayers);
   }

   private static void onRegisterRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)RtsEntities.FALLEN_BRUTE.get(), FallenBruteRenderer::new);
   }

   private static void onRegisterLayers(RegisterLayerDefinitions event) {
      event.registerLayerDefinition(RtsUnitLayers.FALLEN_BRUTE, RtsUnitLayers::createFallenBruteLayer);
   }
}
