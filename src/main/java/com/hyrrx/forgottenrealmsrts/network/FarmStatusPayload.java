package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client status for the farm currently selected in the RTS world. */
public record FarmStatusPayload(long buildingId, BlockPos displayPos, int workersInside,
                                int capacity, int output, int intervalSeconds)
        implements CustomPacketPayload {
    public static final Type<FarmStatusPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "farm_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmStatusPayload> STREAM_CODEC =
            StreamCodec.of(FarmStatusPayload::write, FarmStatusPayload::read);

    public static FarmStatusPayload clear() {
        return new FarmStatusPayload(0L, BlockPos.ZERO, 0, 0, 0, 0);
    }

    public boolean isPresent() {
        return buildingId > 0L;
    }

    private static void write(RegistryFriendlyByteBuf buffer, FarmStatusPayload payload) {
        ByteBufCodecs.VAR_LONG.encode(buffer, payload.buildingId());
        BlockPos.STREAM_CODEC.encode(buffer, payload.displayPos());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.workersInside());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.capacity());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.output());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.intervalSeconds());
    }

    private static FarmStatusPayload read(RegistryFriendlyByteBuf buffer) {
        return new FarmStatusPayload(
                ByteBufCodecs.VAR_LONG.decode(buffer),
                BlockPos.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
