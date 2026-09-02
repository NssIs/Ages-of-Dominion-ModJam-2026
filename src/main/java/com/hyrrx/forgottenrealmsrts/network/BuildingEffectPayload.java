package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server signal for the client-side building action animation. */
public record BuildingEffectPayload(BlockPos origin, int sizeX, int sizeY, int sizeZ,
                                    BuildingActionPayload.Action action)
        implements CustomPacketPayload {
    public static final Type<BuildingEffectPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "building_effect"));

    private static final StreamCodec<RegistryFriendlyByteBuf, BuildingActionPayload.Action> ACTION_CODEC =
            StreamCodec.of(
                    (buffer, action) -> ByteBufCodecs.VAR_INT.encode(buffer, action.ordinal()),
                    buffer -> BuildingActionPayload.Action.values()[Math.floorMod(
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            BuildingActionPayload.Action.values().length)]);

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingEffectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BuildingEffectPayload::origin,
                    ByteBufCodecs.VAR_INT, BuildingEffectPayload::sizeX,
                    ByteBufCodecs.VAR_INT, BuildingEffectPayload::sizeY,
                    ByteBufCodecs.VAR_INT, BuildingEffectPayload::sizeZ,
                    ACTION_CODEC, BuildingEffectPayload::action,
                    BuildingEffectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
