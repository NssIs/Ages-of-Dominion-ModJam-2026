package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/**
 * "Build this structure here, turned this way."
 *
 * <p>Everything in it is a request, not an instruction. The server re-checks the cost, the
 * town-hall rule, the ground support and the collision before it places anything — the client's
 * ghost decides what to *show*, never what is allowed.
 */
public record PlaceBuildingPayload(Identifier structure, BlockPos origin,
                                   Rotation rotation) implements CustomPacketPayload {
    public static final Type<PlaceBuildingPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "place_building"));

    private static final Rotation[] ROTATIONS = Rotation.values();

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceBuildingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, PlaceBuildingPayload::structure,
                    BlockPos.STREAM_CODEC, PlaceBuildingPayload::origin,
                    ByteBufCodecs.VAR_INT.map(
                            ordinal -> ROTATIONS[Math.floorMod(ordinal, ROTATIONS.length)],
                            Rotation::ordinal),
                    PlaceBuildingPayload::rotation,
                    PlaceBuildingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
