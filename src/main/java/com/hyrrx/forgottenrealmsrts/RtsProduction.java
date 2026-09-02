package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.network.ModPayloads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The realm's economy engine: completed economic buildings add their {@code produces} income to the
 * owner's stockpiles on a slow cycle. The Town Hall is deliberately excluded; physical workers are
 * the route to the opening wood supply.
 *
 * <p>Upgrading a non-Town-Hall building multiplies its output by its level. Workers now use the
 * separate physical woodcutting loop in {@link RtsWorkerOrders} instead of being an invisible income
 * multiplier.
 */
public final class RtsProduction {
    /** Ticks between production cycles. 40 ticks is two seconds at 20 tps. */
    public static final int CYCLE_TICKS = 40;
    /** Stockpiles never grow past this, so a long idle session cannot overflow the HUD columns. */
    private static final int STOCKPILE_CAP = 999_999;
    private RtsProduction() {
    }

    /** Called every player tick; does real work only once per {@link #CYCLE_TICKS}. */
    public static void tick(ServerPlayer player) {
        if (!RtsCivilization.isFounded(player)
                || RtsBattle.outcome(player) != RtsBattle.OUTCOME_ONGOING
                || player.tickCount % CYCLE_TICKS != 0) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        int[] income = new int[Resource.COUNT];
        for (RtsBuildingStore.Entry entry : RtsBuildingStore.get(level).entries()) {
            if (!entry.owner().equals(player.getUUID())) {
                continue;
            }
            BuildingCosts.Definition definition = BuildingCosts.get(entry.structure());
            if (definition.townHall()) {
                continue;
            }
            int level_ = Math.max(1, ModPayloads.levelOf(entry.structure()));
            int[] produces = definition.produces();
            for (int index = 0; index < Resource.COUNT; index++) {
                income[index] += produces[index] * level_;
            }
        }

        // Special moons bend the economy: a Golden Moon makes everything run rich, a Slumber Moon
        // slows it while the realm rests.
        double moonFactor = switch (RtsInvasion.moonFor(player, level.getOverworldClockTime() / 24000L)) {
            case GOLDEN -> 2.0D;
            case BLUE -> 0.5D;
            default -> 1.0D;
        };
        // Recovered relics permanently enrich the economy.
        moonFactor *= 1.0D + RtsCivilization.relics(player) * RtsCivilization.RELIC_PRODUCTION_BONUS;

        for (Resource resource : Resource.VALUES) {
            int add = (int) Math.round(income[resource.ordinal()] * moonFactor);
            if (add > 0) {
                int next = Math.min(STOCKPILE_CAP, RtsEconomy.stock(player, resource) + add);
                RtsEconomy.setStock(player, resource, next);
            }
        }
    }
}
