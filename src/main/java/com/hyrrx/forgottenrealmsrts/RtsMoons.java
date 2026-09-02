package com.hyrrx.forgottenrealmsrts;

/**
 * The special-moon schedule — a signature of the game's rhythm.
 *
 * <p>Every {@link #INTERVAL}th night the moon turns and the rules change for one night: a
 * {@code BLOOD} moon throws a far larger invasion, a {@code GOLDEN} moon makes completed building
 * income run rich, and a {@code BLUE} (slumber) moon calls the whole world to rest — no invasion,
 * but slower completed-building income. The rotation is deterministic and keeps the three signs in
 * a fixed order: Golden, Blue, Blood.
 *
 * <p>Both the invasion director ({@link RtsInvasion}) and the economy ({@link RtsProduction}) read
 * the same schedule, so the moon a player is warned about is exactly the one they experience.
 */
public final class RtsMoons {
    public enum Moon { NONE, BLOOD, BLUE, GOLDEN }

    /** A special moon every this-many nights. */
    public static final int INTERVAL = 7;

    private RtsMoons() {
    }

    /** The moon for a given zero-based day index (the HUD shows {@code day + 1}). */
    public static Moon forDay(long day) {
        long shown = day + 1;
        if (shown % INTERVAL != 0) {
            return Moon.NONE;
        }
        return switch ((int) ((shown / INTERVAL) % 3)) {
            case 0 -> Moon.BLOOD;
            case 1 -> Moon.GOLDEN;
            default -> Moon.BLUE;
        };
    }
}
