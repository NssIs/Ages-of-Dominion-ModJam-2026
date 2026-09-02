package com.hyrrx.forgottenrealmsrts.client.model;

import com.hyrrx.forgottenrealmsrts.client.state.RtsUnitRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Shared humanoid model for every RTS unit renderer. Only the seven vanilla {@link HumanoidModel}
 * parts are ever touched here (head, hat, body, right_arm, left_arm, right_leg, left_leg) because
 * {@code RtsUnitLayers} is free to add, rename, or remove custom child cubes underneath them at
 * any time; those children are all {@code PartPose.ZERO}, so they ride along with whatever
 * rotation/position we apply to their vanilla parent for free.
 *
 * <p>{@code HumanoidModel} in 26.1 keeps {@code rightArmPose}/{@code leftArmPose} on the render
 * state (it extends {@code ArmedEntityRenderState}), not on the model. So the weapon pose for a
 * unit is applied by stamping those fields onto the state before delegating to
 * {@code super.setupAnim}, which is what actually swings the arms into a bow/crossbow/spear hold.
 */
public final class RtsUnitModel extends HumanoidModel<RtsUnitRenderState> {
   /** Behaviour/gait/weapon-pose grouping for a unit. Passed in once at bake time, never allocated per frame. */
   public enum Profile {
      WORKER,
      SOLDIER,
      ARCHER,
      CROSSBOW,
      SPEAR,
      UNDEAD,
      BRUTE
   }

   private final Profile profile;

   public RtsUnitModel(ModelPart root, Profile profile) {
      super(root);
      this.profile = profile;
   }

   @Override
   public void setupAnim(RtsUnitRenderState state) {
      // Arm poses live on the state, not the model, in this Minecraft version - stamp them on
      // before super reads them so bows/crossbows/spears actually raise into their hold.
      this.applyWeaponPose(state);
      super.setupAnim(state);
      this.applyIdle(state);
      this.applyGait(state);
   }

   private void applyWeaponPose(RtsUnitRenderState state) {
      switch (this.profile) {
         case ARCHER -> state.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
         case CROSSBOW -> state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
         case SPEAR -> state.rightArmPose = HumanoidModel.ArmPose.SPEAR;
         case SOLDIER -> state.rightArmPose = HumanoidModel.ArmPose.ITEM;
         case UNDEAD -> state.rightArmPose = HumanoidModel.ArmPose.ITEM;
         case WORKER, BRUTE -> state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
      }
      state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
   }

   /**
    * Small idle breathing / weight shift so a standing unit does not look frozen. Viewed from an
    * RTS camera at ~44 blocks a hand-sized motion reads as noise, so the amplitude stays under a
    * third of a unit and fades out as the unit starts walking so it does not fight the leg swing.
    */
   private void applyIdle(RtsUnitRenderState state) {
      float idleStrength = 1.0F - Mth.clamp(state.walkAnimationSpeed * 2.0F, 0.0F, 1.0F);
      if (idleStrength <= 0.0F) {
         return;
      }

      float rate = this.profile == Profile.BRUTE ? 0.035F : 0.06F;
      float amplitude = (this.profile == Profile.BRUTE ? 0.45F : 0.3F) * idleStrength;
      float breathe = Mth.sin(state.ageInTicks * rate);
      this.body.y += breathe * amplitude;

      // Undead sway less: they are dead men walking, not breathing.
      float headSway = this.profile == Profile.UNDEAD ? 0.008F : 0.02F;
      this.head.zRot += Mth.cos(state.ageInTicks * rate * 0.5F) * headSway * idleStrength;
   }

   /** Per-profile walk character layered on top of the vanilla leg/arm swing super already set. */
   private void applyGait(RtsUnitRenderState state) {
      switch (this.profile) {
         case BRUTE -> {
            // Heavy and slow: exaggerate the swing vanilla already computed instead of
            // recomputing it, so this stays a cheap multiply rather than a second cos() pass.
            this.rightLeg.xRot *= 1.3F;
            this.leftLeg.xRot *= 1.3F;
            this.rightArm.xRot *= 1.15F;
            this.leftArm.xRot *= 1.15F;
         }
         case UNDEAD -> {
            // Stiffer, less swinging walk.
            this.rightLeg.xRot *= 0.7F;
            this.leftLeg.xRot *= 0.7F;
            this.rightArm.xRot *= 0.5F;
            this.leftArm.xRot *= 0.5F;
         }
         case WORKER -> {
            if (state.carriedWood > 0) {
               // A slight forward lean and arms held out front, as if balancing a load of logs.
               this.body.xRot += 0.12F;
               this.rightArm.xRot -= 0.9F;
               this.leftArm.xRot -= 0.9F;
               this.rightArm.zRot = -0.15F;
               this.leftArm.zRot = 0.15F;
            }
         }
         default -> {
         }
      }
   }
}
