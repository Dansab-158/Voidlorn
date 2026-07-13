package net.lobster.voidlorn.worldgen;

import net.lobster.voidlorn.worldgen.cell.ArchipelagoCellSampler;
import net.lobster.voidlorn.worldgen.cell.CellParameters;
import net.lobster.voidlorn.worldgen.cell.IslandCore;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchipelagoIslandsDensityFunctionTest {

    private final long seed = 0L;
    private final ArchipelagoIslandsDensityFunction fn = new ArchipelagoIslandsDensityFunction(seed);
    private final ArchipelagoCellSampler sampler = new ArchipelagoCellSampler(seed);

    @Test
    void islandCoreSurfaceIsSolid() {
        CellParameters cell = sampler.cellAt(5000, -3000);
        IslandCore core = sampler.islandCores(cell)[0];
        int y = (int) Math.round(fn.surfaceYAt(core.x(), core.z()));
        // At the core center, at the (noise-warped) surface Y, density must be solid (> 0).
        assertTrue(fn.density(core.x(), y, core.z()) > 0.0,
                "expected solid ground at island core surface");
    }

    @Test
    void gapsAndSolidBothExistWithinOneCell() {
        // Scan a coarse grid over a single archipelago cell; require BOTH void and solid columns
        // (proves islands are discrete with void between them, not one giant landmass).
        CellParameters cell = sampler.cellAt(5000, -3000);
        int baseX = cell.centerX() - 256, baseZ = cell.centerZ() - 256;
        boolean sawSolid = false, sawVoid = false;
        for (int dx = 0; dx <= 512 && !(sawSolid && sawVoid); dx += 16) {
            for (int dz = 0; dz <= 512 && !(sawSolid && sawVoid); dz += 16) {
                int x = baseX + dx, z = baseZ + dz;
                boolean columnSolid = false;
                for (int y = 0; y <= 255 && !columnSolid; y += 2) {
                    if (fn.density(x, y, z) > 0.0) columnSolid = true;
                }
                if (columnSolid) sawSolid = true; else sawVoid = true;
            }
        }
        assertTrue(sawSolid, "no solid columns found in cell");
        assertTrue(sawVoid, "no void columns found in cell — islands merged into one blob");
    }

    @Test
    void terrainFollowsPerIslandCenterY() {
        // Two cores at different centerY produce solid ground at correspondingly different heights.
        CellParameters cell = sampler.cellAt(12000, 8000);
        IslandCore[] cores = sampler.islandCores(cell);
        IslandCore lo = cores[0], hi = cores[0];
        for (IslandCore c : cores) {
            if (c.centerY() < lo.centerY()) lo = c;
            if (c.centerY() > hi.centerY()) hi = c;
        }
        if (hi.centerY() - lo.centerY() >= 20) {
            int yLo = (int) Math.round(fn.surfaceYAt(lo.x(), lo.z()));
            int yHi = (int) Math.round(fn.surfaceYAt(hi.x(), hi.z()));
            assertTrue(yHi > yLo, "higher-centerY island did not sit higher (yLo=" + yLo + ", yHi=" + yHi + ")");
        }
    }

    @Test
    void surfaceHasRealHillsNotJustATilt() {
        // Walk a straight line across several different islands and check each surface has real
        // local peaks/dips along the way - not just one smooth ramp from edge to edge, which would
        // read as a flat, tilted mesa (like vanilla's small islands) rather than genuine hills.
        int islandsChecked = 0;
        for (int gx = 0; gx < 20 && islandsChecked < 5; gx++) {
            CellParameters cell = sampler.cellAt(gx * 400 + 137, -3000);
            for (IslandCore core : sampler.islandCores(cell)) {
                if (islandsChecked >= 5) break;
                islandsChecked++;
                int span = (int) core.radius() - 6;
                if (span < 10) continue;

                double[] heights = new double[2 * span];
                for (int i = -span; i < span; i++) {
                    heights[i + span] = fn.surfaceYAt(core.x() + i, core.z());
                }

                int direction = 0; // -1 falling, +1 rising, 0 unset
                int reversals = 0;
                for (int i = 1; i < heights.length; i++) {
                    double delta = heights[i] - heights[i - 1];
                    if (Math.abs(delta) < 1e-6) continue;
                    int newDirection = delta > 0 ? 1 : -1;
                    if (direction != 0 && newDirection != direction) reversals++;
                    direction = newDirection;
                }
                assertTrue(reversals >= 2,
                        "island at (" + core.x() + "," + core.z() + ") radius=" + core.radius()
                                + " only has " + reversals + " direction change(s) across its profile - "
                                + "reads as a flat, tilted mesa rather than real hills");
            }
        }
        assertTrue(islandsChecked >= 3, "didn't find enough islands to check, only " + islandsChecked);
    }

    @Test
    void densityStaysInDeclaredRange() {
        for (int i = 0; i < 5000; i++) {
            int x = (i * 37) - 90000, y = (i * 3) % 256, z = (i * 53) - 40000;
            double d = fn.density(x, y, z);
            assertTrue(d >= -1.0 && d <= 1.0, "density out of range: " + d);
        }
    }
}
