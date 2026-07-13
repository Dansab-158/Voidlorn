package net.lobster.voidlorn.worldgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchipelagoBiomeSourceCenterTest {

    @Test
    void columnsNearOriginAreCentral() {
        assertTrue(ArchipelagoBiomeSource.isCentralColumn(0, 0));
        assertTrue(ArchipelagoBiomeSource.isCentralColumn(700, 700));   // dist ~989 < 1200
        assertTrue(ArchipelagoBiomeSource.isCentralColumn(-1000, 0));
    }

    @Test
    void columnsFarFromOriginAreNotCentral() {
        assertFalse(ArchipelagoBiomeSource.isCentralColumn(2000, 0));
        assertFalse(ArchipelagoBiomeSource.isCentralColumn(-5000, 4000));
        assertFalse(ArchipelagoBiomeSource.isCentralColumn(900, 900));  // dist ~1273 > 1200
    }
}
