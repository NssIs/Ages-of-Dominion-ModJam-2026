package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.BuildingCosts;
import com.hyrrx.forgottenrealmsrts.BuildingPlacement;
import com.hyrrx.forgottenrealmsrts.FlagDesign;
import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import com.hyrrx.forgottenrealmsrts.RtsBattle;
import com.hyrrx.forgottenrealmsrts.RtsCivilization;
import com.hyrrx.forgottenrealmsrts.RtsConstructionOrders;
import com.hyrrx.forgottenrealmsrts.RtsConstructionStore;
import com.hyrrx.forgottenrealmsrts.RtsDayCycle;
import com.hyrrx.forgottenrealmsrts.RtsEconomy;
import com.hyrrx.forgottenrealmsrts.RtsDefenseDirector;
import com.hyrrx.forgottenrealmsrts.RtsBuildingStore;
import com.hyrrx.forgottenrealmsrts.RtsBuildingDurability;
import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.RtsFarmOrders;
import com.hyrrx.forgottenrealmsrts.RtsInvasion;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.RtsMineOrders;
import com.hyrrx.forgottenrealmsrts.RtsSpectateState;
import com.hyrrx.forgottenrealmsrts.RtsStructureTemplates;
import com.hyrrx.forgottenrealmsrts.RtsWorld;
import com.hyrrx.forgottenrealmsrts.RtsUnitOrders;
import com.hyrrx.forgottenrealmsrts.RtsWorkerOrders;
import com.hyrrx.forgottenrealmsrts.RtsRepairOrders;
import com.hyrrx.forgottenrealmsrts.Resource;
import com.hyrrx.forgottenrealmsrts.StructureSanitizer;
import com.hyrrx.forgottenrealmsrts.particle.BuildingActionBurstEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The buildings wire protocol: a catalog request/response, and a per-structure block request/response.
 *
 * <p><strong>Why this has to go over the network at all.</strong> Saved structures live either in a
 * datapack's {@code data/<namespace>/structure/} or in the world's {@code generated/} folder, and
 * neither is visible to the client's {@link net.minecraft.server.packs.resources.ResourceManager} —
 * the data domain never reaches the client. Only the server can enumerate them.
 */
public final class ModPayloads {
    /** TrainGuardianPayload now carries a unit choice; reject mixed-version clients cleanly. */
    private static final String PROTOCOL_VERSION = "8";
    /** The detached RTS camera can sit well away from the server-side spectator anchor. */
    private static final double RTS_TARGET_RANGE = 192.0D;

    /**
     * Trailing upgrade tier, e.g. the {@code level-2} in
     * {@code villagers/military/watchtower/level-2}.
     */
    private static final Pattern LEVEL_SUFFIX = Pattern.compile("^level[-_]?(\\d+)$");
    /** A compact user-authored name such as {@code coal-level-2}. */
    private static final Pattern COMBINED_LEVEL_SUFFIX = Pattern.compile("^(.+?)[-_]level[-_]?(\\d+)$");

    /**
     * <strong>Categories are read from the structures, not hardcoded.</strong>
     *
     * <p>This used to be a fixed list of four folder names, and the result was that a world full of
     * saved structures listed nothing at all, because the names people actually use do not happen to
     * start with the four words someone picked in advance.
     *
     * <p>The convention it reads instead is the one already in the structure names:
     * <pre>&lt;category…&gt;/&lt;building&gt;/level-&lt;n&gt;</pre>
     * The trailing {@code level-n} is optional and is treated as an upgrade tier of the same
     * building, so {@code villagers/military/watchtower/level-{1,2,3}} is <em>one</em> entry in the
     * tray, not three. Everything before the building name is the category, so
     * {@code villagers/military/watchtower/level-1} files under {@code villagers/military} and
     * {@code soldiers/space/level-1} under {@code soldiers}. A plain
     * {@code defense/watchtower} still works — it is just a category with no level.
     *
     * <p><strong>The namespace is still restricted, with a narrow migration exception.</strong> An
     * earlier version accepted any namespace, which caused {@code listTemplates()} to expose
     * Minecraft's entire built-in structure library. The mod namespace remains the normal route.
     * The explicitly authored legacy trees used by the current world,
     * {@code minecraft:soldiers/...} and {@code minecraft:villagers/...}, are also accepted so a
     * newly saved player structure remains usable without opening the vanilla library floodgate.
     * Those prefixes are deliberately narrower than accepting the whole {@code minecraft:}
     * namespace: vanilla templates live under names such as {@code village}, {@code pillager_outpost},
     * and {@code ancient_city}, not these player-authored RTS trees.
     */
    public static String categoryOf(Identifier id) {
        if (!ForgottenRealmsRTS.MOD_ID.equals(id.getNamespace())
                && !isApprovedLegacyStructure(id)) {
            return null;
        }
        String[] parts = meaningfulSegments(id);
        // Needs at least a category and a building name.
        return parts.length < 2 ? null : String.join("/", Arrays.copyOf(parts, parts.length - 1));
    }

    /** The building's own name — the last segment once any {@code level-n} is stripped. */
    public static String buildingOf(Identifier id) {
        String[] parts = meaningfulSegments(id);
        return parts.length == 0 ? id.getPath() : parts[parts.length - 1];
    }

    /** The upgrade tier, or 1 when the name carries none. */
    public static int levelOf(Identifier id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String leaf = slash >= 0 ? path.substring(slash + 1) : path;
        Matcher matcher = LEVEL_SUFFIX.matcher(leaf);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        matcher = COMBINED_LEVEL_SUFFIX.matcher(leaf);
        return matcher.matches() ? Integer.parseInt(matcher.group(2)) : 1;
    }

    /** Path segments with a trailing {@code level-n} or {@code name-level-n} normalized. */
    private static String[] meaningfulSegments(Identifier id) {
        String[] parts = id.getPath().split("/");
        if (parts.length > 1) {
            String leaf = parts[parts.length - 1];
            if (LEVEL_SUFFIX.matcher(leaf).matches()) {
                return Arrays.copyOf(parts, parts.length - 1);
            }
            Matcher combined = COMBINED_LEVEL_SUFFIX.matcher(leaf);
            if (combined.matches()) {
                parts[parts.length - 1] = combined.group(1);
            }
        }
        return parts;
    }

    /** Keeps ordinary vanilla templates out while honoring player structure-block IDs. */
    private static boolean isApprovedLegacyStructure(Identifier id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.startsWith("soldiers/") || path.startsWith("villagers/");
    }

    /**
     * Ceiling on blocks sent for one preview. The tray draws these at about 40 pixels square, so
     * detail past a couple of thousand blocks is invisible and costs bandwidth for nothing.
     */
    private static final int MAX_PREVIEW_BLOCKS = 2048;
    /** The packing in {@link BuildingPreviewPayload} gives each coordinate and the palette index one
     *  byte. A structure block cannot capture more than 48 on a side, so this is headroom. */
    private static final int MAX_DIMENSION = 255;
    private static final int MAX_PALETTE = 256;

