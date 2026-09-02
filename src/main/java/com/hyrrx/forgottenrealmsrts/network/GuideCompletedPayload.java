package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: the player has visited every page of the first-login field guide. */
public record GuideCompletedPayload() implements CustomPacketPayload {
    public static final Type<GuideCompletedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "guide_completed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuideCompletedPayload> STREAM_CODEC =
            StreamCodec.unit(new GuideCompletedPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
