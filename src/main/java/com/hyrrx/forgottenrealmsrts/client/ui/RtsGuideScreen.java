package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.network.GuideCompletedPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A short, mandatory first-login field guide. It deliberately behaves like a book rather than a
 * dismissible toast: pages must be visited in order before the close button becomes active, and the
 * server records completion only after that final button is used.
 */
public final class RtsGuideScreen extends Screen {
    private static final int BOOK_W = 560;
    private static final int BOOK_H = 336;
    private static final int COVER = 0xFF3B2112;
    private static final int COVER_EDGE = 0xFFE0AD55;
    private static final int PAGE = 0xFF4A3323;
    private static final int PAGE_SHADOW = 0xFF24160D;
    private static final int INK = 0xFFF8F2E4;
    private static final int MUTED_INK = 0xFFE5D7BE;
    private static final int GOLD_INK = 0xFF8A5A1F;
    private static final int OVERLAY = 0xD0100C07;
    private static final int BUTTON_Y_GAP = 30;

    private static final Page[] PAGES = {
            new Page("HAIL, COMMANDER",
                    "Guard the Town Hall, gather stores, and survive the night hosts. The upper bar "
                            + "shows the day and treasury; the right panel shows thy realm; the lower "
                            + "tray holds buildings and unit orders.",
                    "Left-click an allied unit to inspect it. Drag a box over allied units to command "
                            + "a host."),
            new Page("FOUND THY REALM",
                    "Place one Town Hall from the lower tray. Name thy civilization in the founding "
                            + "book. After founding, place one Coal Mine of thy choosing; it is the "
                            + "only next building and its first placement is free. A new realm begins "
                            + "with one worker, one swordsman, and one archer.",
                    "Only one Town Hall may stand. If it falls, spectate the ruins or found anew; a new "
                            + "realm destroys the old buildings, troops, and stores. Existing tracked "
                            + "towns keep their progress when this rule is introduced."),
            new Page("LABOUR & BUILDING",
                    "Choose a worker, then click a tree. He chops logs, carries wood to the Town Hall, "
                            + "and returns. Choose a worker, then click a mine to send him inside to "
                            + "gather coal or gold. Place a building to set its foundation, then select "
                            + "villagers and click the unfinished site to assign them as builders. More "
                            + "builders assemble it faster; the progress readout shows the blocks placed. "
                            + "The Town Hall and first free Coal Mine assemble quickly, with nearby block "
                            + "placement sounds marking the work.",
                    "The small framed saved flag sits beside the age on the right realm card. The "
                            + "selected-building card always shows MOVE, UPGRADE, and DELETE. UPGRADE "
                            + "opens its price popup first; press it again to place the upgrade. The Town Hall's "
                            + "DELETE control is protected; other buildings change it to CONFIRM and need a "
                            + "second press. The UPGRADE control on the realm card shows the next age, its "
                            + "price, and what is still lacking. Dark to Feudal costs 150 Wood, 120 Food, and "
                            + "20 Gold; Feudal to Castle costs 250 Stone, 240 Food, 80 Iron, and 150 Gold. "
                            + "Mine levels 1, 2, and 3 house 2, 4, and 6 workers. "
                            + "A foundation advances slowly on its own, then each builder standing at the site "
                            + "adds work. "
                            + "The first Coal Mine is free; later Coal Mines cost 35 Wood + 20 Stone. "
                            + "Building upgrades cost 3× their base price at level 2 and 5× at level 3. Town Hall "
                            + "II costs 250 Wood, 200 Stone, 100 Iron, 75 Gold, 150 Food, 50 Coal; "
                            + "Town Hall III costs 500 Wood, 400 Stone, 250 Iron, 200 Gold, 300 Food, 150 Coal."),
            new Page("THE HOURS",
                    "Day is for gathering, building, and repair. The first night is quiet. From later "
                            + "nights onward, enemy hosts arrive and grow. Time keeps the ordinary "
                            + "Minecraft pace unless a moon command hastens it.",
                    "The seventh night bears the first special moon. Victory is checked after ten complete "
                            + "days, shown as Day 11 because the hidden clock begins at Day 0."),
            new Page("THE MOONS",
                    "Every seventh night bears a sign: Golden on Day 7, Blue (Slumber) on Day 14, and "
                            + "Blood on Day 21. Golden doubles completed-building income; Blue brings no "
                            + "invasion and halves it; Blood triples the night host.",
                    "Use /game event golden-moon start or end; replace golden-moon with blood-moon or "
                            + "blue-moon. Start hastens night; end works only in that moon's night. "
                            + "Daylight lightning harms RTS foes only."),
            new Page("COMMAND & SURVIVE",
                    "Left-click a unit; drag-select allied units. Click ground once to march. Click a "
                            + "tree with workers to gather, or a mine to assign them there. A mixed host "
                            + "marches together on ground; at a mine, workers enter while soldiers move "
                            + "to its entrance.",
                    "Click an enemy to deal 0.5 damage; never attack allied units. F3 shows XYZ. "
                            + "Spectate freezes a fallen realm; Found a New Town clears it and returns "
                            + "the clock to the first sunrise."),
            new Page("MUSIC & ATTRIBUTION",
                    "Ages of Dominion was made by Hyrrx for the CurseForge ModJam 2026. The "
                            + "campaign score is by playlightgames, from the Medieval RPG music pack, "
                            + "and keeps its own separate licence: CC0 1.0 Universal, with commercial "
                            + "use permitted.",
                    "Technical structure and test markers are scrubbed from previews, placement, and "
                            + "tracked towns; barriers, lights, and decorative purple blocks remain. "
                            + "Credit: playlightgames — Medieval RPG music pack. Source: "
                            + "playlightgames.itch.io/medieval-rpg-music-pack. The full track list, "
                            + "license link, and notice are kept in CREDITS.md.")
    };

