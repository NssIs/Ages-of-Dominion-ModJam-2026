package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: advance the civilization to the next historical age (resource cost, capped). */
public record AdvanceAgePayload() implements CustomPacketPayload {
    public static final Type<AdvanceAgePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "advance_age"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdvanceAgePayload> STREAM_CODEC =
            StreamCodec.unit(new AdvanceAgePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
