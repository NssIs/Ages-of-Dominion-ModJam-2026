package com.hyrrx.forgottenrealmsrts;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Resolves structure IDs that are represented by Minecraft's own runtime templates.
 *
 * <p>The farm and house cards remain part of the RTS catalog, but their shape comes from the
 * vanilla plains-village pool when that template is available. Nothing from Minecraft is copied
 * into this mod: the server's loaded structure manager reads the game asset at placement time. The
 * small authored templates in the mod are retained as a safe fallback for a datapack or test
 * server that does not expose the vanilla village pool.
 */
public final class RtsStructureTemplates {
    private static final Identifier VANILLA_PLAINS_FARM = Identifier.fromNamespaceAndPath(
            "minecraft", "village/plains/houses/plains_small_farm_1");
    private static final Identifier VANILLA_PLAINS_HOUSE = Identifier.fromNamespaceAndPath(
            "minecraft", "village/plains/houses/plains_small_house_1");

    private RtsStructureTemplates() {
    }

    /** Gets a template, preferring the live vanilla design for the farm and house cards. */
    public static Optional<StructureTemplate> get(MinecraftServer server, Identifier requested) {
        if (server == null || requested == null) {
            return Optional.empty();
        }

        Identifier runtime = runtimeId(requested);
        Optional<StructureTemplate> resolved = server.getStructureManager().get(runtime);
        if (resolved.isPresent()) {
            return resolved;
        }
        return server.getStructureManager().get(requested);
    }

    /** Level convenience overload used by the worker and population systems. */
    public static Optional<StructureTemplate> get(ServerLevel level, Identifier requested) {
        return level == null ? Optional.empty() : get(level.getServer(), requested);
    }

    /** The live template used for a catalog entry, or the entry itself when no alias applies. */
    public static Identifier runtimeId(Identifier requested) {
        if (requested == null) {
            return null;
        }
        String path = requested.getPath();
        if (path.contains("villagers/town/farm")) {
            return VANILLA_PLAINS_FARM;
        }
        if (path.contains("villagers/town/house")) {
            return VANILLA_PLAINS_HOUSE;
        }
        return requested;
    }
}
