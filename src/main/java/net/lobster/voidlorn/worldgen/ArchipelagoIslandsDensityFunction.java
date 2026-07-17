package net.lobster.voidlorn.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.lobster.voidlorn.worldgen.cell.ArchipelagoCellSampler;
import net.lobster.voidlorn.worldgen.cell.CellParameters;
import net.lobster.voidlorn.worldgen.cell.IslandCore;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Renders the actual 3D shape of the archipelago's islands. For each column, it looks up the
 * nearest island core (via {@link ArchipelagoCellSampler}) and shapes the terrain around it:
 * solid near the core, fading out to void past its radius so islands stay discrete instead of
 * merging into one landmass, with the vertical band centered on that core's own height so
 * different islands in the same archipelago can sit at different elevations. A 2D noise warps
 * the top into hills and monoliths, a second 2D noise varies how thick the island is, and a 3D
 * noise carves in canyons, arches and overhangs.
 *
 * <p>Like vanilla's {@code DensityFunctions.EndIslandDensityFunction}, this owns its own
 * {@link SimplexNoise} instead of relying on the shared noise router, which keeps it a
 * self-contained leaf function that's easy to unit test. Solid where the output is {@code > 0}.
 *
 * <p>Everything here is continuous noise - no hard cutoffs or box edges - so islands read as
 * naturally sinuous rather than blocky.
 */
public final class ArchipelagoIslandsDensityFunction implements DensityFunction.SimpleFunction {

    public static final MapCodec<ArchipelagoIslandsDensityFunction> CODEC =
            RecordCodecBuilder.mapCodec(inst -> inst.group(
                    com.mojang.serialization.Codec.LONG.optionalFieldOf("seed", 0L)
                            .forGetter(f -> f.seed),
                    // true picks the small filler-islet profile instead of the real archipelago one -
                    // see filler_islets_shaped.json, which is the only place this is set to true.
                    com.mojang.serialization.Codec.BOOL.optionalFieldOf("filler", false)
                            .forGetter(f -> f.filler),
                    // Same pool the biome source resolves from (#voidlorn:archipelago_biomes) - kept
                    // here purely so this function can work out "what biome would be here" for the
                    // flattening check below, via the exact same selection the biome source uses.
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("archipelago_biomes")
                            .forGetter(f -> f.archipelagoBiomes),
                    // Any biome in this set gets noticeably calmer hills (see hillAmpScale) - this is
                    // how corrupted_desert gets its "low hills, no mountains" look without
                    // changing the shared archipelago-wide hill settings every other biome uses.
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("flat_terrain_biomes")
                            .forGetter(f -> f.flatTerrainBiomes)
            ).apply(inst, ArchipelagoIslandsDensityFunction::new));

    private static final KeyDispatchDataCodec<ArchipelagoIslandsDensityFunction> KEY_CODEC =
            KeyDispatchDataCodec.of(CODEC);

    // The shape/thickness/carve numbers below all live in WorldgenTuning now (backed by config -
    // see Config.java's archipelagoShapeFreq/archipelagoThickFreq/archipelagoCarveFreq*/
    // archipelagoCarveAmp entries for what each one does and why). Only GAP_CUTOFF stays a plain
    // constant here, since it's a performance guard rather than a "feel" knob.

    // Once a column's horizontal distance from an island's center passes this (very negative)
    // falloff value, we short-circuit straight to "void" without even bothering to compute the
    // rest of the shape - it's purely a performance guard, not something you'd normally tune.
    private static final double GAP_CUTOFF = -1.5;

    // Any biome that shows up more calmly than the archipelago-wide default - see hillAmpScale.
    private static final double FLAT_HILL_SCALE = 0.15;

    // Must match end_archipelago.json's noise.height (min_y 0 => this is also the build height
    // ceiling). Vanilla structures like the end city are placed by dedicated Java logic that has
    // no data-driven awareness of a taller-than-vanilla dimension: they just start building
    // upward from the terrain surface and let anything past the world ceiling get silently
    // clipped, which reads in-game as a truncated end city. We can't fix that placement logic
    // from a datapack, so instead this guarantees enough headroom above every island's surface -
    // capping raw centerY+hillAmp (which config sliders could otherwise push arbitrarily high,
    // even past the ceiling itself) well below it - for the tallest vanilla structures to fit
    // without ever touching the ceiling. At the default config this clamp never actually engages
    // (the tallest default island sits comfortably under it); it only kicks in if hill height/
    // archipelago altitude sliders get pushed toward their extreme configured maximums.
    private static final double WORLD_CEILING_Y = 320.0;
    private static final double STRUCTURE_HEADROOM = 100.0;
    private static final double MAX_SURFACE_Y = WORLD_CEILING_Y - STRUCTURE_HEADROOM;