    private ModPayloads() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModPayloads::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(RequestBuildingCatalogPayload.TYPE,
                RequestBuildingCatalogPayload.STREAM_CODEC, ModPayloads::onCatalogRequested);
        registrar.playToServer(RequestBuildingPreviewPayload.TYPE,
                RequestBuildingPreviewPayload.STREAM_CODEC, ModPayloads::onPreviewRequested);
        registrar.playToServer(PlaceBuildingPayload.TYPE,
                PlaceBuildingPayload.STREAM_CODEC, ModPayloads::onPlaceRequested);
        registrar.playToServer(PlaceLinearBuildingPayload.TYPE,
                PlaceLinearBuildingPayload.STREAM_CODEC, ModPayloads::onLinearPlaceRequested);
        registrar.playToServer(RequestBuildingSelectionPayload.TYPE,
                RequestBuildingSelectionPayload.STREAM_CODEC, ModPayloads::onSelectionRequested);
        registrar.playToServer(BuildingActionPayload.TYPE,
                BuildingActionPayload.STREAM_CODEC, ModPayloads::onActionRequested);
        registrar.playToServer(FoundCivilizationPayload.TYPE,
                FoundCivilizationPayload.STREAM_CODEC, ModPayloads::onFoundRequested);
        registrar.playToServer(FoundNewTownPayload.TYPE,
                FoundNewTownPayload.STREAM_CODEC, ModPayloads::onFoundNewTownRequested);
        registrar.playToServer(EnterSpectatePayload.TYPE,
                EnterSpectatePayload.STREAM_CODEC, ModPayloads::onSpectateRequested);
        registrar.playToServer(RequestHomePayload.TYPE,
                RequestHomePayload.STREAM_CODEC, ModPayloads::onHomeRequested);
        registrar.playToServer(TrainVillagerPayload.TYPE,
                TrainVillagerPayload.STREAM_CODEC, ModPayloads::onTrainRequested);
        registrar.playToServer(AdvanceAgePayload.TYPE,
                AdvanceAgePayload.STREAM_CODEC, ModPayloads::onAdvanceAgeRequested);
        registrar.playToServer(GuideCompletedPayload.TYPE,
                GuideCompletedPayload.STREAM_CODEC, ModPayloads::onGuideCompleted);
        registrar.playToServer(TrainGuardianPayload.TYPE,
                TrainGuardianPayload.STREAM_CODEC, ModPayloads::onTrainGuardianRequested);
        registrar.playToServer(RepairTownHallPayload.TYPE,
                RepairTownHallPayload.STREAM_CODEC, ModPayloads::onRepairRequested);
        registrar.playToServer(RallyArmyPayload.TYPE,
                RallyArmyPayload.STREAM_CODEC, ModPayloads::onRallyRequested);
        registrar.playToServer(MoveUnitsPayload.TYPE,
                MoveUnitsPayload.STREAM_CODEC, ModPayloads::onMoveUnitsRequested);
        registrar.playToServer(GatherWoodPayload.TYPE,
                GatherWoodPayload.STREAM_CODEC, ModPayloads::onGatherWoodRequested);
        registrar.playToServer(AssignMinePayload.TYPE,
                AssignMinePayload.STREAM_CODEC, ModPayloads::onAssignMineRequested);
        registrar.playToServer(AssignFarmPayload.TYPE,
                AssignFarmPayload.STREAM_CODEC, ModPayloads::onAssignFarmRequested);
        registrar.playToServer(AssignConstructionPayload.TYPE,
                AssignConstructionPayload.STREAM_CODEC, ModPayloads::onAssignConstructionRequested);
        registrar.playToServer(AssignRepairPayload.TYPE,
                AssignRepairPayload.STREAM_CODEC, ModPayloads::onAssignRepairRequested);
        registrar.playToServer(RecallWorkersPayload.TYPE,
                RecallWorkersPayload.STREAM_CODEC, ModPayloads::onRecallWorkersRequested);
        registrar.playToServer(SelectedUnitCommandPayload.TYPE,
                SelectedUnitCommandPayload.STREAM_CODEC, ModPayloads::onSelectedUnitCommand);
        registrar.playToServer(ArmyCommandPayload.TYPE,
                ArmyCommandPayload.STREAM_CODEC, ModPayloads::onArmyCommand);
        registrar.playToServer(MoveCameraPayload.TYPE,
                MoveCameraPayload.STREAM_CODEC, ModPayloads::onMoveCamera);
        registrar.playToServer(RtsMobHitPayload.TYPE,
                RtsMobHitPayload.STREAM_CODEC, ModPayloads::onRtsMobHit);
    }

    private static void onCatalogRequested(RequestBuildingCatalogPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        // 26.1 removed ServerPlayer#getServer(); the server is reached through its level.
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        RtsEconomy.migrateProgression(player);
        RtsWorld.sanitizeOwnedBuildings((ServerLevel) player.level(), player.getUUID());
        context.reply(new BuildingCatalogPayload(
                describeCatalog(server.getStructureManager().listTemplates().toList(), player)));
        sendBuildingHealth(player);
    }

    /**
     * Groups structures into categories, collapsing upgrade levels of the same building into one
     * entry represented by its lowest level.
     *
     * <p>Package-visible rather than private so it can be exercised directly, and so
     * {@code /game buildings} reports exactly what the client will be sent.
     */
    public static Map<String, List<Identifier>> buildCatalog(List<Identifier> templates) {
        // category -> building -> lowest-level identifier
        Map<String, Map<String, Identifier>> grouped = new TreeMap<>();

        for (Identifier id : templates) {
            String category = categoryOf(id);
            if (category == null) {
                continue;
            }
            Map<String, Identifier> buildings = grouped.computeIfAbsent(category, key -> new TreeMap<>());
            String building = buildingOf(id);
            Identifier existing = buildings.get(building);
            if (existing == null || levelOf(id) < levelOf(existing)
                    || levelOf(id) == levelOf(existing)
                    && isApprovedLegacyStructure(id) && !isApprovedLegacyStructure(existing)) {
                buildings.put(building, id);
            }
        }

        Map<String, List<Identifier>> byCategory = new LinkedHashMap<>();
        grouped.forEach((category, buildings) -> byCategory.put(category, List.copyOf(buildings.values())));
        return byCategory;
    }

    /** The same grouping, with each entry's name and price attached from {@code buildings.json}. */
    private static Map<String, List<BuildingInfo>> describeCatalog(List<Identifier> templates,
                                                                    ServerPlayer player) {
        Map<String, List<BuildingInfo>> described = new LinkedHashMap<>();
        buildCatalog(templates).forEach((category, buildings) -> {
            List<BuildingInfo> infos = new ArrayList<>(buildings.size());
            for (Identifier id : buildings) {
                BuildingCosts.Definition definition = BuildingCosts.get(id);
                infos.add(new BuildingInfo(id, definition.name(),
                        BuildingCosts.placementCost(id, player),
                        definition.townHall(), definition.minAge()));
            }
            described.put(category, List.copyOf(infos));
        });
        return described;
    }

    private static void onPreviewRequested(RequestBuildingPreviewPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        // 26.1 removed ServerPlayer#getServer(); the server is reached through its level.
        MinecraftServer server = player.level().getServer();
        if (server == null || categoryOf(payload.structure()) == null) {
            return;
        }

        Optional<StructureTemplate> found = RtsStructureTemplates.get(server, payload.structure());
        if (found.isEmpty()) {
            return;
        }

        StructureTemplate template = found.get();
        Vec3i size = template.getSize();
        if (size.getX() > MAX_DIMENSION || size.getY() > MAX_DIMENSION || size.getZ() > MAX_DIMENSION) {
            return;
        }
        if (template.palettes.isEmpty()) {
            return;
        }

        List<StructureTemplate.StructureBlockInfo> all = template.palettes.get(0).blocks();
        context.reply(buildPreview(payload.structure(), size, all));
    }

    /**
     * Places a building, after re-checking every rule from scratch.
     *
     * <p>None of the client's opinion is trusted here. The ghost's colour is a courtesy to the
     * player; this is the authority, and it re-runs affordability, the town-hall rule and the same
     * {@link BuildingPlacement#checkGeometry} the ghost used, against the real template rather than
     * the shell-only preview the client holds.
     */
    private static void onPlaceRequested(PlaceBuildingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null || !RtsMode.isActive(player) || categoryOf(payload.structure()) == null) {
            return;
        }

        Optional<StructureTemplate> found = RtsStructureTemplates.get(server, payload.structure());
        if (found.isEmpty() || found.get().palettes.isEmpty()) {
            refuse(player, BuildingPlacement.Result.UNKNOWN_STRUCTURE);
            return;
        }
        StructureTemplate template = found.get();
        BuildingCosts.Definition definition = BuildingCosts.get(payload.structure());
        RtsEconomy.migrateProgression(player);
        boolean onboardingCoalMine = BuildingCosts.isFirstCoalMinePlacement(payload.structure(), player);
        // Order matters for the message the player gets: "found your town first" is more useful
        // than "you cannot afford it" when both are true.
        if (definition.townHall() && RtsEconomy.townHallPlaced(player)) {
            refuse(player, "Your civilization already has a Town Hall.");
            return;
        }
        if (!definition.townHall() && !RtsEconomy.townHallPlaced(player)) {
            refuse(player, BuildingPlacement.Result.TOWN_HALL_REQUIRED);
            return;
        }
        if (!definition.townHall() && !RtsCivilization.isFounded(player)) {
            refuse(player, BuildingPlacement.Result.FOUNDING_REQUIRED);
            return;
        }
        if (!definition.townHall() && !RtsEconomy.coalMinePlaced(player)
                && !onboardingCoalMine) {
            refuse(player, BuildingPlacement.Result.COAL_MINE_REQUIRED);
            return;
        }
        if (definition.minAge() > RtsCivilization.age(player)) {
            refuse(player, Component.literal("Requires "
                    + RtsCivilization.AGE_NAMES[Math.min(definition.minAge(),
                    RtsCivilization.MAX_AGE)]));
            return;
        }
        int[] placementCost = BuildingCosts.placementCost(payload.structure(), player);
        if (!RtsEconomy.canAfford(player, placementCost)) {
            refuse(player, BuildingPlacement.Result.UNAFFORDABLE);
            return;
        }

        Rotation rotation = payload.rotation();
        Vec3i size = template.getSize();
        Vec3i rotatedSize = BuildingPlacement.rotateSize(size, rotation);
        Set<Long> solid = solidFootprint(template, size, rotation);

        BuildingPlacement.Result geometry = BuildingPlacement.checkGeometry(
                player.level(), payload.origin(), rotatedSize,
                (x, y, z) -> solid.contains(BlockPos.asLong(x, y, z)));
        if (!geometry.ok()) {
            refuse(player, geometry);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        if (RtsConstructionOrders.overlaps(level, payload.origin(), rotatedSize, solid)) {
            refuse(player, BuildingPlacement.Result.OBSTRUCTED);
            return;
        }

        int totalBlocks = RtsConstructionOrders.blockCount(template);
        if (totalBlocks <= 0) {
            refuse(player, BuildingPlacement.Result.UNKNOWN_STRUCTURE);
            return;
        }

        RtsConstructionStore store = RtsConstructionStore.get(level);
        RtsConstructionStore.Entry construction = store.add(player.getUUID(), payload.structure(),
                payload.origin(), rotation, totalBlocks);
        ConstructionInfo info = describeConstruction(level, construction);
        if (info == null) {
            store.remove(construction.id(), player.getUUID());
            refuse(player, BuildingPlacement.Result.UNKNOWN_STRUCTURE);
            return;
        }

        RtsEconomy.spend(player, placementCost);
        context.reply(new BuildingSelectionPayload(Optional.empty(), Optional.of(info)));
        context.reply(new BuildingEffectPayload(info.origin(), info.sizeX(), info.sizeY(),
                info.sizeZ(), BuildingActionPayload.Action.PLACE));
        BuildingActionBurstEffect.play(level,
                info.origin().getX() + info.sizeX() / 2.0D,
                info.origin().getY() + Math.max(0.5D, info.sizeY() * 0.5D),
                info.origin().getZ() + info.sizeZ() / 2.0D);
        player.sendOverlayMessage(Component.literal("Foundation set. Select villagers and click the site."));
    }

    /** Places a dominant-axis, one-block-wide path or wall drag as one server-side transaction. */
    private static void onLinearPlaceRequested(PlaceLinearBuildingPayload payload,
                                               IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null || !RtsMode.isActive(player)
                || !BuildingPlacement.isLinearStructure(payload.structure())
                || categoryOf(payload.structure()) == null) {
            return;
        }

        Optional<StructureTemplate> found = RtsStructureTemplates.get(server, payload.structure());
        if (found.isEmpty() || found.get().palettes.isEmpty()) {
            refuse(player, BuildingPlacement.Result.UNKNOWN_STRUCTURE);
            return;
        }
        StructureTemplate template = found.get();
        BuildingPlacement.LinearLayout layout = BuildingPlacement.linearLayout(
                payload.structure(), template.getSize(), payload.anchor(), payload.cursor());
        if (layout.pieces() < 1 || layout.pieces() > BuildingPlacement.MAX_LINEAR_PIECES) {
            refuse(player, "That span is too long.");
            return;
        }

        BuildingCosts.Definition definition = BuildingCosts.get(payload.structure());
        RtsEconomy.migrateProgression(player);
        if (!definition.townHall() && !RtsEconomy.townHallPlaced(player)) {
            refuse(player, BuildingPlacement.Result.TOWN_HALL_REQUIRED);
            return;
        }
        if (!definition.townHall() && !RtsCivilization.isFounded(player)) {
            refuse(player, BuildingPlacement.Result.FOUNDING_REQUIRED);
            return;
        }
        if (!definition.townHall() && !RtsEconomy.coalMinePlaced(player)) {
            refuse(player, BuildingPlacement.Result.COAL_MINE_REQUIRED);
            return;
        }
        if (definition.minAge() > RtsCivilization.age(player)) {
            refuse(player, Component.literal("Requires "
                    + RtsCivilization.AGE_NAMES[Math.min(definition.minAge(),
                    RtsCivilization.MAX_AGE)]));
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        boolean path = BuildingPlacement.isPathStructure(payload.structure());
        List<BlockPos> cells = layout.origins();
        if (linearOverTrackedStructure(level, cells, path)) {
            refuse(player, BuildingPlacement.Result.OBSTRUCTED);
            return;
        }
        BuildingPlacement.Result geometry = BuildingPlacement.checkLinearGeometry(
                level, cells, path, ignoredLinearTargets(level, player, cells, path));
        if (!geometry.ok()) {
            refuse(player, geometry);
            return;
        }

        List<BlockPos> newCells = new ArrayList<>(cells.size());
        int alreadyLaid = 0;
        for (BlockPos cell : cells) {
            if (path && level.getBlockState(cell).is(Blocks.DIRT_PATH)) {
                alreadyLaid++;
            } else {
                newCells.add(cell);
            }
        }
        if (newCells.isEmpty()) {
            refuse(player, "That path is already laid — no charge.");
            return;
        }

        int[] totalCost = scaledCosts(BuildingCosts.placementCost(payload.structure(), player),
                newCells.size());
        if (!RtsEconomy.canAfford(player, totalCost)) {
            refuse(player, BuildingPlacement.Result.UNAFFORDABLE);
            return;
        }

        Map<Long, BlockState> originalStates = snapshotStates(level,
                newCells.stream().map(BlockPos::asLong).collect(java.util.stream.Collectors.toSet()));
        BlockState linearState = path ? Blocks.DIRT_PATH.defaultBlockState()
                : canonicalWallState(level, template);
        if (linearState == null) {
            refuse(player, BuildingPlacement.Result.UNKNOWN_STRUCTURE);
            return;
        }
        for (BlockPos cell : newCells) {
            if (!level.setBlock(cell, linearState, Block.UPDATE_ALL)) {
                restoreStates(level, originalStates);
                refuse(player, BuildingPlacement.Result.UNKNOWN_STRUCTURE);
                return;
            }
        }

        RtsEconomy.spend(player, totalCost);
        RtsBuildingStore store = RtsBuildingStore.get(level);
        RtsBuildingStore.Entry last = null;
        for (BlockPos cell : newCells) {
            last = store.add(level, player.getUUID(), payload.structure(), cell, Rotation.NONE, 1);
        }
        if (last != null) {
            sendBuildingUpdate(context, player, server, last, BuildingActionPayload.Action.PLACE);
        }
        String kind = path ? "path tiles" : "wall segments";
        String suffix = alreadyLaid > 0
                ? " (" + alreadyLaid + " already laid; no charge)" : "";
        player.sendOverlayMessage(Component.literal("Laid " + newCells.size() + " "
                + kind + suffix));
    }

    /** Existing dirt paths are the only occupied cells a linear drag may reuse. */
    private static Set<Long> ignoredLinearTargets(ServerLevel level, ServerPlayer player,
                                                  List<BlockPos> cells, boolean path) {
        if (!path) {
            return Set.of();
        }
        Set<Long> ignored = new HashSet<>();
        for (BlockPos cell : cells) {
            if (level.getBlockState(cell).is(Blocks.DIRT_PATH)
                    && !linearOverTrackedStructure(level, List.of(cell), true)) {
                ignored.add(cell.asLong());
            }
        }
        return ignored;
    }

    private static boolean linearOverTrackedStructure(ServerLevel level, List<BlockPos> cells,
                                                      boolean path) {
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            if (path && BuildingPlacement.isPathStructure(entry.structure())) {
                continue;
            }
            for (BlockPos cell : cells) {
                if (RtsBuildingDurability.contains(level, entry, cell)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Selects the lowest authored solid block as the canonical wall voxel. */
    private static BlockState canonicalWallState(ServerLevel level, StructureTemplate template) {
        StructureTemplate.StructureBlockInfo selected = null;
        for (StructureTemplate.StructureBlockInfo info : template.palettes.get(0).blocks()) {
            if (info.state().isAir() || StructureSanitizer.isTechnicalMarker(info.state())) {
                continue;
            }
            if (info.state().getCollisionShape(level, BlockPos.ZERO).isEmpty()) {
                continue;
            }
            if (selected == null || info.pos().getY() < selected.pos().getY()
                    || info.pos().getY() == selected.pos().getY()
                    && (info.pos().getZ() < selected.pos().getZ()
                    || info.pos().getZ() == selected.pos().getZ()
                    && info.pos().getX() < selected.pos().getX())) {
                selected = info;
            }
        }
        return selected == null ? null : selected.state();
    }

    /**
     * The player confirmed the founding screen. Records the civilization's name and banner, spawns
     * the first villager, and announces the realm. The free Coal Mine is the next onboarding step;
     * the rest of the building menu unlocks after it succeeds.
     */
    private static void onFoundRequested(FoundCivilizationPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (RtsCivilization.isFounded(player)) {
            return; // A duplicate confirm — the realm is already founded.
        }
        String name = sanitizeName(payload.name());
        FlagDesign flag = payload.flag().sanitized();
        RtsCivilization.found(player, name, flag);
        RtsEconomy.grantStartingStock(player);
        RtsEntities.ensureTownWorker(player);
        int startingSoldiers = RtsEntities.spawnStartingArmy(player);
        RtsEntities.townHallCenter(player).ifPresent(center -> {
            RtsEntities.reconcilePopulation(player, center);
            RtsEconomy.setMilitary(player, startingSoldiers);
        });
        Component announcement = Component.literal("The Realm of " + name + " has been founded.");
        player.sendSystemMessage(announcement);
        player.sendOverlayMessage(announcement);
        player.sendSystemMessage(Component.literal(
                "Available moon events: golden-moon, blood-moon, blue-moon."));
        ForgottenRealmsRTS.LOGGER.info("{} founded the realm of {}.",
                player.getName().getString(), name);
    }

    /** Clears a defeated player's tracked town and resets the campaign to day zero. */
    private static void onFoundNewTownRequested(FoundNewTownPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_DEFEAT) {
            return;
        }

        RtsSpectateState.exit(player);
        ServerLevel level = (ServerLevel) player.level();
        Optional<BlockPos> oldCenter = RtsEntities.townHallCenter(player);
        RtsInvasion.resetPlayer(player);
        RtsDefenseDirector.clear();
        RtsWorkerOrders.clear();
        RtsUnitOrders.clear();
        oldCenter.ifPresent(center -> RtsEntities.removeOwnedUnits(level, center));
        int removedConstructionBlocks = RtsConstructionOrders.clearOwned(level, player.getUUID());
        int removedBuildings = RtsWorld.clearOwnedBuildings(level, player.getUUID());
        RtsCivilization.reset(player);
        RtsBattle.reset(player);
        RtsEconomy.reset(player);
        RtsDayCycle.resetToDay(level);

        player.sendSystemMessage(Component.literal("The fallen realm has been cleared."));
        player.sendOverlayMessage(Component.literal(
                "Place a new Town Hall to begin again. The old realm is gone."));
        ForgottenRealmsRTS.LOGGER.info("Cleared {} tracked buildings for {}'s replacement town.",
                removedBuildings, player.getName().getString());
        if (removedConstructionBlocks > 0) {
            ForgottenRealmsRTS.LOGGER.info("Cleared {} unfinished construction blocks for {}'s replacement town.",
                    removedConstructionBlocks, player.getName().getString());
        }
        context.reply(new BuildingCatalogPayload(
                describeCatalog(level.getServer().getStructureManager().listTemplates().toList(), player)));
        context.reply(new BuildingHealthPayload(List.of()));
        context.reply(new NewTownReadyPayload());
    }

    /** Enters the frozen view of the defeated town and returns the camera to its Town Hall. */
    private static void onSpectateRequested(EnterSpectatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_DEFEAT) {
            return;
        }

        RtsSpectateState.enter(player);
        onHomeRequested(new RequestHomePayload(), context);
        player.sendOverlayMessage(Component.literal("Spectating the fallen town. The world is paused."));
    }

    private static String sanitizeName(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.length() > 28) {
            trimmed = trimmed.substring(0, 28);
        }
        return trimmed.isEmpty() ? "the Nameless" : trimmed;
    }

    /** Trains one villager at the Town Hall; {@link RtsEntities#trainVillager} enforces cost + cap. */
    private static void onTrainRequested(TrainVillagerPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && RtsMode.isActive(player)) {
            RtsEntities.trainVillager(player, payload.townHallId());
        }
    }

    /** Jumps the RTS camera to a map-clicked world column, teleporting the spectator player there. */
    private static void onMoveCamera(MoveCameraPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !RtsMode.isActive(player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        int x = payload.x();
        int z = payload.z();
        level.getChunk(x >> 4, z >> 4);
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2.0D;
        player.teleportTo(x + 0.5D, y, z + 0.5D);
    }

    /** Applies one small, server-authoritative cursor hit to a custom RTS enemy. */
    private static void onRtsMobHit(RtsMobHitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !RtsMode.isActive(player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        net.minecraft.world.entity.Entity entity = level.getEntity(payload.entityId());
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity target)
                || !target.isAlive() || !RtsEntities.isEnemyUnit(target)) {
            return;
        }

        // Allied clicks are selection-only on the client and can never become attack packets.
        if (RtsEntities.isAlliedUnit(target)) {
            return;
        }

        // The client only sends an id. The range keeps a forged packet from reaching an arbitrary
        // mob while allowing for the detached camera's offset from the server-side RTS anchor. Do
        // not use player.hasLineOfSight here: the spectator anchor is not the camera ray's origin,
        // so terrain between those two different viewpoints rejected legitimate visible clicks.
        if (target.distanceToSqr(player) > RTS_TARGET_RANGE * RTS_TARGET_RANGE) {
            return;
        }
        target.hurtServer(level, level.damageSources().playerAttack(player), 0.5F);
    }

    /** Target-free army orders from the command grid: regroup, attack the nearest enemy, or halt. */
    private static void onArmyCommand(ArmyCommandPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !RtsMode.isActive(player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = RtsEntities.townHallCenter(player).orElse(player.blockPosition());
        List<net.minecraft.world.entity.Mob> army = RtsEntities.alliedCombatUnits(level, center, 96.0D);
        if (army.isEmpty()) {
            player.sendOverlayMessage(Component.literal("You have no army yet."));
            return;
        }
        switch (payload.command()) {
            case RALLY_HOME -> {
                BlockPos groundCenter = RtsEntities.townHallGroundCenter(player).orElse(center);
                RtsUnitOrders.issueMove(army, groundCenter);
                player.sendOverlayMessage(Component.literal(army.size() + " units regroup."));
            }
            case STOP -> {
                RtsUnitOrders.issueHold(army);
                player.sendOverlayMessage(Component.literal("The army holds position."));
            }
            case ATTACK_NEAREST -> {
                net.minecraft.world.entity.LivingEntity nearest = null;
                double best = Double.MAX_VALUE;
                for (var monster : level.getEntitiesOfClass(
                        net.minecraft.world.entity.monster.Monster.class,
                        new AABB(center).inflate(80.0D),
                        monster -> monster.isAlive() && RtsEntities.isEnemyUnit(monster))) {
                    double distance = monster.distanceToSqr(center.getX(), center.getY(), center.getZ());
                    if (distance < best) {
                        best = distance;
                        nearest = monster;
                    }
                }
                if (nearest == null) {
                    player.sendOverlayMessage(Component.literal("No enemies in sight."));
                } else {
                    RtsUnitOrders.issueAttack(army, nearest);
                    player.sendOverlayMessage(Component.literal(army.size() + " units attack."));
                }
            }
        }
    }

    /** Orders every allied combat unit near the settlement to move to the cursor's world position. */
    private static void onRallyRequested(RallyArmyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !RtsMode.isActive(player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos target = payload.target();
        BlockPos center = RtsEntities.townHallCenter(player).orElse(player.blockPosition());
        List<net.minecraft.world.entity.Mob> army = RtsEntities.alliedCombatUnits(level, center, 96.0D);
        RtsUnitOrders.issueMove(army, target);
        if (!army.isEmpty()) {
            player.sendOverlayMessage(Component.literal(army.size() + " units move out."));
        }
    }

    /** Moves only the units the player selected, rather than silently commandeering the whole army. */
    private static void onMoveUnitsRequested(MoveUnitsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING) {
            return;
        }

        List<net.minecraft.world.entity.Mob> units = selectedAlliedUnits(player, payload.entityIds());
        if (units.isEmpty()) {
            return;
        }
        RtsUnitOrders.issueMove(units, payload.target());
        player.sendOverlayMessage(Component.literal(units.size() + " units move out."));
    }

    /** Assigns only selected workers to the validated tree target. */
    private static void onGatherWoodRequested(GatherWoodPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING) {
            return;
        }
        RtsWorkerOrders.assignWood(player, payload.entityIds(), payload.target());
    }

    /** Assigns selected worker villagers to a tracked mine. */
    private static void onAssignMineRequested(AssignMinePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING) {
            return;
        }
        RtsMineOrders.assignMine(player, payload.entityIds(), payload.buildingId());
    }

    /** Assigns selected worker villagers to a one-worker farm. */
    private static void onAssignFarmRequested(AssignFarmPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING) {
            return;
        }
        RtsFarmOrders.assignFarm(player, payload.entityIds(), payload.buildingId());
    }

    /** Assigns selected worker villagers to a structure that is still being assembled. */
    private static void onAssignConstructionRequested(AssignConstructionPayload payload,
                                                       IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING) {
            return;
        }
        RtsConstructionOrders.assignWorkers(player, payload.entityIds(), payload.constructionId());
    }

    /** Assigns selected worker villagers to repair a damaged tracked building. */
    private static void onAssignRepairRequested(AssignRepairPayload payload,
                                                 IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING) {
            return;
        }
        RtsRepairOrders.assignRepair(player, payload.entityIds(), payload.buildingId());
    }

    /** Releases workers from a validated mine or farm and sends them back to the Town Hall. */
    private static void onRecallWorkersRequested(RecallWorkersPayload payload,
                                                  IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING) {
            return;
        }
        Optional<RtsBuildingStore.Entry> found = RtsBuildingStore.get((ServerLevel) player.level())
                .find(payload.buildingId(), player.getUUID());
        if (found.isEmpty()) {
            return;
        }
        if (RtsMineOrders.isMineStructure(found.get().structure())) {
            RtsMineOrders.recallMineWorkers(player, payload.buildingId());
        } else if (RtsFarmOrders.isFarmStructure(found.get().structure())) {
            RtsFarmOrders.recallFarmWorkers(player, payload.buildingId());
        }
    }

    /** Applies a visible command-grid order to exactly the units that were selected on the client. */
    private static void onSelectedUnitCommand(SelectedUnitCommandPayload payload,
                                               IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RtsMode.isActive(player)
                || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || payload.command() == null) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        List<net.minecraft.world.entity.Mob> units = selectedAlliedUnits(player, payload.entityIds());
        if (units.isEmpty()) {
            return;
        }

        switch (payload.command()) {
            case RALLY_HOME -> {
                BlockPos home = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
                RtsUnitOrders.issueMove(units, home);
                player.sendOverlayMessage(Component.literal(units.size() + " selected units regroup."));
            }
            case STOP -> {
                RtsUnitOrders.issueHold(units);
                player.sendOverlayMessage(Component.literal("Selected units hold position."));
            }
            case ATTACK_NEAREST -> {
                List<net.minecraft.world.entity.Mob> combat = units.stream()
                        .filter(RtsEntities::isAlliedCombatUnit)
                        .toList();
                if (combat.isEmpty()) {
                    player.sendOverlayMessage(Component.literal("Select soldiers to attack."));
                    return;
                }
                net.minecraft.world.entity.LivingEntity nearest = nearestEnemy(level, combat);
                if (nearest == null) {
                    player.sendOverlayMessage(Component.literal("No enemies near the selected units."));
                    return;
                }
                RtsUnitOrders.issueAttack(combat, nearest);
                player.sendOverlayMessage(Component.literal(combat.size() + " selected soldiers attack."));
            }
        }
    }

    private static net.minecraft.world.entity.LivingEntity nearestEnemy(
            ServerLevel level, List<? extends net.minecraft.world.entity.Mob> units) {
        net.minecraft.world.entity.LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Mob unit : units) {
            for (net.minecraft.world.entity.LivingEntity candidate : level.getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class,
                    unit.getBoundingBox().inflate(64.0D),
                    entity -> entity.isAlive() && RtsEntities.isEnemyUnit(entity))) {
                double distance = unit.distanceToSqr(candidate);
                if (distance < best) {
                    best = distance;
                    nearest = candidate;
                }
            }
        }
        return nearest;
    }

    private static List<net.minecraft.world.entity.Mob> selectedAlliedUnits(
            ServerPlayer player, List<Integer> entityIds) {
        if (entityIds == null || entityIds.isEmpty() || entityIds.size() > 64) {
            return List.of();
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        double radiusSquared = RtsEntities.POPULATION_SCAN_RADIUS * RtsEntities.POPULATION_SCAN_RADIUS;
        Set<Integer> seen = new HashSet<>();
        List<net.minecraft.world.entity.Mob> units = new ArrayList<>();
        for (Integer entityId : entityIds) {
            if (entityId == null || !seen.add(entityId)) {
                continue;
            }
            net.minecraft.world.entity.Entity entity = level.getEntity(entityId);
            if (entity instanceof net.minecraft.world.entity.Mob unit && unit.isAlive()
                    && RtsEntities.isAlliedUnit(unit)
                    && unit.distanceToSqr(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D)
                    <= radiusSquared) {
                units.add(unit);
            }
        }
        return units;
    }

    /** Spends stone to restore Town Hall integrity — the once-decorative Repair, made real. */
    private static void onRepairRequested(RepairTownHallPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !RtsMode.isActive(player)
                || !RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || RtsBattle.integrity(player) <= 0) {
            return;
        }
        int maxIntegrity = RtsBattle.maxIntegrity(player);
        int repairAmount = Math.max(REPAIR_AMOUNT, maxIntegrity / 10);
        if (RtsBattle.integrity(player) >= maxIntegrity) {
            player.sendOverlayMessage(Component.literal("The Town Hall is already whole."));
            return;
        }
        int[] cost = new int[Resource.COUNT];
        cost[Resource.STONE.ordinal()] = REPAIR_STONE_COST;
        if (!RtsEconomy.canAfford(player, cost)) {
            player.sendOverlayMessage(Component.literal("Not enough stone (" + REPAIR_STONE_COST
                    + ") to repair the Town Hall."));
            return;
        }
        RtsEconomy.spend(player, cost);
        RtsBattle.setIntegrity(player, RtsBattle.integrity(player) + repairAmount);
        player.sendOverlayMessage(Component.literal("Town Hall repaired — integrity "
                + RtsBattle.integrity(player) + "/" + maxIntegrity + "."));
        sendBuildingHealth(player);
    }

    private static final int REPAIR_STONE_COST = 10;
    private static final int REPAIR_AMOUNT = 15;

    /** Trains one guardian profile; {@link RtsEntities#trainGuardian} enforces Barracks + cost + cap. */
    private static void onTrainGuardianRequested(TrainGuardianPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && RtsMode.isActive(player)) {
            RtsEntities.trainGuardian(player, payload.unit() == TrainGuardianPayload.Unit.CROSSBOWMAN);
        }
    }

    /** Advances the civilization one age for an escalating resource cost, capped at Castle. */
    private static void onAdvanceAgeRequested(AdvanceAgePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !RtsMode.isActive(player)) {
            return;
        }
        if (!RtsCivilization.isFounded(player)) {
            player.sendOverlayMessage(Component.literal("Found your civilization first."));
            return;
        }
        int age = RtsCivilization.age(player);
        if (age >= RtsCivilization.MAX_AGE) {
            player.sendOverlayMessage(Component.literal("Your civilization is at its highest age."));
            return;
        }
        int[] cost = RtsCivilization.advanceCost(age);
        if (!RtsEconomy.canAfford(player, cost)) {
            player.sendOverlayMessage(Component.literal("Not enough resources to advance to the "
                    + RtsCivilization.AGE_NAMES[age + 1] + "."));
            return;
        }
        RtsEconomy.spend(player, cost);
        RtsCivilization.setAge(player, age + 1);
        Component message = Component.literal("Your civilization has advanced to the "
                + RtsCivilization.AGE_NAMES[age + 1] + ".");
        player.sendSystemMessage(message);
        player.sendOverlayMessage(message);
        // Advancing unearths a relic of the ages that fell before — a permanent production boon.
        String relic = RtsCivilization.recoverRelic(player);
        player.sendSystemMessage(Component.literal("From the ruins you recover the " + relic + "."));
    }

    /** Records completion only after the client has unlocked the guide's final-page close button. */
    private static void onGuideCompleted(GuideCompletedPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && RtsMode.isActive(player)) {
            RtsMode.setWelcomed(player, true);
        }
    }

    /** Recentres the RTS camera by teleporting the (spectator) player over their Town Hall. */
    private static void onHomeRequested(RequestHomePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            if (!entry.owner().equals(player.getUUID())
                    || !BuildingCosts.get(entry.structure()).townHall()) {
                continue;
            }
            Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
            if (found.isEmpty() || found.get().palettes.isEmpty()) {
                continue;
            }
            StructureTemplate template = found.get();
            BlockPos origin = entry.origin();
            if (!entry.normalizedOrigin()) {
                BlockPos offset = template.getZeroPositionWithTransform(BlockPos.ZERO, Mirror.NONE,
                        entry.rotation());
                origin = origin.offset(-offset.getX(), -offset.getY(), -offset.getZ());
            }
            Vec3i size = BuildingPlacement.rotateSize(template.getSize(), entry.rotation());
            double cx = origin.getX() + size.getX() * 0.5D;
            double cz = origin.getZ() + size.getZ() * 0.5D;
            int bx = (int) Math.floor(cx);
            int bz = (int) Math.floor(cz);
            level.getChunk(bx >> 4, bz >> 4);
            double cy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + 2.0D;
            player.teleportTo(cx, cy, cz);
            return;
        }
        player.sendOverlayMessage(Component.literal("No Town Hall to return to."));
    }

    /** Resolves a free-cursor click against the server's tracked buildings. */
    private static void onSelectionRequested(RequestBuildingSelectionPayload payload,
                                              IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null || !RtsMode.isActive(player)) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        RtsConstructionStore constructionStore = RtsConstructionStore.get(level);
        Optional<RtsConstructionStore.Entry> construction = constructionStore.findAt(player.getUUID(),
                entry -> {
                    ConstructionInfo info = describeConstruction(level, entry);
                    return info != null && new AABB(info.origin().getX(), info.origin().getY(),
                            info.origin().getZ(), info.origin().getX() + info.sizeX(),
                            info.origin().getY() + info.sizeY(), info.origin().getZ() + info.sizeZ())
                            .contains(payload.clicked().getX() + 0.5D,
                                    payload.clicked().getY() + 0.5D,
                                    payload.clicked().getZ() + 0.5D);
                });
        if (construction.isPresent()) {
            ConstructionInfo info = describeConstruction(level, construction.get());
            context.reply(new BuildingSelectionPayload(Optional.empty(), Optional.ofNullable(info)));
            context.reply(MineStatusPayload.clear());
            context.reply(FarmStatusPayload.clear());
            return;
        }

        RtsBuildingStore store = RtsBuildingStore.get(level);
        Optional<RtsBuildingStore.Entry> found = store.findAt(player.getUUID(), entry -> {
            AABB bounds = boundsFor(server, entry);
            return bounds != null && bounds.contains(
                    payload.clicked().getX() + 0.5D,
                    payload.clicked().getY() + 0.5D,
                    payload.clicked().getZ() + 0.5D);
        });

        if (found.isEmpty()) {
            context.reply(new BuildingSelectionPayload(Optional.empty()));
            context.reply(MineStatusPayload.clear());
            context.reply(FarmStatusPayload.clear());
            return;
        }

        PlacedBuildingInfo info = describe(server, found.get());
        context.reply(new BuildingSelectionPayload(info == null
                ? Optional.empty()
                : Optional.of(info)));
        context.reply(info != null && RtsMineOrders.isMineStructure(info.structure())
                ? RtsMineOrders.statusFor((ServerLevel) player.level(), found.get())
                : MineStatusPayload.clear());
        context.reply(info != null && RtsFarmOrders.isFarmStructure(info.structure())
                ? RtsFarmOrders.statusFor((ServerLevel) player.level(), found.get())
                : FarmStatusPayload.clear());
        sendBuildingHealth(player);
    }

    /** Moves or upgrades a tracked building after re-validating the complete request server-side. */
    private static void onActionRequested(BuildingActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null || !RtsMode.isActive(player)) {
            return;
        }
        if (payload.action() == BuildingActionPayload.Action.PLACE) {
            refuse(player, Component.translatable("message.forgotten_realms_rts.invalid_building_action"));
            return;
        }

        RtsBuildingStore store = RtsBuildingStore.get((ServerLevel) player.level());
        RtsBuildingDurability.migrate((ServerLevel) player.level());
        Optional<RtsBuildingStore.Entry> found = store.find(payload.buildingId(), player.getUUID());
        if (found.isEmpty()) {
            refuse(player, Component.translatable("message.forgotten_realms_rts.building_unavailable"));
            return;
        }
        RtsBuildingStore.Entry current = found.get();
        if (BuildingPlacement.isLinearStructure(current.structure())
                && payload.action() == BuildingActionPayload.Action.MOVE) {
            moveLinearBuilding(context, player, server, store, current, payload.destination());
            return;
        }
        if (BuildingPlacement.isLinearStructure(current.structure())
                && payload.action() == BuildingActionPayload.Action.UPGRADE) {
            refuse(player, Component.translatable("message.forgotten_realms_rts.no_upgrade"));
            return;
        }
        Optional<StructureTemplate> oldFound = RtsStructureTemplates.get(server, current.structure());
        if (oldFound.isEmpty() || oldFound.get().palettes.isEmpty()) {
            refuse(player, BuildingPlacement.Result.UNKNOWN_STRUCTURE);
            return;
        }

        if (payload.action() == BuildingActionPayload.Action.DEMOLISH) {
            if (BuildingCosts.get(current.structure()).townHall()) {
                refuse(player, Component.literal("The Town Hall cannot be deleted."));
                return;
            }
            demolish(context, player, store, current, oldFound.get());
            return;
        }

        Identifier targetId = payload.action() == BuildingActionPayload.Action.UPGRADE
                ? nextLevel(current.structure())
                : current.structure();
        if (targetId == null) {
            refuse(player, Component.translatable("message.forgotten_realms_rts.no_upgrade"));
            return;
        }
        Optional<StructureTemplate> targetFound = RtsStructureTemplates.get(server, targetId);
        if (targetFound.isEmpty() || targetFound.get().palettes.isEmpty()) {
            refuse(player, Component.translatable("message.forgotten_realms_rts.no_upgrade"));
            return;
        }

        BuildingCosts.Definition definition = BuildingCosts.get(targetId);
        if (payload.action() == BuildingActionPayload.Action.UPGRADE
                && !RtsCivilization.isFounded(player)) {
            refuse(player, BuildingPlacement.Result.FOUNDING_REQUIRED);
            return;
        }
        if (payload.action() == BuildingActionPayload.Action.UPGRADE
                && definition.minAge() > RtsCivilization.age(player)) {
            refuse(player, Component.literal("Requires "
                    + RtsCivilization.AGE_NAMES[Math.min(definition.minAge(),
                    RtsCivilization.MAX_AGE)]));
            return;
        }
        int[] upgradeCost = BuildingCosts.upgradeCost(targetId);
        if (payload.action() == BuildingActionPayload.Action.UPGRADE
                && !RtsEconomy.canAfford(player, upgradeCost)) {
            refuse(player, BuildingPlacement.Result.UNAFFORDABLE);
            return;
        }

        Rotation rotation = payload.action() == BuildingActionPayload.Action.UPGRADE
                ? current.rotation()
                : payload.rotation();
        StructureTemplate oldTemplate = oldFound.get();
        BlockPos currentOrigin = normalizedOrigin(oldTemplate, current);
        ServerLevel level = (ServerLevel) player.level();
        StructureSanitizer.sanitizePlacedStructure(level, oldTemplate, currentOrigin,
                current.rotation());
        BlockPos destination = payload.action() == BuildingActionPayload.Action.UPGRADE
                ? currentOrigin
                : payload.destination();
        StructureTemplate targetTemplate = targetFound.get();
        Vec3i targetSize = BuildingPlacement.rotateSize(targetTemplate.getSize(), rotation);
        Set<Long> targetSolid = solidFootprint(targetTemplate, targetTemplate.getSize(), rotation);
        Set<Long> ignored = worldFootprint(currentOrigin,
                solidFootprint(oldTemplate, oldTemplate.getSize(), current.rotation()));

        BuildingPlacement.Result geometry = BuildingPlacement.checkGeometry(
                player.level(), destination, targetSize,
                (x, y, z) -> targetSolid.contains(BlockPos.asLong(x, y, z)), ignored);
        if (!geometry.ok()) {
            refuse(player, geometry);
            return;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        BlockPos placementPosition = placementPosition(targetTemplate, destination, rotation);
        Set<Long> targetWorld = worldFootprint(destination, targetSolid);
        Set<Long> transactionPositions = new HashSet<>(ignored);
        transactionPositions.addAll(targetWorld);
        Map<Long, BlockState> originalStates = snapshotStates(level, transactionPositions);
        // StructureTemplate does not reliably overwrite every non-replaceable source block. Clear
        // the old footprint before writing, including same-place moves, then restore it on error.
        clearFootprint(level, ignored);
        boolean placed = targetTemplate.placeInWorld(level, placementPosition, placementPosition, settings,
                level.getRandom(), Block.UPDATE_ALL);
        if (!placed) {
            restoreStates(level, originalStates);
            refuse(player, BuildingPlacement.Result.UNKNOWN_STRUCTURE);
            return;
        }
        StructureSanitizer.sanitizePlacedStructure(level, targetTemplate, destination, rotation);

        if (payload.action() == BuildingActionPayload.Action.UPGRADE) {
            RtsEconomy.spend(player, upgradeCost);
            if (definition.townHall()) {
                // An upgraded Town Hall is a rebuilt fortress: refill it at its new, dramatic cap.
                RtsBattle.applyTownHallUpgrade(player, levelOf(targetId));
            }
        }
        int updatedMaxHealth = payload.action() == BuildingActionPayload.Action.UPGRADE
                ? RtsBuildingDurability.maxHealth(level, targetId)
                : current.maxHealth();
        int updatedHealth = payload.action() == BuildingActionPayload.Action.UPGRADE
                ? updatedMaxHealth : current.health();
        RtsBuildingStore.Entry updated = new RtsBuildingStore.Entry(
                current.id(), current.owner(), targetId, destination, rotation, true,
                updatedHealth, updatedMaxHealth);
        store.update(updated);
        sendBuildingUpdate(context, player, server, updated, payload.action());
    }

    /** Moves one tracked path/wall voxel while preserving its current durability. */
    private static void moveLinearBuilding(IPayloadContext context, ServerPlayer player,
                                           MinecraftServer server, RtsBuildingStore store,
                                           RtsBuildingStore.Entry current, BlockPos destination) {
        ServerLevel level = (ServerLevel) player.level();
        if (destination == null || destination.equals(current.origin())
                || linearOverTrackedStructure(level, List.of(destination), false)) {
            refuse(player, BuildingPlacement.Result.OBSTRUCTED);
            return;
        }
        boolean path = BuildingPlacement.isPathStructure(current.structure());
        BuildingPlacement.Result geometry = BuildingPlacement.checkLinearGeometry(level,
                List.of(destination), path, Set.of(current.origin().asLong()));
        if (!geometry.ok()) {
            refuse(player, geometry);
            return;
        }
        BlockState state = level.getBlockState(current.origin());
        Map<Long, BlockState> original = snapshotStates(level,
                Set.of(current.origin().asLong(), destination.asLong()));
        level.setBlock(current.origin(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (!level.setBlock(destination, state, Block.UPDATE_ALL)) {
            restoreStates(level, original);
            refuse(player, BuildingPlacement.Result.UNKNOWN_STRUCTURE);
            return;
        }
        RtsBuildingStore.Entry updated = new RtsBuildingStore.Entry(current.id(), current.owner(),
                current.structure(), destination, Rotation.NONE, true,
                current.health(), current.maxHealth());
        store.update(updated);
        sendBuildingUpdate(context, player, server, updated, BuildingActionPayload.Action.MOVE);
    }

    /**
     * Airs a demolished building's footprint and drops its store entry. Workers are released to
     * IDLE before the entry disappears, because {@link RtsMineOrders#tick} and
     * {@link RtsFarmOrders#tick} re-resolve the entry every pass and would otherwise treat a demolish
     * exactly like an abandoned building and send its workers marching back to the Town Hall.
     */
    private static void demolish(IPayloadContext context, ServerPlayer player, RtsBuildingStore store,
                                 RtsBuildingStore.Entry entry, StructureTemplate template) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos origin = normalizedOrigin(template, entry);
        StructureSanitizer.sanitizePlacedStructure(level, template, origin, entry.rotation());
        Vec3i size = RtsBuildingDurability.rotatedSize(level, entry);
        Set<Long> footprint = new HashSet<>();
        for (BlockPos block : RtsBuildingDurability.trackedBlocks(level, entry)) {
            footprint.add(block.asLong());
        }

        BlockPos scanCenter = RtsEntities.townHallGroundCenter(player).orElse(player.blockPosition());
        RtsMineOrders.releaseWorkers(level, scanCenter, entry.id());
        RtsFarmOrders.releaseWorkers(level, scanCenter, entry.id());
        RtsRepairOrders.releaseWorkers(level, entry.id());

        clearFootprint(level, footprint);
        store.remove(entry.id(), player.getUUID());
        RtsEntities.reconcilePopulation(player, scanCenter);

        // Drop the now-dead snapshot so the HUD cannot keep showing a building that no longer exists.
        context.reply(new BuildingSelectionPayload(Optional.empty()));
        context.reply(MineStatusPayload.clear());
        context.reply(FarmStatusPayload.clear());
        context.reply(new BuildingEffectPayload(origin, size.getX(), size.getY(), size.getZ(),
                BuildingActionPayload.Action.DEMOLISH));
        sendBuildingHealth(player);
        BuildingActionBurstEffect.play(level,
                origin.getX() + size.getX() / 2.0D,
                origin.getY() + Math.max(0.5D, size.getY() * 0.5D),
                origin.getZ() + size.getZ() / 2.0D);
    }

    /**
     * Finishes a block-by-block foundation and promotes it to the normal tracked-building store.
     * This method is called by the server tick, so it deliberately uses the same server-side
     * template and progression state as ordinary placement and never trusts a client completion.
     */
    public static boolean completeConstruction(ServerPlayer player, RtsConstructionStore.Entry requested) {
        if (player == null || requested == null || !RtsMode.isActive(player)) {
            return false;
        }

        ServerLevel level = (ServerLevel) player.level();
        RtsConstructionStore constructionStore = RtsConstructionStore.get(level);
        RtsConstructionStore.Entry entry = constructionStore.find(requested.id(), player.getUUID())
                .orElse(null);
        if (entry == null || entry.placedBlocks() < entry.totalBlocks()) {
            return false;
        }
        Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
        if (found.isEmpty() || found.get().palettes.isEmpty()) {
            return false;
        }

        StructureTemplate template = found.get();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(entry.rotation());
        BlockPos placementPosition = placementPosition(template, entry.origin(), entry.rotation());
        boolean placed = template.placeInWorld(level, placementPosition, placementPosition,
                settings, level.getRandom(), Block.UPDATE_ALL);
        if (!placed) {
            return false;
        }
        StructureSanitizer.sanitizePlacedStructure(level, template, entry.origin(), entry.rotation());

        boolean onboardingCoalMine = BuildingCosts.isFirstCoalMinePlacement(entry.structure(), player);
        BuildingCosts.Definition definition = BuildingCosts.get(entry.structure());
        RtsConstructionOrders.releaseWorkers(level, entry.id());
        constructionStore.remove(entry.id(), player.getUUID());

        RtsBuildingStore.Entry building = RtsBuildingStore.get(level).add(
                level, player.getUUID(), entry.structure(), entry.origin(), entry.rotation());
        if (definition.townHall()) {
            RtsEconomy.setTownHallPlaced(player, true);
            RtsBattle.applyTownHallUpgrade(player, levelOf(entry.structure()));
        } else if (onboardingCoalMine) {
            RtsEconomy.setCoalMinePlaced(player, true);
        }
        if (RtsCivilization.isFounded(player)) {
            RtsEntities.townHallGroundCenter(player).ifPresent(center ->
                    RtsEntities.reconcilePopulation(player, center));
        }

        PlacedBuildingInfo info = describe(level.getServer(), building);
        PacketDistributor.sendToPlayer(player, new BuildingSelectionPayload(
                info == null ? Optional.empty() : Optional.of(info)));
        PacketDistributor.sendToPlayer(player, RtsMineOrders.isMineStructure(entry.structure())
                ? RtsMineOrders.statusFor(level, building) : MineStatusPayload.clear());
        PacketDistributor.sendToPlayer(player, RtsFarmOrders.isFarmStructure(entry.structure())
                ? RtsFarmOrders.statusFor(level, building) : FarmStatusPayload.clear());
        PacketDistributor.sendToPlayer(player, new BuildingEffectPayload(entry.origin(),
                BuildingPlacement.rotateSize(template.getSize(), entry.rotation()).getX(),
                BuildingPlacement.rotateSize(template.getSize(), entry.rotation()).getY(),
                BuildingPlacement.rotateSize(template.getSize(), entry.rotation()).getZ(),
                BuildingActionPayload.Action.PLACE));
        BuildingActionBurstEffect.play(level,
                entry.origin().getX() + template.getSize().getX() / 2.0D,
                entry.origin().getY() + Math.max(0.5D, template.getSize().getY() * 0.5D),
                entry.origin().getZ() + template.getSize().getZ() / 2.0D);
        RtsBattle.syncTownHallBuilding(player);
        sendBuildingHealth(player);

        if (definition.townHall()) {
            if (!RtsCivilization.isFounded(player)) {
                PacketDistributor.sendToPlayer(player, new OpenFoundingPayload());
            } else {
                RtsEntities.ensureTownWorker(player);
            }
        }
        if (onboardingCoalMine) {
            PacketDistributor.sendToPlayer(player, new BuildingCatalogPayload(
                    describeCatalog(level.getServer().getStructureManager().listTemplates().toList(), player)));
        }
        player.sendOverlayMessage(Component.literal("Construction complete: " + definition.name() + "."));
        return true;
    }

    private static void sendBuildingUpdate(IPayloadContext context, ServerPlayer player,
                                            MinecraftServer server,
                                            RtsBuildingStore.Entry entry,
                                            BuildingActionPayload.Action action) {
        PlacedBuildingInfo info = describe(server, entry);
        if (info == null) {
            // describe() failing here (missing/renamed template) must not leave the client showing a
            // stale snapshot with nothing to refresh it; drop the selection instead of silently
            // returning.
            if (action != BuildingActionPayload.Action.PLACE) {
                context.reply(new BuildingSelectionPayload(Optional.empty()));
                context.reply(MineStatusPayload.clear());
                context.reply(FarmStatusPayload.clear());
            }
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (action != BuildingActionPayload.Action.PLACE) {
            context.reply(new BuildingSelectionPayload(Optional.of(info)));
            // Mine/farm capacity and yield change with level; onSelectionRequested sends this on a
            // fresh click, but an upgrade reply has to resend it too or the panel keeps showing the
            // pre-upgrade numbers until the player clicks away and back.
            context.reply(RtsMineOrders.isMineStructure(info.structure())
                    ? RtsMineOrders.statusFor(level, entry)
                    : MineStatusPayload.clear());
            context.reply(RtsFarmOrders.isFarmStructure(info.structure())
                    ? RtsFarmOrders.statusFor(level, entry)
                    : FarmStatusPayload.clear());
        }
        context.reply(new BuildingEffectPayload(info.origin(), info.sizeX(), info.sizeY(), info.sizeZ(),
                action));
        BuildingActionBurstEffect.play(level,
                info.origin().getX() + info.sizeX() / 2.0D,
                info.origin().getY() + Math.max(0.5D, info.sizeY() * 0.5D),
                info.origin().getZ() + info.sizeZ() / 2.0D);
        sendBuildingHealth(player);
    }

    /** Sends the current durable-building snapshot to the owner. */
    public static void sendBuildingHealth(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        RtsBuildingDurability.migrate(level);
        List<BuildingHealthPayload.BuildingHealth> health = new ArrayList<>();
        MinecraftServer server = level.getServer();
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            if (!entry.owner().equals(player.getUUID())) {
                continue;
            }
            PlacedBuildingInfo info = describe(server, entry);
            if (info == null || info.maxHealth() <= 0) {
                continue;
            }
            health.add(new BuildingHealthPayload.BuildingHealth(entry.id(), entry.structure(),
                    info.origin(), info.sizeX(), info.sizeY(), info.sizeZ(),
                    entry.health(), entry.maxHealth()));
        }
        PacketDistributor.sendToPlayer(player, new BuildingHealthPayload(health));
    }

    /** Builds the compact client snapshot for a structure that has not finished assembling yet. */
    public static ConstructionInfo describeConstruction(ServerLevel level,
                                                         RtsConstructionStore.Entry entry) {
        if (level == null || entry == null) {
            return null;
        }
        Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
        if (found.isEmpty() || found.get().palettes.isEmpty()) {
            return null;
        }
        StructureTemplate template = found.get();
        Vec3i size = BuildingPlacement.rotateSize(template.getSize(), entry.rotation());
        BuildingCosts.Definition definition = BuildingCosts.get(entry.structure());
        return new ConstructionInfo(entry.id(), entry.structure(), entry.origin(), entry.rotation(),
                size.getX(), size.getY(), size.getZ(), levelOf(entry.structure()), definition.name(),
                entry.totalBlocks(), entry.placedBlocks(),
                RtsConstructionOrders.assignedWorkerCount(level, entry));
    }

    private static PlacedBuildingInfo describe(MinecraftServer server, RtsBuildingStore.Entry entry) {
        Optional<StructureTemplate> found = RtsStructureTemplates.get(server, entry.structure());
        if (found.isEmpty() || found.get().palettes.isEmpty()) {
            return null;
        }
        BlockPos origin = normalizedOrigin(found.get(), entry);
        Vec3i size = BuildingPlacement.isLinearStructure(entry.structure())
                ? new Vec3i(1, 1, 1)
                : BuildingPlacement.rotateSize(found.get().getSize(), entry.rotation());
        BuildingCosts.Definition definition = BuildingCosts.get(entry.structure());
        Identifier upgrade = nextLevel(entry.structure());
        if (upgrade != null && RtsStructureTemplates.get(server, upgrade).isEmpty()) {
            upgrade = null;
        }
        int[] upgradeCosts = upgrade == null
                ? new int[Resource.COUNT] : BuildingCosts.upgradeCost(upgrade);
        return new PlacedBuildingInfo(entry.id(), entry.structure(), origin, entry.rotation(),
                size.getX(), size.getY(), size.getZ(), levelOf(entry.structure()), definition.name(),
                Arrays.copyOf(definition.costs(), Resource.COUNT), upgradeCosts, upgrade,
                entry.health(), entry.maxHealth());
    }

    private static AABB boundsFor(MinecraftServer server, RtsBuildingStore.Entry entry) {
        Optional<StructureTemplate> found = RtsStructureTemplates.get(server, entry.structure());
        if (found.isEmpty()) {
            return null;
        }
        ServerLevel level = server.overworld();
        BlockPos origin = RtsBuildingDurability.normalizedOrigin(level, entry);
        Vec3i size = RtsBuildingDurability.rotatedSize(level, entry);
        return new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(),
                origin.getZ() + size.getZ());
    }

    private static Identifier nextLevel(Identifier structure) {
        String path = structure.getPath();
        int slash = path.lastIndexOf('/');
        String leaf = slash >= 0 ? path.substring(slash + 1) : path;
        String nextLeaf;
        Matcher plain = LEVEL_SUFFIX.matcher(leaf);
        if (plain.matches()) {
            nextLeaf = "level-" + (levelOf(structure) + 1);
        } else {
            Matcher combined = COMBINED_LEVEL_SUFFIX.matcher(leaf);
            if (!combined.matches()) {
                return null;
            }
            nextLeaf = combined.group(1) + "-level-" + (levelOf(structure) + 1);
        }
        return Identifier.fromNamespaceAndPath(structure.getNamespace(),
                path.substring(0, slash + 1) + nextLeaf);
    }

    private static Set<Long> worldFootprint(BlockPos origin, Set<Long> localPositions) {
        Set<Long> world = new HashSet<>(localPositions.size());
        for (long packed : localPositions) {
            BlockPos local = BlockPos.of(packed);
            world.add(origin.offset(local.getX(), local.getY(), local.getZ()).asLong());
        }
        return world;
    }

    /**
     * Converts the stored origin, which is the minimum corner of the rotated footprint, to the
     * position Minecraft's structure transform expects. StructureTemplate rotates around a zero
     * pivot; a quarter turn therefore produces negative local coordinates unless this corner offset
     * is applied before placement. The same normalized convention is used by
     * {@link BuildingPlacement#rotateOffset(int, int, int, Vec3i, Rotation)} and the client ghost.
     */
    private static BlockPos placementPosition(StructureTemplate template, BlockPos origin,
                                              Rotation rotation) {
        return template.getZeroPositionWithTransform(origin, Mirror.NONE, rotation);
    }

    /**
     * Converts records written before the normalized-origin fix. Those records stored the raw
     * StructureTemplate placement position, so their rotated blocks were offset from the saved
     * origin. The optional codec field makes old SavedData load as legacy and new entries explicit.
     */
    private static BlockPos normalizedOrigin(StructureTemplate template, RtsBuildingStore.Entry entry) {
        if (entry.normalizedOrigin()) {
            return entry.origin();
        }
        BlockPos offset = template.getZeroPositionWithTransform(BlockPos.ZERO, Mirror.NONE,
                entry.rotation());
        return entry.origin().offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    /**
     * A path may join another path, but it must never be laid on top of a tracked building or use
     * one as its support. The ordinary geometry check catches blocks directly in the way; this
     * second pass closes the less obvious roof case where a path is one block above a building and
     * would otherwise be accepted because the building's top is valid ground.
     */
    private static boolean pathOverTrackedStructure(ServerLevel level, List<BlockPos> origins,
                                                    Vec3i pathSize, Set<Long> pathSolid) {
        Set<Long> structureBlocks = new HashSet<>();
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            if (BuildingPlacement.isPathStructure(entry.structure())) {
                continue;
            }
            Optional<StructureTemplate> found = RtsStructureTemplates.get(level, entry.structure());
            if (found.isEmpty() || found.get().palettes.isEmpty()) {
                continue;
            }
            StructureTemplate template = found.get();
            BlockPos structureOrigin = normalizedOrigin(template, entry);
            structureBlocks.addAll(worldFootprint(structureOrigin,
                    solidFootprint(template, template.getSize(), entry.rotation())));
        }

        if (structureBlocks.isEmpty()) {
            return false;
        }
        for (BlockPos origin : origins) {
            for (long localPacked : pathSolid) {
                BlockPos local = BlockPos.of(localPacked);
                if (structureBlocks.contains(origin.offset(local.getX(), local.getY(),
                        local.getZ()).asLong())) {
                    return true;
                }
            }
            // A path's foundation is allowed to replace terrain, but not the roof/top block of a
            // tracked structure. Check only occupied base columns so hollow path shapes remain
            // compatible with their own geometry.
            for (int x = 0; x < pathSize.getX(); x++) {
                for (int z = 0; z < pathSize.getZ(); z++) {
                    if (pathSolid.contains(BlockPos.asLong(x, 0, z))
                            && structureBlocks.contains(origin.offset(x, -1, z).asLong())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void clearFootprint(ServerLevel level, Set<Long> positions) {
        for (long packed : positions) {
            level.setBlock(BlockPos.of(packed), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static Map<Long, BlockState> snapshotStates(ServerLevel level, Set<Long> positions) {
        Map<Long, BlockState> states = new HashMap<>(positions.size());
        for (long packed : positions) {
            states.put(packed, level.getBlockState(BlockPos.of(packed)));
        }
        return states;
    }

    private static void restoreStates(ServerLevel level, Map<Long, BlockState> states) {
        for (Map.Entry<Long, BlockState> entry : states.entrySet()) {
            level.setBlock(BlockPos.of(entry.getKey()), entry.getValue(), Block.UPDATE_ALL);
        }
    }

    private static int[] scaledCosts(int[] unitCost, int pieces) {
        int[] total = Arrays.copyOf(unitCost, Resource.COUNT);
        for (int index = 0; index < total.length; index++) {
            long scaled = (long) Math.max(0, total[index]) * Math.max(0, pieces);
            total[index] = (int) Math.min(Integer.MAX_VALUE, scaled);
        }
        return total;
    }

    private static void refuse(ServerPlayer player, BuildingPlacement.Result result) {
        // Action bar, not chat: a refusal is transient feedback about the thing you just tried, and
        // it should not pile up in the chat log. (26.1 has no displayClientMessage; this is it.)
        player.sendOverlayMessage(Component.literal(result.message()));
    }

    private static void refuse(ServerPlayer player, String message) {
        player.sendOverlayMessage(Component.literal(message));
    }

    private static void refuse(ServerPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }

    /**
     * Local positions the structure actually occupies, already rotated, as packed longs.
     *
     * <p>Air and technical editor markers are excluded so the footprint means "the gameplay building
     * is here". A barrier still occupies its block for collision purposes even though the preview
     * does not draw it.
     */
    private static Set<Long> solidFootprint(StructureTemplate template, Vec3i size, Rotation rotation) {
        Set<Long> solid = new HashSet<>();
        for (StructureTemplate.StructureBlockInfo info : template.palettes.get(0).blocks()) {
            if (info.state().isAir() || StructureSanitizer.isTechnicalMarker(info.state())) {
                continue;
            }
            BlockPos pos = info.pos();
            BlockPos rotated = BuildingPlacement.rotateOffset(pos.getX(), pos.getY(), pos.getZ(),
                    size, rotation);
            solid.add(BlockPos.asLong(rotated.getX(), rotated.getY(), rotated.getZ()));
        }
        return solid;
    }

    /**
     * Turns a structure's blocks into a shell: air dropped, fully enclosed blocks dropped, the rest
     * packed against a shared palette.
     *
     * <p>Interior blocks are invisible from every angle at preview size, and a solid building is
     * mostly interior — dropping them is typically a five- to ten-fold reduction with no visual
     * difference whatsoever.
     */
    private static BuildingPreviewPayload buildPreview(Identifier structure, Vec3i size,
                                                       List<StructureTemplate.StructureBlockInfo> all) {
        Set<Long> occluding = new HashSet<>();
        for (StructureTemplate.StructureBlockInfo info : all) {
            if (info.state().canOcclude()) {
                occluding.add(info.pos().asLong());
            }
        }

        Map<Identifier, Integer> paletteIndices = new HashMap<>();
        List<Identifier> palette = new ArrayList<>();
        List<Integer> blocks = new ArrayList<>();

        for (StructureTemplate.StructureBlockInfo info : all) {
            if (blocks.size() >= MAX_PREVIEW_BLOCKS) {
                break;
            }
            BlockState state = info.state();
            if (state.isAir() || StructureSanitizer.isTechnicalMarker(state)
                    || isInvisibleMarker(state) || isEnclosed(info.pos(), occluding)) {
                continue;
            }

            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            Integer index = paletteIndices.get(blockId);
            if (index == null) {
                if (palette.size() >= MAX_PALETTE) {
                    continue;
                }
                index = palette.size();
                paletteIndices.put(blockId, index);
                palette.add(blockId);
            }

            BlockPos pos = info.pos();
            blocks.add(BuildingPreviewPayload.pack(pos.getX(), pos.getY(), pos.getZ(), index));
        }

        return new BuildingPreviewPayload(structure, size.getX(), size.getY(), size.getZ(),
                palette, blocks);
    }

    /**
     * Blocks that are invisible in the world but are <strong>not</strong> air, and so must be left
     * out of a preview while still being placed.
     *
     * <p>Barrier is the one that actually bites: it has an item form, so the item-composite preview
     * renderer drew its red-and-white "no entry" icon, and the tray filled up with them. They are
     * load-bearing in these structures and deleting them would break the builds — the watchtower has
     * a column of eight at {@code (1, 1..8, 2)} that its east-facing ladder shaft attaches to, and
     * {@code space/level-1} and {@code level-2} each cap a lit campfire with one. <strong>Preview
     * only; {@code placeInWorld} still writes them.</strong> Technical markers are removed by
     * {@link StructureSanitizer} and are not placed.
     */
    private static boolean isInvisibleMarker(BlockState state) {
        return state.is(Blocks.BARRIER) || state.is(Blocks.LIGHT);
    }

    private static boolean isEnclosed(BlockPos pos, Set<Long> occluding) {
        for (Direction direction : Direction.values()) {
            if (!occluding.contains(pos.relative(direction).asLong())) {
                return false;
            }
        }
        return true;
    }

}
