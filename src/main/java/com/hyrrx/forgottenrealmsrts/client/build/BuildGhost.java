package com.hyrrx.forgottenrealmsrts.client.build;

import com.hyrrx.forgottenrealmsrts.BuildingPlacement;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsBottomBarHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsHudState;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsSidePanelHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsTopBarHud;
import com.hyrrx.forgottenrealmsrts.network.BuildingActionPayload;
import com.hyrrx.forgottenrealmsrts.network.BuildingInfo;
import com.hyrrx.forgottenrealmsrts.network.PlaceBuildingPayload;
import com.hyrrx.forgottenrealmsrts.network.PlaceLinearBuildingPayload;
import com.hyrrx.forgottenrealmsrts.network.PlacedBuildingInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared translucent ghost session for placement, move, and upgrade actions. */
public final class BuildGhost {
    public enum Mode {
        PLACE,
        LINE_PLACE,
        MOVE,
        UPGRADE
    }

    private static BlockPos origin;
    private static Rotation rotation = Rotation.NONE;
    private static BuildingPlacement.Result validity = BuildingPlacement.Result.OK;
    private static boolean targeting;
    private static BuildingInfo building;
    private static PlacedBuildingInfo source;
    private static Mode mode;
    /** Anchor surface cell for a path rectangle or wall span. */
    private static BlockPos linearAnchor;
    /** Last surface cell under the cursor while a linear placement is active. */
    private static BlockPos linearCursor;
    private static BuildingPlacement.LinearLayout linearLayout =
            new BuildingPlacement.LinearLayout(Rotation.NONE, List.of(), 0, 0);
    private static List<BlockPos> previewOrigins = List.of();

