package com.hyrrx.forgottenrealmsrts;

import com.hyrrx.forgottenrealmsrts.entity.RtsEntityVariants;
import com.hyrrx.forgottenrealmsrts.entity.RtsRealmDefender;
import com.hyrrx.forgottenrealmsrts.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The first worker created by a player's Town Hall. */
public final class RtsVillagerEntity extends Villager implements RtsRealmDefender {
    public static final int VARIANT_FARMER = 0;
    public static final int VARIANT_MINER = 1;
    public static final int VARIANT_WOODCUTTER = 2;
    public static final int VARIANT_BUILDER = 3;
    public static final int VARIANT_FORAGER = 4;
    public static final int VARIANT_COUNT = 5;
    private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(
        RtsVillagerEntity.class, EntityDataSerializers.BYTE
    );
    private static final EntityDataAccessor<Byte> DATA_WORK_STATE = SynchedEntityData.defineId(
        RtsVillagerEntity.class, EntityDataSerializers.BYTE
    );
    private static final EntityDataAccessor<Integer> DATA_CARRIED_WOOD = SynchedEntityData.defineId(
        RtsVillagerEntity.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Long> DATA_MINE_BUILDING_ID = SynchedEntityData.defineId(
        RtsVillagerEntity.class, EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<Long> DATA_FARM_BUILDING_ID = SynchedEntityData.defineId(
        RtsVillagerEntity.class, EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<Long> DATA_CONSTRUCTION_BUILDING_ID = SynchedEntityData.defineId(
        RtsVillagerEntity.class, EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<Long> DATA_REPAIR_BUILDING_ID = SynchedEntityData.defineId(
        RtsVillagerEntity.class, EntityDataSerializers.LONG
    );
    private boolean variantAssigned;
    /** True only when this worker added the invisibility effect for mine duty. */
    private boolean mineInvisibilityApplied;
    /** Persistent specialist role; an assigned woodcutter may wait at the hall between groves. */
    private boolean woodcutterAssigned;
    /** A builder designation survives cancellation and is the pool used by dawn auto-repair. */
    private boolean builderDesignated;
    /** Automatic repair pauses at night; manual repair is allowed at any time. */
    private boolean repairAutomatic;
    private BlockPos woodTarget;
    /** Root log for the tree this worker reserved; this survives a relog and prevents overstaffing. */
    private BlockPos woodWorksite;
    /** Temporary scaffold column used to reach a high log. It is removed when the job ends. */
    private BlockPos scaffoldingBase;
    private int scaffoldingHeight;

    public enum WorkState {
        IDLE,
        GATHERING_WOOD,
        RETURNING_WOOD,
        GOING_TO_MINE,
        MINING,
        RETURNING_MINE,
        GOING_TO_FARM,
        FARMING,
        RETURNING_FARM,
        GOING_TO_BUILD,
        BUILDING,
        GOING_TO_REPAIR,
        REPAIRING
    }

    public RtsVillagerEntity(EntityType<? extends Villager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, (byte) VARIANT_FARMER);
        builder.define(DATA_WORK_STATE, (byte) WorkState.IDLE.ordinal());
        builder.define(DATA_CARRIED_WOOD, 0);
        builder.define(DATA_MINE_BUILDING_ID, 0L);
        builder.define(DATA_FARM_BUILDING_ID, 0L);
        builder.define(DATA_CONSTRUCTION_BUILDING_ID, 0L);
        builder.define(DATA_REPAIR_BUILDING_ID, 0L);
    }

    public int getVariant() {
        return RtsEntityVariants.unsignedByte(this.entityData.get(DATA_VARIANT), VARIANT_COUNT);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, (byte) RtsEntityVariants.clamp(variant, VARIANT_COUNT));
        this.variantAssigned = true;
    }

    public WorkState getWorkState() {
        int index = Math.floorMod(this.entityData.get(DATA_WORK_STATE), WorkState.values().length);
        return WorkState.values()[index];
    }

    public void setWorkState(WorkState state) {
        this.entityData.set(DATA_WORK_STATE, (byte) (state == null ? WorkState.IDLE.ordinal() : state.ordinal()));
    }

    public int getCarriedWood() {
        return Math.max(0, this.entityData.get(DATA_CARRIED_WOOD));
    }

    public void setCarriedWood(int amount) {
        this.entityData.set(DATA_CARRIED_WOOD, Math.max(0, amount));
    }

    public long getMineBuildingId() {
        return Math.max(0L, this.entityData.get(DATA_MINE_BUILDING_ID));
    }

    public void setMineBuildingId(long buildingId) {
        this.entityData.set(DATA_MINE_BUILDING_ID, Math.max(0L, buildingId));
    }

    public long getFarmBuildingId() {
        return Math.max(0L, this.entityData.get(DATA_FARM_BUILDING_ID));
    }

    public void setFarmBuildingId(long buildingId) {
        this.entityData.set(DATA_FARM_BUILDING_ID, Math.max(0L, buildingId));
    }

    public long getConstructionBuildingId() {
        return Math.max(0L, this.entityData.get(DATA_CONSTRUCTION_BUILDING_ID));
    }

    public void setConstructionBuildingId(long buildingId) {
        this.entityData.set(DATA_CONSTRUCTION_BUILDING_ID, Math.max(0L, buildingId));
    }

    public long getRepairBuildingId() {
        return Math.max(0L, this.entityData.get(DATA_REPAIR_BUILDING_ID));
    }

    public void setRepairBuildingId(long buildingId) {
        this.entityData.set(DATA_REPAIR_BUILDING_ID, Math.max(0L, buildingId));
    }

    public boolean isWoodcutterAssigned() {
        return woodcutterAssigned;
    }

    public void setWoodcutterAssigned(boolean assigned) {
        this.woodcutterAssigned = assigned;
    }

    public boolean isBuilderDesignated() {
        return builderDesignated;
    }

    public void setBuilderDesignated(boolean designated) {
        this.builderDesignated = designated;
    }

    public boolean isRepairAutomatic() {
        return repairAutomatic;
    }

    public void setRepairAutomatic(boolean automatic) {
        this.repairAutomatic = automatic;
    }

    public BlockPos getWoodTarget() {
        return this.woodTarget;
    }

    public void setWoodTarget(BlockPos target) {
        this.woodTarget = target == null ? null : target.immutable();
    }

    public BlockPos getWoodWorksite() {
        return this.woodWorksite;
    }

    public void setWoodWorksite(BlockPos worksite) {
        this.woodWorksite = worksite == null ? null : worksite.immutable();
    }

    public boolean hasScaffolding() {
        return this.scaffoldingBase != null && this.scaffoldingHeight > 0;
    }

    public BlockPos getScaffoldingBase() {
        return this.scaffoldingBase;
    }

    public int getScaffoldingHeight() {
        return this.scaffoldingHeight;
    }

    public BlockPos getScaffoldingTop() {
        return this.scaffoldingBase == null
                ? null
                : this.scaffoldingBase.above(Math.max(0, this.scaffoldingHeight));
    }

    /** Records a temporary scaffold column after the order system has placed it. */
    public void setScaffolding(BlockPos base, int height) {
        this.scaffoldingBase = base == null ? null : base.immutable();
        this.scaffoldingHeight = base == null ? 0 : Math.max(0, Math.min(64, height));
        if (this.scaffoldingHeight == 0) {
            this.scaffoldingBase = null;
        }
    }

    /** Removes only the scaffold blocks owned by this worker, leaving any later replacement intact. */
    public void clearScaffolding() {
        if (this.scaffoldingBase != null && this.level() instanceof ServerLevel level) {
            for (int offset = 0; offset < this.scaffoldingHeight; offset++) {
                BlockPos position = this.scaffoldingBase.above(offset);
                if (level.getBlockState(position).is(Blocks.SCAFFOLDING)) {
                    level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        this.scaffoldingBase = null;
        this.scaffoldingHeight = 0;
    }

    public boolean isWorking() {
        return getWorkState() != WorkState.IDLE;
    }

    /** Makes a worker look and read like the selected lumberjack job. */
    public void wearWoodcutterKit() {
        setVariant(VARIANT_WOODCUTTER);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
    }

    /** Makes a worker visibly read as a miner while walking to, or working inside, a mine. */
    public void wearMinerKit() {
        setVariant(VARIANT_MINER);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
    }

    /** Makes a worker visibly read as the farmer assigned to a realm farm. */
    public void wearFarmerKit() {
        setVariant(VARIANT_FARMER);
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** Makes a worker visibly read as a builder while walking to, or assembling, a structure. */
    public void wearBuilderKit() {
        setBuilderDesignated(true);
        setVariant(VARIANT_BUILDER);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
    }

    /** Applies only the effect owned by this mine assignment; pre-existing effects are untouched. */
    public void enterMine() {
        if (!hasEffect(MobEffects.INVISIBILITY)) {
            addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                    MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            mineInvisibilityApplied = true;
        }
    }

    /** Clears only the invisibility this worker added for mining, never the player's other effects. */
    public void leaveMine() {
        if (mineInvisibilityApplied) {
            removeEffect(MobEffects.INVISIBILITY);
            mineInvisibilityApplied = false;
        }
    }

    public boolean isMineWorker() {
        return getWorkState() == WorkState.GOING_TO_MINE
                || getWorkState() == WorkState.MINING
                || getWorkState() == WorkState.RETURNING_MINE;
    }

    public boolean isFarmWorker() {
        return getWorkState() == WorkState.GOING_TO_FARM
                || getWorkState() == WorkState.FARMING
                || getWorkState() == WorkState.RETURNING_FARM;
    }

    public boolean isConstructionWorker() {
        return (getWorkState() == WorkState.GOING_TO_BUILD
                || getWorkState() == WorkState.BUILDING)
                && getConstructionBuildingId() > 0L;
    }

    public boolean isRepairWorker() {
        return (getWorkState() == WorkState.GOING_TO_REPAIR
                || getWorkState() == WorkState.REPAIRING)
                && getRepairBuildingId() > 0L;
    }

    /** Cancels a wood assignment when the commander issues another order. */
    public void clearWorkAssignment() {
        leaveMine();
        clearScaffolding();
        setMineBuildingId(0L);
        setFarmBuildingId(0L);
        setConstructionBuildingId(0L);
        setRepairBuildingId(0L);
        setRepairAutomatic(false);
        setWoodcutterAssigned(false);
        setWoodTarget(null);
        setWoodWorksite(null);
        setWorkState(WorkState.IDLE);
        setCarriedWood(0);
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

    /**
     * This is an RTS worker, not a free-roaming vanilla villager. The town director supplies its
     * patrol path; disabling the villager brain prevents its job-site/wander memories from pulling
     * it off the town ring every few seconds.
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return (SoundEvent) ModSounds.PEASANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return (SoundEvent) ModSounds.PEASANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return (SoundEvent) ModSounds.PEASANT_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound((SoundEvent) ModSounds.PEASANT_STEP.get(), 0.7F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Variant", this.getVariant());
        output.putInt("WorkState", this.getWorkState().ordinal());
        output.putInt("CarriedWood", this.getCarriedWood());
        output.putLong("MineBuildingId", this.getMineBuildingId());
        output.putLong("FarmBuildingId", this.getFarmBuildingId());
        output.putLong("ConstructionBuildingId", this.getConstructionBuildingId());
        output.putLong("RepairBuildingId", this.getRepairBuildingId());
        output.putInt("MineInvisibility", this.mineInvisibilityApplied ? 1 : 0);
        output.putInt("WoodcutterAssigned", this.woodcutterAssigned ? 1 : 0);
        output.putInt("BuilderDesignated", this.builderDesignated ? 1 : 0);
        output.putInt("RepairAutomatic", this.repairAutomatic ? 1 : 0);
        if (this.woodTarget != null) {
            output.putInt("WoodTargetX", this.woodTarget.getX());
            output.putInt("WoodTargetY", this.woodTarget.getY());
            output.putInt("WoodTargetZ", this.woodTarget.getZ());
        }
        if (this.woodWorksite != null) {
            output.putInt("WoodWorksiteX", this.woodWorksite.getX());
            output.putInt("WoodWorksiteY", this.woodWorksite.getY());
            output.putInt("WoodWorksiteZ", this.woodWorksite.getZ());
        }
        if (this.hasScaffolding()) {
            output.putInt("ScaffoldBaseX", this.scaffoldingBase.getX());
            output.putInt("ScaffoldBaseY", this.scaffoldingBase.getY());
            output.putInt("ScaffoldBaseZ", this.scaffoldingBase.getZ());
            output.putInt("ScaffoldHeight", this.scaffoldingHeight);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant(RtsEntityVariants.read(input, "Variant", VARIANT_COUNT));
        this.setWorkState(WorkState.values()[Math.floorMod(
                input.getIntOr("WorkState", WorkState.IDLE.ordinal()), WorkState.values().length)]);
        this.setCarriedWood(input.getIntOr("CarriedWood", 0));
        this.setMineBuildingId(input.getLongOr("MineBuildingId", 0L));
        this.setFarmBuildingId(input.getLongOr("FarmBuildingId", 0L));
        this.setConstructionBuildingId(input.getLongOr("ConstructionBuildingId", 0L));
        this.setRepairBuildingId(input.getLongOr("RepairBuildingId", 0L));
        this.mineInvisibilityApplied = input.getIntOr("MineInvisibility", 0) != 0;
        this.woodcutterAssigned = input.getIntOr("WoodcutterAssigned", 0) != 0;
        this.builderDesignated = input.getIntOr("BuilderDesignated", 0) != 0;
        this.repairAutomatic = input.getIntOr("RepairAutomatic", 0) != 0;
        this.builderDesignated = this.builderDesignated
                || this.isConstructionWorker() || this.isRepairWorker();
        // Saves made before the persistent role flag still recover an active woodcutting order.
        this.woodcutterAssigned = this.woodcutterAssigned
                || this.getWorkState() == WorkState.GATHERING_WOOD
                || this.getWorkState() == WorkState.RETURNING_WOOD;
        int targetX = input.getIntOr("WoodTargetX", Integer.MIN_VALUE);
        if (targetX != Integer.MIN_VALUE) {
            this.setWoodTarget(new BlockPos(targetX,
                    input.getIntOr("WoodTargetY", 0), input.getIntOr("WoodTargetZ", 0)));
        } else {
            this.setWoodTarget(null);
            this.setWoodWorksite(null);
            if (!this.isMineWorker() && !this.isFarmWorker() && !this.isConstructionWorker()
                    && !this.isRepairWorker()) {
                this.setWorkState(WorkState.IDLE);
            }
        }
        int worksiteX = input.getIntOr("WoodWorksiteX", Integer.MIN_VALUE);
        if (worksiteX != Integer.MIN_VALUE) {
            this.setWoodWorksite(new BlockPos(worksiteX,
                    input.getIntOr("WoodWorksiteY", 0), input.getIntOr("WoodWorksiteZ", 0)));
        }
        int scaffoldHeight = input.getIntOr("ScaffoldHeight", 0);
        if (scaffoldHeight > 0) {
            this.setScaffolding(new BlockPos(
                    input.getIntOr("ScaffoldBaseX", this.blockPosition().getX()),
                    input.getIntOr("ScaffoldBaseY", this.blockPosition().getY()),
                    input.getIntOr("ScaffoldBaseZ", this.blockPosition().getZ())), scaffoldHeight);
        } else {
            this.setScaffolding(null, 0);
        }
        if (this.isWorking()) {
            if (this.isMineWorker()) {
                this.wearMinerKit();
            } else if (this.isFarmWorker()) {
                this.wearFarmerKit();
            } else if (this.isConstructionWorker()) {
                this.wearBuilderKit();
            } else if (this.isRepairWorker()) {
                this.wearBuilderKit();
            } else {
                this.wearWoodcutterKit();
            }
        } else {
            this.clearScaffolding();
            if (this.woodcutterAssigned) {
                this.wearWoodcutterKit();
            }
        }
    }
}
