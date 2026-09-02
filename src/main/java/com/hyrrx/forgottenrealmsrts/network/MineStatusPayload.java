package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client status for the mine currently selected in the RTS world. */
public record MineStatusPayload(long buildingId, BlockPos displayPos, String resource,
                                int workersInside, int capacity, int output, String bonusResource,
                                int bonusOutput, int intervalSeconds)
        implements CustomPacketPayload {
    public static final Type<MineStatusPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "mine_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MineStatusPayload> STREAM_CODEC =
            StreamCodec.of(MineStatusPayload::write, MineStatusPayload::read);

    public static MineStatusPayload clear() {
        return new MineStatusPayload(0L, BlockPos.ZERO, "", 0, 0, 0, "", 0, 0);
    }

    public boolean isPresent() {
        return buildingId > 0L && !resource.isEmpty();
    }

    private static void write(RegistryFriendlyByteBuf buffer, MineStatusPayload payload) {
        ByteBufCodecs.VAR_LONG.encode(buffer, payload.buildingId());
        BlockPos.STREAM_CODEC.encode(buffer, payload.displayPos());
        ByteBufCodecs.STRING_UTF8.encode(buffer, payload.resource());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.workersInside());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.capacity());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.output());
        ByteBufCodecs.STRING_UTF8.encode(buffer, payload.bonusResource());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.bonusOutput());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.intervalSeconds());
    }

    private static MineStatusPayload read(RegistryFriendlyByteBuf buffer) {
        return new MineStatusPayload(
                ByteBufCodecs.VAR_LONG.decode(buffer),
                BlockPos.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
