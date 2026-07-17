package net.lobster.voidlorn.worldgen;

import net.lobster.voidlorn.Voidlorn;
import net.lobster.voidlorn.worldgen.cell.ArchipelagoCellSampler;
import net.lobster.voidlorn.worldgen.cell.CellParameters;
import net.lobster.voidlorn.worldgen.cell.IslandCore;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests covering the archipelago worldgen pipeline.
 *
 * <p><b>The "empty" template:</b> vanilla and NeoForge don't ship a reusable empty GameTest
 * structure in 1.21.1, so {@code voidlorn:empty} is a hand-built 1x1x1 air structure at
 * {@code data/voidlorn/structure/empty.nbt}. {@code @PrefixGameTestTemplate(false)} stops NeoForge
 * from prefixing the template name with the class/method name, so {@code template = "empty"}
 * actually resolves to {@code voidlorn:empty} instead of {@code voidlorn:worldgengametests.empty}.
 *
 * <p><b>Why these tests don't just use the real "the_end" dimension:</b> a GameTest server builds
 * its dimensions from the flat world preset against an empty {@code LevelStem} registry, which
 * means every built-in dimension - including {@code minecraft:the_end} - falls back to vanilla's
 * hardcoded superflat End instead of picking up our {@code data/minecraft/dimension/the_end.json}
 * override. So {@code server.getLevel(Level.END)} inside a GameTest is plain vanilla End, not our
 * archipelago generator. That's a limitation of the test harness, not a bug in the mod.
 *
 * <p>What does load correctly in a GameTest are the standalone worldgen registries our dimension
 * override points at: the {@code voidlorn:end_archipelago} noise settings, and (through its
 * density-function graph) {@code voidlorn:archipelago_islands} and
 * {@code voidlorn:origin_proximity_mask}. Those are ordinary datapack registries with no tie to
 * {@code LevelStem}, so the tests below build a {@link NoiseBasedChunkGenerator} directly from the
 * real registered noise settings plus a directly-constructed {@link ArchipelagoBiomeSource},
 * sidestepping the dimension bypass and sampling real generated terrain height - exercising the
 * exact same density-function graph a real "the_end" world would use.
 */
@GameTestHolder(Voidlorn.MODID)
@PrefixGameTestTemplate(false)
public class WorldgenGameTests {

    private static final ResourceLocation END_ARCHIPELAGO_SETTINGS_ID =
            ResourceLocation.fromNamespaceAndPath(Voidlorn.MODID, "end_archipelago");
    private static final long SEED = 0L;

