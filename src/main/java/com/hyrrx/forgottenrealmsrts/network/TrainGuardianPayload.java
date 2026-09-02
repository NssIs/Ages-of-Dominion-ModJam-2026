package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: train one Fighter Cabin unit (legacy mixed fighter or the Crossbowman profile). */
public record TrainGuardianPayload(Unit unit) implements CustomPacketPayload {
    public enum Unit {
        RANDOM_GUARDIAN,
        CROSSBOWMAN
    }

    public TrainGuardianPayload() {
        this(Unit.RANDOM_GUARDIAN);
    }

    public TrainGuardianPayload {
        if (unit == null) {
            unit = Unit.RANDOM_GUARDIAN;
        }
    }

    public static final Type<TrainGuardianPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "train_guardian"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Unit> UNIT_CODEC =
            StreamCodec.of(
                    (buffer, value) -> ByteBufCodecs.VAR_INT.encode(buffer, value.ordinal()),
                    buffer -> Unit.values()[Math.floorMod(
                            ByteBufCodecs.VAR_INT.decode(buffer), Unit.values().length)]);

    public static final StreamCodec<RegistryFriendlyByteBuf, TrainGuardianPayload> STREAM_CODEC =
            StreamCodec.composite(UNIT_CODEC, TrainGuardianPayload::unit, TrainGuardianPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
