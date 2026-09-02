package com.hyrrx.forgottenrealmsrts.entity;

import com.hyrrx.forgottenrealmsrts.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class FallenSoldierEntity extends RtsEnemyEntity {
   public static final int VARIANT_RUSTED = 0;
   public static final int VARIANT_ASHEN = 1;
   public static final int VARIANT_COUNT = 2;
   private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(
      FallenSoldierEntity.class, EntityDataSerializers.BYTE
   );
   private boolean variantAssigned;

   public FallenSoldierEntity(EntityType<? extends Monster> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_VARIANT, (byte) VARIANT_RUSTED);
   }

   public int getVariant() {
      return RtsEntityVariants.unsignedByte(this.entityData.get(DATA_VARIANT), VARIANT_COUNT);
   }

   public void setVariant(int variant) {
      this.entityData.set(DATA_VARIANT, (byte) RtsEntityVariants.clamp(variant, VARIANT_COUNT));
      this.variantAssigned = true;
   }

   @Override
   public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData spawnData
   ) {
      if (!this.variantAssigned && reason != EntitySpawnReason.LOAD) {
         this.setVariant(RtsEntityVariants.random(level.getRandom(), VARIANT_COUNT));
      }

      return super.finalizeSpawn(level, difficulty, reason, spawnData);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
      this.addCommonGoals();
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 22.0)
         .add(Attributes.MOVEMENT_SPEED, 0.25)
         .add(Attributes.ATTACK_DAMAGE, 4.0)
         .add(Attributes.FOLLOW_RANGE, 40.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return (SoundEvent) ModSounds.ECHO_AMBIENT.get();
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent) ModSounds.ECHO_HURT.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return (SoundEvent) ModSounds.ECHO_DEATH.get();
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState blockState) {
      this.playSound((SoundEvent) ModSounds.ECHO_STEP.get(), 0.9F, 0.8F);
   }

   @Override
   protected void addAdditionalSaveData(ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.putInt("Variant", this.getVariant());
   }

   @Override
   protected void readAdditionalSaveData(ValueInput input) {
      super.readAdditionalSaveData(input);
      this.setVariant(RtsEntityVariants.read(input, "Variant", VARIANT_COUNT));
   }
}
