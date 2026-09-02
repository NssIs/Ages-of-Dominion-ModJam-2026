package com.hyrrx.forgottenrealmsrts.entity;

import com.hyrrx.forgottenrealmsrts.RtsUnitOrders;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;

public abstract class RtsEnemyEntity extends Monster {
   private BlockPos siegeTarget;
   private long siegeBuildingId;

   protected RtsEnemyEntity(EntityType<? extends Monster> type, Level level) {
      super(type, level);
      // The invasion is a ground assault. A water route is never a useful shortcut for these mobs.
      this.setPathfindingMalus(PathType.WATER, -1.0F);
      this.setPathfindingMalus(PathType.WATER_BORDER, -1.0F);
   }

   /** Gives the enemy a persistent strategic objective instead of a one-tick movement suggestion. */
   public void setTownHallTarget(BlockPos target) {
      setSiegeTarget(0L, target);
   }

   /** Sets a deterministic building or Town Hall objective for the invasion director. */
   public void setBuildingTarget(long buildingId, BlockPos target) {
      setSiegeTarget(buildingId, target);
   }

   private void setSiegeTarget(long buildingId, BlockPos target) {
      BlockPos next = target == null ? null : target.immutable();
      if (this.siegeBuildingId != Math.max(0L, buildingId)
            || !Objects.equals(this.siegeTarget, next)) {
         this.siegeBuildingId = Math.max(0L, buildingId);
         this.siegeTarget = next;
         RtsUnitOrders.clearNavigation(this);
      }
   }

   public void clearTownHallTarget() {
      if (this.siegeTarget != null) {
         this.siegeTarget = null;
         this.siegeBuildingId = 0L;
         RtsUnitOrders.clearNavigation(this);
      }
   }

   public static boolean isRealmDefender(Entity entity) {
      return entity instanceof Player || entity instanceof RtsRealmDefender;
   }

   protected void addCommonGoals() {
      this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
      this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Mob.class, 5, false, false, (target, serverLevel) -> isRealmDefender(target)));
   }

   /** Reasserts the siege route after vanilla target goals have had their normal tick. */
   @Override
   protected void customServerAiStep(ServerLevel level) {
      super.customServerAiStep(level);
      if (this.siegeTarget == null || !this.isAlive()) {
         return;
      }

      this.setTarget(null);
      double distance = this.distanceToSqr(this.siegeTarget.getX() + 0.5D,
            this.siegeTarget.getY(), this.siegeTarget.getZ() + 0.5D);
      if (distance <= 36.0D) {
         this.getNavigation().stop();
      } else {
         RtsUnitOrders.moveToSmart(this, level, this.siegeTarget, 1.15D);
      }
   }

   public boolean removeWhenFarAway(double distanceToClosestPlayer) {
      return false;
   }

   public float getVoicePitch() {
      return super.getVoicePitch() * 0.65F;
   }

   public void die(DamageSource source) {
      if (this.level() instanceof ServerLevel server) {
         double midY = this.getY() + this.getBbHeight() * 0.5;
         server.sendParticles(ParticleTypes.SOUL, this.getX(), midY, this.getZ(), 22, 0.3, 0.5, 0.3, 0.03);
         server.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 0.2, this.getZ(), 18, 0.3, 0.2, 0.3, 0.01);
      }

      super.die(source);
   }
}
