# Trade Station

A Hytale server plugin that adds the **Trade Station**: a placeable block where players sell
building blocks they don't need and buy the ones they do. It's meant to cut down on grinding for
specific building materials and on inventory juggling, without going full creative mode.

> **Status:** version 1.0.0, built against Hytale Early Access **0.7.2**
> (`ServerVersion >=0.7.2 <0.8.0`). The Hytale API changes between Early Access updates.

## How it works

- **No currency.** Each station has its own **balance**. Selling to a station adds the items' value
  to its balance; buying from it pays from that balance.
- **Shared by everyone.** Anyone can use any station; there's no ownership.
- **Unlock what you trade.** A new station trades nothing. Players **donate** base materials to
  unlock groups of items. Donating 500 stone cobble, for example, unlocks cobble, stone, stone bricks
  and everything else made only from them. Unlocks are per station, permanent, and pure investment:
  donated items are used up and add nothing to the balance.
- **Everything travels with the station.** Breaking a station drops it as an item that keeps its
  balance and unlocks; placing that item restores them.
- **Building blocks only.** Placeable rock, soil and wood blocks, plus decoration made from them,
  that can be crafted from other tradeable blocks alone. That's 919 items by default. No
  ingredients, tools, weapons, plants, metal, cloth or creative-only blocks, and nothing that needs
  sticks, fibre, fabric and the like.
