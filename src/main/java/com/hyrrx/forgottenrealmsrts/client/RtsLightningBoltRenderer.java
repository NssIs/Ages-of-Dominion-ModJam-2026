package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsScriptedLightning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import org.joml.Matrix4fc;

/**
 * The normal vanilla lightning geometry with a gold-yellow presentation for RTS daylight purge.
 * The common side still spawns a real visual-only {@link LightningBolt}, so its flash, sound,
 * timing, and branching remain vanilla; this renderer changes only the vertex colour.
 */
public final class RtsLightningBoltRenderer extends LightningBoltRenderer {
    private static final float BOLT_RED = 1.0F;
    private static final float BOLT_GREEN = 0.84F;
    private static final float BOLT_BLUE = 0.18F;
    private static final float BOLT_ALPHA = 0.34F;

    /** The renderer state keeps the scripted-strike marker without tinting ordinary weather. */
    private static final class RtsLightningBoltRenderState extends LightningBoltRenderState {
        private boolean scriptedStrike;
    }

    public RtsLightningBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RtsLightningBoltRenderer::onRegisterRenderers);
    }

    private static void onRegisterRenderers(RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.LIGHTNING_BOLT, RtsLightningBoltRenderer::new);
    }

    @Override
    public void submit(LightningBoltRenderState state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        boolean scriptedStrike = state instanceof RtsLightningBoltRenderState rtsState
                && rtsState.scriptedStrike;
        if (!scriptedStrike) {
            super.submit(state, poseStack, submitNodeCollector, camera);
            return;
        }
        float boltRed = scriptedStrike ? BOLT_RED : 0.45F;
        float boltGreen = scriptedStrike ? BOLT_GREEN : 0.45F;
        float boltBlue = scriptedStrike ? BOLT_BLUE : 0.50F;
        float boltAlpha = scriptedStrike ? BOLT_ALPHA : 0.30F;
        float[] xOffs = new float[8];
        float[] zOffs = new float[8];
        float xOff = 0.0F;
        float zOff = 0.0F;
        RandomSource random = RandomSource.createThreadLocalInstance(state.seed);

        for (int height = 7; height >= 0; height--) {
            xOffs[height] = xOff;
            zOffs[height] = zOff;
            xOff += random.nextInt(11) - 5;
            zOff += random.nextInt(11) - 5;
        }

        float finalXOff = xOff;
        float finalZOff = zOff;
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
            Matrix4fc poseMatrix = pose.pose();
            for (int branch = 0; branch < 4; branch++) {
                RandomSource branchRandom = RandomSource.createThreadLocalInstance(state.seed);
                for (int part = 0; part < 3; part++) {
                    int high = part == 0 ? 7 : 7 - part;
                    int low = part == 0 ? 0 : high - 2;
                    float xo0 = xOffs[high] - finalXOff;
                    float zo0 = zOffs[high] - finalZOff;

                    for (int height = high; height >= low; height--) {
                        float xo1 = xo0;
                        float zo1 = zo0;
                        if (part == 0) {
                            xo0 += branchRandom.nextInt(11) - 5;
                            zo0 += branchRandom.nextInt(11) - 5;
                        } else {
                            xo0 += branchRandom.nextInt(31) - 15;
                            zo0 += branchRandom.nextInt(31) - 15;
                        }

                        float radius = 0.1F + branch * 0.2F;
                        float radiusTop = radius;
                        float radiusBottom = radius;
                        if (part == 0) {
                            radiusTop *= height * 0.1F + 1.0F;
                            radiusBottom *= (height - 1.0F) * 0.1F + 1.0F;
                        }

                        quad(poseMatrix, buffer, xo0, zo0, height, xo1, zo1,
                                radiusTop, radiusBottom, boltRed, boltGreen, boltBlue, boltAlpha,
                                false, false, true, false);
                        quad(poseMatrix, buffer, xo0, zo0, height, xo1, zo1,
                                radiusTop, radiusBottom, boltRed, boltGreen, boltBlue, boltAlpha,
                                true, false, true, true);
                        quad(poseMatrix, buffer, xo0, zo0, height, xo1, zo1,
                                radiusTop, radiusBottom, boltRed, boltGreen, boltBlue, boltAlpha,
                                true, true, false, true);
                        quad(poseMatrix, buffer, xo0, zo0, height, xo1, zo1,
                                radiusTop, radiusBottom, boltRed, boltGreen, boltBlue, boltAlpha,
                                false, true, false, false);
                    }
                }
            }
        });
    }

    private static void quad(Matrix4fc pose, VertexConsumer buffer,
                             float xo0, float zo0, int height,
                             float xo1, float zo1, float radiusTop, float radiusBottom,
                             float red, float green, float blue, float alpha,
                             boolean positiveX0, boolean positiveZ0,
                             boolean positiveX1, boolean positiveZ1) {
        buffer.addVertex(pose, xo0 + (positiveX0 ? radiusBottom : -radiusBottom), height * 16.0F,
                        zo0 + (positiveZ0 ? radiusBottom : -radiusBottom))
                .setColor(red, green, blue, alpha);
        buffer.addVertex(pose, xo1 + (positiveX0 ? radiusTop : -radiusTop), (height + 1) * 16.0F,
                        zo1 + (positiveZ0 ? radiusTop : -radiusTop))
                .setColor(red, green, blue, alpha);
        buffer.addVertex(pose, xo1 + (positiveX1 ? radiusTop : -radiusTop), (height + 1) * 16.0F,
                        zo1 + (positiveZ1 ? radiusTop : -radiusTop))
                .setColor(red, green, blue, alpha);
        buffer.addVertex(pose, xo0 + (positiveX1 ? radiusBottom : -radiusBottom), height * 16.0F,
                        zo0 + (positiveZ1 ? radiusBottom : -radiusBottom))
                .setColor(red, green, blue, alpha);
    }

    @Override
    public RtsLightningBoltRenderState createRenderState() {
        return new RtsLightningBoltRenderState();
    }

    @Override
    public void extractRenderState(LightningBolt entity, LightningBoltRenderState state,
                                   float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (state instanceof RtsLightningBoltRenderState rtsState) {
            rtsState.scriptedStrike = entity instanceof RtsScriptedLightning marker
                    && marker.forgottenRealmsRts$isScripted();
        }
    }
}
