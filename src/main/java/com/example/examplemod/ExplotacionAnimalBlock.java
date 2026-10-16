package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
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

// Boton "explotacion animal": al pulsarlo hay un 80% de que salga el animal del bloque, un 15% de explosion
// en el boton (sin matar) y un 5% de dragon con rayo de noche. Ademas, un 1% de las veces cae un rayo celestial
// que perfora todo hasta la bedrock (menos el animal de este bloque).
// El animal y el color del boton dependen del bloque: gallinas = rojo, vacas = negro, ovejas = blanco.
public class ExplotacionAnimalBlock extends Block {
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");

    // Que animal sale al pulsar el boton (y cual sobrevive al rayo celestial)
    public enum Kind {
        CHICKEN, COW, SHEEP
    }

    private static final double CELESTIAL_RAY_CHANCE = 0.01D;
    private static final int ANIMAL_CHANCE = 80;
    private static final int EXPLOSION_CHANCE = 15; // el resto (5%) es el dragon
    private static final int PRESS_TICKS = 8;
    private static final double BLAST_RADIUS = 5.0D;
    private static final int RAY_RADIUS = 5;
    // distancia desde el centro del boton a la que sale el animal, para que no lo tape al pulsarlo
    private static final double SPAWN_DISTANCE = 1.8D;

    private final Kind kind;

    public ExplotacionAnimalBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
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
            if (roll < ANIMAL_CHANCE) {
                // el animal sale delante del boton, en la cara pulsada, pero un poco mas lejos para no taparlo
                spawnAnimal(serverLevel, pos, hit.getDirection());
                confetti(serverLevel, pos);
            } else if (roll < ANIMAL_CHANCE + EXPLOSION_CHANCE) {
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

    // Animal de tamano normal e indestructible, con una variante al azar de las de vanilla.
    // Sale un poco separado del boton (en la direccion de la cara pulsada) para no taparlo.
    private void spawnAnimal(ServerLevel level, BlockPos buttonPos, Direction direction) {
        Entity animal = switch (kind) {
            case CHICKEN -> createChicken(level);
            case COW -> createCow(level);
            case SHEEP -> createSheep(level);
        };
        if (animal == null) {
            return;
        }
        RandomSource random = level.getRandom();
        animal.setInvulnerable(true);

        Vec3 offset = spawnOffset(direction, random);
        double x = buttonPos.getX() + 0.5D + offset.x;
        double z = buttonPos.getZ() + 0.5D + offset.z;
        // busca un hueco libre con suelo para que no aparezca dentro de un bloque
        int height = Math.max(1, (int) Math.ceil(animal.getBbHeight()));
        BlockPos spot = findSpawnSpot(level, BlockPos.containing(x, buttonPos.getY(), z), height);
        animal.snapTo(x, spot.getY(), z, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(animal);
    }

    private static Chicken createChicken(ServerLevel level) {
        Chicken chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.TRIGGERED);
        if (chicken == null) {
            return null;
        }
        chicken.setVariant(pickVariant(level, Registries.CHICKEN_VARIANT));
        return chicken;
    }

    private static Cow createCow(ServerLevel level) {
        Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.TRIGGERED);
        if (cow == null) {
            return null;
        }
        cow.setVariant(pickVariant(level, Registries.COW_VARIANT));
        return cow;
    }

    private static Sheep createSheep(ServerLevel level) {
        Sheep sheep = EntityTypes.SHEEP.create(level, EntitySpawnReason.TRIGGERED);
        if (sheep == null) {
            return null;
        }
        sheep.setColor(DyeColor.VALUES.get(level.getRandom().nextInt(DyeColor.VALUES.size())));
        sheep.setSheared(false);
        return sheep;
    }

    private static <T> Holder.Reference<T> pickVariant(ServerLevel level, ResourceKey<Registry<T>> registry) {
        List<Holder.Reference<T>> variants = level.registryAccess().lookupOrThrow(registry).listElements().toList();
        return variants.get(level.getRandom().nextInt(variants.size()));
    }

    // Desplazamiento desde el centro del boton: sale en la direccion de la cara pulsada.
    // Si el boton esta en el suelo o el techo (se pulso por arriba/abajo), sale hacia un lado al azar.
    private static Vec3 spawnOffset(Direction direction, RandomSource random) {
        if (direction.getAxis().isVertical()) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            return new Vec3(Math.cos(angle) * SPAWN_DISTANCE, 0.0D, Math.sin(angle) * SPAWN_DISTANCE);
        }
        return new Vec3(direction.getStepX() * SPAWN_DISTANCE, 0.0D, direction.getStepZ() * SPAWN_DISTANCE);
    }

    // Primer hueco cerca del boton con suelo solido y sus bloques de aire libres para que quepa el animal.
    private static BlockPos findSpawnSpot(ServerLevel level, BlockPos origin, int height) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos candidate = origin.offset(0, dy, 0);
            if (!level.getBlockState(candidate.below()).isSolidRender()) {
                continue;
            }
            boolean free = true;
            for (int i = 0; i < height; i++) {
                if (!level.getBlockState(candidate.above(i)).isAir()) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return candidate;
            }
        }
        return origin;
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

    // Rayo celestial: columna de luz que vacia todo desde el cielo hasta la bedrock y mata a todo ser vivo salvo el animal de este bloque
    private void celestialRay(ServerLevel level, BlockPos pos) {
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

        // todo ser vivo en la columna muere, excepto el animal de este bloque
        AABB column = new AABB(cx - RAY_RADIUS, minY, cz - RAY_RADIUS, cx + RAY_RADIUS, maxY, cz + RAY_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, column)) {
            if (!isProtectedAnimal(entity)) {
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

    // El animal de este bloque es el unico que sobrevive al rayo celestial
    private boolean isProtectedAnimal(Entity entity) {
        return switch (kind) {
            case CHICKEN -> entity instanceof Chicken;
            case COW -> entity instanceof Cow;
            case SHEEP -> entity instanceof Sheep;
        };
    }
}
