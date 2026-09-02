package com.hyrrx.forgottenrealmsrts;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The player's civilization identity: whether it has been founded, its name, and its banner.
 *
 * <p>Founding is the first real beat of the game — see {@code FoundingScreen}. Placing the first
 * Town Hall opens the founding screen; the civilization is not {@link #isFounded founded} until the
 * player confirms a name and a flag, and only then does the first worker spawn
 * ({@link RtsEntities#ensureTownWorker}). The first player-placed Coal Mine is the final onboarding
 * step before the rest of the build menu unlocks.
 *
 * <p>All synced data attachments, following {@link RtsMode} and {@link RtsEconomy}: the client reads
 * the name and flag straight off these to paint the HUD, with no payload of its own.
 */
public final class RtsCivilization {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ForgottenRealmsRTS.MOD_ID);

    private static final Supplier<AttachmentType<Boolean>> FOUNDED = ATTACHMENT_TYPES.register(
            "civ_founded",
            () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("civ_founded"))
                    .sync(ByteBufCodecs.BOOL)
                    .copyOnDeath()
                    .build());

    private static final Supplier<AttachmentType<String>> NAME = ATTACHMENT_TYPES.register(
            "civ_name",
            () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING.fieldOf("civ_name"))
                    .sync(ByteBufCodecs.STRING_UTF8)
                    .copyOnDeath()
                    .build());

    private static final Supplier<AttachmentType<FlagDesign>> FLAG = ATTACHMENT_TYPES.register(
            "civ_flag",
            () -> AttachmentType.builder(() -> FlagDesign.DEFAULT)
                    .serialize(FlagDesign.CODEC.fieldOf("civ_flag"))
                    .sync(FlagDesign.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    /** 0 = Dark Age, 1 = Feudal Age, 2 = Castle Age. Synced so the HUD and tray can read it. */
    private static final Supplier<AttachmentType<Integer>> AGE = ATTACHMENT_TYPES.register(
            "civ_age",
            () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("civ_age"))
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());

    /** Whether the ancient ruins have been raised around this realm yet. Persisted, not synced. */
    private static final Supplier<AttachmentType<Boolean>> RUINS_PLACED = ATTACHMENT_TYPES.register(
            "ruins_placed",
            () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("ruins_placed"))
                    .copyOnDeath()
                    .build());

    /** Relics recovered from the ages that fell before this one — the theme's "echoes of the past". */
    private static final Supplier<AttachmentType<Integer>> RELICS = ATTACHMENT_TYPES.register(
            "civ_relics",
            () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("civ_relics"))
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());

    /** Display names of the ages, indexed by {@link #age}. */
    public static final String[] AGE_NAMES = { "Dark Age", "Feudal Age", "Castle Age" };
    /** Roman numeral shown in the civilisation panel, indexed by {@link #age}. */
    public static final String[] AGE_NUMERALS = { "I", "II", "III" };
    public static final int MAX_AGE = AGE_NAMES.length - 1;

    /** Relic names, unearthed one per age advanced. */
    public static final String[] RELIC_NAMES = {
            "Banner of the Last Legion", "Crown of the Fallen King", "Hammer of the First Mason" };
    /** Each recovered relic adds this fraction to the realm's production. */
    public static final double RELIC_PRODUCTION_BONUS = 0.10D;

    private RtsCivilization() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static boolean isFounded(Player player) {
        return player.getData(FOUNDED);
    }

    public static String name(Player player) {
        return player.getData(NAME);
    }

    public static FlagDesign flag(Player player) {
        return player.getData(FLAG);
    }

    /** Records the confirmed identity and marks the civilization founded. */
    public static void found(Player player, String name, FlagDesign flag) {
        player.setData(NAME, name);
        player.setData(FLAG, flag.sanitized());
        player.setData(FOUNDED, Boolean.TRUE);
    }

    /** Clears the identity of a fallen realm before the player starts a replacement town. */
    public static void reset(Player player) {
        player.setData(FOUNDED, Boolean.FALSE);
        player.setData(NAME, "");
        player.setData(FLAG, FlagDesign.DEFAULT);
        player.setData(AGE, 0);
        player.setData(RUINS_PLACED, Boolean.FALSE);
        player.setData(RELICS, 0);
    }

    public static int age(Player player) {
        return player.getData(AGE);
    }

    public static void setAge(Player player, int age) {
        player.setData(AGE, Math.max(0, Math.min(MAX_AGE, age)));
    }

    public static String ageName(Player player) {
        return AGE_NAMES[Math.max(0, Math.min(MAX_AGE, age(player)))];
    }

    public static String ageNumeral(Player player) {
        return AGE_NUMERALS[Math.max(0, Math.min(MAX_AGE, age(player)))];
    }

    /**
     * The resource price for the next age, shared by the server validation and the live upgrade
     * card. Keeping this here prevents the client from drifting away from the authoritative costs.
     */
    public static int[] advanceCost(int fromAge) {
        int[] cost = new int[Resource.COUNT];
        if (fromAge == 0) { // Dark -> Feudal
            cost[Resource.WOOD.ordinal()] = 150;
            cost[Resource.FOOD.ordinal()] = 120;
            cost[Resource.GOLD.ordinal()] = 20;
        } else if (fromAge == 1) { // Feudal -> Castle
            cost[Resource.STONE.ordinal()] = 250;
            cost[Resource.FOOD.ordinal()] = 240;
            cost[Resource.GOLD.ordinal()] = 150;
            cost[Resource.IRON.ordinal()] = 80;
        }
        return cost;
    }

    public static boolean ruinsPlaced(Player player) {
        return player.getData(RUINS_PLACED);
    }

    public static void setRuinsPlaced(Player player, boolean placed) {
        player.setData(RUINS_PLACED, placed);
    }

    public static int relics(Player player) {
        return player.getData(RELICS);
    }

    /** Records one more recovered relic and returns its name (for the announcement). */
    public static String recoverRelic(Player player) {
        int index = relics(player);
        player.setData(RELICS, index + 1);
        return RELIC_NAMES[Math.min(index, RELIC_NAMES.length - 1)];
    }
}
