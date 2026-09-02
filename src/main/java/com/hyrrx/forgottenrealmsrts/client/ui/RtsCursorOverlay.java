package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.cursor.RtsCursor;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class RtsCursorOverlay {
    private RtsCursorOverlay() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsCursorOverlay::onRenderGui);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }
        // Hide the sprite only when the mouse is actually grabbed — that is a world drag, where the
        // real pointer is locked to the window centre and drawing a cursor at its position would be
        // a lie. Keying this off anyButtonDown() instead made the cursor vanish for the whole of any
        // click, including a click on the HUD, where the pointer never moves at all.
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            return;
        }
        float pressAmount = Math.max(
                RtsMouseController.leftButtonDown() ? 1.0F : 0.0F,
                RtsMouseController.clickPulse());
        RtsCursor.render(
                event.getGuiGraphics(),
                RtsMouseController.mouseX(minecraft),
                RtsMouseController.mouseY(minecraft),
                pressAmount
        );
    }
}
