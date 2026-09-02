package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client→server: recentre the RTS camera on the player's Town Hall.
 *
 * <p>The camera anchor is the (spectator) player's own position, so "go home" is a server-side
 * teleport — authoritative and reload-safe — rather than a client-side guess at where the hall is.
 */
public record RequestHomePayload() implements CustomPacketPayload {
    public static final Type<RequestHomePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "request_home"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestHomePayload> STREAM_CODEC =
            StreamCodec.unit(new RequestHomePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
