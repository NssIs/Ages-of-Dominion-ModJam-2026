package com.hyrrx.forgottenrealmsrts.client.input;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import com.hyrrx.forgottenrealmsrts.RtsCivilization;
import com.hyrrx.forgottenrealmsrts.network.RallyArmyPayload;
import com.hyrrx.forgottenrealmsrts.network.ArmyCommandPayload;
import com.hyrrx.forgottenrealmsrts.network.RepairTownHallPayload;
import com.hyrrx.forgottenrealmsrts.network.RequestHomePayload;
import com.hyrrx.forgottenrealmsrts.network.AdvanceAgePayload;
import com.hyrrx.forgottenrealmsrts.network.TrainGuardianPayload;
import com.hyrrx.forgottenrealmsrts.network.TrainVillagerPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.build.BuildGhost;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingRaycast;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingSelectionController;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's own key bindings.
 *
 * <p><strong>Real {@link KeyMapping}s, not hardcoded key codes</strong>, because the player has to
 * be able to rebind them — and because the placement HUD shows whatever they are currently bound
 * to, which only works if the game owns them. This is the mod's first use of the mod event bus in
 * {@code ForgottenRealmsRTSClient.register}, which until now ignored the bus it was handed.
 *
 * <p>Two 26.1 details worth not rediscovering: {@code KeyMapping.Category} is a <strong>record
 * wrapping an {@link Identifier}</strong> (it used to be a plain {@code String}), and it has to be
 * registered with {@code registerCategory} before a mapping can use it.
 */
