package net.lobster.voidlorn.registry;

import net.lobster.voidlorn.Voidlorn;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Voidlorn.MODID);
    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}
