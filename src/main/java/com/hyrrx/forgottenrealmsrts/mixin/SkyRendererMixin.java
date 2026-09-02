package com.hyrrx.forgottenrealmsrts.mixin;

import com.hyrrx.forgottenrealmsrts.client.RtsMoonVisuals;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps vanilla moon phase geometry while replacing only its event palette. */
@Mixin(SkyRenderer.class)
public final class SkyRendererMixin {
    @ModifyArg(
            method = "renderMoon",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            ),
            index = 1
    )
    private Vector4fc forgottenRealmsRts$tintMoon(Vector4fc vanillaColor) {
        return RtsMoonVisuals.tintMoon(vanillaColor);
    }
}
