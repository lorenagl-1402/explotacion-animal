package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.DyeColor;

// Renderer de vaca: hace lo de vanilla y, si el pincel le ha puesto color, pinta la capa.
public class VacaRenderer extends CowRenderer {
    private static final Component NOMBRE = Component.translatable("item.examplemod.pincel");

    public VacaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void extractRenderState(Cow entity, CowRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ColorDeEntidad.guardar(entity, state);
    }

    @Override
    public void submit(CowRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        DyeColor color = ColorDeEntidad.sacar(state);
        if (color != null) {
            TinteAnimal.pintar(this.model, state, color, poseStack, collector, this.getTextureLocation(state));
        }
        super.submit(state, poseStack, collector, camera);
    }
}
