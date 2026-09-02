package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** Server response to a world-building selection request. Empty means clear the selection. */
public record BuildingSelectionPayload(Optional<PlacedBuildingInfo> building,
                                       Optional<ConstructionInfo> construction)
        implements CustomPacketPayload {
    public BuildingSelectionPayload(Optional<PlacedBuildingInfo> building) {
        this(building, Optional.empty());
    }

    public BuildingSelectionPayload {
        building = building == null ? Optional.empty() : building;
        construction = construction == null ? Optional.empty() : construction;
    }

    public static final Type<BuildingSelectionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "building_selection"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,
            BuildingSelectionPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(BuildingSelectionPayload::write,
                    BuildingSelectionPayload::read);

    private static void write(RegistryFriendlyByteBuf buffer, BuildingSelectionPayload payload) {
        buffer.writeBoolean(payload.building().isPresent());
        payload.building().ifPresent(info -> PlacedBuildingInfo.STREAM_CODEC.encode(buffer, info));
        buffer.writeBoolean(payload.construction().isPresent());
        payload.construction().ifPresent(info -> ConstructionInfo.STREAM_CODEC.encode(buffer, info));
    }

    private static BuildingSelectionPayload read(RegistryFriendlyByteBuf buffer) {
        Optional<PlacedBuildingInfo> building = buffer.readBoolean()
                ? Optional.of(PlacedBuildingInfo.STREAM_CODEC.decode(buffer))
                : Optional.empty();
        Optional<ConstructionInfo> construction = buffer.readBoolean()
                ? Optional.of(ConstructionInfo.STREAM_CODEC.decode(buffer))
                : Optional.empty();
        return new BuildingSelectionPayload(building, construction);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
