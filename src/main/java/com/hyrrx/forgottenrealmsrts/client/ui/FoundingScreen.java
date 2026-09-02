package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.FlagDesign;
import com.hyrrx.forgottenrealmsrts.network.FoundCivilizationPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * "FOUND YOUR CIVILIZATION" — opened when the first Town Hall finishes placing (see
 * {@code OpenFoundingPayload}). The player names the civilization and designs a banner from
 * {@link FlagDesign}'s parameter tables, with a live preview, then confirms. Only on confirm does
 * the server mark the civilization founded, spawn the first villager and unlock the rest of the
 * build menu — so the screen cannot be dismissed with Escape.
 */
public final class FoundingScreen extends Screen {
    private static final int PANEL_W = 372;
    private static final int PANEL_H = 216;
    private static final int PANEL_BG = 0xF01B140C;
    private static final int PANEL_BORDER = 0xFFC8A24E;
    private static final int TITLE_COLOR = 0xFFE8C874;
    private static final int LABEL_COLOR = 0xFFCFC2A6;
    private static final int VALUE_COLOR = 0xFFF2E9CF;

    private int primary = FlagDesign.DEFAULT.primary();
    private int secondary = FlagDesign.DEFAULT.secondary();
    private int background = FlagDesign.DEFAULT.background();
    private int layout = FlagDesign.DEFAULT.layout();
    private int emblem = FlagDesign.DEFAULT.emblem();
    private int emblemColor = FlagDesign.DEFAULT.emblemColor();

    private EditBox nameBox;
    private Button confirm;

    private int panelX;
    private int panelY;
    private int previewX;
    private int previewY;
    private int rowsX;
    private int rowsY;

    private static final int PREVIEW_W = 132;
    private static final int PREVIEW_H = 90;
    private static final int ROW_H = 22;
    private static final int ROW_LABEL_WIDTH = 92;
    private static final int VALUE_WIDTH = 92;
    private static final int CYCLER_WIDTH = 18;
    private static final int CYCLER_GAP = 5;

    public FoundingScreen() {
        super(Component.literal("Found Your Civilization"));
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        previewX = panelX + 24;
        previewY = panelY + 66;
        // Keep the labels clear of the preview while leaving a little breathing room before the
        // right-hand arrows. The narrower columns preserve the existing panel width at GUI scale 2.
        rowsX = panelX + 160;
        rowsY = panelY + 62;

        nameBox = new EditBox(this.font, panelX + 92, panelY + 30, 256, 18, null,
                Component.literal("Civilization name"));
        nameBox.setMaxLength(28);
        nameBox.setHint(Component.literal("Name your realm…"));
        nameBox.setResponder(text -> updateConfirmState());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        // One prev/next pair per banner attribute. The value itself is drawn in extractRenderState.
        addCycler(0, () -> layout = wrap(layout - 1, FlagDesign.LAYOUTS.length),
                () -> layout = wrap(layout + 1, FlagDesign.LAYOUTS.length));
        addCycler(1, () -> primary = wrap(primary - 1, FlagDesign.PALETTE.length),
                () -> primary = wrap(primary + 1, FlagDesign.PALETTE.length));
        addCycler(2, () -> secondary = wrap(secondary - 1, FlagDesign.PALETTE.length),
                () -> secondary = wrap(secondary + 1, FlagDesign.PALETTE.length));
        addCycler(3, () -> background = wrap(background - 1, FlagDesign.PALETTE.length),
                () -> background = wrap(background + 1, FlagDesign.PALETTE.length));
        addCycler(4, () -> emblem = wrap(emblem - 1, FlagDesign.EMBLEMS.length),
                () -> emblem = wrap(emblem + 1, FlagDesign.EMBLEMS.length));
        addCycler(5, () -> emblemColor = wrap(emblemColor - 1, FlagDesign.PALETTE.length),
                () -> emblemColor = wrap(emblemColor + 1, FlagDesign.PALETTE.length));

        confirm = Button.builder(Component.literal("Found Civilization"), b -> onConfirm())
                .bounds(panelX + (PANEL_W - 160) / 2, panelY + PANEL_H - 28, 160, 20)
                .build();
        addRenderableWidget(confirm);
        updateConfirmState();
    }

