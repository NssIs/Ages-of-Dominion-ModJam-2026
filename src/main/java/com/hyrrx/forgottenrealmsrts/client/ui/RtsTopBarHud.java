package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.input.RtsMouseController;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Full-width Waybar-style status bar anchored to the top of the screen. Every number is now live:
 * the six stockpiles, Population and Happiness all read from synced state (see {@link RtsHudState}),
 * and the right-hand buttons — Pause, Save, Settings — respond to clicks. See MODMAP.md.
 *
     * <p>The bar's natural (unscaled) content width can easily exceed the GUI-scaled screen at higher
     * GUI Scale settings — six resources, two stats, a day box and three buttons add up fast. The
     * day/control cluster belongs to the top bar, not to the minimap column below it, so it is fitted
     * against the full screen width and anchored at the true right edge. Rather than pick fixed sizes
     * that only fit one GUI Scale, every frame measures how wide the content wants to be and shrinks
     * it (via the pose stack, same mechanism vanilla widgets use) to whatever actually fits, so it
     * never overlaps itself.
 *
 * <p>That shrink is uniform, so the <em>band</em> has to shrink with it: the background is drawn at
 * {@code BAR_HEIGHT * scale}, not at {@code BAR_HEIGHT}. Drawing it at full height while the
 * contents render at half size leaves the bottom half of the bar as empty texture and magnifies
 * each background tile relative to the text sitting on it.
 */
public final class RtsTopBarHud {
    /** Height of the bar at scale 1.0. The height actually drawn is this times the content scale —
     *  see {@link #scaledBarHeight}. Sized to the contents (two 9px text rows, an 18px icon, a 30px
     *  button) rather than to the background art: bar_background.png is 503x131, and picking a bar
     *  height from that ratio is what made the stone panel read as blown up, because the tile width
     *  is derived from the height and a tall bar means few, large, upscaled tiles. */
    private static final int BAR_HEIGHT = 44;
    /** Native pixel size of day_box_background.png. The box is sized from its own aspect ratio
     *  rather than a hand-picked width, which previously squashed it horizontally. */
    private static final int DAY_BOX_SOURCE_WIDTH = 371;
    private static final int DAY_BOX_SOURCE_HEIGHT = 110;
    /** Inset from both screen edges. Generous on purpose — at 6 the first resource icon sat
     *  almost against the corner of the screen, which read as the bar being shoved left rather than
     *  filling the width. Both ends honour it, so raising it pulls the content in symmetrically. */
    private static final int MARGIN = 24;
    private static final int RESOURCE_ICON_SIZE = 15;
    private static final int STAT_ICON_SIZE = 15;
    private static final int DAY_ICON_SIZE = 15;
    /** Nudge applied to the resource/stat icons only — not the text, not the buttons, not the day
     *  box. The icon art has its visual mass low in the frame, so centring it geometrically against
     *  the two text rows leaves it sitting a touch below them. */
    private static final int ICON_Y_NUDGE = -2;
    /** Lifts everything in the bar — text, icons, buttons, day box — off the geometric centre of
     *  the band. The background art's bottom rail is heavier than its top one, so true centring
     *  reads as sitting low. Applied on top of {@link #ICON_Y_NUDGE} for the icons. */
    private static final int CONTENT_Y_NUDGE = -2;
    /** The boxed controls share the bar's actual centre line. Lifting them into the top rail made
     *  the day card's two text rows sit on the decorative frame at smaller screenshot crops. */
    private static final int PANEL_Y_NUDGE = 0;
    /** Lift the day card slightly without moving the pause/save/settings controls. */
    private static final int DAY_BOX_Y_NUDGE = -1;
    private static final int BUTTON_SIZE = 30;
    private static final int ICON_TEXT_GAP = 4;
    private static final int GROUP_GAP = 10;
    /** Wider break between the resources and stats clusters, and between Happiness and Idle, so
     *  those read as visually distinct sections instead of one unbroken run. */
    private static final int SECTION_GAP = 16;
    private static final int BUTTON_GAP = 6;
    private static final int DAY_BOX_HEIGHT = 30;
    private static final int DAY_BOX_WIDTH =
            DAY_BOX_HEIGHT * DAY_BOX_SOURCE_WIDTH / DAY_BOX_SOURCE_HEIGHT;
    private static final int DAY_BOX_PADDING = 7;
    /** The clock is supporting information beneath the day number, not a second title. */
    private static final float DAY_TIME_TEXT_SCALE = 0.64F;
    /** Text-safe rows inside the parchment portion of the day-card art. */
    private static final int DAY_LABEL_Y = 7;
    private static final int DAY_TIME_Y = 16;

