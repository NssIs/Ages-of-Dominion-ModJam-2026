package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: send selected Realm Villagers to repair one damaged building. */
public record AssignRepairPayload(List<Integer> entityIds, long buildingId)
        implements CustomPacketPayload {
    public static final Type<AssignRepairPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "assign_repair"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssignRepairPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AssignRepairPayload::entityIds,
                    ByteBufCodecs.VAR_LONG, AssignRepairPayload::buildingId,
                    AssignRepairPayload::new);

    public AssignRepairPayload {
        entityIds = entityIds == null ? List.of() : List.copyOf(entityIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
