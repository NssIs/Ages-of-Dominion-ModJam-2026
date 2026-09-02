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
 * The "building_action_burst" effect, emitter "construction_ring".
 * expanding construction ring around an edited building
 *
 * <p>Size, alpha and colour are baked curves over normalised lifetime, sampled each tick, so
 * nothing is interpreted at runtime.
 */
public class ConstructionRingParticle extends SingleQuadParticle {

    private static final float[] SIZE  = { 1.0000F, 0.9333F, 0.8667F, 0.8000F, 0.7333F, 0.6667F, 0.6000F, 0.5333F, 0.4667F, 0.4000F, 0.3333F, 0.2667F, 0.2000F, 0.1333F, 0.0667F, 0.0000F };
    private static final float[] ALPHA = { 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 1.0000F, 0.8889F, 0.6667F, 0.4444F, 0.2222F, 0.0000F };
    private static final float[] RED   = { 0.3137F, 0.3569F, 0.4000F, 0.4431F, 0.4863F, 0.5294F, 0.5725F, 0.6157F, 0.6588F, 0.7020F, 0.7451F, 0.7882F, 0.8314F, 0.8745F, 0.9176F, 0.9608F };
    private static final float[] GREEN = { 0.8235F, 0.8314F, 0.8431F, 0.8510F, 0.8588F, 0.8706F, 0.8784F, 0.8863F, 0.8980F, 0.9059F, 0.9137F, 0.9255F, 0.9333F, 0.9412F, 0.9529F, 0.9608F };
    private static final float[] BLUE  = { 0.8627F, 0.8706F, 0.8745F, 0.8824F, 0.8902F, 0.8941F, 0.9020F, 0.9098F, 0.9137F, 0.9216F, 0.9294F, 0.9333F, 0.9412F, 0.9490F, 0.9529F, 0.9608F };

    private static final float BASE_SIZE = 0.2200F;

    private final SpriteSet sprites;
    /** Radians per tick about the view axis. {@code roll} is the only rotation a quad has. */
    private float rollSpeed;

    ConstructionRingParticle(ClientLevel level, double x, double y, double z,
            double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 10 + level.getRandom().nextInt(7);
        // Particle.tick() does `yd -= 0.04 * gravity`, so the field is the per-tick acceleration
        // divided by 0.04, not the acceleration itself.
        this.gravity = 0.0000F;
        this.friction = 0.9200F;
        this.hasPhysics = false;
        this.roll = level.getRandom().nextFloat() * ((float) Math.PI * 2.0F);
        this.rollSpeed = 0.20944F * (1.0F - 1.000F * level.getRandom().nextFloat());
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
            return new ConstructionRingParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
