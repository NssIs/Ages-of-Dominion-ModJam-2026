package com.hyrrx.forgottenrealmsrts;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClocks;

/**
 * Keeps the overworld day/night clock on the familiar vanilla pace. The RTS event command may
 * temporarily fast-forward toward night, but the ordinary campaign clock should not surprise the
 * player with a permanently compressed day.
 *
 * <p>A vanilla day is 24000 ticks — twenty minutes. The campaign keeps that familiar pace so the
 * day/night clock is predictable; an explicit moon-event command is the only thing allowed to
 * accelerate the transition toward night. 26.1 moved time onto a
 * {@link net.minecraft.world.clock.ClockManager}, which exposes a per-clock rate.
 */
public final class RtsDayCycle {
    /** Normal overworld clock rate; explicit event transitions are the only fast-forward. */
    public static final float DAY_RATE = 1.0F;

    private RtsDayCycle() {
    }

    /** Applies the normal campaign rate to the overworld clock. */
    public static void apply(ServerLevel overworld) {
        setRate(overworld, DAY_RATE);
    }

    /** Temporarily changes the clock rate for a controlled event transition. */
    public static void setRate(ServerLevel overworld, float rate) {
        overworld.registryAccess().get(WorldClocks.OVERWORLD).ifPresent(holder ->
                overworld.clockManager().setRate(holder, Math.max(0.0F, rate)));
    }

    /** Returns a defeated or newly founded realm to sunrise on the first day. */
    public static void resetToDay(ServerLevel overworld) {
        overworld.registryAccess().get(WorldClocks.OVERWORLD).ifPresent(holder -> {
            overworld.clockManager().setTotalTicks(holder, 0L);
            overworld.clockManager().setRate(holder, DAY_RATE);
        });
    }
}
