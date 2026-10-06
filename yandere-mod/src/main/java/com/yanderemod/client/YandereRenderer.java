package com.yanderemod.client;

import com.yanderemod.YandereMod;
import com.yanderemod.entity.YandereEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders the yandere with the supplied player skin (classic 4px arms). */
public class YandereRenderer extends HumanoidMobRenderer<YandereEntity, PlayerModel<YandereEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(YandereMod.MODID, "textures/entity/yandere.png");

    public YandereRenderer(EntityRendererProvider.Context context) {
        // second argument false = classic arms. If you swap in a slim (Alex-style) skin, use PLAYER_SLIM + true.
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(YandereEntity entity) {
        return TEXTURE;
    }
}