    private long seed;
    private final boolean filler;
    private final HolderSet<Biome> archipelagoBiomes;
    private final HolderSet<Biome> flatTerrainBiomes;
    private final ArchipelagoCellSampler sampler;
    private SimplexNoise shapeNoise;
    private SimplexNoise thicknessNoise;
    private SimplexNoise carveNoise;
    private SimplexNoise valleyNoise;

    public ArchipelagoIslandsDensityFunction(long seed) {
        this(seed, false, HolderSet.direct(), HolderSet.direct());
    }

    /** @param filler true for the small stepping-stone islet layer, false for the real archipelagos. */
    public ArchipelagoIslandsDensityFunction(long seed, boolean filler) {
        this(seed, filler, HolderSet.direct(), HolderSet.direct());
    }

    public ArchipelagoIslandsDensityFunction(long seed, boolean filler,
                                              HolderSet<Biome> archipelagoBiomes, HolderSet<Biome> flatTerrainBiomes) {
        this.seed = seed;
        this.filler = filler;
        this.archipelagoBiomes = archipelagoBiomes;
        this.flatTerrainBiomes = flatTerrainBiomes;
        this.sampler = new ArchipelagoCellSampler(seed, filler);
        this.shapeNoise = new SimplexNoise(new LegacyRandomSource(seed));
        this.thicknessNoise = new SimplexNoise(new LegacyRandomSource(seed + 0x1234567L));
        this.carveNoise = new SimplexNoise(new LegacyRandomSource(seed + 0x89ABCDEFL));
        this.valleyNoise = new SimplexNoise(new LegacyRandomSource(seed + 0xA11EF00DL));
    }

    /** Whether this instance renders the small filler islets rather than the real archipelagos. */
    public boolean isFiller() {
        return filler;
    }

    private WorldgenTuning.IslandProfile profile() {
        WorldgenTuning.Snapshot tuning = WorldgenTuning.ACTIVE;
        return filler ? tuning.filler() : tuning.main();
    }

    /**
     * Reseeds this function in place - rebuilds the sampler and all three owned noise sources
     * from the new seed. Used once, at server start, to swap in the real world seed (see
     * {@link WorldSeedInjector}) instead of the placeholder {@code seed: 0} baked into
     * {@code archipelago_shaped.json}. Safe to call even though the fields it touches used to be
     * {@code final} - nothing reads them concurrently with this, since it only ever runs once,
     * before any chunk has had a chance to generate.
     */
    public void setSeed(long seed) {
        this.seed = seed;
        this.sampler.setSeed(seed);
        this.shapeNoise = new SimplexNoise(new LegacyRandomSource(seed));
        this.thicknessNoise = new SimplexNoise(new LegacyRandomSource(seed + 0x1234567L));
        this.carveNoise = new SimplexNoise(new LegacyRandomSource(seed + 0x89ABCDEFL));
        this.valleyNoise = new SimplexNoise(new LegacyRandomSource(seed + 0xA11EF00DL));
    }

    /** The noise-warped surface Y of the island nearest to (x,z). Exposed for tests. */
    public double surfaceYAt(int x, int z) {
        CoreLookup lookup = nearestCoreAcrossCells(x, z);
        WorldgenTuning.Snapshot tuning = WorldgenTuning.ACTIVE;
        double shape = shapeNoise.getValue(x * tuning.shapeFreq(), z * tuning.shapeFreq());
        double scale = lookup.core().haven() ? 1.0 : hillAmpScale(lookup.cellId());
        double valleyN = valleyNoise.getValue(x * tuning.valleyFreq(), z * tuning.valleyFreq());
        double valleyDepth = Math.max(0.0, -valleyN) * tuning.valleyAmp();
        double surfaceY = lookup.core().centerY() + shape * lookup.core().hillAmp() * scale - valleyDepth;
        return Math.min(surfaceY, MAX_SURFACE_Y);
    }