    private static final int COLOR_LABEL = 0xFFB8AC85;
    private static final int COLOR_TEXT = 0xFFF4E9C8;
    private static final int TINT_NONE = 0xFFFFFFFF;
    private static final int TINT_HOVER = 0xFFFFE8B4;
    private static final int TINT_SAVED = 0xFFFFD36A;
    private static final long SAVE_FEEDBACK_MILLIS = 1200L;
    private static final Identifier DAY_BOX_BACKGROUND = texture("day_box_background");

    /**
     * A stockpile readout.
     *
     * <p>The amount is a <strong>supplier</strong>, not a string: these were hardcoded
     * ("1,250", "980", ...) and are now read from the synced {@link RtsEconomy} attachments every
     * frame. Keeping the accessor called {@code amount()} means every measuring and drawing site
     * below is unchanged.
     */
    private record Resource(String label, Identifier icon, Supplier<String> source) {
        String amount() {
            return source.get();
        }
    }

    private static final Resource[] RESOURCES = {
            resource(com.hyrrx.forgottenrealmsrts.Resource.WOOD),
            resource(com.hyrrx.forgottenrealmsrts.Resource.STONE),
            resource(com.hyrrx.forgottenrealmsrts.Resource.IRON),
            resource(com.hyrrx.forgottenrealmsrts.Resource.GOLD),
            resource(com.hyrrx.forgottenrealmsrts.Resource.FOOD),
            resource(com.hyrrx.forgottenrealmsrts.Resource.COAL),
    };

    private static Resource resource(com.hyrrx.forgottenrealmsrts.Resource kind) {
        return new Resource(kind.label(), texture("icon_" + kind.key()),
                () -> RtsHudState.resourceAmount(kind));
    }

    private record Stat(String label, Identifier icon, Supplier<String> source) {
        String value() {
            return source.get();
        }
    }

    // Population and Happiness are both real now (see RtsHudState). Idle/Working were removed with
    // the worker-task states they described — the side panel already carries Workers and Military,
    // so an invented "0" here would be exactly the decorative placeholder this bar avoids.
    private static final Stat[] STATS = {
            new Stat("Population", texture("icon_population"), RtsHudState::population),
            new Stat("Happiness", texture("icon_happiness"), RtsHudState::happiness),
    };

    /**
     * Floor on the width of a value column.
     *
     * <p>Column widths come from {@code font.width(amount)}, and the whole bar scales itself to its
     * measured content — so without a floor the entire bar visibly re-scales the moment a stockpile
     * ticks from 999 to 1,000. Reserving room for a four-figure number up front costs a few pixels
     * and stops the layout breathing.
     */
    private static final int MIN_VALUE_WIDTH = 26;

    /** A button and the tooltip it explains. Nothing is wired up yet, and the tooltips say so
     *  rather than implying a working feature. */
    private record BarButton(Identifier icon, String title, String description) {
    }

    // Only functional buttons ship: a dead button is worse than no button. Diplomacy, Objectives
    // and Technology were removed with their features unbuilt.
    private static final BarButton[] BUTTONS = {
            new BarButton(texture("button_pause"), "Pause",
                    "Open the pause menu; the world stops while it is up."),
            new BarButton(texture("button_save"), "Save Realm",
                    "Write the current realm — buildings, population and stockpiles — to disk."),
            new BarButton(texture("button_settings"), "Settings",
                    "Audio, video, camera speed and control bindings."),
    };

    /** Index of the button under the cursor this frame, or -1. Resolved during drawing (inside the
     *  scale transform, where the hit boxes live) and consumed after it, because a tooltip has to be
     *  positioned in real screen coordinates, not the bar's shrunken ones. */
    private static int hoveredButton = -1;
    private static long saveFeedbackUntil;
    private static long saveFeedbackStartedAt;

