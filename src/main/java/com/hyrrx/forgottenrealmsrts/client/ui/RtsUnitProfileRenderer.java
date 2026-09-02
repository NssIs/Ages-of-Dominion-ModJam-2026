package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.RtsVillagerEntity;
import com.hyrrx.forgottenrealmsrts.client.RtsMobTextures;
import com.hyrrx.forgottenrealmsrts.client.RtsUnitOverlayColors;
import com.hyrrx.forgottenrealmsrts.entity.RtsArcherEntity;
import com.hyrrx.forgottenrealmsrts.entity.RtsCrossbowmanEntity;
import com.hyrrx.forgottenrealmsrts.entity.RtsSoldierEntity;
import com.hyrrx.forgottenrealmsrts.entity.RtsSpearmanEntity;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;

/**
 * A small live pixel portrait for the selected unit.
 *
 * <p>The portrait is assembled from the unit's own skin atlas, so a farmer, miner, soldier, and
 * archer keep their actual authored appearance. A tiny bob, step, or attack slash is added from
 * the current client state so this is a status view rather than a dead screenshot.</p>
 *
 * <p>Every rectangle comes from {@link RtsUnitPortraitRegions}, which is generated from the mesh
 * in {@code client/RtsUnitLayers.java}. Hard-coded coordinates had already drifted away from that
 * mesh: the left arm and left leg were being read from the 64x64 player layout, but the humanoid
 * mesh mirrors both limbs onto their right-hand rectangles, so those two blits were sampling
 * unpainted pixels.</p>
 */
public final class RtsUnitProfileRenderer {

    private RtsUnitProfileRenderer() {
    }

