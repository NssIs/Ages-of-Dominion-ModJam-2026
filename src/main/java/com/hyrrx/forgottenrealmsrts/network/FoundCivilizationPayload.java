package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.FlagDesign;
import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: the player confirmed the founding screen with this name and banner. */
public record FoundCivilizationPayload(String name, FlagDesign flag) implements CustomPacketPayload {
    public static final Type<FoundCivilizationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "found_civilization"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoundCivilizationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FoundCivilizationPayload::name,
                    FlagDesign.STREAM_CODEC, FoundCivilizationPayload::flag,
                    FoundCivilizationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
