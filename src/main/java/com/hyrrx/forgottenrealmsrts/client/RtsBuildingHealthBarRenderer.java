package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsHudState;
import com.hyrrx.forgottenrealmsrts.network.BuildingHealthPayload;
import com.hyrrx.forgottenrealmsrts.network.FarmStatusPayload;
import com.hyrrx.forgottenrealmsrts.network.MineStatusPayload;
import com.hyrrx.forgottenrealmsrts.network.PlacedBuildingInfo;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Draws a persistent numeric HP readout and bar above every owned tracked building. */
public final class RtsBuildingHealthBarRenderer {
    private static final double MAX_DISPLAY_DISTANCE = 192.0D;
    private static final int BAR_WIDTH = 42;
    private static final int BAR_HEIGHT = 5;
    private static final int LABEL_GAP = 2;
    private static final Map<Long, BuildingHealthPayload.BuildingHealth> BUILDINGS = new HashMap<>();

    private RtsBuildingHealthBarRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsBuildingHealthBarRenderer::onRenderGui);
    }

    public static void accept(BuildingHealthPayload payload) {
        BUILDINGS.clear();
        if (payload != null) {
            for (BuildingHealthPayload.BuildingHealth building : payload.buildings()) {
                if (building != null && building.maxHealth() > 0) {
                    BUILDINGS.put(building.id(), building);
                }
            }
        }
    }

    public static void clear() {
        BUILDINGS.clear();
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null
                || !RtsMode.isActive(minecraft.player) || BUILDINGS.isEmpty()) {
            return;
        }

        Font font = minecraft.font;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        for (BuildingHealthPayload.BuildingHealth building : BUILDINGS.values()) {
            Vec3 world = new Vec3(
                    building.origin().getX() + building.sizeX() * 0.5D,
                    building.origin().getY() + Math.max(1, building.sizeY()) + 0.75D,
                    building.origin().getZ() + building.sizeZ() * 0.5D);
            if (world.distanceToSqr(camera) > MAX_DISPLAY_DISTANCE * MAX_DISPLAY_DISTANCE) {
                continue;
            }
            RtsUnitScreenProjection.ScreenPoint point = RtsUnitScreenProjection.project(minecraft, world);
            if (point == null) {
                continue;
            }

            int barTop = barTop(font, point.y(), building.id());
            int left = point.x() - BAR_WIDTH / 2;
            int right = left + BAR_WIDTH;
            int bottom = barTop + BAR_HEIGHT;
            boolean damaged = building.health() < building.maxHealth();
            int border = 0xFFE2B15D;
            int track = 0xFF34251B;
            int fill = damaged ? 0xFFD68A42 : 0xFF72C477;
            graphics.fill(left - 1, barTop - 1, right + 1, bottom + 1, border);
            graphics.fill(left, barTop, right, bottom, track);
            int filled = Math.round((BAR_WIDTH - 2) * fraction(building));
            if (filled > 0) {
                graphics.fill(left + 1, barTop + 1, left + 1 + filled, bottom - 1, fill);
                graphics.fill(left + 1, barTop + 1, left + 1 + filled, barTop + 2,
                        damaged ? 0xFFFFC06B : 0xFFB8F2B3);
            }

            String label = Math.max(0, building.health()) + " / " + Math.max(1, building.maxHealth());
            int labelWidth = font.width(label);
            int labelX = point.x() - labelWidth / 2;
            int labelY = barTop - font.lineHeight - LABEL_GAP;
            if (labelY < 1) {
                labelY = bottom + LABEL_GAP;
            }
            graphics.fill(labelX - 2, labelY - 1, labelX + labelWidth + 2,
                    labelY + font.lineHeight + 1, 0xD9141820);
            graphics.text(font, label, labelX, labelY,
                    damaged ? 0xFFFFD59E : 0xFFE8FFD9);
        }
    }

    /** Places the bar immediately above a selected mine/farm panel, leaving that panel readable. */
    private static int barTop(Font font, int pointY, long buildingId) {
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        if (selected == null || selected.id() != buildingId) {
            return pointY;
        }
        MineStatusPayload mine = RtsHudState.selectedMineStatus();
        FarmStatusPayload farm = RtsHudState.selectedFarmStatus();
        if (mine == null && farm == null) {
            return pointY;
        }
        int panelHeight = font.lineHeight * 3 + 2 + 6;
        int panelTop = pointY - panelHeight - 4;
        return panelTop >= 2 ? panelTop - BAR_HEIGHT - font.lineHeight - LABEL_GAP * 2 : pointY;
    }

    private static float fraction(BuildingHealthPayload.BuildingHealth building) {
        return Math.max(0.0F, Math.min(1.0F,
                building.health() / (float) Math.max(1, building.maxHealth())));
    }
}
