package com.hyrrx.forgottenrealmsrts;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The survival side of the game: how much punishment the Town Hall has left, and whether the
 * campaign has been won or lost.
 *
 * <p>Synced so the HUD can draw an integrity bar and so the client can present the victory/defeat
 * moment. Everything the night invasion ({@link RtsInvasion}) does resolves into these two numbers:
 * enemies reaching the hall lower integrity, holding out to the final day raises the outcome to a
 * win, integrity hitting zero raises it to a loss.
 */
public final class RtsBattle {
    /** Base durability of a level-one Town Hall. Higher structure tiers scale this dramatically. */
    public static final int MAX_INTEGRITY = 100;

    /** Ongoing / won / lost, as an int so it can be a trivially synced attachment. */
    public static final int OUTCOME_ONGOING = 0;
    public static final int OUTCOME_VICTORY = 1;
    public static final int OUTCOME_DEFEAT = 2;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ForgottenRealmsRTS.MOD_ID);

    private static final Supplier<AttachmentType<Integer>> INTEGRITY = ATTACHMENT_TYPES.register(
            "town_hall_integrity",
            () -> AttachmentType.builder(() -> MAX_INTEGRITY)
                    .serialize(Codec.INT.fieldOf("town_hall_integrity"))
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());

    /** Synced maximum so the HUD and repair/results screens agree with the upgraded Town Hall. */
    private static final Supplier<AttachmentType<Integer>> MAX_INTEGRITY_DATA = ATTACHMENT_TYPES.register(
            "town_hall_max_integrity",
            () -> AttachmentType.builder(() -> MAX_INTEGRITY)
                    .serialize(Codec.INT.fieldOf("town_hall_max_integrity"))
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());

    private static final Supplier<AttachmentType<Integer>> OUTCOME = ATTACHMENT_TYPES.register(
            "campaign_outcome",
            () -> AttachmentType.builder(() -> OUTCOME_ONGOING)
                    .serialize(Codec.INT.fieldOf("campaign_outcome"))
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());

    private RtsBattle() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static int integrity(Player player) {
        return player.getData(INTEGRITY);
    }

    public static int maxIntegrity(Player player) {
        return Math.max(MAX_INTEGRITY, player.getData(MAX_INTEGRITY_DATA));
    }

    public static void setIntegrity(Player player, int value) {
        int clamped = Math.max(0, Math.min(maxIntegrity(player), value));
        player.setData(INTEGRITY, clamped);
        syncTownHallBuilding(player);
    }

    /**
     * Returns the intentionally dramatic durability for a Town Hall structure tier. A new tier is
     * a rebuilt hall, so callers fully repair it when the upgrade completes.
     */
    public static int maxIntegrityForLevel(int level) {
        int tier = Math.max(1, Math.min(3, level));
        return switch (tier) {
            case 2 -> 1_000;
            case 3 -> 10_000;
            default -> MAX_INTEGRITY;
        };
    }

    /** Applies a Town Hall tier's durability once its replacement structure has been placed. */
    public static void applyTownHallUpgrade(Player player, int level) {
        int upgradedMax = maxIntegrityForLevel(level);
        if (upgradedMax <= maxIntegrity(player)) {
            syncTownHallBuilding(player);
            return;
        }
        player.setData(MAX_INTEGRITY_DATA, upgradedMax);
        setIntegrity(player, upgradedMax);
    }

    /** Reconciles a saved Town Hall tier when an older world is opened after this health change. */
    public static void refreshTownHallLevel(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int highestLevel = 1;
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            if (!entry.owner().equals(player.getUUID())
                    || !BuildingCosts.get(entry.structure()).townHall()) {
                continue;
            }
            highestLevel = Math.max(highestLevel,
                    com.hyrrx.forgottenrealmsrts.network.ModPayloads.levelOf(entry.structure()));
        }
        int expectedMax = maxIntegrityForLevel(highestLevel);
        if (expectedMax > maxIntegrity(player)) {
            player.setData(MAX_INTEGRITY_DATA, expectedMax);
            setIntegrity(player, expectedMax);
        } else {
            player.setData(MAX_INTEGRITY_DATA, expectedMax);
            setIntegrity(player, integrity(player));
        }
        syncTownHallBuilding(player);
    }

    /** Keeps the tracked Town Hall entry exactly aligned with the synced campaign integrity. */
    public static void syncTownHallBuilding(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int health = integrity(player);
        int maxHealth = maxIntegrity(player);
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            if (entry.owner().equals(player.getUUID())
                    && BuildingCosts.get(entry.structure()).townHall()
                    && (entry.health() != health || entry.maxHealth() != maxHealth)) {
                RtsBuildingStore.get(level).update(new RtsBuildingStore.Entry(
                        entry.id(), entry.owner(), entry.structure(), entry.origin(), entry.rotation(),
                        entry.normalizedOrigin(), health, maxHealth));
                break;
            }
        }
    }

    public static int outcome(Player player) {
        return player.getData(OUTCOME);
    }

    public static void setOutcome(Player player, int outcome) {
        player.setData(OUTCOME, outcome);
    }

    /** Starts a fresh campaign after the previous Town Hall has been cleared. */
    public static void reset(Player player) {
        player.setData(MAX_INTEGRITY_DATA, MAX_INTEGRITY);
        setIntegrity(player, MAX_INTEGRITY);
        setOutcome(player, OUTCOME_ONGOING);
    }
}
