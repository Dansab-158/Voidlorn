package net.lobster.voidlorn.worldgen.cell;

import net.lobster.voidlorn.worldgen.WorldgenTuning;

/**
 * Splits the world into a jittered grid of cells (spacing controlled by
 * {@code archipelagoCellSize} in config), each with one randomly offset "site". {@link #cellAt}
 * finds whichever site is nearest to a given point and returns its parameters. Everything here is
 * a hash of the site's lattice coordinates plus the seed, so results are deterministic and never
 * depend on distance from the world origin.
 *
 * <p>The actual spacing/count/size numbers all live in {@link WorldgenTuning} now (backed by
 * config, see {@code Config.java}) rather than as constants on this class - every method here
 * reads {@link WorldgenTuning#ACTIVE} fresh so a config change takes effect without needing a new
 * sampler instance.
 */
public final class ArchipelagoCellSampler {

    // Hard safety clamp on any island's final Y, regardless of what centerY/extent from config
    // would otherwise produce - keeps everything within the build height with some headroom. Not
    // exposed to config: unlike the other numbers, this one is a safety rail tied to the
    // dimension's actual build height, not a "feel" knob - letting it drift arbitrarily risks
    // silently broken generation rather than just a different-looking archipelago.
    public static final int CORE_Y_MIN = 24, CORE_Y_MAX = 240;

    // Salts the hash so the filler layer doesn't land on the exact same jitter/centerY/extent as
    // the main archipelago layer whenever their lattice indices happen to coincide - the two grids
    // use different cell sizes so they're not physically aligned anyway, but this keeps them from
    // being statistically correlated too.
    private static final long FILLER_SALT = 0x51DE0FF5E7L;

    private long seed;
    private final boolean filler;

    public ArchipelagoCellSampler(long seed) {
        this(seed, false);
    }

    /** @param filler true for the small stepping-stone islet layer, false for the real archipelagos. */
    public ArchipelagoCellSampler(long seed, boolean filler) {
        this.seed = seed;
        this.filler = filler;
    }

    private WorldgenTuning.IslandProfile profile() {
        WorldgenTuning.Snapshot tuning = WorldgenTuning.ACTIVE;
        return filler ? tuning.filler() : tuning.main();
    }

    /**
     * Reseeds this sampler in place. Used once, at server start, to swap the placeholder seed
     * baked into the datapack JSON for the real world seed - see {@link WorldSeedInjector}. Every
     * cell/core lookup after this call reflects the new seed; nothing else needs to change since
     * the sampler has no other state that depends on the seed.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    public CellParameters cellAt(int blockX, int blockZ) {
        int cellSize = profile().cellSize();
        int gx = Math.floorDiv(blockX, cellSize);
        int gz = Math.floorDiv(blockZ, cellSize);

        long bestDistSq = Long.MAX_VALUE;
        CellParameters best = null;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int lx = gx + dx, lz = gz + dz;
                CellParameters candidate = cellForLattice(lx, lz);
                long ddx = candidate.centerX() - blockX, ddz = candidate.centerZ() - blockZ;
                long distSq = ddx * ddx + ddz * ddz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = candidate;
                }
            }
        }

        return best;
    }

    /**
     * Returns the {@link CellParameters} for the site belonging to the exact lattice index
     * (lx,lz) — not "nearest site to a query point" (that's {@link #cellAt}). Used when a caller
     * already knows which lattice cell a value (e.g. an {@link IslandCore}) was generated from and
     * needs that cell's parameters directly, without risking a different cell winning the
     * nearest-site search at that point.
     */
    public CellParameters cellForLattice(int lx, int lz) {
        WorldgenTuning.IslandProfile profile = profile();
        int cellSize = profile.cellSize();
        long h = hash(lx, lz);
        // Jitter the site within its lattice cell using two bytes of the hash.
        int jitterX = (int) ((h & 0xFF) * cellSize / 256);
        int jitterZ = (int) (((h >>> 8) & 0xFF) * cellSize / 256);
        int siteX = lx * cellSize + jitterX;
        int siteZ = lz * cellSize + jitterZ;

        int centerY = profile.minCenterY() + (int) (unsignedFrac(h, 16) * (profile.maxCenterY() - profile.minCenterY()));
        int extent  = profile.minExtent()  + (int) (unsignedFrac(h, 24) * (profile.maxExtent() - profile.minExtent()));
        int tier    = (int) ((h >>> 32) % 3L);
        return new CellParameters(h, siteX, siteZ, centerY, extent, tier);
    }

