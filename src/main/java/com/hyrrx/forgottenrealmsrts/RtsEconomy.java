package com.hyrrx.forgottenrealmsrts;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A player's stockpiles, population and build progress.
 *
 * <p>The realm begins with a restrained one-time starter stockpile when the civilization is founded.
 * The Town Hall itself produces nothing; ongoing wood comes from physical workers and other income
 * comes from the economic buildings that explicitly provide it.
 *
 * <p>All of it is <strong>synced</strong> data attachments, following the same pattern
 * {@link RtsMode} established. That is what lets the top bar read these values directly on the
 * client with no payload of its own — the sync is the transport.
 */
public final class RtsEconomy {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ForgottenRealmsRTS.MOD_ID);

    /** One attachment per resource, indexed by {@link Resource#ordinal()}. */
    private static final List<Supplier<AttachmentType<Integer>>> STOCKPILES = new ArrayList<>();

    static {
        for (Resource resource : Resource.VALUES) {
            STOCKPILES.add(intAttachment("stock_" + resource.key()));
        }
    }

    private static final Supplier<AttachmentType<Integer>> POPULATION = intAttachment("population");
    private static final Supplier<AttachmentType<Integer>> POPULATION_CAP = intAttachment("population_cap");
    /** Live count of the realm's guardians, recomputed by {@link RtsInvasion} each cycle. */
    private static final Supplier<AttachmentType<Integer>> MILITARY = intAttachment("military");
    private static final Supplier<AttachmentType<Boolean>> TOWN_WORKER_SPAWNED = ATTACHMENT_TYPES.register(
            "town_worker_spawned",
            () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("town_worker_spawned"))
                    .sync(ByteBufCodecs.BOOL)
                    .copyOnDeath()
                    .build()
    );

    /**
     * Whether this player has founded their town yet. Gates every other building — see
     * {@code BuildingCosts}.
     */
    private static final Supplier<AttachmentType<Boolean>> TOWN_HALL_PLACED = ATTACHMENT_TYPES.register(
            "town_hall_placed",
            () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("town_hall_placed"))
                    .sync(ByteBufCodecs.BOOL)
                    .copyOnDeath()
                    .build()
    );

    /** Whether the player's onboarding Coal Mine has been successfully placed. */
    private static final Supplier<AttachmentType<Boolean>> COAL_MINE_PLACED = ATTACHMENT_TYPES.register(
            "coal_mine_placed",
            () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("coal_mine_placed"))
                    .sync(ByteBufCodecs.BOOL)
                    .copyOnDeath()
                    .build()
    );

    private static Supplier<AttachmentType<Integer>> intAttachment(String name) {
        return ATTACHMENT_TYPES.register(
                name,
                () -> AttachmentType.builder(() -> 0)
                        .serialize(Codec.INT.fieldOf(name))
                        .sync(ByteBufCodecs.VAR_INT)
                        .copyOnDeath()
                        .build()
        );
    }

    private RtsEconomy() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static int stock(Player player, Resource resource) {
        return player.getData(STOCKPILES.get(resource.ordinal()));
    }

    public static void setStock(Player player, Resource resource, int amount) {
        player.setData(STOCKPILES.get(resource.ordinal()), Math.max(0, amount));
    }

    public static int population(Player player) {
        return player.getData(POPULATION);
    }

    public static int populationCap(Player player) {
        return player.getData(POPULATION_CAP);
    }

    public static void setPopulation(Player player, int population, int cap) {
        player.setData(POPULATION, Math.max(0, population));
        player.setData(POPULATION_CAP, Math.max(0, cap));
    }

    public static int military(Player player) {
        return player.getData(MILITARY);
    }

    public static void setMilitary(Player player, int count) {
        player.setData(MILITARY, Math.max(0, count));
    }

    public static boolean townWorkerSpawned(Player player) {
        return player.getData(TOWN_WORKER_SPAWNED);
    }

    public static void setTownWorkerSpawned(Player player, boolean spawned) {
        player.setData(TOWN_WORKER_SPAWNED, spawned);
    }

    public static boolean townHallPlaced(Player player) {
        return player.getData(TOWN_HALL_PLACED);
    }

    public static void setTownHallPlaced(Player player, boolean placed) {
        player.setData(TOWN_HALL_PLACED, placed);
    }

    public static boolean coalMinePlaced(Player player) {
        return player.getData(COAL_MINE_PLACED);
    }

    public static void setCoalMinePlaced(Player player, boolean placed) {
        player.setData(COAL_MINE_PLACED, placed);
    }

    /**
     * Migrates established tracked towns into the onboarding state without relocking them. A save
     * that has only a Town Hall remains in the new Coal Mine step; any older tracked building means
     * the player has already progressed past that step.
     */
    public static void migrateProgression(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        boolean hasTownHall = false;
        boolean hasEstablishedBuilding = false;
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            if (!entry.owner().equals(player.getUUID())) {
                continue;
            }
            if (BuildingCosts.get(entry.structure()).townHall()) {
                hasTownHall = true;
            } else {
                hasEstablishedBuilding = true;
            }
        }

        if (hasTownHall && !townHallPlaced(player)) {
            setTownHallPlaced(player, true);
        }
        if (hasEstablishedBuilding && !coalMinePlaced(player)) {
            setCoalMinePlaced(player, true);
        }
    }

    /** Gives a newly founded realm a modest, normal opening cache without making the Town Hall an ATM. */
    public static void grantStartingStock(Player player) {
        // The opening gold seeds the first age-up; the Dark Age Gold Mine is age-gated, so a
        // zero balance here would make the first civilization upgrade impossible to earn.
        int[] starting = {100, 50, 20, 20, 100, 0};
        for (Resource resource : Resource.VALUES) {
            setStock(player, resource, starting[resource.ordinal()]);
        }
    }

    /** Whether the player holds at least {@code costs} of everything. */
    public static boolean canAfford(Player player, int[] costs) {
        for (Resource resource : Resource.VALUES) {
            if (stock(player, resource) < costs[resource.ordinal()]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deducts a cost. Callers must have checked {@link #canAfford} first — this clamps at zero
     * rather than going negative, so a missed check would silently give the building away instead of
     * corrupting the stockpile.
     */
    public static void spend(Player player, int[] costs) {
        for (Resource resource : Resource.VALUES) {
            setStock(player, resource, stock(player, resource) - costs[resource.ordinal()]);
        }
    }

    /** Clears the old town's stockpiles, population, and bookkeeping for a new campaign. */
    public static void reset(Player player) {
        for (Resource resource : Resource.VALUES) {
            setStock(player, resource, 0);
        }
        setPopulation(player, 0, 0);
        setMilitary(player, 0);
        setTownWorkerSpawned(player, false);
        setTownHallPlaced(player, false);
        setCoalMinePlaced(player, false);
    }
}
