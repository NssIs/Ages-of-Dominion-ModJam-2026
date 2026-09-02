package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Gives RTS units the vanilla glowing-outline treatment, with a role-specific tactical colour. */
public final class RtsUnitThroughWallOutline {
    private RtsUnitThroughWallOutline() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsUnitThroughWallOutline::onExtractLevelRenderState);
    }

    /** Add a role colour only when every sampled part of the unit is hidden behind a block. */
    private static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        LevelRenderState renderState = event.getRenderState();
        for (EntityRenderState entityState : renderState.entityRenderStates) {
            // Mine workers own a hidden-particle invisibility effect while inside the structure. Do
            // not turn that hidden worker back into a spectator-style outline through the wall.
            if (entityState.isInvisible) {
                continue;
            }
            int roleColour = RtsUnitOverlayColors.outlineColor(entityState.entityType);
            if (roleColour != 0 && isOccluded(minecraft, entityState)) {
                entityState.outlineColor = roleColour;
                renderState.haveGlowingEntities = true;
            }
        }
    }

    /**
     * A centre-only ray made a tall villager glow when just its feet were hidden. Check feet, torso,
     * and head; a unit is outlined only when all three are behind the same occluding geometry.
     */
    private static boolean isOccluded(Minecraft minecraft, EntityRenderState entityState) {
        if (minecraft.level == null) {
            return false;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        double height = Math.max(0.5D, entityState.boundingBoxHeight);
        double halfWidth = Math.max(0.15D, entityState.boundingBoxWidth * 0.35D);
        Vec3[] samples = {
                new Vec3(entityState.x, entityState.y + height * 0.12D, entityState.z),
                new Vec3(entityState.x, entityState.y + height * 0.52D, entityState.z),
                new Vec3(entityState.x, entityState.y + height * 0.90D, entityState.z),
                new Vec3(entityState.x - halfWidth, entityState.y + height * 0.52D, entityState.z),
                new Vec3(entityState.x + halfWidth, entityState.y + height * 0.52D, entityState.z)
        };
        for (Vec3 sample : samples) {
            if (hasClearRay(minecraft, camera, sample)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasClearRay(Minecraft minecraft, Vec3 from, Vec3 to) {
        BlockHitResult hit = minecraft.level.clip(new ClipContext(
                from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() != HitResult.Type.BLOCK
                || hit.getLocation().distanceToSqr(from) + 0.0001D >= to.distanceToSqr(from);
    }
}
