package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.client.build.BuildGhost;
import com.hyrrx.forgottenrealmsrts.client.build.BuildGhostRenderer;
import com.hyrrx.forgottenrealmsrts.network.ClientPayloadHandlers;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingActionEffectRenderer;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingSelectionController;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingSelectionRenderer;
import com.hyrrx.forgottenrealmsrts.client.camera.IsometricCameraController;
import com.hyrrx.forgottenrealmsrts.client.input.RtsKeyBindings;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsBottomBarHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsBuildingPreview;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsCursorOverlay;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsMinimap;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsSidePanelHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsSpectateHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsTopBarHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsUnitSelectionOverlay;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsVanillaHudSuppressor;
import net.neoforged.bus.api.IEventBus;

public final class ForgottenRealmsRTSClient {
   private ForgottenRealmsRTSClient() {
   }

   public static void register(IEventBus modEventBus) {
      ClientPayloadHandlers.register(modEventBus);
      RtsMouseController.register();
      RtsGuiScaleGuard.register();
      IsometricCameraController.register();
      RtsKeyBindings.register(modEventBus);
      BuildGhost.register();
      BuildingSelectionController.register();
      BuildGhostRenderer.register();
      BuildingSelectionRenderer.register();
      BuildingActionEffectRenderer.register();
      RtsVillagerRenderer.register(modEventBus);
      RtsSoldierRenderer.register(modEventBus);
      RtsArcherRenderer.register(modEventBus);
      RtsSpearmanRenderer.register(modEventBus);
      RtsCrossbowmanRenderer.register(modEventBus);
      RtsLightningBoltRenderer.register(modEventBus);
      RtsMoonVisuals.register();
      RtsMusicController.register();
      FallenSoldierRenderer.register(modEventBus);
      SkeletalArcherRenderer.register(modEventBus);
      SamuraiZombieRenderer.register(modEventBus);
      FallenKnightRenderer.register(modEventBus);
      FallenBruteRenderer.register(modEventBus);
      RtsUnitThroughWallOutline.register();
      RtsUnitPathRenderer.register();
      RtsVillagerHealthBarRenderer.register();
      RtsBuildingHealthBarRenderer.register();
      RtsMineStatusRenderer.register();
      RtsFarmStatusRenderer.register();
      RtsVanillaHudSuppressor.register();
      RtsMinimap.register();
      RtsOutcomeWatcher.register();
      RtsBuildingPreview.register();
      RtsUnitSelectionOverlay.register();
      RtsTopBarHud.register();
      RtsBottomBarHud.register();
      RtsSidePanelHud.register();
      RtsSpectateHud.register();
      RtsCursorOverlay.register();
   }
}