    public static int draw(GuiGraphicsExtractor graphics, LivingEntity unit,
                           int x, int y, int width, int height) {
        int faction = RtsUnitOverlayColors.accent(unit);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, faction);
        graphics.fill(x, y, x + width, y + height, 0xFF17232C);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF2A3C45);

        Identifier texture = textureFor(unit);
        RtsUnitPortraitRegions.Portrait portrait = portraitFor(unit);
        String activity = activity(unit);
        boolean walking = "Walking".equals(activity);
        boolean attacking = "Attacking".equals(activity);
        long now = Util.getMillis();
        int bob = walking ? Math.round((float) Math.sin(now * 0.022D)) : 0;
        int step = walking && (now / 140L) % 2L == 0L ? 1 : -1;
        int centre = x + width / 2;
        int headWidth = Math.min(22, width - 8);
        int headHeight = headWidth;
        int headX = centre - headWidth / 2;

        int sheet = portrait.sheet();
        RtsUnitPortraitRegions.Region torso = portrait.torso().region();
        // The torso slot is 16x20 on screen for the 8x12 body box, so the two axes scale
        // differently. Keep those factors and apply them to whatever part is actually on top.
        float torsoScaleX = 16.0F / 8.0F;
        float torsoScaleY = 20.0F / 12.0F;
        int torsoX = centre - 8 + Math.round(portrait.torso().offsetX() * torsoScaleX);
        int torsoY = y + 22 + Math.round(portrait.torso().offsetY() * torsoScaleY);

        RtsUnitPortraitRegions.Region headgear = portrait.headgear().region();
        float headScale = headWidth / 8.0F;

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, bob);
        blitRegion(graphics, texture, sheet, torsoX, torsoY,
                Math.round(torso.width() * torsoScaleX), Math.round(torso.height() * torsoScaleY),
                torso);
        blitRegion(graphics, texture, sheet, x + 4, y + 22, 8, 20, portrait.arm());
        blitRegion(graphics, texture, sheet, x + width - 12, y + 22, 8, 20, portrait.arm());
        blitRegion(graphics, texture, sheet, centre - 8 - step, y + 40, 8, 15, portrait.leg());
        blitRegion(graphics, texture, sheet, centre + step, y + 40, 8, 15, portrait.leg());
        blitRegion(graphics, texture, sheet, headX, y + 4, headWidth, headHeight, portrait.head());
        // The headgear gives the portrait the same helmet or hood silhouette as the in-world mob.
        // Its offset is relative to the head box, so a visor lands on the eyes and a crown above
        // the skull instead of both being stretched over the whole face.
        blitRegion(graphics, texture, sheet,
                headX + Math.round(portrait.headgear().offsetX() * headScale),
                y + 4 + Math.round(portrait.headgear().offsetY() * headScale),
                Math.round(headgear.width() * headScale),
                Math.round(headgear.height() * headScale),
                headgear);
        graphics.pose().popMatrix();

        if (attacking) {
            int slash = (int) ((now / 110L) % 2L);
            int armX = x + width - 7 - slash * 3;
            graphics.fill(armX, y + 26, armX + 3, y + 36, 0xFFF2D47C);
            graphics.fill(armX - 4, y + 24 + slash * 3, armX + 2,
                    y + 26 + slash * 3, 0xFFFFF1B0);
        }

        graphics.fill(x + width - 7, y + 4, x + width - 3, y + 8, faction);
        return width;
    }

    /** Short, live status text for both the profile and the selected-unit readout. */
    public static String activity(LivingEntity unit) {
        if (unit instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (mob.isAggressive() || target != null && target.isAlive()) {
                return "Attacking";
            }
        }
        if (unit instanceof RtsVillagerEntity villager) {
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.GATHERING_WOOD) {
                return villager.getNavigation().isDone() ? "Chopping wood" : "Walking to trees";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.RETURNING_WOOD) {
                return "Returning wood";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.GOING_TO_MINE) {
                return "Walking to mine";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.MINING) {
                return "Mining ore";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.RETURNING_MINE) {
                return "Returning to town";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.GOING_TO_FARM) {
                return "Walking to farm";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.FARMING) {
                return "Tending the farm";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.RETURNING_FARM) {
                return "Returning to town";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.GOING_TO_BUILD) {
                return "Walking to build site";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.BUILDING) {
                return "Building";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.GOING_TO_REPAIR) {
                return "Walking to repair site";
            }
            if (villager.getWorkState() == RtsVillagerEntity.WorkState.REPAIRING) {
                return "Repairing building";
            }
            if (villager.getDeltaMovement().horizontalDistanceSqr() > 0.0004D
                    || !villager.getNavigation().isDone()) {
                return "Walking";
            }
            // The variant is a role/appearance, not an autonomous farm or mine simulation yet.
            // Do not tell the player an idle villager is gathering something it is not gathering.
            return "Awaiting orders";
        }
        if (unit.getDeltaMovement().horizontalDistanceSqr() > 0.0004D
                || unit instanceof PathfinderMob pathfinder
                && !pathfinder.getNavigation().isDone()) {
            return "Walking";
        }
        return "Holding position";
    }

    private static RtsUnitPortraitRegions.Portrait portraitFor(LivingEntity unit) {
        if (unit instanceof RtsSoldierEntity) {
            return RtsUnitPortraitRegions.SOLDIER;
        }
        if (unit instanceof RtsArcherEntity) {
            return RtsUnitPortraitRegions.ARCHER;
        }
        if (unit instanceof RtsCrossbowmanEntity) {
            return RtsUnitPortraitRegions.CROSSBOWMAN;
        }
        if (unit instanceof RtsSpearmanEntity) {
            return RtsUnitPortraitRegions.SPEARMAN;
        }
        // Peasants and anything else fall back to the peasant sheet, which is what
        // textureFor returns for them too.
        return RtsUnitPortraitRegions.PEASANT;
    }

    private static Identifier textureFor(LivingEntity unit) {
        if (unit instanceof RtsVillagerEntity villager) {
            return RtsMobTextures.texture(switch (villager.getVariant()) {
                case RtsVillagerEntity.VARIANT_MINER -> "peasant_miner";
                case RtsVillagerEntity.VARIANT_WOODCUTTER -> "peasant_woodcutter";
                case RtsVillagerEntity.VARIANT_BUILDER -> "peasant_builder";
                case RtsVillagerEntity.VARIANT_FORAGER -> "peasant_forager";
                default -> "peasant_farmer";
            });
        }
        if (unit instanceof RtsSoldierEntity soldier) {
            if (soldier.isKnight()) {
                return RtsMobTextures.texture("soldier_knight");
            }
            return RtsMobTextures.texture(switch (soldier.getVariant()) {
                case RtsSoldierEntity.VARIANT_CRIMSON -> "soldier_manatarms_crimson";
                case RtsSoldierEntity.VARIANT_GREEN -> "soldier_manatarms_green";
                default -> "soldier_manatarms_blue";
            });
        }
        if (unit instanceof RtsArcherEntity archer) {
            return RtsMobTextures.texture(switch (archer.getVariant()) {
                case RtsArcherEntity.VARIANT_CRIMSON -> "archer_crimson";
                case RtsArcherEntity.VARIANT_GREEN -> "archer_green";
                default -> "archer";
            });
        }
        if (unit instanceof RtsCrossbowmanEntity crossbowman) {
            return RtsMobTextures.texture(switch (crossbowman.getVariant()) {
                case RtsCrossbowmanEntity.VARIANT_CRIMSON -> "crossbowman_crimson";
                case RtsCrossbowmanEntity.VARIANT_GREEN -> "crossbowman_green";
                default -> "crossbowman_blue";
            });
        }
        if (unit instanceof RtsSpearmanEntity spearman) {
            return RtsMobTextures.texture(switch (spearman.getVariant()) {
                case RtsSpearmanEntity.VARIANT_CRIMSON -> "spearman_crimson";
                case RtsSpearmanEntity.VARIANT_GREEN -> "spearman_green";
                default -> "spearman";
            });
        }
        return RtsMobTextures.FALLBACK;
    }

    private static void blitRegion(GuiGraphicsExtractor graphics, Identifier texture, int sheet,
                                   int x, int y, int width, int height,
                                   RtsUnitPortraitRegions.Region source) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, source.u(), source.v(),
                width, height, source.width(), source.height(), sheet, sheet);
    }
}
