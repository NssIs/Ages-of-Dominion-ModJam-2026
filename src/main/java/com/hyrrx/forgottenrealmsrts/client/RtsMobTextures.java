package com.hyrrx.forgottenrealmsrts.client;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Shared texture selection for the RTS unit renderers.
 *
 * <p>The renderer slice is intentionally resource-independent: it never falls back to a Mojang
 * texture. {@link #FALLBACK} is the existing mod-owned {@code rts_villager.png}, which is present
 * in this recovered worktree. The other identifiers name mod-owned unit art recovered with the
 * historical client jar; when a variant is outside its authored range, this class returns the
 * guaranteed mod-owned fallback instead of guessing at an asset or indexing past the array.</p>
 */
public final class RtsMobTextures {
    public static final String MOD_ID = "forgotten_realms_rts";
    /** Existing mod-owned art used when a requested variant has no safe texture ID. */
    public static final Identifier FALLBACK = texture("rts_villager");

    private RtsMobTextures() {
    }

    public static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, "textures/entity/" + name + ".png");
    }

    /**
     * Returns a requested variant when it is in range, otherwise the existing mod-owned fallback.
     */
    public static Identifier select(Identifier[] variants, int variant) {
        if (variants == null || variant < 0 || variant >= variants.length || variants[variant] == null) {
            return FALLBACK;
        }
        return variants[variant];
    }

    /**
     * Derives a stable cosmetic palette from the entity UUID for entities without synced variant
     * data. UUIDs keep the palette stable through render-state extraction and do not mutate common
     * entity code or network payloads.
     */
    public static int stableVariant(Entity entity, int variantCount) {
        if (variantCount <= 1 || entity == null) {
            return 0;
        }

        UUID uuid = entity.getUUID();
        int hash = (int) (uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        return Math.floorMod(hash, variantCount);
    }
}
