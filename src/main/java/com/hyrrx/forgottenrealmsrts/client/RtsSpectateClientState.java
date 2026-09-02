package com.hyrrx.forgottenrealmsrts.client;

/** Client presentation state for the persistent restart affordance while viewing a fallen town. */
public final class RtsSpectateClientState {
    private static boolean active;

    private RtsSpectateClientState() {
    }

    public static void enter() {
        active = true;
    }

    public static boolean active() {
        return active;
    }

    public static void clear() {
        active = false;
    }
}
