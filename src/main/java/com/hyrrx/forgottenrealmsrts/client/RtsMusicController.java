package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;

/** Plays the mod's licensed campaign score, looping continuously, only while the RTS mode is active. */
public final class RtsMusicController {
    /** A tiny hand-off lets the sound manager release the finished stream without audible silence. */
    private static final int RESTART_DELAY_TICKS = 2;
    /** Fixed order makes the playlist deterministic and guarantees no immediate repeat. */
    private static final List<DeferredHolder<SoundEvent, SoundEvent>> TRACKS = List.of(
            ModSounds.MUSIC_CASTLE_OF_THE_ANCIENT_KINGS,
            ModSounds.MUSIC_FINAL_BATTLE_OF_THE_LOST_KINGDOM_1,
            ModSounds.MUSIC_FINAL_BATTLE_OF_THE_LOST_KINGDOM_2,
            ModSounds.MUSIC_FINAL_BOSS,
            ModSounds.MUSIC_HEROIC_BATTLE_ON_THE_PLAINS,
            ModSounds.MUSIC_MEDIEVAL_VILLAGE_IN_PEACE,
            ModSounds.MUSIC_SAILORS_OF_THE_NORTH_1,
            ModSounds.MUSIC_SONG_OF_THE_VALLEY_TAVERN_1,
            ModSounds.MUSIC_THE_HEROS_JOURNEY_1,
            ModSounds.MUSIC_THE_LIGHT_OF_THE_KINGDOM_1,
            ModSounds.MUSIC_TRIBUTE_TO_THE_HERO);

    private static SoundInstance current;
    private static int restartDelay;
    private static int nextTrack;

    private RtsMusicController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsMusicController::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        // No world is loaded, i.e. we are at the main menu (or any pre-world screen). Keep this
        // silent unconditionally, regardless of RtsMode, so the mod never plays a note before the
        // player has actually joined a world.
        if (minecraft.player == null || minecraft.level == null) {
            minecraft.getMusicManager().stopPlaying();
            stop(minecraft);
            return;
        }

        if (!RtsMode.isActive(minecraft.player)) {
            // Ordinary (non-RTS) play: leave vanilla's own music alone. Previously the "screen open"
            // check below ran unconditionally here too, so opening an inventory in a normal save
            // silenced vanilla's biome music for no reason - this mod only owns its own stream.
            stop(minecraft);
            return;
        }

        // RTS mode is active: the campaign score owns the music channel regardless of which screen
        // (pause menu, inventory, an RTS GUI...) is open. Previously `minecraft.screen != null` was
        // part of the early-return above, which stopped the current stream the instant any screen
        // opened; since the SoundInstance was stopped rather than paused, closing the screen then
        // rolled a brand new random track instead of resuming the loop.
        minecraft.getMusicManager().stopPlaying();
        SoundManager soundManager = minecraft.getSoundManager();
        if (current != null) {
            if (soundManager.isActive(current)) {
                return;
            }
            // Stop the ended instance explicitly before selecting another event. This is harmless
            // after natural completion and closes the overlap window during screen/mode changes.
            soundManager.stop(current);
            current = null;
            restartDelay = RESTART_DELAY_TICKS;
        }
        if (restartDelay > 0) {
            restartDelay--;
            return;
        }

        current = playNextTrack(soundManager);
    }

    /**
     * Starts exactly one of the individually registered streamed events. The fixed sequence avoids
     * the sound engine's weighted multi-file reroll and makes every transition one instance out,
     * then one instance in.
     */
    private static SoundInstance playNextTrack(SoundManager soundManager) {
        if (TRACKS.isEmpty()) {
            return null;
        }
        if (current != null) {
            soundManager.stop(current);
        }
        SoundInstance next = SimpleSoundInstance.forMusic(TRACKS.get(nextTrack).get());
        nextTrack = (nextTrack + 1) % TRACKS.size();
        soundManager.play(next);
        return next;
    }

    private static void stop(Minecraft minecraft) {
        if (current != null) {
            minecraft.getSoundManager().stop(current);
            current = null;
        }
        restartDelay = RESTART_DELAY_TICKS;
    }
}
