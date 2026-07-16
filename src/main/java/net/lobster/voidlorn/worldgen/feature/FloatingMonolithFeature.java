package net.lobster.voidlorn.worldgen.feature;

import com.mojang.serialization.Codec;
import net.lobster.voidlorn.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * A rare, tapering shard of rock floating in open air - wide in the middle, coming to a point at
 * both ends, like a splinter of the islands torn loose. Grown along a randomly-oriented axis (not
 * always straight up-and-down) so different shards lean and tumble at different angles instead of
 * all standing the same way. The radius along that axis follows a sine curve (0 at one tip, widest
 * at the middle, 0 again at the other tip), which is what gives it pointed ends rather than flat
 * ones. A little per-layer jitter keeps it from looking like a perfect, machine-turned solid of
 * revolution.
 *
 * <p>Meant to be placed very rarely - see {@code Config.ARCHIPELAGO_MONOLITH_CHANCE}, which this
 * checks itself rather than relying on a fixed rarity baked into the datapack JSON, so a player
 * can turn the frequency up or down from the config file without needing a resource pack. These
 * are occasional landmarks breaking up the empty space between archipelagos, not a texture that
 * covers the void.
 */
public class FloatingMonolithFeature extends Feature<FloatingMonolithConfiguration> {

    public FloatingMonolithFeature(Codec<FloatingMonolithConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<FloatingMonolithConfiguration> context) {
        if (!Config.OVERRIDE_VANILLA_END.get()) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        FloatingMonolithConfiguration config = context.config();

        if (random.nextDouble() >= Config.ARCHIPELAGO_MONOLITH_CHANCE.get()) {
            return false;
        }

        // origin sits at the monolith's visual center (its widest point), so this check actually
        // means something - a check at one of the pointed tips would almost always pass trivially.
        if (!level.isEmptyBlock(origin)) {
            return false;
        }

        // One shared, skewed roll for both height and radius so the shape scales consistently -
        // most monoliths land small, with an occasional bigger one, never a mismatched combination.
        double t = random.nextDouble();
        t *= t;
        int configMinHeight = Config.ARCHIPELAGO_MONOLITH_MIN_HEIGHT.get();
        int configMaxHeight = Config.ARCHIPELAGO_MONOLITH_MAX_HEIGHT.get();
        double configMinRadius = Config.ARCHIPELAGO_MONOLITH_MIN_RADIUS.get();
        double configMaxRadius = Config.ARCHIPELAGO_MONOLITH_MAX_RADIUS.get();
        int height = configMinHeight + (int) Math.round(t * (configMaxHeight - configMinHeight));
        double maxRadius = configMinRadius + t * (configMaxRadius - configMinRadius);
        if (height < 2) {
            return false;
        }

        // A uniformly random point on the unit sphere - the long axis the shard grows along.
        // Sampling yaw/pitch independently like this would bunch samples near the poles, but for a
        // handful of blocks that's not something anyone would notice, and it keeps the math simple.
        double yaw = random.nextDouble() * Math.PI * 2.0;
        double pitch = (random.nextDouble() - 0.5) * Math.PI;
        double cosPitch = Math.cos(pitch);
        double axisX = cosPitch * Math.cos(yaw);
        double axisY = Math.sin(pitch);
        double axisZ = cosPitch * Math.sin(yaw);

        int halfHeight = height / 2;
        // Per-layer jitter, keyed by position along the axis rather than a plain Y level, so the
        // rough silhouette rotates along with the shard instead of always rippling on the world's
        // vertical axis regardless of how the shard is oriented.
        double[] layerJitter = new double[height];
        for (int i = 0; i < height; i++) {
            layerJitter[i] = 0.8 + random.nextDouble() * 0.3;
        }

        int bound = (int) Math.ceil(Math.max(maxRadius, halfHeight)) + 2;
        boolean placedAny = false;
        for (int dx = -bound; dx <= bound; dx++) {
            for (int dy = -bound; dy <= bound; dy++) {
                for (int dz = -bound; dz <= bound; dz++) {
                    double along = dx * axisX + dy * axisY + dz * axisZ;
                    if (along < -halfHeight || along > halfHeight) {
                        continue;
                    }
                    double perpX = dx - along * axisX;
                    double perpY = dy - along * axisY;
                    double perpZ = dz - along * axisZ;
                    double perpDist = Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);

                    double frac = (along + halfHeight) / height;
                    int layerIndex = Mth.clamp((int) Math.round(along + halfHeight), 0, height - 1);
                    double allowedRadius = maxRadius * Math.sin(Math.PI * frac) * layerJitter[layerIndex];
                    if (perpDist > allowedRadius) {
                        continue;
                    }

                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.isEmptyBlock(pos)) {
                        level.setBlock(pos, config.stateProvider().getState(random, pos), 3);
                        placedAny = true;
                    }
                }
            }
        }

        return placedAny;
    }
}
