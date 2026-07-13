package net.lobster.voidlorn.worldgen.cell;

/**
 * Splits the world into a jittered grid of {@code CELL_SIZE} cells, each with one randomly
 * offset "site". {@link #cellAt} finds whichever site is nearest to a given point and returns
 * its parameters. Everything here is a hash of the site's lattice coordinates plus the seed, so
 * results are deterministic and never depend on distance from the world origin.
 */
public final class ArchipelagoCellSampler {

    // How far apart archipelagos are, on average, in blocks. Smaller = archipelagos closer
    // together and more frequent while flying around; larger = more empty void between them.
    public static final int CELL_SIZE = 512;

    // The vertical "home band" an archipelago's islands are centered around. Each cell picks one
    // centerY in this range, then its islands scatter around that Y (see MIN_EXTENT/MAX_EXTENT
    // below). Widen this range to spread archipelagos across more of the world's height overall.
    public static final int MIN_CENTER_Y = 40, MAX_CENTER_Y = 160;

    // How far, in blocks, an individual island's Y can drift from its archipelago's centerY.
    // Bigger extent = more dramatic height differences between islands in the same archipelago.
    public static final int MIN_EXTENT = 24, MAX_EXTENT = 72;

    private final long seed;

    public ArchipelagoCellSampler(long seed) {
        this.seed = seed;
    }

    public CellParameters cellAt(int blockX, int blockZ) {
        int gx = Math.floorDiv(blockX, CELL_SIZE);
        int gz = Math.floorDiv(blockZ, CELL_SIZE);

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
        long h = hash(lx, lz);
        // Jitter the site within its lattice cell using two bytes of the hash.
        int jitterX = (int) ((h & 0xFF) * CELL_SIZE / 256);
        int jitterZ = (int) (((h >>> 8) & 0xFF) * CELL_SIZE / 256);
        int siteX = lx * CELL_SIZE + jitterX;
        int siteZ = lz * CELL_SIZE + jitterZ;

        int centerY = MIN_CENTER_Y + (int) (unsignedFrac(h, 16) * (MAX_CENTER_Y - MIN_CENTER_Y));
        int extent  = MIN_EXTENT   + (int) (unsignedFrac(h, 24) * (MAX_EXTENT - MIN_EXTENT));
        int tier    = (int) ((h >>> 32) % 3L);
        return new CellParameters(h, siteX, siteZ, centerY, extent, tier);
    }

    /** SplitMix64-style avalanche of the two lattice coords and the seed. */
    private long hash(int lx, int lz) {
        long z = seed ^ (0x9E3779B97F4A7C15L * lx) ^ (0xC2B2AE3D27D4EB4FL * lz);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Returns a value in [0,1) from 8 bits of the hash starting at bit `shift`. */
    private double unsignedFrac(long h, int shift) {
        return ((h >>> shift) & 0xFF) / 256.0;
    }

    // --- Island sub-lattice (per archipelago cell) ---

    // How many separate islands each archipelago gets. Raise ISLANDS_MAX for busier archipelagos,
    // lower ISLANDS_MIN if you want some archipelagos to feel sparse.
    public static final int ISLANDS_MIN = 3;
    public static final int ISLANDS_MAX = 6; // inclusive

    // How far an island can be placed from its archipelago's own center point. This is what
    // creates the void gaps between islands - push CORE_DIST_MIN up and islands never crowd
    // together; push CORE_DIST_MAX down and the whole archipelago feels tighter/smaller overall.
    public static final double CORE_DIST_MIN = 48.0, CORE_DIST_MAX = 168.0;

    // Island footprint size (radius) and vertical thickness (height), in blocks. This is the main
    // "how big/chunky do islands look" knob. The floor got raised here after playtesting: the old
    // minimum (radius 15, height 6) produced islands only ~30 blocks wide and ~4 blocks thick,
    // which read as broken debris rather than real islands. The height ceiling was trimmed too, so
    // the biggest islands don't feel like solid bricks of endstone.
    public static final double CORE_RADIUS_MIN = 24.0, CORE_RADIUS_MAX = 46.0;
    public static final double CORE_HEIGHT_MIN = 9.0, CORE_HEIGHT_MAX = 15.0;

    // How tall the hills/monoliths on top of an island can get (added on top of the base height
    // above). Most islands use the NORMAL amplitude; a rare ~1/8 of islands ("spiky", see
    // islandCores below) use the taller SPIKY one for the occasional dramatic monolith.
    public static final double HILL_AMP_NORMAL = 10.0, HILL_AMP_SPIKY = 17.0;

    // Hard safety clamp on any island's final Y, regardless of what centerY/extent above would
    // otherwise produce - keeps everything within the build height with some headroom.
    public static final int CORE_Y_MIN = 24, CORE_Y_MAX = 232;

    /** Deterministic 3..6 island cores for a cell, derived purely from its cellId. */
    public IslandCore[] islandCores(CellParameters cell) {
        long id = cell.cellId();
        int span = ISLANDS_MAX - ISLANDS_MIN + 1;               // 4
        int count = ISLANDS_MIN + (int) (frac(id, 40) * span);  // 3..6
        IslandCore[] cores = new IslandCore[count];
        for (int i = 0; i < count; i++) {
            long ch = coreHash(id, i);
            double angle = frac(ch, 0) * (Math.PI * 2.0);
            double dist  = CORE_DIST_MIN + frac(ch, 16) * (CORE_DIST_MAX - CORE_DIST_MIN);
            int cx = cell.centerX() + (int) Math.round(Math.cos(angle) * dist);
            int cz = cell.centerZ() + (int) Math.round(Math.sin(angle) * dist);
            double rr = frac(ch, 24);
            double radius = CORE_RADIUS_MIN + rr * rr * (CORE_RADIUS_MAX - CORE_RADIUS_MIN); // skew small
            double height = CORE_HEIGHT_MIN + frac(ch, 32) * (CORE_HEIGHT_MAX - CORE_HEIGHT_MIN);
            double yFrac = frac(ch, 40) * 2.0 - 1.0;            // -1..1 within the cell's vertical band
            int coreY = (int) Math.round(cell.centerY() + yFrac * cell.verticalExtent());
            coreY = Math.max(CORE_Y_MIN, Math.min(CORE_Y_MAX, coreY));
            boolean spiky = ((ch >>> 56) & 0x7L) == 0L;        // ~1/8 monolith-forming cores
            double hillAmp = spiky ? HILL_AMP_SPIKY : HILL_AMP_NORMAL;
            cores[i] = new IslandCore(cx, cz, coreY, radius, height, hillAmp, ch);
        }
        return cores;
    }

    /** Nearest (horizontally) island core in the given cell. Never null (cores.length >= 3). */
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
