package com.hyrrx.forgottenrealmsrts.client.build;

import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.BuildingPlacement;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Free-cursor world ray used by both building placement and world selection. */
public final class BuildingRaycast {
    private static final double PICK_RANGE = 256.0D;

    private BuildingRaycast() {
    }

    public static BlockHitResult pick(Minecraft minecraft) {
        Vec3[] ray = ray(minecraft);
        if (ray == null) {
            return null;
        }
        BlockHitResult hit = minecraft.level.clip(new ClipContext(
                ray[0], ray[1], ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /**
     * The living entity under the free cursor, or null. Stops at the first solid block so an entity
     * cannot be selected through a wall — this is the world-side of unit selection.
     */
    public static net.minecraft.world.entity.LivingEntity pickEntity(Minecraft minecraft) {
        Vec3[] ray = ray(minecraft);
        if (ray == null) {
            return null;
        }
        Vec3 from = ray[0];
        Vec3 to = ray[1];
        BlockHitResult block = minecraft.level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        if (block.getType() == HitResult.Type.BLOCK) {
            to = block.getLocation();
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(from, to).inflate(0.5D);
        net.minecraft.world.phys.EntityHitResult hit =
                net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                        minecraft.player, from, to, box,
                        entity -> entity != minecraft.player && entity.isAlive()
                                && RtsEntities.isRtsUnit(entity),
                        from.distanceToSqr(to));
        return hit != null && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                ? living : null;
    }

    /** Camera-through-cursor ray as {@code {from, to}}, or null if the view is not ready. */
    private static Vec3[] ray(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Matrix4f inverse = camera.getViewRotationProjectionMatrix(new Matrix4f()).invert();

        double width = minecraft.getWindow().getScreenWidth();
        double height = minecraft.getWindow().getScreenHeight();
        if (width <= 0.0D || height <= 0.0D) {
            return null;
        }
        float ndcX = (float) (2.0D * minecraft.mouseHandler.xpos() / width - 1.0D);
        float ndcY = (float) (1.0D - 2.0D * minecraft.mouseHandler.ypos() / height);

        Vector3f near = inverse.transformProject(new Vector3f(ndcX, ndcY, -1.0F));
        Vector3f far = inverse.transformProject(new Vector3f(ndcX, ndcY, 1.0F));
        Vec3 direction = new Vec3(far.x - near.x, far.y - near.y, far.z - near.z).normalize();
        Vec3 from = camera.position().add(near.x, near.y, near.z);
        Vec3 to = from.add(direction.scale(PICK_RANGE));
        return new Vec3[]{from, to};
    }

    /**
     * Returns the first block position the building should occupy at the cursor. A top-face hit
     * starts above the support block; a side-face hit starts in the cell outside that face. Using
     * the hit block itself for every face made placement appear several blocks behind the cursor
     * when the free camera ray struck a terrain wall.
     */
    public static BlockPos pickPlacementBase(Minecraft minecraft) {
        return pickPlacementBase(minecraft, null);
    }

    /**
     * Path placement targets the grass cell itself so the dirt-path structure replaces the surface;
     * every other structure still starts in the cell above its support block.
     */
    public static BlockPos pickPlacementBase(Minecraft minecraft,
                                             net.minecraft.resources.Identifier structure) {
        BlockHitResult hit = pick(minecraft);
        if (hit == null) {
            return null;
        }
        Direction face = hit.getDirection();
        if (structure != null && BuildingPlacement.isPathStructure(structure)) {
            // A free RTS camera often meets the side of the grass block instead of its top face.
            // Resolve that hit back to the visible surface cell so a path never previews one block
            // out in the air and then reports a misleading "Blocked" result.
            BlockPos surface = findPathSurface(minecraft, hit.getBlockPos());
            if (surface != null) {
                return surface;
            }
        }
        return face == Direction.UP
                ? hit.getBlockPos().above()
                    : face.getAxis().isHorizontal()
                        ? hit.getBlockPos().relative(face)
                        : hit.getBlockPos();
    }

    private static BlockPos findPathSurface(Minecraft minecraft, BlockPos hit) {
        for (int down = 0; down <= 8; down++) {
            BlockPos candidate = hit.below(down);
            if (BuildingPlacement.isPathSurface(minecraft.level.getBlockState(candidate))
                    && minecraft.level.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return null;
    }
}
