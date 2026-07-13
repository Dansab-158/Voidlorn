package net.lobster.voidlorn.registry;

import net.lobster.voidlorn.Voidlorn;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    private ModEffects() {}
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Voidlorn.MODID);
    public static void register(IEventBus bus) { EFFECTS.register(bus); }
}
