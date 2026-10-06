package com.yanderemod;

import com.yanderemod.config.YandereConfig;
import com.yanderemod.entity.YandereEntity;
import com.yanderemod.registry.ModEntities;
import com.yanderemod.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(YandereMod.MODID)
public class YandereMod {
    public static final String MODID = "yanderemod";

    public YandereMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::onAttributes);
        bus.addListener(this::onCreativeTab);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, YandereConfig.SPEC);
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.YANDERE.get(), YandereEntity.createAttributes().build());
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.YANDERE_SPAWN_EGG);
        }
    }
}
