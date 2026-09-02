package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.client.model.RtsUnitModel;
import com.hyrrx.forgottenrealmsrts.client.state.RtsUnitRenderState;
import com.hyrrx.forgottenrealmsrts.entity.SamuraiZombieEntity;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

public final class SamuraiZombieRenderer extends HumanoidMobRenderer<SamuraiZombieEntity, RtsUnitRenderState, RtsUnitModel> {
   private static final Identifier[] TEXTURES = new Identifier[]{RtsMobTextures.texture("samurai_zombie")};

   public SamuraiZombieRenderer(Context context) {
      super(context, new RtsUnitModel(context.bakeLayer(RtsUnitLayers.SAMURAI_ZOMBIE), RtsUnitModel.Profile.UNDEAD), 0.5F);
   }

   public RtsUnitRenderState createRenderState() {
      return new RtsUnitRenderState();
   }

   public Identifier getTextureLocation(RtsUnitRenderState state) {
      return RtsMobTextures.select(TEXTURES, state.variant);
   }

   public static void register(IEventBus modEventBus) {
      modEventBus.addListener(SamuraiZombieRenderer::onRegisterRenderers);
      modEventBus.addListener(SamuraiZombieRenderer::onRegisterLayers);
   }

   private static void onRegisterRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)RtsEntities.SAMURAI_ZOMBIE.get(), SamuraiZombieRenderer::new);
   }

   private static void onRegisterLayers(RegisterLayerDefinitions event) {
      event.registerLayerDefinition(RtsUnitLayers.SAMURAI_ZOMBIE, RtsUnitLayers::createSamuraiLayer);
   }
}
