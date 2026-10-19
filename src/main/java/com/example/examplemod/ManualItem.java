package com.example.examplemod;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

// Libro que explica el mod: los 3 botones, los porcentajes, las recetas y el pincel.
// No hay item propio: es un libro escrito de vanilla que se entrega ya relleno.
public final class ManualItem {
    // Una entrada por pagina. El texto va en el idioma del cliente (lang), no en el codigo.
    private static final List<String> PAGINAS = List.of(
        "examplemod.manual.p1",
        "examplemod.manual.p2",
        "examplemod.manual.p3",
        "examplemod.manual.p4",
        "examplemod.manual.p5",
        "examplemod.manual.p6",
        "examplemod.manual.p7",
        "examplemod.manual.p8"
    );

    private ManualItem() {
    }

    public static ItemStack crear() {
        ItemStack libro = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> paginas = PAGINAS.stream()
            .map(clave -> Filterable.<Component>passThrough(Component.translatable(clave)))
            .toList();
        libro.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
            Filterable.passThrough(Component.translatable("examplemod.manual.title").getString()),
            Component.translatable("examplemod.manual.author").getString(),
            0,
            paginas,
            false
        ));
        return libro;
    }
}
