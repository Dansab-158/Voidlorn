# Voidlorn

Voidlorn replaces vanilla's End with a scattered archipelago of floating islands set against open
void, instead of one solid landmass. Islands are grouped into archipelagos with real gaps between
them, filled out by smaller stepping-stone islets and rare floating obsidian monoliths so the
space between them stays interesting to explore rather than empty.

Requires [NeoForge](https://neoforged.net/) for Minecraft 1.21.1.

## What it changes

- **The End's terrain** is generated from scratch as an archipelago: clusters of islands (each
  cluster is one "archipelago") separated by void, rather than vanilla's single central landmass.
- **Smaller filler islets** dot the gaps between archipelagos, giving a place to land if you're
  flying between them.
- **Floating monoliths** - rare, tapering shards of obsidian and crying obsidian - drift through
  the void as occasional landmarks.
- A small area around world origin (0, 0) keeps vanilla's original End terrain and the dragon
  fight untouched.
- The world's real seed drives the archipelago layout, so every world generates differently, the
  same as vanilla.

## Compatibility

- Other mods' End biomes are picked up automatically if they're tagged into `#minecraft:is_end`
  (or `#c:is_end`) - no extra configuration needed on either side.
- If you're running another mod that also rewrites End generation, set `overrideVanillaEnd` to
  `false` in the config to let vanilla End generation apply instead of Voidlorn's.

## Configuration

Nearly every aspect of the generation is exposed in `config/voidlorn-common.toml`: archipelago
spacing and size, island height distribution, filler islet density, and the floating monoliths'
size and spawn rate. Each entry in the file documents what it does, its default value, and any
limits it has to respect (for example, some values have to stay smaller than a related value, or
the generator can't place islands correctly) - this README doesn't duplicate that, the config file
itself is the reference.

## License

MIT - see the mod metadata for details.
