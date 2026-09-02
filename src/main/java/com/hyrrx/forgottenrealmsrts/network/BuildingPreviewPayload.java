package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The blocks needed to draw one structure, already culled and capped by the server.
 *
 * <p><strong>Everything is packed into ints on purpose.</strong> A visible-shell-only preview of a
 * decent building still runs to a couple of thousand blocks, and sending each as a {@code BlockPos}
 * plus a registry name would be tens of kilobytes per building for a picture 40 pixels wide. One
 * int per block plus a shared palette is about 8KB for the worst case this allows.
 *
 * <p>The packing gives each coordinate a byte and the palette index a byte, so a structure may be
 * up to 256 blocks on a side and use up to 256 distinct blocks. Both limits are enforced
 * server-side in {@code ModPayloads}; a structure block cannot capture more than 48 on a side
 * anyway.
 */
public record BuildingPreviewPayload(Identifier structure,
                                     int sizeX, int sizeY, int sizeZ,
                                     List<Identifier> palette,
                                     List<Integer> blocks) implements CustomPacketPayload {
    public static final Type<BuildingPreviewPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "building_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingPreviewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, BuildingPreviewPayload::structure,
                    ByteBufCodecs.VAR_INT, BuildingPreviewPayload::sizeX,
                    ByteBufCodecs.VAR_INT, BuildingPreviewPayload::sizeY,
                    ByteBufCodecs.VAR_INT, BuildingPreviewPayload::sizeZ,
                    Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), BuildingPreviewPayload::palette,
                    ByteBufCodecs.INT.apply(ByteBufCodecs.list()), BuildingPreviewPayload::blocks,
                    BuildingPreviewPayload::new);

    public static int pack(int x, int y, int z, int paletteIndex) {
        return (x & 0xFF) << 24 | (y & 0xFF) << 16 | (z & 0xFF) << 8 | (paletteIndex & 0xFF);
    }

    public static int unpackX(int packed) {
        return (packed >>> 24) & 0xFF;
    }

    public static int unpackY(int packed) {
        return (packed >>> 16) & 0xFF;
    }

    public static int unpackZ(int packed) {
        return (packed >>> 8) & 0xFF;
    }

    public static int unpackPalette(int packed) {
        return packed & 0xFF;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
