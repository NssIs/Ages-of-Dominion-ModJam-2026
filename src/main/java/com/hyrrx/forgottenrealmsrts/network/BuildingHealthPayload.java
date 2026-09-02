package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server snapshot used to draw durable-building labels in the world. */
public record BuildingHealthPayload(List<BuildingHealth> buildings)
        implements CustomPacketPayload {
    public record BuildingHealth(long id, Identifier structure, BlockPos origin,
                                 int sizeX, int sizeY, int sizeZ, int health, int maxHealth) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BuildingHealth> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, BuildingHealth::id,
                        Identifier.STREAM_CODEC, BuildingHealth::structure,
                        BlockPos.STREAM_CODEC, BuildingHealth::origin,
                        ByteBufCodecs.VAR_INT, BuildingHealth::sizeX,
                        ByteBufCodecs.VAR_INT, BuildingHealth::sizeY,
                        ByteBufCodecs.VAR_INT, BuildingHealth::sizeZ,
                        ByteBufCodecs.VAR_INT, BuildingHealth::health,
                        ByteBufCodecs.VAR_INT, BuildingHealth::maxHealth,
                        BuildingHealth::new);
    }

    public static final Type<BuildingHealthPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "building_health"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingHealthPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BuildingHealth.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    BuildingHealthPayload::buildings,
                    BuildingHealthPayload::new);

    public BuildingHealthPayload {
        buildings = buildings == null ? List.of() : List.copyOf(buildings);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
