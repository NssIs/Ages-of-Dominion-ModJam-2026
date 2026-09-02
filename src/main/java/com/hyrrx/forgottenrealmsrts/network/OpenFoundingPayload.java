package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server→client: the first Town Hall finished placing — open the "FOUND YOUR CIVILIZATION" screen.
 *
 * <p>Carries no data; the screen reads the (default) banner from the synced {@link
 * com.hyrrx.forgottenrealmsrts.RtsCivilization} attachment and everything else is chosen by the
 * player. A record with no components still needs a stream codec, so this uses the unit codec.
 */
public record OpenFoundingPayload() implements CustomPacketPayload {
    public static final Type<OpenFoundingPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "open_founding"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFoundingPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenFoundingPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
