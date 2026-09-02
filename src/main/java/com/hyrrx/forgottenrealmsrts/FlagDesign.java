package com.hyrrx.forgottenrealmsrts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A civilization's banner, stored as <strong>parameters, never an image</strong>.
 *
 * <p>Every field is an index into one of the fixed tables below, which keeps the whole design to six
 * ints: trivially serialisable, trivially synced, and reproducible on any client from the shared
 * tables. The client draws it (see {@code FlagRenderer}); this class is deliberately render-free so
 * it is safe to touch on a dedicated server.
 *
 * <p>The tables are append-only for the same reason {@link Resource} is: an index is the wire
 * format. Adding a colour, layout or emblem is fine; reordering or removing one rewrites every saved
 * banner.
 */
public record FlagDesign(int primary, int secondary, int background, int layout, int emblem,
                         int emblemColor) {

    /** Banner palette, ARGB and opaque. Index is the wire value — append, never insert. */
    public static final int[] PALETTE = {
            0xFFE8DCC0, // 0 cream
            0xFF1A1A1A, // 1 black
            0xFFB02A2A, // 2 red
            0xFFD9A521, // 3 gold
            0xFF2A4B8D, // 4 blue
            0xFF2E7D46, // 5 green
            0xFF6A3D9A, // 6 purple
            0xFFB8B8C0, // 7 silver
            0xFF6E4A2A, // 8 brown
            0xFF1F7A70, // 9 teal
            0xFF7A1F2B, // 10 crimson
            0xFFF2F2F2, // 11 white
    };

    /** Field divisions. 0..5. */
    public static final String[] LAYOUTS = {
            "Solid", "Horizontal Split", "Vertical Split",
            "Horizontal Bands", "Vertical Bands", "Cross",
    };

    /** Emblem glyphs drawn centred over the field. 0 is none. */
    public static final String[] EMBLEMS = {
            "None", "Sun", "Moon", "Sword", "Shield",
            "Tower", "Tree", "Star", "Crown", "Hammer",
    };

    public static final FlagDesign DEFAULT = new FlagDesign(2, 3, 0, 3, 8, 3);

    public static final Codec<FlagDesign> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("primary").forGetter(FlagDesign::primary),
            Codec.INT.fieldOf("secondary").forGetter(FlagDesign::secondary),
            Codec.INT.fieldOf("background").forGetter(FlagDesign::background),
            Codec.INT.fieldOf("layout").forGetter(FlagDesign::layout),
            Codec.INT.fieldOf("emblem").forGetter(FlagDesign::emblem),
            Codec.INT.fieldOf("emblem_color").forGetter(FlagDesign::emblemColor)
    ).apply(instance, FlagDesign::new));

    /** ByteBuf-based so it satisfies attachment sync (RegistryFriendlyByteBuf is a ByteBuf). */
    public static final StreamCodec<ByteBuf, FlagDesign> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FlagDesign::primary,
            ByteBufCodecs.VAR_INT, FlagDesign::secondary,
            ByteBufCodecs.VAR_INT, FlagDesign::background,
            ByteBufCodecs.VAR_INT, FlagDesign::layout,
            ByteBufCodecs.VAR_INT, FlagDesign::emblem,
            ByteBufCodecs.VAR_INT, FlagDesign::emblemColor,
            FlagDesign::new);

    /** Clamps every index into its table, so a malformed packet can never index out of bounds. */
    public FlagDesign sanitized() {
        return new FlagDesign(
                clamp(primary, PALETTE.length),
                clamp(secondary, PALETTE.length),
                clamp(background, PALETTE.length),
                clamp(layout, LAYOUTS.length),
                clamp(emblem, EMBLEMS.length),
                clamp(emblemColor, PALETTE.length));
    }

    private static int clamp(int value, int size) {
        return value < 0 ? 0 : (value >= size ? size - 1 : value);
    }

    public int primaryArgb() {
        return PALETTE[clamp(primary, PALETTE.length)];
    }

    public int secondaryArgb() {
        return PALETTE[clamp(secondary, PALETTE.length)];
    }

    public int backgroundArgb() {
        return PALETTE[clamp(background, PALETTE.length)];
    }

    public int emblemArgb() {
        return PALETTE[clamp(emblemColor, PALETTE.length)];
    }
}
