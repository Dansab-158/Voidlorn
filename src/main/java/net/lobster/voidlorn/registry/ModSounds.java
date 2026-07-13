package net.lobster.voidlorn.registry;

import net.lobster.voidlorn.Voidlorn;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModSounds {
    private ModSounds() {}
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Voidlorn.MODID);

    public static final Supplier<SoundEvent> VOID_MOTE_PURSUIT =
            SOUNDS.register("void_mote_pursuit",
                () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(Voidlorn.MODID, "void_mote_pursuit")));

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
