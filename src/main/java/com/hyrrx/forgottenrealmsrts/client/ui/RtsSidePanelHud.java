package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import com.hyrrx.forgottenrealmsrts.FlagDesign;
import com.hyrrx.forgottenrealmsrts.Resource;
import com.hyrrx.forgottenrealmsrts.RtsCivilization;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;
import com.hyrrx.forgottenrealmsrts.network.AdvanceAgePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * The right-hand column: the minimap flush under the top bar, and the civilisation readout flush
 * under the minimap.
 *
 * <p><strong>One container, not two.</strong> The map panel draws only its side rails and the
 * civilisation panel draws its sides plus a bottom rail, so the column reads as a single box hanging
 * off the bar above it rather than as separate widgets with a seam between them. That is what the
 * {@link RtsPanel} edge flags are for.
 *
 * <p><strong>The civilisation readout is drawn from {@link RtsHudState}, not blitted.</strong> The
 * original sprite had its numbers painted into the artwork, which meant they could never change.
 * Now the shield and the stat glyphs are bare icons and every string comes from live synced state
 * ({@link RtsHudState#stats()} reads the economy, battle and civilization attachments each frame).
 * The row count is taken from the list, so the panel grows and shrinks with the data.
 *
 * <p><strong>The minimap is live.</strong> It used to be a static {@code minimap_content.png};
 * {@link RtsMinimap} now samples the client's loaded chunks into a dynamic texture and draws
 * everything the client has not been sent as fog. The constants below still describe the old
 * sprite's 404x219 aspect ratio on purpose — that is what fixes the map block's height in this
 * column, and changing it would move every panel underneath.
 */
public final class RtsSidePanelHud {
    private static final int MINIMAP_SRC_W = 404;
    private static final int MINIMAP_SRC_H = 219;

    /** Share of screen width the column occupies, and the bounds that keep it sane. */
    private static final float WIDTH_FRACTION = 0.22F;
    private static final int MIN_WIDTH = 110;
    private static final int MAX_WIDTH = 240;

    private static final int PADDING = 4;
    /** Tight on purpose: the readout's last row should sit close to the bottom rail, not float
     *  above a band of empty stone. */
    private static final int BOTTOM_PADDING = 1;
    private static final int ROW_GAP = 2;
    private static final int ICON_TEXT_GAP = 4;
    /** Small enough to share the age header, while the two-pixel dark frame remains visible. */
    private static final int FLAG_WIDTH = 28;
    private static final int FLAG_HEIGHT = 17;

    private static final int COLOR_LABEL = 0xFFB8AC85;
    private static final int COLOR_VALUE = 0xFFF4E9C8;
    private static final int COLOR_AGE = 0xFFE8C87A;
    private static final int COLOR_UPGRADE = 0xFFD8A847;
    private static final int COLOR_UPGRADE_TEXT = 0xFFF4E9C8;
    private static final int COLOR_UPGRADE_SHORT = 0xFFE07A68;

    /** The card is the entry point; the details popup is deliberately kept outside the column. */
    private static final int UPGRADE_BUTTON_HEIGHT = 18;
    private static final int UPGRADE_BUTTON_GAP = 3;
    private static final int UPGRADE_POPUP_MAX_WIDTH = 220;
    private static final int UPGRADE_POPUP_PADDING = 7;
    private static final int UPGRADE_POPUP_LINE_GAP = 2;
    private static boolean upgradeOpen;

    private RtsSidePanelHud() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsSidePanelHud::onRenderGui);
    }

    /** Clears the civilization price popup when the client leaves its current player/world. */
    public static void clearTransientState() {
        upgradeOpen = false;
    }

    /** Width of the column. Public because the top bar has to know where to stop drawing its
     *  bottom rail, so the two meet without a wooden strip between them. */
    public static int columnWidth(int screenWidth) {
        return panelWidth(screenWidth);
    }

    private static int panelWidth(int screenWidth) {
        return Math.clamp(Math.round(screenWidth * WIDTH_FRACTION), MIN_WIDTH, MAX_WIDTH);
    }

    /** Height of the civilisation block, which follows however many stats the state holds. */
    private static int civHeight(Font font) {
        int rows = RtsHudState.stats().size();
        int header = Math.max(FLAG_HEIGHT, font.lineHeight * 2);
        return PADDING + header + UPGRADE_BUTTON_GAP
                + UPGRADE_BUTTON_HEIGHT + ROW_GAP
                + rows * (statRowHeight(font) + ROW_GAP)
                + BOTTOM_PADDING
                + RtsPanel.capBottomHeight(RtsPanel.DEFAULT_TEXTURE_SCALE);
    }

    /** Rows are a shade taller than the text so the glyphs are not downscaled into mush. */
    private static int statRowHeight(Font font) {
        return font.lineHeight + 3;
    }

    private static int innerWidth(int width) {
        return width - 2 * RtsPanel.capSideWidth(RtsPanel.DEFAULT_TEXTURE_SCALE) - 2 * PADDING;
    }

    /** Height of the map block, measured from {@link #railTop} — the map's top edge is level with
     *  the wooden rail either side of the column, not below it. */
    private static int mapHeight(int width) {
        return innerWidth(width) * MINIMAP_SRC_H / MINIMAP_SRC_W + PADDING;
    }

    /**
     * Where the column's side rails begin: one rail-height <em>above</em> the bottom of the top bar,
     * level with the wooden rails to either side. The bar omits its bottom rail over this stretch, so
     * without the overlap the grey verticals would start below the wood and the corner would read as
     * two disconnected pieces.
     */
    private static int railTop(Minecraft minecraft) {
        return RtsTopBarHud.currentHeight(minecraft)
                - RtsPanel.capBottomHeight(RtsPanel.DEFAULT_TEXTURE_SCALE);
    }

    /** Used by the input guards, same contract as the two bars. */
    public static boolean isPointInside(int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return false;
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int width = panelWidth(screenWidth);
        int left = screenWidth - width;
        int top = railTop(minecraft);
        int bottom = top + mapHeight(width) + civHeight(minecraft.font);
        if (mouseX >= left && mouseX < screenWidth && mouseY >= top && mouseY < bottom) {
            return true;
        }
        if (!upgradeOpen || !upgradeVisible(minecraft)) {
            return false;
        }
        int civTop = top + mapHeight(width);
        UpgradePopupBounds popup = upgradePopupBounds(minecraft, left, civTop);
        return contains(mouseX, mouseY, popup.x(), popup.y(), popup.width(), popup.height());
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            upgradeOpen = false;
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int width = panelWidth(screenWidth);
        // Hard against the right edge and hard against the bar above it: no margin anywhere, so the
        // column is continuous with the top bar instead of floating below it.
        int left = screenWidth - width;
        int top = railTop(minecraft);

        int rail = RtsPanel.capSideWidth(RtsPanel.DEFAULT_TEXTURE_SCALE);
        int innerLeft = left + rail + PADDING;
        int innerWidth = innerWidth(width);

        int mapHeight = mapHeight(width);
        int civHeight = civHeight(font);

        // The map starts at railTop, so its top edge lines up with the wooden rail to either side
        // rather than sitting a rail's height below it.
        RtsPanel.draw(graphics, left, top, width, mapHeight, RtsPanel.EDGE_SIDES);
        int mapDrawHeight = innerWidth * MINIMAP_SRC_H / MINIMAP_SRC_W;
        RtsMinimap.draw(graphics, innerLeft, top, innerWidth, mapDrawHeight);

        // The minimap is informational. Consume a map click so it cannot fall through to a camera
        // or spectator-anchor teleport; keyboard/edge panning remains the camera control.
        if (RtsMouseController.uiClickPending()) {
            int clickX = RtsMouseController.clickMouseX();
            int clickY = RtsMouseController.clickMouseY();
            if (clickX >= innerLeft && clickX < innerLeft + innerWidth
                    && clickY >= top && clickY < top + mapDrawHeight) {
                RtsMouseController.consumeUiClick();
            }
        }

        int civTop = top + mapHeight;
        RtsPanel.draw(graphics, left, civTop, width, civHeight,
                RtsPanel.EDGE_SIDES | RtsPanel.EDGE_BOTTOM);
        int clickX = RtsMouseController.uiClickPending()
                ? RtsMouseController.clickMouseX() : Integer.MIN_VALUE;
        int clickY = RtsMouseController.uiClickPending()
                ? RtsMouseController.clickMouseY() : Integer.MIN_VALUE;
        drawCivilization(graphics, font, minecraft, innerLeft, civTop + PADDING, innerWidth,
                clickX, clickY);
        if (upgradeOpen) {
            drawUpgradePopup(graphics, font, minecraft, left, civTop, clickX, clickY);
        }
    }

    private static void drawCivilization(GuiGraphicsExtractor graphics, Font font,
                                         Minecraft minecraft, int x, int y, int width,
                                         int clickX, int clickY) {
        int header = Math.max(FLAG_HEIGHT, font.lineHeight * 2);
        int flagX = x;
        int flagY = y + Math.max(0, (header - FLAG_HEIGHT) / 2);
        boolean founded = RtsCivilization.isFounded(minecraft.player);
        if (founded) {
            FlagDesign flag = RtsCivilization.flag(minecraft.player);
            FlagRenderer.draw(graphics, flag, flagX, flagY, Math.min(FLAG_WIDTH, width), FLAG_HEIGHT);
        }
        int textX = founded ? flagX + FLAG_WIDTH + ICON_TEXT_GAP : flagX;
        graphics.text(font, RtsHudState.ageNumeral(), textX, y + 1, COLOR_AGE);
        graphics.text(font, RtsHudState.ageName(), textX, y + 1 + font.lineHeight, COLOR_LABEL);

        int buttonY = y + header + UPGRADE_BUTTON_GAP;
        int age = RtsCivilization.age(minecraft.player);
        boolean maxed = age >= RtsCivilization.MAX_AGE;
        boolean ready = !maxed && RtsHudState.canAdvanceAge();
        int buttonWidth = Math.max(1, width);
        boolean hover = contains(RtsMouseController.mouseX(minecraft), RtsMouseController.mouseY(minecraft),
                x, buttonY, buttonWidth, UPGRADE_BUTTON_HEIGHT);
        // Keep the details panel reachable while the pointer travels from the opener to the
        // panel. Once it leaves both regions, the click-opened popup behaves like a compact tooltip
        // instead of remaining stranded over the playfield.
        boolean popupHover = upgradeOpen && upgradePopupHovered(minecraft);
        if (upgradeOpen && !hover && !popupHover) {
            upgradeOpen = false;
        }
        boolean pressed = contains(clickX, clickY, x, buttonY, buttonWidth, UPGRADE_BUTTON_HEIGHT);
        graphics.fill(x, buttonY, x + buttonWidth, buttonY + UPGRADE_BUTTON_HEIGHT,
                maxed ? 0xFF51483C : ready ? 0xFF765522 : 0xFF3C3027);
        graphics.outline(x, buttonY, buttonWidth, UPGRADE_BUTTON_HEIGHT,
                hover ? COLOR_UPGRADE_TEXT : COLOR_UPGRADE);
        drawFittedCenteredText(graphics, font, maxed ? "MAXED" : "UPGRADE",
                x + buttonWidth / 2, buttonY + 3, buttonWidth - 4,
                maxed ? COLOR_LABEL : COLOR_UPGRADE_TEXT);

        if (pressed) {
            RtsMouseController.consumeUiClick();
            if (!maxed) {
                upgradeOpen = !upgradeOpen;
            }
        }

        int rowY = buttonY + UPGRADE_BUTTON_HEIGHT + ROW_GAP;
        int rowHeight = statRowHeight(font);
        for (RtsHudState.Stat stat : RtsHudState.stats()) {
            int iconWidth = HudTextures.widthForHeight(rowHeight, stat.iconWidth(), stat.iconHeight());
            HudTextures.blitWhole(graphics, stat.icon(), x, rowY, iconWidth, rowHeight,
                    stat.iconWidth(), stat.iconHeight());
            // Text is centred against the taller icon rather than sharing its top edge.
            int textY = rowY + (rowHeight - font.lineHeight) / 2;
            graphics.text(font, stat.label(), x + iconWidth + ICON_TEXT_GAP, textY, COLOR_LABEL);
            // Values right-align against the panel's inner edge so the numbers form a column
            // regardless of how long the labels are.
            graphics.text(font, stat.value(), x + width - font.width(stat.value()), textY, COLOR_VALUE);
            rowY += rowHeight + ROW_GAP;
        }
    }

    /** The upgrade details live to the left of the civilization card, never over the playfield
     * column itself. Keeping this in physical GUI pixels makes the costs readable at every scale. */
    private static void drawUpgradePopup(GuiGraphicsExtractor graphics, Font font,
                                         Minecraft minecraft, int sideLeft, int civTop,
                                         int clickX, int clickY) {
        if (!upgradeVisible(minecraft)) {
            upgradeOpen = false;
            return;
        }

        UpgradePopupBounds popup = upgradePopupBounds(minecraft, sideLeft, civTop);
        int x = popup.x();
        int y = popup.y();
        int width = popup.width();
        int height = popup.height();
        int age = Math.min(RtsCivilization.MAX_AGE, RtsCivilization.age(minecraft.player));
        boolean maxed = age >= RtsCivilization.MAX_AGE;
        boolean ready = !maxed && RtsHudState.canAdvanceAge();

        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2,
                ready ? COLOR_UPGRADE : 0xFF725536);
        graphics.fill(x, y, x + width, y + height, 0xF01D150D);

        int contentX = x + UPGRADE_POPUP_PADDING;
        int contentWidth = Math.max(1, width - 2 * UPGRADE_POPUP_PADDING);
        int actionY = y + UPGRADE_POPUP_PADDING;
        boolean actionPressed = !maxed && contains(clickX, clickY, contentX, actionY,
                contentWidth, UPGRADE_BUTTON_HEIGHT);
        graphics.fill(contentX, actionY, contentX + contentWidth,
                actionY + UPGRADE_BUTTON_HEIGHT,
                maxed ? 0xFF51483C : ready ? 0xFF765522 : 0xFF453A30);
        graphics.outline(contentX, actionY, contentWidth, UPGRADE_BUTTON_HEIGHT,
                actionPressed ? COLOR_UPGRADE_TEXT : COLOR_UPGRADE);

        String title = maxed ? "CIVILIZATION MAXED"
                : "ADVANCE TO " + RtsCivilization.AGE_NAMES[age + 1].toUpperCase(java.util.Locale.ROOT);
        drawFittedCenteredText(graphics, font, title, contentX + contentWidth / 2,
                actionY + 3, contentWidth - 4, maxed ? COLOR_LABEL : COLOR_UPGRADE_TEXT);

        if (maxed) {
            graphics.text(font, "Castle Age complete", contentX,
                    actionY + UPGRADE_BUTTON_HEIGHT + 5, COLOR_LABEL);
        } else {
            int lineY = actionY + UPGRADE_BUTTON_HEIGHT + 5;
            graphics.text(font, "Next: " + RtsCivilization.AGE_NAMES[age + 1], contentX,
                    lineY, COLOR_UPGRADE_TEXT);
            lineY += font.lineHeight + UPGRADE_POPUP_LINE_GAP;
            graphics.text(font, "Conditions", contentX, lineY, COLOR_LABEL);
            lineY += font.lineHeight + UPGRADE_POPUP_LINE_GAP;
            int costRows = drawUpgradeCosts(graphics, font, contentX, lineY, contentWidth, age);
            lineY += costRows * (font.lineHeight + UPGRADE_POPUP_LINE_GAP);
            graphics.text(font, ready ? "Ready to advance" : "Red costs are still needed",
                    contentX, lineY, ready ? COLOR_UPGRADE_TEXT : COLOR_UPGRADE_SHORT);
        }

        if (contains(clickX, clickY, x, y, width, height)) {
            RtsMouseController.consumeUiClick();
            if (actionPressed && ready) {
                ClientPacketDistributor.sendToServer(new AdvanceAgePayload());
                upgradeOpen = false;
            }
        }
    }

    /** Returns the number of cost rows drawn, so the popup can size itself to the data. */
    private static int drawUpgradeCosts(GuiGraphicsExtractor graphics, Font font,
                                        int x, int y, int width, int age) {
        int[] costs = RtsCivilization.advanceCost(age);
        List<String> labels = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (Resource resource : Resource.VALUES) {
            int cost = costs[resource.ordinal()];
            if (cost <= 0) {
                continue;
            }
            String label = cost + " " + resource.label();
            int columnWidth = Math.max(1, width / 2);
            if (font.width(label) > columnWidth - 2) {
                label = cost + " " + resource.key();
            }
            if (font.width(label) > columnWidth - 2) {
                label = cost + resource.key().substring(0, 1);
            }
            labels.add(label);
            colours.add(RtsHudState.stockOf(resource) >= cost
                    ? 0xFFD8E7C6 : COLOR_UPGRADE_SHORT);
        }

        int columnWidth = Math.max(1, width / 2);
        for (int index = 0; index < labels.size(); index++) {
            int column = index % 2;
            int row = index / 2;
            graphics.text(font, labels.get(index), x + column * columnWidth,
                    y + row * (font.lineHeight + UPGRADE_POPUP_LINE_GAP), colours.get(index));
        }
        return (labels.size() + 1) / 2;
    }

    private static boolean upgradeVisible(Minecraft minecraft) {
        return minecraft.player != null && RtsMode.isActive(minecraft.player)
                && RtsCivilization.isFounded(minecraft.player);
    }

    private static boolean upgradePopupHovered(Minecraft minecraft) {
        if (!upgradeVisible(minecraft)) {
            return false;
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int width = panelWidth(screenWidth);
        int sideLeft = screenWidth - width;
        int top = railTop(minecraft);
        UpgradePopupBounds popup = upgradePopupBounds(minecraft, sideLeft,
                top + mapHeight(width));
        return contains(RtsMouseController.mouseX(minecraft), RtsMouseController.mouseY(minecraft),
                popup.x(), popup.y(), popup.width(), popup.height());
    }

    private static int upgradePopupHeight(Minecraft minecraft, int age) {
        if (age >= RtsCivilization.MAX_AGE) {
            return UPGRADE_POPUP_PADDING + UPGRADE_BUTTON_HEIGHT + 5
                    + minecraft.font.lineHeight + UPGRADE_POPUP_PADDING;
        }
        int costCount = 0;
        for (int cost : RtsCivilization.advanceCost(age)) {
            if (cost > 0) {
                costCount++;
            }
        }
        int rows = (costCount + 1) / 2;
        return UPGRADE_POPUP_PADDING + UPGRADE_BUTTON_HEIGHT + 5
                + 2 * (minecraft.font.lineHeight + UPGRADE_POPUP_LINE_GAP)
                + rows * (minecraft.font.lineHeight + UPGRADE_POPUP_LINE_GAP)
                + minecraft.font.lineHeight + UPGRADE_POPUP_PADDING;
    }

    private record UpgradePopupBounds(int x, int y, int width, int height) {
    }

    private static UpgradePopupBounds upgradePopupBounds(Minecraft minecraft, int sideLeft,
                                                          int civTop) {
        int available = Math.max(1, sideLeft - 2 * UPGRADE_BUTTON_GAP);
        int width = Math.min(UPGRADE_POPUP_MAX_WIDTH, available);
        int age = Math.min(RtsCivilization.MAX_AGE, RtsCivilization.age(minecraft.player));
        int height = upgradePopupHeight(minecraft, age);
        int x = Math.max(0, sideLeft - width - UPGRADE_BUTTON_GAP);

        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int minY = RtsTopBarHud.currentHeight(minecraft) + UPGRADE_BUTTON_GAP;
        int maxY = screenHeight - RtsBottomBarHud.currentHeight(minecraft)
                - height - UPGRADE_BUTTON_GAP;
        int desiredY = civTop + UPGRADE_BUTTON_GAP;
        int y = maxY >= minY ? Math.max(minY, Math.min(desiredY, maxY)) : minY;
        return new UpgradePopupBounds(x, y, width, height);
    }

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

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "textures/gui/bottombar/" + name + ".png");
    }
}
