package net.lobster.voidlorn.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Fades from 1.0 near world origin (keeping the vanilla dragon island untouched) down to 0.0
 * past {@code outerRadius} (full archipelago generation), with a smoothstep in between. This is
 * the one place in the generator that's allowed to care about distance from origin - everywhere
 * else, island placement has to stay independent of it.
 */
public final class OriginProximityMask implements DensityFunction.SimpleFunction {

    // The 1024/1200 defaults here rarely matter in practice - the actual values in play come from
    // src/main/resources/data/voidlorn/worldgen/density_function/origin_mask.json, which sets both
    // explicitly. Change the radii there, not here, if you want the vanilla-preserved zone around
    // spawn to be bigger or smaller (or the fade band between the two to be wider/narrower).
    public static final MapCodec<OriginProximityMask> CODEC =
            RecordCodecBuilder.mapCodec(inst -> inst.group(
                    com.mojang.serialization.Codec.DOUBLE.optionalFieldOf("inner_radius", 1024.0)
                            .forGetter(f -> f.innerRadius),
                    com.mojang.serialization.Codec.DOUBLE.optionalFieldOf("outer_radius", 1200.0)
                            .forGetter(f -> f.outerRadius)
            ).apply(inst, OriginProximityMask::new));

    private static final KeyDispatchDataCodec<OriginProximityMask> KEY_CODEC =
            KeyDispatchDataCodec.of(CODEC);

    private final double innerRadius;
    private final double outerRadius;

    public OriginProximityMask(double innerRadius, double outerRadius) {
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
    }

    /** 1.0 inside innerRadius, 0.0 outside outerRadius, smoothstep between. */
    public double profile(int blockX, int blockZ) {
        double dist = Math.sqrt((double) blockX * blockX + (double) blockZ * blockZ);
        if (dist <= innerRadius) return 1.0;
        if (dist >= outerRadius) return 0.0;
        double t = (dist - innerRadius) / (outerRadius - innerRadius); // 0..1
        double s = t * t * (3.0 - 2.0 * t); // smoothstep
        return 1.0 - s;
    }

    @Override
    public double compute(FunctionContext ctx) {
        return profile(ctx.blockX(), ctx.blockZ());
    }

    @Override
    public double minValue() { return 0.0; }

    @Override
    public double maxValue() { return 1.0; }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
