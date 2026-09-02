package com.hyrrx.forgottenrealmsrts.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws a whole texture, scaled, into a destination rectangle.
 *
 * <p>This exists because the obvious {@code blit} overload does not do that. The ten-argument form:
 *
 * <pre>blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight)</pre>
 *
 * uses {@code width}/{@code height} as <em>both</em> the destination rectangle and the size of the
 * region sampled out of the texture. Passing the art's real dimensions as the last two arguments
 * therefore does not scale it down — it crops a {@code width x height} patch out of the top-left
 * corner at 1:1 and draws that. On a 503x131 background drawn into a 145x38 bar, what you see is a
 * magnified corner of the art, which reads as "zoomed in" and does not change when you adjust the
 * destination size. That cost this project two rounds of chasing the wrong cause.
 *
 * <p>The twelve-argument form takes the source region separately, which is what every call here
 * wants: source region = the entire texture, destination = whatever fits.
 */
public final class HudTextures {
    private static final int TINT_NONE = 0xFFFFFFFF;

    private HudTextures() {
    }

    /** Draws all of {@code texture} scaled into the {@code width x height} rectangle at x, y. */
    public static void blitWhole(GuiGraphicsExtractor graphics, Identifier texture,
                                 int x, int y, int width, int height, int sourceWidth, int sourceHeight) {
        blitWhole(graphics, texture, x, y, width, height, sourceWidth, sourceHeight, TINT_NONE);
    }

    /** As {@link #blitWhole}, with an ARGB tint — used for the hover highlight on HUD buttons. */
    public static void blitWhole(GuiGraphicsExtractor graphics, Identifier texture,
                                 int x, int y, int width, int height,
                                 int sourceWidth, int sourceHeight, int tint) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                width, height, sourceWidth, sourceHeight, sourceWidth, sourceHeight, tint);
    }

    /** Draws one source rectangle from a texture into a separately sized destination rectangle. */
    public static void blitRegion(GuiGraphicsExtractor graphics, Identifier texture,
                                  int x, int y, int width, int height,
                                  int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                                  int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, sourceX, sourceY,
                width, height, sourceWidth, sourceHeight, textureWidth, textureHeight);
    }

    /** Width that preserves the source aspect ratio at the given drawn height. */
    public static int widthForHeight(int height, int sourceWidth, int sourceHeight) {
        return height * sourceWidth / sourceHeight;
    }

    /**
     * Repeats a texture across {@code totalWidth} at its own aspect ratio, rather than stretching a
     * single copy — stretching smears the frame and rivet detail on these panel textures.
     *
     * <p>Because the tile is cut from the drawn {@code height}, a shorter bar means smaller, more
     * numerous tiles. That is deliberate: it keeps the art downscaled instead of magnified.
     * The final tile is allowed to run past the edge and be clipped by the screen.
     */
    public static void tileHorizontally(GuiGraphicsExtractor graphics, Identifier texture,
                                        int y, int totalWidth, int height,
                                        int sourceWidth, int sourceHeight) {
        int tileWidth = Math.max(1, widthForHeight(height, sourceWidth, sourceHeight));
        for (int x = 0; x < totalWidth; x += tileWidth) {
            blitWhole(graphics, texture, x, y, tileWidth, height, sourceWidth, sourceHeight);
        }
    }
}
