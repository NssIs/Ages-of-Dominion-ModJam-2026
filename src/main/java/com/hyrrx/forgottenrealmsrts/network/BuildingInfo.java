package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.Resource;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * One entry in the buildings tray: which structure, what it is called, and what it costs.
 *
 * <p>Costs are a fixed {@link Resource#COUNT}-length array indexed by {@link Resource#ordinal()},
 * not a map — the whole point of the enum carrying its wire position is that this codec stays six
 * varints with no keys. The minimum civilization age travels with the entry so the client can show
 * the same lock the server enforces.
 */
public record BuildingInfo(Identifier id, String name, int[] costs, boolean townHall, int minAge) {
    /** Compatibility constructor for locally synthesized upgrade/building entries. */
    public BuildingInfo(Identifier id, String name, int[] costs, boolean townHall) {
        this(id, name, costs, townHall, 0);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingInfo> STREAM_CODEC =
            StreamCodec.of(BuildingInfo::write, BuildingInfo::read);

    private static void write(RegistryFriendlyByteBuf buffer, BuildingInfo info) {
        buffer.writeIdentifier(info.id());
        buffer.writeUtf(info.name());
        for (int cost : info.costs()) {
            buffer.writeVarInt(cost);
        }
        buffer.writeBoolean(info.townHall());
        buffer.writeVarInt(Math.max(0, info.minAge()));
    }

    private static BuildingInfo read(RegistryFriendlyByteBuf buffer) {
        Identifier id = buffer.readIdentifier();
        String name = buffer.readUtf();
        int[] costs = new int[Resource.COUNT];
        for (int i = 0; i < costs.length; i++) {
            costs[i] = buffer.readVarInt();
        }
        return new BuildingInfo(id, name, costs, buffer.readBoolean(), buffer.readVarInt());
    }

    public int cost(Resource resource) {
        return costs[resource.ordinal()];
    }

    public boolean free() {
        for (int cost : costs) {
            if (cost > 0) {
                return false;
            }
        }
        return true;
    }
}
