package net.lobster.voidlorn.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * What a spike is made of and how big it grows relative to {@link SpikeFeature}'s base
 * 14-28 block/2.0-4.5 radius range. {@code sizeScale} lets the same shape/curve/pitting algorithm
 * produce both the tall, imposing obsidian claws and a much smaller tuff variant without a second
 * Java {@code Feature} class.
 */
public record SpikeConfiguration(BlockStateProvider toPlace, float sizeScale) implements FeatureConfiguration {

    public static final Codec<SpikeConfiguration> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BlockStateProvider.CODEC.fieldOf("to_place").forGetter(SpikeConfiguration::toPlace),
            Codec.FLOAT.optionalFieldOf("size_scale", 1.0F).forGetter(SpikeConfiguration::sizeScale)
    ).apply(inst, SpikeConfiguration::new));
}
