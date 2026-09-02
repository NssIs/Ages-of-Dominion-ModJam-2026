package com.hyrrx.forgottenrealmsrts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The display name and price of every building, read from
 * {@code data/forgotten_realms_rts/buildings.json}.
 *
 * <p><strong>Server-side only, and deliberately not a reload listener.</strong> The catalog is sent
 * to clients on every {@code /game activate}, so a fresh copy of this data reaches them without a
 * resource-pack reload hook; loading it once when the server starts is the whole requirement.
 *
 * <p>An unlisted structure is not an error. It gets a zero cost and the name its folder implies, so
 * dropping a new {@code .nbt} in and seeing it appear works before anyone writes a price for it.
 */
public final class BuildingCosts {
    /** Name, per-resource cost and per-cycle production indexed by
     *  {@link com.hyrrx.forgottenrealmsrts.Resource}, the minimum age required to build it, and the
     *  town-hall flag. */
    public record Definition(String name, int[] costs, int[] produces, int minAge, boolean townHall) {
        public boolean free() {
            for (int cost : costs) {
                if (cost > 0) {
                    return false;
                }
            }
            return true;
        }

        /** Whether this building type adds anything to the stockpiles each production cycle. */
        public boolean producesAnything() {
            for (int amount : produces) {
                if (amount > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final Identifier FILE =
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "buildings.json");

    /** Keyed by the structure path with the level suffix dropped, e.g. {@code villagers/town/hall}. */
    private static Map<String, Definition> definitions = Map.of();

    private BuildingCosts() {
    }

    public static void load(MinecraftServer server) {
        Map<String, Definition> loaded = new HashMap<>();
        ResourceManager resources = server.getResourceManager();
        Optional<Resource> found = resources.getResource(FILE);
        if (found.isEmpty()) {
            ForgottenRealmsRTS.LOGGER.warn("No {} found; every building will be free and unnamed.", FILE);
            definitions = Map.of();
            return;
        }

        try (BufferedReader reader = found.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                // The file documents itself in a "_comment" key; skip anything not an object.
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                loaded.put(entry.getKey(), parse(entry.getValue().getAsJsonObject()));
            }
        } catch (Exception exception) {
            ForgottenRealmsRTS.LOGGER.error("Could not read {}; buildings will be free and unnamed.",
                    FILE, exception);
            definitions = Map.of();
            return;
        }

        definitions = Map.copyOf(loaded);
        ForgottenRealmsRTS.LOGGER.info("Loaded {} building definitions.", definitions.size());
    }

    private static Definition parse(JsonObject object) {
        int[] costs = new int[com.hyrrx.forgottenrealmsrts.Resource.COUNT];
        for (com.hyrrx.forgottenrealmsrts.Resource resource : com.hyrrx.forgottenrealmsrts.Resource.VALUES) {
            costs[resource.ordinal()] = object.has(resource.key())
                    ? object.get(resource.key()).getAsInt()
                    : 0;
        }
        int[] produces = new int[com.hyrrx.forgottenrealmsrts.Resource.COUNT];
        if (object.has("produces") && object.get("produces").isJsonObject()) {
            JsonObject income = object.getAsJsonObject("produces");
            for (com.hyrrx.forgottenrealmsrts.Resource resource : com.hyrrx.forgottenrealmsrts.Resource.VALUES) {
                produces[resource.ordinal()] = income.has(resource.key())
                        ? income.get(resource.key()).getAsInt()
                        : 0;
            }
        }
        String name = object.has("name") ? object.get("name").getAsString() : "";
        int minAge = object.has("minAge") ? object.get("minAge").getAsInt() : 0;
        boolean townHall = object.has("townHall") && object.get("townHall").getAsBoolean();
        return new Definition(name, costs, produces, minAge, townHall);
    }

    /**
     * The definition for a structure, falling back to a free, folder-named one so an unlisted
     * building still works.
     */
    public static Definition get(Identifier structure) {
        String key = keyOf(structure);
        Definition definition = definitions.get(key);
        if (definition != null && !definition.name().isEmpty()) {
            return definition;
        }
        int[] costs = definition != null
                ? definition.costs()
                : new int[com.hyrrx.forgottenrealmsrts.Resource.COUNT];
        int[] produces = definition != null
                ? definition.produces()
                : new int[com.hyrrx.forgottenrealmsrts.Resource.COUNT];
        int minAge = definition != null ? definition.minAge() : 0;
        boolean townHall = definition != null && definition.townHall();
        return new Definition(fallbackName(structure), costs, produces, minAge, townHall);
    }

    /** Returns the effective level-one placement price for a player. */
    public static int[] placementCost(Identifier structure, Player player) {
        Definition definition = get(structure);
        if (isFirstCoalMinePlacement(structure, player)) {
            return new int[com.hyrrx.forgottenrealmsrts.Resource.COUNT];
        }
        return Arrays.copyOf(definition.costs(), com.hyrrx.forgottenrealmsrts.Resource.COUNT);
    }

    /**
     * Returns the exact price for the requested upgrade target. Upgrade prices are deliberately
     * derived from the target tier so the catalog, HUD and server charge cannot drift apart.
     */
    public static int[] upgradeCost(Identifier target) {
        Definition definition = get(target);
        int level = com.hyrrx.forgottenrealmsrts.network.ModPayloads.levelOf(target);
        if (definition.townHall()) {
            if (level == 2) {
                return new int[] {250, 200, 100, 75, 150, 50};
            }
            if (level == 3) {
                return new int[] {500, 400, 250, 200, 300, 150};
            }
        }

        int multiplier = level == 2 ? 3 : level == 3 ? 5 : 1;
        int[] cost = Arrays.copyOf(definition.costs(), com.hyrrx.forgottenrealmsrts.Resource.COUNT);
        for (int index = 0; index < cost.length; index++) {
            long scaled = (long) Math.max(0, cost[index]) * multiplier;
            cost[index] = (int) Math.min(Integer.MAX_VALUE, scaled);
        }
        return cost;
    }

    /** The Coal Mine entry is the only special onboarding building. */
    public static boolean isCoalMine(Identifier structure) {
        return structure != null && "coal".equals(
                com.hyrrx.forgottenrealmsrts.network.ModPayloads.buildingOf(structure));
    }

    /** Only a level-one Coal Mine completes the free onboarding step. */
    public static boolean isFirstCoalMinePlacement(Identifier structure, Player player) {
        return player != null && isCoalMine(structure)
                && com.hyrrx.forgottenrealmsrts.network.ModPayloads.levelOf(structure) == 1
                && !RtsEconomy.coalMinePlaced(player);
    }

    /** Category + building, with the level suffix dropped — the shape the JSON is keyed by. */
    private static String keyOf(Identifier structure) {
        String category = com.hyrrx.forgottenrealmsrts.network.ModPayloads.categoryOf(structure);
        String building = com.hyrrx.forgottenrealmsrts.network.ModPayloads.buildingOf(structure);
        return category == null ? building : category + "/" + building;
    }

    private static String fallbackName(Identifier structure) {
        String raw = com.hyrrx.forgottenrealmsrts.network.ModPayloads.buildingOf(structure)
                .replace('_', ' ');
        if (raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
