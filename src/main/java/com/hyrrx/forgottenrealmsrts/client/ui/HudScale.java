package com.hyrrx.forgottenrealmsrts.client.ui;

/**
 * The fit-to-screen rule shared by the top and bottom bars.
 *
 * <p>Both bars are authored at a natural size and then shrunk to whatever the GUI-scaled screen
 * actually gives them, using {@code graphics.pose().scale(s, s)}. That transform is <em>uniform</em>,
 * which is the whole point of putting this in one place: a bar that shrinks horizontally also
 * shrinks vertically, so its background band must be drawn at {@link #bandHeight}, not at its
 * natural height. Drawing the band at full height while the contents render at half size leaves the
 * rest of the band as empty texture, and — because a tiled background's tile width is derived from
 * the band height — magnifies the art relative to the text sitting on it.
 *
 * <p>Anything that hit-tests a bar must use the same two calls the renderer used. A hit box computed
 * from the natural height while the bar draws at 60% of it swallows clicks on empty sky.
 */
public final class HudScale {
    /** Floor on the shrink. Past this, text stops being legible and clipping is the lesser evil. */
    public static final float MIN_SCALE = 0.4F;

    private HudScale() {
    }

    /** How much content of {@code naturalWidth} must shrink to fit {@code availableWidth}. */
    public static float fit(int naturalWidth, int availableWidth) {
        return fit(naturalWidth, availableWidth, 1.0F);
    }

    /**
     * As {@link #fit(int, int)}, but allowed to grow up to {@code maxScale}.
     *
     * <p>Capping at 1.0 is right for a bar whose art is authored at its intended size, but wrong for
     * one that should fill the screen: at a low GUI Scale the screen is far wider than the layout,
     * the scale pins at 1.0, and the surplus turns into a dead strip of empty bar. Passing a
     * {@code maxScale} above 1.0 lets the layout expand into that surplus instead. The cap should
     * come from a real constraint — usually how tall the bar is allowed to get, since this scale is
     * uniform and grows the height too.
     */
    public static float fit(int naturalWidth, int availableWidth, float maxScale) {
        if (naturalWidth <= 0) {
            return 1.0F;
        }
        return Math.max(MIN_SCALE, Math.min(maxScale, (float) availableWidth / naturalWidth));
    }

    /** The height a bar of {@code naturalHeight} is actually drawn at under {@code scale}. */
    public static int bandHeight(int naturalHeight, float scale) {
        return Math.round(naturalHeight * scale);
    }
}
