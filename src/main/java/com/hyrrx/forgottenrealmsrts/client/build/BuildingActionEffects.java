package com.hyrrx.forgottenrealmsrts.client.build;

import com.hyrrx.forgottenrealmsrts.network.BuildingEffectPayload;

import java.util.ArrayList;
import java.util.List;

/** Short-lived client-side action signals, consumed by the world effect renderer. */
public final class BuildingActionEffects {
    private static final long DURATION_MILLIS = 900L;
    private static final List<ActiveEffect> ACTIVE = new ArrayList<>();

    private BuildingActionEffects() {
    }

    public static void accept(BuildingEffectPayload payload) {
        ACTIVE.add(new ActiveEffect(payload, System.currentTimeMillis()));
    }

    public static List<ActiveEffect> active(long now) {
        ACTIVE.removeIf(effect -> now - effect.startedAt() >= DURATION_MILLIS);
        return List.copyOf(ACTIVE);
    }

    public static long durationMillis() {
        return DURATION_MILLIS;
    }

    public record ActiveEffect(BuildingEffectPayload payload, long startedAt) {
    }
}
