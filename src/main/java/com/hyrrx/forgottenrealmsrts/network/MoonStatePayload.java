package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import com.hyrrx.forgottenrealmsrts.RtsMoons;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server→client synchronization for a named moon event while it approaches or owns the night. */
public record MoonStatePayload(RtsMoons.Moon moon) implements CustomPacketPayload {
    public static final Type<MoonStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "moon_state"));

    private static final StreamCodec<RegistryFriendlyByteBuf, RtsMoons.Moon> MOON_CODEC =
            StreamCodec.of(
                    (buffer, moon) -> ByteBufCodecs.VAR_INT.encode(buffer, moon.ordinal()),
                    buffer -> RtsMoons.Moon.values()[Math.floorMod(
                            ByteBufCodecs.VAR_INT.decode(buffer), RtsMoons.Moon.values().length)]);

    public static final StreamCodec<RegistryFriendlyByteBuf, MoonStatePayload> STREAM_CODEC =
            StreamCodec.composite(MOON_CODEC, MoonStatePayload::moon, MoonStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
