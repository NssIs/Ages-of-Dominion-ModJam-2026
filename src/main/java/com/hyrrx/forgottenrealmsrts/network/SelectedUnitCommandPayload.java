package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Client→server: apply one strategic command to the units the player selected. */
public record SelectedUnitCommandPayload(List<Integer> entityIds,
                                         ArmyCommandPayload.Command command)
        implements CustomPacketPayload {
    public static final Type<SelectedUnitCommandPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "selected_unit_command"));

    private static final StreamCodec<RegistryFriendlyByteBuf, ArmyCommandPayload.Command> COMMAND_CODEC =
            StreamCodec.of(
                    (buffer, command) -> ByteBufCodecs.VAR_INT.encode(buffer, command.ordinal()),
                    buffer -> ArmyCommandPayload.Command.values()[Math.floorMod(
                            ByteBufCodecs.VAR_INT.decode(buffer), ArmyCommandPayload.Command.values().length)]);

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectedUnitCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
                    SelectedUnitCommandPayload::entityIds,
                    COMMAND_CODEC,
                    SelectedUnitCommandPayload::command,
                    SelectedUnitCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
