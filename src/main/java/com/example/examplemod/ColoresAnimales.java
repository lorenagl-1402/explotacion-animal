package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

// Logica del color de los animales pintados con el pincel.
//
// La oveja guarda el color con el sistema de vanilla (Sheep#setColor): se sincroniza
// y se guarda en el mundo automaticamente.
//
// La vaca y la gallina no tienen color en vanilla, asi que llevan una marca invisible:
// un jubon de cuero teñido en el pecho. Ese hueco de equipamiento no se dibuja en
// ninguno de los dos modelos, pero Minecraft lo sincroniza a todos los clientes y lo
// guarda en el disco por nosotros, asi que no hace falta inventar paquetes nuevos.
public final class ColoresAnimales {
    // La vaca y la gallina solo se pintan si son de los tipos de vanilla (asi no se
    // cuela la vaca mushroom, que tiene su propio renderer y otro aspecto).
    private static final Map<Integer, DyeColor> POR_COLOR = new HashMap<>();

    static {
        for (DyeColor color : DyeColor.VALUES) {
            POR_COLOR.put(color.getTextureDiffuseColor(), color);
        }
    }

    // Opacidad del tinte que se pinta encima de la vaca y la gallina
    private static final int ALFA_TINTE = 150;

    private ColoresAnimales() {
    }

    public static boolean sePuedePintar(LivingEntity entity) {
        var tipo = entity.getType();
        return tipo == EntityTypes.CHICKEN || tipo == EntityTypes.COW || tipo == EntityTypes.SHEEP;
    }

    // Color actual del animal, o null si nunca se le ha pintado
    public static @Nullable DyeColor leerColor(LivingEntity entity) {
        if (entity instanceof Sheep sheep) {
            return sheep.getColor();
        }
        if (!sePuedePintar(entity)) {
            return null;
        }
        DyedItemColor marca = entity.getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.DYED_COLOR);
        return marca == null ? null : POR_COLOR.get(marca.rgb());
    }

    public static void aplicarColor(LivingEntity entity, DyeColor color) {
        if (entity instanceof Sheep sheep) {
            // la oveja ya sabe pintarse sola en vanilla
            sheep.setColor(color);
            return;
        }
        ItemStack marca = new ItemStack(Items.LEATHER_CHESTPLATE);
        marca.set(DataComponents.DYED_COLOR, new DyedItemColor(color.getTextureDiffuseColor()));
        entity.setItemSlot(EquipmentSlot.CHEST, marca);
        if (entity instanceof Mob mob) {
            // la marca no debe caer al suelo si el animal muere
            mob.setDropChance(EquipmentSlot.CHEST, 0.0F);
        }
    }

    // Tinte semitransparente con el que el cliente pinta encima de la vaca y la gallina.
    // Va con alfa para que el blanco aclare en vez de quedarse igual.
    public static int tinteOverlay(DyeColor color) {
        int rgb = color.getTextureDiffuseColor();
        return ARGB.color(ALFA_TINTE, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }
}
