package com.hyrrx.forgottenrealmsrts.entity;

import com.hyrrx.forgottenrealmsrts.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A slow, heavy echo reserved for later invasion waves. */
public final class FallenBruteEntity extends RtsEnemyEntity {
   public FallenBruteEntity(EntityType<? extends Monster> type, Level level) {
      super(type, level);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.8, true));
      this.addCommonGoals();
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 80.0)
         .add(Attributes.MOVEMENT_SPEED, 0.16)
         .add(Attributes.ATTACK_DAMAGE, 9.0)
         .add(Attributes.FOLLOW_RANGE, 44.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
         .add(Attributes.ARMOR, 8.0);
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return (SoundEvent) ModSounds.BRUTE_AMBIENT.get();
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent) ModSounds.BRUTE_HURT.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return (SoundEvent) ModSounds.BRUTE_DEATH.get();
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState blockState) {
      this.playSound((SoundEvent) ModSounds.BRUTE_STEP.get(), 1.2F, 0.65F);
   }
}
