package com.yanderemod.registry;

import com.yanderemod.YandereMod;
import com.yanderemod.entity.YandereEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YandereMod.MODID);

    public static final RegistryObject<EntityType<YandereEntity>> YANDERE = ENTITIES.register("yandere",
            () -> EntityType.Builder.<YandereEntity>of(YandereEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("yandere"));
}
