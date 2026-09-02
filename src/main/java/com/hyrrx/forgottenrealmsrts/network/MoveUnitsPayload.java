package com.hyrrx.forgottenrealmsrts.network;

import java.util.List;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: move exactly the allied units currently selected by the commander. */
public record MoveUnitsPayload(List<Integer> entityIds, BlockPos target) implements CustomPacketPayload {
    public static final Type<MoveUnitsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "move_units"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoveUnitsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), MoveUnitsPayload::entityIds,
                    BlockPos.STREAM_CODEC, MoveUnitsPayload::target,
                    MoveUnitsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