public final class RtsKeyBindings {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "rts"));

    /** Only meaningful with no screen open, which is where the RTS overlay lives. */
    public static final KeyMapping ROTATE_LEFT = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".rotate_left",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, CATEGORY);

    public static final KeyMapping ROTATE_RIGHT = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".rotate_right",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, CATEGORY);

    public static final KeyMapping MOVE_SELECTED = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".move_selected",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    public static final KeyMapping UPGRADE_SELECTED = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".upgrade_selected",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY);

    /** Destructive and irreversible, so a single press only arms it; see {@code beginDemolish()}. */
    public static final KeyMapping DEMOLISH_SELECTED = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".demolish_selected",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DELETE, CATEGORY);

    public static final KeyMapping HOME = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".home",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    public static final KeyMapping TRAIN_VILLAGER = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".train_villager",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, CATEGORY);

    public static final KeyMapping ADVANCE_AGE = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".advance_age",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    public static final KeyMapping TRAIN_GUARDIAN = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".train_guardian",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    public static final KeyMapping REPAIR = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".repair",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    public static final KeyMapping RALLY_ARMY = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".rally_army",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    public static final KeyMapping RALLY_HOME = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".command_rally_home",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    public static final KeyMapping ATTACK_NEAREST = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".command_attack_nearest",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    public static final KeyMapping STOP_ARMY = new KeyMapping(
            "key." + ForgottenRealmsRTS.MOD_ID + ".command_stop",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private RtsKeyBindings() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RtsKeyBindings::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(RtsKeyBindings::onClientTick);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(ROTATE_LEFT);
        event.register(ROTATE_RIGHT);
        event.register(MOVE_SELECTED);
        event.register(UPGRADE_SELECTED);
        event.register(DEMOLISH_SELECTED);
        event.register(HOME);
        event.register(TRAIN_VILLAGER);
        event.register(ADVANCE_AGE);
        event.register(TRAIN_GUARDIAN);
        event.register(REPAIR);
        event.register(RALLY_ARMY);
        event.register(RALLY_HOME);
        event.register(ATTACK_NEAREST);
        event.register(STOP_ARMY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        // consumeClick drains the queued presses, so holding the key does not spin the building.
        while (ROTATE_LEFT.consumeClick()) {
            BuildGhost.rotateLeft();
        }
        while (ROTATE_RIGHT.consumeClick()) {
            BuildGhost.rotateRight();
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean active = minecraft.player != null && minecraft.screen == null
                && RtsMode.isActive(minecraft.player);
        if (!active) {
            while (MOVE_SELECTED.consumeClick()) {
                // Drain action presses outside the RTS overlay so they cannot fire on activation.
            }
            while (UPGRADE_SELECTED.consumeClick()) {
                // Drain action presses outside the RTS overlay so they cannot fire on activation.
            }
            while (DEMOLISH_SELECTED.consumeClick()) {
                // Drain, as above.
            }
            while (HOME.consumeClick()) {
                // Drain, as above.
            }
            while (TRAIN_VILLAGER.consumeClick()) {
                // Drain, as above.
            }
            while (ADVANCE_AGE.consumeClick()) {
                // Drain, as above.
            }
            while (TRAIN_GUARDIAN.consumeClick()) {
                // Drain, as above.
            }
            while (REPAIR.consumeClick()) {
                // Drain, as above.
            }
            while (RALLY_ARMY.consumeClick()) {
                // Drain, as above.
            }
            while (RALLY_HOME.consumeClick()) {
                // Drain, as above.
            }
            while (ATTACK_NEAREST.consumeClick()) {
                // Drain, as above.
            }
            while (STOP_ARMY.consumeClick()) {
                // Drain, as above.
            }
            return;
        }
        while (MOVE_SELECTED.consumeClick()) {
            BuildingSelectionController.beginMove();
        }
        while (UPGRADE_SELECTED.consumeClick()) {
            BuildingSelectionController.beginUpgrade();
        }
        while (DEMOLISH_SELECTED.consumeClick()) {
            BuildingSelectionController.beginDemolish();
        }
        boolean home = false;
        while (HOME.consumeClick()) {
            home = true;
        }
        if (home && RtsCivilization.isFounded(minecraft.player)) {
            ClientPacketDistributor.sendToServer(new RequestHomePayload());
        }
        boolean train = false;
        while (TRAIN_VILLAGER.consumeClick()) {
            train = true;
        }
        if (train && RtsCivilization.isFounded(minecraft.player)) {
            ClientPacketDistributor.sendToServer(new TrainVillagerPayload());
        }
        boolean advance = false;
        while (ADVANCE_AGE.consumeClick()) {
            advance = true;
        }
        if (advance && RtsCivilization.isFounded(minecraft.player)) {
            ClientPacketDistributor.sendToServer(new AdvanceAgePayload());
        }
        boolean guardian = false;
        while (TRAIN_GUARDIAN.consumeClick()) {
            guardian = true;
        }
        if (guardian && RtsCivilization.isFounded(minecraft.player)) {
            ClientPacketDistributor.sendToServer(new TrainGuardianPayload());
        }
        boolean repair = false;
        while (REPAIR.consumeClick()) {
            repair = true;
        }
        if (repair && RtsCivilization.isFounded(minecraft.player)) {
            ClientPacketDistributor.sendToServer(new RepairTownHallPayload());
        }
        boolean rally = false;
        while (RALLY_ARMY.consumeClick()) {
            rally = true;
        }
        if (rally && RtsCivilization.isFounded(minecraft.player)) {
            BlockHitResult hit = BuildingRaycast.pick(minecraft);
            if (hit != null) {
                ClientPacketDistributor.sendToServer(new RallyArmyPayload(hit.getBlockPos()));
            }
        }

        boolean rallyHome = false;
        while (RALLY_HOME.consumeClick()) {
            rallyHome = true;
        }
        if (rallyHome && RtsCivilization.isFounded(minecraft.player)) {
            issueCommand(ArmyCommandPayload.Command.RALLY_HOME);
        }

        boolean attackNearest = false;
        while (ATTACK_NEAREST.consumeClick()) {
            attackNearest = true;
        }
        if (attackNearest && RtsCivilization.isFounded(minecraft.player)) {
            issueCommand(ArmyCommandPayload.Command.ATTACK_NEAREST);
        }

        boolean stopArmy = false;
        while (STOP_ARMY.consumeClick()) {
            stopArmy = true;
        }
        if (stopArmy && RtsCivilization.isFounded(minecraft.player)) {
            issueCommand(ArmyCommandPayload.Command.STOP);
        }
    }

    /** Selected units get the order; with no selection the legacy whole-army shortcut remains. */
    private static void issueCommand(ArmyCommandPayload.Command command) {
        if (!BuildingSelectionController.issueSelectedCommand(command)) {
            ClientPacketDistributor.sendToServer(new ArmyCommandPayload(command));
        }
    }
}
