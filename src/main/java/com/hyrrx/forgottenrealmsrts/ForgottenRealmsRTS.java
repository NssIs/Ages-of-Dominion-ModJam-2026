package com.hyrrx.forgottenrealmsrts;

import org.slf4j.Logger;

import com.hyrrx.forgottenrealmsrts.client.ForgottenRealmsRTSClient;
import com.hyrrx.forgottenrealmsrts.command.GameCommand;
import com.hyrrx.forgottenrealmsrts.network.OpenGuidePayload;
import com.hyrrx.forgottenrealmsrts.network.OpenFoundingPayload;
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import com.hyrrx.forgottenrealmsrts.network.MoonStatePayload;
import com.hyrrx.forgottenrealmsrts.sound.ModSounds;
import com.hyrrx.forgottenrealmsrts.particle.BuildingActionBurstParticles;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.net.URL;

@Mod(ForgottenRealmsRTS.MOD_ID)
public final class ForgottenRealmsRTS {
    public static final String MOD_ID = "forgotten_realms_rts";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final float RTS_FLYING_SPEED = 0.16F;
    /** Vanilla's own default, read from a fresh instance rather than hardcoded, for `/game deactivate`. */
    private static final float DEFAULT_FLYING_SPEED = new Abilities().getFlyingSpeed();

