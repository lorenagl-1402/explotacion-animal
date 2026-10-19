package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.DyeColor;

// Renderer de gallina: igual que el de vaca, con la capa del pincel encima.
public class GallinaRenderer extends ChickenRenderer {
    public GallinaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void extractRenderState(Chicken entity, ChickenRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ColorDeEntidad.guardar(entity, state);
    }

    @Override
    public void submit(ChickenRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        DyeColor color = ColorDeEntidad.sacar(state);
        if (color != null) {
            TinteAnimal.pintar(this.model, state, color, poseStack, collector, this.getTextureLocation(state));
        }
        super.submit(state, poseStack, collector, camera);
    }
}
