package net.lobster.voidlorn.registry;

import net.lobster.voidlorn.Voidlorn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Voidlorn.MODID);
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
