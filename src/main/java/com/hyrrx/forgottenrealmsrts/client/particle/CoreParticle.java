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
 * The "building_action_burst" effect, emitter "core".
 * the flash
 *
 * <p>Size, alpha and colour are baked curves over normalised lifetime, sampled each tick, so
 * nothing is interpreted at runtime.
 */
public class CoreParticle extends SingleQuadParticle {

    private static final float[] SIZE  = { 0.3000F, 0.6667F, 1.0333F, 1.4000F, 1.2833F, 1.1667F, 1.0500F, 0.9333F, 0.8167F, 0.7000F, 0.5833F, 0.4667F, 0.3500F, 0.2333F, 0.1167F, 0.0000F };
    private static final float[] ALPHA = { 1.0000F, 0.9733F, 0.9467F, 0.9200F, 0.8933F, 0.8667F, 0.8400F, 0.8133F, 0.7467F, 0.6400F, 0.5333F, 0.4267F, 0.3200F, 0.2133F, 0.1067F, 0.0000F };
    private static final float[] RED   = { 0.9608F, 0.9490F, 0.9333F, 0.9216F, 0.9098F, 0.8980F, 0.8863F, 0.8745F, 0.8627F, 0.8510F, 0.8392F, 0.8275F, 0.8196F, 0.8078F, 0.7961F, 0.7843F };
    private static final float[] GREEN = { 0.9608F, 0.8745F, 0.7882F, 0.6980F, 0.6118F, 0.5490F, 0.5098F, 0.4706F, 0.4314F, 0.3922F, 0.3529F, 0.3137F, 0.2745F, 0.2353F, 0.1961F, 0.1569F };
    private static final float[] BLUE  = { 0.9608F, 0.7804F, 0.6039F, 0.4235F, 0.2471F, 0.1569F, 0.1569F, 0.1569F, 0.1569F, 0.1569F, 0.1569F, 0.1569F, 0.1569F, 0.1569F, 0.1569F, 0.1569F };

    private static final float BASE_SIZE = 0.5000F;

    private final SpriteSet sprites;
    /** Radians per tick about the view axis. {@code roll} is the only rotation a quad has. */
    private float rollSpeed;

    CoreParticle(ClientLevel level, double x, double y, double z,
            double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 4 + level.getRandom().nextInt(5);
        // Particle.tick() does `yd -= 0.04 * gravity`, so the field is the per-tick acceleration
        // divided by 0.04, not the acceleration itself.
        this.gravity = 0.0000F;
        this.friction = 0.8000F;
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
            return new CoreParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
