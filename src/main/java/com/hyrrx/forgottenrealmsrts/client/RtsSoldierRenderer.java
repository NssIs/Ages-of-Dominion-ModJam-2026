package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.client.model.RtsUnitModel;
import com.hyrrx.forgottenrealmsrts.client.state.RtsUnitRenderState;
import com.hyrrx.forgottenrealmsrts.entity.RtsSoldierEntity;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

public final class RtsSoldierRenderer extends HumanoidMobRenderer<RtsSoldierEntity, RtsUnitRenderState, RtsUnitModel> {
   private static final Identifier[] TEXTURES = new Identifier[]{
      RtsMobTextures.texture("soldier_manatarms_blue"),
      RtsMobTextures.texture("soldier_manatarms_crimson"),
      RtsMobTextures.texture("soldier_manatarms_green"),
      RtsMobTextures.texture("soldier_knight"),
      RtsMobTextures.texture("soldier_ochre")
   };

   public RtsSoldierRenderer(Context context) {
      super(context, new RtsUnitModel(context.bakeLayer(RtsUnitLayers.SOLDIER), RtsUnitModel.Profile.SOLDIER), 0.5F);
   }

   public RtsUnitRenderState createRenderState() {
      return new RtsUnitRenderState();
   }

   public void extractRenderState(RtsSoldierEntity entity, RtsUnitRenderState state, float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.variant = entity.getVariant();
   }

   public Identifier getTextureLocation(RtsUnitRenderState state) {
      return RtsMobTextures.select(TEXTURES, state.variant);
   }

   public static void register(IEventBus modEventBus) {
      modEventBus.addListener(RtsSoldierRenderer::onRegisterRenderers);
      modEventBus.addListener(RtsSoldierRenderer::onRegisterLayers);
   }

   private static void onRegisterRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)RtsEntities.RTS_SOLDIER.get(), RtsSoldierRenderer::new);
   }

   private static void onRegisterLayers(RegisterLayerDefinitions event) {
      event.registerLayerDefinition(RtsUnitLayers.SOLDIER, RtsUnitLayers::createSoldierLayer);
   }
}
