package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.RtsBattle;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.RtsSpectateClientState;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Persistent, high-contrast restart affordance for the frozen defeated-town view. */
public final class RtsSpectateHud {
    private static final int BUTTON_WIDTH = 226;
    private static final int BUTTON_HEIGHT = 34;
    private static final int LEFT_MARGIN = 12;
    private static final int TOP_GAP = 8;

    private RtsSpectateHud() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsSpectateHud::onRenderGui);
    }

    public static boolean isPointInside(int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!RtsSpectateClientState.active() || minecraft.player == null
                || !RtsMode.isActive(minecraft.player)
                || RtsBattle.outcome(minecraft.player) != RtsBattle.OUTCOME_DEFEAT) {
            return false;
        }
        int x = buttonX(minecraft);
        int y = buttonY(minecraft);
        int width = buttonWidth(minecraft);
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || !isPointInsideState(minecraft)) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int x = buttonX(minecraft);
        int y = buttonY(minecraft);
        int width = buttonWidth(minecraft);
        float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() * 0.008D);
        int glowAlpha = 0x38 + Math.round(pulse * 0x38);
        int border = pulse > 0.5F ? 0xFFFFE28A : 0xFFF1B84B;

        graphics.fill(x - 5, y - 5, x + width + 5, y + BUTTON_HEIGHT + 5,
                (glowAlpha << 24) | 0xE19A27);
        graphics.fill(x - 2, y - 2, x + width + 2, y + BUTTON_HEIGHT + 2,
                0xFFB66E20);
        graphics.fill(x, y, x + width, y + BUTTON_HEIGHT, 0xED24170D);
        graphics.outline(x, y, width, BUTTON_HEIGHT, border);

        String title = "Restart the Civilization";
        graphics.text(font, title, x + (width - font.width(title)) / 2, y + 6, border);
        String subtitle = "GAME PAUSED  •  SPECTATING";
        graphics.text(font, subtitle, x + (width - font.width(subtitle)) / 2,
                y + 20, 0xFFCFC2A6);

        if (RtsMouseController.uiClickPending()
                && isPointInside(RtsMouseController.clickMouseX(), RtsMouseController.clickMouseY())) {
            RtsMouseController.consumeUiClick();
            minecraft.setScreen(new NewTownWarningScreen());
        }
    }

    private static boolean isPointInsideState(Minecraft minecraft) {
        return RtsSpectateClientState.active() && minecraft.player != null
                && RtsMode.isActive(minecraft.player)
                && RtsBattle.outcome(minecraft.player) == RtsBattle.OUTCOME_DEFEAT;
    }

    private static int buttonX(Minecraft minecraft) {
        return LEFT_MARGIN;
    }

    /** Keep the card in the open playfield instead of covering the right-hand minimap column. */
    private static int buttonWidth(Minecraft minecraft) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int playfieldWidth = screenWidth - RtsSidePanelHud.columnWidth(screenWidth);
        return Math.min(BUTTON_WIDTH, Math.max(96, playfieldWidth - LEFT_MARGIN * 2));
    }

    private static int buttonY(Minecraft minecraft) {
        return RtsTopBarHud.currentHeight(minecraft) + TOP_GAP;
    }
}
