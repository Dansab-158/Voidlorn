package net.lobster.voidlorn.registry;

import net.lobster.voidlorn.Voidlorn;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistryNamespaceTest {
    @Test
    void registersUseModNamespace() {
        assertEquals(Voidlorn.MODID, ModEntities.ENTITIES.getNamespace());
        assertEquals(Voidlorn.MODID, ModEffects.EFFECTS.getNamespace());
        assertEquals(Voidlorn.MODID, ModItems.ITEMS.getNamespace());
        assertEquals(Voidlorn.MODID, ModBlocks.BLOCKS.getNamespace());
        assertEquals(Voidlorn.MODID, ModSounds.SOUNDS.getNamespace());
    }
}
