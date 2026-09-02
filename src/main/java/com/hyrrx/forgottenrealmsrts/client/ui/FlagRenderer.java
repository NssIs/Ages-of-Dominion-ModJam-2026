package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.FlagDesign;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws a {@link FlagDesign} into a rectangle from its parameters alone — no texture, so a banner is
 * reproducible on any client and the founding screen previews it live as the player edits.
 *
 * <p>Everything is blocky {@code fill}s on purpose: it reads as pixel art beside the mod's existing
 * UI, and it stays crisp at both the founding-screen preview size and the small HUD badge.
 */
public final class FlagRenderer {
    private static final int BORDER = 0xFF15100A;
    private static final int SHADE = 0x33000000;

    /** 9x9 emblem glyphs, indexed by {@link FlagDesign#EMBLEMS}. Index 0 (None) has no glyph. */
    private static final String[][] GLYPHS = {
            null, // None
            { // Sun
                "....#....", ".#..#..#.", "..#####..", "...###...", "##.###.##",
                "...###...", "..#####..", ".#..#..#.", "....#...." },
            { // Moon
                "..####...", ".##......", "##.......", "##.......", "##.......",
                "##.......", "##.......", ".##......", "..####..." },
            { // Sword
                "....#....", "....#....", "....#....", "....#....", "..#####..",
                "....#....", "....#....", "....#....", "....#...." },
            { // Shield
                ".#######.", ".#######.", ".#######.", ".#######.", "..#####..",
                "..#####..", "...###...", "....#....", "........." },
            { // Tower
                "#.#.#.#.#", "#########", "#########", ".#######.", ".#######.",
                ".#######.", ".#######.", "#########", "........." },
            { // Tree
                "....#....", "...###...", "..#####..", ".#######.", "..#####..",
                ".#######.", "....#....", "....#....", "...###..." },
            { // Star
                "....#....", "....#....", "...###...", "#########", ".#######.",
                "..#####..", "..#.#.#..", ".#.....#.", "........." },
            { // Crown
                "#...#...#", "#.#.#.#.#", "#########", "#########", "#########",
                ".........", ".........", ".........", "........." },
            { // Hammer
                ".#######.", ".#######.", ".#######.", "...##....", "...##....",
                "...##....", "...##....", "...##....", "...##...." },
    };

    private FlagRenderer() {
    }

    /** Draws the banner (with a dark border) into the rectangle. */
    public static void draw(GuiGraphicsExtractor graphics, FlagDesign design, int x, int y,
                            int width, int height) {
        FlagDesign flag = design.sanitized();
        int primary = flag.primaryArgb();
        int secondary = flag.secondaryArgb();
        int background = flag.backgroundArgb();

        // Border frame.
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER);

        switch (flag.layout()) {
            case 1 -> { // Horizontal split
                graphics.fill(x, y, x + width, y + height / 2, primary);
                graphics.fill(x, y + height / 2, x + width, y + height, secondary);
            }
            case 2 -> { // Vertical split
                graphics.fill(x, y, x + width / 2, y + height, primary);
                graphics.fill(x + width / 2, y, x + width, y + height, secondary);
            }
            case 3 -> { // Horizontal bands
                int third = height / 3;
                graphics.fill(x, y, x + width, y + height, primary);
                graphics.fill(x, y + third, x + width, y + height - third, secondary);
            }
            case 4 -> { // Vertical bands
                int third = width / 3;
                graphics.fill(x, y, x + width, y + height, primary);
                graphics.fill(x + third, y, x + width - third, y + height, secondary);
            }
            case 5 -> { // Cross
                graphics.fill(x, y, x + width, y + height, background);
                int barW = Math.max(2, width / 5);
                int barH = Math.max(2, height / 5);
                graphics.fill(x + (width - barW) / 2, y, x + (width + barW) / 2, y + height, secondary);
                graphics.fill(x, y + (height - barH) / 2, x + width, y + (height + barH) / 2, secondary);
            }
            default -> graphics.fill(x, y, x + width, y + height, primary); // Solid
        }

        drawEmblem(graphics, flag, x, y, width, height);

        // A soft top-down shade so the flat fills read as cloth rather than paper.
        graphics.fill(x, y + height * 3 / 4, x + width, y + height, SHADE);
    }

    private static void drawEmblem(GuiGraphicsExtractor graphics, FlagDesign flag,
                                   int x, int y, int width, int height) {
        String[] glyph = flag.emblem() >= 0 && flag.emblem() < GLYPHS.length
                ? GLYPHS[flag.emblem()] : null;
        if (glyph == null) {
            return;
        }
        int color = flag.emblemArgb();
        int span = Math.round(Math.min(width, height) * 0.6F);
        int cell = Math.max(1, span / 9);
        int drawn = cell * 9;
        int ox = x + (width - drawn) / 2;
        int oy = y + (height - drawn) / 2;
        for (int row = 0; row < 9; row++) {
            String line = glyph[row];
            for (int col = 0; col < 9 && col < line.length(); col++) {
                if (line.charAt(col) == '#') {
                    int px = ox + col * cell;
                    int py = oy + row * cell;
                    graphics.fill(px, py, px + cell, py + cell, color);
                }
            }
        }
    }
}
