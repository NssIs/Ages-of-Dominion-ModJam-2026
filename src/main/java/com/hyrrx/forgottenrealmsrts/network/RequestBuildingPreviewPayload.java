package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** "Send me the blocks for this one structure, so I can draw it." Replied to with
 *  {@link BuildingPreviewPayload}. */
public record RequestBuildingPreviewPayload(Identifier structure) implements CustomPacketPayload {
    public static final Type<RequestBuildingPreviewPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "request_building_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestBuildingPreviewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC,
                    RequestBuildingPreviewPayload::structure,
                    RequestBuildingPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
