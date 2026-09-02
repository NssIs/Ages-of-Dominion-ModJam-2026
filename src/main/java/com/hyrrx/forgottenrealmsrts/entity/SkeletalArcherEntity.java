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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class SkeletalArcherEntity extends RtsEnemyEntity implements RangedAttackMob {
   public static final int VARIANT_BONE = 0;
   public static final int VARIANT_IRON = 1;
   public static final int VARIANT_COUNT = 2;
   private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(
      SkeletalArcherEntity.class, EntityDataSerializers.BYTE
   );
   private boolean variantAssigned;

   public SkeletalArcherEntity(EntityType<? extends Monster> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_VARIANT, (byte) VARIANT_BONE);
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
      this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 20, 40, 15.0F));
      this.addCommonGoals();
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 16.0)
         .add(Attributes.MOVEMENT_SPEED, 0.26)
         .add(Attributes.ATTACK_DAMAGE, 2.0)
         .add(Attributes.FOLLOW_RANGE, 44.0);
   }

   @Override
   public void performRangedAttack(LivingEntity target, float power) {
      Arrow arrow = new Arrow(this.level(), this, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
      double dx = target.getX() - this.getX();
      double dy = target.getY(0.3333) - arrow.getY();
      double dz = target.getZ() - this.getZ();
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.6F, 8.0F);
      this.playSound((SoundEvent) ModSounds.RANGED_SHOOT.get(), 1.0F,
         0.85F + this.getRandom().nextFloat() * 0.15F);
      this.level().addFreshEntity(arrow);
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
      this.playSound((SoundEvent) ModSounds.ECHO_STEP.get(), 0.8F, 1.0F);
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
