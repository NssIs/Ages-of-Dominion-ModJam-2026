package com.hyrrx.forgottenrealmsrts.client;

import com.hyrrx.forgottenrealmsrts.RtsEntities;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.camera.IsometricCameraController;
import com.hyrrx.forgottenrealmsrts.client.ui.RtsHudState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/** Draws readable faction-coloured health readouts above every RTS unit. */
public final class RtsVillagerHealthBarRenderer {
    private static final double MAX_DISPLAY_DISTANCE = 192.0D;
    private static final int BAR_WIDTH = 34;
    private static final int BAR_HEIGHT = 4;
    private static final int LABEL_GAP = 1;

    private RtsVillagerHealthBarRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsVillagerHealthBarRenderer::onRenderGui);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.level == null || minecraft.player == null
                || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        boolean showHealthBars = IsometricCameraController.shouldShowHealthBars();
        List<LivingEntity> selectedUnits = RtsHudState.selectedUnits();
        LivingEntity target = RtsHudState.selectedTarget();

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity unit) || !unit.isAlive()
                    || !RtsEntities.isRtsUnit(unit)
                    || unit.distanceToSqr(camera.position()) > MAX_DISPLAY_DISTANCE * MAX_DISPLAY_DISTANCE) {
                continue;
            }
            if (unit instanceof com.hyrrx.forgottenrealmsrts.RtsVillagerEntity worker
                    && worker.isMineWorker()) {
                continue;
            }

            boolean isSelected = selectedUnits.contains(unit) || unit == target;
            boolean hitFlash = RtsHudState.isTargetHitFlash(unit);
            boolean recentlyDamaged = recentlyDamaged(unit);
            boolean inFight = recentlyDamaged || isActivelyFighting(unit);
            if (!showHealthBars) {
                continue;
            }
            // Idle units keep their model unobscured. Selection, a recent hit, or active combat
            // brings the compact bar back; only the selected/targeted unit gets the numeric label.
            if (!isSelected && !hitFlash && !inFight) {
                continue;
            }

            RtsUnitScreenProjection.ScreenPoint point = RtsUnitScreenProjection.project(minecraft,
                    unit.position().add(0.0D, unit.getBbHeight() + 0.62D, 0.0D));
            if (point == null) {
                continue;
            }

            boolean allied = RtsEntities.isAlliedUnit(unit);
            boolean military = allied && RtsEntities.isAlliedCombatUnit(unit);
            boolean styled = RtsEntities.isStyledRtsUnit(unit);
            int width = hitFlash ? BAR_WIDTH + 4 : BAR_WIDTH;
            int left = point.x() - width / 2;
            int top = point.y();
            int right = left + width;
            int bottom = top + BAR_HEIGHT;
            int border = isSelected || hitFlash
                    ? 0xFFF4D35E
                    : !styled ? 0xFFB8B8B8 : allied ? 0xFFD8F4C0 : 0xFFFFB3A9;
            int track = styled ? RtsUnitOverlayColors.track(unit) : 0xFF4A4A4A;
            int fill = styled ? RtsUnitOverlayColors.accent(unit) : 0xFFB0B0B0;
            int highlight = !styled ? 0xFFE2E2E2
                    : !allied ? 0xFFFFA9A9 : military ? 0xFFB8D4FF : 0xFFB9FFD0;

            // Dark outline + dark faction track makes a full-health bar readable over grass, stone,
            // buildings, and the bright green cursor-highlighted terrain.
            graphics.fill(left - 1, top - 1, right + 1, bottom + 1, border);
            graphics.fill(left, top, right, bottom, track);
            int filled = Math.round((width - 2) * healthFraction(unit));
            if (filled > 0) {
                graphics.fill(left + 1, top + 1, left + 1 + filled, bottom - 1, fill);
                graphics.fill(left + 1, top + 1, left + 1 + filled, top + 2,
                        highlight);
            }

            if (isSelected || hitFlash || recentlyDamaged) {
                String health = RtsHudState.formatHealth(unit.getHealth()) + " / "
                        + RtsHudState.formatHealth(unit.getMaxHealth());
                int labelWidth = font.width(health);
                int labelX = point.x() - labelWidth / 2;
                int labelY = top - font.lineHeight - LABEL_GAP;
                if (labelY < 1) {
                    labelY = bottom + LABEL_GAP;
                }
                graphics.fill(labelX - 2, labelY - 1, labelX + labelWidth + 2,
                        labelY + font.lineHeight + 1, 0xB9141820);
                graphics.text(font, health, labelX, labelY,
                        hitFlash ? 0xFFFFF1A8
                                : !styled ? 0xFFE4E4E4
                                : !allied ? 0xFFFFD8D2
                                : military ? 0xFFDDE8FF : 0xFFE9FFD7);
            }

            if (hitFlash) {
                String damage = "-0.5";
                graphics.text(font, damage, right + 4, top - 1, 0xFFFFE28A);
            }
        }
    }

    private static boolean recentlyDamaged(LivingEntity unit) {
        if (unit.hurtTime > 0) {
            return true;
        }
        LivingEntity attacker = unit.getLastHurtByMob();
        if (attacker == null) {
            return false;
        }
        int age = unit.tickCount - unit.getLastHurtByMobTimestamp();
        return age >= 0 && age <= 80;
    }

    private static boolean isActivelyFighting(LivingEntity unit) {
        if (!(unit instanceof Mob mob)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        return mob.isAggressive() || target != null && target.isAlive();
    }

    private static float healthFraction(LivingEntity unit) {
        float maxHealth = unit.getMaxHealth();
        if (maxHealth <= 0.0F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, unit.getHealth() / maxHealth));
    }

}
