package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: dedicate selected worker villagers to one farm. */
public record AssignFarmPayload(List<Integer> entityIds, long buildingId)
        implements CustomPacketPayload {
    public static final Type<AssignFarmPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "assign_farm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssignFarmPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AssignFarmPayload::entityIds,
                    ByteBufCodecs.VAR_LONG, AssignFarmPayload::buildingId,
                    AssignFarmPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