    /** SplitMix64-style avalanche of the two lattice coords and the seed. */
    private long hash(int lx, int lz) {
        long saltedSeed = filler ? seed ^ FILLER_SALT : seed;
        long z = saltedSeed ^ (0x9E3779B97F4A7C15L * lx) ^ (0xC2B2AE3D27D4EB4FL * lz);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Returns a value in [0,1) from 8 bits of the hash starting at bit `shift`. */
    private double unsignedFrac(long h, int shift) {
        return ((h >>> shift) & 0xFF) / 256.0;
    }

    // --- Island sub-lattice (per archipelago cell) ---
    // How many islands, how far apart, and how big they are all come from WorldgenTuning now -
    // see Config.java's archipelagoIslands*/archipelagoCoreDist*/archipelagoCoreRadius*/
    // archipelagoCoreHeight*/archipelagoHillAmp* entries for what each one does.

    /** Deterministic island cores for a cell, derived purely from its cellId. */
    public IslandCore[] islandCores(CellParameters cell) {
        WorldgenTuning.Snapshot tuning = WorldgenTuning.ACTIVE;
        WorldgenTuning.IslandProfile profile = profile();
        long id = cell.cellId();

        // Havens only ever replace a main-layer cell's whole cluster with one big island - never
        // on the filler layer, which is meant to stay small stepping stones (see its own doc
        // comment on the filler config section). Centered rather than jittered like a normal core,
        // since it has the whole cell to itself and centering minimizes how far its oversized
        // radius can reach into a neighboring cell.
        if (!filler && Math.floorMod(id, tuning.havenChance()) == 0L) {
            return new IslandCore[] { havenCore(cell, tuning, profile) };
        }

        int span = profile.islandsMax() - profile.islandsMin() + 1;
        int count = profile.islandsMin() + (int) (frac(id, 40) * span);
        // Each core gets its own angular slice of the circle (2*PI/count wide) instead of a fully
        // free random angle, with jitter confined to 80% of that slice so it can't spill into a
        // neighboring core's slice. A free random angle let siblings land close together by pure
        // chance far too often (measured ~59% of cells had at least one overlapping pair before
        // this) - spacing them out by construction fixes the common case; the density function's
        // union-of-blobs blend (see ArchipelagoIslandsDensityFunction#density) smooths over
        // whatever overlap still slips through here or across a cell boundary.
        double sector = (Math.PI * 2.0) / count;
        IslandCore[] cores = new IslandCore[count];
        for (int i = 0; i < count; i++) {
            long ch = coreHash(id, i);
            double angle = i * sector + (frac(ch, 0) - 0.5) * sector * 0.8;
            double dist  = profile.coreDistMin() + frac(ch, 16) * (profile.coreDistMax() - profile.coreDistMin());
            int cx = cell.centerX() + (int) Math.round(Math.cos(angle) * dist);
            int cz = cell.centerZ() + (int) Math.round(Math.sin(angle) * dist);
            double rr = frac(ch, 24);
            // skew small: most islands land toward the smaller end of the radius range
            double radius = profile.coreRadiusMin() + rr * rr * (profile.coreRadiusMax() - profile.coreRadiusMin());
            double height = profile.coreHeightMin() + frac(ch, 32) * (profile.coreHeightMax() - profile.coreHeightMin());
            double yFrac = frac(ch, 40) * 2.0 - 1.0;            // -1..1 within the cell's vertical band
            int coreY = (int) Math.round(cell.centerY() + yFrac * cell.verticalExtent());
            coreY = Math.max(CORE_Y_MIN, Math.min(CORE_Y_MAX, coreY));
            boolean spiky = ((ch >>> 56) & 0x7L) == 0L;        // ~1/8 monolith-forming cores
            double hillAmp = spiky ? profile.hillAmpSpiky() : profile.hillAmpNormal();
            cores[i] = new IslandCore(cx, cz, coreY, radius, height, hillAmp, false, ch);
        }
        return cores;
    }

    /** The single large island that takes over a whole "haven" cell - see {@link #islandCores}. */
    private IslandCore havenCore(CellParameters cell, WorldgenTuning.Snapshot tuning, WorldgenTuning.IslandProfile profile) {
        long ch = coreHash(cell.cellId(), 0);
        double rr = frac(ch, 24); // no small-skew here, unlike normal cores - big is the whole point
        double radius = tuning.havenRadiusMin() + rr * (tuning.havenRadiusMax() - tuning.havenRadiusMin());
        double height = profile.coreHeightMin() + frac(ch, 32) * (profile.coreHeightMax() - profile.coreHeightMin());
        int coreY = Math.max(CORE_Y_MIN, Math.min(CORE_Y_MAX, cell.centerY()));
        return new IslandCore(cell.centerX(), cell.centerZ(), coreY, radius, height, tuning.havenHillAmp(), true, ch);
    }

    /** Nearest (horizontally) island core in the given cell. Never null (cores.length >= 1). */
    public IslandCore nearestCore(CellParameters cell, int blockX, int blockZ) {
        IslandCore[] cores = islandCores(cell);
        IslandCore best = cores[0];
        double bestSq = Double.MAX_VALUE;
        for (IslandCore c : cores) {
            double dx = blockX - c.x(), dz = blockZ - c.z();
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) { bestSq = sq; best = c; }
        }
        return best;
    }

    private long coreHash(long cellId, int index) {
        long z = cellId ^ (0x9E3779B97F4A7C15L * (index + 1L));
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 16-bit fraction in [0,1) from the hash starting at bit {@code shift}. */
    private double frac(long h, int shift) {
        return ((h >>> shift) & 0xFFFFL) / 65536.0;
    }
}
