package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

// El color del pincel tiene que viajar de extractRenderState (donde el renderer ve la
// entidad) a submit (donde solo hay estado de render), porque en 26.2 el cliente ya no
// dibuja mirando la entidad. Se guarda en un mapa por estado de render y se borra al leerlo.
public final class ColorDeEntidad {
    private static final Map<LivingEntityRenderState, DyeColor> COLORES = new HashMap<>();

    private ColorDeEntidad() {
    }

    // Se llama desde extractRenderState, que es el unico sitio con la entidad a mano
    public static void guardar(LivingEntity entity, LivingEntityRenderState state) {
        COLORES.remove(state);
        DyeColor color = ColoresAnimales.leerColor(entity);
        if (color != null) {
            COLORES.put(state, color);
        }
    }

    public static @Nullable DyeColor sacar(LivingEntityRenderState state) {
        return COLORES.remove(state);
    }
}
