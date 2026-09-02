package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws a stone panel of <em>any</em> width and height, so HUD containers stop being fixed-size
 * pictures.
 *
 * <p>The bars used to be one 503x131 image scaled into whatever rectangle was wanted, which is why
 * they looked stretched: a wide short bar squashed the rails, a tall one smeared them. This instead
 * builds the panel out of three slices cut from that same art (see {@code tools/make_panel_slices.py}):
 * a top rail, a bare stone middle, and a bottom rail. The rails keep their real proportions at every
 * size, and the middle repeats to fill whatever is left.
 *
 * <p>The middle slice is mirrored vertically in the source file precisely so that stacking copies of
 * it produces no seam — that is what lets a panel be any height without the stone banding.
 *
 * <p>Tiles are drawn overrunning the panel and clipped with a scissor rectangle, rather than being
 * squeezed to divide evenly. Squeezing is the stretch this class exists to avoid.
 */
public final class RtsPanel {
    private static final Identifier CAP_TOP = texture("panel_cap_top");
    private static final Identifier MIDDLE = texture("panel_middle");
    private static final Identifier CAP_BOTTOM = texture("panel_cap_bottom");
    private static final Identifier CAP_LEFT = texture("panel_cap_left");
    private static final Identifier CAP_RIGHT = texture("panel_cap_right");

    /**
     * Which rails to draw. Omitting a rail is how two stacked panels become one continuous box
     * instead of two boxes touching: the map panel leaves off its top and bottom, the civilisation
     * panel below it leaves off its top, and the column reads as a single container running down
     * from the bar above it.
     */
    public static final int EDGE_TOP = 1;
    public static final int EDGE_BOTTOM = 2;
    public static final int EDGE_LEFT = 4;
    public static final int EDGE_RIGHT = 8;
    public static final int EDGE_SIDES = EDGE_LEFT | EDGE_RIGHT;
    public static final int EDGE_ALL = EDGE_TOP | EDGE_BOTTOM | EDGE_LEFT | EDGE_RIGHT;

    /** Native slice sizes. All three are cut from the same 503px-wide source. */
    private static final int SLICE_WIDTH = 503;
    private static final int CAP_TOP_HEIGHT = 16;
    private static final int MIDDLE_HEIGHT = 96;
    private static final int CAP_BOTTOM_HEIGHT = 33;
    /** The vertical rails: the horizontal rail rotated a quarter turn, mirrored to tile. */
    private static final int CAP_SIDE_WIDTH = 16;
    private static final int CAP_SIDE_HEIGHT = 1004;

    /**
     * How many destination pixels one source pixel becomes. Below 1.0 the art is downsampled, which
     * is what keeps it sharp — see the MODMAP gotcha about the bar reading as "zoomed in" when it
     * was being drawn magnified.
     */
    public static final float DEFAULT_TEXTURE_SCALE = 0.25F;

    private RtsPanel() {
    }

    /** Height of the top rail at a given texture scale — content should start below it. */
    public static int capTopHeight(float textureScale) {
        return Math.max(1, Math.round(CAP_TOP_HEIGHT * textureScale));
    }

    /** Height of the bottom rail at a given texture scale. */
    public static int capBottomHeight(float textureScale) {
        return Math.max(1, Math.round(CAP_BOTTOM_HEIGHT * textureScale));
    }

    /** Width of a vertical rail at a given texture scale — content should start inside it. */
    public static int capSideWidth(float textureScale) {
        return Math.max(1, Math.round(CAP_SIDE_WIDTH * textureScale));
    }

    /** A full-width bar: top and bottom rails only, since its sides run off the screen. */
    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        draw(graphics, x, y, width, height, EDGE_TOP | EDGE_BOTTOM, DEFAULT_TEXTURE_SCALE);
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            int edges) {
        draw(graphics, x, y, width, height, edges, DEFAULT_TEXTURE_SCALE);
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            int edges, float textureScale) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int tileWidth = Math.max(1, Math.round(SLICE_WIDTH * textureScale));
        int topHeight = (edges & EDGE_TOP) != 0 ? capTopHeight(textureScale) : 0;
        int bottomHeight = (edges & EDGE_BOTTOM) != 0 ? capBottomHeight(textureScale) : 0;
        int middleHeight = Math.max(1, Math.round(MIDDLE_HEIGHT * textureScale));
        int sideWidth = capSideWidth(textureScale);
        int sideTileHeight = Math.max(1, Math.round(CAP_SIDE_HEIGHT * textureScale));

        // A panel shorter than its two rails gets the rails only, split proportionally, rather than
        // a negative middle.
        if (topHeight > 0 && bottomHeight > 0 && topHeight + bottomHeight >= height) {
            topHeight = Math.max(1, height * CAP_TOP_HEIGHT / (CAP_TOP_HEIGHT + CAP_BOTTOM_HEIGHT));
            bottomHeight = Math.max(1, height - topHeight);
        }

        graphics.enableScissor(x, y, x + width, y + height);

        // Tiles are phase-locked to the screen origin, not to the panel's own corner. Two panels
        // drawn side by side — the top bar split so its bottom rail stops where the side column
        // begins — then share one continuous stone pattern instead of each restarting its tiling and
        // showing a vertical join. Scissor already clips the overhang, so starting off-panel is free.
        int middleTop = y + topHeight;
        int middleBottom = y + height - bottomHeight;
        for (int tileY = alignDown(middleTop, middleHeight); tileY < middleBottom; tileY += middleHeight) {
            for (int tileX = alignDown(x, tileWidth); tileX < x + width; tileX += tileWidth) {
                HudTextures.blitWhole(graphics, MIDDLE, tileX, tileY, tileWidth, middleHeight,
                        SLICE_WIDTH, MIDDLE_HEIGHT);
            }
        }
        // Sides first, so the horizontal rails cap them at the corners rather than the reverse.
        for (int tileY = alignDown(y, sideTileHeight); tileY < y + height; tileY += sideTileHeight) {
            if ((edges & EDGE_LEFT) != 0) {
                HudTextures.blitWhole(graphics, CAP_LEFT, x, tileY, sideWidth, sideTileHeight,
                        CAP_SIDE_WIDTH, CAP_SIDE_HEIGHT);
            }
            if ((edges & EDGE_RIGHT) != 0) {
                HudTextures.blitWhole(graphics, CAP_RIGHT, x + width - sideWidth, tileY,
                        sideWidth, sideTileHeight, CAP_SIDE_WIDTH, CAP_SIDE_HEIGHT);
            }
        }
        for (int tileX = alignDown(x, tileWidth); tileX < x + width; tileX += tileWidth) {
            if (topHeight > 0) {
                HudTextures.blitWhole(graphics, CAP_TOP, tileX, y, tileWidth, topHeight,
                        SLICE_WIDTH, CAP_TOP_HEIGHT);
            }
            if (bottomHeight > 0) {
                HudTextures.blitWhole(graphics, CAP_BOTTOM, tileX, y + height - bottomHeight,
                        tileWidth, bottomHeight, SLICE_WIDTH, CAP_BOTTOM_HEIGHT);
            }
        }

        graphics.disableScissor();
    }

    /** Largest multiple of {@code step} at or below {@code value}, for negative values too. */
    private static int alignDown(int value, int step) {
        return value - Math.floorMod(value, step);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "textures/gui/panel/" + name + ".png");
    }
}
