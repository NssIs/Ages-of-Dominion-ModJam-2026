package com.hyrrx.forgottenrealmsrts.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * The "building_action_burst" effect, emitter "debris".
 * thrown outward, falls
 *
 * <p>Size, alpha and colour are baked curves over normalised lifetime, sampled each tick, so
 * nothing is interpreted at runtime.
 */
public class DebrisParticle extends SingleQuadParticle {

    private static final float[] SIZE  = { 1.0000F, 0.9600F, 0.9200F, 0.8800F, 0.8400F, 0.8000F, 0.7600F, 0.7200F, 0.6800F, 0.6400F, 0.6000F, 0.5600F, 0.5200F, 0.4800F, 0.4400F, 0.4000F };
    private static final float[] ALPHA = { 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 0.8889F, 0.6667F, 0.4444F, 0.2222F, 0.0000F };
    private static final float[] RED   = { 0.9020F, 0.8549F, 0.8078F, 0.7608F, 0.7098F, 0.6627F, 0.6157F, 0.5686F, 0.5216F, 0.4745F, 0.4275F, 0.3804F, 0.3294F, 0.2824F, 0.2353F, 0.1882F };
    private static final float[] GREEN = { 0.5686F, 0.5451F, 0.5176F, 0.4941F, 0.4667F, 0.4431F, 0.4157F, 0.3922F, 0.3647F, 0.3412F, 0.3137F, 0.2902F, 0.2627F, 0.2392F, 0.2118F, 0.1882F };
    private static final float[] BLUE  = { 0.1569F, 0.1608F, 0.1647F, 0.1647F, 0.1686F, 0.1725F, 0.1765F, 0.1804F, 0.1804F, 0.1843F, 0.1882F, 0.1922F, 0.1961F, 0.1961F, 0.2000F, 0.2039F };

    private static final float BASE_SIZE = 0.1000F;

    private final SpriteSet sprites;
    /** Radians per tick about the view axis. {@code roll} is the only rotation a quad has. */
    private float rollSpeed;

    DebrisParticle(ClientLevel level, double x, double y, double z,
            double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 10 + level.getRandom().nextInt(16);
        // Particle.tick() does `yd -= 0.04 * gravity`, so the field is the per-tick acceleration
        // divided by 0.04, not the acceleration itself.
        this.gravity = 0.2000F;
        this.friction = 0.8800F;
        this.hasPhysics = false;
        this.roll = level.getRandom().nextFloat() * ((float) Math.PI * 2.0F);
        this.rollSpeed = 0.0F;
        this.setSpriteFromAge(sprites);
        this.apply(0.0F);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        // `Particle.tick()` does not touch roll, so the previous-frame value has to be carried
        // here or the renderer interpolates against a stale angle and the sprite judders.
        this.oRoll = this.roll;
        this.roll += this.rollSpeed;
        super.tick();
        if (this.age < this.lifetime) {
            this.setSpriteFromAge(this.sprites);
            this.apply((float) this.age / (float) this.lifetime);
        }
    }

    /** Sample the baked curves at normalised lifetime {@code t}. */
    private void apply(float t) {
        float f = Math.max(0.0F, Math.min(1.0F, t)) * (SIZE.length - 1);
        int i = (int) f;
        int j = Math.min(i + 1, SIZE.length - 1);
        float k = f - i;

        this.quadSize = BASE_SIZE * lerp(SIZE[i], SIZE[j], k);
        this.setAlpha(lerp(ALPHA[i], ALPHA[j], k));
        this.setColor(lerp(RED[i], RED[j], k), lerp(GREEN[i], GREEN[j], k),
                lerp(BLUE[i], BLUE[j], k));
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    /** Registered client-side with {@code RegisterParticleProvidersEvent#registerSpriteSet}. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        @Nullable
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double xd, double yd, double zd,
                RandomSource random) {
            return new DebrisParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
