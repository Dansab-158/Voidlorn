package net.lobster.voidlorn.worldgen.cell;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IslandSizeFloorTest {

    private final ArchipelagoCellSampler sampler = new ArchipelagoCellSampler(0L);

    @Test
    void islandsNeverGetSoSmallTheyReadAsBrokenDebris() {
        // Even at the low end of the size range, an island should have a real footprint and
        // enough vertical solidity to not read as a fragile, paper-thin "broken" scrap.
        assertTrue(ArchipelagoCellSampler.CORE_RADIUS_MIN >= 20.0,
                "smallest island radius is too small: " + ArchipelagoCellSampler.CORE_RADIUS_MIN);
        assertTrue(ArchipelagoCellSampler.CORE_HEIGHT_MIN >= 8.0,
                "smallest island height is too small: " + ArchipelagoCellSampler.CORE_HEIGHT_MIN);
        assertTrue(ArchipelagoCellSampler.CORE_HEIGHT_MAX <= 16.0,
                "largest island height is thicker than intended: " + ArchipelagoCellSampler.CORE_HEIGHT_MAX);
    }

    @Test
    void sampledCoresRespectTheSizeFloor() {
        for (int gx = 0; gx < 60; gx++) {
            CellParameters cell = sampler.cellAt(gx * 512 + 137, 9000);
            for (IslandCore c : sampler.islandCores(cell)) {
                assertTrue(c.radius() >= ArchipelagoCellSampler.CORE_RADIUS_MIN,
                        "core radius " + c.radius() + " below the floor");
                assertTrue(c.height() >= ArchipelagoCellSampler.CORE_HEIGHT_MIN,
                        "core height " + c.height() + " below the floor");
            }
        }
    }
}
