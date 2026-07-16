package net.lobster.voidlorn.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * What a floating monolith is made of - usually a {@code minecraft:weighted_state_provider} so it
 * can mix obsidian and crying obsidian in the same shape rather than one solid material. Size and
 * spawn rate aren't part of this configuration - they're read live from {@link net.lobster.voidlorn.Config}
 * instead (see {@link FloatingMonolithFeature}), so players can retune them without a resource pack.
 */
public record FloatingMonolithConfiguration(BlockStateProvider stateProvider) implements FeatureConfiguration {

    public static final Codec<FloatingMonolithConfiguration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BlockStateProvider.CODEC.fieldOf("state_provider").forGetter(FloatingMonolithConfiguration::stateProvider)
    ).apply(inst, FloatingMonolithConfiguration::new));
}
