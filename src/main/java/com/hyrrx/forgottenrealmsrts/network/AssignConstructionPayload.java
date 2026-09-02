package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: send selected worker villagers to one in-progress construction site. */
public record AssignConstructionPayload(List<Integer> entityIds, long constructionId)
        implements CustomPacketPayload {
    public static final Type<AssignConstructionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "assign_construction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssignConstructionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
                    AssignConstructionPayload::entityIds,
                    ByteBufCodecs.VAR_LONG, AssignConstructionPayload::constructionId,
                    AssignConstructionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
