package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.network.FoundNewTownPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Billboard confirmation for the irreversible replacement-town cleanup. */
public final class NewTownWarningScreen extends Screen {
    private static final int PANEL_W = 390;
    private static final int PANEL_H = 176;
    private static final int PANEL_BG = 0xF01B140C;
    private static final int PANEL_BORDER = 0xFFC8A24E;
    private static final int TITLE_COLOR = 0xFFE8C874;
    private static final int BODY_COLOR = 0xFFF2E9CF;
    private static final int MUTED_COLOR = 0xFFCFC2A6;

    private int panelX;
    private int panelY;
    private Button confirm;
    private boolean sent;

    public NewTownWarningScreen() {
        super(Component.literal("Found a New Town"));
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        confirm = Button.builder(Component.literal("Destroy Old Realm"), b -> confirmReset())
                .bounds(panelX + 28, panelY + PANEL_H - 30, 160, 20)
                .build();
        addRenderableWidget(confirm);
        addRenderableWidget(Button.builder(Component.literal("Cancel"),
                        b -> this.minecraft.setScreen(new ResultsScreen(false)))
                .bounds(panelX + PANEL_W - 188, panelY + PANEL_H - 30, 160, 20)
                .build());
    }

    private void confirmReset() {
        if (sent) {
            return;
        }
        sent = true;
        confirm.active = false;
        ClientPacketDistributor.sendToServer(new FoundNewTownPayload());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xD0090603);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_W + 2, panelY + PANEL_H + 2,
                PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PANEL_BG);

        String title = "FOUND A NEW TOWN?";
        graphics.text(this.font, title, panelX + (PANEL_W - this.font.width(title)) / 2,
                panelY + 14, TITLE_COLOR);
        List<FormattedCharSequence> lines = this.font.split(Component.literal(
                "Your old buildings, soldiers, and workers will be destroyed. This cannot be undone."),
                PANEL_W - 42);
        int y = panelY + 48;
        for (FormattedCharSequence line : lines) {
            graphics.text(this.font, line, panelX + 21, y, BODY_COLOR);
            y += this.font.lineHeight + 3;
        }
        String detail = sent ? "Clearing the fallen realm…" : "Choose Cancel to keep spectating the ruins.";
        graphics.text(this.font, detail, panelX + (PANEL_W - this.font.width(detail)) / 2,
                panelY + 105, MUTED_COLOR);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
