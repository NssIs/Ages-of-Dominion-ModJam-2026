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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A durable ranged defender intended for the barracks training route. */
public final class RtsCrossbowmanEntity extends PathfinderMob implements RangedAttackMob, RtsRealmDefender {
   public static final int VARIANT_BLUE = 0;
   public static final int VARIANT_CRIMSON = 1;
   public static final int VARIANT_GREEN = 2;
   public static final int VARIANT_COUNT = 3;
   private static final double PROJECTILE_DAMAGE = 6.0D;
   private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(
      RtsCrossbowmanEntity.class, EntityDataSerializers.BYTE
   );
   private boolean variantAssigned;

   public RtsCrossbowmanEntity(EntityType<? extends RtsCrossbowmanEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_VARIANT, (byte) VARIANT_BLUE);
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

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(2, new RangedAttackGoal(this, 0.85, 40, 70, 20.0F));
      this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
      this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
   }

   public static Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 28.0)
         .add(Attributes.MOVEMENT_SPEED, 0.23)
         .add(Attributes.ATTACK_DAMAGE, PROJECTILE_DAMAGE)
         .add(Attributes.FOLLOW_RANGE, 28.0)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   @Override
   public void performRangedAttack(LivingEntity target, float power) {
      Arrow arrow = new Arrow(this.level(), this, new ItemStack(Items.ARROW), new ItemStack(Items.CROSSBOW));
      arrow.setBaseDamage(PROJECTILE_DAMAGE);
      double dx = target.getX() - this.getX();
      double dy = target.getY(0.3333) - arrow.getY();
      double dz = target.getZ() - this.getZ();
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.55F, 4.0F);
      this.playSound((SoundEvent) ModSounds.RANGED_SHOOT.get(), 1.0F,
         0.78F + this.getRandom().nextFloat() * 0.12F);
      this.level().addFreshEntity(arrow);
   }

   @Override
   public boolean removeWhenFarAway(double distanceToClosestPlayer) {
      return false;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return (SoundEvent) ModSounds.RANGED_AMBIENT.get();
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent) ModSounds.RANGED_HURT.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return (SoundEvent) ModSounds.RANGED_DEATH.get();
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState blockState) {
      this.playSound((SoundEvent) ModSounds.RANGED_STEP.get(), 0.75F, 1.1F);
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
