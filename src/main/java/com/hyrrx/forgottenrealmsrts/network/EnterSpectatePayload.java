package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: enter the frozen spectator view for the player's defeated town. */
public record EnterSpectatePayload() implements CustomPacketPayload {
    public static final Type<EnterSpectatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "enter_spectate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnterSpectatePayload> STREAM_CODEC =
            StreamCodec.unit(new EnterSpectatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
