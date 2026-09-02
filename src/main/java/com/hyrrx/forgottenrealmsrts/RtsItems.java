package com.hyrrx.forgottenrealmsrts;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative/debug access to the two new roster entries without adding a survival recipe. */
public final class RtsItems {
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ForgottenRealmsRTS.MOD_ID);

    public static final DeferredItem<Item> RTS_CROSSBOWMAN_SPAWN_EGG = ITEMS.registerItem(
            "rts_crossbowman_spawn_egg",
            properties -> new SpawnEggItem(properties.component(
                    DataComponents.ENTITY_DATA,
                    TypedEntityData.of(RtsEntities.RTS_CROSSBOWMAN.get(), new CompoundTag()))));

    public static final DeferredItem<Item> FALLEN_BRUTE_SPAWN_EGG = ITEMS.registerItem(
            "fallen_brute_spawn_egg",
            properties -> new SpawnEggItem(properties.component(
                    DataComponents.ENTITY_DATA,
                    TypedEntityData.of(RtsEntities.FALLEN_BRUTE.get(), new CompoundTag()))));

    private RtsItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(RtsItems::addCreativeEntries);
    }

    private static void addCreativeEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
            event.accept(RTS_CROSSBOWMAN_SPAWN_EGG);
            event.accept(FALLEN_BRUTE_SPAWN_EGG);
        }
    }
}
