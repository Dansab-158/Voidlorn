package net.lobster.voidlorn.worldgen.cell;

import net.lobster.voidlorn.worldgen.WorldgenTuning;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IslandCoreSamplerTest {

    private final ArchipelagoCellSampler sampler = new ArchipelagoCellSampler(0L);

    @Test
    void atLeastThreeCoresPerCell() {
        for (int[] xz : new int[][]{{200, 200}, {5000, -3000}, {-40000, 12000}}) {
            CellParameters cell = sampler.cellAt(xz[0], xz[1]);
            IslandCore[] cores = sampler.islandCores(cell);
            assertTrue(cores.length >= WorldgenTuning.ACTIVE.main().islandsMin(),
                    "cell " + cell.cellId() + " had only " + cores.length + " cores");
            assertTrue(cores.length <= WorldgenTuning.ACTIVE.main().islandsMax());
        }
    }

    @Test
    void coresAreDeterministicFromCellId() {
        CellParameters cell = sampler.cellAt(5000, -3000);
        IslandCore[] a = sampler.islandCores(cell);
        IslandCore[] b = sampler.islandCores(cell);
        assertArrayEquals(a, b);
    }

    @Test
    void coreParametersAreDistanceIndependent() {
        // Radii/heights must fall in the SAME fixed range near and far from origin (no gradient).
        for (int[] xz : new int[][]{{300, 300}, {60000, -60000}}) {
            CellParameters cell = sampler.cellAt(xz[0], xz[1]);
            for (IslandCore c : sampler.islandCores(cell)) {
                assertTrue(c.radius() >= WorldgenTuning.ACTIVE.main().coreRadiusMin()
                        && c.radius() <= WorldgenTuning.ACTIVE.main().coreRadiusMax());
                assertTrue(c.centerY() >= ArchipelagoCellSampler.CORE_Y_MIN
                        && c.centerY() <= ArchipelagoCellSampler.CORE_Y_MAX);
            }
        }
    }

    @Test
    void islandsAreVerticallyDistributedWithinArchipelago() {
        // Find a cell whose cores are not all at one Y (requirement: vertical spread within a cell).
        boolean foundSpread = false;
        for (int gx = 0; gx < 40 && !foundSpread; gx++) {
            CellParameters cell = sampler.cellAt(gx * 512 + 100, 100);
            IslandCore[] cores = sampler.islandCores(cell);
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (IslandCore c : cores) { min = Math.min(min, c.centerY()); max = Math.max(max, c.centerY()); }
            if (max - min >= 20) foundSpread = true;
        }
        assertTrue(foundSpread, "no archipelago cell showed >=20 blocks of per-island Y spread");
    }

    @Test
    void nearestCoreReturnsTheClosest() {
        CellParameters cell = sampler.cellAt(5000, -3000);
        IslandCore[] cores = sampler.islandCores(cell);
        IslandCore target = cores[cores.length - 1];
        IslandCore nearest = sampler.nearestCore(cell, target.x(), target.z());
        assertEquals(target, nearest);
    }
}