    /**
     * How much to shrink a core's {@code hillAmp} by, for biomes that want calmer terrain than the
     * archipelago-wide default (see {@link #FLAT_HILL_SCALE}). Resolves "what biome is at this
     * cell" the exact same way {@link ArchipelagoBiomeSource} does, via
     * {@link ArchipelagoBiomeSource#resolvePoolBiome} - so this can never disagree with what biome
     * actually ends up there. Short-circuits to 1.0 (no scaling) whenever no biome has opted into
     * flatter terrain, which is also what keeps the plain seed-only constructors (used by unit
     * tests, which pass empty holder sets for both) from ever needing to resolve a pool at all.
     *
     * <p>Callers must skip this entirely for haven cores (pass a flat {@code 1.0} instead of
     * calling this) rather than folding a haven check in here - a haven's whole point is full
     * terrain regardless of which biome happens to render on it, and this only knows about biome,
     * not which core asked. Missing that distinction is exactly what let a haven that happened to
     * land on a flat-terrain biome (e.g. corrupted_desert) get crushed down to a near-flat plateau
     * despite {@code havenHillAmp} being set high - confirmed by that being the only way this
     * method's math can silently combine with a haven's own hillAmp like that.
     */
    private double hillAmpScale(long cellId) {
        if (flatTerrainBiomes.size() == 0) {
            return 1.0;
        }
        Holder<Biome> resolved = ArchipelagoBiomeSource.resolvePoolBiome(archipelagoBiomes, cellId);
        return flatTerrainBiomes.contains(resolved) ? FLAT_HILL_SCALE : 1.0;
    }

    /**
     * Finds the island core actually nearest to (x,z), searching the full 3x3 lattice
     * neighborhood instead of trusting {@code cellAt(x,z)} to pick the right cell.
     *
     * <p>The reason this matters: cores are jittered up to {@code CORE_DIST_MAX} (168 blocks)
     * away from their parent cell's center, so a core sitting near a Voronoi boundary can end up
     * geometrically closer to a neighboring cell's site than to its own. If we only checked the
     * single cell {@code cellAt(x,z)} returns, we'd miss that core entirely - it would show up as
     * void right at its own center, chopping the island off wherever a cell boundary crosses it.
     * Checking all nine neighboring lattice cells and picking the globally-closest core avoids
     * that. Since {@code CORE_DIST_MAX < CELL_SIZE}, a 3x3 window is always enough - a core can
     * never wander far enough to need a 5x5 search.
     *
     * <p>The per-cell parameters for each of the nine candidates come from
     * {@link ArchipelagoCellSampler#cellForLattice}, so there's one shared implementation of the
     * site hash/jitter math instead of a second copy that could drift out of sync.
     */
    /** An island core plus the cellId of the archipelago cell it actually came from. */
    private record CoreLookup(IslandCore core, long cellId) {}

    private CoreLookup nearestCoreAcrossCells(int x, int z) {
        int cellSize = profile().cellSize();
        int gx = Math.floorDiv(x, cellSize);
        int gz = Math.floorDiv(z, cellSize);

        IslandCore best = null;
        long bestCellId = 0L;
        double bestSq = Double.MAX_VALUE;
        for (int lz = gz - 1; lz <= gz + 1; lz++) {
            for (int lx = gx - 1; lx <= gx + 1; lx++) {
                CellParameters cell = sampler.cellForLattice(lx, lz);
                for (IslandCore c : sampler.islandCores(cell)) {
                    double dx = x - c.x();
                    double dz = z - c.z();
                    double sq = dx * dx + dz * dz;
                    if (sq < bestSq) {
                        bestSq = sq;
                        best = c;
                        bestCellId = cell.cellId();
                    }
                }
            }
        }
        return new CoreLookup(best, bestCellId);
    }

