package com.hyrrx.forgottenrealmsrts.client.camera;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class IsometricCameraController {
    private static final float CAMERA_PITCH = 60.0F;
    private static final float MIN_CAMERA_DISTANCE = 18.0F;
    private static final float MAX_CAMERA_DISTANCE = 86.0F;
    private static final float DEFAULT_CAMERA_DISTANCE = 44.0F;
    private static final float HEALTH_BAR_MAX_CAMERA_DISTANCE = 70.0F;
    private static final double CAMERA_ANCHOR_GROUND_OFFSET = 2.0D;
    private static final double KEYBOARD_PAN_SPEED = 1.35D;
    private static final double DRAG_SPEED = 0.045D;
    private static final double SCROLL_STEP = 5.0D;
    private static final int TERRAIN_SCAN_RADIUS = 6;

    private static float cameraDistance = DEFAULT_CAMERA_DISTANCE;
    private static boolean dragging;
    private static double previousMouseX;
    private static double previousMouseY;

    private IsometricCameraController() {
    }

    /** Keeps unit health readable at tactical zooms without turning the map into a wall of labels. */
    public static boolean shouldShowHealthBars() {
        return cameraDistance <= HEALTH_BAR_MAX_CAMERA_DISTANCE;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(IsometricCameraController::onClientTick);
        NeoForge.EVENT_BUS.addListener(IsometricCameraController::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(IsometricCameraController::onCameraDistance);
        NeoForge.EVENT_BUS.addListener(IsometricCameraController::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(IsometricCameraController::onPlayerTurn);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.player.setXRot(CAMERA_PITCH);
        minecraft.player.xRotO = CAMERA_PITCH;

        updateKeyboardMovement(minecraft, minecraft.player);
        updateDragMovement(minecraft, minecraft.player);
        followTerrainHeight(minecraft.level, minecraft.player);
    }

    /**
     * Pans the camera while the left button is held past the hold threshold.
     *
     * <p><strong>The one thing this must not do is test where the cursor is.</strong> The original
     * version of this method dropped the drag whenever the pointer was over a HUD panel, so a pan
     * that swept across the screen died the moment it touched the top bar or the command bar and the
     * camera appeared to hang. Whether a press may become a pan is decided once, at press time, by
     * {@link RtsMouseController} — here we only ask whether it currently <em>is</em> one.
     */
    private static void updateDragMovement(Minecraft minecraft, LocalPlayer player) {
        if (!RtsMouseController.isPanning() || minecraft.screen != null) {
            dragging = false;
            return;
        }

        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        if (!dragging) {
            // First frame of the pan: anchor, do not jump by the distance travelled while the
            // player was still just holding the button down.
            dragging = true;
            previousMouseX = mouseX;
            previousMouseY = mouseY;
            return;
        }

        double deltaX = mouseX - previousMouseX;
        double deltaY = mouseY - previousMouseY;
        previousMouseX = mouseX;
        previousMouseY = mouseY;
        if (deltaX == 0.0D && deltaY == 0.0D) {
            return;
        }

        double yawRadians = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        Vec3 right = new Vec3(-Math.cos(yawRadians), 0.0D, -Math.sin(yawRadians));
        double zoomScale = cameraDistance / DEFAULT_CAMERA_DISTANCE;
        // Dragging pulls the world with the cursor, so the camera moves the opposite way.
        Vec3 movement = right.scale(-deltaX).add(forward.scale(deltaY)).scale(DRAG_SPEED * zoomScale);

        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(player.getX() + movement.x, player.getY(), player.getZ() + movement.z);
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }
        // Pitch only. The yaw override that used to sit here existed to hold the camera steady
        // during a left-drag pan; with drag panning gone, yaw is already held by onPlayerTurn
        // zeroing mouse sensitivity unless the right button is down.
        event.setPitch(CAMERA_PITCH);
    }

    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && RtsMode.isActive(minecraft.player)) {
            event.setDistance(cameraDistance);
        }
    }

    private static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        double scroll = event.getScrollDeltaY();

        // The wheel always zooms, including while a building is being sited. It used to turn the
        // ghost instead, and that was wrong: zoom is the one thing the player wants continuously
        // available, and taking it away exactly when they are looking for somewhere to build reads
        // as the game ignoring the wheel. Rotation is on the rebindable Q/E mappings.
        cameraDistance = Mth.clamp(
                (float)(cameraDistance - scroll * SCROLL_STEP),
                MIN_CAMERA_DISTANCE,
                MAX_CAMERA_DISTANCE
        );
        event.setCanceled(true);
    }

    private static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null && RtsMode.isActive(minecraft.player)
                && !RtsMouseController.rightButtonDown()) {
            event.setMouseSensitivity(0.0D);
        }
    }

    /**
     * Pans the camera with WASD and with the arrow keys.
     *
     * <p><strong>Left-drag panning used to live alongside this and has been removed.</strong> It
     * cancelled itself the moment the cursor crossed any HUD panel — a deliberate guard so a click
     * on a button did not also drag the world — but the effect while actually playing was that a
     * pan across the screen died as soon as it touched the top bar, the bottom bar or the map
     * column, which reads as the camera hanging. Keyboard panning has no such conflict, so that is
     * the whole of camera movement now.
     *
     * <p>The arrow keys are read straight from GLFW rather than through a {@code KeyMapping},
     * because vanilla binds them to nothing and a mapping would show up in the controls screen as a
     * rebindable duplicate of movement the player did not ask for.
     */
    private static void updateKeyboardMovement(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.screen != null) {
            return;
        }

        long window = minecraft.getWindow().handle();
        double forwardInput = 0.0D;
        double rightInput = 0.0D;
        if (minecraft.options.keyUp.isDown() || isKeyDown(window, GLFW.GLFW_KEY_UP)) {
            forwardInput += 1.0D;
        }
        if (minecraft.options.keyDown.isDown() || isKeyDown(window, GLFW.GLFW_KEY_DOWN)) {
            forwardInput -= 1.0D;
        }
        if (minecraft.options.keyRight.isDown() || isKeyDown(window, GLFW.GLFW_KEY_RIGHT)) {
            rightInput += 1.0D;
        }
        if (minecraft.options.keyLeft.isDown() || isKeyDown(window, GLFW.GLFW_KEY_LEFT)) {
            rightInput -= 1.0D;
        }

        if (forwardInput == 0.0D && rightInput == 0.0D) {
            return;
        }

        double length = Math.sqrt(forwardInput * forwardInput + rightInput * rightInput);
        forwardInput /= length;
        rightInput /= length;

        double yawRadians = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        Vec3 right = new Vec3(-Math.cos(yawRadians), 0.0D, -Math.sin(yawRadians));
        double zoomScale = cameraDistance / DEFAULT_CAMERA_DISTANCE;
        Vec3 movement = forward.scale(forwardInput).add(right.scale(rightInput)).scale(KEYBOARD_PAN_SPEED * zoomScale);

        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(player.getX() + movement.x, player.getY(), player.getZ() + movement.z);
    }

    private static boolean isKeyDown(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static void followTerrainHeight(Level level, LocalPlayer player) {
        int terrainY = findTerrainY(level, Mth.floor(player.getX()), Mth.floor(player.getZ()), Mth.floor(player.getY()));
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(player.getX(), terrainY + CAMERA_ANCHOR_GROUND_OFFSET, player.getZ());
    }

    private static int findTerrainY(Level level, int centerX, int centerZ, int currentY) {
        int scanTop = Math.min(level.getMaxY(), Math.max(currentY + 24, level.getSeaLevel() + 96));
        int scanBottom = level.getMinY();

        for (int y = scanTop; y >= scanBottom; y--) {
            for (int radius = 0; radius <= TERRAIN_SCAN_RADIUS; radius++) {
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                        if (Math.abs(x - centerX) != radius && Math.abs(z - centerZ) != radius) {
                            continue;
                        }

                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (isTerrain(state)) {
                            return y + 1;
                        }
                    }
                }
            }
        }

        return level.getSeaLevel();
    }

    private static boolean isTerrain(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(BlockTags.BADLANDS_TERRACOTTA)
                || state.is(BlockTags.ICE)
                || state.is(BlockTags.SNOW)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.getFluidState().is(FluidTags.WATER);
    }
}
