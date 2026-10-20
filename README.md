# Trade Station

A Hytale server plugin that adds the **Trade Station**: a placeable block where players sell
building blocks they don't need and buy the ones they do. It's meant to cut down on grinding for
specific building materials and on inventory juggling, without going full creative mode.

> **Status:** work in progress, built against Hytale Early Access **0.7.2**
> (`ServerVersion >=0.7.2 <0.8.0`). The Hytale API changes between Early Access updates.

## How it works

- **No currency.** Each station has its own **balance**. Selling to a station adds the items' value
  to its balance; buying from it pays from that balance.
- **Shared by everyone.** Anyone can use any station; there's no ownership.
- **The balance travels with the station.** Breaking a station drops it as an item that keeps its
  balance; placing that item restores it.
- **Building blocks only.** Placeable rock, soil and wood blocks plus decoration made from them
  (941 items by default). No ingredients, tools, weapons, plants, metal, cloth, or creative-only
  blocks.
- **Buying costs more than selling earns.** Buying one item costs `ceil(base × (1 + spread))`, with
  a default spread of 25 %. Prices are derived from the game's recipes so that buying ingredients,
  crafting and selling the result can never make a profit (see [Prices](#prices)).

### Getting a station

Craft it at a **Workbench upgraded to tier 2** (crafting category), 3 seconds:

| Ingredient | Amount |
|---|---|
| Iron bar | 6 |
| Any log | 5 |
| Any rock | 5 |

### Using a station

Use the station (interact key) to open its trading screen. Each row is one tradeable item:

- **Sell 1 / Sell all** sell from anywhere in your inventory (hotbar, storage, backpack).
- **Buy 1 / Buy *stack*** buy one item or a full stack.
- **Search** narrows the list as you type. It matches item names and IDs, and every word must
  match (e.g. `sand brick`).
- **Only items I have** shows just what you can sell.
- Results are shown 30 per page.

All open screens of a station update when anyone trades with it. Every trade is checked again on the
server at the moment it happens, and it's all-or-nothing: if your inventory can't fit a purchase,
nothing is bought and the balance isn't touched.

## Installation

1. Build the jar (see [Development](#development)) or take `tradestation-<version>.jar` from a release.
2. Put it in the server's `mods/` folder (singleplayer: `UserData/Mods/`).
3. Start the server. On first start the plugin creates its data folder
   `mods/obut_tradestation/` with `tradeables.json` and `price_seeds.json`.

## Commands

All commands are under `/tradestation` and are **operator-only** by default. Each has an automatic
permission node such as `obut.tradestation.command.tradestation.reload`. `reload`,
`generateprices` and `checkprices` also work from the server console.

| Command | What it does |
|---|---|
| `/tradestation reload` | Reloads `tradeables.json`. A broken file keeps the previous price list and reports the error. |
| `/tradestation generateprices` | Derives a price list from `price_seeds.json` and the server's recipes. Doesn't activate it. |
| `/tradestation checkprices` | Checks the active price list for craft-and-sell loops. |
| `/tradestation sell` | Sells the held item type (the held stack's amount) to the station you're looking at. |
| `/tradestation buy <item> [--quantity=N]` | Buys from the station you're looking at. |
| `/tradestation setbalance <amount>` | Sets the balance of the station you're looking at (for testing). |

## Configuration

### `tradeables.json`: the active price list

An item is tradeable **if and only if** it's listed. The value is its **base sell price** (a
positive integer):

```json
{
  "spread": 0.25,
  "items": {
    "Rock_Stone_Cobble": 10,
    "Rock_Stone_Brick_Half": 5
  }
}
```

- Selling one item adds `base` to the station's balance.
- Buying one item costs `ceil(base × (1 + spread))`. `spread` must be ≥ 0.
- Malformed JSON, a missing or negative spread, or a missing `items` object rejects the whole file.
  Bad single entries (unknown item IDs, non-positive or non-integer prices, duplicates) are skipped
  with a warning in the log. A bad file never crashes the server.
- Apply edits with `/tradestation reload`.

### Prices

You normally don't write `tradeables.json` by hand: it's generated.

**`price_seeds.json`** holds hand-set prices for raw materials only (rock cobble, logs, natural
soils), plus what to leave out:

```json
{
  "spread": 0.25,
  "excludeCategories": ["Furniture.Benches", "Blocks.Plants", "Items", "Tool."],
  "excludeItems": ["Trade_Station", "Bench_*"],
  "seeds": {
    "Rock_Stone_Cobble": 10,
    "Wood_Oak_Trunk": 20
  }
}
```

`excludeCategories` match by prefix. `excludeItems` are exact IDs, or prefixes when they end in `*`.

**`/tradestation generateprices`** works out the price of every block that can be made from seeded
materials. It follows every way the game can turn items into other items:

- crafting recipes at any bench, including other mods' recipes;
- swapping a block within its block group at the Builder's Workbench, which is free and 1:1;
- placing a block and breaking it, if it drops something else.

An item's price is the **cheapest way to make it, rounded down**. Crafting therefore never increases
value, and craft-and-sell loops are impossible by construction. Blocks that need an unpriced input
(fabric, metal, plants, …) stay out of the list.

It writes two files to `mods/obut_tradestation/generated/`:

- `tradeables.generated.json` is the proposed price list.
- `tradeables.generated.report.txt` gives every price with what determined it, seeds that were
  lowered, and the blocks that couldn't be priced and why.

Typical workflow:

1. Adjust seeds or exclusions in `price_seeds.json`.
2. Run `/tradestation generateprices` and read the report.
3. Copy `generated/tradeables.generated.json` over `tradeables.json`.
4. Run `/tradestation reload`, then optionally `/tradestation checkprices`.

## Development

Requirements: **Java 25** (JetBrains Runtime recommended). Gradle comes with the wrapper.

```bash
./gradlew setupHytaleDev   # download the server + assets (needs a Hytale account login)
./gradlew build            # compile, run unit tests, build build/libs/tradestation-<version>.jar
./gradlew runServer        # local dev server on port 5520; join it from the game
```

Notes:

- **Decompile runs out of memory:** the server-source decompile step in `setupHytaleDev` may run
  out of memory with the default heap. Use `./gradlew --stop`, then
  `JDK_JAVA_OPTIONS=-Xmx4g ./gradlew setupHytaleDev`.
- **Dev server login:** the dev server must be logged in to Hytale to accept normal game clients.
  Run `/auth login device` in its console once; the login is stored encrypted in `run/`.
- **Dev server settings:** `runServer` passes `--allow-op` (so you can `/op self`) and caps the
  heap at 2.5 GB. Both are set in `build.gradle.kts`.
- **Live config in dev:** the dev server uses `src/main/resources` as the plugin's data folder,
  so `tradeables.json` and `price_seeds.json` there are the live config. Generator output lands in
  `src/main/resources/generated/`, which is git-ignored and left out of the jar.

Project metadata (IDs, version, author, server version range) lives in `gradle.properties`; the
manifest is generated from it (`./gradlew updatePluginManifest`). Project notes and design decisions
are in [`CLAUDE.md`](CLAUDE.md).

### Code layout

```
src/main/java/io/github/obut/tradestation/
  TradeStationPlugin.java     entry point: registers components, systems, interactions, commands
  config/                     tradeables.json: parsing, validation, loading/reloading
  pricing/                    price and balance math (plain Java)
  pricegen/                   price generator and loop checker (plain Java) + server asset adapter
  station/                    block component, break/place handling, trading logic
  ui/                         trading screen, open-viewer tracking, search/paging helpers
  commands/                   /tradestation and its subcommands
  text/                       translation helpers
src/main/resources/
  Server/Item/Items/Trade_Station.json      the block/item (model, recipe, interaction)
  Server/Languages/en-US/tradestation.lang  all player-facing text
  Common/UI/Custom/Pages/TradeStation/      trading screen layout
  tradeables.json, price_seeds.json         default configuration
```

Everything that doesn't need a running server (price math, config parsing, the price generator, loop
checking, search, paging, translation keys) is unit-tested; run `./gradlew test`.

### Translations

All player-facing text is in `Server/Languages/en-US/tradestation.lang`. To add a language, add
`Server/Languages/<code>/tradestation.lang` with the same keys; no code changes are needed. A unit
test makes sure every key the code and UI use exists in the English file.

## Known limitations

- The station reuses the Builder's Workbench model; it has no model of its own yet.
- **Creative mode:** placing a block doesn't use up the item there, so a station item carrying a
  balance can be placed repeatedly. Breaking a station in creative mode drops its item, so a balance
  is never silently lost.
- Not yet tested in-game:
  - two players viewing the same station at once;
  - stations destroyed by explosions or builder tools.

  Both should work: removal is handled generically for any cause.
- The price generator ignores random drop lists.

## Credits

Built on the [HytaleModding plugin template](https://hytalemodding.dev) and AzureDoom's
[Hytale Gradle Plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin).

## License

MIT (as declared in `gradle.properties`).
