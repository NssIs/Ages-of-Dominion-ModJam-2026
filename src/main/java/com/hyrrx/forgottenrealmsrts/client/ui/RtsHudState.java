package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import com.hyrrx.forgottenrealmsrts.BuildingPlacement;
import com.hyrrx.forgottenrealmsrts.Resource;
import com.hyrrx.forgottenrealmsrts.RtsBattle;
import com.hyrrx.forgottenrealmsrts.RtsCivilization;
import com.hyrrx.forgottenrealmsrts.RtsEconomy;
import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.RtsFarmOrders;
import com.hyrrx.forgottenrealmsrts.RtsMineOrders;
import com.hyrrx.forgottenrealmsrts.network.FarmStatusPayload;
import com.hyrrx.forgottenrealmsrts.network.BuildingInfo;
import com.hyrrx.forgottenrealmsrts.network.ConstructionInfo;
import com.hyrrx.forgottenrealmsrts.network.MineStatusPayload;
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import com.hyrrx.forgottenrealmsrts.network.PlacedBuildingInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * Everything the HUD displays, in one place, so the panels render <em>state</em> rather than
 * pictures with the numbers painted on.
 *
 * <p>This is the seam for a backend. Nothing in this class knows where the values come from: a
 * network payload handler, a REST poll, or the synced attachments below all look identical to the
 * renderers. The values here read straight off the synced {@code RtsEconomy}/{@code RtsCivilization}
 * attachments each frame, so no drawing code changes and no artwork is re-cut.
 *
 * <p>Client-side and single-threaded: only the render thread touches it.
 */
public final class RtsHudState {
    /**
     * One labelled figure in the civilisation panel. The icon's native size travels with it: the
     * glyphs cut out of the readout are not square (17x22, 16x22, ...), and blitting them as if
     * they were squashes them.
     */
    public record Stat(Identifier icon, int iconWidth, int iconHeight, String label, String value) {
    }


    /**
     * The top bar's stockpiles and headline stats, read straight off the synced
     * {@link RtsEconomy} attachments each frame.
     *
     * <p>These used to be a hardcoded {@code Resource[]} in {@code RtsTopBarHud} reading "1,250",
     * "980" and so on. They are real now, and because the realm starts with nothing, they all read
     * zero until there is something to gather with.
     */
    public static String resourceAmount(Resource resource) {
        Player player = Minecraft.getInstance().player;
        return player == null ? "0" : format(RtsEconomy.stock(player, resource));
    }

