# CLAUDE.md — Trade Station (Hytale server plugin)

## What this project is

A Hytale (Early Access, 0.7.x) server plugin + asset pack that adds a **Trade Station**: a placeable, interactable block where players sell and buy whitelisted building-block items. The goal is to reduce grinding for specific building materials and reduce inventory juggling, without going full creative mode.

Working name is "Trade Station" — the owner may rename it; follow whatever `gradle.properties` says.

## Working with the developer

- Background and preferences of the developer are in the workspace `../CLAUDE.md` (not part of this repo); follow them.
- You cannot run the game client. Anything that needs in-game verification: tell the developer exactly what to do in-game and what log output / behaviour to report back. Don't mark a milestone done until they confirm.

## Toolchain & commands

- Java 25 (JetBrains Runtime recommended). Gradle wrapper — never require a system Gradle.
- Based on the HytaleModding plugin template using AzureDoom's `com.azuredoom.hytale-tools` Gradle plugin. Project metadata lives in `gradle.properties`; manifest is generated from it.

```bash
./gradlew setupHytaleDev                       # fetch/sync server jar + assets for dev
./gradlew build                                # compile, test, package -> build/libs/
./gradlew runServer                            # local dev server; connect from game to localhost
./gradlew runServer -Ddebug=true -Dhotswap=true
./gradlew updatePluginManifest                 # after changing manifest-related properties
./gradlew build --refresh-dependencies         # if resolution fails
./gradlew hytaleJvmDoctor                      # JVM / hot-swap diagnostics
```

Singleplayer mods folder: `UserData/Mods` in the Hytale data folder

Always run `./gradlew build` after changes and fix all compile errors and test failures before reporting back.

## API accuracy rules (important)

The Hytale API is young, changes between Early Access updates, and is not well represented in your training data. Community docs (hytalemodding.dev) are good but can lag behind the current server version.

- **Never invent class names, method signatures, event names, or JSON keys.** Verify against the actual server JAR resolved by Gradle (browse classes in the IDE / via `javap`, or the sources/Javadoc the Gradle plugin sets up), or against an up-to-date guide.
- If you can't verify something, say so explicitly and propose how to check it, rather than guessing.
- When a community guide and the server JAR disagree, the JAR wins.
- Useful references: https://hytalemodding.dev/en/docs (guides: block components, inventory management, custom items/interactions, custom blocks, configuration files, events; "Custom UI" under official documentation), https://github.com/AzureDoom/Hytale-Gradle-Plugin

## Design (decided — do not change without asking)

### Gameplay
- Players sell whitelisted items to a station → the station's **balance** increases. Players buy whitelisted items from a station → its balance decreases.
- **No currency item.** The balance is a number stored on the station itself.
- **Stations and balances are shared** by all players. No ownership.
- **Breaking a station keeps its balance in the dropped item.** Placing that item restores the balance. A station item with no balance data places as a fresh station with balance 0.

### Data model
- Balance is a **`long`** (integer value units). No floating-point balances anywhere.
- Placed block: a custom block component (`Component<ChunkStore>`) holding the balance, persisted with the chunk.
- Station item: balance stored in ItemStack metadata (BSON) under a namespaced key owned by this plugin (e.g. `TradeStation:Balance`). Prefer a typed codec over raw BSON access.
- Break handler: component → item metadata. Place handler: item metadata → component.

### Config: `tradeables.json`
Single file serving as both whitelist and price list — an item is tradeable **iff** it has an entry.

```json
{
  "spread": 0.25,
  "items": {
    "Example_Item_Id": 2
  }
}
```

- `items`: item ID → **base sell price** (positive integer). Item IDs must be real Hytale item IDs (verify against assets).
- Selling 1 item adds `base` to the balance.
- Buying 1 item costs `ceil(base * (1 + spread))`. Round **up** so the spread is never lost to rounding.
- `spread` is configurable, must be ≥ 0.
- Loop prevention (e.g. craft → sell → buy cycles) is handled by the owner tuning base prices manually; the plugin just needs to make prices easy to adjust. Optional nice-to-have later: a dev command that flags recipes where outputs sell for more than their inputs.
- Validate on load: reject/warn on unknown item IDs, non-positive prices, negative spread, malformed JSON. A bad config must never crash the server; log clearly and fall back to an empty/previous config.
- Ship a sensible default file; the server-side copy is the one admins edit.

### Correctness rules
- All buy/sell logic runs server-side. **Re-check balance and inventory at the moment of the transaction**, never trust what a UI last displayed.
- A transaction is all-or-nothing: never remove items without crediting, never debit without giving items. If the player's inventory can't fit a purchase, don't debit.
- Guard against `long` overflow on balance additions.
- If multiple players view the same station, all open views must refresh after any transaction.
- Keep price math in plain, game-independent Java classes so it's unit-testable without a server.

## Milestones (do them in order, one at a time)

1. **Hello world**: plugin loads on `runServer`, logs a message. Confirm the environment works.
2. **`/sell` command** (dev-only): sells the held stack if whitelisted, logs the value. Exercises the inventory API and config loading. Unit tests for price math and config validation.
3. **Station block**: asset-pack block (reuse an existing bench model at first) that can be placed; interacting with it logs a message.
4. **Balance persistence**: block component with balance; survives world reload. Break → item metadata → place round-trip works. **Also test in this milestone:**
    - every way the block can be removed (player break, explosions, admin/builder tools). If some path can't carry the balance, report it and propose a fallback (e.g. block removal while balance > 0, or log it) — ask before choosing;
    - whether two station items with different balances incorrectly stack together.
5. **UI**: custom UI page opened on interaction: shows balance, sell controls, buy list with prices. `.ui` files go in `src/main/resources/Common/UI/Custom/`. Multi-viewer refresh.
6. **Polish**: config reload command, permissions if needed, localization strings, final model/texture.

Milestones 4's risk checks come before UI work on purpose: they're the parts most likely to force a design change.

## Code conventions

- One responsibility per class: config loading, pricing, station state, interaction handling, UI.
- Log through the plugin logger with a clear prefix; no `System.out`.
- Unit-test anything that doesn't need the server (pricing, config parsing/validation).
- Small, reviewable changes. Explain non-obvious Hytale-specific code with a short comment.
- Don't add third-party dependencies without asking.