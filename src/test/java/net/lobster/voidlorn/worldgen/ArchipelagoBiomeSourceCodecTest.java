package net.lobster.voidlorn.worldgen;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchipelagoBiomeSourceCodecTest {

    // Merely loading BiomeSource triggers BuiltInRegistries.<clinit>, which asserts vanilla's
    // bootstrap has run (Bootstrap.checkBootstrapCalled). No MC runtime/server is started here —
    // this just performs the same registry bootstrap vanilla's own test suite performs before
    // touching any built-in registry, so the class can be loaded outside a full game instance.
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void codecFieldIsExposedAndNonNull() {
        assertNotNull(ArchipelagoBiomeSource.CODEC,
                "custom biome sources must expose a MapCodec for datapack use");
    }
}
