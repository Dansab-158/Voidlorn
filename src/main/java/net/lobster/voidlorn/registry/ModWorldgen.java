package net.lobster.voidlorn.registry;

import com.mojang.serialization.MapCodec;
import net.lobster.voidlorn.Voidlorn;
import net.lobster.voidlorn.worldgen.ArchipelagoBiomeSource;
import net.lobster.voidlorn.worldgen.ArchipelagoIslandsDensityFunction;
import net.lobster.voidlorn.worldgen.OriginProximityMask;
import net.lobster.voidlorn.worldgen.feature.FloatingMonolithConfiguration;
import net.lobster.voidlorn.worldgen.feature.FloatingMonolithFeature;
import net.lobster.voidlorn.worldgen.feature.SpikeConfiguration;
import net.lobster.voidlorn.worldgen.feature.SpikeFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
    private ModWorldgen() {}

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Voidlorn.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Voidlorn.MODID);

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Voidlorn.MODID);

    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<ArchipelagoBiomeSource>> ARCHIPELAGO =
            BIOME_SOURCES.register("archipelago", () -> ArchipelagoBiomeSource.CODEC);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<OriginProximityMask>>
            ORIGIN_PROXIMITY_MASK = DENSITY_FUNCTION_TYPES.register("origin_proximity_mask",
                    () -> OriginProximityMask.CODEC);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<ArchipelagoIslandsDensityFunction>>
            ARCHIPELAGO_ISLANDS = DENSITY_FUNCTION_TYPES.register("archipelago_islands",
                    () -> ArchipelagoIslandsDensityFunction.CODEC);

    public static final DeferredHolder<Feature<?>, FloatingMonolithFeature> FLOATING_MONOLITH =
            FEATURES.register("floating_monolith", () -> new FloatingMonolithFeature(FloatingMonolithConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, SpikeFeature> SPIKE =
            FEATURES.register("spike", () -> new SpikeFeature(SpikeConfiguration.CODEC));

    public static void register(IEventBus modEventBus) {
        DENSITY_FUNCTION_TYPES.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
        FEATURES.register(modEventBus);
    }
}
