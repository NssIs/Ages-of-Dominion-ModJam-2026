package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: spend stone to restore some Town Hall integrity. */
public record RepairTownHallPayload() implements CustomPacketPayload {
    public static final Type<RepairTownHallPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "repair_town_hall"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RepairTownHallPayload> STREAM_CODEC =
            StreamCodec.unit(new RepairTownHallPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
