package net.lobster.voidlorn;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every player-facing tuning knob for Voidlorn's End overhaul lives here, backed by
 * {@code config/voidlorn-common.toml}. See {@link net.lobster.voidlorn.worldgen.WorldgenTuning}
 * for how the archipelago/small-island values get from this file into the actual world generator
 * without paying for a config lookup on every block column.
 *
 * <p>The Java field names below (e.g. {@code ARCHIPELAGO_CORE_RADIUS_MIN}) are only ever used
 * internally by our own code - the string passed to each {@code .define(...)} call is the actual
 * name a player sees and edits in the .toml file, and those are written to read naturally without
 * needing to know how the generator works internally.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Voidlorn normally replaces vanilla's End dimension outright with its own archipelago. Turn
    // this off if you're running another mod that also rewrites End generation and you'd rather
    // have a genuinely vanilla End (same biomes, same terrain shape) instead of ours - this is a
    // real fallback, not a half-measure: with it off, the biome source and terrain both delegate
    // straight to vanilla's own logic instead of trying to approximate it.
    public static final ModConfigSpec.BooleanValue OVERRIDE_VANILLA_END = BUILDER
            .comment("Set this to false if another mod also changes how the End generates, and you'd",
                    "rather let that mod (or plain vanilla) take over instead of Voidlorn's archipelago.",
                    "Default: true.")
            .define("overrideVanillaEnd", true);

    // --- Archipelagos: the real islands, grouped into clusters with void between clusters ---

    public static final ModConfigSpec.IntValue ARCHIPELAGO_CELL_SIZE;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_MIN_CENTER_Y;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_MAX_CENTER_Y;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_MIN_EXTENT;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_MAX_EXTENT;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_ISLANDS_MIN;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_ISLANDS_MAX;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_CORE_DIST_MIN;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_CORE_DIST_MAX;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_CORE_RADIUS_MIN;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_CORE_RADIUS_MAX;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_CORE_HEIGHT_MIN;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_CORE_HEIGHT_MAX;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_HILL_AMP_NORMAL;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_HILL_AMP_SPIKY;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_SHAPE_FREQ;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_THICK_FREQ;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_CARVE_FREQ_XZ;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_CARVE_FREQ_Y;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_CARVE_AMP;

    static {
        BUILDER.push("archipelago");

        ARCHIPELAGO_CELL_SIZE = BUILDER
                .comment("How far apart the groups of islands (archipelagos) are from each other, on",
                        "average, in blocks. Lower this to make them feel closer together while flying",
                        "around; raise it for more open space between them. Default: 416.")
                .defineInRange("archipelagoSpacing", 416, 256, 1024);

        ARCHIPELAGO_MIN_CENTER_Y = BUILDER
                .comment("Together with maxArchipelagoHeight, sets the range of altitudes where a group",
                        "of islands can appear. Each archipelago picks one \"home\" height somewhere in",
                        "this range, then its own islands scatter around that height. Default: 40.")
                .defineInRange("minArchipelagoHeight", 40, 8, 128);

        ARCHIPELAGO_MAX_CENTER_Y = BUILDER
                .comment("The upper end of that same altitude range. Widen the gap between this and",
                        "minArchipelagoHeight to spread archipelagos across more of the dimension's height",
                        "instead of them all clustering around one typical altitude. Default: 200.")
                .defineInRange("maxArchipelagoHeight", 200, 96, 248);

        ARCHIPELAGO_MIN_EXTENT = BUILDER
                .comment("The least an individual island's height can drift away from its archipelago's",
                        "\"home\" altitude, in blocks. Default: 32.")
                .defineInRange("minHeightVariation", 32, 8, 96);

        ARCHIPELAGO_MAX_EXTENT = BUILDER
                .comment("The most an individual island's height can drift away from its archipelago's",
                        "\"home\" altitude. Bigger numbers mean more dramatic height differences between",
                        "islands that are otherwise part of the same group. Default: 104.")
                .defineInRange("maxHeightVariation", 104, 32, 160);

        ARCHIPELAGO_ISLANDS_MIN = BUILDER
                .comment("Fewest islands a single archipelago can have. Default: 4.")
                .defineInRange("minIslandsPerArchipelago", 4, 1, 12);

        ARCHIPELAGO_ISLANDS_MAX = BUILDER
                .comment("Most islands a single archipelago can have - raise this for busier, denser",
                        "groups of islands. Default: 8.")
                .defineInRange("maxIslandsPerArchipelago", 8, 1, 16);

        ARCHIPELAGO_CORE_DIST_MIN = BUILDER
                .comment("The closest, in blocks, two islands in the same archipelago can sit to each",
                        "other. This is what keeps a gap of void between them - raise it if islands feel",
                        "too crowded together. Default: 80.0.")
                .defineInRange("minIslandSpacing", 80.0, 0.0, 511.0);

        ARCHIPELAGO_CORE_DIST_MAX = BUILDER
                .comment("The farthest two islands in the same archipelago can sit from each other. Must",
                        "stay below archipelagoSpacing or islands can get cut off at the edge of their",
                        "group - if you set it too high, it's automatically brought back down and a",
                        "warning is logged. Default: 220.0.")
                .defineInRange("maxIslandSpacing", 220.0, 0.0, 511.0);

        ARCHIPELAGO_CORE_RADIUS_MIN = BUILDER
                .comment("The smallest an island's footprint can be, in blocks. Don't go much below 20 -",
                        "smaller than that and islands stop looking like real islands and start looking",
                        "like scattered rubble. Default: 22.0.")
                .defineInRange("minIslandSize", 22.0, 20.0, 64.0);

        ARCHIPELAGO_CORE_RADIUS_MAX = BUILDER
                .comment("The biggest an island's footprint can be, in blocks. This is the main \"how big",
                        "do islands get\" setting. Default: 42.0.")
                .defineInRange("maxIslandSize", 42.0, 24.0, 128.0);

        ARCHIPELAGO_CORE_HEIGHT_MIN = BUILDER
                .comment("How thick, top to bottom, the thinnest islands are, in blocks. Keep this at 8",
                        "or above so islands still feel solid instead of paper-thin. Default: 10.0.")
                .defineInRange("minIslandThickness", 10.0, 8.0, 16.0);

        ARCHIPELAGO_CORE_HEIGHT_MAX = BUILDER
                .comment("How thick the thickest islands are, in blocks. Keep this at 16 or below so the",
                        "biggest islands don't turn into solid bricks of end stone. Default: 16.0.")
                .defineInRange("maxIslandThickness", 16.0, 8.0, 16.0);

        ARCHIPELAGO_HILL_AMP_NORMAL = BUILDER
                .comment("How tall, in blocks, the hills on top of a typical island can get. Default: 10.0.")
                .defineInRange("hillHeight", 10.0, 0.0, 64.0);

        ARCHIPELAGO_HILL_AMP_SPIKY = BUILDER
                .comment("How tall the hill gets on the occasional dramatic island (roughly 1 in 8) that",
                        "gets a taller, spikier monolith instead of a normal hill. Default: 17.0.")
                .defineInRange("dramaticHillHeight", 17.0, 0.0, 96.0);

        ARCHIPELAGO_SHAPE_FREQ = BUILDER
                .comment("How closely packed the hills and bumps are across an island's surface. Higher",
                        "makes the terrain busier, with more small hills close together; lower makes it",
                        "smoother and calmer. Needs to stay reasonably high so even a small island shows",
                        "a couple of real hills instead of one flat, tilted slope. Default: 0.045.")
                .defineInRange("hillBumpiness", 0.045, 0.001, 0.2);

        ARCHIPELAGO_THICK_FREQ = BUILDER
                .comment("How quickly an island's thickness changes as you move across it. Default: 0.035.")
                .defineInRange("thicknessVariation", 0.035, 0.001, 0.2);

        ARCHIPELAGO_CARVE_FREQ_XZ = BUILDER
                .comment("How tightly packed, sideways, the canyons and overhangs carved into islands",
                        "are. Default: 0.014.")
                .defineInRange("caveBumpinessHorizontal", 0.014, 0.001, 0.2);

        ARCHIPELAGO_CARVE_FREQ_Y = BUILDER
                .comment("How tightly packed, vertically, those same canyons and overhangs are.",
                        "Default: 0.022.")
                .defineInRange("caveBumpinessVertical", 0.022, 0.001, 0.2);

        ARCHIPELAGO_CARVE_AMP = BUILDER
                .comment("How aggressively those canyons and overhangs cut into an island's shape. Higher",
                        "makes for more dramatic, broken-up islands; lower makes for smoother, more solid",
                        "ones. Default: 0.55.")
                .defineInRange("caveCarvingStrength", 0.55, 0.0, 2.0);

        BUILDER.pop();
    }

    // --- Small islands: little stepping stones scattered into the gaps between archipelagos ---
    // so flying between them doesn't mean crossing a long stretch of nothing. Same shape rules as
    // the real islands above (they share hillBumpiness/thicknessVariation/etc.), just smaller and
    // packed into a tighter grid of their own. Wherever a small island happens to overlap a real
    // one, whichever is more solid at that spot wins, so a small island can never cut a real one in half.

    public static final ModConfigSpec.IntValue ARCHIPELAGO_FILLER_CELL_SIZE;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_FILLER_MIN_CENTER_Y;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_FILLER_MAX_CENTER_Y;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_FILLER_MIN_EXTENT;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_FILLER_MAX_EXTENT;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_FILLER_ISLANDS_MIN;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_FILLER_ISLANDS_MAX;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_FILLER_CORE_DIST_MIN;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_FILLER_CORE_DIST_MAX;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_FILLER_CORE_RADIUS_MIN;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_FILLER_CORE_RADIUS_MAX;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_FILLER_CORE_HEIGHT_MIN;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_FILLER_CORE_HEIGHT_MAX;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_FILLER_HILL_AMP_NORMAL;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_FILLER_HILL_AMP_SPIKY;

    static {
        BUILDER.push("small_islands");

        ARCHIPELAGO_FILLER_CELL_SIZE = BUILDER
                .comment("Average spacing, in blocks, between small islands. Keep this well below",
                        "archipelagoSpacing so they actually fill the gaps instead of forming another,",
                        "smaller archipelago pattern of their own. Default: 144.")
                .defineInRange("smallIslandSpacing", 144, 32, 400);

        ARCHIPELAGO_FILLER_MIN_CENTER_Y = BUILDER
                .comment("Same idea as minArchipelagoHeight, but for small islands. Default: 40.")
                .defineInRange("smallIslandMinHeight", 40, 8, 128);

        ARCHIPELAGO_FILLER_MAX_CENTER_Y = BUILDER
                .comment("Same idea as maxArchipelagoHeight, but for small islands. Default: 200.")
                .defineInRange("smallIslandMaxHeight", 200, 96, 248);

        ARCHIPELAGO_FILLER_MIN_EXTENT = BUILDER
                .comment("Same idea as minHeightVariation, but for small islands. Default: 16.")
                .defineInRange("smallIslandMinHeightVariation", 16, 4, 96);

        ARCHIPELAGO_FILLER_MAX_EXTENT = BUILDER
                .comment("Same idea as maxHeightVariation, but for small islands. Default: 48.")
                .defineInRange("smallIslandMaxHeightVariation", 48, 16, 160);

        ARCHIPELAGO_FILLER_ISLANDS_MIN = BUILDER
                .comment("Fewest small islands in one cluster. Default: 2.")
                .defineInRange("minSmallIslandsPerGroup", 2, 1, 12);

        ARCHIPELAGO_FILLER_ISLANDS_MAX = BUILDER
                .comment("Most small islands in one cluster. Default: 4.")
                .defineInRange("maxSmallIslandsPerGroup", 4, 1, 16);

        ARCHIPELAGO_FILLER_CORE_DIST_MIN = BUILDER
                .comment("Same idea as minIslandSpacing, but for small islands. Default: 16.0.")
                .defineInRange("minSmallIslandSpacing", 16.0, 0.0, 399.0);

        ARCHIPELAGO_FILLER_CORE_DIST_MAX = BUILDER
                .comment("Same idea as maxIslandSpacing, but for small islands - must stay below",
                        "smallIslandSpacing (enforced automatically). Default: 56.0.")
                .defineInRange("maxSmallIslandSpacing", 56.0, 0.0, 399.0);

        ARCHIPELAGO_FILLER_CORE_RADIUS_MIN = BUILDER
                .comment("Smallest small-island footprint, in blocks. Kept small on purpose - these are",
                        "meant to feel like a quick stepping stone, not a real island. Default: 5.0.")
                .defineInRange("minSmallIslandSize", 5.0, 3.0, 32.0);

        ARCHIPELAGO_FILLER_CORE_RADIUS_MAX = BUILDER
                .comment("Largest small-island footprint, in blocks. Default: 9.0.")
                .defineInRange("maxSmallIslandSize", 9.0, 3.0, 48.0);

        ARCHIPELAGO_FILLER_CORE_HEIGHT_MIN = BUILDER
                .comment("Thinnest a small island can be, top to bottom, in blocks. Default: 3.0.")
                .defineInRange("minSmallIslandThickness", 3.0, 2.0, 16.0);

        ARCHIPELAGO_FILLER_CORE_HEIGHT_MAX = BUILDER
                .comment("Thickest a small island can be, top to bottom, in blocks. Default: 5.0.")
                .defineInRange("maxSmallIslandThickness", 5.0, 2.0, 20.0);

        ARCHIPELAGO_FILLER_HILL_AMP_NORMAL = BUILDER
                .comment("Same idea as hillHeight, but for small islands. Default: 4.0.")
                .defineInRange("smallIslandHillHeight", 4.0, 0.0, 32.0);

        ARCHIPELAGO_FILLER_HILL_AMP_SPIKY = BUILDER
                .comment("Same idea as dramaticHillHeight, but for small islands. Default: 8.0.")
                .defineInRange("smallIslandDramaticHillHeight", 8.0, 0.0, 48.0);

        BUILDER.pop();
    }

    // --- Floating monoliths: rare, tapering shards of obsidian and crying obsidian drifting in ---
    // the void between archipelagos - purely decorative landmarks, not something to land on.

    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_MONOLITH_CHANCE;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_MONOLITH_MIN_HEIGHT;
    public static final ModConfigSpec.IntValue ARCHIPELAGO_MONOLITH_MAX_HEIGHT;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_MONOLITH_MIN_RADIUS;
    public static final ModConfigSpec.DoubleValue ARCHIPELAGO_MONOLITH_MAX_RADIUS;

    static {
        BUILDER.push("floating_monoliths");

        ARCHIPELAGO_MONOLITH_CHANCE = BUILDER
                .comment("The odds that any given chunk spawns one of the rare floating monoliths. 0.002",
                        "means roughly 1 in 500 chunks. Keep this low - these are meant to be an",
                        "occasional landmark breaking up the void, not something you constantly bump",
                        "into. Default: 0.002.")
                .defineInRange("floatingMonolithChance", 0.002, 0.0, 1.0);

        ARCHIPELAGO_MONOLITH_MIN_HEIGHT = BUILDER
                .comment("Shortest a floating monolith can be, from one pointed tip to the other, in",
                        "blocks. Default: 6.")
                .defineInRange("floatingMonolithMinHeight", 6, 2, 64);

        ARCHIPELAGO_MONOLITH_MAX_HEIGHT = BUILDER
                .comment("Tallest a floating monolith can be, tip to tip, in blocks. Default: 24.")
                .defineInRange("floatingMonolithMaxHeight", 24, 2, 96);

        ARCHIPELAGO_MONOLITH_MIN_RADIUS = BUILDER
                .comment("Narrowest a floating monolith gets at its widest point, in blocks. Default: 2.0.")
                .defineInRange("floatingMonolithMinSize", 2.0, 1.0, 32.0);

        ARCHIPELAGO_MONOLITH_MAX_RADIUS = BUILDER
                .comment("Widest a floating monolith gets at its widest point, in blocks. Default: 7.0.")
                .defineInRange("floatingMonolithMaxSize", 7.0, 1.0, 48.0);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
