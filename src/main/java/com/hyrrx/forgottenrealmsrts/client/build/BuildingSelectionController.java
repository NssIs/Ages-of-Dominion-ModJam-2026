package com.hyrrx.forgottenrealmsrts.client.build;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.RtsFarmOrders;
import com.hyrrx.forgottenrealmsrts.RtsMineOrders;
import com.hyrrx.forgottenrealmsrts.RtsVillagerEntity;
import com.hyrrx.forgottenrealmsrts.RtsWorkerOrders;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;
import com.hyrrx.forgottenrealmsrts.client.RtsUnitScreenProjection;
import com.hyrrx.forgottenrealmsrts.client.RtsUnitPathState;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsBottomBarHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsHudState;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsSidePanelHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsTopBarHud;
import com.hyrrx.forgottenrealmsrts.network.PlacedBuildingInfo;
import com.hyrrx.forgottenrealmsrts.network.ConstructionInfo;
import com.hyrrx.forgottenrealmsrts.network.AssignConstructionPayload;
import com.hyrrx.forgottenrealmsrts.network.AssignFarmPayload;
import com.hyrrx.forgottenrealmsrts.network.AssignMinePayload;
import com.hyrrx.forgottenrealmsrts.network.AssignRepairPayload;
import com.hyrrx.forgottenrealmsrts.network.BuildingActionPayload;
import com.hyrrx.forgottenrealmsrts.network.RequestBuildingSelectionPayload;
import com.hyrrx.forgottenrealmsrts.network.RtsMobHitPayload;
import com.hyrrx.forgottenrealmsrts.network.GatherWoodPayload;
import com.hyrrx.forgottenrealmsrts.network.MoveUnitsPayload;
import com.hyrrx.forgottenrealmsrts.network.ArmyCommandPayload;
import com.hyrrx.forgottenrealmsrts.network.SelectedUnitCommandPayload;
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/** Converts a world click into a server-authoritative tracked-building selection. */
public final class BuildingSelectionController {
    /** Window in ticks a first demolish press stays armed, waiting for the confirming second press. */
    private static final int DEMOLISH_CONFIRM_WINDOW_TICKS = 60;

    /** Kept until the server identifies the structure under a selected-unit order click. */
    private static PendingOrder pendingOrder;
    private static int worksiteStatusRefreshTicks;
    /** The building id armed by a first demolish press, or -1 when nothing is armed. */
    private static long demolishArmedBuildingId = -1L;
    private static int demolishArmTicksRemaining;

