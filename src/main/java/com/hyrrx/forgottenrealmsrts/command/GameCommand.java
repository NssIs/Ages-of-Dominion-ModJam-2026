package com.hyrrx.forgottenrealmsrts.command;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import com.hyrrx.forgottenrealmsrts.RtsCivilization;
import com.hyrrx.forgottenrealmsrts.RtsEconomy;
import com.hyrrx.forgottenrealmsrts.RtsInvasion;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.RtsMoons;
import com.hyrrx.forgottenrealmsrts.RtsWorld;
import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /game activate} / {@code /game deactivate} — operator-only toggle for the whole RTS
 * overlay (forced spectator, isometric camera, cursor grab, both HUD panels). Deactivating
 * restores whatever gamemode the player was in right before the mod last forced them into
 * spectator; activating captures the current one so a later deactivate has it to restore.
 */
public final class GameCommand {
    private GameCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("game")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("activate").executes(GameCommand::activate))
                .then(Commands.literal("deactivate").executes(GameCommand::deactivate))
                .then(Commands.literal("event")
                        .then(Commands.argument("moon", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("golden-moon", "blood-moon", "blue-moon"), builder))
                                .then(Commands.literal("start").executes(GameCommand::startEvent))
                                .then(Commands.literal("end").executes(GameCommand::endEvent))))
                .then(Commands.literal("buildings").executes(GameCommand::buildings)));
    }

    /**
     * Lists every structure the server can see and which category, if any, it matched.
     *
     * <p>Exists because "my buildings do not show up" has exactly three causes and this separates
     * them in one command: the structure was never saved where the game can find it (it is missing
     * from the list entirely), it was saved under a name whose first path segment is not a category
     * (it appears as "no category"), or it is fine and the problem is on the client.
     */
    private static int buildings(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<Identifier> all = source.getServer().getStructureManager().listTemplates().sorted().toList();

        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No structures at all. Nothing has been saved with a structure block in this world."), false);
            return 0;
        }

        // Match the same admission rule as the network catalog: normal mod assets plus the narrow
        // player-authored minecraft:soldiers/... and minecraft:villagers/... migration trees.
        List<Identifier> eligible = all.stream()
                .filter(id -> ModPayloads.categoryOf(id) != null)
                .toList();
        Map<String, List<Identifier>> catalog = ModPayloads.buildCatalog(eligible);
        int slots = catalog.values().stream().mapToInt(List::size).sum();

        source.sendSuccess(() -> Component.literal(
                        all.size() + " structures visible to the server, " + eligible.size()
                        + " eligible for the RTS catalog -> " + catalog.size()
                        + " categories, " + slots + " tray slots (upgrade levels collapse)."), false);

        catalog.forEach((category, buildings) -> {
            source.sendSuccess(() -> Component.literal("  " + category + ":"), false);
            for (Identifier id : buildings) {
                source.sendSuccess(() -> Component.literal(
                        "    " + ModPayloads.buildingOf(id) + "   (" + id + ")"), false);
            }
        });

        for (Identifier id : all) {
            if (ModPayloads.categoryOf(id) == null) {
                source.sendSuccess(() -> Component.literal(
                        "  " + id + "  ->  no category; needs '<category>/<name>'"), false);
            }
        }
        if (eligible.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No eligible structures. Use '" + ForgottenRealmsRTS.MOD_ID
                            + ":<category>/<building>' or a player-authored "
                            + "'minecraft:villagers/...' / 'minecraft:soldiers/...' tree."), false);
        }
        return slots;
    }

    private static int activate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        GameType current = player.gameMode.getGameModeForPlayer();
        if (current != GameType.SPECTATOR) {
            RtsMode.setPreviousGameType(player, current);
        }
        RtsMode.setActive(player, true);
        RtsEconomy.migrateProgression(player);
        int scrubbedMarkers = RtsWorld.sanitizeOwnedBuildings(player.level(), player.getUUID());
        if (scrubbedMarkers > 0) {
            ForgottenRealmsRTS.LOGGER.info("Removed {} technical structure markers while activating {}'s realm.",
                    scrubbedMarkers, player.getName().getString());
        }
        player.removeAllEffects();
        ForgottenRealmsRTS.enforceObserverState(player);

        context.getSource().sendSuccess(() -> Component.literal("RTS mode activated."), false);
        return 1;
    }

    private static int deactivate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        ForgottenRealmsRTS.releaseObserverState(player);

        context.getSource().sendSuccess(() -> Component.literal("RTS mode deactivated."), false);
        return 1;
    }

    private static int startEvent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        RtsMoons.Moon moon = parseMoon(context.getArgument("moon", String.class));
        if (moon == RtsMoons.Moon.NONE) {
            source.sendFailure(Component.literal("Unknown moon. Use golden-moon, blood-moon, or blue-moon."));
            return 0;
        }
        if (!RtsCivilization.isFounded(player)) {
            source.sendFailure(Component.literal("Found your civilization before starting a moon event."));
            return 0;
        }
        if (RtsInvasion.forcedMoon(player) != RtsMoons.Moon.NONE) {
            source.sendFailure(Component.literal("A moon event is already approaching or active."));
            return 0;
        }
        if (!RtsInvasion.startForcedMoon(player, moon)) {
            source.sendFailure(Component.literal("That moon event could not be started right now."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(moonLabel(moon)
                + " is approaching. The clock is accelerating toward night."), false);
        return 1;
    }

    private static int endEvent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        RtsMoons.Moon moon = parseMoon(context.getArgument("moon", String.class));
        if (moon == RtsMoons.Moon.NONE) {
            source.sendFailure(Component.literal("Unknown moon. Use golden-moon, blood-moon, or blue-moon."));
            return 0;
        }
        if (!RtsInvasion.isNight(player)) {
            source.sendFailure(Component.literal("A moon event can only end during night."));
            return 0;
        }
        if (RtsInvasion.forcedMoon(player) != moon) {
            source.sendFailure(Component.literal("That moon is not active for this civilization."));
            return 0;
        }
        if (!RtsInvasion.endForcedMoon(player, moon)) {
            source.sendFailure(Component.literal("That moon event could not be ended right now."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(moonLabel(moon) + " ended."), false);
        return 1;
    }

    private static RtsMoons.Moon parseMoon(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "golden", "golden-moon" -> RtsMoons.Moon.GOLDEN;
            case "blood", "blood-moon" -> RtsMoons.Moon.BLOOD;
            case "blue", "blue-moon", "slumber", "slumber-moon" -> RtsMoons.Moon.BLUE;
            default -> RtsMoons.Moon.NONE;
        };
    }

    private static String moonLabel(RtsMoons.Moon moon) {
        return switch (moon) {
            case GOLDEN -> "Golden Moon";
            case BLOOD -> "Blood Moon";
            case BLUE -> "Slumber Moon";
            default -> "Moon";
        };
    }
}
