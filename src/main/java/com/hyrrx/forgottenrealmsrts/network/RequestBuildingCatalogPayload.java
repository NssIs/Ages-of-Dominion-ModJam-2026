package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * "Tell me what buildings exist." Sent client→server once whenever RTS mode turns on.
 *
 * <p>Carries nothing — the answer depends only on what is saved on the server, not on anything the
 * client knows. The reply is {@link BuildingCatalogPayload}.
 */
public record RequestBuildingCatalogPayload() implements CustomPacketPayload {
    public static final Type<RequestBuildingCatalogPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "request_building_catalog"));

    public static final RequestBuildingCatalogPayload INSTANCE = new RequestBuildingCatalogPayload();

    /** A payload with no fields still needs a codec; this one reads and writes zero bytes. */
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RequestBuildingCatalogPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
