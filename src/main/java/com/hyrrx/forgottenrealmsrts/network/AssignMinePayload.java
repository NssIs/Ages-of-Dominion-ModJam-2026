package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: send selected worker villagers to one tracked mine. */
public record AssignMinePayload(List<Integer> entityIds, long buildingId)
        implements CustomPacketPayload {
    public static final Type<AssignMinePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "assign_mine"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssignMinePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AssignMinePayload::entityIds,
                    ByteBufCodecs.VAR_LONG, AssignMinePayload::buildingId,
                    AssignMinePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