    private record Page(String title, String body, String note) {
    }

    private final boolean[] visited = new boolean[PAGES.length];
    private int page;
    private int bookX;
    private int bookY;
    private int bookWidth;
    private int bookHeight;
    private Button previous;
    private Button next;
    private Button close;

    public RtsGuideScreen() {
        super(Component.literal("The Commander's Field Book"));
        visited[0] = true;
    }

    @Override
    protected void init() {
        bookWidth = Math.min(BOOK_W, Math.max(320, this.width - 24));
        bookHeight = Math.min(BOOK_H, Math.max(260, this.height - 24));
        bookX = (this.width - bookWidth) / 2;
        bookY = (this.height - bookHeight) / 2;

        int buttonWidth = Math.min(132, Math.max(86, (bookWidth - 62) / 3));
        int buttonY = bookY + bookHeight - BUTTON_Y_GAP;
        previous = addRenderableWidget(Button.builder(Component.literal("< PREVIOUS PAGE"),
                        button -> previousPage())
                .bounds(bookX + 16, buttonY, buttonWidth, 20).build());
        close = addRenderableWidget(Button.builder(Component.literal("CLOSE BOOK"),
                        button -> closeGuide())
                .bounds(bookX + (bookWidth - buttonWidth) / 2, buttonY, buttonWidth, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("NEXT PAGE >"),
                        button -> nextPage())
                .bounds(bookX + bookWidth - buttonWidth - 16, buttonY, buttonWidth, 20).build());
        updateButtons();
    }

    private void nextPage() {
        if (page >= PAGES.length - 1) {
            return;
        }
        page++;
        visited[page] = true;
        updateButtons();
    }

    private void previousPage() {
        if (page <= 0) {
            return;
        }
        page--;
        updateButtons();
    }

    private void updateButtons() {
        if (previous == null) {
            return;
        }
        previous.active = page > 0;
        next.active = page < PAGES.length - 1;
        close.active = allPagesVisited();
    }

    private boolean allPagesVisited() {
        for (boolean pageVisited : visited) {
            if (!pageVisited) {
                return false;
            }
        }
        return true;
    }

