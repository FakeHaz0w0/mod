package com.yanderemod.registry;

import com.yanderemod.YandereMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, YandereMod.MODID);

    /** Debug/admin spawn egg. The mod only ever allows ONE yandere at a time, extras remove themselves. */
    public static final RegistryObject<Item> YANDERE_SPAWN_EGG = ITEMS.register("yandere_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.YANDERE, 0x2B0A14, 0xFF4D88, new Item.Properties()));
}
