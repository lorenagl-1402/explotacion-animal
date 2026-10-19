package com.example.examplemod;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

// Pincel: clic derecho en una gallina, vaca u oveja para cambiar su color,
// recorriendo los 16 tintes de Minecraft en orden (blanco -> naranja -> ... -> negro).
// Agachado al usarlo, retrocede al color anterior.
public class PincelItem extends Item {
    public PincelItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!ColoresAnimales.sePuedePintar(target)) {
            return InteractionResult.PASS;
        }
        Level level = target.level();
        if (level instanceof ServerLevel serverLevel) {
            List<DyeColor> colores = DyeColor.VALUES;
            DyeColor actual = ColoresAnimales.leerColor(target);
            // sin color asignado todavia: se empieza por el blanco
            int indice = actual == null ? 0 : colores.indexOf(actual);
            int paso = player.isShiftKeyDown() ? colores.size() - 1 : 1;
            DyeColor nuevo = colores.get((indice + paso) % colores.size());
            ColoresAnimales.aplicarColor(target, nuevo);
            avisar(serverLevel, player, target, nuevo);
        }
        return InteractionResult.SUCCESS;
    }

    // Mensaje en la barra de acción, partículas del nuevo color y sonido de tinte
    private static void avisar(ServerLevel level, Player player, LivingEntity target, DyeColor color) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() / 2.0D;
        double z = target.getZ();

        int rgb = color.getTextureDiffuseColor();
        double radio = target.getBbWidth() / 2.0D + 0.2D;
        double alto = target.getBbHeight() / 2.0D + 0.2D;
        level.sendParticles(new DustParticleOptions(rgb, 1.1F), x, y, z, 30, radio, alto, radio, 0.02D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, x, y + alto, z, 3, 0.2D, 0.1D, 0.2D, 0.0D);

        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

        player.sendOverlayMessage(
            net.minecraft.network.chat.Component.translatable(
                "examplemod.msg.color",
                net.minecraft.network.chat.Component.translatable("color.minecraft." + color.getName())
                    .withStyle(estilo -> estilo.withColor(color.getTextColor())))
            );
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(net.minecraft.network.chat.Component.translatable("item.examplemod.pincel.tooltip"));
    }
}
