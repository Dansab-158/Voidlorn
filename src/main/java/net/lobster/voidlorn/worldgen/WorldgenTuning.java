package net.lobster.voidlorn.worldgen;

import net.lobster.voidlorn.Config;
import net.lobster.voidlorn.Voidlorn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Holds the "how does the archipelago look" numbers players can retune from
 * {@code config/voidlorn-common.toml}, baked from {@link Config} into one plain, cheap-to-read
 * snapshot instead of going through {@code ModConfigSpec.get()} on every single block column.
 *
 * <p>{@link ArchipelagoCellSampler} and {@link ArchipelagoIslandsDensityFunction} run on chunk
 * generation's hot path - potentially millions of calls per loaded area - so they read
 * {@link #ACTIVE} once per call rather than touching the config system directly. {@code ACTIVE}
 * starts out pointing at {@link #DEFAULTS} so unit tests and GameTests (which never fire a config
 * event) still see sane, correct numbers; the real config only overrides it once the mod's config
 * file has actually loaded in a running game.
 */
public final class WorldgenTuning {
    private WorldgenTuning() {}

    /**
     * The size/spacing numbers for one layer of islands. Used twice - once for the real
     * archipelagos, once for the smaller "filler" islets scattered into the gaps between them -
     * so both layers share the exact same math in {@link ArchipelagoCellSampler} and
     * {@link ArchipelagoIslandsDensityFunction}, just with different numbers.
     */
    public record IslandProfile(
            int cellSize,
            int minCenterY, int maxCenterY,
            int minExtent, int maxExtent,
            int islandsMin, int islandsMax,
            double coreDistMin, double coreDistMax,
            double coreRadiusMin, double coreRadiusMax,
            double coreHeightMin, double coreHeightMax,
            double hillAmpNormal, double hillAmpSpiky
    ) {}

    public record Snapshot(
            IslandProfile main,
            IslandProfile filler,
            double shapeFreq, double thickFreq,
            double carveFreqXZ, double carveFreqY, double carveAmp
    ) {}

    // Mirrors whatever's currently in Config.java, so a fresh build/test run behaves exactly like
    // the shipped defaults even before any config file has been read.
    public static final Snapshot DEFAULTS = new Snapshot(
            new IslandProfile(
                    416,
                    40, 200,
                    32, 104,
                    4, 8,
                    80.0, 220.0,
                    22.0, 42.0,
                    10.0, 16.0,
                    10.0, 17.0
            ),
            new IslandProfile(
                    144,
                    40, 200,
                    16, 48,
                    2, 4,
                    16.0, 56.0,
                    5.0, 9.0,
                    3.0, 5.0,
                    4.0, 8.0
            ),
            0.045, 0.035,
            0.014, 0.022, 0.55
    );

    // volatile, not synchronized: Reloading can fire off the server/client thread (see
    // ModConfigEvent's own javadoc), and readers are on worldgen worker threads. A plain volatile
    // reference swap is enough here since Snapshot is immutable - readers always see a complete,
    // consistent set of numbers, never a half-updated one.
    public static volatile Snapshot ACTIVE = DEFAULTS;

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != Config.SPEC) {
            return; // some other mod's config finished loading, not ours
        }
        bake();
    }

    /** Re-reads every value out of Config and republishes a new snapshot. */
    static void bake() {
        IslandProfile main = bakeProfile(
                Config.ARCHIPELAGO_CELL_SIZE.get(),
                Config.ARCHIPELAGO_MIN_CENTER_Y.get(), Config.ARCHIPELAGO_MAX_CENTER_Y.get(),
                Config.ARCHIPELAGO_MIN_EXTENT.get(), Config.ARCHIPELAGO_MAX_EXTENT.get(),
                Config.ARCHIPELAGO_ISLANDS_MIN.get(), Config.ARCHIPELAGO_ISLANDS_MAX.get(),
                Config.ARCHIPELAGO_CORE_DIST_MIN.get(), Config.ARCHIPELAGO_CORE_DIST_MAX.get(),
                Config.ARCHIPELAGO_CORE_RADIUS_MIN.get(), Config.ARCHIPELAGO_CORE_RADIUS_MAX.get(),
                Config.ARCHIPELAGO_CORE_HEIGHT_MIN.get(), Config.ARCHIPELAGO_CORE_HEIGHT_MAX.get(),
                Config.ARCHIPELAGO_HILL_AMP_NORMAL.get(), Config.ARCHIPELAGO_HILL_AMP_SPIKY.get(),
                "archipelago"
        );

        IslandProfile filler = bakeProfile(
                Config.ARCHIPELAGO_FILLER_CELL_SIZE.get(),
                Config.ARCHIPELAGO_FILLER_MIN_CENTER_Y.get(), Config.ARCHIPELAGO_FILLER_MAX_CENTER_Y.get(),
                Config.ARCHIPELAGO_FILLER_MIN_EXTENT.get(), Config.ARCHIPELAGO_FILLER_MAX_EXTENT.get(),
                Config.ARCHIPELAGO_FILLER_ISLANDS_MIN.get(), Config.ARCHIPELAGO_FILLER_ISLANDS_MAX.get(),
                Config.ARCHIPELAGO_FILLER_CORE_DIST_MIN.get(), Config.ARCHIPELAGO_FILLER_CORE_DIST_MAX.get(),
                Config.ARCHIPELAGO_FILLER_CORE_RADIUS_MIN.get(), Config.ARCHIPELAGO_FILLER_CORE_RADIUS_MAX.get(),
                Config.ARCHIPELAGO_FILLER_CORE_HEIGHT_MIN.get(), Config.ARCHIPELAGO_FILLER_CORE_HEIGHT_MAX.get(),
                Config.ARCHIPELAGO_FILLER_HILL_AMP_NORMAL.get(), Config.ARCHIPELAGO_FILLER_HILL_AMP_SPIKY.get(),
                "archipelagoFiller"
        );

        ACTIVE = new Snapshot(
                main, filler,
                Config.ARCHIPELAGO_SHAPE_FREQ.get(), Config.ARCHIPELAGO_THICK_FREQ.get(),
                Config.ARCHIPELAGO_CARVE_FREQ_XZ.get(), Config.ARCHIPELAGO_CARVE_FREQ_Y.get(),
                Config.ARCHIPELAGO_CARVE_AMP.get()
        );
    }

    /**
     * Builds one {@link IslandProfile}, defending against a player swapping a min/max pair
     * backwards (ModConfigSpec can only range-check one value at a time, it can't compare two
     * fields against each other) and against a core-distance that would reach past the cell size,
     * which would break the 3x3 neighbourhood search {@link ArchipelagoIslandsDensityFunction}
     * relies on to find cores near a cell boundary.
     */
    private static IslandProfile bakeProfile(
            int cellSize,
            int rawMinCenterY, int rawMaxCenterY,
            int rawMinExtent, int rawMaxExtent,
            int rawIslandsMin, int rawIslandsMax,
            double rawCoreDistMin, double rawCoreDistMax,
            double rawCoreRadiusMin, double rawCoreRadiusMax,
            double rawCoreHeightMin, double rawCoreHeightMax,
            double hillAmpNormal, double hillAmpSpiky,
            String configPrefix
    ) {
        int minCenterY = Math.min(rawMinCenterY, rawMaxCenterY);
        int maxCenterY = Math.max(rawMinCenterY, rawMaxCenterY);
        int minExtent = Math.min(rawMinExtent, rawMaxExtent);
        int maxExtent = Math.max(rawMinExtent, rawMaxExtent);
        int islandsMin = Math.min(rawIslandsMin, rawIslandsMax);
        int islandsMax = Math.max(rawIslandsMin, rawIslandsMax);
        double coreRadiusMin = Math.min(rawCoreRadiusMin, rawCoreRadiusMax);
        double coreRadiusMax = Math.max(rawCoreRadiusMin, rawCoreRadiusMax);
        double coreHeightMin = Math.min(rawCoreHeightMin, rawCoreHeightMax);
        double coreHeightMax = Math.max(rawCoreHeightMin, rawCoreHeightMax);

        double coreDistMin = Math.min(rawCoreDistMin, rawCoreDistMax);
        double coreDistMax = Math.max(rawCoreDistMin, rawCoreDistMax);
        if (coreDistMax >= cellSize) {
            Voidlorn.LOGGER.warn("[WorldgenTuning] {}CoreDistMax ({}) must stay below {}CellSize ({}) - "
                    + "clamping it down.", configPrefix, coreDistMax, configPrefix, cellSize);
            coreDistMax = cellSize - 1.0;
        }

        return new IslandProfile(
                cellSize,
                minCenterY, maxCenterY,
                minExtent, maxExtent,
                islandsMin, islandsMax,
                coreDistMin, coreDistMax,
                coreRadiusMin, coreRadiusMax,
                coreHeightMin, coreHeightMax,
                hillAmpNormal, hillAmpSpiky
        );
    }
}