    /**
     * Pure, unit-testable density. Solid where {@code > 0}; clamped to [-1, 1].
     *
     * <p>Takes the blob value from EVERY nearby core (not just whichever one is nearest by raw
     * distance) and keeps whichever is most solid at this exact column/height. Picking only the
     * nearest core used to cut a hard seam wherever two cores' influence zones overlapped - which,
     * measured directly, happens far more than you'd expect (the per-core placement in
     * {@link ArchipelagoCellSampler} has no mutual-exclusion between siblings or across a cell
     * boundary) - since two overlapping cores almost always disagree on surface height/thickness,
     * that seam showed up as an ugly, unnatural break right where two islands met. Taking the max
     * blob across all of them instead means overlapping islands blend into one smooth landmass:
     * each core's own blob already fades continuously to zero at its own edge (see
     * {@link #smoothTaper}), so the max of several continuous fields is itself continuous
     * everywhere - no seam, by construction, regardless of how the cores happen to be arranged.
     */
    public double density(int x, int y, int z) {
        WorldgenTuning.Snapshot tuning = WorldgenTuning.ACTIVE;
        double shape = shapeNoise.getValue(x * tuning.shapeFreq(), z * tuning.shapeFreq());
        // Only sampled/used for haven cores (see the blend below) - a second, much lower frequency
        // pass so a haven's much bigger radius gets a handful of genuinely large mountains/valleys
        // instead of the same small bump size as a normal island just tiled across more space.
        double havenShape = shapeNoise.getValue(x * tuning.havenShapeFreq(), z * tuning.havenShapeFreq());
        double thicknessN = thicknessNoise.getValue(x * tuning.thickFreq(), z * tuning.thickFreq());
        // Independent of the hill shape noise above on purpose (see valleyFrequency/valleyDepth in
        // Config.java) - only the negative excursions carve, so a valley shows up wherever this
        // noise happens to dip low, with no correlation to where the hills happen to be.
        double valleyN = valleyNoise.getValue(x * tuning.valleyFreq(), z * tuning.valleyFreq());
        double valleyDepth = Math.max(0.0, -valleyN) * tuning.valleyAmp();

        int cellSize = profile().cellSize();
        int gx = Math.floorDiv(x, cellSize);
        int gz = Math.floorDiv(z, cellSize);

        double bestBlob = -1.0;
        for (int lz = gz - 1; lz <= gz + 1; lz++) {
            for (int lx = gx - 1; lx <= gx + 1; lx++) {
                CellParameters cell = sampler.cellForLattice(lx, lz);
                for (IslandCore c : sampler.islandCores(cell)) {
                    double dx = x - c.x();
                    double dz = z - c.z();
                    double hDist = Math.sqrt(dx * dx + dz * dz);
                    double hMask = smoothTaper(hDist / c.radius());
                    if (hMask <= GAP_CUTOFF) {
                        continue; // cheap reject before the more expensive surfaceY/vMask work below
                    }

                    // Mostly the large-scale pass, a little of the normal one layered on top for
                    // fine detail - see the havenShape comment above.
                    double coreShape = c.haven() ? (0.7 * havenShape + 0.3 * shape) : shape;
                    // 0.65/0.35 keep the actual thickness between 65% and 100% of the core's
                    // height (never 0, so an island can't randomly pinch down to nothing) while
                    // still letting it vary a bit.
                    double biomeScale = c.haven() ? 1.0 : hillAmpScale(cell.cellId());
                    double surfaceY = Math.min(c.centerY() + coreShape * c.hillAmp() * biomeScale - valleyDepth, MAX_SURFACE_Y);
                    double thickness = c.height() * (0.65 + 0.35 * thicknessN);
                    double vMask = smoothTaper(Math.abs(y - surfaceY) / thickness);
                    double blob = Math.min(hMask, vMask);   // solid only where BOTH proximities positive

                    bestBlob = Math.max(bestBlob, blob);
                }
            }
        }

        if (bestBlob <= GAP_CUTOFF) {
            return -1.0; // clearly outside every nearby island => void gap
        }

        double carve = carveNoise.getValue(x * tuning.carveFreqXZ(), y * tuning.carveFreqY(), z * tuning.carveFreqXZ());
        // A void needs d < 0, i.e. carveAmp*carve < -bestBlob - since bestBlob's own max is 1.0
        // (smoothTaper never exceeds that) and stays above ~0.57 for a wide inner fraction of any
        // core's radius/thickness, carveAmp needs to reach roughly 1.0 for even the most extreme
        // carve value to ever punch a hole near a core's own center - below that, carving is
        // mathematically incapable of reaching there at all, not from any deliberate exclusion.
        double d = bestBlob + tuning.carveAmp() * carve;
        return Mth.clamp(d, -1.0, 1.0);
    }

    /**
     * Rounds off the sharp corner a plain linear falloff ({@code 1 - t}) has right at its peak
     * (t=0) and at its edge (t=1) - a bare linear falloff is a cone/tent shape, with a pointed
     * apex and a sharp crease where it meets zero, which is what was reading as oddly steep,
     * almost squared-off hills and cliffs. This is the same value at {@code t=0.5} as the old
     * linear formula (0.5), it just eases smoothly into and out of the ends instead of turning a
     * hard corner there.
     *
     * <p>Left unchanged for {@code t >= 1} (still plain {@code 1 - t}) so the "how far outside an
     * island's radius" math that {@link #GAP_CUTOFF} relies on keeps behaving exactly like before -
     * only the inside-the-island shape changes.
     */
    private static double smoothTaper(double t) {
        if (t <= 0.0) {
            return 1.0;
        }
        if (t >= 1.0) {
            return 1.0 - t;
        }
        return 1.0 - (3.0 * t * t - 2.0 * t * t * t);
    }

    @Override
    public double compute(FunctionContext ctx) {
        return density(ctx.blockX(), ctx.blockY(), ctx.blockZ());
    }

    @Override
    public double minValue() { return -1.0; }

    @Override
    public double maxValue() { return 1.0; }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
