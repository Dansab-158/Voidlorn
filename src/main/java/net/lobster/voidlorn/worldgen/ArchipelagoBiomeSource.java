package net.lobster.voidlorn.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.lobster.voidlorn.worldgen.cell.ArchipelagoCellSampler;
import net.lobster.voidlorn.worldgen.cell.CellParameters;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class ArchipelagoBiomeSource extends BiomeSource {

    public static final MapCodec<ArchipelagoBiomeSource> CODEC =
            RecordCodecBuilder.mapCodec(inst -> inst.group(
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("archipelago_biomes")
                            .forGetter(s -> s.archipelagoBiomes),
                    Biome.CODEC.fieldOf("center_biome").forGetter(s -> s.centerBiome),
                    com.mojang.serialization.Codec.LONG.optionalFieldOf("seed", 0L).forGetter(s -> s.seed)
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

    private final HolderSet<Biome> archipelagoBiomes;
    private final Holder<Biome> centerBiome;
    private final long seed;
    private final ArchipelagoCellSampler sampler;

    public ArchipelagoBiomeSource(HolderSet<Biome> archipelagoBiomes, Holder<Biome> centerBiome, long seed) {
        this.archipelagoBiomes = archipelagoBiomes;
        this.centerBiome = centerBiome;
        this.seed = seed;
        this.sampler = new ArchipelagoCellSampler(seed);
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
        return Stream.concat(eligibleBiomes().stream(), Stream.of(centerBiome));
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler climateSampler) {
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
