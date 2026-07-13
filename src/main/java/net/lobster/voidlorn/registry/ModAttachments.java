package net.lobster.voidlorn.registry;

import com.mojang.serialization.Codec;
import net.lobster.voidlorn.Voidlorn;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class ModAttachments {
    private ModAttachments() {}
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Voidlorn.MODID);

    // Set of UUIDs of motes currently attached to the player.
    public static final Supplier<AttachmentType<Set<UUID>>> ATTACHED_MOTES =
            ATTACHMENT_TYPES.register("attached_motes", () ->
                AttachmentType.<Set<UUID>>builder(() -> new HashSet<>())
                        .serialize(UUIDUtil.CODEC.listOf()
                                .xmap(list -> (Set<UUID>) new HashSet<>(list), s -> s.stream().toList()))
                        .copyOnDeath()
                        .build());

    public static void register(IEventBus bus) { ATTACHMENT_TYPES.register(bus); }
}
