package com.hyrrx.forgottenrealmsrts.mixin;

import com.hyrrx.forgottenrealmsrts.RtsTerrainGeneration;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the owned terrain presentation at the generator's decoration stage. */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void forgottenRealmsRts$prepareTerrain(WorldGenLevel level, ChunkAccess chunk,
                                                    StructureManager structureManager,
                                                    CallbackInfo callback) {
        RtsTerrainGeneration.prepareOverworldDecoration(level, chunk, structureManager);
    }
}
