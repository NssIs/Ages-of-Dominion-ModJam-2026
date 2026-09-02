package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsEntities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;

/** The tactical palette used consistently by through-wall markers, selection, and health bars. */
public final class RtsUnitOverlayColors {
    public static final int ENEMY = 0xFFE04455;
    public static final int SOLDIER = 0xFF4D8DFF;
    public static final int WORKER = 0xFF35D875;
    public static final int SELECTED = 0xFFF4D35E;

    private RtsUnitOverlayColors() {
    }

    public static int accent(Entity entity) {
        if (entity instanceof IronGolem) {
            return 0xFFE0E0E0;
        }
        if (RtsEntities.isEnemyUnit(entity)) {
            return ENEMY;
        }
        if (RtsEntities.isAlliedCombatUnit(entity)) {
            return SOLDIER;
        }
        return WORKER;
    }

    /**
     * Returns the outline colour for a render state. Render-state events expose the entity type
     * rather than the live entity, so keep this mapping beside the normal overlay palette.
     */
    public static int outlineColor(EntityType<?> entityType) {
        if (entityType == null) {
            return 0;
        }
        if (entityType == RtsEntities.FALLEN_SOLDIER.get()
                || entityType == RtsEntities.SKELETAL_ARCHER.get()
                || entityType == RtsEntities.SAMURAI_ZOMBIE.get()
                || entityType == RtsEntities.FALLEN_KNIGHT.get()
                || entityType == RtsEntities.FALLEN_BRUTE.get()) {
            return ENEMY;
        }
        if (entityType == RtsEntities.RTS_VILLAGER.get()) {
            return WORKER;
        }
        if (entityType == RtsEntities.RTS_SOLDIER.get()
                || entityType == RtsEntities.RTS_ARCHER.get()
                || entityType == RtsEntities.RTS_SPEARMAN.get()
                || entityType == RtsEntities.RTS_CROSSBOWMAN.get()) {
            return SOLDIER;
        }
        return 0;
    }

    public static int track(Entity entity) {
        if (entity instanceof IronGolem) {
            return 0xFF4A4A4A;
        }
        if (RtsEntities.isEnemyUnit(entity)) {
            return 0xFF541B22;
        }
        if (RtsEntities.isAlliedCombatUnit(entity)) {
            return 0xFF172E63;
        }
        return 0xFF174E31;
    }
}
