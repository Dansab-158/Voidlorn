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
                            .forGetter(f -> f.seed)
            ).apply(inst, ArchipelagoIslandsDensityFunction::new));

    private static final KeyDispatchDataCodec<ArchipelagoIslandsDensityFunction> KEY_CODEC =
            KeyDispatchDataCodec.of(CODEC);

    // All tunable in playtest - none of these affect where islands are, just how they look.

    // How fast the hill/monolith noise wiggles across the surface. Higher = bumpier, more
    // frequent hills; lower = smoother, gentler terrain. This needs to be high enough that even a
    // small island (radius ~24-46) shows a few real bumps rather than a single gentle slope from
    // one edge to the other - at the old value (0.012, ~80-block wavelength) an island this size
    // only ever saw a fraction of one wave, so every island read as a flat, slightly tilted mesa
    // instead of having real hills. 0.045 gives a ~22-block wavelength, so even the smallest
    // islands get a couple of real peaks and dips.
    private static final double SHAPE_FREQ = 0.045;

    // How fast the thickness noise varies. Raised alongside SHAPE_FREQ for the same reason - the
    // old value barely changed across a single island either.
    private static final double THICK_FREQ = 0.035;

    // Frequency of the 3D noise that carves canyons/arches/overhangs into islands. XZ controls
    // how tightly carved features are spaced sideways; Y controls how tightly spaced vertically.
    private static final double CARVE_FREQ_XZ = 0.014;
    private static final double CARVE_FREQ_Y = 0.022;

    // How aggressively the carve noise can eat into (or occasionally add to) an island's shape.
    // Higher = more dramatic canyons/overhangs, but also a rougher, more broken-up silhouette.
    private static final double CARVE_AMP = 0.55;

    // Once a column's horizontal distance from an island's center passes this (very negative)
    // falloff value, we short-circuit straight to "void" without even bothering to compute the
    // rest of the shape - it's purely a performance guard, not something you'd normally tune.
    private static final double GAP_CUTOFF = -1.5;

    private final long seed;
    private final ArchipelagoCellSampler sampler;
    private final SimplexNoise shapeNoise;
    private final SimplexNoise thicknessNoise;
    private final SimplexNoise carveNoise;

    public ArchipelagoIslandsDensityFunction(long seed) {
        this.seed = seed;
        this.sampler = new ArchipelagoCellSampler(seed);
        this.shapeNoise = new SimplexNoise(new LegacyRandomSource(seed));
        this.thicknessNoise = new SimplexNoise(new LegacyRandomSource(seed + 0x1234567L));
        this.carveNoise = new SimplexNoise(new LegacyRandomSource(seed + 0x89ABCDEFL));
    }

    /** The noise-warped surface Y of the island nearest to (x,z). Exposed for tests. */
    public double surfaceYAt(int x, int z) {
        IslandCore core = nearestCoreAcrossCells(x, z);
        double shape = shapeNoise.getValue(x * SHAPE_FREQ, z * SHAPE_FREQ);
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
        int gx = Math.floorDiv(x, ArchipelagoCellSampler.CELL_SIZE);
        int gz = Math.floorDiv(z, ArchipelagoCellSampler.CELL_SIZE);

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
        IslandCore core = nearestCoreAcrossCells(x, z);

        double dx = x - core.x();
        double dz = z - core.z();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        double hMask = 1.0 - hDist / core.radius();     // 1 at center, 0 at edge, negative beyond
        if (hMask <= GAP_CUTOFF) {
            return -1.0;                                 // clearly outside any island => void gap
        }

        double shape = shapeNoise.getValue(x * SHAPE_FREQ, z * SHAPE_FREQ);
        double surfaceY = core.centerY() + shape * core.hillAmp();
        double thicknessN = thicknessNoise.getValue(x * THICK_FREQ, z * THICK_FREQ);
        // 0.65/0.35 keep the actual thickness between 65% and 100% of the core's height (never 0,
        // so an island can't randomly pinch down to nothing) while still letting it vary a bit.
        double thickness = core.height() * (0.65 + 0.35 * thicknessN);
        double vMask = 1.0 - Math.abs(y - surfaceY) / thickness;

        double blob = Math.min(hMask, vMask);            // solid only where BOTH proximities positive
        double carve = carveNoise.getValue(x * CARVE_FREQ_XZ, y * CARVE_FREQ_Y, z * CARVE_FREQ_XZ);
        double d = blob + CARVE_AMP * carve;
        return Mth.clamp(d, -1.0, 1.0);
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
