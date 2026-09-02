package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: the free RTS cursor struck an enemy mob. */
public record RtsMobHitPayload(int entityId) implements CustomPacketPayload {
    public static final Type<RtsMobHitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "rts_mob_hit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsMobHitPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, RtsMobHitPayload::entityId,
                    RtsMobHitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
