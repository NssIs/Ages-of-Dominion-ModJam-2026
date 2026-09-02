package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Keeps the GUI scale somewhere the RTS HUD is actually usable.
 *
 * <p>The HUD is dense — a full-width status bar, a command bar, a minimap column — and a higher GUI
 * scale draws all of it <em>larger</em>, not smaller: the screen is only 640 GUI pixels wide at
 * scale 3 and 480 at scale 4, so less fits and the structure tray sheds columns. RTS mode drops the
 * scale to 2 the first time it is entered, and anything above 2 afterwards prompts.
 *
 * <p>The prompt is a warning, <strong>not a block</strong>: "continue at my own risk" keeps the
 * player's choice and is not asked about again until they change the setting once more. Reverting is
 * only done when they actively cancel.
 */
public final class RtsGuiScaleGuard {
    /** The scale the HUD is designed around. */
    private static final int PREFERRED_SCALE = 2;

    private static boolean appliedDefault;
    /** Last value we have already judged, so the prompt fires on a change rather than every tick. */
    private static int lastSeenScale = -1;
    /** Set while our own prompt is open, so reverting the setting does not re-trigger the prompt. */
    private static boolean prompting;

    private RtsGuiScaleGuard() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsGuiScaleGuard::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        int scale = minecraft.options.guiScale().get();
        if (!appliedDefault) {
            appliedDefault = true;
            // Scale 0 is "auto", which on a large monitor resolves well past 2.
            if (scale == 0 || scale > PREFERRED_SCALE) {
                applyScale(minecraft, PREFERRED_SCALE);
                lastSeenScale = PREFERRED_SCALE;
                return;
            }
            lastSeenScale = scale;
            return;
        }

        if (prompting || scale == lastSeenScale) {
            return;
        }
        int previous = lastSeenScale;
        lastSeenScale = scale;
        if (scale != 0 && scale <= PREFERRED_SCALE) {
            return;
        }

        prompting = true;
        minecraft.setScreen(new ConfirmScreen(
                accepted -> {
                    prompting = false;
                    if (!accepted) {
                        int revertTo = previous > 0 && previous <= PREFERRED_SCALE ? previous : PREFERRED_SCALE;
                        applyScale(minecraft, revertTo);
                        lastSeenScale = revertTo;
                    }
                    minecraft.setScreen(null);
                },
                Component.literal("GUI Scale not recommended").withStyle(ChatFormatting.GOLD),
                Component.literal("Ages of Dominion is not recommended being used on a "
                        + "gui level more than two. Everything is drawn larger, so less of the "
                        + "HUD fits on screen and the structure tray loses columns."),
                Component.literal("Continue on my own risk"),
                CommonComponents.GUI_CANCEL
        ));
    }

    private static void applyScale(Minecraft minecraft, int scale) {
        minecraft.options.guiScale().set(scale);
        minecraft.options.save();
        minecraft.resizeGui();
    }
}
