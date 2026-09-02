package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;

/**
 * Hides the parts of the vanilla HUD that have no place under the RTS overlay — above all the
 * hotbar.
 *
 * <p><strong>Nothing hid the hotbar before this class existed.</strong> It was merely absent as a
 * side effect of {@code ForgottenRealmsRTS.enforceObserverState} forcing spectator, which is why it
 * reappeared the instant anything touched the gamemode. Blocked by accident is not blocked, and the
 * user asked for it blocked.
 *
 * <p><strong>{@link VanillaGuiLayers#SPECTATOR_TOOLTIP} is the one that is easy to miss.</strong>
 * Spectator draws its own hotbar-shaped teleport menu on a separate layer, and since this mod
 * <em>forces</em> spectator, cancelling {@code HOTBAR} alone leaves the player looking at a bar
 * that is still there. Both have to go.
 *
 * <p>This only stops the layers being <em>drawn</em>. The keys that drive them are swallowed
 * separately by {@code RtsMouseController.drainBlockedKeys} — without that, number keys still move
 * the selected slot and {@code Q} still throws items on the floor behind the HUD.
 */
public final class RtsVanillaHudSuppressor {
    /**
     * Compared by identity against the {@link VanillaGuiLayers} constants rather than by string:
     * these are interned {@link Identifier} fields, and matching on their text would silently stop
     * working the day one is renamed instead of failing to compile.
     */
    private static final Set<Identifier> SUPPRESSED = Set.of(
            VanillaGuiLayers.HOTBAR,
            // The spectator teleport menu — a second, hotbar-shaped bar in the same place.
            VanillaGuiLayers.SPECTATOR_TOOLTIP,
            // The held item's name, which pops up whenever the selected slot changes.
            VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.EXPERIENCE_LEVEL,
            // The RTS cursor is the pointer here; a crosshair pinned to the screen centre is not.
            VanillaGuiLayers.CROSSHAIR
    );
    private static boolean tutorialSuppressed;
    private static TutorialSteps tutorialBeforeRts;

    private RtsVanillaHudSuppressor() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsVanillaHudSuppressor::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(RtsVanillaHudSuppressor::onClientTick);
    }

    private static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        // OVERLAY_MESSAGE is intentionally not suppressed: sendOverlayMessage is the readable RTS
        // feedback channel. Vanilla normally places it near the bottom of the screen, underneath
        // our full-width build bar, so lift its anchor by the bar's actual fitted height.
        if (VanillaGuiLayers.OVERLAY_MESSAGE.equals(event.getName())) {
            int lift = RtsBottomBarHud.currentHeight(minecraft) + 6;
            minecraft.gui.leftHeight = Math.max(minecraft.gui.leftHeight, lift);
            minecraft.gui.rightHeight = Math.max(minecraft.gui.rightHeight, lift);
            return;
        }

        if (SUPPRESSED.contains(event.getName())) {
            event.setCanceled(true);
        }
    }

    /** The vanilla movement toast teaches first-person mouse look, which is not the RTS input model. */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        if (RtsMode.isActive(minecraft.player)) {
            if (!tutorialSuppressed) {
                tutorialBeforeRts = minecraft.options.tutorialStep;
                tutorialSuppressed = true;
            }
            if (minecraft.options.tutorialStep != TutorialSteps.NONE) {
                minecraft.getTutorial().setStep(TutorialSteps.NONE);
            }
        } else if (tutorialSuppressed) {
            TutorialSteps restore = tutorialBeforeRts;
            tutorialSuppressed = false;
            tutorialBeforeRts = null;
            if (restore != null && restore != TutorialSteps.NONE) {
                minecraft.getTutorial().setStep(restore);
            }
        }
    }
}
