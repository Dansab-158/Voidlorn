package net.lobster.voidlorn.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.lobster.voidlorn.Config;
import net.lobster.voidlorn.worldgen.cell.ArchipelagoCellSampler;
import net.lobster.voidlorn.worldgen.cell.CellParameters;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class ArchipelagoBiomeSource extends BiomeSource {

    public static final MapCodec<ArchipelagoBiomeSource> CODEC =
            RecordCodecBuilder.mapCodec(inst -> inst.group(
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("archipelago_biomes")
                            .forGetter(s -> s.archipelagoBiomes),
                    Biome.CODEC.fieldOf("center_biome").forGetter(s -> s.centerBiome),
                    com.mojang.serialization.Codec.LONG.optionalFieldOf("seed", 0L).forGetter(s -> s.seed),
                    // Not a real datapack field - this just pulls the biome registry out of the
                    // decode context so we can build the vanilla-End fallback below. Same trick
                    // vanilla's own TheEndBiomeSource.CODEC uses for its five biome references.
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(inst, ArchipelagoBiomeSource::new));

    // How far from spawn columns still resolve to the vanilla center_biome. Keep this matching the
    // terrain-side outer_radius in origin_mask.json (currently also 1200) - if this radius and
    // that one drift apart, you'd get a ring of columns with vanilla-shaped terrain but an
    // archipelago biome, or vice versa.
    public static final long CENTER_RADIUS = 1200L;
    private static final long CENTER_RADIUS_SQ = CENTER_RADIUS * CENTER_RADIUS;

    // minecraft:small_end_islands rides along for free through #minecraft:is_end (part of our
    // default #c:is_end pool), but it comes with its own standalone vanilla feature
    // (minecraft:end_island) that scatters small round islands on top of whatever we generate,
    // independent of and uncoordinated with our own terrain - restricted to Y 55-70, which is
    // exactly the "small circular islands, mostly below Y100" bug this filter exists to fix.
    // Tags can't subtract entries from something they inherit, so this has to be filtered in code.
    private static final Set<ResourceKey<Biome>> EXCLUDED_BIOMES = Set.of(Biomes.SMALL_END_ISLANDS);

    // How other mods get their own End biomes into this pool: tag their biome into
    // #minecraft:is_end (or NeoForge's #c:is_end, which already includes the vanilla one) - our
    // own tags/worldgen/biome/archipelago_biomes.json points "archipelago_biomes" at #c:is_end,
    // so anything tagged that way shows up here automatically. Nothing on our side needs to change
    // for a new mod's biome to be picked up; the only exclusion is EXCLUDED_BIOMES above.

    private final HolderSet<Biome> archipelagoBiomes;
    private final Holder<Biome> centerBiome;
    private long seed;
    private final ArchipelagoCellSampler sampler;

    // Real vanilla End (same highlands/midlands/barrens/islands ring as an unmodded world), built
    // once up front so it's ready the moment Config.OVERRIDE_VANILLA_END gets checked. Used as a
    // genuine fallback - see getNoiseBiome/collectPossibleBiomes below - not an approximation.
    private final TheEndBiomeSource vanillaDelegate;

    public ArchipelagoBiomeSource(HolderSet<Biome> archipelagoBiomes, Holder<Biome> centerBiome, long seed,
                                   HolderGetter<Biome> biomeGetter) {
        this.archipelagoBiomes = archipelagoBiomes;
        this.centerBiome = centerBiome;
        this.seed = seed;
        this.sampler = new ArchipelagoCellSampler(seed);
        this.vanillaDelegate = TheEndBiomeSource.create(biomeGetter);
    }

    /**
     * Reseeds this source in place - see {@link ArchipelagoIslandsDensityFunction#setSeed} for why
     * this needs to be mutable rather than a fresh instance: whatever object is sitting in the
     * live {@code ChunkGenerator} when a chunk generates has to be the one that gets reseeded, and
     * we only get a handle to that exact instance after it's already built.
     */
    public void setSeed(long seed) {
        this.seed = seed;
        this.sampler.setSeed(seed);
    }

    /**
     * Biomes eligible for picking, with {@link #EXCLUDED_BIOMES} removed - computed fresh every
     * time rather than once in the constructor. This matters more than it looks: when
     * {@code archipelago_biomes} resolves to a tag (the normal case, {@code #voidlorn:archipelago_biomes}),
     * it decodes to a {@code HolderSet.Named} that starts out genuinely empty and only gets its
     * real contents filled in later, once tag binding finishes elsewhere in startup. Filtering
     * once in the constructor was silently filtering an empty list, then falling back to the
     * still-unbound (and later fully-populated) original set - so the exclusion looked like it
     * worked in every test that resolves the tag *after* startup completes, but did nothing for
     * the actual game. Filtering here instead always reads whatever is currently bound.
     */
    private List<Holder<Biome>> eligibleBiomes() {
        return archipelagoBiomes.stream()
                .filter(h -> h.unwrapKey().map(key -> !EXCLUDED_BIOMES.contains(key)).orElse(true))
                .toList();
    }

    /** True within {@link #CENTER_RADIUS} of (0,0) — the preserved vanilla dragon-island zone. */
    public static boolean isCentralColumn(int blockX, int blockZ) {
        return (long) blockX * blockX + (long) blockZ * blockZ <= CENTER_RADIUS_SQ;
    }

    /** Which pool entry a cell gets — a hash of its own identity, nothing else. */
    static int pickIndex(long cellId, int poolSize) {
        return Math.floorMod(cellId, poolSize);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        if (!Config.OVERRIDE_VANILLA_END.get()) {
            return vanillaDelegate.possibleBiomes().stream();
        }
        return Stream.concat(eligibleBiomes().stream(), Stream.of(centerBiome));
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler climateSampler) {
        if (!Config.OVERRIDE_VANILLA_END.get()) {
            return vanillaDelegate.getNoiseBiome(quartX, quartY, quartZ, climateSampler);
        }
        int blockX = QuartPos.toBlock(quartX);
        int blockZ = QuartPos.toBlock(quartZ);
        if (isCentralColumn(blockX, blockZ)) {
            return centerBiome;
        }
        CellParameters cell = sampler.cellAt(blockX, blockZ);
        List<Holder<Biome>> pool = eligibleBiomes();
        return pool.get(pickIndex(cell.cellId(), pool.size()));
    }
}