    private RtsTopBarHud() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsTopBarHud::onRenderGui);
    }

    /** Used by RtsMouseController so a click anywhere on the bar never starts a camera drag. Must
     *  agree with what {@link #onRenderGui} actually draws, hence the same scale calculation —
     *  otherwise the bar swallows clicks on empty sky below it. */
    public static boolean isPointInside(int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return false;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        float scale = contentScale(minecraft.font, width);
        if (mouseX >= 0 && mouseX < width
                && mouseY >= 0 && mouseY < scaledBarHeight(scale)) {
            return true;
        }
        return false;
    }

    /** The bar's drawn height right now. Anything anchored below the top bar must use this rather
     *  than BAR_HEIGHT, which is only the unscaled authoring size. */
    public static int currentHeight(Minecraft minecraft) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        return scaledBarHeight(contentScale(minecraft.font, width));
    }

    /** Width the content wants before any shrinking. The top bar spans the whole screen. */
    private static int naturalContentWidth(Font font) {
        return 2 * MARGIN + measureResourcesWidth(font) + SECTION_GAP
                + measureStatsWidth(font) + GROUP_GAP + DAY_BOX_WIDTH + GROUP_GAP + measureButtonsWidth();
    }

    /** How much the content has to shrink to fit the full top bar. */
    private static float contentScale(Font font, int width) {
        return HudScale.fit(naturalContentWidth(font), Math.max(1, width));
    }

    /** The bar's drawn height. Every vertical measurement inside the bar is expressed against
     *  BAR_HEIGHT and then multiplied by the same scale, so the two stay in step. */
    private static int scaledBarHeight(float scale) {
        return HudScale.bandHeight(BAR_HEIGHT, scale);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int mouseX = RtsMouseController.mouseX(minecraft);
        int mouseY = RtsMouseController.mouseY(minecraft);
        float scale = contentScale(font, width);
        drawBackground(graphics, width, scaledBarHeight(scale));

        // The right-side minimap starts below this bar. Keeping this virtual width at the full
        // screen width puts the day card and pause/save/settings controls above that column instead
        // of marooning them at the left edge of the playable map.
        int virtualWidth = Math.round(width / scale);
        int virtualMouseX = Math.round(mouseX / scale);
        int virtualMouseY = Math.round(mouseY / scale);

        hoveredButton = -1;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);

        int x = MARGIN;
        x = drawResources(graphics, font, x);
        x += SECTION_GAP;
        x = drawStats(graphics, font, x);

        // Keep the day readout and controls as one right-anchored cluster. The old layout placed the
        // day box immediately after the stats and left the rest of a wide top bar looking empty;
        // controls are much easier to scan when they live at the edge they belong to.
        int dayX = dayBoxX(virtualWidth, x);
        drawDayBox(graphics, font, dayX, currentDay(minecraft), daytimeLabel(minecraft));
        int dayY = dayBoxY();
        boolean hoveringDayBox = virtualMouseX >= dayX && virtualMouseX < dayX + DAY_BOX_WIDTH
                && virtualMouseY >= dayY && virtualMouseY < dayY + DAY_BOX_HEIGHT;

        drawButtons(graphics, virtualWidth, virtualMouseX, virtualMouseY);

        graphics.pose().popMatrix();

        // The actionbar is hidden by the RTS presentation, so saving gets a short, self-contained
        // confirmation card below the top bar as well as a flash on the button itself.
        if (saveFeedbackActive()) {
            drawSaveFeedback(graphics, font, width, scaledBarHeight(scale));
        }

        // After popMatrix on purpose: the tooltip renders at real screen coordinates, so emitting it
        // inside the bar's scale transform would both shrink the tooltip and misplace it.
        if (hoveredButton >= 0) {
            BarButton button = BUTTONS[hoveredButton];
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal(button.title()).withStyle(ChatFormatting.GOLD),
                    Component.literal(button.description()).withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
            // Only consume the click when it is actually over a button, so the bottom bar keeps its
            // own clicks regardless of which HUD listener runs first.
            if (RtsMouseController.consumeUiClickPressed()) {
                activateButton(hoveredButton, minecraft);
            }
        }
        if (hoveringDayBox) {
            graphics.setComponentTooltipForNextFrame(font, moonTooltip(), mouseX, mouseY);
        }
    }

    private static void activateButton(int index, Minecraft minecraft) {
        switch (index) {
            case 0 -> minecraft.pauseGame(false);
            case 1 -> {
                net.minecraft.client.server.IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> server.saveEverything(false, true, true));
                    saveFeedbackStartedAt = Util.getMillis();
                    saveFeedbackUntil = saveFeedbackStartedAt + SAVE_FEEDBACK_MILLIS;
                }
            }
            case 2 -> minecraft.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(
                    minecraft.screen, minecraft.options, true));
            default -> {
            }
        }
    }

    private static int dayBoxX(int virtualWidth, int contentEnd) {
        int buttonWidth = (BUTTONS.length * BUTTON_SIZE) + ((BUTTONS.length - 1) * BUTTON_GAP);
        int controlStart = virtualWidth - MARGIN - buttonWidth - GROUP_GAP - DAY_BOX_WIDTH;
        return Math.max(contentEnd + GROUP_GAP, controlStart);
    }

    /** Built from the three panel slices rather than one squeezed copy of bar_background.png.
     *  Scaling a 503x131 image into a 44px-tall band squashes its rails; {@link RtsPanel} keeps them
     *  at their real proportions and repeats the bare stone to fill the rest. */
    private static void drawBackground(GuiGraphicsExtractor graphics, int width, int barHeight) {
        // The stretch of bar directly above the side column gets no bottom rail, so the column reads
        // as hanging off the bar instead of having a wooden strip wedged between them. Panel tiles
        // are phase-locked to the screen origin, so the two segments share one continuous stone
        // pattern and the split is invisible.
        int column = RtsSidePanelHud.columnWidth(width);
        int mainWidth = Math.max(0, width - column);
        RtsPanel.draw(graphics, 0, 0, mainWidth, barHeight, RtsPanel.EDGE_TOP | RtsPanel.EDGE_BOTTOM);
        RtsPanel.draw(graphics, mainWidth, 0, column, barHeight, RtsPanel.EDGE_TOP);
    }

    private static int measureResourcesWidth(Font font) {
        int total = 0;
        for (Resource resource : RESOURCES) {
            total += RESOURCE_ICON_SIZE + ICON_TEXT_GAP + resourceColumnWidth(font, resource) + GROUP_GAP / 2;
        }
        return total;
    }

    private static int resourceColumnWidth(Font font, Resource resource) {
        return Math.max(Math.max(font.width(resource.label()), font.width(resource.amount())),
                MIN_VALUE_WIDTH);
    }

    /** Index of the first stat after the Population/Happiness pair — where SECTION_GAP applies
     *  instead of the normal GROUP_GAP, splitting the stats cluster into two visual sections. */
    private static final int STATS_SECTION_SPLIT = 2;

    private static int measureStatsWidth(Font font) {
        int total = 0;
        for (int i = 0; i < STATS.length; i++) {
            Stat stat = STATS[i];
            total += STAT_ICON_SIZE + ICON_TEXT_GAP + statColumnWidth(font, stat)
                    + (i == STATS_SECTION_SPLIT - 1 ? SECTION_GAP : GROUP_GAP / 2);
        }
        return total;
    }

    private static int statColumnWidth(Font font, Stat stat) {
        return Math.max(Math.max(font.width(stat.label()), font.width(stat.value())),
                MIN_VALUE_WIDTH);
    }

    private static int measureButtonsWidth() {
        return BUTTONS.length * BUTTON_SIZE + (BUTTONS.length - 1) * BUTTON_GAP;
    }

    private static int drawResources(GuiGraphicsExtractor graphics, Font font, int startX) {
        int x = startX;
        int textRowsHeight = font.lineHeight * 2;
        int labelY = (BAR_HEIGHT - textRowsHeight) / 2 + CONTENT_Y_NUDGE;
        int valueY = labelY + font.lineHeight;
        for (Resource resource : RESOURCES) {
            int iconY = (BAR_HEIGHT - RESOURCE_ICON_SIZE) / 2 + ICON_Y_NUDGE + CONTENT_Y_NUDGE;
            blitIcon(graphics, resource.icon(), x, iconY, RESOURCE_ICON_SIZE);
            int textX = x + RESOURCE_ICON_SIZE + ICON_TEXT_GAP;

            graphics.text(font, resource.label(), textX, labelY, COLOR_LABEL);
            graphics.text(font, resource.amount(), textX, valueY, COLOR_TEXT);

            x = textX + resourceColumnWidth(font, resource) + GROUP_GAP / 2;
        }
        return x;
    }

    private static int drawStats(GuiGraphicsExtractor graphics, Font font, int startX) {
        int x = startX;
        int textRowsHeight = font.lineHeight * 2;
        int labelY = (BAR_HEIGHT - textRowsHeight) / 2 + CONTENT_Y_NUDGE;
        int valueY = labelY + font.lineHeight;
        for (int i = 0; i < STATS.length; i++) {
            Stat stat = STATS[i];
            int iconY = (BAR_HEIGHT - STAT_ICON_SIZE) / 2 + ICON_Y_NUDGE + CONTENT_Y_NUDGE;
            blitIcon(graphics, stat.icon(), x, iconY, STAT_ICON_SIZE);
            int textX = x + STAT_ICON_SIZE + ICON_TEXT_GAP;

            graphics.text(font, stat.label(), textX, labelY, COLOR_LABEL);
            graphics.text(font, stat.value(), textX, valueY, COLOR_TEXT);

            x = textX + statColumnWidth(font, stat) + (i == STATS_SECTION_SPLIT - 1 ? SECTION_GAP : GROUP_GAP / 2);
        }
        return x;
    }

    /**
     * The in-game day, counting from 1.
     *
     * <p>{@code Level#getDayTime()} does not exist in 26.1 — time moved to a world-clock system, and
     * this is {@code getOverworldClockTime()} so the counter still reads as the overworld day even
     * if the player is somewhere else.
     */
    private static long currentDay(Minecraft minecraft) {
        if (minecraft.level == null) {
            return 1L;
        }
        return minecraft.level.getOverworldClockTime() / 24000L + 1L;
    }

    private static void drawDayBox(GuiGraphicsExtractor graphics, Font font, int x, long day,
                                   String daytime) {
        int y = dayBoxY();
        HudTextures.blitWhole(graphics, DAY_BOX_BACKGROUND, x, y, DAY_BOX_WIDTH, DAY_BOX_HEIGHT,
                DAY_BOX_SOURCE_WIDTH, DAY_BOX_SOURCE_HEIGHT);

        int iconX = x + DAY_BOX_PADDING;
        int iconY = y + (DAY_BOX_HEIGHT - DAY_ICON_SIZE) / 2;
        blitIcon(graphics, texture("icon_day"), iconX, iconY, DAY_ICON_SIZE);

        String label = "Day " + day;
        int textX = iconX + DAY_ICON_SIZE + ICON_TEXT_GAP;
        graphics.text(font, label, textX, y + DAY_LABEL_Y, COLOR_TEXT);
        drawDaytime(graphics, font, daytime, textX, y + DAY_TIME_Y,
                x + DAY_BOX_WIDTH - DAY_BOX_PADDING - textX);
    }

    /**
     * Keeps the live clock and its DAYTIME/NIGHT marker inside the parchment panel. A temporary
     * event fast-forward changes only the value; it must never change the panel's geometry or make
     * the second line look like a duplicate title.
     */
    private static void drawDaytime(GuiGraphicsExtractor graphics, Font font, String daytime,
                                    int x, int y, int availableWidth) {
        int textWidth = Math.max(1, font.width(daytime));
        float scale = Math.min(DAY_TIME_TEXT_SCALE,
                Math.max(0.4F, availableWidth / (float) textWidth));
        graphics.pose().pushMatrix();
        // Keep the clock aligned with the left edge of the day-number column. Centering it made the
        // live time look detached from the heading and left the requested left corner unused.
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, daytime, 0, 0, COLOR_LABEL);
        graphics.pose().popMatrix();
    }

    /** Minecraft's clock starts at 06:00; expose it as a compact 24-hour RTS clock. */
    private static String daytimeLabel(Minecraft minecraft) {
        if (minecraft.level == null) {
            return "06:00 DAYTIME";
        }
        long phase = Math.floorMod(minecraft.level.getOverworldClockTime(), 24000L);
        long minutes = Math.floorMod((phase + 6000L) * 1440L / 24000L, 1440L);
        long hour = minutes / 60L;
        long minute = minutes % 60L;
        String light = phase >= 12000L ? "NIGHT" : "DAYTIME";
        return String.format(Locale.ROOT, "%02d:%02d %s", hour, minute, light);
    }

    private static int dayBoxY() {
        return (BAR_HEIGHT - DAY_BOX_HEIGHT) / 2 + CONTENT_Y_NUDGE + PANEL_Y_NUDGE + DAY_BOX_Y_NUDGE;
    }

    /** The day panel is also the discoverable in-game reference for operator moon events. */
    private static List<Component> moonTooltip() {
        return List.of(
                Component.literal("Available moon events").withStyle(ChatFormatting.GOLD),
                Component.literal("golden-moon — rich resources").withStyle(ChatFormatting.GRAY),
                Component.literal("blood-moon — heavier invasion").withStyle(ChatFormatting.GRAY),
                Component.literal("blue-moon — slumber, no invasion").withStyle(ChatFormatting.GRAY),
                Component.literal("/game event <moon> start|end").withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    private static void drawButtons(GuiGraphicsExtractor graphics, int virtualWidth, int mouseX, int mouseY) {
        int x = virtualWidth - MARGIN;
        int y = (BAR_HEIGHT - BUTTON_SIZE) / 2 + CONTENT_Y_NUDGE + PANEL_Y_NUDGE;
        for (int i = BUTTONS.length - 1; i >= 0; i--) {
            x -= BUTTON_SIZE;
            boolean hover = mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
            if (hover) {
                hoveredButton = i;
            }
            boolean saveFlash = i == 1 && saveFeedbackActive();
            if (saveFlash) {
                float pulse = 0.5F + 0.5F * (float) Math.sin((Util.getMillis() - saveFeedbackStartedAt) * 0.025D);
                int alpha = 0x42 + Math.round(pulse * 0x38);
                graphics.fill(x - 3, y - 3, x + BUTTON_SIZE + 3, y + BUTTON_SIZE + 3,
                        (alpha << 24) | 0xD99B2F);
            }
            HudTextures.blitWhole(graphics, BUTTONS[i].icon(), x, y, BUTTON_SIZE, BUTTON_SIZE,
                    BUTTON_SIZE, BUTTON_SIZE,
                    saveFlash ? TINT_SAVED : (hover ? TINT_HOVER : TINT_NONE));
            if (saveFlash) {
                graphics.text(Minecraft.getInstance().font, "✓", x + BUTTON_SIZE - 10, y + 1,
                        TINT_SAVED);
            }
            x -= BUTTON_GAP;
        }
    }

    private static boolean saveFeedbackActive() {
        return Util.getMillis() < saveFeedbackUntil;
    }

    /** Adds a small confirmation outside the actionbar, which is intentionally not part of RTS HUD. */
    private static void drawSaveFeedback(GuiGraphicsExtractor graphics, Font font, int width,
                                         int barHeight) {
        String text = "REALM SAVED";
        int textWidth = font.width(text);
        int cardWidth = textWidth + 14;
        int mainWidth = width - RtsSidePanelHud.columnWidth(width);
        int x = Math.max(6, mainWidth - cardWidth - 8);
        int y = barHeight + 5;
        graphics.fill(x - 1, y - 1, x + cardWidth + 1, y + font.lineHeight + 5, 0xFFD8A847);
        graphics.fill(x, y, x + cardWidth, y + font.lineHeight + 4, 0xE0251B10);
        graphics.text(font, text, x + 7, y + 2, TINT_SAVED);
    }

    private static void blitIcon(GuiGraphicsExtractor graphics, Identifier icon, int x, int y, int size) {
        HudTextures.blitWhole(graphics, icon, x, y, size, size, size, size);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "textures/gui/topbar/" + name + ".png");
    }
}
