package com.hyrrx.forgottenrealmsrts.network;

import com.hyrrx.forgottenrealmsrts.client.build.BuildGhost;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingSelectionController;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingPreviewShape;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingActionEffects;
import com.hyrrx.forgottenrealmsrts.client.ui.FoundingScreen;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsBuildingPreview;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsGuideScreen;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsHudState;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsSidePanelHud;
import com.hyrrx.forgottenrealmsrts.client.RtsSpectateClientState;
import com.hyrrx.forgottenrealmsrts.client.RtsBuildingHealthBarRenderer;
import com.hyrrx.forgottenrealmsrts.client.NaturalTreeTransparencyController;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Where the two server→client buildings payloads land.
 *
 * <p>Separate from {@link ModPayloads} because that class is loaded on a dedicated server, and this
 * one touches client-only state. {@code ModPayloads} refers to these as method references, which
 * the JVM does not resolve until the lambda is invoked — and on a server it never is.
 *
 * <p>Both handlers run on the client's main thread (that is the payload registrar's default), so
 * they can write {@link RtsHudState} directly, which is the same thread the HUD renders from.
 *
 * <p><strong>Deliberately not annotated {@code @OnlyIn(Dist.CLIENT)}.</strong> NeoForge 26.1 logs
 * an ERROR for every use of that annotation — its runtime member-stripping behaviour was removed,
 * so it now only misleads. Lazy class loading is what actually keeps this off a dedicated server,
 * and it does so whether the annotation is present or not.
 */
public final class ClientPayloadHandlers {
    private static final String PROTOCOL_VERSION = "8";
    /** Set when the server asks for the guide before the client has finished entering the world. */
    private static boolean guidePending;
    /** Client level identity used to discard static selection state across world/dimension changes. */
    private static Object observedLevel;

    private ClientPayloadHandlers() {
    }

    /** Registers client-bound payloads only from the client bootstrap path. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientPayloadHandlers::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(ClientPayloadHandlers::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientPayloadHandlers::onClientLoggingOut);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(OpenFoundingPayload.TYPE,
                OpenFoundingPayload.STREAM_CODEC, ClientPayloadHandlers::handleOpenFounding);
        registrar.playToClient(OpenGuidePayload.TYPE,
                OpenGuidePayload.STREAM_CODEC, ClientPayloadHandlers::handleOpenGuide);
        registrar.playToClient(BuildingCatalogPayload.TYPE,
                BuildingCatalogPayload.STREAM_CODEC, ClientPayloadHandlers::handleCatalog);
        registrar.playToClient(BuildingPreviewPayload.TYPE,
                BuildingPreviewPayload.STREAM_CODEC, ClientPayloadHandlers::handlePreview);
        registrar.playToClient(BuildingSelectionPayload.TYPE,
                BuildingSelectionPayload.STREAM_CODEC, ClientPayloadHandlers::handleSelection);
        registrar.playToClient(MineStatusPayload.TYPE,
                MineStatusPayload.STREAM_CODEC, ClientPayloadHandlers::handleMineStatus);
        registrar.playToClient(FarmStatusPayload.TYPE,
                FarmStatusPayload.STREAM_CODEC, ClientPayloadHandlers::handleFarmStatus);
        registrar.playToClient(BuildingEffectPayload.TYPE,
                BuildingEffectPayload.STREAM_CODEC, ClientPayloadHandlers::handleEffect);
        registrar.playToClient(NewTownReadyPayload.TYPE,
                NewTownReadyPayload.STREAM_CODEC, ClientPayloadHandlers::handleNewTownReady);
        registrar.playToClient(MoonStatePayload.TYPE,
                MoonStatePayload.STREAM_CODEC, ClientPayloadHandlers::handleMoonState);
        registrar.playToClient(BuildingHealthPayload.TYPE,
                BuildingHealthPayload.STREAM_CODEC, ClientPayloadHandlers::handleBuildingHealth);
    }

    static void handleCatalog(BuildingCatalogPayload payload, IPayloadContext context) {
        RtsHudState.acceptCatalog(payload.byCategory());
    }

    static void handlePreview(BuildingPreviewPayload payload, IPayloadContext context) {
        // Two consumers of the same blocks: the tray's flat icon and the in-world ghost's massing.
        // Decoding once here is why siting a building costs no extra network traffic.
        RtsBuildingPreview.accept(payload);
        BuildingPreviewShape.accept(payload);
    }

    static void handleSelection(BuildingSelectionPayload payload, IPayloadContext context) {
        // The same response is either a normal panel selection or the acknowledgement that lets a
        // pending selected-worker order identify its target. Resolve the command first so a consumed
        // mine response is not immediately reapplied as an ordinary mine-panel selection.
        boolean consumedByUnitCommand;
        if (payload.construction().isPresent()) {
            consumedByUnitCommand = BuildingSelectionController.resolvePendingConstructionSelection(
                    payload.construction().orElse(null));
        } else {
            consumedByUnitCommand = BuildingSelectionController.resolvePendingBuildingSelection(
                    payload.building().orElse(null));
        }
        if (suppressOrdinaryBuildingSelection(consumedByUnitCommand)) {
            // A selected worker clicking a mine uses this response only to discover the server's
            // tracked id. The order resolver already cleared the unit selection; do not immediately
            // turn the same response back into a selected mine panel.
            RtsHudState.setSelectedPlacedBuilding(null);
            RtsHudState.setSelectedConstruction(null);
            RtsHudState.setSelectedBuilding(null);
            return;
        }
        RtsHudState.setSelectedPlacedBuilding(payload.building().orElse(null));
        RtsHudState.setSelectedConstruction(payload.construction().orElse(null));
        RtsHudState.setSelectedBuilding(null);
    }

    /** Package-private so the focused client-order check can pin down this response-consumer rule. */
    static boolean suppressOrdinaryBuildingSelection(boolean consumedByUnitCommand) {
        return consumedByUnitCommand;
    }

