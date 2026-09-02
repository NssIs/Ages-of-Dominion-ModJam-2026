package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The answer to {@link RequestBuildingCatalogPayload}: which structures exist, grouped by category.
 *
 * <p>Deliberately thin — identifiers only, no block data. A catalog is sent on every activation and
 * a world may hold hundreds of structures; the blocks needed to actually draw one arrive later and
 * one at a time, via {@link BuildingPreviewPayload}, only for the buildings the tray is about to
 * show.
 */
public record BuildingCatalogPayload(Map<String, List<BuildingInfo>> byCategory) implements CustomPacketPayload {
    public static final Type<BuildingCatalogPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "building_catalog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingCatalogPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.map(HashMap::new,
                            ByteBufCodecs.STRING_UTF8,
                            BuildingInfo.STREAM_CODEC.apply(ByteBufCodecs.list())),
                    BuildingCatalogPayload::byCategory,
                    BuildingCatalogPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