- **Fair prices.** By default, buying costs exactly what selling earns (spread 0), so trades are
  fully reversible. A spread can be configured. Prices are derived from the game's recipes so that
  buying ingredients, crafting and selling the result can never make a profit (see [Prices](#prices)).

### Getting a station

Craft it at a **Workbench upgraded to tier 2** (crafting category), 3 seconds:

| Ingredient | Amount |
|---|---|
| Iron bar | 6 |
| Any log | 5 |
| Any rock | 5 |

### Using a station

Use the station (interact key) to open it. The screen has three tabs:

- **Sell:** the unlocked items you have, with **Sell 1 / Sell all**. Items are taken from anywhere
  in your inventory (hotbar, storage, backpack).
- **Buy:** all unlocked items, with **Buy 1 / Buy *stack*** (one item or a full stack).
- **Unlock:** one row per unlock, with its progress (e.g. 320 / 500), how many items it opens up,
  and **Donate 1 / Donate all**. Two filters, both on by default:
  - **Hide unlocked**
  - **Only what I can donate**

  Donations stop at the required amount. A station with nothing unlocked opens on this tab.

All tabs have a **search** box that matches item names and IDs; every word must match, e.g.
`sand brick`. Results are shown 30 per page.

All open screens of a station update when anyone trades or donates. Every action is checked again on
the server at the moment it happens, and it's all-or-nothing: if your inventory can't fit a
purchase, nothing is bought and the balance isn't touched.

## Installation

1. Build the jar (see [Development](#development)) or take `tradestation-<version>.jar` from a release.
2. Put it in the server's `mods/` folder (singleplayer: `UserData/Mods/`).
3. Start the server. On first start the plugin creates its data folder `mods/obut_tradestation/`:
   - `price_seeds.json`: the default raw-material prices and unlock settings.
   - `tradeables.json`: **generated from this server's recipes**, including other installed mods.
     If generation fails, the default list bundled in the jar is used instead.

   Existing files are never overwritten. To regenerate later, see [Prices](#prices).

## Commands

All commands are under `/tradestation` and are **operator-only** by default. Each has an automatic
permission node such as `obut.tradestation.command.tradestation.reload`. `reload`,
`generateprices`, `checkprices`, `spread` and the `item` / `unlockdef` commands also work from the
server console. Together they can build a complete `tradeables.json` in game.

The `item`, `unlockdef` and `spread` commands do the following:
- They write `tradeables.json`, then reload it and refresh open station screens.
- They warn right away if a hand-set price creates a craft-and-sell loop.
- Station progress is stored per unlock ID: changing an amount never re-locks a completed unlock,
  and deleting and re-adding an ID keeps progress.
- Note: running `generateprices` again and copying its output over `tradeables.json` replaces such
  edits.

| Command | What it does |
|---|---|
| `/tradestation reload` | Reloads `tradeables.json`. A broken file keeps the previous price list and reports the error. |
| `/tradestation generateprices` | Derives a price list from `price_seeds.json` and the server's recipes. Doesn't activate it. |
| `/tradestation checkprices` | Checks the active price list for craft-and-sell loops. |
| `/tradestation item info <item>` | Shows an item's price and required unlocks. |
| `/tradestation item add <item> <price> [--requires=…]` | Makes an item tradeable. |
| `/tradestation item remove <item>` | Makes an item no longer tradeable. |
| `/tradestation item price <item> <price>` | Changes an item's price. |
| `/tradestation item requires <item> <spec>` | Changes an item's required unlocks, e.g. `Rock_Stone_Cobble+Wood_Oak_Trunk\|Wood_Ash_Trunk` (`+` = and, `\|` = any one of, `none` = no unlock). |
| `/tradestation unlockdef list` / `info <id>` | Shows all unlocks / one unlock: amount, accepted items, how many items require it. |
| `/tradestation unlockdef add <id> <amount> <items>` | Defines a new unlock; `<items>` joined with `+`, e.g. `Wood_Oak_Trunk+Wood_Ash_Trunk`. |
| `/tradestation unlockdef amount <id> <amount>` | Changes how many items must be donated. |
| `/tradestation unlockdef accept\|unaccept <id> <item>` | Adds/removes an item that counts as a donation (at least one stays). |
| `/tradestation unlockdef remove <id> [--force]` | Deletes an unlock. If items require it, refused unless `--force`: then it's also taken out of their requirements. An item can end up needing no unlock at all, i.e. tradeable everywhere. |
| `/tradestation spread <value>` | Sets the spread (`0` = fully reversible trades). |
| `/tradestation sell` | Sells the held item type (the held stack's amount) to the station you're looking at. |
| `/tradestation buy <item> [--quantity=N]` | Buys from the station you're looking at. |
| `/tradestation setbalance <amount>` | Sets the balance of the station you're looking at (for testing). |
| `/tradestation unlock <id\|all>` | Completes an unlock at the station you're looking at, without donating (for testing). |
| `/tradestation lock <id\|all>` | Undoes an unlock and its donations there (for testing). |

## Configuration

### `tradeables.json`: the active price list

An item is tradeable **if and only if** it's listed. Its value is either just its **base sell price**
(a positive integer) or an object that also says which unlocks it needs:

```json
{
  "spread": 0,
  "unlocks": {
    "Rock_Stone_Cobble": { "amount": 500, "accepts": ["Rock_Stone_Cobble"] },
    "Clay": { "amount": 500, "accepts": ["Soil_Clay_Red", "Soil_Clay_Blue", "..."] }
  },
  "items": {
    "Rock_Stone_Brick": { "price": 10, "requires": ["Rock_Stone_Cobble"] },
    "Wood_Hardwood_Planks": { "price": 20, "requires": [["Wood_Oak_Trunk", "Wood_Ash_Trunk", "..."]] },
    "Some_Free_Item": 5
  }
}
```

- **Prices:** selling one item adds `base` to the station's balance. Buying one costs
  `ceil(base × (1 + spread))`, where `spread` must be ≥ 0.
- **Unlocks:** an unlock is complete once `amount` items from `accepts` have been donated. Each item
  in `accepts` counts as 1, and any mix works.
- **Requirements:** each entry in `requires` is one requirement. A plain string means that unlock is
  needed; a list means **any one of** those unlocks. An item is tradeable at a station once all of
  its requirements are met there. Items without `requires` need no unlock.
- **Validation:**
  - Malformed JSON, a missing or negative spread, or a missing `items` object rejects the whole file.
  - Bad single entries are skipped with a warning in the log: unknown item IDs, invalid prices,
    duplicates, undefined unlocks, invalid unlock definitions.
  - A bad file never crashes the server.
- **Unlocks are permanent:** completed unlocks stay unlocked even if amounts change later.
- Apply edits with `/tradestation reload`.

### Prices

You normally don't write `tradeables.json` by hand: it's generated.

**`price_seeds.json`** holds hand-set prices for raw materials only (rock cobble, logs, natural
soils), plus what to leave out and how unlocks work:

```json
{
  "spread": 0,
  "excludeCategories": ["Furniture.Benches", "Blocks.Plants", "Items", "Tool."],
  "excludeItems": ["Trade_Station", "Bench_*"],
  "seeds": {
    "Rock_Stone_Cobble": 10,
    "Wood_Oak_Trunk": 20
  },
  "unlocks": {
    "defaultAmount": 500,
    "amounts": { "Wood_Oak_Trunk": 200 },
    "groups": { "Clay": ["Soil_Clay_Red", "Soil_Clay_Blue"] }
  }
}
```

- `excludeCategories` match by prefix. `excludeItems` are exact IDs, or prefixes when they end in `*`.
- **Unlocks:** each seed is its own unlock, unless it's listed in a group: all seeds of a group
  share one unlock, and donations of any member count. `amounts` overrides `defaultAmount` per
  unlock. Without an `unlocks` section, nothing needs unlocking.

**`/tradestation generateprices`** works out the price of every block that can be made from seeded
materials. It follows every way the game can turn items into other items:

- crafting recipes at any bench, including other mods' recipes;
- swapping a block within its block group at the Builder's Workbench, which is free and 1:1;
- placing a block and breaking it, if it drops something else.

The rules:

- **Prices:** an item's price is the **cheapest way to make it, rounded down**. Crafting therefore
  never increases value, and craft-and-sell loops are impossible by construction.
- **What gets listed:** only blocks that can be made **from other tradeable blocks alone**. Prices
  still consider all recipes, including routes via sticks or fibre, so loops stay impossible.
- **Required unlocks:** an item needs the unlocks of the seeds its cheapest recipe chain starts
  from. Where a recipe accepts several equally cheap materials (e.g. "any hardwood log"), any one
  of their unlocks is enough.

It writes two files to `mods/obut_tradestation/generated/`:

- `tradeables.generated.json` is the proposed price list.
- `tradeables.generated.report.txt` contains:
  - every price, with what determined it;
  - every unlock and its items;
  - items whose requirements offer alternatives;
  - what was left out, and why.

Typical workflow:

1. Adjust seeds, exclusions or unlock settings in `price_seeds.json`.
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
  unlock/                     per-station unlock progress rules (plain Java)
  station/                    block component, break/place handling, trading logic
  ui/                         trading screen (Sell/Buy/Unlock tabs), open-viewer tracking, search/paging
  commands/                   /tradestation and its subcommands
  text/                       translation helpers
src/main/resources/
  Server/Item/Items/Trade_Station.json      the block/item (model, recipe, interaction)
  Server/Languages/en-US/tradestation.lang  all player-facing text
  Common/UI/Custom/Pages/TradeStation/      trading screen layout and tab icons
  tradeables.json, price_seeds.json         default configuration
```

Everything that doesn't need a running server is unit-tested: price math, config parsing, the price
generator, unlock requirements, loop checking, unlock rules, search, paging and translation keys.
Run `./gradlew test`.

### Translations

All player-facing text is in `Server/Languages/en-US/tradestation.lang`. To add a language, add
`Server/Languages/<code>/tradestation.lang` with the same keys; no code changes are needed. A unit
test makes sure every key the code and UI use exists in the English file.

## Known limitations

- **Creative mode:** placing a block doesn't use up the item there, so a station item carrying a
  balance and unlocks can be placed repeatedly. Breaking a station in creative mode drops its item, so a balance
  is never silently lost.
- Not yet tested in-game:
  - two players viewing the same station at once;
  - stations destroyed by explosions or builder tools.

  Both should work: removal is handled generically for any cause.
- The price generator ignores random drop lists.
- Grass, leaf and needle soil, and pathways aren't tradeable: their recipes need plant fibre, which
  isn't a block.

## Credits

Built on the [HytaleModding plugin template](https://hytalemodding.dev) and AzureDoom's
[Hytale Gradle Plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin).

## License

MIT (as declared in `gradle.properties`).
