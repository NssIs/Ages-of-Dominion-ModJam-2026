package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server: jump the RTS camera to a world (x, z) — used by clicking the minimap. */
public record MoveCameraPayload(int x, int z) implements CustomPacketPayload {
    public static final Type<MoveCameraPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "move_camera"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoveCameraPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MoveCameraPayload::x,
                    ByteBufCodecs.VAR_INT, MoveCameraPayload::z,
                    MoveCameraPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
