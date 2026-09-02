package com.hyrrx.forgottenrealmsrts;

/**
 * The six stockpiles the realm runs on, in the order the top bar shows them.
 *
 * <p><strong>The ordinal is the wire format.</strong> Building costs travel as a fixed six-int array
 * indexed by this enum rather than as a map of strings, which keeps the payload codec trivial and
 * the packet small. Reordering these constants therefore changes the protocol — append, never
 * insert.
 */
public enum Resource {
    WOOD("Wood", "wood"),
    STONE("Stone", "stone"),
    IRON("Iron", "iron"),
    GOLD("Gold", "gold"),
    FOOD("Food", "food"),
    COAL("Coal", "coal");

    public static final Resource[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final String label;
    /** The key used in {@code buildings.json} and the suffix of the top bar's icon texture. */
    private final String key;

    Resource(String label, String key) {
        this.label = label;
        this.key = key;
    }

    public String label() {
        return label;
    }

    public String key() {
        return key;
    }
}
