package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client request for the tracked building containing the clicked block. */
public record RequestBuildingSelectionPayload(BlockPos clicked) implements CustomPacketPayload {
    public static final Type<RequestBuildingSelectionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "request_building_selection"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,
            RequestBuildingSelectionPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestBuildingSelectionPayload::clicked,
                    RequestBuildingSelectionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
