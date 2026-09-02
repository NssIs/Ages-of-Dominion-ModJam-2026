package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.Resource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/** Server-authoritative description of the building the player clicked in the world. */
public record PlacedBuildingInfo(long id, Identifier structure, BlockPos origin, Rotation rotation,
                                 int sizeX, int sizeY, int sizeZ, int level, String name,
                                 int[] costs, int[] upgradeCosts, Identifier upgradeStructure,
                                 int health, int maxHealth) {
    /** Compatibility constructor for payloads created before effective upgrade prices were sent. */
    public PlacedBuildingInfo(long id, Identifier structure, BlockPos origin, Rotation rotation,
                              int sizeX, int sizeY, int sizeZ, int level, String name,
                              int[] costs, Identifier upgradeStructure) {
        this(id, structure, origin, rotation, sizeX, sizeY, sizeZ, level, name, costs,
                new int[Resource.COUNT], upgradeStructure, 0, 0);
    }

    /** Compatibility constructor for callers that do not yet have a health snapshot. */
    public PlacedBuildingInfo(long id, Identifier structure, BlockPos origin, Rotation rotation,
                              int sizeX, int sizeY, int sizeZ, int level, String name,
                              int[] costs, int[] upgradeCosts, Identifier upgradeStructure) {
        this(id, structure, origin, rotation, sizeX, sizeY, sizeZ, level, name, costs,
                upgradeCosts, upgradeStructure, 0, 0);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PlacedBuildingInfo> STREAM_CODEC =
            StreamCodec.of(PlacedBuildingInfo::write, PlacedBuildingInfo::read);

    private static void write(RegistryFriendlyByteBuf buffer, PlacedBuildingInfo info) {
        ByteBufCodecs.VAR_LONG.encode(buffer, info.id());
        Identifier.STREAM_CODEC.encode(buffer, info.structure());
        BlockPos.STREAM_CODEC.encode(buffer, info.origin());
        Rotation.STREAM_CODEC.encode(buffer, info.rotation());
        ByteBufCodecs.VAR_INT.encode(buffer, info.sizeX());
        ByteBufCodecs.VAR_INT.encode(buffer, info.sizeY());
        ByteBufCodecs.VAR_INT.encode(buffer, info.sizeZ());
        ByteBufCodecs.VAR_INT.encode(buffer, info.level());
        ByteBufCodecs.STRING_UTF8.encode(buffer, info.name());
        for (int index = 0; index < Resource.COUNT; index++) {
            ByteBufCodecs.VAR_INT.encode(buffer,
                    index < info.costs().length ? info.costs()[index] : 0);
        }
        for (int index = 0; index < Resource.COUNT; index++) {
            ByteBufCodecs.VAR_INT.encode(buffer,
                    index < info.upgradeCosts().length ? info.upgradeCosts()[index] : 0);
        }
        buffer.writeBoolean(info.upgradeStructure() != null);
        if (info.upgradeStructure() != null) {
            Identifier.STREAM_CODEC.encode(buffer, info.upgradeStructure());
        }
        ByteBufCodecs.VAR_INT.encode(buffer, info.health());
        ByteBufCodecs.VAR_INT.encode(buffer, info.maxHealth());
    }

    private static PlacedBuildingInfo read(RegistryFriendlyByteBuf buffer) {
        long id = ByteBufCodecs.VAR_LONG.decode(buffer);
        Identifier structure = Identifier.STREAM_CODEC.decode(buffer);
        BlockPos origin = BlockPos.STREAM_CODEC.decode(buffer);
        Rotation rotation = Rotation.STREAM_CODEC.decode(buffer);
        int sizeX = ByteBufCodecs.VAR_INT.decode(buffer);
        int sizeY = ByteBufCodecs.VAR_INT.decode(buffer);
        int sizeZ = ByteBufCodecs.VAR_INT.decode(buffer);
        int level = ByteBufCodecs.VAR_INT.decode(buffer);
        String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
        int[] costs = new int[Resource.COUNT];
        for (int index = 0; index < costs.length; index++) {
            costs[index] = ByteBufCodecs.VAR_INT.decode(buffer);
        }
        int[] upgradeCosts = new int[Resource.COUNT];
        for (int index = 0; index < upgradeCosts.length; index++) {
            upgradeCosts[index] = ByteBufCodecs.VAR_INT.decode(buffer);
        }
        Identifier upgradeStructure = buffer.readBoolean()
                ? Identifier.STREAM_CODEC.decode(buffer)
                : null;
        int health = ByteBufCodecs.VAR_INT.decode(buffer);
        int maxHealth = ByteBufCodecs.VAR_INT.decode(buffer);
        return new PlacedBuildingInfo(id, structure, origin, rotation, sizeX, sizeY, sizeZ, level,
                name, costs, upgradeCosts, upgradeStructure, health, maxHealth);
    }

    public boolean canUpgrade() {
        return upgradeStructure != null;
    }

}
