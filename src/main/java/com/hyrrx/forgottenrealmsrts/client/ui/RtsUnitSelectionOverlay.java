package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Draws the translucent marquee used for Windows-style unit box selection. */
public final class RtsUnitSelectionOverlay {
    private RtsUnitSelectionOverlay() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsUnitSelectionOverlay::onRenderGui);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null
                || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        RtsMouseController.SelectionBox box = RtsMouseController.currentSelectionBox();
        if (box == null) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int left = box.left();
        int top = box.top();
        int right = box.right() + 1;
        int bottom = box.bottom() + 1;
        graphics.fill(left, top, right, bottom, 0x243E9BFF);
        graphics.fill(left, top, right, top + 1, 0xFF7DB7FF);
        graphics.fill(left, bottom - 1, right, bottom, 0xFF7DB7FF);
        graphics.fill(left, top, left + 1, bottom, 0xFF7DB7FF);
        graphics.fill(right - 1, top, right, bottom, 0xFF7DB7FF);

        String hint = "SELECT UNITS";
        int labelWidth = minecraft.font.width(hint);
        int labelX = left;
        int labelY = Math.max(1, top - minecraft.font.lineHeight - 3);
        graphics.fill(labelX - 2, labelY - 1, labelX + labelWidth + 2,
                labelY + minecraft.font.lineHeight + 1, 0xC0142034);
        graphics.text(minecraft.font, hint, labelX, labelY, 0xFFD6E8FF);
    }
}
