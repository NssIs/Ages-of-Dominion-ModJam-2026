package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server→client: open the mandatory first-login field guide. */
public record OpenGuidePayload() implements CustomPacketPayload {
    public static final Type<OpenGuidePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "open_guide"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuidePayload> STREAM_CODEC =
            StreamCodec.unit(new OpenGuidePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
