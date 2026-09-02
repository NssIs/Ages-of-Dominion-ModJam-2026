package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsBattle;
import com.hyrrx.forgottenrealmsrts.client.ui.ResultsScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opens the {@link ResultsScreen} the moment the synced campaign outcome leaves "ongoing". */
public final class RtsOutcomeWatcher {
    private static int lastOutcome = RtsBattle.OUTCOME_ONGOING;

    private RtsOutcomeWatcher() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsOutcomeWatcher::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            lastOutcome = RtsBattle.OUTCOME_ONGOING;
            RtsSpectateClientState.clear();
            return;
        }
        int outcome = RtsBattle.outcome(minecraft.player);
        if (outcome != lastOutcome && outcome != RtsBattle.OUTCOME_ONGOING
                && !(minecraft.screen instanceof ResultsScreen)) {
            minecraft.setScreen(new ResultsScreen(outcome == RtsBattle.OUTCOME_VICTORY));
        }
        lastOutcome = outcome;
    }
}
