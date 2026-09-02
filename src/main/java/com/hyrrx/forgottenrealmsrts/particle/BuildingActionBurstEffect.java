package com.hyrrx.forgottenrealmsrts.particle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Plays the "building_action_burst" effect.
 * 
 *
 * <p>Call this <b>server side</b>. {@code sendParticles} is what makes the effect visible to
 * every player nearby; {@code level.addParticle} on the server does nothing at all, which is the
 * usual reason a new effect appears to be broken.
 *
 * <p>A count of 0 in {@code sendParticles} is deliberate: it makes the xd/yd/zd arguments mean
 * "velocity" rather than "spread", which is how the launch direction survives the trip to the
 * client.
 */
public final class BuildingActionBurstEffect {

    public static void play(ServerLevel level, double x, double y, double z) {
        RandomSource rng = level.getRandom();

        // core: point, 8 particles. the flash
        for (int i = 0; i < 8; i++) {
            double sx = (rng.nextDouble() * 2.0 - 1.0) * 1.0E-3;
            double sy = (rng.nextDouble() * 2.0 - 1.0) * 1.0E-3;
            double sz = (rng.nextDouble() * 2.0 - 1.0) * 1.0E-3;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-4) {
                len = 1.0;
            }
            double speed = 0.0500 * (1.0 + (rng.nextDouble() * 2.0 - 1.0) * 0.300);
            level.sendParticles(BuildingActionBurstParticles.CORE.get(),
                    x + sx, y + sy, z + sz,
                    0,
                    sx / len * speed + 0.0000 * speed,
                    sy / len * speed + 0.0000 * speed,
                    sz / len * speed + 0.0000 * speed,
                    1.0);
        }

        // debris: sphere, 24 particles. thrown outward, falls
        for (int i = 0; i < 24; i++) {
            double sx = (rng.nextDouble() * 2.0 - 1.0) * 0.1000;
            double sy = (rng.nextDouble() * 2.0 - 1.0) * 0.1000;
            double sz = (rng.nextDouble() * 2.0 - 1.0) * 0.1000;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-4) {
                len = 1.0;
            }
            double speed = 0.1500 * (1.0 + (rng.nextDouble() * 2.0 - 1.0) * 0.600);
            level.sendParticles(BuildingActionBurstParticles.DEBRIS.get(),
                    x + sx, y + sy, z + sz,
                    0,
                    sx / len * speed + 0.0000 * speed,
                    sy / len * speed + 0.0000 * speed,
                    sz / len * speed + 0.0000 * speed,
                    1.0);
        }

        // construction_ring: ring, 10 particles. expanding construction ring around an edited building
        for (int i = 0; i < 10; i++) {
            double sx = (rng.nextDouble() * 2.0 - 1.0) * 0.8000;
            double sy = 0.0;
            double sz = (rng.nextDouble() * 2.0 - 1.0) * 0.8000;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-4) {
                len = 1.0;
            }
            double speed = 0.0600 * (1.0 + (rng.nextDouble() * 2.0 - 1.0) * 0.020);
            level.sendParticles(BuildingActionBurstParticles.CONSTRUCTION_RING.get(),
                    x + sx, y + sy, z + sz,
                    0,
                    sx / len * speed + 0.0000 * speed,
                    sy / len * speed + 1.0000 * speed,
                    sz / len * speed + 0.0000 * speed,
                    1.0);
        }
    }

    private BuildingActionBurstEffect() {
    }
}
