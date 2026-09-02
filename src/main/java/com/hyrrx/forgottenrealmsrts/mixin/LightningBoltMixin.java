package com.hyrrx.forgottenrealmsrts.mixin;

import com.hyrrx.forgottenrealmsrts.RtsScriptedLightning;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a synchronized semantic marker without changing natural-weather lightning state. */
@Mixin(LightningBolt.class)
public final class LightningBoltMixin implements RtsScriptedLightning {
    @Unique
    private static final EntityDataAccessor<Boolean> forgottenRealmsRts$scriptedData =
            SynchedEntityData.defineId(LightningBolt.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void forgottenRealmsRts$defineData(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(forgottenRealmsRts$scriptedData, false);
    }

    @Override
    public boolean forgottenRealmsRts$isScripted() {
        return ((LightningBolt) (Object) this).getEntityData().get(forgottenRealmsRts$scriptedData);
    }

    @Override
    public void forgottenRealmsRts$setScripted(boolean scripted) {
        ((LightningBolt) (Object) this).getEntityData().set(forgottenRealmsRts$scriptedData, scripted);
    }
}
