package net.lobster.voidlorn.worldgen.feature;

import net.lobster.voidlorn.Voidlorn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpikeFeatureRegistrationTest {

    @Test
    void spikeFeatureIsRegistered() {
        var id = ResourceLocation.fromNamespaceAndPath(Voidlorn.MODID, "spike");
        assertTrue(BuiltInRegistries.FEATURE.containsKey(id),
                "voidlorn:spike feature is not registered");
    }
}