    public ForgottenRealmsRTS(IEventBus modEventBus) {
        ModSounds.register(modEventBus);
        RtsMode.register(modEventBus);
        RtsEconomy.register(modEventBus);
        RtsCivilization.register(modEventBus);
        RtsBattle.register(modEventBus);
        RtsEntities.register(modEventBus);
        RtsItems.register(modEventBus);
        BuildingActionBurstParticles.PARTICLE_TYPES.register(modEventBus);
        ModPayloads.register(modEventBus);

        // Building names and prices are read once the server has its resource manager; the RTS
        // the day/night clock is normalized on the same edge.
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            BuildingCosts.load(event.getServer());
            RtsDayCycle.apply(event.getServer().overworld());
        });

        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerChangeGameMode);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onEntityJoinLevel);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            ForgottenRealmsRTSClient.register(modEventBus);
        }

        LOGGER.info("Ages of Dominion version {} loaded from {}.", modVersion(), loadSource());
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            enforceObserverState(player);
            if (RtsSpectateState.isFrozen(player)) {
                return;
            }
            RtsEntities.ensureTownWorker(player);
            if (player.tickCount % 20 == 0) {
                RtsBuildingDurability.migrate((net.minecraft.server.level.ServerLevel) player.level());
                ModPayloads.sendBuildingHealth(player);
            }
            RtsWorkerOrders.tick(player);
            RtsProduction.tick(player);
            RtsInvasion.tick(player);
            RtsDefenseDirector.tick(player);
            RtsWorld.tickWildlife(player);
            RtsWorld.tickDroppedItems(player);
        }
    }

    private void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && RtsMode.isActive(player)
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                && event.getNewGameMode() != GameType.SPECTATOR) {
            event.setCanceled(true);
            enforceObserverState(player);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        GameCommand.register(event.getDispatcher());
    }

    /** Prevents vanilla hostile mobs from diluting the RTS encounter roster. */
    private void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || !(event.getEntity() instanceof Monster monster)
                || !isVanillaMonster(monster.getType())
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }

        if (level.players().stream().anyMatch(RtsMode::isActive)) {
            event.setCanceled(true);
        }
    }

    private static boolean isVanillaMonster(EntityType<?> type) {
        return "minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace());
    }

    private static String modVersion() {
        try {
            return ModList.get().getModContainerById(MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private static String loadSource() {
        try {
            URL location = ForgottenRealmsRTS.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            String path = location.toString();
            if (path.endsWith(".jar")) {
                return "packaged JAR";
            }
            if (path.contains("/build/classes/") || path.contains("\\build\\classes\\")) {
                return "development classes";
            }
            return path;
        } catch (RuntimeException exception) {
            return "unknown source";
        }
    }

    /**
     * Puts every player back into the RTS overlay when they join.
     *
     * <p>The {@code rts_active} flag is serialized with the player, so before this existed a single
     * {@code /game deactivate} — which is exactly what you do to go and build a structure — meant
     * every subsequent login landed you outside the overlay, needing a manual
     * {@code /game activate}. That is not what a mod whose entire premise is "Minecraft is an RTS
     * now" should do on login.
     *
     * <p>So {@code /game deactivate} is a <strong>session</strong> escape hatch: it lasts until you
     * log out. If you would rather it be remembered across logins, delete this listener — the
     * attachment already persists on its own.
     */
    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RtsMode.setActive(player, true);
            RtsEconomy.migrateProgression(player);
            int scrubbedMarkers = RtsWorld.sanitizeOwnedBuildings((net.minecraft.server.level.ServerLevel) player.level(),
                    player.getUUID());
            if (scrubbedMarkers > 0) {
                LOGGER.info("Removed {} technical structure markers while loading {}'s realm.",
                        scrubbedMarkers, player.getName().getString());
            }
            RtsBuildingDurability.migrate((net.minecraft.server.level.ServerLevel) player.level());
            RtsBattle.refreshTownHallLevel(player);
            // Specialist jobs are part of the saved realm. Re-issue their first route after the
            // player returns so navigation does not remain in the completed/stale state saved at
            // logout; manual move/attack orders intentionally remain session-only strategy.
            RtsWorkerOrders.restoreAssignments(player);
            PacketDistributor.sendToPlayer(player, new MoonStatePayload(RtsInvasion.forcedMoon(player)));
            ModPayloads.sendBuildingHealth(player);
            if (!RtsMode.welcomed(player)) {
                PacketDistributor.sendToPlayer(player, new OpenGuidePayload());
                player.sendSystemMessage(Component.literal("Welcome to Ages of Dominion."));
                player.sendSystemMessage(Component.literal(
                        "Read the field guide, then place a Town Hall to found your civilization."));
            }
            // The Town Hall is persisted before the founding form is confirmed. Re-send the form on
            // login so leaving the world at that screen never strands a half-founded settlement.
            if (RtsEconomy.townHallPlaced(player) && !RtsCivilization.isFounded(player)) {
                PacketDistributor.sendToPlayer(player, new OpenFoundingPayload());
                player.sendSystemMessage(Component.literal(
                        "Your Town Hall is waiting. Finish naming your civilization to continue."));
            }
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RtsSpectateState.exit(player);
            RtsInvasion.clearForcedMoon(player);
        }
    }

    /** Forces the RTS observer overlay onto a player, if {@link RtsMode} says it should be active. */
    public static void enforceObserverState(ServerPlayer player) {
        if (!RtsMode.isActive(player)) {
            return;
        }

        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            RtsMode.setPreviousGameType(player, player.gameMode.getGameModeForPlayer());
            player.setGameMode(GameType.SPECTATOR);
        }

        player.setInvisible(true);
        if (Math.abs(player.getAbilities().getFlyingSpeed() - RTS_FLYING_SPEED) > 0.001F) {
            player.getAbilities().setFlyingSpeed(RTS_FLYING_SPEED);
            player.onUpdateAbilities();
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 255, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
    }

    /** Reverses {@link #enforceObserverState}: restores the player's real gamemode and undoes its side effects. */
    public static void releaseObserverState(ServerPlayer player) {
        RtsSpectateState.exit(player);
        RtsMode.setActive(player, false);
        player.setGameMode(RtsMode.previousGameType(player));
        player.setInvisible(false);
        player.getAbilities().setFlyingSpeed(DEFAULT_FLYING_SPEED);
        player.onUpdateAbilities();
        player.removeAllEffects();
    }
}
