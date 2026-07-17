package net.lobster.voidlorn.worldgen.cell;

/**
 * One island core within an archipelago cell. Placed on a finer jittered sub-lattice than the
 * 512-block Voronoi cell, so a single cell hosts several bounded islands with void between them.
 * All fields are a pure hash of the parent {@code cellId} + core index (no origin dependence).
 *
 * @param x,z     block coordinates of the island's horizontal center
 * @param centerY block Y around which the island's mass concentrates (spread within the cell band)
 * @param radius  horizontal radius in blocks beyond which this core contributes only void
 * @param height  vertical half-extent in blocks of the island's solid band
 * @param hillAmp amplitude (blocks) by which surface noise warps the island top (hills/monoliths)
 * @param haven   true for a "haven" - one large island (full hills/valleys/canyons, same as any
 *                other core - size is what sets it apart) replacing its whole cell's normal
 *                cluster, instead of one of several jittered cores (see
 *                {@link net.lobster.voidlorn.worldgen.cell.ArchipelagoCellSampler#islandCores})
 * @param coreId  the per-core hash (also seeds nothing directly; useful for debugging)
 */
public record IslandCore(
        int x,
        int z,
        int centerY,
        double radius,
        double height,
        double hillAmp,
        boolean haven,
        long coreId
) {}
