package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsMoons;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/** Client-only palette controls for the natural and operator-forced RTS moons. */
public final class RtsMoonVisuals {
    private static final int BLOOD_SKY = 0xFF64151D;
    private static final int GOLDEN_SKY = 0xFF836126;
    private static final int BLUE_SKY = 0xFF1F3B78;
    private static final int BLOOD_MOON = 0xFFFF4E4E;
    private static final int GOLDEN_MOON = 0xFFFFD45A;
    private static final int BLUE_MOON = 0xFF8AB8FF;
    private static RtsMoons.Moon forcedMoon = RtsMoons.Moon.NONE;

    private RtsMoonVisuals() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsMoonVisuals::onExtractLevelRenderState);
        NeoForge.EVENT_BUS.addListener(RtsMoonVisuals::onRenderGuiAtmosphere);
    }

    public static void setForcedMoon(RtsMoons.Moon moon) {
        forcedMoon = moon == null ? RtsMoons.Moon.NONE : moon;
    }

    /** The moon is only visible in the normal Minecraft night window. */
    public static RtsMoons.Moon activeMoon(ClientLevel level) {
        if (level == null || !isNight(level.getOverworldClockTime())) {
            return RtsMoons.Moon.NONE;
        }
        if (forcedMoon != RtsMoons.Moon.NONE) {
            return forcedMoon;
        }
        return RtsMoons.forDay(Math.floorDiv(level.getOverworldClockTime(), 24000L));
    }

    /** Tints the mutable vanilla sky render state, preserving the normal time-of-day brightness. */
    private static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        RtsMoons.Moon moon = activeMoon(event.getLevel());
        if (moon == RtsMoons.Moon.NONE) {
            return;
        }
        LevelRenderState state = event.getRenderState();
        state.skyRenderState.skyColor = tintSky(state.skyRenderState.skyColor, skyColor(moon), skyStrength(moon));
    }

    /**
     * The detached RTS camera often leaves the actual moon outside the useful part of the frame.
     * A restrained world-facing colour wash keeps the grass and terrain readable as a blood, golden,
     * or slumber moon without tinting the RTS panels drawn after the GUI pre-event.
     */
    private static void onRenderGuiAtmosphere(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null
                || !RtsMode.isActive(minecraft.player)) {
            return;
        }
        RtsMoons.Moon moon = activeMoon(minecraft.level);
        if (moon == RtsMoons.Moon.NONE) {
            return;
        }
        var graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), atmosphereTint(moon));
    }

    /** Supplies the RGB passed into vanilla's moon transform without replacing its atlas or phase. */
    public static Vector4fc tintMoon(Vector4fc vanillaColor) {
        ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return vanillaColor;
        }
        int color = moonColor(activeMoon(level));
        if (color == 0) {
            return vanillaColor;
        }
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        return new Vector4f(red, green, blue, vanillaColor.w());
    }

    private static boolean isNight(long dayTime) {
        long phase = Math.floorMod(dayTime, 24000L);
        return phase >= 13000L && phase < 23000L;
    }

    private static int skyColor(RtsMoons.Moon moon) {
        return switch (moon) {
            case BLOOD -> BLOOD_SKY;
            case GOLDEN -> GOLDEN_SKY;
            case BLUE -> BLUE_SKY;
            default -> 0;
        };
    }

    private static int moonColor(RtsMoons.Moon moon) {
        return switch (moon) {
            case BLOOD -> BLOOD_MOON;
            case GOLDEN -> GOLDEN_MOON;
            case BLUE -> BLUE_MOON;
            default -> 0;
        };
    }

    private static float skyStrength(RtsMoons.Moon moon) {
        return switch (moon) {
            case BLOOD -> 0.62F;
            case GOLDEN, BLUE -> 0.48F;
            default -> 0.0F;
        };
    }

    private static int atmosphereTint(RtsMoons.Moon moon) {
        return switch (moon) {
            // Enough red to make the terrain read as an event even when the moon is off-screen.
            case BLOOD -> 0x2EB52B35;
            case GOLDEN -> 0x22D3A52A;
            case BLUE -> 0x222D63A8;
            default -> 0;
        };
    }

    private static int tintSky(int base, int tint, float strength) {
        int alpha = (base >>> 24) & 0xFF;
        int red = blend((base >> 16) & 0xFF, (tint >> 16) & 0xFF, strength);
        int green = blend((base >> 8) & 0xFF, (tint >> 8) & 0xFF, strength);
        int blue = blend(base & 0xFF, tint & 0xFF, strength);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int blend(int base, int tint, float strength) {
        return Math.round(base + (tint - base) * strength);
    }
}
