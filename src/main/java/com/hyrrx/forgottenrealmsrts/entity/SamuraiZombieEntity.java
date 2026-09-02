package com.hyrrx.forgottenrealmsrts.entity;

import com.hyrrx.forgottenrealmsrts.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SamuraiZombieEntity extends RtsEnemyEntity {
   public SamuraiZombieEntity(EntityType<? extends Monster> type, Level level) {
      super(type, level);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05, false));
      this.addCommonGoals();
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 34.0)
         .add(Attributes.MOVEMENT_SPEED, 0.27)
         .add(Attributes.ATTACK_DAMAGE, 7.0)
         .add(Attributes.FOLLOW_RANGE, 40.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
   }

   protected SoundEvent getAmbientSound() {
      return (SoundEvent) ModSounds.ECHO_AMBIENT.get();
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent) ModSounds.ECHO_HURT.get();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent) ModSounds.ECHO_DEATH.get();
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState blockState) {
      this.playSound((SoundEvent) ModSounds.ECHO_STEP.get(), 0.95F, 0.9F);
   }
}
