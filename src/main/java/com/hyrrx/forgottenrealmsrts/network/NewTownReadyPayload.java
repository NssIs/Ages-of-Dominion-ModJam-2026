package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server→client: the old realm is gone and the founding tray can guide the next Town Hall. */
public record NewTownReadyPayload() implements CustomPacketPayload {
    public static final Type<NewTownReadyPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "new_town_ready"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NewTownReadyPayload> STREAM_CODEC =
            StreamCodec.unit(new NewTownReadyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