    private void addCycler(int row, Runnable prev, Runnable next) {
        int y = rowsY + row * ROW_H;
        int valueX = rowsX + ROW_LABEL_WIDTH;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> prev.run())
                .bounds(valueX - CYCLER_GAP - CYCLER_WIDTH, y - 2, CYCLER_WIDTH, CYCLER_WIDTH).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> next.run())
                .bounds(valueX + VALUE_WIDTH + CYCLER_GAP, y - 2, CYCLER_WIDTH, CYCLER_WIDTH).build());
    }

    private void updateConfirmState() {
        if (confirm != null) {
            confirm.active = !nameBox.getValue().trim().isEmpty();
        }
    }

    private void onConfirm() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        FlagDesign flag = new FlagDesign(primary, secondary, background, layout, emblem, emblemColor);
        ClientPacketDistributor.sendToServer(new FoundCivilizationPayload(name, flag.sanitized()));
        this.minecraft.setScreen(null);
    }

    private static int wrap(int value, int size) {
        return ((value % size) + size) % size;
    }

    private FlagDesign current() {
        return new FlagDesign(primary, secondary, background, layout, emblem, emblemColor);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0090603);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_W + 2, panelY + PANEL_H + 2, PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PANEL_BG);

        String title = "FOUND YOUR CIVILIZATION";
        graphics.text(this.font, title, panelX + (PANEL_W - this.font.width(title)) / 2, panelY + 12,
                TITLE_COLOR);
        graphics.text(this.font, "Name", panelX + 24, panelY + 35, LABEL_COLOR);

        // Live banner preview.
        FlagRenderer.draw(graphics, current(), previewX, previewY, PREVIEW_W, PREVIEW_H);

        // Attribute rows.
        drawRow(graphics, 0, "Layout", FlagDesign.LAYOUTS[layout], -1);
        drawRow(graphics, 1, "Primary", null, FlagDesign.PALETTE[primary]);
        drawRow(graphics, 2, "Secondary", null, FlagDesign.PALETTE[secondary]);
        drawRow(graphics, 3, "Field", null, FlagDesign.PALETTE[background]);
        drawRow(graphics, 4, "Emblem", FlagDesign.EMBLEMS[emblem], -1);
        drawRow(graphics, 5, "Emblem Color", null, FlagDesign.PALETTE[emblemColor]);

        // Widgets (name box, cyclers, confirm) on top.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphicsExtractor graphics, int row, String label, String value, int swatch) {
        int y = rowsY + row * ROW_H;
        graphics.text(this.font, label, rowsX, y + 3, LABEL_COLOR);
        int valueX = rowsX + ROW_LABEL_WIDTH;
        if (value != null) {
            String fitted = fitText(value, VALUE_WIDTH);
            int vx = valueX + (VALUE_WIDTH - this.font.width(fitted)) / 2;
            graphics.text(this.font, fitted, vx, y + 3, VALUE_COLOR);
        } else if (swatch != -1) {
            int swatchX = valueX + (VALUE_WIDTH - 20) / 2;
            graphics.fill(swatchX, y, swatchX + 20, y + 16, 0xFF000000);
            graphics.fill(swatchX + 1, y + 1, swatchX + 19, y + 15, swatch);
        }
    }

    /** Fits every row value inside its reserved column instead of letting widgets paint over it. */
    private String fitText(String value, int width) {
        if (this.font.width(value) <= width) {
            return value;
        }
        String ellipsis = "…";
        String fitted = value;
        while (!fitted.isEmpty() && this.font.width(fitted + ellipsis) > width) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        return fitted.isEmpty() ? ellipsis : fitted + ellipsis;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
