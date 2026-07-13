package net.lobster.voidlorn.registry;

import net.lobster.voidlorn.Voidlorn;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModWorldgenLoadTest {
    @Test
    void densityFunctionRegisterIsCreatedForModNamespace() {
        assertNotNull(ModWorldgen.DENSITY_FUNCTION_TYPES);
        assertEquals(Voidlorn.MODID, ModWorldgen.DENSITY_FUNCTION_TYPES.getNamespace());
    }
}
