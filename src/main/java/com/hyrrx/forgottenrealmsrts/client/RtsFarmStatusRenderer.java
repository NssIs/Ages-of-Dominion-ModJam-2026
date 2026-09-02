package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsHudState;
import com.hyrrx.forgottenrealmsrts.network.FarmStatusPayload;
import com.hyrrx.forgottenrealmsrts.network.PlacedBuildingInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Draws the selected farm's live worker count and food output above its world position. */
public final class RtsFarmStatusRenderer {
    private static final double MAX_DISPLAY_DISTANCE = 192.0D;
    private static final int HORIZONTAL_PADDING = 5;
    private static final int VERTICAL_PADDING = 3;
    private static final int LINE_GAP = 1;
    private static final int PANEL = 0xE916120D;
    private static final int EDGE = 0xFFE2B15D;
    private static final int TITLE = 0xFFFFE4A3;
    private static final int DETAIL = 0xFFE5D8BB;
    private static final int YIELD = 0xFFB9E7B0;

    private RtsFarmStatusRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsFarmStatusRenderer::onRenderGui);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null
                || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        FarmStatusPayload status = RtsHudState.selectedFarmStatus();
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        if (status == null || selected == null || selected.id() != status.buildingId()) {
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 world = Vec3.atCenterOf(status.displayPos());
        if (world.distanceToSqr(camera.position()) > MAX_DISPLAY_DISTANCE * MAX_DISPLAY_DISTANCE) {
            return;
        }
        RtsUnitScreenProjection.ScreenPoint point = RtsUnitScreenProjection.project(minecraft, world);
        if (point == null) {
            return;
        }

        Font font = minecraft.font;
        String title = "Farm";
        String occupants = "Workers " + status.workersInside() + " / " + status.capacity();
        String yield = "Output " + status.output() + " Food / " + status.intervalSeconds() + "s";
        int width = Math.max(font.width(title), Math.max(font.width(occupants), font.width(yield)))
                + HORIZONTAL_PADDING * 2;
        int height = font.lineHeight * 3 + LINE_GAP * 2 + VERTICAL_PADDING * 2;
        int left = point.x() - width / 2;
        int top = point.y() - height - 4;
        if (top < 2) {
            top = point.y() + 4;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        graphics.fill(left + 2, top + 2, left + width + 2, top + height + 2, 0x80000000);
        graphics.fill(left, top, left + width, top + height, PANEL);
        graphics.outline(left, top, width, height, EDGE);
        graphics.text(font, title, left + (width - font.width(title)) / 2,
                top + VERTICAL_PADDING, TITLE);
        graphics.text(font, occupants, left + HORIZONTAL_PADDING,
                top + VERTICAL_PADDING + font.lineHeight + LINE_GAP, DETAIL);
        graphics.text(font, yield, left + HORIZONTAL_PADDING,
                top + VERTICAL_PADDING + (font.lineHeight + LINE_GAP) * 2, YIELD);
    }
}
