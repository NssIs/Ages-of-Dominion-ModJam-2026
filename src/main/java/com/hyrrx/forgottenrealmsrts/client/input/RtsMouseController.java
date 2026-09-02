package com.hyrrx.forgottenrealmsrts.client.input;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.build.BuildGhost;
import com.hyrrx.forgottenrealmsrts.client.cursor.RtsCursor;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsBottomBarHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsSidePanelHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsSpectateHud;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsTopBarHud;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class RtsMouseController {
    private static final double CURSOR_WRAP_MARGIN = 2.0D;
    private static final long CLICK_PULSE_MILLIS = 110L;

    /**
     * How long the left button must be held, on the world rather than on a HUD panel, before it
     * counts as a camera pan instead of a click. Below this the press is a click and the camera
     * never moves — that is the "or else it's a false alarm" rule.
     */
    private static final long PAN_HOLD_MILLIS = 1000L;
    /** A small cursor movement turns a world press into a Windows-style selection box. */
    private static final double SELECTION_DRAG_DISTANCE = 6.0D;

    private static boolean leftButtonDown;
    private static boolean rightButtonDown;
    private static boolean previousLeftButtonDown;
    private static boolean uiInteraction;
    /** When the current left press began, for the hold threshold. */
    private static long leftPressedAt;
    /**
     * Decided <strong>once, at press time</strong>: could this press become a pan at all?
     *
     * <p>The whole point of storing it is that it is never re-evaluated. The first version of drag
     * panning tested "is the cursor over a HUD panel" every tick and dropped the drag when it was,
     * so a pan across the screen died the instant it crossed the top bar or the command bar, which
     * played as the camera hanging. Where the cursor travels *during* a drag is not information
     * about whether the drag is a drag.
     */
    private static boolean panEligible;
    /** True once the hold threshold has been passed; latched until the button comes back up. */
    private static boolean panning;
    /** True while the current world press is drawing a unit-selection rectangle. */
    private static boolean selecting;
    /** Set on release when the press turned out to be a pan, so it is not also read as a click. */
    private static boolean panConsumedClick;
    /** A completed selection rectangle waiting for the selection controller to consume it. */
    private static boolean selectionReleasePending;
    /** A genuine release edge waiting for the interaction controller to consume it. */
    private static boolean clickReleasePending;
    /** A HUD press edge waiting for the bar renderer to consume it. */
    private static boolean uiClickPending;
    private static double releaseCursorX;
    private static double releaseCursorY;
    private static int clickMouseX;
    private static int clickMouseY;
    private static int selectionStartX;
    private static int selectionStartY;
    private static int selectionEndX;
    private static int selectionEndY;
    private static long lastLeftClickAt = Long.MIN_VALUE;
    /** The last client-side state that actually owns the mouse buttons. */
    private static boolean lastRtsInputActive;

    private RtsMouseController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsMouseController::onMouseButton);
        NeoForge.EVENT_BUS.addListener(RtsMouseController::onClientTick);
        NeoForge.EVENT_BUS.addListener(RtsMouseController::onScreenOpening);
    }

    public static boolean leftButtonDown() {
        return leftButtonDown;
    }

    public static boolean rightButtonDown() {
        return rightButtonDown;
    }

    public static boolean anyButtonDown() {
        return leftButtonDown || rightButtonDown;
    }

    /**
     * True while a press that began over a HUD panel is still held. The whole press is then
     * treated as a UI interaction: the mouse is never grabbed, so the cursor stays exactly where the
     * player clicked instead of being warped to the centre of the window, and the camera ignores it.
     */
    public static boolean isUiInteraction() {
        return uiInteraction;
    }

    public static boolean leftClickStarted() {
        return leftButtonDown && !previousLeftButtonDown;
    }

    /**
     * True while the camera is being panned by a held left button.
     *
     * <p>Latched once the press has been held past {@link #PAN_HOLD_MILLIS} and stays true until the
     * button is released, regardless of where the cursor goes.
     */
    public static boolean isPanning() {
        return panning;
    }

    /** True while a world drag is selecting units rather than panning the camera. */
    public static boolean isSelecting() {
        return selecting;
    }

    /** The live selection rectangle, in GUI-scaled coordinates, while the button is held. */
    public static SelectionBox currentSelectionBox() {
        return selecting ? new SelectionBox(selectionStartX, selectionStartY,
                selectionEndX, selectionEndY) : null;
    }

    /** Consumes the completed rectangle after the left button is released. */
    public static SelectionBox consumeSelectionRelease() {
        if (!selectionReleasePending) {
            return null;
        }
        selectionReleasePending = false;
        return new SelectionBox(selectionStartX, selectionStartY, selectionEndX, selectionEndY);
    }

    /**
     * Consumes a genuine left-click release. The edge is latched by the GLFW release callback rather
     * than inferred from {@code previousLeftButtonDown}; the latter is updated by this controller
     * before the other client-tick listeners run.
     */
    public static boolean consumeLeftClickReleased() {
        boolean released = clickReleasePending;
        clickReleasePending = false;
        return released;
    }

    /**
     * Consumes a left press that started over the RTS HUD. Rendering consumes this edge directly;
     * deriving it from the tick-based button state loses quick clicks between tick and frame.
     */
    public static boolean consumeUiClickPressed() {
        boolean pressed = uiClickPending;
        uiClickPending = false;
        return pressed;
    }

    /** Peeks the pending UI-click edge without consuming it, so a panel can claim only its own. */
    public static boolean uiClickPending() {
        return uiClickPending;
    }

    /** Consumes the pending UI click. Pair with {@link #uiClickPending()} + a region test. */
    public static void consumeUiClick() {
        uiClickPending = false;
    }

    public static int clickMouseX() {
        return clickMouseX;
    }

    public static int clickMouseY() {
        return clickMouseY;
    }

    public static int mouseX(Minecraft minecraft) {
        return RtsCursor.scaledMouseX(minecraft);
    }

    public static int mouseY(Minecraft minecraft) {
        return RtsCursor.scaledMouseY(minecraft);
    }

    /** A short press pulse so a quick click still reads as a deliberate cursor action. */
    public static float clickPulse() {
        long elapsed = Util.getMillis() - lastLeftClickAt;
        if (elapsed < 0L || elapsed >= CLICK_PULSE_MILLIS) {
            return 0.0F;
        }
        float progress = elapsed / (float) CLICK_PULSE_MILLIS;
        return 1.0F - progress;
    }

    /**
     * Fires the instant GLFW reports a press or release — not polled once a tick, not polled once a
     * render frame. A previous fix moved the UI-click classification onto {@code RenderFrameEvent.Pre}
     * to close a tick-rate race, but that only traded one polling rate for another: it also silently
     * broke {@link #leftClickStarted()} for every HUD panel, because {@code previousLeftButtonDown}
     * was then updated many times per tick, so the click-tick's own edge had almost always already
     * been consumed before {@code ClientTickEvent.Post} ever saw it. Reacting to the actual GLFW
     * event removes the race by construction: the UI-vs-grab decision is made synchronously, in the
     * same call as the OS telling us the button went down, before anything else in the mod — polled
     * or not — can act on "a button is now down" first.
     */
    private static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            // Screen input belongs to vanilla. In particular, do not let a HUD press that was
            // active when the screen opened leave the menu's click or release event cancelled.
            clearRtsInputState();
            RtsCursor.showNativeCursor(minecraft);
            return;
        }

        boolean pressed = event.getAction() == GLFW.GLFW_PRESS;
        // The first button to go down decides whether the whole press-and-hold is a UI interaction,
        // which is the same rule updateMouseMode and isUiInteraction already use. Doing this for any
        // button, not just left, means a right-press on the bar does not grab either.
        if (pressed && !anyButtonDown()) {
            onPress();
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (pressed) {
                lastLeftClickAt = Util.getMillis();
                leftPressedAt = Util.getMillis();
                selectionStartX = mouseX(minecraft);
                selectionStartY = mouseY(minecraft);
                selectionEndX = selectionStartX;
                selectionEndY = selectionStartY;
                selecting = false;
                selectionReleasePending = false;
                // Eligible only if the press landed on the world. Fixed here for the whole drag.
                panEligible = !uiInteraction;
                if (uiInteraction) {
                    uiClickPending = true;
                }
            } else {
                // A release is latched until the interaction controller consumes it. This must not
                // depend on previousLeftButtonDown: that field is bookkeeping for press edges and is
                // overwritten at the end of this controller's tick.
                boolean matchedPress = leftButtonDown;
                if (matchedPress && panEligible && !uiInteraction) {
                    selectionEndX = mouseX(minecraft);
                    selectionEndY = mouseY(minecraft);
                    if (!selecting && selectionDistanceSquared() >=
                            SELECTION_DRAG_DISTANCE * SELECTION_DRAG_DISTANCE) {
                        selecting = true;
                    }
                }
                if (matchedPress) {
                    boolean completedSelection = selecting;
                    panConsumedClick = panning || completedSelection;
                    clickReleasePending = !panning && !completedSelection && !uiInteraction
                            && rtsInputActive(minecraft);
                    if (completedSelection) {
                        selectionReleasePending = true;
                    }
                } else {
                    // A screen or mode transition may have already cleared the matching press.
                    // Ignore this orphaned release instead of turning it into a world click.
                    panConsumedClick = false;
                    clickReleasePending = false;
                    selectionReleasePending = false;
                }
                panning = false;
                selecting = false;
                panEligible = false;
            }
            leftButtonDown = pressed;
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rightButtonDown = pressed;
        }

        // Vanilla MouseHandler#onButton does `if (!mouseGrabbed && pressed) grabMouse()` whenever no
        // screen is open, and grabMouse warps the pointer to the centre of the window. Cancelling
        // the event returns out of that method before the grab, which is the only way to stop it.
        //
        // **Every press in RTS mode is cancelled, not just the ones on a HUD panel.** Exempting
        // world presses was the bug the player saw as "clicking teleports my cursor to the middle":
        // a press on the terrain grabbed instantly, and the RTS cursor is free, so it visibly jumped
        // and stayed there until the button came back up. Nothing is lost by cancelling — the grab
        // that drag-to-pan needs is issued by updateMouseMode when the *gesture* starts
        // (`panning || rightButtonDown`), not by the press — and swallowing the click also keeps it
        // out of the attack/use keybinds, which it has no business reaching here.
        boolean rtsWorldPress = rtsInputActive(minecraft);
        if (uiInteraction || rtsWorldPress) {
            event.setCanceled(true);
        }
    }

    private static void onPress() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean rtsActive = rtsInputActive(minecraft);
        int mouseX = mouseX(minecraft);
        int mouseY = mouseY(minecraft);

        uiInteraction = rtsActive
                && minecraft.screen == null
                && (RtsTopBarHud.isPointInside(mouseX, mouseY)
                    || RtsBottomBarHud.isPointInside(mouseX, mouseY)
                    || RtsSidePanelHud.isPointInside(mouseX, mouseY)
                    || RtsSpectateHud.isPointInside(mouseX, mouseY));

        releaseCursorX = minecraft.mouseHandler.xpos();
        releaseCursorY = minecraft.mouseHandler.ypos();
        clickMouseX = mouseX;
        clickMouseY = mouseY;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean rtsActive = rtsInputActive(minecraft);

        if (minecraft.screen != null) {
            // ScreenEvent.Opening runs before Minecraft assigns the screen, so this second guard
            // also catches screens opened by vanilla or another mod without leaving RTS state
            // armed for the menu underneath it.
            clearRtsInputState();
            RtsCursor.showNativeCursor(minecraft);
        }

        // /game deactivate changes the server attachment and the client gamemode in separate
        // packets. If the attachment arrives first, the old RTS state can otherwise survive long
        // enough to swallow the next vanilla right-click (and a held HUD press can leave
        // uiInteraction latched). Treat the transition out of the actual spectator RTS state as a
        // hard input boundary so Creative/Survival receives a clean mouse state immediately.
        if (lastRtsInputActive && !rtsActive) {
            resetForVanillaInput();
        }
        lastRtsInputActive = rtsActive;

        if (minecraft.screen != null || !rtsActive) {
            uiClickPending = false;
        }

        if (!anyButtonDown()) {
            uiInteraction = false;
        }

        // A movement becomes a selection rectangle first. If the player simply holds still, the
        // older long-hold pan gesture remains available after its threshold.
        if (leftButtonDown && panEligible && !panning) {
            selectionEndX = mouseX(minecraft);
            selectionEndY = mouseY(minecraft);
            if (!selecting && selectionDistanceSquared() >=
                    SELECTION_DRAG_DISTANCE * SELECTION_DRAG_DISTANCE) {
                selecting = true;
            }
            if (!selecting && Util.getMillis() - leftPressedAt >= PAN_HOLD_MILLIS) {
                panning = true;
            }
        }

        updateMouseMode(minecraft);
        drainBlockedKeys(minecraft);
        wrapVisibleCursor(minecraft);
        previousLeftButtonDown = leftButtonDown;
        // A pending genuine release is cleared by consumeLeftClickReleased(), not by this bookkeeping
        // tick. That is what lets a later-registered placement/selection listener see the edge.
        if (!leftButtonDown) {
            panConsumedClick = false;
        }
    }

    /**
     * Blocks the inventory and every other container screen — <strong>only while RTS mode is
     * active.</strong>
     *
     * <p>Without that guard this cancelled every {@link AbstractContainerScreen} unconditionally,
     * which did not just stop {@code E} after {@code /game deactivate}: it stopped chests, furnaces
     * and crafting tables too, permanently, for the rest of the session. Every other client system
     * in this mod already gates on {@link RtsMode}; these two guards were missed because they read
     * like input plumbing rather than like RTS behaviour.
     */
    private static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getNewScreen();
        boolean rtsActive = rtsInputActive(minecraft);
        boolean cancelPause = rtsActive
                && screen instanceof PauseScreen
                && BuildGhost.building() != null;
        boolean cancelContainer = rtsActive
                && (screen instanceof InventoryScreen
                    || screen instanceof CreativeModeInventoryScreen
                    || screen instanceof AbstractContainerScreen<?>);

        // Opening is fired before Minecraft assigns the new screen. Clear here as well as in the
        // tick handler so a HUD/world press cannot leak into the first menu click.
        clearRtsInputState();

        // Esc while a building is selected cancels the selection instead of pausing. There is no
        // other way to do this: InputEvent.Key is not cancellable in 26.1, so the key itself cannot
        // be swallowed — but Minecraft#pauseGame does nothing except open this screen (there is no
        // separate paused flag), so refusing the screen is the whole behaviour. The selection is
        // gone by the time the player presses Esc again, and the pause menu opens normally.
        if (cancelPause) {
            BuildGhost.cancel();
            event.setCanceled(true);
            return;
        }

        if (cancelContainer) {
            event.setCanceled(true);
            return;
        }

        if (screen != null) {
            RtsCursor.showNativeCursor(minecraft);
        }
    }

    private static void updateMouseMode(Minecraft minecraft) {
        if (minecraft.screen != null) {
            if (minecraft.mouseHandler.isMouseGrabbed()) {
                releaseMouseAtSavedPosition(minecraft);
            }
            RtsCursor.showNativeCursor(minecraft);
            return;
        }

        // RTS mode off: behave like vanilla survival/creative — mouse stays grabbed for normal
        // first-person look, none of the RTS free-cursor/edge-wrap/panel logic applies.
        if (!rtsInputActive(minecraft)) {
            if (!minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }
            return;
        }

        // Grab only while the mouse is actually driving the camera — a left-hold that has passed the
        // pan threshold, or a right-drag turning the view. Grabbing gives the drag unlimited travel
        // instead of stopping at the screen edge.
        //
        // **Not simply "a button is down".** That grabbed the pointer the instant a press landed on
        // the world, which is a whole second before panning latches: for that second the cursor was
        // captured and warped to the centre while nothing moved, so the pointer appeared to stick
        // and the player could do nothing. The grab has to begin when the pan does.
        if (panning || rightButtonDown) {
            if (!minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }
            return;
        }

        if (minecraft.mouseHandler.isMouseGrabbed()) {
            releaseMouseAtSavedPosition(minecraft);
        }
        RtsCursor.hideNativeCursor(minecraft);
    }

    private static void releaseMouseAtSavedPosition(Minecraft minecraft) {
        minecraft.mouseHandler.releaseMouse();
        GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), releaseCursorX, releaseCursorY);
    }

    private static double selectionDistanceSquared() {
        double dx = selectionEndX - selectionStartX;
        double dy = selectionEndY - selectionStartY;
        return dx * dx + dy * dy;
    }

    /**
     * Swallows every key that would reach the vanilla item bar while the RTS overlay is up: the
     * inventory, the nine hotbar slots, offhand swap, drop, and spectator's own hotbar menu.
     *
     * <p>Hiding the hotbar's GUI layer (see {@code RtsVanillaHudSuppressor}) only stops it being
     * <em>drawn</em> — the keys still change the selected slot and still throw items on the floor
     * behind the HUD. Blocked has to mean both.
     *
     * <p>Gated on {@link RtsMode} like everything else: outside RTS mode these keys are the
     * player's again, which is the whole point of {@code /game deactivate}.
     */
    private static void drainBlockedKeys(Minecraft minecraft) {
        if (!rtsInputActive(minecraft) || minecraft.screen != null) {
            return;
        }

        drain(minecraft.options.keyInventory);
        for (KeyMapping hotbarSlot : minecraft.options.keyHotbarSlots) {
            drain(hotbarSlot);
        }
        drain(minecraft.options.keySwapOffhand);
        drain(minecraft.options.keyDrop);
        // Spectator draws its own hotbar-shaped teleport menu; this is the key that opens it.
        drain(minecraft.options.keySpectatorHotbar);
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // Consume every queued press so nothing downstream ever sees it.
        }
    }

    private static void wrapVisibleCursor(Minecraft minecraft) {
        if (minecraft.screen != null || anyButtonDown() || minecraft.mouseHandler.isMouseGrabbed()
                || !rtsInputActive(minecraft)) {
            return;
        }

        int width = minecraft.getWindow().getScreenWidth();
        int height = minecraft.getWindow().getScreenHeight();
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        double wrappedX = mouseX;
        double wrappedY = mouseY;

        if (mouseX <= 0.0D) {
            wrappedX = width - CURSOR_WRAP_MARGIN;
        } else if (mouseX >= width - 1.0D) {
            wrappedX = CURSOR_WRAP_MARGIN;
        }

        if (mouseY <= 0.0D) {
            wrappedY = height - CURSOR_WRAP_MARGIN;
        } else if (mouseY >= height - 1.0D) {
            wrappedY = CURSOR_WRAP_MARGIN;
        }

        if (wrappedX != mouseX || wrappedY != mouseY) {
            GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), wrappedX, wrappedY);
        }
    }

    /**
     * RTS owns the mouse only while its forced spectator camera is actually in effect. The
     * attachment is synced independently from the gamemode; checking both makes deactivation
     * fail-safe if those packets cross, and avoids stealing vanilla placement during that window.
     */
    private static boolean rtsInputActive(Minecraft minecraft) {
        return minecraft.player != null
                && RtsMode.isActive(minecraft.player)
                && minecraft.player.isSpectator();
    }

    /** Drop all edges that belong to the RTS cursor before a screen or vanilla input takes over. */
    private static void resetForVanillaInput() {
        clearRtsInputState();
        BuildGhost.cancel();
    }

    /** Clear all transient mouse state without cancelling an otherwise valid build ghost. */
    private static void clearRtsInputState() {
        leftButtonDown = false;
        rightButtonDown = false;
        previousLeftButtonDown = false;
        uiInteraction = false;
        leftPressedAt = 0L;
        panEligible = false;
        panning = false;
        selecting = false;
        panConsumedClick = false;
        selectionReleasePending = false;
        clickReleasePending = false;
        uiClickPending = false;
        lastLeftClickAt = Long.MIN_VALUE;
        clickMouseX = 0;
        clickMouseY = 0;
        selectionStartX = 0;
        selectionStartY = 0;
        selectionEndX = 0;
        selectionEndY = 0;
    }

    /** A GUI-scaled, inclusive drag rectangle. */
    public record SelectionBox(int startX, int startY, int endX, int endY) {
        public int left() {
            return Math.min(startX, endX);
        }

        public int top() {
            return Math.min(startY, endY);
        }

        public int right() {
            return Math.max(startX, endX);
        }

        public int bottom() {
            return Math.max(startY, endY);
        }
    }
}