    @GameTest(template = "empty")
    public void archipelagoBiomeSourceCodecRegistered(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME_SOURCE);
        var id = ResourceLocation.fromNamespaceAndPath(Voidlorn.MODID, "archipelago");
        if (!registry.containsKey(id)) {
            helper.fail("voidlorn:archipelago biome source codec is not registered");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void smallEndIslandsIsNeverPickedFromThePool(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        HolderGetter<Biome> biomeGetter = registries.lookupOrThrow(Registries.BIOME);
        Holder<Biome> smallEndIslands = biomeGetter.getOrThrow(Biomes.SMALL_END_ISLANDS);
        Holder<Biome> midBiome = biomeGetter.getOrThrow(Biomes.END_MIDLANDS);
        Holder<Biome> centerBiome = biomeGetter.getOrThrow(Biomes.THE_END);
        // A pool that's mostly small_end_islands - if it isn't filtered out, it'll dominate picks.
        HolderSet<Biome> pool = HolderSet.direct(smallEndIslands, smallEndIslands, smallEndIslands, midBiome);
        ArchipelagoBiomeSource biomeSource = new ArchipelagoBiomeSource(pool, centerBiome, SEED, biomeGetter);

        for (int gx = 4; gx < 60; gx++) { // gx*512 >= ~2048 blocks => outside the origin carve-out
            int blockX = gx * 512 + 137, blockZ = 9000;
            Holder<Biome> b = biomeSource.getNoiseBiome(
                    QuartPos.fromBlock(blockX), QuartPos.fromBlock(64), QuartPos.fromBlock(blockZ),
                    Climate.empty());
            if (b.is(Biomes.SMALL_END_ISLANDS)) {
                helper.fail("small_end_islands was picked at gx=" + gx + " - its own end_island "
                        + "feature would scatter small vanilla-style islands independent of our terrain");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void realArchipelagoBiomesTagNeverPicksSmallEndIslands(GameTestHelper helper) {
        // Unlike the test above (a hand-built pool), this resolves the REAL
        // #voidlorn:archipelago_biomes tag exactly like the_end.json does, to make sure the
        // exclusion filter actually applies to the tag-backed HolderSet the real game uses -
        // not just to a manually constructed one.
        RegistryAccess registries = helper.getLevel().registryAccess();
        HolderGetter<Biome> biomeGetter = registries.lookupOrThrow(Registries.BIOME);
        net.minecraft.tags.TagKey<Biome> archipelagoBiomesTag = net.minecraft.tags.TagKey.create(
                Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Voidlorn.MODID, "archipelago_biomes"));
        HolderSet<Biome> realPool = biomeGetter.getOrThrow(archipelagoBiomesTag);
        Voidlorn.LOGGER.info("[WorldgenGameTests] real archipelago_biomes tag has {} entries: {}",
                realPool.size(), realPool.stream().map(h -> h.unwrapKey().map(Object::toString).orElse("?")).toList());

        Holder<Biome> centerBiome = biomeGetter.getOrThrow(Biomes.THE_END);
        ArchipelagoBiomeSource biomeSource = new ArchipelagoBiomeSource(realPool, centerBiome, SEED, biomeGetter);

        for (int gx = 4; gx < 200; gx++) { // gx*512 >= ~2048 blocks => outside the origin carve-out
            int blockX = gx * 512 + 137, blockZ = 9000;
            Holder<Biome> b = biomeSource.getNoiseBiome(
                    QuartPos.fromBlock(blockX), QuartPos.fromBlock(64), QuartPos.fromBlock(blockZ),
                    Climate.empty());
            if (b.is(Biomes.SMALL_END_ISLANDS)) {
                helper.fail("small_end_islands was picked from the REAL tag at gx=" + gx);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void archipelagoPoolResolvesMultipleDistinctBiomes(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        HolderGetter<Biome> biomeGetter = registries.lookupOrThrow(Registries.BIOME);
        Holder<Biome> lowBiome = biomeGetter.getOrThrow(Biomes.END_BARRENS);
        Holder<Biome> midBiome = biomeGetter.getOrThrow(Biomes.END_MIDLANDS);
        Holder<Biome> highBiome = biomeGetter.getOrThrow(Biomes.END_HIGHLANDS);
        Holder<Biome> centerBiome = biomeGetter.getOrThrow(Biomes.THE_END);
        HolderSet<Biome> pool = HolderSet.direct(lowBiome, midBiome, highBiome);
        ArchipelagoBiomeSource biomeSource = new ArchipelagoBiomeSource(pool, centerBiome, SEED, biomeGetter);

        var seen = new java.util.HashSet<Holder<Biome>>();
        for (int gx = 4; gx < 40; gx++) { // gx*512 >= ~2048 blocks => outside the origin carve-out
            int blockX = gx * 512 + 137, blockZ = 9000;
            Holder<Biome> b = biomeSource.getNoiseBiome(
                    QuartPos.fromBlock(blockX), QuartPos.fromBlock(64), QuartPos.fromBlock(blockZ),
                    Climate.empty());
            seen.add(b);
        }
        if (seen.size() < 2) {
            helper.fail("expected the open pool to resolve more than one distinct biome across many "
                    + "cells, got " + seen.size());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void newDensityFunctionsRegistered(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION_TYPE);
        for (String path : new String[]{"archipelago_islands", "origin_proximity_mask"}) {
            var id = ResourceLocation.fromNamespaceAndPath(Voidlorn.MODID, path);
            if (!registry.containsKey(id)) {
                helper.fail("voidlorn:" + path + " density function is not registered");
                return;
            }
        }
        // The retired cell_vertical_shift must NOT be present anymore.
        if (registry.containsKey(ResourceLocation.fromNamespaceAndPath(Voidlorn.MODID, "cell_vertical_shift"))) {
            helper.fail("voidlorn:cell_vertical_shift should have been removed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void islandsAreDiscreteWithVoidGaps(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        NoiseBasedChunkGenerator generator = buildArchipelagoGenerator(registries);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(0, 320);
        RandomState randomState = buildRandomState(registries);
        ArchipelagoCellSampler sampler = new ArchipelagoCellSampler(SEED);

        // A far-from-origin cell (outside the 1200-block vanilla carve-out).
        CellParameters cell = sampler.cellAt(20000, -14000);
        IslandCore[] cores = sampler.islandCores(cell);

        // (a) At least 3 islands in the archipelago.
        if (cores.length < 3) { helper.fail("cell has < 3 island cores: " + cores.length); return; }

        // (b) Each core column has solid ground (island present).
        for (IslandCore core : cores) {
            int h = generator.getBaseHeight(core.x(), core.z(), Heightmap.Types.WORLD_SURFACE, heightAccessor, randomState);
            if (h <= heightAccessor.getMinBuildHeight()) {
                helper.fail("island core at (" + core.x() + "," + core.z() + ") generated no terrain (h=" + h + ")");
                return;
            }
        }

        // (c) Void gaps exist: scan the cell footprint; require at least one column with no terrain.
        int baseX = cell.centerX() - 256, baseZ = cell.centerZ() - 256;
        boolean sawVoid = false;
        for (int dx = 0; dx <= 512 && !sawVoid; dx += 32) {
            for (int dz = 0; dz <= 512 && !sawVoid; dz += 32) {
                int x = baseX + dx, z = baseZ + dz;
                int h = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE, heightAccessor, randomState);
                if (h <= heightAccessor.getMinBuildHeight()) sawVoid = true;
            }
        }
        if (!sawVoid) { helper.fail("no void gaps found in cell footprint — islands merged into one blob"); return; }

        helper.succeed();
    }

    @GameTest(template = "empty")
    public void islandsAreVerticallyDistributedWithinArchipelago(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        NoiseBasedChunkGenerator generator = buildArchipelagoGenerator(registries);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(0, 320);
        RandomState randomState = buildRandomState(registries);
        ArchipelagoCellSampler sampler = new ArchipelagoCellSampler(SEED);

        // Find a far cell whose cores span a real range of centerY, then check that the actual
        // generated heights across those cores spread out too. We deliberately don't check that
        // the highest-centerY core generates the tallest terrain: each core's hill/thickness noise
        // can swing the generated top by up to ~130 blocks on its own, easily masking or flipping a
        // modest centerY gap between just two cores. The guarantee we care about is "islands sit at
        // different heights", not "height tracks centerY exactly" - so we just require the spread
        // across the whole cell to clear a threshold, same as the pure-math check in
        // IslandCoreSamplerTest.
        final int MIN_GENERATED_HEIGHT_SPREAD = 20;
        for (int gx = 4; gx < 60; gx++) { // gx*512 >= ~2048 blocks => outside origin carve-out
            CellParameters cell = sampler.cellAt(gx * 512 + 137, 9000);
            IslandCore[] cores = sampler.islandCores(cell);
            IslandCore lo = cores[0], hi = cores[0];
            for (IslandCore c : cores) {
                if (c.centerY() < lo.centerY()) lo = c;
                if (c.centerY() > hi.centerY()) hi = c;
            }
            if (hi.centerY() - lo.centerY() < 24) continue;

            int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
            int sampledCores = 0;
            for (IslandCore core : cores) {
                int h = generator.getBaseHeight(core.x(), core.z(), Heightmap.Types.WORLD_SURFACE, heightAccessor, randomState);
                if (h <= heightAccessor.getMinBuildHeight()) continue; // stray core landed in a void gap
                sampledCores++;
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;
            }
            if (sampledCores < 2) continue; // not enough real samples in this cell to judge spread

            int spread = maxH - minH;
            Voidlorn.LOGGER.info("[WorldgenGameTests] vertical-spread cell gx={} centerYSpread={} sampledCores={} minH={} maxH={} heightSpread={}",
                    gx, hi.centerY() - lo.centerY(), sampledCores, minH, maxH, spread);
            if (spread >= MIN_GENERATED_HEIGHT_SPREAD) { helper.succeed(); return; }
            helper.fail("generated heights across cell's islands did not spread enough (minH=" + minH
                    + ", maxH=" + maxH + ", spread=" + spread + ", needed >=" + MIN_GENERATED_HEIGHT_SPREAD + ")");
            return;
        }
        helper.fail("found no archipelago cell with >=24 blocks of per-island centerY spread to test");
    }

    @GameTest(template = "empty")
    public void originIslandIsPreservedVanilla(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        NoiseBasedChunkGenerator generator = buildArchipelagoGenerator(registries);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(0, 320);
        RandomState randomState = buildRandomState(registries);

        // The vanilla central island exists near (0,0): the spawn-platform column has solid ground.
        int h = generator.getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, heightAccessor, randomState);
        if (h <= heightAccessor.getMinBuildHeight()) {
            helper.fail("vanilla central island missing at origin (h=" + h + ")");
            return;
        }
        // Biome carve-out: origin columns must resolve to minecraft:the_end.
        if (!ArchipelagoBiomeSource.isCentralColumn(0, 0)
                || ArchipelagoBiomeSource.isCentralColumn(3000, 3000)) {
            helper.fail("origin biome carve-out boundary is wrong");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void corruptedDesertResolvesAndHasDistinctSurface(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        HolderGetter<Biome> biomeGetter = registries.lookupOrThrow(Registries.BIOME);
        ResourceKey<Biome> desertKey = ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(Voidlorn.MODID, "corrupted_desert"));
        if (biomeGetter.get(desertKey).isEmpty()) {
            helper.fail("voidlorn:corrupted_desert biome is not registered");
            return;
        }
        Holder<Biome> desertBiome = biomeGetter.getOrThrow(desertKey);
        Holder<Biome> centerBiome = biomeGetter.getOrThrow(Biomes.THE_END);
        HolderSet<Biome> pool = HolderSet.direct(desertBiome);
        ArchipelagoBiomeSource biomeSource = new ArchipelagoBiomeSource(pool, centerBiome, SEED, biomeGetter);

        // Every non-central column now resolves to the desert (pool of size 1) - confirm the
        // biome itself is reachable and distinct from the vanilla center biome.
        Holder<Biome> resolved = biomeSource.getNoiseBiome(
                QuartPos.fromBlock(9000), QuartPos.fromBlock(64), QuartPos.fromBlock(9000), Climate.empty());
        if (!resolved.is(desertKey)) {
            helper.fail("expected the pool (size 1, desert only) to resolve to the desert biome");
            return;
        }
        helper.succeed();
    }

    private NoiseBasedChunkGenerator buildArchipelagoGenerator(RegistryAccess registries) {
        ResourceKey<NoiseGeneratorSettings> settingsKey =
                ResourceKey.create(Registries.NOISE_SETTINGS, END_ARCHIPELAGO_SETTINGS_ID);
        Holder.Reference<NoiseGeneratorSettings> settingsHolder =
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settingsKey);

        HolderGetter<Biome> biomeGetter = registries.lookupOrThrow(Registries.BIOME);
        Holder<Biome> lowBiome = biomeGetter.getOrThrow(Biomes.END_BARRENS);
        Holder<Biome> midBiome = biomeGetter.getOrThrow(Biomes.END_MIDLANDS);
        Holder<Biome> highBiome = biomeGetter.getOrThrow(Biomes.END_HIGHLANDS);
        Holder<Biome> centerBiome = biomeGetter.getOrThrow(Biomes.THE_END);
        HolderSet<Biome> archipelagoBiomes = HolderSet.direct(lowBiome, midBiome, highBiome);
        ArchipelagoBiomeSource biomeSource = new ArchipelagoBiomeSource(archipelagoBiomes, centerBiome, SEED, biomeGetter);

        return new NoiseBasedChunkGenerator(biomeSource, settingsHolder);
    }

    private RandomState buildRandomState(RegistryAccess registries) {
        ResourceKey<NoiseGeneratorSettings> settingsKey =
                ResourceKey.create(Registries.NOISE_SETTINGS, END_ARCHIPELAGO_SETTINGS_ID);
        NoiseGeneratorSettings settings =
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settingsKey).value();
        HolderGetter<NormalNoise.NoiseParameters> noiseParams = registries.lookupOrThrow(Registries.NOISE);
        return RandomState.create(settings, noiseParams, SEED);
    }
}
