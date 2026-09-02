package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/** Server snapshot of a structure that is still being assembled. */
public record ConstructionInfo(long id, Identifier structure, BlockPos origin, Rotation rotation,
                               int sizeX, int sizeY, int sizeZ, int level, String name,
                               int totalBlocks, int placedBlocks, int assignedWorkers) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionInfo> STREAM_CODEC =
            StreamCodec.of(ConstructionInfo::write, ConstructionInfo::read);

    private static void write(RegistryFriendlyByteBuf buffer, ConstructionInfo info) {
        ByteBufCodecs.VAR_LONG.encode(buffer, info.id());
        Identifier.STREAM_CODEC.encode(buffer, info.structure());
        BlockPos.STREAM_CODEC.encode(buffer, info.origin());
        Rotation.STREAM_CODEC.encode(buffer, info.rotation());
        ByteBufCodecs.VAR_INT.encode(buffer, info.sizeX());
        ByteBufCodecs.VAR_INT.encode(buffer, info.sizeY());
        ByteBufCodecs.VAR_INT.encode(buffer, info.sizeZ());
        ByteBufCodecs.VAR_INT.encode(buffer, info.level());
        ByteBufCodecs.STRING_UTF8.encode(buffer, info.name());
        ByteBufCodecs.VAR_INT.encode(buffer, info.totalBlocks());
        ByteBufCodecs.VAR_INT.encode(buffer, info.placedBlocks());
        ByteBufCodecs.VAR_INT.encode(buffer, info.assignedWorkers());
    }

    private static ConstructionInfo read(RegistryFriendlyByteBuf buffer) {
        return new ConstructionInfo(
                ByteBufCodecs.VAR_LONG.decode(buffer),
                Identifier.STREAM_CODEC.decode(buffer),
                BlockPos.STREAM_CODEC.decode(buffer),
                Rotation.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer));
    }
}
