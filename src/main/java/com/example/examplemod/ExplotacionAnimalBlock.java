package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// Bloque "explotacion_animal2": al pulsarlo hay un 80% de gallina, un 15% de explosion en el boton (sin matar) y un 5% de dragon con rayo de noche.
// Ademas, un 1% de las veces cae un rayo celestial que perfora todo hasta la bedrock (menos las gallinas).
public class ExplotacionAnimalBlock extends Block {
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");

    private static final double CELESTIAL_RAY_CHANCE = 0.01D;
    private static final int CHICKEN_CHANCE = 80;
    private static final int EXPLOSION_CHANCE = 15; // el resto (5%) es el dragon
    private static final int PRESS_TICKS = 8;
    private static final double BLAST_RADIUS = 5.0D;
    private static final int RAY_RADIUS = 5;
    private static final double MIN_CHICKEN_SCALE = 0.2D;
    private static final double MAX_CHICKEN_SCALE = 5.0D;

    public ExplotacionAnimalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PRESSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PRESSED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PRESSED)) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel) {
            // animacion de boton pulsado: se hunde y vuelve a subir en tick()
            level.setBlock(pos, state.setValue(PRESSED, true), Block.UPDATE_ALL);
            level.scheduleTick(pos, this, PRESS_TICKS);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0F, 0.8F);

            if (player.isShiftKeyDown() || level.getRandom().nextDouble() < CELESTIAL_RAY_CHANCE) { // agachado = rayo garantizado, para probarlo
                celestialRay(serverLevel, pos);
                return InteractionResult.SUCCESS;
            }
            int roll = level.getRandom().nextInt(100);
            if (roll < CHICKEN_CHANCE) {
                // la gallina sale en el bloque de al lado, el de la cara pulsada
                spawnChicken(serverLevel, pos.relative(hit.getDirection()));
                confetti(serverLevel, pos);
            } else if (roll < CHICKEN_CHANCE + EXPLOSION_CHANCE) {
                buttonExplosion(serverLevel, pos);
            } else {
                spawnDragon(serverLevel, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PRESSED)) {
            level.setBlock(pos, state.setValue(PRESSED, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
    }

    // Gallina indestructible, de cualquiera de las razas de vanilla y de un tamano aleatorio (de diminuta a gigante)
    private static void spawnChicken(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        Chicken chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.TRIGGERED);
        if (chicken == null) {
            return;
        }
        List<Holder.Reference<ChickenVariant>> variants = level.registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT).listElements().toList();
        chicken.setVariant(variants.get(random.nextInt(variants.size())));

        // escala logaritmica para que haya tantas muy chicas como muy grandes
        double minLog = Math.log(MIN_CHICKEN_SCALE);
        double maxLog = Math.log(MAX_CHICKEN_SCALE);
        chicken.getAttribute(Attributes.SCALE).setBaseValue(Math.exp(minLog + random.nextDouble() * (maxLog - minLog)));

        chicken.setInvulnerable(true);
        chicken.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(chicken);
    }

    // Confeti de colores y sonidos alegres saliendo del boton
    private static void confetti(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.0D;
        double z = pos.getZ() + 0.5D;

        int[] colors = {0xFF3B3B, 0xFF9A28, 0xFFE83C, 0x8CE632, 0x28AA46, 0x3CDCE6, 0x3C64FF, 0xA046E6, 0xFF78C8};
        for (int color : colors) {
            level.sendParticles(new DustParticleOptions(color, 1.4F), x, y, z, 14, 0.5D, 0.5D, 0.5D, 0.4D);
        }
        level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 30, 0.2D, 0.2D, 0.2D, 0.25D);
        level.sendParticles(ParticleTypes.NOTE, x, y + 0.2D, z, 8, 0.5D, 0.3D, 0.5D, 1.0D);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 10, 0.5D, 0.4D, 0.5D, 0.0D);

        level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 1.0F, 1.2F);
        level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.5F);
        level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F, 0.8F + random.nextFloat() * 0.8F);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.0F + random.nextFloat() * 0.5F);
    }

    private static void spawnDragon(ServerLevel level, BlockPos pos) {
        // cuando cae el rayo se hace de noche
        Holder<WorldClock> overworld = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
        level.clockManager().moveToTimeMarker(overworld, ClockTimeMarkers.MIDNIGHT);

        EntityTypes.ENDER_DRAGON.spawn(level, pos.above(6), EntitySpawnReason.TRIGGERED);
        // rayo solo visual: trueno y destello sin quemar ni matar a quien esta pulsando el boton
        strike(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private static void strike(ServerLevel level, double x, double y, double z) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(x, y, z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    // Explosion en el propio boton: mucho efecto (fuego, humo, chispas, onda), nada de bloques rotos y dano que nunca mata
    private static void buttonExplosion(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.9D;
        double z = pos.getZ() + 0.5D;

        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, 0.9F + random.nextFloat() * 0.2F);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 1.0F, 1.4F);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 12, 0.8D, 0.5D, 0.8D, 0.1D);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 60, 0.3D, 0.3D, 0.3D, 0.18D);
        level.sendParticles(ParticleTypes.LAVA, x, y, z, 25, 0.4D, 0.3D, 0.4D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 40, 0.5D, 0.5D, 0.5D, 0.08D);
        level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3D, z, 50, 0.7D, 0.7D, 0.7D, 0.04D);
        level.sendParticles(ParticleTypes.POOF, x, y, z, 30, 0.4D, 0.4D, 0.4D, 0.25D);

        // onda de choque: empuja y hace dano segun la distancia, dejando siempre al menos medio corazon
        Vec3 center = new Vec3(x, y, z);
        AABB area = new AABB(center, center).inflate(BLAST_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            Vec3 away = entity.position().add(0.0D, entity.getBbHeight() / 2.0D, 0.0D).subtract(center);
            double distance = away.length();
            if (distance > BLAST_RADIUS) {
                continue;
            }
            double force = 1.0D - distance / BLAST_RADIUS;
            Vec3 push = distance < 1.0E-3D ? new Vec3(0.0D, 1.0D, 0.0D) : away.normalize();
            entity.push(push.x * force * 1.6D, 0.3D + force * 0.7D, push.z * force * 1.6D);
            entity.hurtMarked = true;
            float damage = (float) Math.min(force * 8.0D, entity.getHealth() - 1.0F);
            if (damage > 0.0F) {
                entity.hurtServer(level, level.damageSources().generic(), damage);
            }
        }
    }

    // Rayo celestial: columna de luz que vacia todo desde el cielo hasta la bedrock y mata a todo ser vivo salvo las gallinas
    private static void celestialRay(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        int minY = level.getMinY();
        int maxY = level.getMaxY();

        level.playSound(null, cx, pos.getY(), cz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 12.0F, 0.5F);
        level.playSound(null, cx, pos.getY(), cz, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 8.0F, 0.4F);
        for (int i = 0; i < 4; i++) {
            strike(level, cx + (i % 2 == 0 ? 3 : -3), pos.getY(), cz + (i < 2 ? 3 : -3));
        }
        for (int y = pos.getY() + 80; y >= pos.getY() - 80; y -= 2) {
            level.sendParticles(ParticleTypes.END_ROD, cx, y, cz, 8, 1.2D, 1.0D, 1.2D, 0.05D);
            level.sendParticles(ParticleTypes.FLAME, cx, y, cz, 4, RAY_RADIUS * 0.4D, 1.0D, RAY_RADIUS * 0.4D, 0.02D);
        }

        // todo ser vivo en la columna muere, excepto las gallinas
        AABB column = new AABB(cx - RAY_RADIUS, minY, cz - RAY_RADIUS, cx + RAY_RADIUS, maxY, cz + RAY_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, column)) {
            if (!(entity instanceof Chicken)) {
                entity.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
            }
        }

        // boquete circular desde la superficie hasta justo encima de la bedrock
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -RAY_RADIUS; dx <= RAY_RADIUS; dx++) {
            for (int dz = -RAY_RADIUS; dz <= RAY_RADIUS; dz++) {
                if (dx * dx + dz * dz > RAY_RADIUS * RAY_RADIUS) {
                    continue;
                }
                int x = pos.getX() + dx;
                int z = pos.getZ() + dz;
                int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), pos.getY());
                for (int y = top; y >= minY; y--) {
                    cursor.set(x, y, z);
                    BlockState current = level.getBlockState(cursor);
                    if (!current.isAir() && !current.is(Blocks.BEDROCK)) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
