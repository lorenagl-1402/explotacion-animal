package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.DyeColor;

// Capa de color del pincel: vuelve a dibujar el mismo modelo con la textura en modo
// translucido y el tinte por encima, en la misma pose que el animal.
//
// El truco: el renderer de vanilla coloca el modelo con una serie de transformaciones
// (escala, giro, volteo y desplazamiento vertical) y luego deshace la pose. Como aqui
// solo hay estado de render, repetimos esos mismos pasos para dejar la capa justo
// encima del modelo de siempre.
public final class TinteAnimal {
    private TinteAnimal() {
    }

    public static <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void pintar(
        M modelo, S state, DyeColor color, PoseStack poseStack, SubmitNodeCollector collector, Identifier textura
    ) {
        float escala = state.scale;
        poseStack.pushPose();
        poseStack.scale(escala, escala, escala);
        // mismo giro del cuerpo que aplica el renderer de vanilla al colocar el animal
        if (!state.hasPose(Pose.SLEEPING)) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - state.bodyRot));
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(
            modelo, state, poseStack,
            RenderTypes.entityTranslucent(textura),
            state.lightCoords, OverlayTexture.NO_OVERLAY,
            ColoresAnimales.tinteOverlay(color), null, 0, null);
        poseStack.popPose();
    }
}
