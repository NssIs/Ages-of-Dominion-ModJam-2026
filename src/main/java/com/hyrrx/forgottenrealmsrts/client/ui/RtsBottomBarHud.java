package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.RtsEconomy;
import com.hyrrx.forgottenrealmsrts.RtsFarmOrders;
import com.hyrrx.forgottenrealmsrts.RtsMineOrders;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.RtsProduction;
import com.hyrrx.forgottenrealmsrts.RtsCivilization;
import com.hyrrx.forgottenrealmsrts.RtsVillagerEntity;
import com.hyrrx.forgottenrealmsrts.RtsWorkerOrders;
import com.hyrrx.forgottenrealmsrts.Resource;
import com.hyrrx.forgottenrealmsrts.client.build.BuildGhost;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingSelectionController;
import com.hyrrx.forgottenrealmsrts.client.input.RtsKeyBindings;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;
import com.hyrrx.forgottenrealmsrts.network.ArmyCommandPayload;
import com.hyrrx.forgottenrealmsrts.network.BuildingInfo;
import com.hyrrx.forgottenrealmsrts.network.ConstructionInfo;
import com.hyrrx.forgottenrealmsrts.network.FarmStatusPayload;
import com.hyrrx.forgottenrealmsrts.network.MineStatusPayload;
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import com.hyrrx.forgottenrealmsrts.network.PlacedBuildingInfo;
import com.hyrrx.forgottenrealmsrts.network.TrainVillagerPayload;
import com.hyrrx.forgottenrealmsrts.network.TrainGuardianPayload;
import com.hyrrx.forgottenrealmsrts.network.RecallWorkersPayload;
import com.hyrrx.forgottenrealmsrts.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-width build bar anchored to the bottom of the screen: the selected-unit/building panel on
 * the left, then the structures tray with its category tabs.
 *
 * <p>The left panel is the mod's only readout for a selected building — see
 * {@link #drawUnitPanel} for what it shows and why the floating one that used to duplicate it is
 * gone. The old nine-card command grid is intentionally not rendered: the world cursor, keyboard
 * bindings, and contextual building panel remain the focused RTS controls, while this bar is now
 * only a readable build tray.
 *
 * <p>Every size below is derived from its sprite's own aspect ratio rather than hand-picked, and
 * every draw goes through {@link HudTextures#blitWhole} — see that class for why the obvious blit
 * overload silently crops instead of scaling. Fitting works the same way as the top bar: measure the
 * natural width, shrink uniformly via the pose stack, and take the band height from
 * {@link HudScale#bandHeight} so the background never outgrows its contents.
 */
public final class RtsBottomBarHud {
    private static final int BAR_HEIGHT = 80;
    private static final int MARGIN = 5;
    private static final int SECTION_GAP = 12;
    private static final int ITEM_GAP = 3;

    /** Native sprite sizes, needed because blitWhole scales the whole source into the destination. */
    private static final int UNIT_RENDER_SRC_W = 147;
    private static final int UNIT_RENDER_SRC_H = 248;

    private static final int CONTENT_HEIGHT = BAR_HEIGHT - 2 * MARGIN;
    /** Lifts the unit/building panel one virtual pixel off the band's centre line. */
    private static final int LEFT_SECTION_Y_NUDGE = -1;
    private static final int UNIT_PANEL_HEIGHT = CONTENT_HEIGHT;
    /** Wide enough for the readout to be *text*. The old panel was one 326x332 picture with the
     *  stats painted in, squeezed to 68px — a five-fold downscale, which is why it was unreadable at
     *  any bar height. Drawing a container plus live text instead means the figures render at font
     *  size no matter how the bar scales. Widened from 118 when this panel absorbed the deleted
     *  floating build HUD: at 118 the text column was under 70px, and "Not enough resources" wrapped
     *  to three rows in a panel that only has room for six. */
    private static final int UNIT_PANEL_WIDTH = 190;
    /** Tight, because every pixel here is a row: the panel shows name, category, prices and the
     *  placement hint, and at 4 only two rows survived. */
    private static final int UNIT_PADDING = 2;
    private static final int UNIT_TEXT_GAP = 5;
    /** Room for the live skin-atlas portrait in the selected-unit card. */
    private static final int UNIT_PROFILE_WIDTH = 42;
    /** Same as the font's line height, so a cost row with an icon is no taller than one without. */
    private static final int ICON_SIZE = 9;
    private static final int ICON_TEXT_GAP = 4;
    /** Native size of the top bar resource icons this panel borrows for its cost rows. */
    private static final int RESOURCE_ICON_SRC = 100;

    private static final int COLOR_UNIT_NAME = 0xFFF4E9C8;
    private static final int COLOR_UNIT_LABEL = 0xFFB8AC85;
    private static final int COLOR_TARGET = 0xFFE15E55;
    private static final int COMMAND_ROW_HEIGHT = 13;
    private static final int COMMAND_GAP = 2;
    private static final int COLOR_COMMAND_BORDER = 0xFFB98B45;
    private static final int COLOR_COMMAND_FILL = 0xD0251B12;
    private static final int COLOR_COMMAND_DISABLED = 0xFF6F6654;
    private static final int COLOR_ACTION_CONFIRM = 0xFF8A3A2E;
    private static final int COLOR_POPUP_BORDER = 0xFFB98B45;
    private static final int COLOR_POPUP_FILL = 0xF01D150D;
    private static final int COLOR_POPUP_TEXT = 0xFFF4E9C8;
    private static final int COLOR_POPUP_OK = 0xFFD8E7C6;
    private static final int COLOR_POPUP_SHORT = 0xFFE07A68;
    private static final int UPGRADE_POPUP_MAX_WIDTH = 220;
    private static final int UPGRADE_POPUP_SCREEN_MARGIN = 4;
    private static final int UPGRADE_POPUP_GAP = 4;
    private static final int UPGRADE_POPUP_PADDING = 7;
    private static final int UPGRADE_POPUP_LINE_GAP = 3;
    private static final int UPGRADE_POPUP_COLUMN_GAP = 8;

    /** Ceiling on the bar's share of screen height. The fit scale is uniform, so letting the
     *  layout grow into a wide screen also makes it taller; this is what stops that runaway. */
    private static final float MAX_HEIGHT_FRACTION = 0.22F;

    private static final int TINT_NONE = 0xFFFFFFFF;
    private static final int TINT_HOVER = 0xFFFFE8B4;

    /**
     * Category tabs at the right end: two rows of narrow tabs butted together with no gaps, with
     * their labels turned a quarter turn so they read down the tab. The number of columns grows with
     * the catalog, so a newly authored category is not silently hidden after the fourth tab.
     */
    private static final int CATEGORY_COLUMNS = 2;
    private static final int CATEGORY_ROWS = 2;
    private static final int CATEGORY_WIDTH = 14;
    private static final int CATEGORY_LABEL_PADDING = 2;
    private static final int CATEGORY_HEIGHT = CONTENT_HEIGHT / CATEGORY_ROWS;

    /** Overflow marker for the structures tray, shown when the slots outnumber the visible cells. */
    private static final Identifier CHEVRON_RIGHT = texture("chevron_right");
    private static final int CHEVRON_SRC_W = 51;
    private static final int CHEVRON_SRC_H = 49;

    /** Native size of the empty container frame. */
    private static final int SLOT_SRC_W = 84;
    private static final int SLOT_SRC_H = 98;
    private static final Identifier SLOT_EMPTY = texture("slot_empty");
    /** Painted long ago and unused until now: the frame for the highlighted building and the open
     *  category tab. */
    private static final Identifier SLOT_SELECTED = texture("slot_selected");

    /** A click that changed nothing (re-selecting the open tab), per blueprint 020's convention. */
    private static final float PITCH_DENIED = 0.6F;

    /** Wash over a building that cannot be built yet — visible, but clearly not available. */
    private static final int COLOR_LOCKED_WASH = 0xA012100C;
    private static final int COLOR_COST_OK = 0xFFD8CFA8;
    private static final int COLOR_COST_SHORT = 0xFFD86A5A;

    /** The tray slot under the cursor this frame, for the name/price tooltip. Reset every frame. */
    private static BuildingInfo hoveredBuilding;

    /** Two rows of containers rather than one row of cards, so the tray holds more in less height. */
    private static final int SLOT_ROWS = 2;
    private static final int SLOT_HEIGHT = (CONTENT_HEIGHT - ITEM_GAP) / SLOT_ROWS;
    private static final int SLOT_WIDTH =
            HudTextures.widthForHeight(SLOT_HEIGHT, SLOT_SRC_W, SLOT_SRC_H);
    /** Columns guaranteed room; the fit scale is measured against this, and a wider screen gets more. */
    private static final int MIN_SLOT_COLUMNS = 6;

    private RtsBottomBarHud() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsBottomBarHud::onRenderGui);
    }

    /** Same contract as {@link RtsTopBarHud#isPointInside} — a click here must not pan the camera. */
    public static boolean isPointInside(int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return false;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int top = height - HudScale.bandHeight(BAR_HEIGHT, contentScale(width, height));
        if (mouseX >= 0 && mouseX < width && mouseY >= top && mouseY < height) {
            return true;
        }
        PlacedBuildingInfo selected = RtsHudState.selectedPlacedBuilding();
        if (selected == null || !RtsHudState.isUpgradePopupOpen(selected.id())) {
            return false;
        }
        UpgradePopupBounds popup = upgradePopupBounds(minecraft, top, selected);
        return contains(mouseX, mouseY, popup.x(), popup.y(), popup.width(), popup.height());
    }

    /** Current rendered height, exposed so vanilla overlay messages can sit just above this bar. */
    public static int currentHeight(Minecraft minecraft) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        return HudScale.bandHeight(BAR_HEIGHT, contentScale(width, height));
    }

    /**
     * Fit scale for the current screen. Unlike the top bar this is allowed to grow past 1.0, because
     * the bottom bar is meant to span the screen: at GUI Scale 1 or 2 the screen is much wider than
     * the natural layout, and pinning at 1.0 left a wide dead strip between the buildings and the
     * minimap. Growth is capped by {@link #MAX_HEIGHT_FRACTION} rather than by a magic number, so
     * the bar takes the same share of the screen at every GUI Scale.
     */
    private static float contentScale(int width, int height) {
        float heightCap = Math.max(1.0F, height * MAX_HEIGHT_FRACTION / BAR_HEIGHT);
        return HudScale.fit(naturalContentWidth(), width, heightCap);
    }

    private static int categoryColumns() {
        int categoryCount = RtsHudState.buildCategories().size();
        return Math.max(CATEGORY_COLUMNS,
                (categoryCount + CATEGORY_ROWS - 1) / CATEGORY_ROWS);
    }

    private static int categoriesSectionWidth() {
        return categoryColumns() * CATEGORY_WIDTH;
    }

    /** Width the overflow arrow reserves at the end of the tray. */
    private static int chevronWidth() {
        return HudTextures.widthForHeight(SLOT_HEIGHT, CHEVRON_SRC_W, CHEVRON_SRC_H);
    }

    private static int slotsSectionWidth() {
        return MIN_SLOT_COLUMNS * (SLOT_WIDTH + ITEM_GAP) - ITEM_GAP;
    }

    private static int naturalContentWidth() {
        return 2 * MARGIN
                + UNIT_PANEL_WIDTH + SECTION_GAP
                + slotsSectionWidth() + SECTION_GAP
                + categoriesSectionWidth();
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();

        float scale = contentScale(width, height);
        int bandHeight = HudScale.bandHeight(BAR_HEIGHT, scale);
        int top = height - bandHeight;

        RtsPanel.draw(graphics, 0, top, width, bandHeight);

        // Everything below is authored in unscaled "virtual" coordinates and squeezed by the pose
        // stack, so the mouse has to be converted into the same space to hit-test the buttons.
        int virtualMouseX = Math.round(RtsMouseController.mouseX(minecraft) / scale);
        int virtualMouseY = Math.round((RtsMouseController.mouseY(minecraft) - top) / scale);

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, top);
        graphics.pose().scale(scale, scale);

        // Left-aligned: the selected-unit/building panel is followed immediately by the structures
        // tray, which absorbs the remaining width instead of leaving a dead command-card block.
        int virtualWidth = Math.round(width / scale);

        // One press edge is consumed per tick by the whole bar. Reading it once here (rather than
        // per widget) is what stops a single click registering on two overlapping hit boxes.
        //
        // Hit-testing uses the position recorded *at the press*, not the live cursor: the two are
        // usually the same pixel, but the mouse controller may have released and repositioned the
        // OS cursor between the press and this frame, and then the live position is not where the
        // player clicked.
        // Only claim clicks that landed on this bar, so the side panel keeps its own (the minimap).
        boolean clicked = RtsMouseController.uiClickPending()
                && isPointInside(RtsMouseController.clickMouseX(), RtsMouseController.clickMouseY());
        if (clicked) {
            RtsMouseController.consumeUiClick();
        }
        int clickX = clicked ? Math.round(RtsMouseController.clickMouseX() / scale) : Integer.MIN_VALUE;
        int clickY = clicked ? Math.round((RtsMouseController.clickMouseY() - top) / scale) : Integer.MIN_VALUE;

        int x = MARGIN;
        drawUnitPanel(graphics, minecraft.font, x, MARGIN + LEFT_SECTION_Y_NUDGE,
                virtualMouseX, virtualMouseY, clickX, clickY);
        x += UNIT_PANEL_WIDTH + SECTION_GAP;

        int categoriesX = virtualWidth - MARGIN - categoriesSectionWidth();
        hoveredBuilding = null;
        drawSlots(graphics, x, categoriesX - SECTION_GAP, virtualMouseX, virtualMouseY, clickX, clickY);
        drawCategories(graphics, minecraft.font, categoriesX, virtualMouseX, virtualMouseY, clickX, clickY);

        graphics.pose().popMatrix();

        drawUpgradePricePopup(graphics, minecraft, top);

        // Emitted after popMatrix, like the top bar's tooltips: inside the bar's scale transform a
        // tooltip renders shrunken and mispositioned.
        if (hoveredBuilding != null) {
            graphics.setComponentTooltipForNextFrame(minecraft.font, tooltipFor(hoveredBuilding),
                    RtsMouseController.mouseX(minecraft), RtsMouseController.mouseY(minecraft));
        }
    }

    /** Name, price, and why it is not available if it is not. */
    private static List<Component> tooltipFor(BuildingInfo building) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(RtsHudState.displayName(building))
                .withStyle(ChatFormatting.WHITE));
        boolean linear = BuildGhost.isLinearPlacement() && sameBuilding(building);
        int spanPieces = linear ? BuildGhost.linearPieceCount() : 1;
        int multiplier = linear ? BuildGhost.linearChargeablePieceCount() : 1;
        if (spanPieces > 1) {
            lines.add(Component.literal("Span: " + BuildGhost.linearColumns() + " × "
                    + BuildGhost.linearRows() + " (" + spanPieces + " pieces; "
                    + multiplier + " new)")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (linear && BuildGhost.linearExistingPieceCount() > 0) {
            lines.add(Component.literal("Already laid: " + BuildGhost.linearExistingPieceCount()
                    + " (free)").withStyle(ChatFormatting.GRAY));
        }

        if (RtsHudState.buildingFree(building, multiplier)) {
            lines.add(Component.literal("Free").withStyle(ChatFormatting.GRAY));
        } else {
            for (Resource resource : Resource.VALUES) {
                int cost = RtsHudState.buildingCost(building, resource) * multiplier;
                if (cost <= 0) {
                    continue;
                }
                boolean enough = RtsHudState.stockOf(resource) >= cost;
                lines.add(Component.literal(cost + "  " + resource.label())
                        .withColor(enough ? COLOR_COST_OK : COLOR_COST_SHORT));
            }
        }

        if (building.minAge() > 0) {
            lines.add(Component.literal(RtsHudState.ageRequirement(building))
                    .withColor(RtsHudState.ageLocked(building) ? COLOR_COST_SHORT : COLOR_COST_OK));
        }

        if (building.townHall() && RtsHudState.townHallPlaced()) {
            lines.add(Component.literal("Only one Town Hall allowed").withStyle(ChatFormatting.RED));
        } else if (!building.townHall() && !RtsHudState.townHallPlaced()) {
            lines.add(Component.literal("Build the Town Hall first").withStyle(ChatFormatting.RED));
        } else if (!building.townHall()
                && !com.hyrrx.forgottenrealmsrts.RtsCivilization.isFounded(
                        Minecraft.getInstance().player)) {
            lines.add(Component.literal("Finish founding your civilization").withStyle(ChatFormatting.RED));
        } else if (!building.townHall() && !RtsHudState.coalMinePlaced()
                && !RtsHudState.isFirstCoalMine(building)) {
            lines.add(Component.literal("Place the free Coal Mine first").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    /**
     * The selected building: its 3D render, what it is, what it costs, and whether it can go where
     * the cursor is pointing.
     *
     * <p>This panel used to print a hardcoded villager — "Villager / Worker / Armor 0 / Gathering
     * Wood". It is real now: clicking a unit in the world selects it (see
     * {@code BuildingSelectionController}) and this panel shows that unit's name, health and role.
     *
     * <p>It is also the readout for a selected building. A second floating panel
     * (`RtsBuildHud`) used to draw the same name and the same prices in the upper left; two places
     * showing one fact is one place too many, so this one absorbed its rows and it was deleted.
     *
     * <p><strong>Every line is wrapped and rows stop when they run out of panel.</strong> Refusal
     * text changes length, and text that can change length cannot be laid out by counting rows.
     * Rows are emitted most-important-first so the ones that fall off the bottom are the hints.
     */
    private static void drawUnitPanel(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                      int mouseX, int mouseY, int clickX, int clickY) {
        RtsPanel.draw(graphics, x, y, UNIT_PANEL_WIDTH, UNIT_PANEL_HEIGHT, RtsPanel.EDGE_ALL);

        int rail = RtsPanel.capSideWidth(RtsPanel.DEFAULT_TEXTURE_SCALE);
        int innerX = x + rail + UNIT_PADDING;
        int innerY = y + RtsPanel.capTopHeight(RtsPanel.DEFAULT_TEXTURE_SCALE) + UNIT_PADDING;
        int innerBottom = y + UNIT_PANEL_HEIGHT
                - RtsPanel.capBottomHeight(RtsPanel.DEFAULT_TEXTURE_SCALE) - UNIT_PADDING;
        int innerHeight = innerBottom - innerY;
        int innerRight = x + UNIT_PANEL_WIDTH - rail - UNIT_PADDING;

        net.minecraft.world.entity.LivingEntity unit = RtsHudState.selectedUnit();
        if (unit != null) {
            List<net.minecraft.world.entity.LivingEntity> selectedUnits = RtsHudState.selectedUnits();
            boolean allWorkers = !selectedUnits.isEmpty() && selectedUnits.stream()
                    .allMatch(candidate -> candidate instanceof RtsVillagerEntity);
            boolean showCommands = !allWorkers;
            int commandY = innerBottom - COMMAND_ROW_HEIGHT;
            int contentBottom = showCommands ? commandY - COMMAND_GAP : innerBottom;
            int contentHeight = Math.max(1, contentBottom - innerY);
            RtsUnitProfileRenderer.draw(graphics, unit, innerX, innerY,
                    UNIT_PROFILE_WIDTH, contentHeight);
            int textX = innerX + UNIT_PROFILE_WIDTH + UNIT_TEXT_GAP;
            drawRows(graphics, font, selectedUnitRows(font, innerRight - textX),
                    textX, innerY, contentBottom);
            if (showCommands) {
                drawSelectedCommands(graphics, font, innerX, commandY, innerRight,
                        mouseX, mouseY, clickX, clickY);
            }
            return;
        }

        net.minecraft.world.entity.LivingEntity target = RtsHudState.selectedTarget();
        if (target != null) {
            drawRows(graphics, font, selectedTargetRows(font, target, innerRight - innerX),
                    innerX, innerY, innerBottom);
            return;
        }

        ConstructionInfo construction = RtsHudState.selectedConstruction();
        if (construction != null) {
            int contentBottom = innerBottom;
            int contentHeight = Math.max(1, contentBottom - innerY);
            int renderWidth = HudTextures.widthForHeight(contentHeight, UNIT_RENDER_SRC_W,
                    UNIT_RENDER_SRC_H);
            RtsBuildingPreview.draw(graphics, construction.structure(), innerX, innerY,
                    renderWidth, contentHeight);
            int textX = innerX + renderWidth + UNIT_TEXT_GAP;
            drawRows(graphics, font, selectedConstructionRows(font, construction, innerRight - textX),
                    textX, innerY, contentBottom);
            return;
        }

        PlacedBuildingInfo placed = RtsHudState.selectedPlacedBuilding();
        if (placed != null) {
            boolean townHall = isTownHallStructure(placed);
            boolean mine = RtsMineOrders.isMineStructure(placed.structure());
            boolean farm = RtsFarmOrders.isFarmStructure(placed.structure());
            boolean barracks = isBarracksStructure(placed);
            boolean hasContextAction = townHall || mine || farm || barracks;
            int contextY = innerBottom - COMMAND_ROW_HEIGHT;
            int actionY = hasContextAction
                    ? contextY - COMMAND_GAP - COMMAND_ROW_HEIGHT : contextY;
            int contentBottom = actionY - COMMAND_GAP;
            int contentHeight = Math.max(1, contentBottom - innerY);
            Identifier preview = BuildGhost.hasSession() && BuildGhost.building() != null
                    ? BuildGhost.building().id() : placed.structure();
            int renderWidth = HudTextures.widthForHeight(contentHeight, UNIT_RENDER_SRC_W, UNIT_RENDER_SRC_H);
            RtsBuildingPreview.draw(graphics, preview, innerX, innerY, renderWidth, contentHeight);
            int textX = innerX + renderWidth + UNIT_TEXT_GAP;
            drawRows(graphics, font, selectedPlacedRows(font, placed, innerRight - textX), textX,
                    innerY, contentBottom);
            drawBuildingActionButtons(graphics, font, placed, innerX, actionY, innerRight,
                    mouseX, mouseY, clickX, clickY);
            if (townHall) {
                drawTrainWorkerButton(graphics, font, placed.id(), innerX, contextY, innerRight,
                        mouseX, mouseY, clickX, clickY);
            } else if (mine || farm) {
                drawRecallWorkersButton(graphics, font, placed.id(), innerX, contextY, innerRight,
                        mouseX, mouseY, clickX, clickY);
            } else if (barracks) {
                drawTrainFighterButton(graphics, font, innerX, contextY, innerRight,
                        mouseX, mouseY, clickX, clickY);
            }
            return;
        }

        BuildingInfo selected = RtsHudState.selectedBuilding();
        if (selected == null) {
            drawRows(graphics, font, emptyRows(font, innerRight - innerX), innerX, innerY,
                    innerBottom);
            return;
        }

        // The building, rendered large enough to actually see, in the gap the portrait left behind.
        int renderWidth = HudTextures.widthForHeight(innerHeight, UNIT_RENDER_SRC_W, UNIT_RENDER_SRC_H);
        RtsBuildingPreview.draw(graphics, selected.id(), innerX, innerY, renderWidth, innerHeight);

        int textX = innerX + renderWidth + UNIT_TEXT_GAP;
        drawRows(graphics, font, selectedRows(font, selected, innerRight - textX), textX, innerY,
                innerBottom);
    }

    /** One laid-out line: its text, its colour, and an optional icon to the left of it. */
    private record Row(FormattedCharSequence text, int color, Identifier icon) {
    }

    private static List<Row> emptyRows(Font font, int available) {
        List<Row> rows = new ArrayList<>();
        addWrapped(font, rows, "Nothing selected", COLOR_UNIT_NAME, null, available);
        addWrapped(font, rows, "Click a building in the tray.", COLOR_UNIT_LABEL, null, available);
        return rows;
    }

    private static List<Row> selectedRows(Font font, BuildingInfo building, int available) {
        List<Row> rows = new ArrayList<>();
        addWrapped(font, rows, RtsHudState.displayName(building), COLOR_UNIT_NAME, null, available);
        boolean linear = BuildGhost.isLinearPlacement() && sameBuilding(building);
        int spanPieces = linear ? BuildGhost.linearPieceCount() : 1;
        int multiplier = linear ? BuildGhost.linearChargeablePieceCount() : 1;

        List<String> categories = RtsHudState.buildCategories();
        int category = RtsHudState.selectedCategory();
        if (category >= 0 && category < categories.size()) {
            addWrapped(font, rows, categories.get(category), COLOR_UNIT_LABEL, null, available);
        }

        if (RtsHudState.buildingFree(building, multiplier)) {
            addWrapped(font, rows, "Free", COLOR_UNIT_LABEL, null, available);
        } else {
            for (Resource resource : Resource.VALUES) {
                int cost = RtsHudState.buildingCost(building, resource) * multiplier;
                if (cost <= 0) {
                    continue;
                }
                boolean enough = RtsHudState.stockOf(resource) >= cost;
                // The top bar's own resource icons, so the cost rows needed no new art.
                Identifier icon = Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID,
                        "textures/gui/topbar/icon_" + resource.key() + ".png");
                addWrapped(font, rows, cost + "  " + resource.label(),
                        enough ? COLOR_COST_OK : COLOR_COST_SHORT, icon,
                        available - ICON_SIZE - ICON_TEXT_GAP);
            }
        }

        if (linear) {
            addWrapped(font, rows, "Span: " + BuildGhost.linearColumns() + " × "
                    + BuildGhost.linearRows() + " (" + spanPieces + " pieces; "
                    + multiplier + " new)",
                    COLOR_UNIT_LABEL, null, available);
            if (BuildGhost.linearExistingPieceCount() > 0) {
                addWrapped(font, rows, "Already laid: " + BuildGhost.linearExistingPieceCount()
                        + " (free)", COLOR_UNIT_LABEL, null, available);
            }
            BlockPos cursor = BuildGhost.linearCursor();
            if (cursor != null) {
                addWrapped(font, rows, "Target: " + cursor.getX() + ", "
                                + cursor.getY() + ", " + cursor.getZ(),
                        COLOR_UNIT_LABEL, null, available);
            }
        }

        if (building.townHall() && RtsHudState.townHallPlaced()) {
            addWrapped(font, rows, "One Town Hall only", COLOR_COST_SHORT, null, available);
        } else if (!building.townHall() && !RtsHudState.townHallPlaced()) {
            addWrapped(font, rows, "Town Hall first", COLOR_COST_SHORT, null, available);
        } else if (!building.townHall()
                && !RtsCivilization.isFounded(Minecraft.getInstance().player)) {
            addWrapped(font, rows, "Found your civilization first", COLOR_COST_SHORT, null, available);
        } else if (!building.townHall() && !RtsHudState.coalMinePlaced()
                && !RtsHudState.isFirstCoalMine(building)) {
            addWrapped(font, rows, "Place the free Coal Mine first", COLOR_COST_SHORT, null, available);
        } else if (RtsHudState.ageLocked(building)) {
            addWrapped(font, rows, RtsHudState.ageRequirement(building), COLOR_COST_SHORT, null, available);
        } else if (!BuildGhost.validity().ok()) {
            addWrapped(font, rows, BuildGhost.validity().message(), COLOR_COST_SHORT, null, available);
        } else if (linear && multiplier == 0) {
            addWrapped(font, rows, "Already laid — no charge", COLOR_UNIT_LABEL, null, available);
        } else if (!RtsHudState.canAfford(building, multiplier)) {
            addWrapped(font, rows, "Not enough resources", COLOR_COST_SHORT, null, available);
        } else if (linear && !BuildGhost.linearStarted()) {
            addWrapped(font, rows, "Click a start cell", COLOR_UNIT_LABEL, null, available);
        } else if (linear) {
            addWrapped(font, rows, "Click the far end to lay", COLOR_UNIT_LABEL, null, available);
        } else {
            addWrapped(font, rows, "Click to place", COLOR_UNIT_LABEL, null, available);
        }

        // Read from the bindings, never written as literals: rebinding in the Controls screen
        // updates this label with no code involved, which is the point of them being real mappings.
        if (linear) {
            addWrapped(font, rows, "Start, move, click end; Esc cancel", COLOR_UNIT_LABEL,
                    null, available);
        } else {
            String keys = RtsKeyBindings.ROTATE_LEFT.getTranslatedKeyMessage().getString()
                    + "/" + RtsKeyBindings.ROTATE_RIGHT.getTranslatedKeyMessage().getString();
            addWrapped(font, rows, keys + " turn, Esc cancel", COLOR_UNIT_LABEL, null, available);
        }
        return rows;
    }

    private static List<Row> selectedConstructionRows(Font font, ConstructionInfo construction,
                                                       int available) {
        List<Row> rows = new ArrayList<>();
        addWrapped(font, rows, RtsHudState.displayName(construction), COLOR_UNIT_NAME, null, available);
        int percent = construction.totalBlocks() <= 0 ? 0
                : Math.min(100, construction.placedBlocks() * 100 / construction.totalBlocks());
        addWrapped(font, rows, "BUILDING " + percent + "%", COLOR_COST_OK, null, available);
        addWrapped(font, rows, "Blocks " + construction.placedBlocks() + " / "
                + construction.totalBlocks(), COLOR_UNIT_LABEL, null, available);
        addWrapped(font, rows, "Builders " + construction.assignedWorkers(), COLOR_UNIT_LABEL,
                null, available);
        if (percent >= 100) {
            addWrapped(font, rows, "Finishing structure...", COLOR_UNIT_LABEL, null, available);
        } else if (construction.assignedWorkers() > 0) {
            addWrapped(font, rows, "Crew is assembling the site", COLOR_UNIT_LABEL, null, available);
        } else {
            addWrapped(font, rows, "Select villagers, then click this site", COLOR_COST_SHORT,
                    null, available);
        }
        return rows;
    }

    private static boolean sameBuilding(BuildingInfo building) {
        return BuildGhost.building() != null
                && building.id().equals(BuildGhost.building().id());
    }

    /** Safe commands for the current selection; map clicks still issue movement or work orders. */
    private static void drawSelectedCommands(GuiGraphicsExtractor graphics, Font font,
                                              int left, int y, int right,
                                              int mouseX, int mouseY, int clickX, int clickY) {
        List<net.minecraft.world.entity.LivingEntity> selected = RtsHudState.selectedUnits();
        boolean hasWorker = selected.stream()
                .anyMatch(unit -> unit instanceof RtsVillagerEntity);
        int gap = COMMAND_GAP;
        String[] labels = hasWorker
                ? new String[] {"RALLY", "STOP"}
                : new String[] {"ATTACK", "RALLY", "STOP"};
        ArmyCommandPayload.Command[] commands = {
                hasWorker ? ArmyCommandPayload.Command.RALLY_HOME
                        : ArmyCommandPayload.Command.ATTACK_NEAREST,
                hasWorker ? ArmyCommandPayload.Command.STOP
                        : ArmyCommandPayload.Command.RALLY_HOME,
                ArmyCommandPayload.Command.STOP
        };
        int buttonCount = labels.length;
        int buttonWidth = Math.max(1, (right - left - (buttonCount - 1) * gap) / buttonCount);
        for (int index = 0; index < labels.length; index++) {
            int buttonLeft = left + index * (buttonWidth + gap);
            int buttonRight = index == labels.length - 1 ? right : buttonLeft + buttonWidth;
            boolean hover = contains(mouseX, mouseY, buttonLeft, y,
                    buttonRight - buttonLeft, COMMAND_ROW_HEIGHT);
            boolean pressed = contains(clickX, clickY, buttonLeft, y,
                    buttonRight - buttonLeft, COMMAND_ROW_HEIGHT);
            graphics.fill(buttonLeft, y, buttonRight, y + COMMAND_ROW_HEIGHT,
                    hover ? COLOR_COMMAND_BORDER : COLOR_COMMAND_FILL);
            graphics.outline(buttonLeft, y, buttonRight - buttonLeft, COMMAND_ROW_HEIGHT,
                    hover ? TINT_HOVER : COLOR_COMMAND_BORDER);
            int textColor = selected.isEmpty()
                    ? COLOR_COMMAND_DISABLED : COLOR_UNIT_NAME;
            graphics.text(font, labels[index], buttonLeft
                    + (buttonRight - buttonLeft - font.width(labels[index])) / 2,
                    y + 2, textColor);

            if (pressed) {
                boolean issued = BuildingSelectionController.issueSelectedCommand(commands[index]);
                if (issued) {
                    playClick(1.0F);
                } else {
                    playClick(PITCH_DENIED);
                }
            }
        }
    }

    private static List<Row> selectedUnitRows(Font font, int available) {
        List<Row> rows = new ArrayList<>();
        List<net.minecraft.world.entity.LivingEntity> units = RtsHudState.selectedUnits();
        net.minecraft.world.entity.LivingEntity unit = RtsHudState.selectedUnit();
        if (unit == null) {
            return rows;
        }
        if (units.size() > 1) {
            long workers = units.stream().filter(candidate -> candidate instanceof RtsVillagerEntity).count();
            long soldiers = units.stream().filter(RtsEntities::isAlliedCombatUnit).count();
            addWrapped(font, rows, workers > 0 && soldiers > 0 ? "MIXED HOST" : "HOST SELECTED",
                    COLOR_UNIT_NAME, null, available);
            addWrapped(font, rows, workers + " workers  ·  " + soldiers + " soldiers",
                    COLOR_UNIT_LABEL, null, available);
            addWrapped(font, rows, "HP total " + selectionHealth(units, false) + " / "
                    + selectionHealth(units, true), COLOR_UNIT_LABEL, null, available);
            if (workers > 0 && soldiers > 0) {
                addWrapped(font, rows, "Ground: all march  ·  Tree: workers gather",
                        COLOR_UNIT_LABEL, null, available);
            } else if (workers > 0) {
                addWrapped(font, rows, "Choose a tree to assign woodcutting",
                        COLOR_UNIT_LABEL, null, available);
            } else {
                addWrapped(font, rows, "Choose ground to march the host",
                        COLOR_UNIT_LABEL, null, available);
            }
        } else {
            addWrapped(font, rows, RtsEntities.unitName(unit), COLOR_UNIT_NAME, null, available);
            if (unit instanceof RtsVillagerEntity villager) {
                addWrapped(font, rows, "Role: " + workerRole(villager), COLOR_UNIT_LABEL,
                        null, available);
                addWrapped(font, rows, "HP " + RtsHudState.formatHealth(unit.getHealth()) + " / "
                        + RtsHudState.formatHealth(unit.getMaxHealth()), COLOR_UNIT_LABEL,
                        null, available);
                addWrapped(font, rows, "Inventory: " + workerInventory(villager),
                        COLOR_UNIT_LABEL, null, available);
                addWrapped(font, rows, "Task: " + RtsUnitProfileRenderer.activity(unit),
                        COLOR_UNIT_LABEL, null, available);
            } else {
                addWrapped(font, rows, RtsEntities.unitRole(unit), COLOR_UNIT_LABEL, null, available);
                addWrapped(font, rows, "HP " + RtsHudState.formatHealth(unit.getHealth()) + " / "
                        + RtsHudState.formatHealth(unit.getMaxHealth()), COLOR_UNIT_LABEL, null, available);
                addWrapped(font, rows, "Now: " + RtsUnitProfileRenderer.activity(unit),
                        COLOR_UNIT_LABEL, null, available);
            }
        }
        return rows;
    }

    private static String selectionHealth(List<net.minecraft.world.entity.LivingEntity> units,
                                           boolean maximum) {
        float total = 0.0F;
        for (net.minecraft.world.entity.LivingEntity unit : units) {
            total += maximum ? unit.getMaxHealth() : unit.getHealth();
        }
        return RtsHudState.formatHealth(total);
    }

    private static String workerRole(RtsVillagerEntity worker) {
        return switch (worker.getVariant()) {
            case RtsVillagerEntity.VARIANT_MINER -> "Miner";
            case RtsVillagerEntity.VARIANT_WOODCUTTER -> "Woodcutter";
            case RtsVillagerEntity.VARIANT_BUILDER -> "Builder";
            case RtsVillagerEntity.VARIANT_FORAGER -> "Forager";
            default -> "Farmer";
        };
    }

    private static String workerInventory(RtsVillagerEntity worker) {
        int carried = worker.getCarriedWood();
        return carried <= 0 ? "Empty" : "Wood " + carried + " / " + RtsWorkerOrders.WOOD_CAPACITY;
    }

    private static List<Row> selectedTargetRows(Font font,
            net.minecraft.world.entity.LivingEntity target, int available) {
        List<Row> rows = new ArrayList<>();
        addWrapped(font, rows, "Target: " + RtsEntities.unitName(target), COLOR_TARGET, null, available);
        addWrapped(font, rows, "Health " + RtsHudState.formatHealth(target.getHealth()) + " / "
                + RtsHudState.formatHealth(target.getMaxHealth()), COLOR_TARGET, null, available);
        addWrapped(font, rows, "Cursor hit: 0.5 damage", COLOR_UNIT_LABEL, null, available);
        return rows;
    }

    private static List<Row> selectedPlacedRows(Font font, PlacedBuildingInfo building, int available) {
        List<Row> rows = new ArrayList<>();
        addWrapped(font, rows, RtsHudState.displayName(building), COLOR_UNIT_NAME, null, available);
        addWrapped(font, rows, tr("gui.forgotten_realms_rts.level", building.level()),
                COLOR_UNIT_LABEL, null, available);
        if (building.maxHealth() > 0) {
            addWrapped(font, rows, "HP " + Math.max(0, building.health()) + " / "
                            + building.maxHealth(),
                    building.health() < building.maxHealth() ? COLOR_COST_SHORT : COLOR_COST_OK,
                    null, available);
        }

        if (isTownHallStructure(building)) {
            addWrapped(font, rows, "Train worker: 20 Food",
                    RtsHudState.stockOf(Resource.FOOD) >= RtsEntities.VILLAGER_FOOD_COST
                            ? COLOR_COST_OK : COLOR_COST_SHORT,
                    null, available);
        } else if (isMerchantStructure(building)) {
            addWrapped(font, rows, "Brings 2 Gold + 1 Iron / "
                            + (RtsProduction.CYCLE_TICKS / 20) + "s",
                    COLOR_COST_OK, null, available);
        } else if (RtsMineOrders.isMineStructure(building.structure())) {
            MineStatusPayload status = RtsHudState.selectedMineStatus();
            if (status != null) {
                addWrapped(font, rows, "Inside " + status.workersInside() + " / " + status.capacity(),
                        COLOR_UNIT_LABEL, null, available);
                addWrapped(font, rows, "Yield " + status.output() + " " + status.resource()
                                + mineBonusText(status) + " / " + status.intervalSeconds() + "s",
                        COLOR_COST_OK, null, available);
            }
        } else if (RtsFarmOrders.isFarmStructure(building.structure())) {
            FarmStatusPayload status = RtsHudState.selectedFarmStatus();
            if (status != null) {
                addWrapped(font, rows, "Workers " + status.workersInside() + " / " + status.capacity(),
                        COLOR_UNIT_LABEL, null, available);
                addWrapped(font, rows, "Output " + status.output() + " Food / "
                                + status.intervalSeconds() + "s",
                        COLOR_COST_OK, null, available);
            } else {
                addWrapped(font, rows, "One worker per Farm",
                        COLOR_UNIT_LABEL, null, available);
            }
        } else if (isBarracksStructure(building)) {
            addWrapped(font, rows, "Fighter uses " + RtsEntities.FIGHTER_POPULATION_COST
                            + " population",
                    COLOR_UNIT_LABEL, null, available);
            addWrapped(font, rows, "Costs " + RtsEntities.GUARDIAN_FOOD_COST + " Food + "
                            + RtsEntities.GUARDIAN_IRON_COST + " Iron",
                    fighterCostColor(), null, available);
            addWrapped(font, rows, "Roster: 24-50 HP · 2-8 damage",
                    COLOR_UNIT_LABEL, null, available);
        }

        if (BuildGhost.hasSession()) {
            String state = BuildGhost.validity().ok()
                    ? tr(BuildGhost.mode() == BuildGhost.Mode.UPGRADE
                        ? "gui.forgotten_realms_rts.confirm_upgrade"
                        : "gui.forgotten_realms_rts.confirm_move")
                    : BuildGhost.validity().message();
            addWrapped(font, rows, state,
                    BuildGhost.validity().ok() ? COLOR_UNIT_LABEL : COLOR_COST_SHORT,
                    null, available);
        } else if (BuildingSelectionController.isDemolishArmed(building.id())) {
            // A demolish is destructive and irreversible; the first press only armed it, and this
            // row is the confirmation prompt for the second.
            addWrapped(font, rows, tr("gui.forgotten_realms_rts.confirm_demolish",
                    keyName(RtsKeyBindings.DEMOLISH_SELECTED)),
                    COLOR_COST_SHORT, null, available);
        } else if (isTownHallStructure(building)) {
            addWrapped(font, rows, "Town Hall protected from deletion",
                    COLOR_UNIT_LABEL, null, available);
        } else {
            addWrapped(font, rows, building.canUpgrade()
                    ? tr("gui.forgotten_realms_rts.action_shortcuts",
                        keyName(RtsKeyBindings.MOVE_SELECTED),
                        keyName(RtsKeyBindings.UPGRADE_SELECTED),
                        keyName(RtsKeyBindings.DEMOLISH_SELECTED))
                    : tr("gui.forgotten_realms_rts.move_shortcut",
                        keyName(RtsKeyBindings.MOVE_SELECTED),
                        keyName(RtsKeyBindings.DEMOLISH_SELECTED)),
                    COLOR_UNIT_LABEL, null, available);
        }

        if (!building.canUpgrade()) {
            addWrapped(font, rows, "MAX LEVEL", COLOR_UNIT_LABEL, null, available);
        }
        return rows;
    }

    /** The three actions available for every server-tracked building. */
    private static void drawBuildingActionButtons(GuiGraphicsExtractor graphics, Font font,
                                                    PlacedBuildingInfo building, int left, int y,
                                                    int right, int mouseX, int mouseY,
                                                    int clickX, int clickY) {
        String[] labels = {"MOVE", "UPGRADE", "DELETE"};
        int gap = COMMAND_GAP;
        int buttonWidth = Math.max(1, (right - left - 2 * gap) / labels.length);
        Minecraft minecraft = Minecraft.getInstance();
        for (int index = 0; index < labels.length; index++) {
            int buttonLeft = left + index * (buttonWidth + gap);
            int buttonRight = index == labels.length - 1 ? right : buttonLeft + buttonWidth;
            boolean hover = contains(mouseX, mouseY, buttonLeft, y,
                    buttonRight - buttonLeft, COMMAND_ROW_HEIGHT);
            boolean pressed = contains(clickX, clickY, buttonLeft, y,
                    buttonRight - buttonLeft, COMMAND_ROW_HEIGHT);
            if (index == 1 && RtsHudState.isUpgradePopupOpen(building.id())
                    && !hover && !upgradePopupHovered(minecraft, building)) {
                RtsHudState.closeUpgradePopup();
            }
            boolean protectedTownHall = index == 2 && isTownHallStructure(building);
            boolean armed = !protectedTownHall && index == 2
                    && BuildingSelectionController.isDemolishArmed(building.id());
            int fill = protectedTownHall ? COLOR_COMMAND_DISABLED : armed ? COLOR_ACTION_CONFIRM
                    : hover ? COLOR_COMMAND_BORDER : COLOR_COMMAND_FILL;
            int border = protectedTownHall ? COLOR_COMMAND_DISABLED : armed ? COLOR_COST_SHORT
                    : hover ? TINT_HOVER : COLOR_COMMAND_BORDER;
            graphics.fill(buttonLeft, y, buttonRight, y + COMMAND_ROW_HEIGHT, fill);
            graphics.outline(buttonLeft, y, buttonRight - buttonLeft, COMMAND_ROW_HEIGHT, border);

            String label = protectedTownHall ? "PROTECTED" : armed ? "CONFIRM" : labels[index];
            drawFittedCenteredText(graphics, font, label,
                    buttonLeft + (buttonRight - buttonLeft) / 2, y + 2,
                    buttonRight - buttonLeft - 2, COLOR_UNIT_NAME);

            if (!pressed) {
                continue;
            }
            boolean accepted;
            if (index == 0) {
                accepted = BuildingSelectionController.beginMove();
            } else if (index == 1) {
                accepted = BuildingSelectionController.pressUpgradeButton();
            } else if (protectedTownHall) {
                accepted = false;
            } else {
                accepted = BuildingSelectionController.beginDemolish();
            }
            playClick(accepted ? 1.0F : PITCH_DENIED);
        }
    }

    private static void drawTrainWorkerButton(GuiGraphicsExtractor graphics, Font font, long townHallId,
                                               int left, int y, int right, int mouseX, int mouseY,
                                               int clickX, int clickY) {
        boolean affordable = canTrainWorker();
        boolean hover = contains(mouseX, mouseY, left, y, right - left, COMMAND_ROW_HEIGHT);
        boolean pressed = contains(clickX, clickY, left, y, right - left, COMMAND_ROW_HEIGHT);
        int fill = !affordable ? COLOR_COMMAND_DISABLED
                : hover ? COLOR_COMMAND_BORDER : COLOR_COMMAND_FILL;
        int border = !affordable ? COLOR_COMMAND_DISABLED : COLOR_COMMAND_BORDER;
        graphics.fill(left, y, right, y + COMMAND_ROW_HEIGHT, fill);
        graphics.outline(left, y, right - left, COMMAND_ROW_HEIGHT, border);
        String label = "TRAIN WORKER";
        graphics.text(font, label, left + (right - left - font.width(label)) / 2,
                y + 2, affordable ? COLOR_UNIT_NAME : COLOR_UNIT_LABEL);
        if (pressed) {
            ClientPacketDistributor.sendToServer(new TrainVillagerPayload(townHallId));
            playClick(affordable ? 1.0F : PITCH_DENIED);
        }
    }

    private static void drawRecallWorkersButton(GuiGraphicsExtractor graphics, Font font,
                                                long buildingId, int left, int y, int right,
                                                int mouseX, int mouseY, int clickX, int clickY) {
        boolean hover = contains(mouseX, mouseY, left, y, right - left, COMMAND_ROW_HEIGHT);
        boolean pressed = contains(clickX, clickY, left, y, right - left, COMMAND_ROW_HEIGHT);
        graphics.fill(left, y, right, y + COMMAND_ROW_HEIGHT,
                hover ? COLOR_COMMAND_BORDER : COLOR_COMMAND_FILL);
        graphics.outline(left, y, right - left, COMMAND_ROW_HEIGHT, COLOR_COMMAND_BORDER);
        String label = "RECALL WORKERS";
        graphics.text(font, label, left + (right - left - font.width(label)) / 2,
                y + 2, COLOR_UNIT_NAME);
        if (pressed) {
            ClientPacketDistributor.sendToServer(new RecallWorkersPayload(buildingId));
            playClick(1.0F);
        }
    }

    private static void drawTrainFighterButton(GuiGraphicsExtractor graphics, Font font,
                                               int left, int y, int right, int mouseX, int mouseY,
                                               int clickX, int clickY) {
        boolean affordable = canTrainFighter();
        boolean hover = contains(mouseX, mouseY, left, y, right - left, COMMAND_ROW_HEIGHT);
        boolean pressed = contains(clickX, clickY, left, y, right - left, COMMAND_ROW_HEIGHT);
        int fill = !affordable ? COLOR_COMMAND_DISABLED
                : hover ? COLOR_COMMAND_BORDER : COLOR_COMMAND_FILL;
        int border = !affordable ? COLOR_COMMAND_DISABLED : COLOR_COMMAND_BORDER;
        graphics.fill(left, y, right, y + COMMAND_ROW_HEIGHT, fill);
        graphics.outline(left, y, right - left, COMMAND_ROW_HEIGHT, border);
        String label = "TRAIN FIGHTER";
        graphics.text(font, label, left + (right - left - font.width(label)) / 2,
                y + 2, affordable ? COLOR_UNIT_NAME : COLOR_UNIT_LABEL);
        if (pressed) {
            ClientPacketDistributor.sendToServer(new TrainGuardianPayload());
            playClick(affordable ? 1.0F : PITCH_DENIED);
        }
    }

    /** Draws the persistent price readout above the selected-building card at normal GUI scale. */
    private static void drawUpgradePricePopup(GuiGraphicsExtractor graphics, Minecraft minecraft,
                                               int barTop) {
        PlacedBuildingInfo building = RtsHudState.selectedPlacedBuilding();
        if (building == null || !RtsHudState.isUpgradePopupOpen(building.id())) {
            return;
        }

        UpgradePopupBounds popup = upgradePopupBounds(minecraft, barTop, building);
        int x = popup.x();
        int y = popup.y();
        int width = popup.width();
        int height = popup.height();
        int contentX = x + UPGRADE_POPUP_PADDING;
        int contentWidth = Math.max(1, width - 2 * UPGRADE_POPUP_PADDING);

        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, COLOR_POPUP_BORDER);
        graphics.fill(x, y, x + width, y + height, COLOR_POPUP_FILL);
        graphics.outline(x, y, width, height, COLOR_POPUP_BORDER);

        drawFittedCenteredText(graphics, minecraft.font, "UPGRADE PRICE",
                x + width / 2, y + UPGRADE_POPUP_PADDING, contentWidth, COLOR_POPUP_TEXT);
        int lineY = y + UPGRADE_POPUP_PADDING + minecraft.font.lineHeight
                + UPGRADE_POPUP_LINE_GAP;
        String level = building.canUpgrade()
                ? "LEVEL " + building.level() + " -> " + (building.level() + 1)
                : "LEVEL " + building.level();
        graphics.text(minecraft.font, level, contentX, lineY, COLOR_UNIT_LABEL);
        lineY += minecraft.font.lineHeight + UPGRADE_POPUP_LINE_GAP;

        if (!building.canUpgrade()) {
            graphics.text(minecraft.font, "MAX LEVEL", contentX, lineY, COLOR_POPUP_TEXT);
            return;
        }

        int costRows = drawUpgradePriceRows(graphics, minecraft.font, building, contentX, lineY,
                contentWidth);
        lineY += costRows * upgradePopupRowHeight(minecraft.font) + UPGRADE_POPUP_LINE_GAP;
        boolean affordable = RtsHudState.canAffordUpgrade(building);
        graphics.text(minecraft.font, affordable ? "Ready to upgrade" : "Need more resources",
                contentX, lineY, affordable ? COLOR_POPUP_OK : COLOR_POPUP_SHORT);
    }

    /** Draws every nonzero server-sent cost in two columns and returns the number of rows used. */
    private static int drawUpgradePriceRows(GuiGraphicsExtractor graphics, Font font,
                                            PlacedBuildingInfo building, int x, int y,
                                            int width) {
        int columnWidth = Math.max(1, (width - UPGRADE_POPUP_COLUMN_GAP) / 2);
        int labelWidth = Math.max(1, columnWidth - ICON_SIZE - ICON_TEXT_GAP);
        int rowHeight = upgradePopupRowHeight(font);
        int count = 0;
        for (Resource resource : Resource.VALUES) {
            int cost = RtsHudState.upgradeCost(building, resource);
            if (cost <= 0) {
                continue;
            }
            int column = count % 2;
            int row = count / 2;
            int cellX = x + column * (columnWidth + UPGRADE_POPUP_COLUMN_GAP);
            int rowY = y + row * rowHeight;
            Identifier icon = Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID,
                    "textures/gui/topbar/icon_" + resource.key() + ".png");
            HudTextures.blitWhole(graphics, icon, cellX, rowY, ICON_SIZE, ICON_SIZE,
                    RESOURCE_ICON_SRC, RESOURCE_ICON_SRC);
            String label = cost + " " + resource.label();
            if (font.width(label) > labelWidth) {
                label = cost + " " + resource.key();
            }
            if (font.width(label) > labelWidth) {
                label = Integer.toString(cost);
            }
            drawFittedCenteredText(graphics, font, label,
                    cellX + ICON_SIZE + ICON_TEXT_GAP + labelWidth / 2,
                    rowY, labelWidth,
                    RtsHudState.stockOf(resource) >= cost ? COLOR_POPUP_OK : COLOR_POPUP_SHORT);
            count++;
        }
        if (count == 0) {
            graphics.text(font, "Free", x, y, COLOR_UNIT_LABEL);
            return 1;
        }
        return (count + 1) / 2;
    }

    private static int upgradePopupRowHeight(Font font) {
        return Math.max(font.lineHeight, ICON_SIZE) + UPGRADE_POPUP_LINE_GAP;
    }

    private static UpgradePopupBounds upgradePopupBounds(Minecraft minecraft, int barTop,
                                                          PlacedBuildingInfo building) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int width = Math.min(UPGRADE_POPUP_MAX_WIDTH,
                Math.max(1, screenWidth - 2 * UPGRADE_POPUP_SCREEN_MARGIN));
        int height = upgradePopupHeight(minecraft.font, building);
        float scale = contentScale(screenWidth, screenHeight);
        int cardLeft = Math.round(MARGIN * scale);
        int cardWidth = Math.max(1, Math.round(UNIT_PANEL_WIDTH * scale));
        int x = cardLeft + (cardWidth - width) / 2;
        int maxX = screenWidth - UPGRADE_POPUP_SCREEN_MARGIN - width;
        x = Math.max(UPGRADE_POPUP_SCREEN_MARGIN, Math.min(x, maxX));

        int cardTop = barTop + Math.round((MARGIN + LEFT_SECTION_Y_NUDGE) * scale);
        int y = cardTop - height - UPGRADE_POPUP_GAP;
        int minimumY = RtsTopBarHud.currentHeight(minecraft) + UPGRADE_POPUP_GAP;
        return new UpgradePopupBounds(x, Math.max(minimumY, y), width, height);
    }

    private static boolean upgradePopupHovered(Minecraft minecraft, PlacedBuildingInfo building) {
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int barTop = screenHeight - currentHeight(minecraft);
        UpgradePopupBounds popup = upgradePopupBounds(minecraft, barTop, building);
        return contains(RtsMouseController.mouseX(minecraft), RtsMouseController.mouseY(minecraft),
                popup.x(), popup.y(), popup.width(), popup.height());
    }

    private static int upgradePopupHeight(Font font, PlacedBuildingInfo building) {
        int costCount = 0;
        if (building.canUpgrade()) {
            for (Resource resource : Resource.VALUES) {
                if (RtsHudState.upgradeCost(building, resource) > 0) {
                    costCount++;
                }
            }
        }
        int costRows = building.canUpgrade() ? Math.max(1, (costCount + 1) / 2) : 1;
        return 2 * UPGRADE_POPUP_PADDING
                + 2 * font.lineHeight
                + 2 * UPGRADE_POPUP_LINE_GAP
                + costRows * upgradePopupRowHeight(font)
                + (building.canUpgrade()
                        ? UPGRADE_POPUP_LINE_GAP + font.lineHeight : 0);
    }

    private record UpgradePopupBounds(int x, int y, int width, int height) {
    }

    private static boolean canTrainWorker() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && RtsEconomy.population(minecraft.player) < RtsEconomy.populationCap(minecraft.player)
                && RtsHudState.stockOf(Resource.FOOD) >= RtsEntities.VILLAGER_FOOD_COST;
    }

    private static boolean canTrainFighter() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && RtsCivilization.age(minecraft.player) >= 1
                && RtsHudState.stockOf(Resource.FOOD) >= RtsEntities.GUARDIAN_FOOD_COST
                && RtsHudState.stockOf(Resource.IRON) >= RtsEntities.GUARDIAN_IRON_COST
                && RtsEconomy.population(minecraft.player) + RtsEntities.FIGHTER_POPULATION_COST
                <= RtsEconomy.populationCap(minecraft.player);
    }

    private static int fighterCostColor() {
        return RtsHudState.stockOf(Resource.FOOD) >= RtsEntities.GUARDIAN_FOOD_COST
                && RtsHudState.stockOf(Resource.IRON) >= RtsEntities.GUARDIAN_IRON_COST
                ? COLOR_COST_OK : COLOR_COST_SHORT;
    }

    private static String mineBonusText(MineStatusPayload status) {
        return status.bonusOutput() > 0 && status.bonusResource() != null
                && !status.bonusResource().isEmpty()
                ? " + " + status.bonusOutput() + " " + status.bonusResource() : "";
    }

    private static boolean isTownHallStructure(PlacedBuildingInfo building) {
        return building != null && "hall".equals(ModPayloads.buildingOf(building.structure()));
    }

    private static boolean isMerchantStructure(PlacedBuildingInfo building) {
        return building != null && "merchant".equals(ModPayloads.buildingOf(building.structure()));
    }

    private static boolean isBarracksStructure(PlacedBuildingInfo building) {
        return building != null && building.structure().getPath().contains("military/space");
    }

    /** Splits a string to the available width and adds one {@link Row} per resulting line. */
    private static void addWrapped(Font font, List<Row> rows, String text, int color,
                                   Identifier icon, int available) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(1, available));
        for (int i = 0; i < lines.size(); i++) {
            // Only the first line of a wrapped row carries the icon.
            rows.add(new Row(lines.get(i), color, i == 0 ? icon : null));
        }
    }

    private static void drawRows(GuiGraphicsExtractor graphics, Font font, List<Row> rows,
                                 int x, int y, int bottom) {
        for (Row row : rows) {
            if (y + font.lineHeight > bottom) {
                break;
            }
            int textX = x;
            if (row.icon() != null) {
                HudTextures.blitWhole(graphics, row.icon(), x, y, ICON_SIZE, ICON_SIZE,
                        RESOURCE_ICON_SRC, RESOURCE_ICON_SRC);
                textX += ICON_SIZE + ICON_TEXT_GAP;
            }
            graphics.text(font, row.text(), textX, y, row.color());
            y += font.lineHeight;
        }
    }

    /**
     * The structures tray: a grid of <strong>empty</strong> container frames.
     *
     * <p>No artwork and no labels inside them on purpose. The slots are filled from
     * {@link RtsHudState#buildSlots()}, which is empty right now, so what a backend adds or removes
     * later changes the tray without touching this class. Baking building pictures in would have
     * meant re-cutting art every time the roster changed.
     *
     * <p>Column count comes from the space between {@code startX} and {@code endX}, so the tray
     * fills the bar instead of ending in dead stone.
     */
    private static void drawSlots(GuiGraphicsExtractor graphics, int startX, int endX,
                                  int mouseX, int mouseY, int clickX, int clickY) {
        List<BuildingInfo> filled = RtsHudState.visibleBuildings();
        int available = Math.max(0, endX - startX + ITEM_GAP);
        int columns = Math.max(0, available / (SLOT_WIDTH + ITEM_GAP));

        // At a large GUI scale the tray may not have room for everything the state holds. Give back
        // one column for an arrow rather than silently truncating, so it is visible that there is
        // more than what is shown.
        boolean overflowing = filled.size() > columns * SLOT_ROWS;
        if (overflowing && columns > 1) {
            columns--;
        }

        BuildingInfo selected = RtsHudState.selectedBuilding();
        for (int row = 0; row < SLOT_ROWS; row++) {
            int y = MARGIN + row * (SLOT_HEIGHT + ITEM_GAP);
            for (int column = 0; column < columns; column++) {
                int x = startX + column * (SLOT_WIDTH + ITEM_GAP);
                int index = row * columns + column;
                BuildingInfo building = index < filled.size() ? filled.get(index) : null;

                Identifier frame = building != null && building.equals(selected)
                        ? SLOT_SELECTED : SLOT_EMPTY;
                blitButton(graphics, frame, x, y, SLOT_WIDTH, SLOT_HEIGHT,
                        SLOT_SRC_W, SLOT_SRC_H, mouseX, mouseY);

                if (building == null) {
                    continue;
                }

                // Unavailable entries stay visible so the player can inspect them, but the label
                // says why this one is unavailable rather than treating every reason as a lock.
                boolean locked = !RtsHudState.canBuild(building);

                // The building itself, rendered in 3D inside the frame. Inset so the frame's own
                // border is not drawn over.
                int inset = Math.max(1, SLOT_WIDTH / 8);
                RtsBuildingPreview.draw(graphics, building.id(), x + inset, y + inset,
                        SLOT_WIDTH - 2 * inset, SLOT_HEIGHT - 2 * inset);
                if (locked) {
                    graphics.fill(x + inset, y + inset,
                            x + SLOT_WIDTH - inset, y + SLOT_HEIGHT - inset, COLOR_LOCKED_WASH);
                    String lock = RtsHudState.availabilityLabel(building);
                    graphics.fill(x + 1, y + SLOT_HEIGHT - Minecraft.getInstance().font.lineHeight - 2,
                            x + SLOT_WIDTH - 1, y + SLOT_HEIGHT - 1, 0xC018120D);
                    drawFittedCenteredText(graphics, Minecraft.getInstance().font, lock,
                            x + SLOT_WIDTH / 2,
                            y + SLOT_HEIGHT - Minecraft.getInstance().font.lineHeight - 1,
                            SLOT_WIDTH - 2, COLOR_COST_SHORT);
                }

                if (RtsHudState.newTownGuide() && building.townHall()) {
                    drawNewTownGuide(graphics, x, y);
                }

                if (contains(mouseX, mouseY, x, y, SLOT_WIDTH, SLOT_HEIGHT)) {
                    hoveredBuilding = building;
                }

                if (contains(clickX, clickY, x, y, SLOT_WIDTH, SLOT_HEIGHT)) {
                    if (locked) {
                        // Inspection is allowed even when placement is not. Cancel first so a
                        // previous ghost cannot survive this click, then keep the building selected
                        // for the left description panel to explain the lock reason.
                        BuildGhost.cancel();
                        RtsHudState.setSelectedPlacedBuilding(null);
                        RtsHudState.setSelectedBuilding(building);
                        playClick(PITCH_DENIED);
                    } else if (building.equals(selected)) {
                        // Clicking the open slot again puts the ghost away. Right-click would be the
                        // usual RTS cancel, but right-drag already turns the camera here, so the
                        // slot itself is the toggle.
                        BuildGhost.cancel();
                        playClick(PITCH_DENIED);
                    } else {
                        if (building.townHall()) {
                            RtsHudState.clearNewTownGuide();
                        }
                        BuildGhost.beginPlace(building);
                        playClick(1.0F);
                    }
                }
            }
        }

        if (overflowing && columns > 0) {
            int arrowWidth = chevronWidth();
            int arrowX = startX + columns * (SLOT_WIDTH + ITEM_GAP);
            int arrowY = MARGIN + (CONTENT_HEIGHT - SLOT_HEIGHT) / 2;
            blitButton(graphics, CHEVRON_RIGHT, arrowX, arrowY, arrowWidth, SLOT_HEIGHT,
                    CHEVRON_SRC_W, CHEVRON_SRC_H, mouseX, mouseY);
        }
    }

    /** Points the player at the first actionable slot after a confirmed replacement-town reset. */
    private static void drawNewTownGuide(GuiGraphicsExtractor graphics, int x, int y) {
        int pulse = (int)((Util.getMillis() / 350L) % 2L);
        int accent = pulse == 0 ? 0xFFF6D36A : 0xFFFFF0A6;
        graphics.fill(x - 2, y - 2, x + SLOT_WIDTH + 2, y, accent);
        graphics.fill(x - 2, y + SLOT_HEIGHT, x + SLOT_WIDTH + 2, y + SLOT_HEIGHT + 2, accent);
        graphics.fill(x - 2, y, x, y + SLOT_HEIGHT, accent);
        graphics.fill(x + SLOT_WIDTH, y, x + SLOT_WIDTH + 2, y + SLOT_HEIGHT, accent);
        int centre = x + SLOT_WIDTH / 2;
        graphics.fill(centre - 1, y - 9, centre + 1, y, accent);
        String label = "NEW TOWN HALL";
        Font font = Minecraft.getInstance().font;
        int labelWidth = font.width(label);
        int labelX = centre - labelWidth / 2;
        int labelY = y - font.lineHeight - 10;
        graphics.fill(labelX - 3, labelY - 2, labelX + labelWidth + 3,
                labelY + font.lineHeight + 1, 0xD01B140C);
        graphics.text(font, label, labelX, labelY, accent);
        graphics.text(font, "▼", centre - font.width("▼") / 2, y - 10, accent);
    }

    /**
     * Structure category buttons: a two-row block at the right end of the bar. Labels come from
     * {@link RtsHudState#buildCategories()}, so what the categories are is data, not code — same
     * reasoning as the empty structure slots they filter.
     *
     * <p>Clicking a tab switches the open category (and cancels any in-flight ghost), so the tray
     * below shows that category's structures.
     */
    private static void drawCategories(GuiGraphicsExtractor graphics, Font font, int startX,
                                       int mouseX, int mouseY, int clickX, int clickY) {
        List<String> categories = RtsHudState.buildCategories();
        int active = RtsHudState.selectedCategory();
        int columns = categoryColumns();
        for (int i = 0; i < categories.size(); i++) {
            int x = startX + (i % columns) * CATEGORY_WIDTH;
            int y = MARGIN + (i / columns) * CATEGORY_HEIGHT;
            blitButton(graphics, i == active ? SLOT_SELECTED : SLOT_EMPTY,
                    x, y, CATEGORY_WIDTH, CATEGORY_HEIGHT,
                    SLOT_SRC_W, SLOT_SRC_H, mouseX, mouseY);
            drawRotatedLabel(graphics, font, categories.get(i),
                    x + CATEGORY_WIDTH / 2, y + CATEGORY_HEIGHT / 2,
                    CATEGORY_HEIGHT - 2 * CATEGORY_LABEL_PADDING);

            if (contains(clickX, clickY, x, y, CATEGORY_WIDTH, CATEGORY_HEIGHT)) {
                // Re-clicking the open tab is not a no-op the player should hear as success.
                playClick(i == active ? PITCH_DENIED : 1.0F);
                if (i != active) {
                    BuildGhost.cancel();
                }
                RtsHudState.setSelectedCategory(i);
            }
        }
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * One click sound, two pitches — the convention blueprint 020 set: normal for an action that
     * happened, pitched down for one that was rejected. One asset, two meanings.
     */
    private static void playClick(float pitch) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.UI_CLICK.get(), pitch));
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String keyName(net.minecraft.client.KeyMapping binding) {
        return binding.getTranslatedKeyMessage().getString();
    }

    /**
     * Draws a label turned a quarter turn, centred on the given point, so it reads down a narrow
     * tab with the glyph tops facing left. Rotating the pose is the only way to do this — the font
     * itself only draws horizontally — and it has to be undone immediately or every later draw
     * inherits the rotation.
     *
     * <p>The sign is the one thing to get right here: GUI space has +Y pointing down, so a positive
     * angle turns clockwise on screen. This is the direction that was asked for after the other one
     * was tried.
     */
    private static void drawRotatedLabel(GuiGraphicsExtractor graphics, Font font, String label,
                                         int centreX, int centreY, int available) {
        // Once rotated, the label's *width* has to fit the tab's height. "Resource" is 48px against
        // a 35px tab, so it is scaled down to fit rather than being allowed to run past the frame.
        // Shrinking beats truncating here: the words are short and the whole point is reading them.
        int width = Math.max(1, font.width(label));
        float scale = Math.min(1.0F, (float) available / width);

        graphics.pose().pushMatrix();
        graphics.pose().translate(centreX, centreY);
        graphics.pose().rotate((float) (Math.PI / 2.0));
        graphics.pose().scale(scale, scale);
        graphics.text(font, label, -width / 2, -font.lineHeight / 2, COLOR_UNIT_NAME);
        graphics.pose().popMatrix();
    }

    /** Keeps the complete LOCKED label inside narrow building slots instead of clipping its last
     * character. The scale is only used when the slot is too narrow for the current GUI font. */
    private static void drawFittedCenteredText(GuiGraphicsExtractor graphics, Font font,
                                               String text, int centreX, int y, int maxWidth,
                                               int color) {
        int textWidth = Math.max(1, font.width(text));
        float scale = Math.min(1.0F, Math.max(1.0F, maxWidth) / (float) textWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(centreX, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -textWidth / 2, 0, color);
        graphics.pose().popMatrix();
    }

    private static void blitButton(GuiGraphicsExtractor graphics, Identifier texture,
                                   int x, int y, int width, int height,
                                   int sourceWidth, int sourceHeight, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        HudTextures.blitWhole(graphics, texture, x, y, width, height, sourceWidth, sourceHeight,
                hover ? TINT_HOVER : TINT_NONE);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "textures/gui/bottombar/" + name + ".png");
    }
}
