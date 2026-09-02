package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: a target-free order to the realm's guardians, issued from the command grid. */
public record ArmyCommandPayload(Command command) implements CustomPacketPayload {
    public enum Command {
        /** Regroup at the Town Hall. */
        RALLY_HOME,
        /** Move onto the nearest threatening enemy (the guardians take over from there). */
        ATTACK_NEAREST,
        /** Halt where they stand. */
        STOP
    }

    public static final Type<ArmyCommandPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "army_command"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Command> COMMAND_CODEC =
            StreamCodec.of(
                    (buffer, command) -> ByteBufCodecs.VAR_INT.encode(buffer, command.ordinal()),
                    buffer -> Command.values()[Math.floorMod(
                            ByteBufCodecs.VAR_INT.decode(buffer), Command.values().length)]);

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmyCommandPayload> STREAM_CODEC =
            StreamCodec.composite(COMMAND_CODEC, ArmyCommandPayload::command, ArmyCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
