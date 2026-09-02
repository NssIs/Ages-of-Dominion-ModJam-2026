package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/** Client request to move or upgrade one server-tracked building. */
public record BuildingActionPayload(long buildingId, Action action, BlockPos destination,
                                    Rotation rotation) implements CustomPacketPayload {
    public enum Action {
        PLACE,
        MOVE,
        UPGRADE,
        DEMOLISH
    }

    public static final Type<BuildingActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "building_action"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Action> ACTION_CODEC =
            StreamCodec.of(
                    (buffer, action) -> ByteBufCodecs.VAR_INT.encode(buffer, action.ordinal()),
                    buffer -> Action.values()[Math.floorMod(
                            ByteBufCodecs.VAR_INT.decode(buffer), Action.values().length)]);

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, BuildingActionPayload::buildingId,
                    ACTION_CODEC, BuildingActionPayload::action,
                    BlockPos.STREAM_CODEC, BuildingActionPayload::destination,
                    Rotation.STREAM_CODEC, BuildingActionPayload::rotation,
                    BuildingActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
