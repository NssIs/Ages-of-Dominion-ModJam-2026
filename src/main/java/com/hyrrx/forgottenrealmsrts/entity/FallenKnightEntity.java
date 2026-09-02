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

public final class FallenKnightEntity extends RtsEnemyEntity {
   public FallenKnightEntity(EntityType<? extends Monster> type, Level level) {
      super(type, level);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
      this.addCommonGoals();
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 60.0)
         .add(Attributes.MOVEMENT_SPEED, 0.24)
         .add(Attributes.ATTACK_DAMAGE, 10.0)
         .add(Attributes.FOLLOW_RANGE, 44.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
         .add(Attributes.ARMOR, 8.0);
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
      this.playSound((SoundEvent) ModSounds.ECHO_STEP.get(), 1.0F, 0.7F);
   }
}
