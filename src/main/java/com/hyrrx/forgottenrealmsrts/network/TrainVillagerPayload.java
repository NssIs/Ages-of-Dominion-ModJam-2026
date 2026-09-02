package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: train one villager at the Town Hall (food cost, within the population cap). */
public record TrainVillagerPayload(long townHallId) implements CustomPacketPayload {
    public static final Type<TrainVillagerPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "train_villager"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrainVillagerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, TrainVillagerPayload::townHallId,
                    TrainVillagerPayload::new);

    /** Keyboard training keeps its previous behaviour by allowing the server to find the hall. */
    public TrainVillagerPayload() {
        this(0L);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
