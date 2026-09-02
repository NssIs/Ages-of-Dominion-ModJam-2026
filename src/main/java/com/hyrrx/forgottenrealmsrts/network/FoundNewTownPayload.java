package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: confirms the irreversible cleanup of a defeated realm. */
public record FoundNewTownPayload() implements CustomPacketPayload {
    public static final Type<FoundNewTownPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "found_new_town"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoundNewTownPayload> STREAM_CODEC =
            StreamCodec.unit(new FoundNewTownPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
