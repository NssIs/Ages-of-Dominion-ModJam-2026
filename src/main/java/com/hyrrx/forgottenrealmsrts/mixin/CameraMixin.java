package com.hyrrx.forgottenrealmsrts.mixin;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsSidePanelHud;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops the third-person camera being dragged towards the player by terrain while RTS mode is on.
 *
 * <p>{@code Camera#getMaxZoom} raycasts from the eye to the requested distance and returns the
 * first hit, so the camera never ends up inside a wall. At a fixed 60-degree downward pitch the
 * camera sits high above the player, and a single leaf block overhead collapses the view onto their
 * head.
 *
 * <p>This has to be a mixin. The bytecode order inside {@code Camera#update} is:
 * {@code setPosition} → fire {@code ViewportEvent.ComputeCameraAngles} → … →
 * {@code getMaxZoom} → {@code move}. The event fires <em>before</em> the collision, so repositioning
 * the camera from it is simply overwritten — that was the first attempt at this bug and it did not
 * work. {@code CalculateDetachedCameraDistanceEvent} is no better: it supplies the distance that
 * {@code getMaxZoom} then clamps.
 *
 * <p>Clipping through terrain is the right trade for an overhead strategy camera, and the override
 * is gated on {@link RtsMode} so ordinary third-person play is untouched.
 */
@Mixin(Camera.class)
public class CameraMixin {
    /**
     * Keeps the RTS playfield centred in the unobscured part of the window. Minecraft's camera
     * projection is still full-window, while the civilization column covers the right edge; without
     * this small camera-space correction the Town Hall sits behind the column whenever the view is
     * centred on it. The offset is applied after third-person placement and before the culling
     * frustum is prepared, so rendering and the free-cursor ray agree about what is under the mouse.
     */
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void forgottenRealmsRts$makeRoomForSidePanel(float partialTicks, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !RtsMode.isActive(player)) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int panelWidth = RtsSidePanelHud.columnWidth(screenWidth);
        if (screenWidth <= 0 || panelWidth <= 0 || panelWidth >= screenWidth) {
            return;
        }

        Camera camera = (Camera) (Object) this;
        Vec3 forward = new Vec3(camera.forwardVector());
        Vec3 target = player.position().add(0.0D, player.getEyeHeight(), 0.0D);
        double targetDepth = target.subtract(camera.position()).dot(forward);
        if (targetDepth <= 1.0D) {
            return;
        }

        double aspect = (double) screenWidth
                / Math.max(1, minecraft.getWindow().getGuiScaledHeight());
        double halfHorizontalFov = Math.atan(
                Math.tan(Math.toRadians(camera.getFov()) * 0.5D) * aspect);
        double rightwardShift = (panelWidth / (double) screenWidth)
                * targetDepth * Math.tan(halfHorizontalFov);
        Vec3 right = new Vec3(camera.leftVector()).scale(-rightwardShift);
        camera.setPosition(camera.position().add(right));
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void forgottenRealmsRts$ignoreTerrainWhileInRtsMode(
            float requestedDistance, CallbackInfoReturnable<Float> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && RtsMode.isActive(minecraft.player)) {
            callback.setReturnValue(requestedDistance);
        }
    }
}
