package com.hyrrx.forgottenrealmsrts.network;

import java.util.List;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: assign selected workers to the tree under the RTS cursor. */
public record GatherWoodPayload(List<Integer> entityIds, BlockPos target) implements CustomPacketPayload {
    public static final Type<GatherWoodPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "gather_wood"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GatherWoodPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), GatherWoodPayload::entityIds,
                    BlockPos.STREAM_CODEC, GatherWoodPayload::target,
                    GatherWoodPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
