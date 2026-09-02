package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Requests one expanded path rectangle or wall span. The server derives every segment from the two
 * cells rather than trusting a client-supplied list of blocks.
 */
public record PlaceLinearBuildingPayload(Identifier structure, BlockPos anchor,
                                         BlockPos cursor) implements CustomPacketPayload {
    public static final Type<PlaceLinearBuildingPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "place_linear_building"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceLinearBuildingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, PlaceLinearBuildingPayload::structure,
                    BlockPos.STREAM_CODEC, PlaceLinearBuildingPayload::anchor,
                    BlockPos.STREAM_CODEC, PlaceLinearBuildingPayload::cursor,
                    PlaceLinearBuildingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
