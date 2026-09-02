package com.hyrrx.forgottenrealmsrts;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative state for viewing a defeated town.
 *
 * <p>Spectating uses Minecraft's own server tick-rate manager. That freezes entity, block, clock,
 * and invasion simulation for the whole server while the defeated realm is being inspected; the
 * spectator player itself still ticks, so the camera and the restart control remain responsive.</p>
 */
public final class RtsSpectateState {
    private static final Set<UUID> SPECTATORS = new HashSet<>();
    private static MinecraftServer frozenServer;
    private static boolean serverWasFrozen;

    private RtsSpectateState() {
    }

    public static void enter(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        if (frozenServer != null && frozenServer != server) {
            SPECTATORS.clear();
            frozenServer = null;
        }

        if (SPECTATORS.add(player.getUUID())) {
            if (SPECTATORS.size() == 1) {
                frozenServer = server;
                serverWasFrozen = server.tickRateManager().isFrozen();
                server.tickRateManager().setFrozen(true);
            }
        }
    }

    public static boolean isFrozen(ServerPlayer player) {
        return SPECTATORS.contains(player.getUUID());
    }

    public static void exit(ServerPlayer player) {
        if (!SPECTATORS.remove(player.getUUID()) || !SPECTATORS.isEmpty()) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            server = frozenServer;
        }
        if (server != null && server == frozenServer) {
            server.tickRateManager().setFrozen(serverWasFrozen);
        }
        frozenServer = null;
    }
}
