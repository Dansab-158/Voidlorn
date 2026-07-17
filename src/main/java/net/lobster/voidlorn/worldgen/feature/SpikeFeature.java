package net.lobster.voidlorn.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A tall, imposing rock spike meant to read as a broken claw or talon jutting out of the ground -
 * thick at the base, tapering (but never to a clean point) as it rises, leaning gently to one side
 * like it's curving, with a rough, pitted surface and a scatter of shattered fragments near the
 * top instead of a smooth tip. Grown as a stack of horizontal discs whose radius and horizontal
 * offset both change per layer, reading its blocks from a configurable
 * {@link BlockStateProvider} - vanilla's basalt pillar does something similar but hardcodes
 * {@code Blocks.BASALT} directly in Java with no config hook (confirmed against decompiled
 * source), so it can't be reused for a different material.
 *
 * <p>{@link SpikeConfiguration#sizeScale()} scales the base 14-28 block/2.0-4.5 radius range down
 * (or up), which is how the same class produces both the tall obsidian claws and the smaller tuff
 * spike variant.
 */
public class SpikeFeature extends Feature<SpikeConfiguration> {

    public SpikeFeature(Codec<SpikeConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SpikeConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        if (!level.isEmptyBlock(origin) || level.isEmptyBlock(origin.below())) {
            return false; // needs solid ground to stand on and clear air above it
        }

        RandomSource random = context.random();
        BlockStateProvider palette = context.config().toPlace();
        double sizeScale = context.config().sizeScale();

        // 14..28 and 2.0..4.5 at sizeScale 1.0 (tall/imposing); clamped so a small spike variant
        // still reads as an actual spike rather than shrinking away to nothing.
        int height = Math.max(3, (int) Math.round((14 + random.nextInt(15)) * sizeScale));
        double baseRadius = Math.max(0.6, (2.0 + random.nextDouble() * 2.5) * sizeScale);

        // A fixed lean direction, drifted into over the whole height, is what gives the claw its
        // curve - a straight-up column with a tapering radius alone still reads as a plain pillar.
        double curveYaw = random.nextDouble() * Math.PI * 2.0;
        double curveStrength = 0.1 + random.nextDouble() * 0.25;
        double curveDx = Math.cos(curveYaw) * curveStrength;
        double curveDz = Math.sin(curveYaw) * curveStrength;

        double offsetX = 0.0;
        double offsetZ = 0.0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < height; i++) {
            int y = origin.getY() + i;
            if (y > level.getMaxBuildHeight()) {
                break;
            }
            double frac = (double) i / height;
            // Tapers toward the top but never past ~15% of the base radius - a claw's tip is
            // broken and blunt, not a needle point.
            double radius = Math.max(baseRadius * (1.0 - frac * 0.85), 0.6);
            int r = (int) Math.round(radius);
            int centerX = origin.getX() + (int) Math.round(offsetX);
            int centerZ = origin.getZ() + (int) Math.round(offsetZ);

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) {
                        continue;
                    }
                    // A pitted, uneven silhouette instead of a smooth, machine-turned cylinder.
                    if (random.nextFloat() < 0.08F) {
                        continue;
                    }
                    pos.set(centerX + dx, y, centerZ + dz);
                    level.setBlock(pos, palette.getState(random, pos), 2);
                }
            }

            offsetX += curveDx;
            offsetZ += curveDz;
        }

        // A handful of loose fragments scattered around the break point, like the claw sheared
        // off mid-shape rather than tapering away cleanly.
        int shardCount = 2 + random.nextInt(4);
        int tipX = origin.getX() + (int) Math.round(offsetX);
        int tipZ = origin.getZ() + (int) Math.round(offsetZ);
        int tipY = origin.getY() + height;
        for (int i = 0; i < shardCount; i++) {
            int sx = tipX + random.nextInt(5) - 2;
            int sy = tipY - random.nextInt(4);
            int sz = tipZ + random.nextInt(5) - 2;
            pos.set(sx, sy, sz);
            if (level.isEmptyBlock(pos)) {
                level.setBlock(pos, palette.getState(random, pos), 2);
            }
        }

        return true;
    }
}