    static void handleMineStatus(MineStatusPayload payload, IPayloadContext context) {
        RtsHudState.setSelectedMineStatus(payload);
    }

    static void handleFarmStatus(FarmStatusPayload payload, IPayloadContext context) {
        RtsHudState.setSelectedFarmStatus(payload);
    }

    static void handleOpenFounding(OpenFoundingPayload payload, IPayloadContext context) {
        // The first Town Hall finished placing: open the founding screen. The player names the realm
        // and designs a banner, then confirms — that is what actually founds the civilization.
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof FoundingScreen)) {
            minecraft.setScreen(new FoundingScreen());
        }
    }

    static void handleOpenGuide(OpenGuidePayload payload, IPayloadContext context) {
        // PlayerLoggedInEvent can reach the client while the terrain/loading screen still owns the
        // window. Keep the request instead of dropping it when that screen is present.
        guidePending = true;
        tryOpenGuide();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != observedLevel) {
            if (observedLevel != null) {
                BuildGhost.cancel();
                RtsHudState.clearSelection();
                BuildingSelectionController.clearTransientState();
                RtsSidePanelHud.clearTransientState();
                RtsBuildingHealthBarRenderer.clear();
                NaturalTreeTransparencyController.clear();
            }
            observedLevel = minecraft.level;
        }
        if (guidePending) {
            tryOpenGuide();
        }
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        guidePending = false;
        observedLevel = null;
        BuildGhost.cancel();
        RtsHudState.clearSelection();
        BuildingSelectionController.clearTransientState();
        RtsSidePanelHud.clearTransientState();
        RtsBuildingHealthBarRenderer.clear();
        NaturalTreeTransparencyController.clear();
    }

    private static void tryOpenGuide() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!guidePending || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen instanceof RtsGuideScreen) {
            guidePending = false;
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        minecraft.setScreen(new RtsGuideScreen());
        guidePending = false;
    }

    static void handleEffect(BuildingEffectPayload payload, IPayloadContext context) {
        if (payload.action() == BuildingActionPayload.Action.PLACE
                && BuildGhost.mode() == BuildGhost.Mode.PLACE) {
            BuildGhost.cancel();
        }
        BuildingActionEffects.accept(payload);
    }

    static void handleNewTownReady(NewTownReadyPayload payload, IPayloadContext context) {
        BuildGhost.cancel();
        RtsSpectateClientState.clear();
        RtsHudState.clearSelection();
        RtsHudState.startNewTownGuide();
        RtsBuildingHealthBarRenderer.clear();
        NaturalTreeTransparencyController.clear();
        Minecraft.getInstance().setScreen(null);
    }

    static void handleMoonState(MoonStatePayload payload, IPayloadContext context) {
        com.hyrrx.forgottenrealmsrts.client.RtsMoonVisuals.setForcedMoon(payload.moon());
    }

    static void handleBuildingHealth(BuildingHealthPayload payload, IPayloadContext context) {
        RtsBuildingHealthBarRenderer.accept(payload);
        for (BuildingHealthPayload.BuildingHealth building : payload.buildings()) {
            RtsHudState.updateSelectedPlacedBuildingHealth(building.id(), building.health(),
                    building.maxHealth());
        }
    }
}
