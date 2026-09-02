package com.hyrrx.forgottenrealmsrts.entity;

import com.hyrrx.forgottenrealmsrts.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class RtsSoldierEntity extends PathfinderMob implements RtsRealmDefender {
   public static final int VARIANT_BLUE = 0;
   public static final int VARIANT_CRIMSON = 1;
   public static final int VARIANT_GREEN = 2;
   public static final int VARIANT_KNIGHT = 3;
   public static final int VARIANT_OCHRE_GUARD = 4;
   public static final int MAN_AT_ARMS_VARIANTS = 3;
   public static final int VARIANT_COUNT = 5;
   private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(RtsSoldierEntity.class, EntityDataSerializers.BYTE);
   private boolean variantAssigned;

   public RtsSoldierEntity(EntityType<? extends RtsSoldierEntity> type, Level level) {
      super(type, level);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
      this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
      this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
   }

   public static Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 40.0)
         .add(Attributes.MOVEMENT_SPEED, 0.28)
         .add(Attributes.ATTACK_DAMAGE, 8.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
         .add(Attributes.FOLLOW_RANGE, 24.0)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   @Override
   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_VARIANT, (byte) VARIANT_BLUE);
   }

   public int getVariant() {
      return RtsEntityVariants.unsignedByte(this.entityData.get(DATA_VARIANT), VARIANT_COUNT);
   }

   public void setVariant(int variant) {
      this.entityData.set(DATA_VARIANT, (byte) RtsEntityVariants.clamp(variant, VARIANT_COUNT));
      this.variantAssigned = true;
      this.applyVariantStats();
   }

   public boolean isKnight() {
      return this.getVariant() == 3;
   }

   private void applyVariantStats() {
      float currentHealth = this.getHealth();
      double maxHealth = 40.0;
      double movementSpeed = 0.28;
      double attackDamage = 8.0;
      double knockbackResistance = 0.5;

      if (this.isKnight()) {
         maxHealth = 60.0;
         movementSpeed = 0.25;
         attackDamage = 11.0;
         knockbackResistance = 0.8;
      } else if (this.getVariant() == VARIANT_OCHRE_GUARD) {
         maxHealth = 44.0;
         movementSpeed = 0.27;
         attackDamage = 8.5;
         knockbackResistance = 0.55;
      }

      this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
      this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(movementSpeed);
      this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attackDamage);
      this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(knockbackResistance);
      this.setHealth(Math.min(currentHealth, this.getMaxHealth()));
   }

   @Override
   public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData spawnData) {
      if (!this.variantAssigned && reason != EntitySpawnReason.LOAD) {
         this.setVariant(RtsEntityVariants.random(level.getRandom(), MAN_AT_ARMS_VARIANTS));
      }

      this.applyVariantStats();
      if (reason != EntitySpawnReason.LOAD) {
         this.setHealth(this.getMaxHealth());
      }

      return super.finalizeSpawn(level, difficulty, reason, spawnData);
   }

   @Override
   public boolean removeWhenFarAway(double distanceToClosestPlayer) {
      return false;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return (SoundEvent)ModSounds.SOLDIER_AMBIENT.get();
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent)ModSounds.SOLDIER_HURT.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return (SoundEvent)ModSounds.SOLDIER_DEATH.get();
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState blockState) {
      this.playSound((SoundEvent)ModSounds.SOLDIER_STEP.get(), 0.9F, 1.0F);
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
      this.applyVariantStats();
   }
}