    private BuildingSelectionController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BuildingSelectionController::onClientTick);
    }

    public static boolean beginMove() {
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        if (selected == null) {
            return false;
        }
        RtsHudState.closeUpgradePopup();
        BuildGhost.beginMove(selected);
        return BuildGhost.hasSession();
    }

    public static boolean beginUpgrade() {
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        if (selected == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!selected.canUpgrade()) {
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                        "message.forgotten_realms_rts.no_upgrade"));
            }
            return false;
        }
        if (!RtsHudState.canAffordUpgrade(selected)) {
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                        "message.forgotten_realms_rts.not_enough_resources"));
            }
            return false;
        }
        RtsHudState.closeUpgradePopup();
        BuildGhost.beginUpgrade(selected);
        return BuildGhost.hasSession();
    }

    /**
     * The visible UPGRADE button is deliberately a two-step action: the first press opens the
     * price readout, and only a second press can begin the placement ghost.
     */
    public static boolean pressUpgradeButton() {
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        if (selected == null) {
            return false;
        }
        if (!RtsHudState.isUpgradePopupOpen(selected.id())) {
            RtsHudState.openUpgradePopup(selected);
            return true;
        }
        return beginUpgrade();
    }

    /**
     * A demolish is destructive and irreversible, so the first press only arms it; the player has to
     * press again within {@link #DEMOLISH_CONFIRM_WINDOW_TICKS} to actually send the request. Any
     * press against a different building re-arms rather than confirms, so switching selection can
     * never demolish the wrong thing.
     */
    public static boolean beginDemolish() {
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        if (selected == null || BuildGhost.hasSession()) {
            return false;
        }
        if ("hall".equals(ModPayloads.buildingOf(selected.structure()))) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendOverlayMessage(
                        net.minecraft.network.chat.Component.literal(
                                "The Town Hall cannot be deleted."));
            }
            demolishArmedBuildingId = -1L;
            demolishArmTicksRemaining = 0;
            return false;
        }
        RtsHudState.closeUpgradePopup();
        if (demolishArmedBuildingId == selected.id() && demolishArmTicksRemaining > 0) {
            demolishArmedBuildingId = -1L;
            demolishArmTicksRemaining = 0;
            ClientPacketDistributor.sendToServer(new BuildingActionPayload(selected.id(),
                    BuildingActionPayload.Action.DEMOLISH, selected.origin(), selected.rotation()));
            return true;
        }
        demolishArmedBuildingId = selected.id();
        demolishArmTicksRemaining = DEMOLISH_CONFIRM_WINDOW_TICKS;
        return true;
    }

    /** Drops action latches when the client leaves a world, before the next player can select a card. */
    public static void clearTransientState() {
        pendingOrder = null;
        worksiteStatusRefreshTicks = 0;
        demolishArmedBuildingId = -1L;
        demolishArmTicksRemaining = 0;
    }

    /** Whether the given building is currently armed and waiting for the confirming second press. */
    public static boolean isDemolishArmed(long buildingId) {
        return demolishArmedBuildingId == buildingId && demolishArmTicksRemaining > 0;
    }

    /** Lets the arm window lapse on its own, and drops it the moment selection moves elsewhere. */
    private static void tickDemolishArm() {
        if (demolishArmTicksRemaining <= 0) {
            return;
        }
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        if (selected == null || selected.id() != demolishArmedBuildingId) {
            demolishArmedBuildingId = -1L;
            demolishArmTicksRemaining = 0;
            return;
        }
        if (--demolishArmTicksRemaining <= 0) {
            demolishArmedBuildingId = -1L;
        }
    }

    /** Sends a command to the current selection and immediately releases the world-order latch. */
    public static boolean issueSelectedCommand(ArmyCommandPayload.Command command) {
        List<LivingEntity> selected = RtsHudState.selectedUnits();
        if (selected.isEmpty()) {
            return false;
        }
        ClientPacketDistributor.sendToServer(new SelectedUnitCommandPayload(ids(selected), command));
        RtsHudState.clearSelectedUnits();
        return true;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        refreshSelectedWorksiteStatus(minecraft);
        tickDemolishArm();

        RtsMouseController.SelectionBox selection = RtsMouseController.consumeSelectionRelease();
        if (selection != null) {
            if (!BuildGhost.hasSession()) {
                RtsHudState.setSelectedUnits(unitsIn(selection, minecraft));
            }
            return;
        }

        if (BuildGhost.hasSession() || RtsMouseController.isPanning()
                || RtsMouseController.isSelecting() || pendingOrder != null) {
            return;
        }
        if (!RtsMouseController.consumeLeftClickReleased() || overHud(minecraft)) {
            return;
        }

        // A unit under the cursor takes priority over the building behind it.
        LivingEntity unit = BuildingRaycast.pickEntity(minecraft);
        if (unit != null) {
            if (RtsEntities.isEnemyUnit(unit)) {
                RtsHudState.setSelectedTarget(unit);
                RtsHudState.noteTargetHit(unit);
                ClientPacketDistributor.sendToServer(new RtsMobHitPayload(unit.getId()));
            } else {
                RtsHudState.setSelectedUnit(unit);
            }
            return;
        }

        BlockHitResult hit = BuildingRaycast.pick(minecraft);
        List<LivingEntity> selected = RtsHudState.selectedUnits();
        if (!selected.isEmpty()) {
            if (hit == null) {
                RtsHudState.clearSelectedUnits();
                return;
            }
            requestUnitOrder(minecraft, selected, hit);
            return;
        }

        RtsHudState.setSelectedUnit(null);
        if (hit == null) {
            RtsHudState.setSelectedPlacedBuilding(null);
            return;
        }
        ClientPacketDistributor.sendToServer(new RequestBuildingSelectionPayload(hit.getBlockPos()));
    }

    /** A selected unit click first resolves the tracked building, then falls back to tree/move. */
    private static void requestUnitOrder(Minecraft minecraft, List<LivingEntity> selected,
                                         BlockHitResult hit) {
        pendingOrder = new PendingOrder(List.copyOf(selected), hit.getBlockPos(), movementTarget(hit));
        ClientPacketDistributor.sendToServer(new RequestBuildingSelectionPayload(hit.getBlockPos()));
    }

    /** Completes a pending order after the server has identified the clicked structure, if any. */
    public static boolean resolvePendingBuildingSelection(PlacedBuildingInfo building) {
        PendingOrder pending = pendingOrder;
        if (pending == null) {
            return false;
        }
        pendingOrder = null;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            RtsHudState.clearSelectedUnits();
            return true;
        }

        List<LivingEntity> selected = pending.units().stream()
                .filter(unit -> unit != null && unit.isAlive())
                .toList();
        if (selected.isEmpty()) {
            RtsHudState.clearSelectedUnits();
            return true;
        }

        List<LivingEntity> workers = selected.stream()
                .filter(unit -> unit instanceof RtsVillagerEntity)
                .toList();
        List<LivingEntity> nonWorkers = selected.stream()
                .filter(unit -> !(unit instanceof RtsVillagerEntity))
                .toList();

        if (building != null && building.health() > 0
                && building.health() < building.maxHealth()) {
            if (!workers.isEmpty()) {
                ClientPacketDistributor.sendToServer(new AssignRepairPayload(ids(workers), building.id()));
                RtsUnitPathState.show(workers, building.origin());
            }
            if (!nonWorkers.isEmpty()) {
                minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                        "Only workers can repair buildings."));
            }
            if (workers.isEmpty() && nonWorkers.isEmpty()) {
                minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                        "Select villagers to repair."));
            }
            RtsHudState.clearSelectedUnits();
            return true;
        }

        if (building != null && RtsMineOrders.isMineStructure(building.structure())) {
            BlockPos mineTarget = building.origin().offset(building.sizeX() / 2,
                    Math.min(1, Math.max(0, building.sizeY() - 1)), building.sizeZ() / 2);
            if (!workers.isEmpty()) {
                ClientPacketDistributor.sendToServer(new AssignMinePayload(ids(workers), building.id()));
                RtsUnitPathState.show(workers, mineTarget);
            }
            if (!nonWorkers.isEmpty()) {
                minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                        "Only workers can enter a mine."));
            }
            RtsHudState.clearSelectedUnits();
            return true;
        }

        if (building != null && RtsFarmOrders.isFarmStructure(building.structure())) {
            BlockPos farmTarget = building.origin().offset(building.sizeX() / 2,
                    Math.min(1, Math.max(0, building.sizeY() - 1)), building.sizeZ() / 2);
            if (!workers.isEmpty()) {
                ClientPacketDistributor.sendToServer(new AssignFarmPayload(ids(workers), building.id()));
                RtsUnitPathState.show(workers, farmTarget);
            }
            if (!nonWorkers.isEmpty()) {
                minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                        "Only workers can tend a Farm."));
            }
            RtsHudState.clearSelectedUnits();
            return true;
        }

        if (!workers.isEmpty()
                && RtsWorkerOrders.isTreeBlock(minecraft.level.getBlockState(pending.clicked()))) {
            ClientPacketDistributor.sendToServer(new GatherWoodPayload(ids(workers), pending.clicked()));
            RtsUnitPathState.show(workers, pending.clicked());
            if (!nonWorkers.isEmpty()) {
                ClientPacketDistributor.sendToServer(new MoveUnitsPayload(ids(nonWorkers), pending.moveTarget()));
                RtsUnitPathState.show(nonWorkers, pending.moveTarget());
            }
            RtsHudState.clearSelectedUnits();
            return true;
        }

        ClientPacketDistributor.sendToServer(new MoveUnitsPayload(ids(selected), pending.moveTarget()));
        RtsUnitPathState.show(selected, pending.moveTarget());
        RtsHudState.clearSelectedUnits();
        return true;
    }

    /** Assigns only selected workers when a pending order clicked an unfinished foundation. */
    public static boolean resolvePendingConstructionSelection(ConstructionInfo construction) {
        PendingOrder pending = pendingOrder;
        if (pending == null) {
            return false;
        }
        pendingOrder = null;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || construction == null) {
            RtsHudState.clearSelectedUnits();
            return true;
        }

        List<LivingEntity> selected = pending.units().stream()
                .filter(unit -> unit != null && unit.isAlive())
                .toList();
        List<LivingEntity> workers = selected.stream()
                .filter(unit -> unit instanceof RtsVillagerEntity)
                .toList();
        List<LivingEntity> nonWorkers = selected.stream()
                .filter(unit -> !(unit instanceof RtsVillagerEntity))
                .toList();
        BlockPos siteTarget = construction.origin().offset(construction.sizeX() / 2, 0,
                construction.sizeZ() / 2);
        if (!workers.isEmpty()) {
            ClientPacketDistributor.sendToServer(new AssignConstructionPayload(ids(workers),
                    construction.id()));
            RtsUnitPathState.show(workers, siteTarget);
        }
        if (!nonWorkers.isEmpty()) {
            minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "Only workers can build."));
        }
        if (workers.isEmpty() && nonWorkers.isEmpty()) {
            minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "Select villagers to build."));
        }
        RtsHudState.clearSelectedUnits();
        return true;
    }

    private static List<Integer> ids(List<? extends LivingEntity> units) {
        return units.stream().map(LivingEntity::getId).toList();
    }

    private static BlockPos movementTarget(BlockHitResult hit) {
        return switch (hit.getDirection()) {
            case UP -> hit.getBlockPos().above();
            case DOWN -> hit.getBlockPos();
            default -> hit.getBlockPos().relative(hit.getDirection());
        };
    }

    /** Projects all loaded RTS units, including units behind walls, into the drag rectangle. */
    private static List<LivingEntity> unitsIn(RtsMouseController.SelectionBox selection,
                                               Minecraft minecraft) {
        int left = selection.left();
        int top = selection.top();
        int right = selection.right();
        int bottom = selection.bottom();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        double maxDistance = 192.0D * 192.0D;
        List<LivingEntity> selected = new ArrayList<>();

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity unit) || !unit.isAlive()
                    || !RtsEntities.isAlliedUnit(unit)
                    || unit.distanceToSqr(camera) > maxDistance) {
                continue;
            }
            RtsUnitScreenProjection.ScreenPoint point = RtsUnitScreenProjection.project(minecraft,
                    unit.position().add(0.0D, unit.getBbHeight() * 0.55D, 0.0D));
            if (point != null && point.x() >= left && point.x() <= right
                    && point.y() >= top && point.y() <= bottom) {
                selected.add(unit);
            }
        }
        return selected;
    }

    private static boolean overHud(Minecraft minecraft) {
        int mouseX = RtsMouseController.mouseX(minecraft);
        int mouseY = RtsMouseController.mouseY(minecraft);
        return RtsTopBarHud.isPointInside(mouseX, mouseY)
                || RtsBottomBarHud.isPointInside(mouseX, mouseY)
                || RtsSidePanelHud.isPointInside(mouseX, mouseY);
    }

    /**
     * Keeps the selected building's panel current without adding a world action. This used to
     * re-poll only mines and farms, which is exactly why a Town Hall's own billboard (population,
     * upgrade availability) stayed stale after an upgrade landed while something else — an in-flight
     * unit order — consumed the immediate reply. Any selected placed building now gets the same
     * one-second re-poll; the server sends mine/farm status alongside the selection either way, and
     * for every other building the reply is just the refreshed {@code PlacedBuildingInfo}.
     */
    private static void refreshSelectedWorksiteStatus(Minecraft minecraft) {
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        ConstructionInfo construction = RtsHudState.selectedConstruction();
        if (selected == null && construction == null) {
            worksiteStatusRefreshTicks = 0;
            return;
        }
        if (++worksiteStatusRefreshTicks < 20) {
            return;
        }
        worksiteStatusRefreshTicks = 0;
        ClientPacketDistributor.sendToServer(new RequestBuildingSelectionPayload(
                selected != null ? selected.origin() : construction.origin()));
    }

    private record PendingOrder(List<LivingEntity> units, BlockPos clicked, BlockPos moveTarget) {
    }
}
