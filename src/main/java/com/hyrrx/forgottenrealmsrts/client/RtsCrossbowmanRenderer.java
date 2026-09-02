package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.client.model.RtsUnitModel;
import com.hyrrx.forgottenrealmsrts.client.state.RtsUnitRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.entity.RtsCrossbowmanEntity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

/** Client renderer for the blueprint's trainable crossbow specialist. */
public final class RtsCrossbowmanRenderer extends HumanoidMobRenderer<RtsCrossbowmanEntity, RtsUnitRenderState, RtsUnitModel> {
   private static final Identifier[] TEXTURES = new Identifier[]{
      RtsMobTextures.texture("crossbowman_blue"),
      RtsMobTextures.texture("crossbowman_crimson"),
      RtsMobTextures.texture("crossbowman_green")
   };

   public RtsCrossbowmanRenderer(Context context) {
      super(context, new RtsUnitModel(context.bakeLayer(RtsUnitLayers.CROSSBOWMAN), RtsUnitModel.Profile.CROSSBOW), 0.5F);
   }

   public RtsUnitRenderState createRenderState() {
      return new RtsUnitRenderState();
   }

   public void extractRenderState(RtsCrossbowmanEntity entity, RtsUnitRenderState state, float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.variant = entity.getVariant();
   }

   public Identifier getTextureLocation(RtsUnitRenderState state) {
      return RtsMobTextures.select(TEXTURES, state.variant);
   }

   public static void register(IEventBus modEventBus) {
      modEventBus.addListener(RtsCrossbowmanRenderer::onRegisterRenderers);
      modEventBus.addListener(RtsCrossbowmanRenderer::onRegisterLayers);
   }

   private static void onRegisterRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)RtsEntities.RTS_CROSSBOWMAN.get(), RtsCrossbowmanRenderer::new);
   }

   private static void onRegisterLayers(RegisterLayerDefinitions event) {
      event.registerLayerDefinition(RtsUnitLayers.CROSSBOWMAN, RtsUnitLayers::createCrossbowmanLayer);
   }
}
