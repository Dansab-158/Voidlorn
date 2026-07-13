package net.lobster.voidlorn.registry;

import net.lobster.voidlorn.Voidlorn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Voidlorn.MODID);
    public static void register(IEventBus bus) { BLOCKS.register(bus); }
}
