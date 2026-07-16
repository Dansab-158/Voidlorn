package net.lobster.voidlorn.worldgen.cell;

import net.lobster.voidlorn.worldgen.WorldgenTuning;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IslandSizeFloorTest {

    private final ArchipelagoCellSampler sampler = new ArchipelagoCellSampler(0L);
    // These tests check the shipped DEFAULTS, not whatever a player's config happens to say - a
    // player can retune these in config/voidlorn-common.toml, that's the point of exposing them.

    @Test
    void islandsNeverGetSoSmallTheyReadAsBrokenDebris() {
        // Even at the low end of the size range, an island should have a real footprint and
        // enough vertical solidity to not read as a fragile, paper-thin "broken" scrap.
        assertTrue(WorldgenTuning.DEFAULTS.main().coreRadiusMin() >= 20.0,
                "smallest island radius is too small: " + WorldgenTuning.DEFAULTS.main().coreRadiusMin());
        assertTrue(WorldgenTuning.DEFAULTS.main().coreHeightMin() >= 8.0,
                "smallest island height is too small: " + WorldgenTuning.DEFAULTS.main().coreHeightMin());
        assertTrue(WorldgenTuning.DEFAULTS.main().coreHeightMax() <= 16.0,
                "largest island height is thicker than intended: " + WorldgenTuning.DEFAULTS.main().coreHeightMax());
    }

    @Test
    void sampledCoresRespectTheSizeFloor() {
        // Compared against ACTIVE, not DEFAULTS: this checks that sampled cores respect whatever
        // profile is actually driving generation right now (which reads ACTIVE internally) - not
        // the compiled-in defaults, which could drift from a config file already on disk.
        for (int gx = 0; gx < 60; gx++) {
            CellParameters cell = sampler.cellAt(gx * 512 + 137, 9000);
            for (IslandCore c : sampler.islandCores(cell)) {
                assertTrue(c.radius() >= WorldgenTuning.ACTIVE.main().coreRadiusMin(),
                        "core radius " + c.radius() + " below the floor");
                assertTrue(c.height() >= WorldgenTuning.ACTIVE.main().coreHeightMin(),
                        "core height " + c.height() + " below the floor");
            }
        }
    }
}
