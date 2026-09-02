package com.hyrrx.forgottenrealmsrts.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

public final class RtsUnitLayers {
   public static final ModelLayerLocation PEASANT = new ModelLayerLocation(Identifier.fromNamespaceAndPath("forgotten_realms_rts", "peasant"), "main");
   public static final ModelLayerLocation SOLDIER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("forgotten_realms_rts", "soldier"), "main");
   public static final ModelLayerLocation FALLEN_SOLDIER = new ModelLayerLocation(
      Identifier.fromNamespaceAndPath("forgotten_realms_rts", "fallen_soldier"), "main"
   );
   public static final ModelLayerLocation SKELETAL_ARCHER = new ModelLayerLocation(
      Identifier.fromNamespaceAndPath("forgotten_realms_rts", "skeletal_archer"), "main"
   );
   public static final ModelLayerLocation SAMURAI_ZOMBIE = new ModelLayerLocation(
      Identifier.fromNamespaceAndPath("forgotten_realms_rts", "samurai_zombie"), "main"
   );
   public static final ModelLayerLocation FALLEN_KNIGHT = new ModelLayerLocation(
      Identifier.fromNamespaceAndPath("forgotten_realms_rts", "fallen_knight"), "main"
   );
   public static final ModelLayerLocation ARCHER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("forgotten_realms_rts", "archer"), "main");
   public static final ModelLayerLocation SPEARMAN = new ModelLayerLocation(Identifier.fromNamespaceAndPath("forgotten_realms_rts", "spearman"), "main");
   public static final ModelLayerLocation CROSSBOWMAN = new ModelLayerLocation(
      Identifier.fromNamespaceAndPath("forgotten_realms_rts", "crossbowman"), "main"
   );
   public static final ModelLayerLocation FALLEN_BRUTE = new ModelLayerLocation(
      Identifier.fromNamespaceAndPath("forgotten_realms_rts", "fallen_brute"), "main"
   );

   private RtsUnitLayers() {
   }

   public static LayerDefinition createBodyLayer() {
      return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
   }

   public static LayerDefinition createPeasantLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      head.addOrReplaceChild(
         "hood_shell", CubeListBuilder.create().texOffs(52, 32).addBox(-4.5F, -10.0F, -4.5F, 9.0F, 4.0F, 9.0F), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "hood_brim", CubeListBuilder.create().texOffs(0, 32).addBox(-6.5F, -6.0F, -6.5F, 13.0F, 1.0F, 13.0F), PartPose.ZERO
      );
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild(
         "tunic_hem", CubeListBuilder.create().texOffs(22, 46).addBox(-4.5F, 11.0F, -2.5F, 9.0F, 4.0F, 5.0F, new CubeDeformation(0.3F)), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "belt", CubeListBuilder.create().texOffs(50, 46).addBox(-4.5F, 7.25F, -2.5F, 9.0F, 2.0F, 5.0F, new CubeDeformation(0.0F, -0.05F, 0.0F)), PartPose.ZERO
      );
      root.getChild("right_leg").addOrReplaceChild(
         "boot_right", CubeListBuilder.create().texOffs(0, 46).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "boot_left", CubeListBuilder.create().texOffs(88, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   /** A line infantryman: helmet with a real brim, plate over a gambeson, and a drawn sword. */
   public static LayerDefinition createSoldierLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      // The helm stops above the eyes and the brim overhangs it. The old version was a flat
      // 10x2x10 plate at neck height, which read as a collar rather than a helmet.
      head.addOrReplaceChild(
         "helmet", CubeListBuilder.create().texOffs(34, 32).addBox(-4.5F, -10.0F, -4.5F, 9.0F, 4.0F, 9.0F, new CubeDeformation(0.2F)), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "helmet_brim", CubeListBuilder.create().texOffs(82, 32).addBox(-5.5F, -6.0F, -5.5F, 11.0F, 1.0F, 11.0F), PartPose.ZERO
      );
      PartDefinition body = root.getChild("body");
      // A genuinely larger plate, not the vanilla body box +0.3 -- that was coplanar with the
      // torso on every face and z-fought instead of reading as armour.
      body.addOrReplaceChild(
         "cuirass", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, 0.0F, -2.5F, 9.0F, 11.0F, 5.0F), PartPose.ZERO
      );
      // The tabard used to sit at z=-4, a clear unit in front of the cuirass face, so it hung
      // in the air off the chest. It now rests against the plate.
      body.addOrReplaceChild(
         "tabard", CubeListBuilder.create().texOffs(70, 32).addBox(-2.5F, 1.0F, -3.0F, 5.0F, 12.0F, 1.0F), PartPose.ZERO
      );
      PartDefinition rightArm = root.getChild("right_arm");
      rightArm.addOrReplaceChild(
         "pauldron_right", CubeListBuilder.create().texOffs(64, 48).addBox(-4.0F, -2.5F, -2.5F, 5.0F, 3.0F, 5.0F), PartPose.ZERO
      );
      // The core melee unit had no weapon at all. A blade held upright is the clearest thing
      // a soldier can carry at RTS zoom -- it nearly doubles the unit's read.
      rightArm.addOrReplaceChild(
         "sword_grip", CubeListBuilder.create().texOffs(84, 48).addBox(-1.0F, 7.0F, -5.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO
      );
      rightArm.addOrReplaceChild(
         "sword_guard", CubeListBuilder.create().texOffs(92, 48).addBox(-3.0F, 6.0F, -5.0F, 6.0F, 1.0F, 2.0F), PartPose.ZERO
      );
      rightArm.addOrReplaceChild(
         "sword_blade", CubeListBuilder.create().texOffs(28, 32).addBox(-1.0F, -7.0F, -4.5F, 2.0F, 13.0F, 1.0F), PartPose.ZERO
      );
      PartDefinition leftArm = root.getChild("left_arm");
      leftArm.addOrReplaceChild(
         "pauldron_left", CubeListBuilder.create().texOffs(44, 48).addBox(-1.0F, -2.5F, -2.5F, 5.0F, 3.0F, 5.0F), PartPose.ZERO
      );
      // Boots are wider than the leg and darker, so the two legs stop fusing into one slab
      // at gameplay distance -- the roster's most consistent silhouette failure.
      root.getChild("right_leg").addOrReplaceChild(
         "boot_right", CubeListBuilder.create().texOffs(22, 48).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "boot_left", CubeListBuilder.create().texOffs(0, 48).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   /** A hooded ranged silhouette with a readable bow stave and back quiver. */
   public static LayerDefinition createArcherLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      // A single hood box sized to the whole head buries the face: nothing of the archer
      // was visible from the front at any angle. A crown down to the brow plus a drape at
      // the back keeps the hooded silhouette and gives the face back.
      head.addOrReplaceChild(
         "archer_hood", CubeListBuilder.create().texOffs(20, 32).addBox(-4.5F, -9.0F, -4.5F, 9.0F, 4.0F, 9.0F, new CubeDeformation(0.25F)), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "archer_hood_drape", CubeListBuilder.create().texOffs(22, 46).addBox(-4.5F, -5.0F, 1.5F, 9.0F, 5.0F, 3.0F, new CubeDeformation(0.25F)), PartPose.ZERO
      );
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild(
         "archer_cowl", CubeListBuilder.create().texOffs(64, 32).addBox(-4.0F, -0.4F, -2.5F, 8.0F, 4.0F, 5.0F, new CubeDeformation(0.4F, 0.2F, 0.05F)), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "quiver", CubeListBuilder.create().texOffs(56, 32).addBox(1.95F, 1.5F, 1.55F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F, 0.15F, 0.0F)), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "quiver_strap", CubeListBuilder.create().texOffs(46, 46).addBox(-4.5F, 1.1F, 1.8F, 9.0F, 1.0F, 1.0F, new CubeDeformation(0.1F, 0.2F, 0.1F)), PartPose.ZERO
      );
      addBow(root.getChild("right_arm"));
      root.getChild("left_arm").addOrReplaceChild(
         "archer_bracer", CubeListBuilder.create().texOffs(112, 32).addBox(-1.0F, 5.0F, -2.5F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.35F, 0.15F, 0.0F)), PartPose.ZERO
      );
      root.getChild("right_leg").addOrReplaceChild(
         "boot_right", CubeListBuilder.create().texOffs(0, 46).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "boot_left", CubeListBuilder.create().texOffs(90, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("body").addOrReplaceChild(
         "archer_cloak", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, 0.0F, 2.5F, 9.0F, 13.0F, 1.0F), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   /** A long-armed melee silhouette whose oversized spear reads at isometric zoom. */
   public static LayerDefinition createSpearmanLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      head.addOrReplaceChild(
         "spearman_cap", CubeListBuilder.create().texOffs(94, 32).addBox(-4.0F, -8.25F, -4.1F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.4F, 0.45F, 0.3F)), PartPose.ZERO
      );
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild(
         "spearman_harness", CubeListBuilder.create().texOffs(4, 32).addBox(-4.5F, 0.0F, -2.5F, 9.0F, 11.0F, 5.0F, new CubeDeformation(0.02F, 0.22F, 0.12F)), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "spear_strap", CubeListBuilder.create().texOffs(32, 32).addBox(-4.7F, 0.75F, -2.9F, 1.0F, 12.0F, 1.0F, new CubeDeformation(0.2F, -0.15F, 0.1F)), PartPose.ZERO
      );
      root.getChild("right_arm").addOrReplaceChild(
         "spear_shaft", CubeListBuilder.create().texOffs(0, 32).addBox(-0.5F, -10.5F, -4.5F, 1.0F, 22.0F, 1.0F), PartPose.ZERO
      );
      root.getChild("right_arm").addOrReplaceChild(
         "spear_head", CubeListBuilder.create().texOffs(120, 32).addBox(-1.0F, -13.0F, -5.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO
      );
      root.getChild("left_arm").addOrReplaceChild(
         "round_shield", CubeListBuilder.create().texOffs(80, 32).addBox(-3.0F, 0.0F, -5.0F, 6.0F, 8.0F, 1.0F), PartPose.ZERO
      );
      root.getChild("right_leg").addOrReplaceChild(
         "boot_right", CubeListBuilder.create().texOffs(58, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "boot_left", CubeListBuilder.create().texOffs(36, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   /** A battered echo silhouette with a ragged crown, exposed plate and hanging chain. */
   public static LayerDefinition createFallenSoldierLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      head.addOrReplaceChild(
         "ragged_crown", CubeListBuilder.create().texOffs(48, 32).addBox(-4.5F, -8.85F, -4.55F, 9.0F, 2.0F, 9.0F, new CubeDeformation(0.4F, 0.35F, 0.25F)), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "broken_faceplate", CubeListBuilder.create().texOffs(40, 48).addBox(-3.5F, -3.4F, -4.7F, 7.0F, 2.0F, 1.0F, new CubeDeformation(0.02F, 0.12F, 0.12F)), PartPose.ZERO
      );
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild(
         "torn_cuirass", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, -0.4F, -2.5F, 9.0F, 11.0F, 5.0F, new CubeDeformation(0.28F, 0.18F, 0.28F)), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "loose_chain", CubeListBuilder.create().texOffs(0, 48).addBox(2.0F, 3.0F, -2.9F, 1.0F, 8.0F, 1.0F, new CubeDeformation(0.08F)), PartPose.ZERO
      );
      root.getChild("right_arm").addOrReplaceChild(
         "right_shoulder_scrap", CubeListBuilder.create().texOffs(4, 48).addBox(-3.35F, -2.5F, -2.5F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.5F, 0.25F, 0.05F)), PartPose.ZERO
      );
      root.getChild("left_arm").addOrReplaceChild(
         "left_shoulder_scrap", CubeListBuilder.create().texOffs(22, 48).addBox(-1.0F, -1.9F, -2.5F, 4.0F, 3.0F, 5.0F, new CubeDeformation(0.2F, 0.3F, 0.0F)), PartPose.ZERO
      );
      root.getChild("right_leg").addOrReplaceChild(
         "boot_right", CubeListBuilder.create().texOffs(106, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "boot_left", CubeListBuilder.create().texOffs(84, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("body").addOrReplaceChild(
         "fallen_cloak", CubeListBuilder.create().texOffs(28, 32).addBox(-4.5F, 0.0F, 2.5F, 9.0F, 14.0F, 1.0F), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   /** A narrow undead ranged silhouette with rib bars and a high quiver. */
   public static LayerDefinition createSkeletalArcherLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      head.addOrReplaceChild(
         "bone_crown", CubeListBuilder.create().texOffs(8, 32).addBox(-4.0F, -9.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.12F)), PartPose.ZERO
      );
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild(
         "rib_upper", CubeListBuilder.create().texOffs(0, 43).addBox(-4.0F, 2.0F, -3.0F, 8.0F, 2.0F, 2.0F), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "rib_middle", CubeListBuilder.create().texOffs(100, 32).addBox(-3.5F, 5.0F, -3.0F, 7.0F, 2.0F, 2.0F), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "rib_lower", CubeListBuilder.create().texOffs(84, 32).addBox(-3.0F, 8.0F, -3.0F, 6.0F, 2.0F, 2.0F), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "bone_quiver", CubeListBuilder.create().texOffs(0, 32).addBox(1.9F, 1.0F, 1.6F, 2.0F, 9.0F, 2.0F, new CubeDeformation(-0.1F, 0.1F, -0.1F)), PartPose.ZERO
      );
      addBow(root.getChild("right_arm"));
      root.getChild("right_leg").addOrReplaceChild(
         "boot_right", CubeListBuilder.create().texOffs(62, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "boot_left", CubeListBuilder.create().texOffs(40, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   /** A compact samurai echo silhouette with a kabuto crest and broad shoulder guards. */
   public static LayerDefinition createSamuraiLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      head.addOrReplaceChild(
         "kabuto", CubeListBuilder.create().texOffs(48, 32).addBox(-4.5F, -9.2F, -4.5F, 9.0F, 3.0F, 9.0F, new CubeDeformation(0.22F)), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "kabuto_crest", CubeListBuilder.create().texOffs(68, 49).addBox(-1.0F, -13.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "face_guard", CubeListBuilder.create().texOffs(76, 49).addBox(-3.5F, -3.45F, -4.8F, 7.0F, 2.0F, 1.0F, new CubeDeformation(-0.1F, 0.25F, 0.1F)), PartPose.ZERO
      );
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild(
         "lamellar_cuirass", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, -0.25F, -2.5F, 9.0F, 12.0F, 5.0F, new CubeDeformation(0.25F, 0.0F, 0.25F)), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "sash", CubeListBuilder.create().texOffs(36, 49).addBox(-5.0F, 7.7F, -3.0F, 10.0F, 2.0F, 6.0F, new CubeDeformation(-0.08F, 0.02F, -0.08F)), PartPose.ZERO
      );
      root.getChild("right_arm").addOrReplaceChild(
         "sode_right", CubeListBuilder.create().texOffs(18, 49).addBox(-3.4F, -2.4F, -2.5F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.35F, 0.25F, 0.35F)), PartPose.ZERO
      );
      root.getChild("left_arm").addOrReplaceChild(
         "sode_left", CubeListBuilder.create().texOffs(0, 49).addBox(-0.9F, -2.4F, -2.5F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.35F, 0.25F, 0.35F)), PartPose.ZERO
      );
      root.getChild("right_leg").addOrReplaceChild(
         "boot_right", CubeListBuilder.create().texOffs(106, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "boot_left", CubeListBuilder.create().texOffs(84, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("body").addOrReplaceChild(
         "samurai_cloak", CubeListBuilder.create().texOffs(28, 32).addBox(-4.5F, 0.0F, 2.5F, 9.0F, 13.0F, 1.0F), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   /** A heavy knight echo silhouette with a great helm, shield and oversized pauldrons. */
   public static LayerDefinition createFallenKnightLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      head.addOrReplaceChild(
         "great_helm", CubeListBuilder.create().texOffs(0, 32).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 9.0F, 10.0F, new CubeDeformation(0.1F)), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "helm_visor", CubeListBuilder.create().texOffs(96, 51).addBox(-4.0F, -4.0F, -5.1F, 8.0F, 2.0F, 1.0F, new CubeDeformation(0.2F, 0.1F, 0.1F)), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "broken_plume", CubeListBuilder.create().texOffs(88, 51).addBox(-1.0F, -13.0F, -1.0F, 2.0F, 5.0F, 2.0F), PartPose.ZERO
      );
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild(
         "heavy_plate", CubeListBuilder.create().texOffs(64, 32).addBox(-5.0F, -0.3F, -2.5F, 10.0F, 12.0F, 5.0F, new CubeDeformation(0.14F, 0.34F, 0.54F)), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "fallen_tabard", CubeListBuilder.create().texOffs(94, 32).addBox(-3.0F, 0.0F, -3.2F, 6.0F, 13.0F, 1.0F, new CubeDeformation(-0.08F, 0.12F, 0.12F)), PartPose.ZERO
      );
      root.getChild("right_arm").addOrReplaceChild(
         "great_pauldron_right", CubeListBuilder.create().texOffs(22, 51).addBox(-4.1F, -3.0F, -3.0F, 5.0F, 5.0F, 6.0F, new CubeDeformation(0.2F, 0.3F, 0.1F)), PartPose.ZERO
      );
      root.getChild("left_arm").addOrReplaceChild(
         "great_pauldron_left", CubeListBuilder.create().texOffs(0, 51).addBox(-1.1F, -3.0F, -3.0F, 5.0F, 5.0F, 6.0F, new CubeDeformation(0.2F, 0.3F, 0.1F)), PartPose.ZERO
      );
      root.getChild("left_arm").addOrReplaceChild(
         "tower_shield", CubeListBuilder.create().texOffs(108, 32).addBox(-3.0F, -1.0F, -5.5F, 7.0F, 11.0F, 1.0F), PartPose.ZERO
      );
      root.getChild("right_leg").addOrReplaceChild(
         "boot_right", CubeListBuilder.create().texOffs(66, 51).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "boot_left", CubeListBuilder.create().texOffs(44, 51).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("body").addOrReplaceChild(
         "knight_cloak", CubeListBuilder.create().texOffs(40, 32).addBox(-5.5F, -1.0F, 3.0F, 11.0F, 17.0F, 1.0F), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   /** A ranged specialist silhouette with a distinct crossbow stock, grip and bolt case. */
   public static LayerDefinition createCrossbowmanLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      head.addOrReplaceChild(
         "crossbow_hood", CubeListBuilder.create().texOffs(28, 32).addBox(-4.5F, -9.0F, -4.3F, 9.0F, 9.0F, 5.0F, new CubeDeformation(0.15F, 0.05F, 0.25F)), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "crossbow_visor", CubeListBuilder.create().texOffs(42, 49).addBox(-4.0F, -3.35F, -4.8F, 8.0F, 2.0F, 1.0F, new CubeDeformation(-0.1F, -0.15F, 0.1F)), PartPose.ZERO
      );
      PartDefinition rightArmCrossbow = root.getChild("right_arm");
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild(
         "brigandine", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, -0.45F, -2.5F, 9.0F, 12.0F, 5.0F, new CubeDeformation(0.14F, -0.01F, 0.14F)), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "bolt_case", CubeListBuilder.create().texOffs(56, 32).addBox(1.9F, 0.25F, 1.7F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.02F, -0.13F, 0.02F)), PartPose.ZERO
      );
      rightArmCrossbow.addOrReplaceChild(
         "crossbow_stock", CubeListBuilder.create().texOffs(0, 49).addBox(-4.5F, 2.0F, -6.0F, 9.0F, 2.0F, 2.0F), PartPose.ZERO
      );
      root.getChild("right_arm").addOrReplaceChild(
         "crossbow_grip", CubeListBuilder.create().texOffs(108, 32).addBox(-1.5F, 1.0F, -5.0F, 3.0F, 6.0F, 2.0F), PartPose.ZERO
      );
      rightArmCrossbow.addOrReplaceChild(
         "crossbow_limb", CubeListBuilder.create().texOffs(22, 49).addBox(-4.0F, 0.0F, -7.0F, 8.0F, 1.0F, 2.0F), PartPose.ZERO
      );
      root.getChild("right_leg").addOrReplaceChild(
         "boot_right", CubeListBuilder.create().texOffs(86, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "boot_left", CubeListBuilder.create().texOffs(64, 32).addBox(-2.5F, 9.0F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   /** A broad, low undead silhouette that reads as a siege brute instead of another biped. */
   public static LayerDefinition createFallenBruteLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      head.addOrReplaceChild(
         "brute_brow", CubeListBuilder.create().texOffs(38, 32).addBox(-5.0F, -8.8F, -4.9F, 10.0F, 4.0F, 9.0F, new CubeDeformation(0.35F, 0.35F, 0.25F)), PartPose.ZERO
      );
      head.addOrReplaceChild(
         "stone_jaw", CubeListBuilder.create().texOffs(40, 51).addBox(-4.5F, -3.2F, -5.05F, 9.0F, 3.0F, 6.0F, new CubeDeformation(0.25F, 0.25F, 0.0F)), PartPose.ZERO
      );
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild(
         "brute_chest", CubeListBuilder.create().texOffs(0, 32).addBox(-6.0F, -0.25F, -3.5F, 12.0F, 12.0F, 7.0F, new CubeDeformation(0.4F, 0.65F, 0.2F)), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "cairn_collar", CubeListBuilder.create().texOffs(70, 51).addBox(-6.0F, -1.7F, -3.0F, 12.0F, 2.0F, 6.0F, new CubeDeformation(0.1F, 0.4F, 0.5F)), PartPose.ZERO
      );
      root.getChild("right_arm").addOrReplaceChild(
         "brute_fist_right", CubeListBuilder.create().texOffs(98, 32).addBox(-4.4F, 6.25F, -3.1F, 5.0F, 6.0F, 6.0F, new CubeDeformation(0.45F, 0.6F, 0.45F)), PartPose.ZERO
      );
      root.getChild("left_arm").addOrReplaceChild(
         "brute_fist_left", CubeListBuilder.create().texOffs(76, 32).addBox(-0.6F, 6.25F, -3.1F, 5.0F, 6.0F, 6.0F, new CubeDeformation(0.45F, 0.6F, 0.45F)), PartPose.ZERO
      );
      root.getChild("right_leg").addOrReplaceChild(
         "brute_greave_right", CubeListBuilder.create().texOffs(20, 51).addBox(-2.5F, 7.0F, -2.5F, 5.0F, 6.0F, 5.0F, new CubeDeformation(0.5F, 0.3F, 0.3F)), PartPose.ZERO
      );
      root.getChild("left_leg").addOrReplaceChild(
         "brute_greave_left", CubeListBuilder.create().texOffs(0, 51).addBox(-2.5F, 7.0F, -2.5F, 5.0F, 6.0F, 5.0F, new CubeDeformation(0.5F, 0.3F, 0.3F)), PartPose.ZERO
      );
      return LayerDefinition.create(mesh, 128, 128);
   }

   private static void addBow(PartDefinition arm) {
      arm.addOrReplaceChild(
         "bow_stave", CubeListBuilder.create().texOffs(0, 62).addBox(-0.5F, -7.0F, -5.0F, 1.0F, 14.0F, 1.0F), PartPose.ZERO
      );
      arm.addOrReplaceChild(
         "bow_grip", CubeListBuilder.create().texOffs(4, 62).addBox(-1.5F, -1.0F, -5.0F, 3.0F, 2.0F, 1.0F), PartPose.ZERO
      );
      arm.addOrReplaceChild(
         "bow_upper_limb", CubeListBuilder.create().texOffs(12, 62).addBox(-1.5F, -8.0F, -5.0F, 3.0F, 1.0F, 1.0F), PartPose.ZERO
      );
      arm.addOrReplaceChild(
         "bow_lower_limb", CubeListBuilder.create().texOffs(20, 62).addBox(-1.5F, 6.0F, -5.0F, 3.0F, 1.0F, 1.0F), PartPose.ZERO
      );
   }
}
