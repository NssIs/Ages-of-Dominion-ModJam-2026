package com.hyrrx.forgottenrealmsrts.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** One shared world-to-GUI projection for health markers and drag selection. */
public final class RtsUnitScreenProjection {
    private RtsUnitScreenProjection() {
    }

    public static ScreenPoint project(Minecraft minecraft, Vec3 world) {
        if (minecraft == null || minecraft.level == null || world == null) {
            return null;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
        Vec3 cameraPosition = camera.position();
        Vector4f clip = viewProjection.transform(new Vector4f(
                (float) (world.x - cameraPosition.x),
                (float) (world.y - cameraPosition.y),
                (float) (world.z - cameraPosition.z),
                1.0F));
        if (clip.w() <= 0.001F) {
            return null;
        }

        float ndcX = clip.x() / clip.w();
        float ndcY = clip.y() / clip.w();
        if (ndcX < -1.15F || ndcX > 1.15F || ndcY < -1.15F || ndcY > 1.15F) {
            return null;
        }
        return new ScreenPoint(
                Math.round((ndcX + 1.0F) * 0.5F * screenWidth),
                Math.round((1.0F - ndcY) * 0.5F * screenHeight));
    }

    public record ScreenPoint(int x, int y) {
    }
}