    private BuildGhost() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BuildGhost::onClientTick);
    }

    /** The structure currently being previewed, or {@code null} when no action is pending. */
    public static BuildingInfo building() {
        return building;
    }

    /** True while any placement/move/upgrade session exists, even while its preview is loading. */
    public static boolean hasSession() {
        return building != null && mode != null;
    }

    public static boolean isActive() {
        return targeting && hasSession() && origin != null && !previewOrigins.isEmpty();
    }

    public static Mode mode() {
        return mode;
    }

    public static PlacedBuildingInfo source() {
        return source;
    }

    public static BlockPos origin() {
        return origin;
    }

    public static Rotation rotation() {
        return rotation;
    }

    public static BuildingPlacement.Result validity() {
        return validity;
    }

    public static boolean isLinearPlacement() {
        return mode == Mode.LINE_PLACE;
    }

    /** A selected path/wall is still a one-block object when it is being moved. */
    public static boolean isLinearMove() {
        return mode == Mode.MOVE && building != null
                && BuildingPlacement.isLinearStructure(building.id());
    }

    /** Whether the active ghost should use the one-block linear footprint. */
    public static boolean isLinearSession() {
        return isLinearPlacement() || isLinearMove();
    }

    public static boolean linearStarted() {
        return isLinearPlacement() && linearAnchor != null;
    }

    public static int linearPieceCount() {
        return isLinearPlacement() ? linearLayout.pieces() : 1;
    }

    /** Number of new path tiles in the live span; tiles already laid in the world cost nothing. */
    public static int linearChargeablePieceCount() {
        if (!isLinearPlacement() || building == null
                || !BuildingPlacement.isPathStructure(building.id())) {
            return linearPieceCount();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || previewOrigins.isEmpty()) {
            return linearPieceCount();
        }
        int chargeable = 0;
        for (BlockPos previewOrigin : previewOrigins) {
            if (!minecraft.level.getBlockState(previewOrigin).is(
                    net.minecraft.world.level.block.Blocks.DIRT_PATH)) {
                chargeable++;
            }
        }
        return chargeable;
    }

    public static int linearExistingPieceCount() {
        return Math.max(0, linearPieceCount() - linearChargeablePieceCount());
    }

    public static int linearColumns() {
        return isLinearPlacement() ? linearLayout.columns() : 1;
    }

    public static int linearRows() {
        return isLinearPlacement() ? linearLayout.rows() : 1;
    }

    /** Current surface cell under the cursor, exposed so the placement card can show the live target. */
    public static BlockPos linearCursor() {
        return linearCursor;
    }

    /** All tile/segment origins in the current preview, in the same order sent to the server. */
    public static List<BlockPos> previewOrigins() {
        return previewOrigins;
    }

    public static void beginPlace(BuildingInfo selected) {
        if (selected == null) {
            cancel();
            return;
        }
        building = selected;
        source = null;
        mode = BuildingPlacement.isLinearStructure(selected.id())
                ? Mode.LINE_PLACE : Mode.PLACE;
        RtsHudState.setSelectedBuilding(selected);
        RtsHudState.setSelectedPlacedBuilding(null);
        resetTarget();
    }

    public static void beginMove(PlacedBuildingInfo selected) {
        if (selected == null) {
            return;
        }
        building = infoFor(selected, selected.structure());
        source = selected;
        mode = Mode.MOVE;
        RtsHudState.setSelectedBuilding(null);
        RtsHudState.setSelectedPlacedBuilding(selected);
        origin = selected.origin();
        previewOrigins = List.of(origin);
        rotation = selected.rotation();
        targeting = false;
        validity = BuildingPlacement.Result.OK;
    }

    public static void beginUpgrade(PlacedBuildingInfo selected) {
        if (selected == null || selected.upgradeStructure() == null) {
            return;
        }
        building = infoFor(selected, selected.upgradeStructure());
        source = selected;
        mode = Mode.UPGRADE;
        RtsHudState.setSelectedBuilding(null);
        RtsHudState.setSelectedPlacedBuilding(selected);
        origin = selected.origin();
        previewOrigins = List.of(origin);
        rotation = selected.rotation();
        targeting = false;
        validity = BuildingPlacement.Result.OK;
    }

    public static void rotateLeft() {
        if (mode != Mode.UPGRADE && mode != Mode.LINE_PLACE) {
            rotation = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
        }
    }

    public static void rotateRight() {
        if (mode != Mode.UPGRADE && mode != Mode.LINE_PLACE) {
            rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
        }
    }

    /** Cancels the pending action. It never removes a placed building. */
    public static void cancel() {
        if (mode == Mode.PLACE || mode == Mode.LINE_PLACE) {
            RtsHudState.setSelectedBuilding(null);
        }
        building = null;
        source = null;
        mode = null;
        linearAnchor = null;
        linearCursor = null;
        linearLayout = new BuildingPlacement.LinearLayout(Rotation.NONE, List.of(), 0, 0);
        previewOrigins = List.of();
        resetTarget();
    }

    private static void resetTarget() {
        origin = null;
        targeting = false;
        rotation = Rotation.NONE;
        validity = BuildingPlacement.Result.OK;
        previewOrigins = List.of();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !RtsMode.isActive(minecraft.player)) {
            if (hasSession()) {
                cancel();
            }
            targeting = false;
            return;
        }
        if (minecraft.screen != null || !hasSession()) {
            targeting = false;
            return;
        }

        if (RtsMouseController.isPanning()) {
            targeting = false;
            return;
        }

        BuildingPreviewShape resolvedShape = BuildingPreviewShape.of(building.id());
        if (resolvedShape == null) {
            resolvedShape = BuildingPreviewShape.fallback(building.id());
        }
        if (resolvedShape == null) {
            // The server preview is shared by the tray and the ghost; wait for it once, rather than
            // inventing a client-side block list.
            targeting = false;
            return;
        }
        final BuildingPreviewShape shape = resolvedShape;

        BlockPos placementBase = null;
        if (mode == Mode.UPGRADE) {
            origin = source.origin();
            rotation = source.rotation();
            previewOrigins = List.of(origin);
        } else {
            placementBase = BuildingRaycast.pickPlacementBase(minecraft, building.id());
            if (placementBase == null) {
                targeting = false;
                return;
            }
            if (mode == Mode.LINE_PLACE) {
                linearCursor = placementBase;
                BlockPos anchor = linearAnchor == null ? placementBase : linearAnchor;
                linearLayout = BuildingPlacement.linearLayout(building.id(), shape.size(),
                        anchor, placementBase);
                rotation = linearLayout.rotation();
                previewOrigins = linearLayout.origins();
                if (previewOrigins.isEmpty()) {
                    targeting = false;
                    return;
                }
                origin = previewOrigins.get(0);
            } else if (isLinearMove()) {
                rotation = Rotation.NONE;
                origin = placementBase;
                previewOrigins = List.of(origin);
                linearLayout = new BuildingPlacement.LinearLayout(Rotation.NONE,
                        previewOrigins, 1, 1);
            } else {
                Vec3i rotatedSize = BuildingPlacement.rotateSize(shape.size(), rotation);
                origin = new BlockPos(
                        placementBase.getX() - rotatedSize.getX() / 2,
                        placementBase.getY(),
                        placementBase.getZ() - rotatedSize.getZ() / 2);
                previewOrigins = List.of(origin);
            }
        }
        targeting = true;

        Set<Long> ignored = source == null ? Set.of() : sourceBounds(source);
        if (isLinearSession()) {
            validity = checkLinearGeometry(minecraft, shape, linearLayout, ignored);
        } else {
            Vec3i rotatedSize = BuildingPlacement.rotateSize(shape.size(), rotation);
            validity = BuildingPlacement.checkGeometry(minecraft.level, origin, rotatedSize,
                    (x, y, z) -> shape.occupiedRotated(x, y, z, rotation), ignored);
        }

        if (RtsMouseController.consumeLeftClickReleased() && !overHud(minecraft)) {
            if (mode == Mode.LINE_PLACE && linearAnchor == null) {
                if (!validity.ok() || !RtsHudState.canBuild(building, linearChargeablePieceCount())) {
                    refusePlacement(minecraft);
                } else {
                    linearAnchor = placementBase;
                    linearCursor = placementBase;
                    minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                            "Anchor set — choose the far end"));
                }
            } else {
                confirm(minecraft);
            }
        }
    }

    private static BuildingPlacement.Result checkLinearGeometry(
            Minecraft minecraft, BuildingPreviewShape shape,
            BuildingPlacement.LinearLayout layout, Set<Long> ignored) {
        boolean path = BuildingPlacement.isPathStructure(building.id());
        return BuildingPlacement.checkLinearGeometry(minecraft.level, layout.origins(), path, ignored);
    }

    private static boolean overHud(Minecraft minecraft) {
        int mouseX = RtsMouseController.mouseX(minecraft);
        int mouseY = RtsMouseController.mouseY(minecraft);
        return RtsTopBarHud.isPointInside(mouseX, mouseY)
                || RtsBottomBarHud.isPointInside(mouseX, mouseY)
                || RtsSidePanelHud.isPointInside(mouseX, mouseY);
    }

    private static void confirm(Minecraft minecraft) {
        if (mode == Mode.LINE_PLACE) {
            if (!validity.ok()) {
                refusePlacement(minecraft);
                return;
            }
            if (!RtsHudState.canBuild(building, linearChargeablePieceCount())) {
                refusePlacement(minecraft);
                return;
            }
            ClientPacketDistributor.sendToServer(new PlaceLinearBuildingPayload(
                    building.id(), linearAnchor, linearCursor));
            cancel();
            return;
        }
        if (mode == Mode.PLACE) {
            if (!validity.ok() || !RtsHudState.canBuild(building)) {
                refusePlacement(minecraft);
                return;
            }
            ClientPacketDistributor.sendToServer(new PlaceBuildingPayload(
                    building.id(), origin, rotation));
            return;
        }

        if (!validity.ok()) {
            minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    validity.message()));
            return;
        }
        if (mode == Mode.UPGRADE && !RtsHudState.canAffordUpgrade(source)) {
            minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "message.forgotten_realms_rts.not_enough_resources"));
            return;
        }

        ClientPacketDistributor.sendToServer(new BuildingActionPayload(
                source.id(), mode == Mode.MOVE
                        ? BuildingActionPayload.Action.MOVE
                        : BuildingActionPayload.Action.UPGRADE,
                origin, rotation));
        cancel();
    }

    private static void refusePlacement(Minecraft minecraft) {
        String message = !validity.ok() ? validity.message()
                : RtsHudState.buildReason(building,
                        mode == Mode.LINE_PLACE ? linearChargeablePieceCount() : 1);
        minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                message));
    }

    private static BuildingInfo infoFor(PlacedBuildingInfo selected, net.minecraft.resources.Identifier id) {
        int[] costs = id.equals(selected.upgradeStructure())
                ? selected.upgradeCosts() : selected.costs();
        return new BuildingInfo(id, selected.name(), Arrays.copyOf(costs, costs.length), false);
    }

    /** The source size is server-authoritative; the client ignores its whole box for a smooth ghost. */
    private static Set<Long> sourceBounds(PlacedBuildingInfo selected) {
        Set<Long> positions = new HashSet<>(selected.sizeX() * selected.sizeY() * selected.sizeZ());
        for (int x = 0; x < selected.sizeX(); x++) {
            for (int y = 0; y < selected.sizeY(); y++) {
                for (int z = 0; z < selected.sizeZ(); z++) {
                    positions.add(selected.origin().offset(x, y, z).asLong());
                }
            }
        }
        return positions;
    }
}
