package net.lobster.voidlorn.worldgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OriginProximityMaskTest {

    private final OriginProximityMask mask = new OriginProximityMask(1024.0, 1200.0);

    @Test
    void fullVanillaInsideInnerRadius() {
        assertEquals(1.0, mask.profile(0, 0), 1e-9);
        assertEquals(1.0, mask.profile(700, 700), 1e-9); // dist ~989 < 1024
    }

    @Test
    void fullArchipelagoOutsideOuterRadius() {
        assertEquals(0.0, mask.profile(2000, 0), 1e-9);
        assertEquals(0.0, mask.profile(-5000, 4000), 1e-9);
    }

    @Test
    void smoothCrossfadeInBand() {
        double m = mask.profile(1112, 0); // midpoint of [1024,1200]
        assertTrue(m > 0.2 && m < 0.8, "expected mid crossfade, got " + m);
        // monotonic non-increasing with distance
        assertTrue(mask.profile(1030, 0) >= mask.profile(1150, 0));
    }
}
