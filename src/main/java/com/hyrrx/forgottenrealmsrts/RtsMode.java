package com.hyrrx.forgottenrealmsrts;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Whether a player is currently inside the RTS overlay (forced spectator, isometric camera, the
 * recruit and building HUD panels), and the real gamemode to restore when they leave it.
 *
 * <p>Defaults to active so the mod's original always-on behaviour is unchanged for anyone who
 * never touches {@code /game}. The active flag is synced to the client because the client-only
 * camera and mouse controllers read it every frame; the stored gamemode never needs to leave the
 * server, since only the {@code /game} command reads it.
 */
public final class RtsMode {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ForgottenRealmsRTS.MOD_ID);

    private static final Supplier<AttachmentType<Boolean>> ACTIVE = ATTACHMENT_TYPES.register(
            "rts_active",
            () -> AttachmentType.builder(() -> Boolean.TRUE)
                    .serialize(Codec.BOOL.fieldOf("active"))
                    .sync(ByteBufCodecs.BOOL)
                    .copyOnDeath()
                    .build()
    );

    private static final Supplier<AttachmentType<GameType>> PREVIOUS_GAME_TYPE = ATTACHMENT_TYPES.register(
            "rts_previous_game_type",
            () -> AttachmentType.builder(() -> GameType.SURVIVAL)
                    .serialize(GameType.CODEC.fieldOf("previous_game_type"))
                    .copyOnDeath()
                    .build()
    );

    /** Whether this player has completed the one-time first-join field guide. Persisted, never synced. */
    private static final Supplier<AttachmentType<Boolean>> WELCOMED = ATTACHMENT_TYPES.register(
            "welcomed",
            () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("welcomed"))
                    .copyOnDeath()
                    .build()
    );

    private RtsMode() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static boolean isActive(Player player) {
        return player.getData(ACTIVE);
    }

    public static void setActive(Player player, boolean active) {
        player.setData(ACTIVE, active);
    }

    public static GameType previousGameType(Player player) {
        return player.getData(PREVIOUS_GAME_TYPE);
    }

    public static void setPreviousGameType(Player player, GameType gameType) {
        player.setData(PREVIOUS_GAME_TYPE, gameType);
    }

    public static boolean welcomed(Player player) {
        return player.getData(WELCOMED);
    }

    public static void setWelcomed(Player player, boolean welcomed) {
        player.setData(WELCOMED, welcomed);
    }
}
