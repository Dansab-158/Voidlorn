package net.lobster.voidlorn.worldgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchipelagoBiomeSourcePoolTest {

    @Test
    void pickIndexIsInBoundsForVariousPoolSizes() {
        for (int poolSize : new int[]{1, 2, 3, 5, 8}) {
            for (long cellId : new long[]{0L, 1L, -1L, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE}) {
                int idx = ArchipelagoBiomeSource.pickIndex(cellId, poolSize);
                assertTrue(idx >= 0 && idx < poolSize,
                        "index " + idx + " out of bounds for poolSize " + poolSize + " (cellId=" + cellId + ")");
            }
        }
    }

    @Test
    void pickIndexIsDeterministic() {
        assertEquals(ArchipelagoBiomeSource.pickIndex(987654321L, 4),
                ArchipelagoBiomeSource.pickIndex(987654321L, 4));
    }

    @Test
    void pickIndexVariesAcrossDifferentCellIds() {
        var seen = new java.util.HashSet<Integer>();
        for (long i = 0; i < 200; i++) {
            long cellId = i * 0x9E3779B97F4A7C15L;
            seen.add(ArchipelagoBiomeSource.pickIndex(cellId, 5));
        }
        assertTrue(seen.size() > 1, "pickIndex returned the same index for every sampled cellId");
    }
}
