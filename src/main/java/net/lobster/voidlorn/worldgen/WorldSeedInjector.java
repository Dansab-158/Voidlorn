package net.lobster.voidlorn.worldgen;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Swaps the placeholder {@code seed: 0} baked into the datapack JSON for the real world seed,
 * once, right before the server starts generating anything.
 *
 * <p>Datapack JSON has no way to reference "the actual world seed" as a value - vanilla only
 * reseeds the handful of density-function types it recognizes (via a visitor pass inside
 * {@code RandomState.create}), and our own custom types aren't among them, so the {@code seed}
 * field in the JSON would otherwise stay a dead constant forever, identical across every world.
 * Instead we reach into the exact {@code BiomeSource}/{@code DensityFunction} instances the End's
 * real {@code ChunkGenerator} is about to use and reseed them directly, in place, before any chunk
 * has generated - so every world gets its own archipelago layout again, the way you'd expect.
 */
public final class WorldSeedInjector {
    private WorldSeedInjector() {}

    // Offset applied to the real seed for the filler-islet layer, so its lattice doesn't hash
    // identically to the main archipelago layer's whenever their (unrelated-scale) lattice indices
    // happen to coincide - see ArchipelagoCellSampler.FILLER_SALT for the analogous per-lookup salt.
    private static final long FILLER_SEED_OFFSET = 0x51DE0FF5E7L;

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        long realSeed = server.getWorldData().worldGenOptions().seed();
        RegistryAccess registryAccess = server.registryAccess();

        LevelStem endStem = registryAccess.registryOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.END);
        ChunkGenerator generator = endStem.generator();

        BiomeSource biomeSource = generator.getBiomeSource();
        if (biomeSource instanceof ArchipelagoBiomeSource archipelago) {
            archipelago.setSeed(realSeed);
        }

        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            // finalDensity here is the tree exactly as decoded from end_archipelago.json - the
            // same object graph the live generator uses. Vanilla's own combinator nodes (add/mul/
            // interpolated/...) all recurse into their children on mapAll, so walking the whole
            // tree with a visitor is enough to reach our leaf regardless of how deep it's nested -
            // no need to hardcode the JSON's exact shape here.
            DensityFunction finalDensity = noiseGenerator.generatorSettings().value().noiseRouter().finalDensity();
            finalDensity.mapAll(densityFunction -> {
                if (densityFunction instanceof ArchipelagoIslandsDensityFunction archipelagoIslands) {
                    archipelagoIslands.setSeed(archipelagoIslands.isFiller() ? realSeed ^ FILLER_SEED_OFFSET : realSeed);
                }
                return densityFunction;
            });
        }
    }
}
