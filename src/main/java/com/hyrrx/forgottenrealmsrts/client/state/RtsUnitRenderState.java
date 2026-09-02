package com.hyrrx.forgottenrealmsrts.client.state;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class RtsUnitRenderState extends HumanoidRenderState {
   public int variant;
   /** Wood units the villager is currently hauling; drives the worker's carry-lean gait. */
   public int carriedWood;
}
