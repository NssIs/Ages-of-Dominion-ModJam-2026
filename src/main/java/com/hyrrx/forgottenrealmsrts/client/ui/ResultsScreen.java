package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.Resource;
import com.hyrrx.forgottenrealmsrts.RtsBattle;
import com.hyrrx.forgottenrealmsrts.RtsCivilization;
import com.hyrrx.forgottenrealmsrts.RtsEconomy;
import com.hyrrx.forgottenrealmsrts.client.RtsSpectateClientState;
import com.hyrrx.forgottenrealmsrts.network.EnterSpectatePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The campaign's closing moment: <em>YOUR CIVILIZATION ENDURES</em> or <em>… HAS FALLEN</em>, with
 * the realm's name, banner and a scoreboard. Opened by {@link RtsOutcomeWatcher} the moment the
 * synced {@link RtsBattle} outcome leaves "ongoing".
 */
public final class ResultsScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 240;
    private static final int PANEL_BG = 0xF01B140C;

    private final boolean victory;

    public ResultsScreen(boolean victory) {
        super(Component.literal(victory ? "Your Civilization Endures" : "Your Civilization Has Fallen"));
        this.victory = victory;
    }

    @Override
    protected void init() {
        int py = (this.height - PANEL_H) / 2;
        if (victory) {
            addRenderableWidget(Button.builder(Component.literal("Continue"), b -> this.minecraft.setScreen(null))
                    .bounds((this.width - 140) / 2, py + PANEL_H - 34, 140, 20)
                    .build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Spectate the Town"), b -> spectateTown())
                    .bounds((this.width - PANEL_W) / 2 + 10, py + PANEL_H - 36, 150, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Found a New Town"),
                            b -> this.minecraft.setScreen(new NewTownWarningScreen()))
                    .bounds((this.width - PANEL_W) / 2 + 180, py + PANEL_H - 36, 150, 20)
                    .build());
        }
    }

    private void spectateTown() {
        RtsSpectateClientState.enter();
        ClientPacketDistributor.sendToServer(new EnterSpectatePayload());
        this.minecraft.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int accent = victory ? 0xFFE8C874 : 0xFFB84A3A;

        graphics.fill(0, 0, this.width, this.height, 0xD0090603);
        graphics.fill(px - 2, py - 2, px + PANEL_W + 2, py + PANEL_H + 2, accent);
        graphics.fill(px, py, px + PANEL_W, py + PANEL_H, PANEL_BG);

        String title = victory ? "YOUR CIVILIZATION ENDURES" : "YOUR CIVILIZATION HAS FALLEN";
        graphics.text(this.font, title, px + (PANEL_W - this.font.width(title)) / 2, py + 14, accent);

        Player player = this.minecraft.player;
        String name = player == null ? "the Realm" : "The Realm of " + RtsCivilization.name(player);
        graphics.text(this.font, name, px + (PANEL_W - this.font.width(name)) / 2, py + 30, 0xFFCFC2A6);

        if (player != null) {
            FlagRenderer.draw(graphics, RtsCivilization.flag(player), px + 24, py + 54, 96, 66);

            long day = this.minecraft.level == null ? 0
                    : this.minecraft.level.getOverworldClockTime() / 24000L + 1L;
            int economy = stock(player, Resource.WOOD) + stock(player, Resource.STONE)
                    + stock(player, Resource.FOOD) + 2 * stock(player, Resource.GOLD)
                    + 2 * stock(player, Resource.IRON) + 2 * stock(player, Resource.COAL);
            int rowX = px + 140;
            int rowY = py + 56;
            row(graphics, rowX, rowY, "Age", RtsCivilization.ageName(player));
            row(graphics, rowX, rowY + 16, "Days survived", Long.toString(day));
            row(graphics, rowX, rowY + 32, "Town Hall", RtsBattle.integrity(player) + " / "
                    + RtsBattle.maxIntegrity(player));
            row(graphics, rowX, rowY + 48, "Economy score", Integer.toString(economy));
            row(graphics, rowX, rowY + 64, "Workers", Integer.toString(RtsEconomy.population(player)));
            row(graphics, rowX, rowY + 80, "Guardians", Integer.toString(RtsEconomy.military(player)));
            row(graphics, rowX, rowY + 96, "Relics recovered",
                    Integer.toString(RtsCivilization.relics(player)));
            if (!victory) {
                String prompt = "The old town remains to spectate until you choose a new start.";
                List<FormattedCharSequence> lines = this.font.split(Component.literal(prompt), PANEL_W - 32);
                int promptY = py + 170;
                for (FormattedCharSequence line : lines) {
                    graphics.text(this.font, line, px + 16, promptY, 0xFFCFC2A6);
                    promptY += this.font.lineHeight + 2;
                }
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void row(GuiGraphicsExtractor graphics, int x, int y, String label, String value) {
        graphics.text(this.font, label, x, y, 0xFFB9AC8E);
        graphics.text(this.font, value, x + 170 - this.font.width(value), y, 0xFFF2E9CF);
    }

    private static int stock(Player player, Resource resource) {
        return RtsEconomy.stock(player, resource);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
