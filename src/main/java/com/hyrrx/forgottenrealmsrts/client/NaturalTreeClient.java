package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.NaturalTreeClassifier;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsHudState;
import com.hyrrx.forgottenrealmsrts.network.ConstructionInfo;
import com.hyrrx.forgottenrealmsrts.network.PlacedBuildingInfo;
import net.minecraft.core.BlockPos;

/** Client-side metadata protection passed into the shared natural-tree classifier. */
public final class NaturalTreeClient {
    private NaturalTreeClient() {
    }

    public static NaturalTreeClassifier.Protection protection() {
        return position -> RtsBuildingHealthBarRenderer.protects(position)
                || selectedBuildingContains(position)
                || selectedConstructionContains(position);
    }

    private static boolean selectedBuildingContains(BlockPos position) {
        PlacedBuildingInfo building = RtsHudState.selectedPlacedBuilding();
        return building != null && position.getX() >= building.origin().getX()
                && position.getX() < building.origin().getX() + building.sizeX()
                && position.getY() >= building.origin().getY()
                && position.getY() < building.origin().getY() + building.sizeY()
                && position.getZ() >= building.origin().getZ()
                && position.getZ() < building.origin().getZ() + building.sizeZ();
    }

    private static boolean selectedConstructionContains(BlockPos position) {
        ConstructionInfo construction = RtsHudState.selectedConstruction();
        return construction != null && position.getX() >= construction.origin().getX()
                && position.getX() < construction.origin().getX() + construction.sizeX()
                && position.getY() >= construction.origin().getY()
                && position.getY() < construction.origin().getY() + construction.sizeY()
                && position.getZ() >= construction.origin().getZ()
                && position.getZ() < construction.origin().getZ() + construction.sizeZ();
    }
}
