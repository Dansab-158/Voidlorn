package net.lobster.voidlorn.worldgen.cell;

import net.lobster.voidlorn.worldgen.WorldgenTuning;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchipelagoCellSamplerTest {

    @Test
    void isDeterministicForSameSeedAndPosition() {
        var a = new ArchipelagoCellSampler(1234L);
        var b = new ArchipelagoCellSampler(1234L);
        assertEquals(a.cellAt(700, -300), b.cellAt(700, -300));
    }

    @Test
    void centerYAndExtentStayWithinConfiguredRange() {
        var s = new ArchipelagoCellSampler(42L);
        for (int x = -5000; x <= 5000; x += 137) {
            for (int z = -5000; z <= 5000; z += 149) {
                CellParameters p = s.cellAt(x, z);
                assertTrue(p.centerY() >= WorldgenTuning.ACTIVE.main().minCenterY()
                        && p.centerY() <= WorldgenTuning.ACTIVE.main().maxCenterY(),
                        "centerY out of range at " + x + "," + z + " = " + p.centerY());
                assertTrue(p.verticalExtent() >= WorldgenTuning.ACTIVE.main().minExtent()
                        && p.verticalExtent() <= WorldgenTuning.ACTIVE.main().maxExtent());
                assertTrue(p.ruggednessTier() >= 0 && p.ruggednessTier() <= 2);
            }
        }
    }

    @Test
    void neighbouringColumnsInSameCellShareParameters() {
        var s = new ArchipelagoCellSampler(9L);
        int sameCellPairs = 0;
        for (int x = -3000; x <= 3000; x += 11) {
            CellParameters a = s.cellAt(x, 0);
            CellParameters b = s.cellAt(x + 1, 0);
            if (a.cellId() == b.cellId()) {
                assertEquals(a, b, "same cellId must imply identical parameters at x=" + x);
                sameCellPairs++;
            }
        }
        assertTrue(sameCellPairs > 0,
                "no adjacent-column pair landed in the same cell across the sampled range; "
                        + "the invariant this test exists to check was never exercised");
    }

    @Test
    void parameterDistributionDoesNotCorrelateWithDistanceFromOrigin() {
        // Anti "distance gradient" guard: mean centerY of near cells ~= mean centerY of far cells.
        var s = new ArchipelagoCellSampler(7L);
        double near = meanCenterY(s, 0, 2000);
        double far = meanCenterY(s, 40000, 42000);
        assertTrue(Math.abs(near - far) < 20.0,
                "centerY correlates with distance: near=" + near + " far=" + far);
    }

    private double meanCenterY(ArchipelagoCellSampler s, int lo, int hi) {
        long sum = 0; int n = 0;
        for (int x = lo; x < hi; x += 97) { sum += s.cellAt(x, x).centerY(); n++; }
        return (double) sum / n;
    }
}
