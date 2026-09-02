package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.client.particle.CoreParticle;
import com.hyrrx.forgottenrealmsrts.client.particle.DebrisParticle;
import com.hyrrx.forgottenrealmsrts.client.particle.ConstructionRingParticle;
import com.hyrrx.forgottenrealmsrts.particle.BuildingActionBurstParticles;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Client-only: this maps each particle type to the class that draws it.
 *
 * <p>Without this, the types register fine and nothing ever appears.
 *
 * <p>Note there is no {@code bus = ...} element: it was removed from
 * {@code @EventBusSubscriber} in FML loader 11, and the bus is inferred from the event type.
 * Every example written for an earlier version still passes it, and it will not compile.
 */
@EventBusSubscriber(modid = "forgotten_realms_rts", value = Dist.CLIENT)
public class BuildingActionBurstParticleClient {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BuildingActionBurstParticles.CORE.get(),
                CoreParticle.Provider::new);
        event.registerSpriteSet(BuildingActionBurstParticles.DEBRIS.get(),
                DebrisParticle.Provider::new);
        event.registerSpriteSet(BuildingActionBurstParticles.CONSTRUCTION_RING.get(),
                ConstructionRingParticle.Provider::new);
    }

    private BuildingActionBurstParticleClient() {
    }
}
