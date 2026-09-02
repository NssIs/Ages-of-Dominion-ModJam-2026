package com.hyrrx.forgottenrealmsrts.particle;

import java.util.function.Supplier;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Particle types for the "building_action_burst" effect.
 *
 * <p>Register this on the mod event bus: {@code BuildingActionBurstParticles.PARTICLE_TYPES.register(modEventBus);}
 */
public class BuildingActionBurstParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, "forgotten_realms_rts");

    /** the flash */
    public static final Supplier<SimpleParticleType> CORE =
            PARTICLE_TYPES.register("building_action_burst_core",
                    () -> new SimpleParticleType(false));

    /** thrown outward, falls */
    public static final Supplier<SimpleParticleType> DEBRIS =
            PARTICLE_TYPES.register("building_action_burst_debris",
                    () -> new SimpleParticleType(false));

    /** expanding construction ring around an edited building */
    public static final Supplier<SimpleParticleType> CONSTRUCTION_RING =
            PARTICLE_TYPES.register("building_action_burst_construction_ring",
                    () -> new SimpleParticleType(false));

    private BuildingActionBurstParticles() {
    }
}