    public static String population() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return "0 / 0";
        }
        return RtsEconomy.population(player) + " / " + RtsEconomy.populationCap(player);
    }

    public static boolean townHallPlaced() {
        Player player = Minecraft.getInstance().player;
        return player != null && RtsEconomy.townHallPlaced(player);
    }

    public static boolean coalMinePlaced() {
        Player player = Minecraft.getInstance().player;
        return player != null && RtsEconomy.coalMinePlaced(player);
    }

    /** The one level-one Coal Mine that is free during fresh-realm onboarding. */
    public static boolean isFirstCoalMine(BuildingInfo building) {
        return building != null && "coal".equals(ModPayloads.buildingOf(building.id()))
                && ModPayloads.levelOf(building.id()) == 1 && !coalMinePlaced();
    }

    /**
     * A simple, understandable morale figure computed live from state — food in store, whether there
     * is housing headroom, and how battered the Town Hall is. No longer a decorative "0%".
     */
    public static String happiness() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return "0%";
        }
        int food = RtsEconomy.stock(player, Resource.FOOD);
        int population = RtsEconomy.population(player);
        int cap = RtsEconomy.populationCap(player);
        int happiness = 55;
        happiness += food > 20 ? 20 : (food == 0 ? -30 : 5);
        happiness += (cap > 0 && population < cap) ? 15 : (cap > 0 ? -15 : 0);
        happiness += (RtsBattle.integrity(player) - 50) / 5;
        return Math.max(0, Math.min(100, happiness)) + "%";
    }

    public static int stockOf(Resource resource) {
        Player player = Minecraft.getInstance().player;
        return player == null ? 0 : RtsEconomy.stock(player, resource);
    }

    /**
     * Whether this building can be started right now — affordable, and either the town hall itself
     * or placed after one. This is the client's copy of the server's rule; the server re-checks it.
     */
    public static boolean canBuild(BuildingInfo building) {
        return canBuild(building, 1);
    }

    /** Same gate for an expandable path/wall span, including an all-existing free span. */
    public static boolean canBuild(BuildingInfo building, int multiplier) {
        if (building == null || ageLocked(building)) {
            return false;
        }
        if (building.townHall() && townHallPlaced()) {
            return false;
        }
        if (!building.townHall() && !townHallPlaced()) {
            return false;
        }
        if (!building.townHall() && !founded()) {
            return false;
        }
        if (!building.townHall() && !coalMinePlaced() && !isFirstCoalMine(building)) {
            return false;
        }
        return canAfford(building, multiplier);
    }

    /** Shared client-side reason for a refused ordinary or linear placement. */
    public static String buildReason(BuildingInfo building, int multiplier) {
        if (building == null) {
            return BuildingPlacement.Result.UNKNOWN_STRUCTURE.message();
        }
        if (building.townHall() && townHallPlaced()) {
            return "Only one Town Hall allowed";
        }
        if (!building.townHall() && !townHallPlaced()) {
            return BuildingPlacement.Result.TOWN_HALL_REQUIRED.message();
        }
        Player player = Minecraft.getInstance().player;
        if (!building.townHall() && (player == null || !RtsCivilization.isFounded(player))) {
            return BuildingPlacement.Result.FOUNDING_REQUIRED.message();
        }
        if (!building.townHall() && !coalMinePlaced() && !isFirstCoalMine(building)) {
            return BuildingPlacement.Result.COAL_MINE_REQUIRED.message();
        }
        if (ageLocked(building)) {
            return ageRequirement(building);
        }
        if (!canAfford(building, multiplier)) {
            return BuildingPlacement.Result.UNAFFORDABLE.message();
        }
        return "";
    }

    /** Whether the current civilization is below a building's minimum age. */
    public static boolean ageLocked(BuildingInfo building) {
        Player player = Minecraft.getInstance().player;
        return building != null && (player == null || RtsCivilization.age(player) < building.minAge());
    }

    /** Full requirement text shared by the tray, tooltip, and placement refusal. */
    public static String ageRequirement(BuildingInfo building) {
        if (building == null || building.minAge() <= 0) {
            return "";
        }
        int required = Math.min(building.minAge(), RtsCivilization.MAX_AGE);
        return "Requires " + RtsCivilization.AGE_NAMES[required];
    }

    /** Short slot label that remains legible in the narrow building frame. */
    public static String ageRequirementShort(BuildingInfo building) {
        if (building == null || building.minAge() <= 0) {
            return "UNAVAILABLE";
        }
        int required = Math.min(building.minAge(), RtsCivilization.MAX_AGE);
        return "AGE " + RtsCivilization.AGE_NUMERALS[required];
    }

    /**
     * Short reason shown over a tray slot that cannot be placed yet. The Town Hall duplicate is
     * intentionally the only ordinary availability state called {@code LOCKED}; every other state
     * tells the player what to do next instead of making an affordable building look permanently
     * unavailable.
     */
    public static String availabilityLabel(BuildingInfo building) {
        if (building == null) {
            return "UNAVAILABLE";
        }
        if (building.townHall() && townHallPlaced()) {
            return "LOCKED";
        }
        if (!building.townHall() && !townHallPlaced()) {
            return "HALL FIRST";
        }
        if (!building.townHall() && !founded()) {
            return "FOUND FIRST";
        }
        if (!building.townHall() && !coalMinePlaced()) {
            return isFirstCoalMine(building) ? "FREE" : "COAL FIRST";
        }
        if (ageLocked(building)) {
            return ageRequirementShort(building);
        }
        if (!canAfford(building)) {
            return "UNAFFORDABLE";
        }
        return "UNAVAILABLE";
    }

    /** True when the live player has the resources for the next civilization age. */
    public static boolean canAdvanceAge() {
        Player player = Minecraft.getInstance().player;
        if (player == null || !RtsCivilization.isFounded(player)
                || RtsCivilization.age(player) >= RtsCivilization.MAX_AGE) {
            return false;
        }
        for (Resource resource : Resource.VALUES) {
            int cost = RtsCivilization.advanceCost(RtsCivilization.age(player))[resource.ordinal()];
            if (RtsEconomy.stock(player, resource) < cost) {
                return false;
            }
        }
        return true;
    }

    /** Whether the local player could pay for this building right now. */
    public static boolean canAfford(BuildingInfo building) {
        return canAfford(building, 1);
    }

    /** Whether the stockpiles cover a repeated path tile or wall segment price. */
    public static boolean canAfford(BuildingInfo building, int multiplier) {
        Player player = Minecraft.getInstance().player;
        if (player == null || building == null || multiplier < 0) {
            return false;
        }
        for (Resource resource : Resource.VALUES) {
            long cost = (long) buildingCost(building, resource) * multiplier;
            if (RtsEconomy.stock(player, resource) < cost) {
                return false;
            }
        }
        return true;
    }

    public static boolean canAffordUpgrade(PlacedBuildingInfo building) {
        if (building == null || !building.canUpgrade()) {
            return false;
        }
        Player player = Minecraft.getInstance().player;
        return player != null && canAffordCosts(player, building.upgradeCosts());
    }

    /** Effective price shown and charged for a level-one tray entry. */
    public static int buildingCost(BuildingInfo building, Resource resource) {
        return isFirstCoalMine(building) ? 0 : Math.max(0, building.cost(resource));
    }

    private static boolean founded() {
        Player player = Minecraft.getInstance().player;
        return player != null && RtsCivilization.isFounded(player);
    }

    public static boolean buildingFree(BuildingInfo building, int multiplier) {
        if (multiplier == 0) {
            return true;
        }
        for (Resource resource : Resource.VALUES) {
            if (buildingCost(building, resource) * (long) multiplier > 0) {
                return false;
            }
        }
        return true;
    }

    public static int upgradeCost(PlacedBuildingInfo building, Resource resource) {
        if (building == null || resource.ordinal() >= building.upgradeCosts().length) {
            return 0;
        }
        return Math.max(0, building.upgradeCosts()[resource.ordinal()]);
    }

    private static boolean canAffordCosts(Player player, int[] costs) {
        for (Resource resource : Resource.VALUES) {
            int index = resource.ordinal();
            int cost = index < costs.length ? Math.max(0, costs[index]) : 0;
            if (RtsEconomy.stock(player, resource) < cost) {
                return false;
            }
        }
        return true;
    }

    /** Thousands separators, so a five-figure stockpile stays readable in a 40px column. */
    public static String format(int amount) {
        return String.format("%,d", amount);
    }

    /** Formats half-heart values without rounding away the damage from a cursor hit. */
    public static String formatHealth(float amount) {
        float halfHearts = Math.round(amount * 2.0F) / 2.0F;
        int whole = (int) halfHearts;
        return halfHearts == whole ? Integer.toString(whole) : whole + ".5";
    }

    private RtsHudState() {
    }

    public static String ageNumeral() {
        Player player = Minecraft.getInstance().player;
        return player == null ? "I" : RtsCivilization.ageNumeral(player);
    }

    public static String ageName() {
        Player player = Minecraft.getInstance().player;
        return player == null ? "Dark Age" : RtsCivilization.ageName(player);
    }

    /**
     * The civilisation readout, computed live from synced state each frame — no longer the
     * hardcoded "1,850 / 1,320 / 32 / 13" it used to be. Economy Score is a weighted sum of the
     * stockpiles (metals count double); Workers is the real population. Military and its score stay
     * zero until there are soldiers to count.
     */
    public static List<Stat> stats() {
        Player player = Minecraft.getInstance().player;
        int military = player == null ? 0 : RtsEconomy.military(player);
        int workers = player == null ? 0
                : Math.max(0, RtsEconomy.population(player) - military);
        int maxIntegrity = player == null ? RtsBattle.MAX_INTEGRITY : RtsBattle.maxIntegrity(player);
        int integrity = player == null ? maxIntegrity : RtsBattle.integrity(player);
        int economyScore = 0;
        if (player != null) {
            economyScore = stockOf(Resource.WOOD) + stockOf(Resource.STONE) + stockOf(Resource.FOOD)
                    + 2 * stockOf(Resource.GOLD) + 2 * stockOf(Resource.IRON)
                    + 2 * stockOf(Resource.COAL);
        }
        return List.of(
                new Stat(icon("civ_shield"), 17, 17, "Town Hall",
                        integrity + " / " + maxIntegrity),
                new Stat(icon("civ_economy"), 17, 17, "Economy Score", format(economyScore)),
                new Stat(icon("civ_military_score"), 18, 18, "Military Score", format(military * 120)),
                new Stat(icon("civ_workers"), 17, 22, "Workers", format(workers)),
                new Stat(icon("civ_military"), 16, 22, "Military", format(military)));
    }

    /**
     * The buildings the server knows about, keyed by the lower-case category folder.
     *
     * <p>Filled by {@link #acceptCatalog} from {@code BuildingCatalogPayload}, which the client
     * requests once whenever RTS mode turns on. Empty until that round trip completes, and empty
     * forever in a world where nobody has saved a structure yet — the tray then draws bare frames,
     * exactly as it did before this was wired up.
     */
    private static Map<String, List<BuildingInfo>> buildings = Map.of();
    /** Category keys, sorted, as received. Empty until the catalog arrives. */
    private static List<String> categoryKeys = List.of();

    /** Index into {@link #categoryKeys}; which category tab is currently open. */
    private static int selectedCategory;
    /** The highlighted building, or {@code null}. Cleared whenever the category changes. */
    private static BuildingInfo selectedBuilding;
    private static PlacedBuildingInfo selectedPlacedBuilding;
    private static ConstructionInfo selectedConstruction;
    private static MineStatusPayload selectedMineStatus;
    private static FarmStatusPayload selectedFarmStatus;
    /** ID of the selected building whose upgrade-price popup is currently open. */
    private static long upgradePopupBuildingId = -1L;

    /**
     * Display labels for the category tabs.
     *
     * <p>Derived from whatever categories the structures themselves define — the last segment of
     * each key, title-cased, so {@code villagers/military} reads as "Military". They are not a fixed
     * list: hardcoding four names is exactly why a world full of saved structures once listed
     * nothing.
     */
    public static List<String> buildCategories() {
        return categoryKeys.stream().map(RtsHudState::lastSegmentLabel).toList();
    }

    public static int selectedCategory() {
        return selectedCategory;
    }

    /** Selecting a category drops the building selection: the highlighted slot would otherwise
     *  point at an index in a list that is no longer on screen. */
    public static void setSelectedCategory(int index) {
        if (index >= 0 && index < categoryKeys.size() && index != selectedCategory) {
            selectedCategory = index;
            selectedBuilding = null;
        }
    }

    /** The buildings under the open tab, in the order the server sorted them. */
    public static List<BuildingInfo> visibleBuildings() {
        if (selectedCategory >= categoryKeys.size()) {
            return List.of();
        }
        return buildings.getOrDefault(categoryKeys.get(selectedCategory), List.of());
    }

    public static BuildingInfo selectedBuilding() {
        return selectedBuilding;
    }

    public static void setSelectedBuilding(BuildingInfo building) {
        if (building != null || selectedBuilding != null) {
            closeUpgradePopup();
        }
        selectedBuilding = building;
        selectedMineStatus = null;
        selectedFarmStatus = null;
        if (building != null) {
            selectedTarget = null;
            selectedUnits = List.of();
            selectedConstruction = null;
        }
    }

    public static PlacedBuildingInfo selectedPlacedBuilding() {
        return selectedPlacedBuilding;
    }

    public static void setSelectedPlacedBuilding(PlacedBuildingInfo building) {
        if (selectedPlacedBuilding == null || building == null
                || selectedPlacedBuilding.id() != building.id()) {
            closeUpgradePopup();
        }
        selectedPlacedBuilding = building;
        selectedConstruction = null;
        selectedTarget = null;
        if (building == null || !RtsMineOrders.isMineStructure(building.structure())
                || selectedMineStatus != null && selectedMineStatus.buildingId() != building.id()) {
            selectedMineStatus = null;
        }
        if (building == null || !RtsFarmOrders.isFarmStructure(building.structure())
                || selectedFarmStatus != null && selectedFarmStatus.buildingId() != building.id()) {
            selectedFarmStatus = null;
        }
        if (building != null) {
            selectedUnits = List.of();
        }
    }

    /** Applies a server health snapshot without clearing the selected building's side-panel state. */
    public static void updateSelectedPlacedBuildingHealth(long buildingId, int health, int maxHealth) {
        if (selectedPlacedBuilding == null || selectedPlacedBuilding.id() != buildingId) {
            return;
        }
        selectedPlacedBuilding = new PlacedBuildingInfo(
                selectedPlacedBuilding.id(), selectedPlacedBuilding.structure(),
                selectedPlacedBuilding.origin(), selectedPlacedBuilding.rotation(),
                selectedPlacedBuilding.sizeX(), selectedPlacedBuilding.sizeY(),
                selectedPlacedBuilding.sizeZ(), selectedPlacedBuilding.level(),
                selectedPlacedBuilding.name(), selectedPlacedBuilding.costs(),
                selectedPlacedBuilding.upgradeCosts(), selectedPlacedBuilding.upgradeStructure(),
                Math.max(0, health), Math.max(0, maxHealth));
    }

    public static ConstructionInfo selectedConstruction() {
        return selectedConstruction;
    }

    /** Selects an in-progress foundation and clears the completed-building/action state. */
    public static void setSelectedConstruction(ConstructionInfo construction) {
        if (construction != null && (selectedConstruction == null
                || selectedConstruction.id() != construction.id())) {
            closeUpgradePopup();
        }
        selectedConstruction = construction;
        if (construction != null) {
            selectedPlacedBuilding = null;
            selectedBuilding = null;
            selectedMineStatus = null;
            selectedFarmStatus = null;
            selectedTarget = null;
            selectedUnits = List.of();
        }
    }

    /** Opens the price readout for the exact server-selected building. */
    public static void openUpgradePopup(PlacedBuildingInfo building) {
        upgradePopupBuildingId = building == null ? -1L : building.id();
    }

    /** The popup remains attached to the building ID while its server snapshot refreshes. */
    public static boolean isUpgradePopupOpen(long buildingId) {
        return upgradePopupBuildingId == buildingId && selectedPlacedBuilding != null
                && selectedPlacedBuilding.id() == buildingId;
    }

    public static void closeUpgradePopup() {
        upgradePopupBuildingId = -1L;
    }

    /** Live server status for the selected mine, or {@code null} for every other selection. */
    public static MineStatusPayload selectedMineStatus() {
        return selectedMineStatus;
    }

    public static void setSelectedMineStatus(MineStatusPayload status) {
        if (status == null || !status.isPresent() || selectedPlacedBuilding == null
                || selectedPlacedBuilding.id() != status.buildingId()
                || !RtsMineOrders.isMineStructure(selectedPlacedBuilding.structure())) {
            selectedMineStatus = null;
            return;
        }
        selectedMineStatus = status;
    }

    /** Live server status for the selected farm, or {@code null} for every other selection. */
    public static FarmStatusPayload selectedFarmStatus() {
        return selectedFarmStatus;
    }

    public static void setSelectedFarmStatus(FarmStatusPayload status) {
        if (status == null || !status.isPresent() || selectedPlacedBuilding == null
                || selectedPlacedBuilding.id() != status.buildingId()
                || !RtsFarmOrders.isFarmStructure(selectedPlacedBuilding.structure())) {
            selectedFarmStatus = null;
            return;
        }
        selectedFarmStatus = status;
    }

    /** The selected allied world units, kept as entities so the readout follows live health/state. */
    private static List<net.minecraft.world.entity.LivingEntity> selectedUnits = List.of();
    private static net.minecraft.world.entity.LivingEntity selectedTarget;
    private static net.minecraft.world.entity.LivingEntity hitFlashTarget;
    private static long hitFlashUntil;
    private static boolean newTownGuide;

    /** Clears stale world/build selections after the server replaces a defeated realm. */
    public static void clearSelection() {
        selectedUnits = List.of();
        selectedTarget = null;
        selectedBuilding = null;
        selectedPlacedBuilding = null;
        selectedConstruction = null;
        selectedMineStatus = null;
        selectedFarmStatus = null;
        hitFlashTarget = null;
        hitFlashUntil = 0L;
        closeUpgradePopup();
    }

    /** Shows the bottom-tray arrow until the player chooses the next Town Hall. */
    public static void startNewTownGuide() {
        newTownGuide = true;
        for (int index = 0; index < categoryKeys.size(); index++) {
            if (buildings.getOrDefault(categoryKeys.get(index), List.of()).stream()
                    .anyMatch(BuildingInfo::townHall)) {
                selectedCategory = index;
                selectedBuilding = null;
                return;
            }
        }
    }

    public static boolean newTownGuide() {
        return newTownGuide;
    }

    public static void clearNewTownGuide() {
        newTownGuide = false;
    }

    public static net.minecraft.world.entity.LivingEntity selectedUnit() {
        List<net.minecraft.world.entity.LivingEntity> units = selectedUnits();
        return units.isEmpty() ? null : units.get(0);
    }

    /** Returns all still-live allied units in the current box selection. */
    public static List<net.minecraft.world.entity.LivingEntity> selectedUnits() {
        List<net.minecraft.world.entity.LivingEntity> live = selectedUnits.stream()
                .filter(unit -> unit != null && unit.isAlive() && RtsEntities.isAlliedUnit(unit))
                .toList();
        if (live.size() != selectedUnits.size()) {
            selectedUnits = live;
        }
        return selectedUnits;
    }

    public static void setSelectedUnit(net.minecraft.world.entity.LivingEntity unit) {
        selectedTarget = null;
        selectedUnits = unit != null && unit.isAlive() && RtsEntities.isAlliedUnit(unit)
                ? List.of(unit) : List.of();
        // A world entity took priority over the building ray. Clear the building even when the
        // clicked entity was hostile, so a rejected enemy click cannot leave stale building data in
        // the command panel.
        if (unit != null) {
            closeUpgradePopup();
            selectedBuilding = null;
            selectedPlacedBuilding = null;
            selectedConstruction = null;
            selectedMineStatus = null;
            selectedFarmStatus = null;
        }
    }

    /** Replaces the current selection with the allied units captured by a drag rectangle. */
    public static void setSelectedUnits(Collection<? extends net.minecraft.world.entity.LivingEntity> units) {
        List<net.minecraft.world.entity.LivingEntity> clean = new ArrayList<>();
        if (units != null) {
            for (net.minecraft.world.entity.LivingEntity unit : units) {
                if (unit != null && unit.isAlive() && RtsEntities.isAlliedUnit(unit)
                        && !clean.contains(unit)) {
                    clean.add(unit);
                }
            }
        }
        selectedUnits = List.copyOf(clean);
        selectedTarget = null;
        selectedBuilding = null;
        selectedPlacedBuilding = null;
        selectedConstruction = null;
        selectedMineStatus = null;
        selectedFarmStatus = null;
        closeUpgradePopup();
    }

    /** Drops the one-shot world command selection after an order has been issued. */
    public static void clearSelectedUnits() {
        selectedUnits = List.of();
    }

    /** The hostile RTS mob most recently struck by the free cursor, if it is still alive. */
    public static net.minecraft.world.entity.LivingEntity selectedTarget() {
        if (selectedTarget != null
                && (!selectedTarget.isAlive() || !RtsEntities.isEnemyUnit(selectedTarget))) {
            selectedTarget = null;
        }
        return selectedTarget;
    }

    public static void setSelectedTarget(net.minecraft.world.entity.LivingEntity target) {
        selectedTarget = target != null && target.isAlive() && RtsEntities.isEnemyUnit(target)
                ? target : null;
        if (target != null) {
            closeUpgradePopup();
            selectedUnits = List.of();
            selectedBuilding = null;
            selectedPlacedBuilding = null;
            selectedConstruction = null;
            selectedMineStatus = null;
            selectedFarmStatus = null;
        }
    }

    /** Starts a short local confirmation flash for a click that selected an enemy target. */
    public static void noteTargetHit(net.minecraft.world.entity.LivingEntity target) {
        if (target != null && target.isAlive() && RtsEntities.isEnemyUnit(target)) {
            hitFlashTarget = target;
            hitFlashUntil = Util.getMillis() + 450L;
        }
    }

    public static boolean isTargetHitFlash(net.minecraft.world.entity.LivingEntity target) {
        if (target == null || target != hitFlashTarget || Util.getMillis() >= hitFlashUntil) {
            if (Util.getMillis() >= hitFlashUntil) {
                hitFlashTarget = null;
            }
            return false;
        }
        return true;
    }

    /** Replaces the whole catalog. Called from the payload handler on the client thread. */
    public static void acceptCatalog(Map<String, List<BuildingInfo>> byCategory) {
        Map<String, List<BuildingInfo>> copy = new HashMap<>();
        byCategory.forEach((category, list) -> copy.put(category, List.copyOf(list)));
        buildings = Map.copyOf(copy);
        // The payload travels as a HashMap, so its iteration order is not the server's. Sorting the
        // keys here is what makes the tab order stable between sessions.
        categoryKeys = buildings.keySet().stream().sorted().toList();
        if (selectedCategory >= categoryKeys.size()) {
            selectedCategory = 0;
        }
        // A defeat response can arrive before the catalog response on a fresh client. Re-run the
        // guide now that the Town Hall entry exists, so the arrow always points at the real slot.
        if (newTownGuide) {
            startNewTownGuide();
        }
        // A structure may have been deleted between catalogs; do not keep pointing at it.
        if (selectedBuilding != null && !visibleBuildings().contains(selectedBuilding)) {
            selectedBuilding = null;
        }
    }

    /**
     * The building's display name: whatever {@code buildings.json} calls it, falling back to the
     * folder name so an unlisted structure still reads sensibly.
     */
    public static String displayName(BuildingInfo building) {
        return building.name().isEmpty()
                ? titleCase(ModPayloads.buildingOf(building.id()))
                : building.name();
    }

    public static String displayName(PlacedBuildingInfo building) {
        return building.name().isEmpty()
                ? titleCase(ModPayloads.buildingOf(building.structure()))
                : building.name();
    }

    public static String displayName(ConstructionInfo construction) {
        return construction.name().isEmpty()
                ? titleCase(ModPayloads.buildingOf(construction.structure()))
                : construction.name();
    }

    private static String lastSegmentLabel(String categoryKey) {
        int slash = categoryKey.lastIndexOf('/');
        return titleCase(slash >= 0 ? categoryKey.substring(slash + 1) : categoryKey);
    }

    private static String titleCase(String raw) {
        String[] words = raw.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    // The selected-*unit* readout used to live here — a hardcoded "Villager / Worker / Armor 0 /
    // Gathering Wood" with a setter nothing ever called. It is deleted rather than left dormant:
    // this mod has no unit selection, the bottom-left panel now shows the selected *building*, and
    // an unused setter next to plausible-looking data is an invitation to wire the fiction back up.

    /** The bare glyphs cut out of the original readout by {@code tools/make_panel_slices.py}. */
    public static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "textures/gui/bottombar/" + name + ".png");
    }
}
