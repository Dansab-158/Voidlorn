package net.lobster.voidlorn.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.lobster.voidlorn.worldgen.cell.ArchipelagoCellSampler;
import net.lobster.voidlorn.worldgen.cell.CellParameters;
import net.lobster.voidlorn.worldgen.cell.IslandCore;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
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
                            .forGetter(f -> f.filler)
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

    private long seed;
    private final boolean filler;
    private final ArchipelagoCellSampler sampler;
    private SimplexNoise shapeNoise;
    private SimplexNoise thicknessNoise;
    private SimplexNoise carveNoise;

    public ArchipelagoIslandsDensityFunction(long seed) {
        this(seed, false);
    }

    /** @param filler true for the small stepping-stone islet layer, false for the real archipelagos. */
    public ArchipelagoIslandsDensityFunction(long seed, boolean filler) {
        this.seed = seed;
        this.filler = filler;
        this.sampler = new ArchipelagoCellSampler(seed, filler);
        this.shapeNoise = new SimplexNoise(new LegacyRandomSource(seed));
        this.thicknessNoise = new SimplexNoise(new LegacyRandomSource(seed + 0x1234567L));
        this.carveNoise = new SimplexNoise(new LegacyRandomSource(seed + 0x89ABCDEFL));
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
    }

    /** The noise-warped surface Y of the island nearest to (x,z). Exposed for tests. */
    public double surfaceYAt(int x, int z) {
        IslandCore core = nearestCoreAcrossCells(x, z);
        double shapeFreq = WorldgenTuning.ACTIVE.shapeFreq();
        double shape = shapeNoise.getValue(x * shapeFreq, z * shapeFreq);
        return core.centerY() + shape * core.hillAmp();
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
    private IslandCore nearestCoreAcrossCells(int x, int z) {
        int cellSize = profile().cellSize();
        int gx = Math.floorDiv(x, cellSize);
        int gz = Math.floorDiv(z, cellSize);

        IslandCore best = null;
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
                    }
                }
            }
        }
        return best;
    }

    /** Pure, unit-testable density. Solid where {@code > 0}; clamped to [-1, 1]. */
    public double density(int x, int y, int z) {
        WorldgenTuning.Snapshot tuning = WorldgenTuning.ACTIVE;
        IslandCore core = nearestCoreAcrossCells(x, z);

        double dx = x - core.x();
        double dz = z - core.z();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        double hMask = smoothTaper(hDist / core.radius());     // 1 at center, 0 at edge, negative beyond
        if (hMask <= GAP_CUTOFF) {
            return -1.0;                                 // clearly outside any island => void gap
        }

        double shape = shapeNoise.getValue(x * tuning.shapeFreq(), z * tuning.shapeFreq());
        double surfaceY = core.centerY() + shape * core.hillAmp();
        double thicknessN = thicknessNoise.getValue(x * tuning.thickFreq(), z * tuning.thickFreq());
        // 0.65/0.35 keep the actual thickness between 65% and 100% of the core's height (never 0,
        // so an island can't randomly pinch down to nothing) while still letting it vary a bit.
        double thickness = core.height() * (0.65 + 0.35 * thicknessN);
        double vMask = smoothTaper(Math.abs(y - surfaceY) / thickness);

        double blob = Math.min(hMask, vMask);            // solid only where BOTH proximities positive
        double carve = carveNoise.getValue(x * tuning.carveFreqXZ(), y * tuning.carveFreqY(), z * tuning.carveFreqXZ());
        double d = blob + tuning.carveAmp() * carve;
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
