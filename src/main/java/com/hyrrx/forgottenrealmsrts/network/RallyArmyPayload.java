package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: order the realm's guardians to move to a world position under the cursor. */
public record RallyArmyPayload(BlockPos target) implements CustomPacketPayload {
    public static final Type<RallyArmyPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "rally_army"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RallyArmyPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, RallyArmyPayload::target, RallyArmyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
