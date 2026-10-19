package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ExampleMod.MODID)
public final class ExampleMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "examplemod";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Boton rojo: al pulsarlo sale una gallina, una explosion cercana o el dragon
    public static final RegistryObject<Block> EXPLOTACION_ANIMAL_BLOCK = registerButton("explotacion_animal", ExplotacionAnimalBlock.Kind.CHICKEN);
    public static final RegistryObject<Item> EXPLOTACION_ANIMAL_ITEM = registerButtonItem("explotacion_animal", EXPLOTACION_ANIMAL_BLOCK);

    // Boton negro: igual que el de gallinas, pero con vacas
    public static final RegistryObject<Block> EXPLOTACION_VACA_BLOCK = registerButton("explotacion_vaca", ExplotacionAnimalBlock.Kind.COW);
    public static final RegistryObject<Item> EXPLOTACION_VACA_ITEM = registerButtonItem("explotacion_vaca", EXPLOTACION_VACA_BLOCK);

    // Boton blanco: igual que el de gallinas, pero con ovejas
    public static final RegistryObject<Block> EXPLOTACION_OVEJA_BLOCK = registerButton("explotacion_oveja", ExplotacionAnimalBlock.Kind.SHEEP);
    public static final RegistryObject<Item> EXPLOTACION_OVEJA_ITEM = registerButtonItem("explotacion_oveja", EXPLOTACION_OVEJA_BLOCK);

    // Pincel: cambia el color de gallinas, vacas y ovejas
    public static final RegistryObject<Item> PINCEL_ITEM = ITEMS.register("pincel",
        () -> new PincelItem(new Item.Properties().setId(ITEMS.key("pincel")).stacksTo(1)));

    // Libro con las instrucciones del mod. No es un item propio: es un libro escrito ya relleno.
    public static ItemStack manual() {
        return ManualItem.crear();
    }

    private static RegistryObject<Block> registerButton(String id, ExplotacionAnimalBlock.Kind kind) {
        return BLOCKS.register(id, () -> new ExplotacionAnimalBlock(BlockBehaviour.Properties.of()
            .setId(BLOCKS.key(id))
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .noOcclusion(), kind));
    }

    private static RegistryObject<Item> registerButtonItem(String id, RegistryObject<Block> block) {
        return ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties().setId(ITEMS.key(id))));
    }

    // Pestana creativa del mod, con el boton como icono, colocada despues de la pestana de combate
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXPLOTACION_ANIMAL_ITEM.get().getDefaultInstance())
            .displayItems((_, output) -> {
                output.accept(EXPLOTACION_ANIMAL_ITEM.get());
                output.accept(EXPLOTACION_VACA_ITEM.get());
                output.accept(EXPLOTACION_OVEJA_ITEM.get());
                output.accept(PINCEL_ITEM.get());
                output.accept(manual());
            }).build());

    public ExampleMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        // Register the commonSetup method for modloading
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modBusGroup);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modBusGroup);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modBusGroup);


        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // El libro de instrucciones se regala al entrar al mundo
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerLogin);
    }

    // Se entrega el manual una sola vez por jugador
    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.getPersistentData().getBooleanOr("examplemod:manual_entregado", false)) {
            serverPlayer.getPersistentData().putBoolean("examplemod:manual_entregado", true);
            if (!serverPlayer.getInventory().add(manual())) {
                serverPlayer.drop(manual(), false);
            }
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        // Los renderers de vaca y gallina son los de vanilla con la capa del pincel encima
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(net.minecraft.world.entity.EntityTypes.CHICKEN, GallinaRenderer::new);
            event.registerEntityRenderer(net.minecraft.world.entity.EntityTypes.COW, VacaRenderer::new);
        }
    }
}
