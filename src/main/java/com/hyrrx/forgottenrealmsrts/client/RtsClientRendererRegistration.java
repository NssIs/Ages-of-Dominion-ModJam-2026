package com.hyrrx.forgottenrealmsrts.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

/** Small client-only bridge for renderer registrations whose common entity classes are supplied elsewhere. */
public final class RtsClientRendererRegistration {
    private static final String MOD_ID = "forgotten_realms_rts";

    private RtsClientRendererRegistration() {
    }

    /**
     * Registers a renderer if the common side has registered the requested mod entity ID.
     *
     * <p>The two expansion mobs are intentionally resolved by stable registry ID here. That keeps
     * this rendering slice independent from common/entity work and lets the client compile while
     * those entity classes are reconstructed in parallel.</p>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void registerMob(RegisterRenderers event, String path, EntityRendererProvider<Mob> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, path);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type != null) {
            event.registerEntityRenderer((EntityType) type, (EntityRendererProvider) factory);
        }
    }
}