    private void closeGuide() {
        if (!allPagesVisited()) {
            return;
        }
        ClientPacketDistributor.sendToServer(new GuideCompletedPayload());
        this.minecraft.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        graphics.fill(0, 0, this.width, this.height, OVERLAY);
        drawBook(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawBook(GuiGraphicsExtractor graphics) {
        graphics.fill(bookX - 7, bookY - 5, bookX + bookWidth + 7, bookY + bookHeight + 6,
                0xA0000000);
        graphics.fill(bookX - 3, bookY - 3, bookX + bookWidth + 3, bookY + bookHeight + 3,
                COVER_EDGE);
        graphics.fill(bookX, bookY, bookX + bookWidth, bookY + bookHeight, COVER);
        graphics.outline(bookX + 5, bookY + 5, bookWidth - 10, bookHeight - 10, COVER_EDGE);

        String header = fitText("THE COMMANDER'S FIELD BOOK  -  AGES OF DOMINION", bookWidth - 92);
        graphics.text(this.font, header,
                bookX + (bookWidth - this.font.width(header)) / 2, bookY + 12, COVER_EDGE);

        int pageTop = bookY + 35;
        // Keep the footer status in its own cover strip instead of letting it paint over the page
        // border or the navigation buttons.
        int pageBottom = bookY + bookHeight - 60;
        int pageWidth = (bookWidth - 38) / 2;
        int leftX = bookX + 14;
        int rightX = bookX + bookWidth / 2 + 5;
        graphics.fill(leftX + 3, pageTop + 3, leftX + pageWidth + 3, pageBottom + 3, PAGE_SHADOW);
        graphics.fill(rightX + 3, pageTop + 3, rightX + pageWidth + 3, pageBottom + 3, PAGE_SHADOW);
        graphics.fill(leftX, pageTop, leftX + pageWidth, pageBottom, PAGE);
        graphics.fill(rightX, pageTop, rightX + pageWidth, pageBottom, PAGE);
        graphics.outline(leftX, pageTop, pageWidth, pageBottom - pageTop, GOLD_INK);
        graphics.outline(rightX, pageTop, pageWidth, pageBottom - pageTop, GOLD_INK);

        int seamX = bookX + bookWidth / 2;
        graphics.fill(seamX - 2, pageTop + 2, seamX + 2, pageBottom - 2, 0xFF8A5A34);
        graphics.fill(seamX - 1, pageTop + 4, seamX + 1, pageBottom - 4, 0xFFE4BE7E);

        Page current = PAGES[page];
        drawPageText(graphics, current, leftX, rightX, pageTop, pageBottom, pageWidth);

        String progress = allPagesVisited()
                ? "PAGE " + (page + 1) + " / " + PAGES.length + "   -   CLOSE BOOK UNLOCKED"
                : "PAGE " + (page + 1) + " / " + PAGES.length
                        + "   -   READ EVERY PAGE TO UNLOCK CLOSE BOOK";
        progress = fitText(progress, bookWidth - 28);
        graphics.text(this.font, progress,
                bookX + (bookWidth - this.font.width(progress)) / 2,
                bookY + bookHeight - 43, allPagesVisited() ? COVER_EDGE : MUTED_INK);
    }

    private void drawPageText(GuiGraphicsExtractor graphics, Page current, int leftX, int rightX,
                              int pageTop, int pageBottom, int pageWidth) {
        int innerWidth = pageWidth - 22;
        String title = fitText(current.title(), innerWidth);
        graphics.text(this.font, title,
                leftX + (pageWidth - this.font.width(title)) / 2, pageTop + 12, GOLD_INK);
        graphics.fill(leftX + 12, pageTop + 29, leftX + pageWidth - 12, pageTop + 30, PAGE_SHADOW);

        int y = pageTop + 41;
        for (FormattedCharSequence line : this.font.split(Component.literal(current.body()), innerWidth)) {
            if (y + this.font.lineHeight > pageBottom - 8) {
                break;
            }
            graphics.text(this.font, line, leftX + 11, y, INK);
            y += this.font.lineHeight + 3;
        }

        String noteTitle = "SCRIBE'S NOTE";
        graphics.text(this.font, noteTitle,
                rightX + (pageWidth - this.font.width(noteTitle)) / 2, pageTop + 15, GOLD_INK);
        graphics.fill(rightX + 12, pageTop + 29, rightX + pageWidth - 12, pageTop + 30, PAGE_SHADOW);
        y = pageTop + 42;
        for (FormattedCharSequence line : this.font.split(Component.literal(current.note()), innerWidth)) {
            if (y + this.font.lineHeight > pageBottom - 38) {
                break;
            }
            graphics.text(this.font, line, rightX + 11, y, MUTED_INK);
            y += this.font.lineHeight + 3;
        }

    }

    private String fitText(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        String fitted = text;
        while (fitted.length() > 1 && this.font.width(fitted + "…") > width) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        return fitted + "…";
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
