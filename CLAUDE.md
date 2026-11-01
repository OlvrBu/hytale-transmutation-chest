# CLAUDE.md — Transmutation Chest (Hytale server plugin)

## What this project is

A Hytale (Early Access, 0.7.x) server plugin + asset pack that adds a **Transmutation Chest**: a placeable, interactable block where players sell and buy whitelisted building-block items. The goal is to reduce grinding for specific building materials and reduce inventory juggling, without going full creative mode.

Name: "Transmutation Chest" (mod ID `transmutationchest`, item `Transmutation_Chest`, command `/transmutationchest` alias `/tchest`). Renamed on 2026-10-21 from "Trade Station" (the block became a magical chest); the rename deliberately broke compatibility with earlier worlds and config folders. Code calls the block a "chest" throughout.

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
- Players sell whitelisted items to a chest → the chest's **balance** increases. Players buy whitelisted items from a chest → its balance decreases.
- **No currency item.** The balance is a number stored on the chest itself.
- **Chests and balances are shared** by all players. No ownership.
- **Breaking a chest keeps its balance in the dropped item.** Placing that item restores the balance. A chest item with no balance data places as a fresh chest with balance 0.
- **Unlocks (decided, milestone 6+):** items must be unlocked per chest by donating base materials (default 500 per unlock). Unlocks are per chest, permanent once complete (even if config amounts change later), pure investment (donated items consumed, no balance credit), and travel with the chest item like the balance. Each price seed is its own unlock except configured groups (currently only "Clay": any of 17 clays). An item needs, for every seed its cheapest recipe chain starts from, that seed's unlock; where a recipe accepts several equally cheap materials ("any hardwood log") any one of their unlocks suffices ("any one of" requirements).

### Data model
- Balance is a **`long`** (integer value units). No floating-point balances anywhere.
- Placed block: a custom block component (`ChestComponent`, `Component<ChunkStore>`, registered as `TransmutationChest_Data`) holding the balance, persisted with the chunk.
- Chest item: balance stored in Hytale's built-in `BlockHolder` item metadata (a serialized holder containing just our `ChestComponent`). Decided in milestone 4 instead of a plugin-owned key: the server restores `BlockHolder` onto the block when the item is placed, so no custom place handler is needed. A chest with balance 0 drops a plain item without metadata.
- Break handler: a `RefSystem` on block-entity removal (any cause: player break, commands, explosions, builder tools) drops the chest item with the balance; the block's own drop is disabled (`Gathering.Breaking.Quantity: 0`). Removal where a chest remains at the position (data replaced) does not drop. Creative-mode breaking also drops the item (accepted: a balance must never be silently lost).
- Obtaining (changed 2026-10-21, owner's choice, thematic): crafted at the Arcanist's Workbench (`Arcanebench`, category `Arcane_Misc` = Misc tab, 3 s) from 15 Azure Log (`Wood_Azure_Trunk`), 10 Marble (`Rock_Marble`), 50 each of Essence of Ice/Life/Fire (`Ingredient_*_Essence`), 1 Voidheart (`Ingredient_Voidheart`).
- Lid animation (decided 2026-10-21): the block behaves like a vanilla two-block chest. `ChestLid` switches the block to its `OpenWindow`/`CloseWindow` states (defined in `Transmutation_Chest.json`, vanilla chest animations) when the first viewer opens / last viewer closes the page, like vanilla `OpenContainerInteraction`; the state switch keeps the block entity, so the balance stays. Chests saved while open are reset to closed on chunk load. A custom model must keep a node named `Lid` for the vanilla animations to work.
- Known: in creative mode, placing doesn't consume the item, so a balance-carrying item can be placed repeatedly (accepted; creative can create anything).

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
- `spread` is configurable, must be ≥ 0. **Default is 0** (decided): trades are fully reversible; the option stays.
- Loop prevention (e.g. craft → sell → buy cycles) is handled by the owner tuning base prices manually; the plugin just needs to make prices easy to adjust. Optional nice-to-have later: a dev command that flags recipes where outputs sell for more than their inputs.
- Validate on load: reject/warn on unknown item IDs, non-positive prices, negative spread, malformed JSON. A bad config must never crash the server; log clearly and fall back to an empty/previous config.
- Ship a sensible default file; the server-side copy is the one admins edit.
- **Price workflow (built in milestone 6):** prices are not hand-written. Hand-set raw-material prices + exclusions live in `price_seeds.json`; `/transmutationchest generateprices` derives every craftable block's price from the server's recipes, block-group swaps (Builder's Workbench cycles blocks 1:1 within a `BlockGroup`) and fixed block drops (price = cheapest way to make it, rounded down, so crafting never gains value), writing `generated/tradeables.generated.json` + a report. Review, copy over `tradeables.json`, `/transmutationchest reload`; `/transmutationchest checkprices` verifies no craft-and-sell loops. Scope (decided): placeable rock/soil/wood blocks and decoration made from them; no ingredients, weapons, plants, metal, cloth; nothing creative-only. Only items craftable from other tradeable blocks alone are listed (decided: nothing needing sticks, fibre, ... — so grass/pathways are out), but prices are computed over all recipes so loops stay impossible. **First launch:** if `tradeables.json` doesn't exist, the plugin generates it from the server's own recipes (incl. other mods) on BootEvent; on failure it copies the bundled default; an existing file is never overwritten.

### Chest page UI
- Tab order (2026-10-24): Buy, Sell, Unlock; the page always opens on Buy (even at a chest with nothing unlocked; its empty message points to the Unlock tab).
- Buy tab (2026-10-24): grid of item squares in groups, chosen with small icon tabs in the title row: Recents (first, the default group), Rocks, Wood, Soil & Clay, Furniture (incl. doors, windows, shelves, signs, beds, deco), Other (fallback). Groups come from the items' creative-library `Categories` (`BuyGroup`). A search covers all groups. Rocks stays one group for now (sub-sections by stone type were considered and postponed). Recents (2026-10-24): per player, the last 50 items they bought at any chest (sells don't count, owner's choice), newest first, shown if tradeable at this chest; in memory only, lost on server restart (`RecentItems`). Title reads "Transmutation Chest > Buy > <group>".
- Sell tab (2026-10-24): a list of rows (Sell 1 / Sell all, Shift+Sell 1 = a stack), not a grid (the grid kept resizing); no groups (players only carry a few sellable stacks).
- Item order (2026-10-24): alphabetical by translated name on Buy (except Recents), Sell and the unlock details view.
- Unlock tab (2026-10-24): no filters (the "only unlocks I have materials for" and "show completed" toggles were removed); all unlocks are listed: ones you have materials for, then ones with progress > 0, then untouched, then completed; alphabetical within each group (`UnlockOrder`).

### Correctness rules
- All buy/sell logic runs server-side. **Re-check balance and inventory at the moment of the transaction**, never trust what a UI last displayed.
- A transaction is all-or-nothing: never remove items without crediting, never debit without giving items. If the player's inventory can't fit a purchase, don't debit.
- Guard against `long` overflow on balance additions.
- If multiple players view the same chest, all open views must refresh after any transaction.
- Keep price math in plain, game-independent Java classes so it's unit-testable without a server.

## Milestones (do them in order, one at a time)

1. **Hello world**: plugin loads on `runServer`, logs a message. Confirm the environment works.
2. **`/sell` command** (dev-only): sells the held stack if whitelisted, logs the value. Exercises the inventory API and config loading. Unit tests for price math and config validation.
3. **Chest block**: asset-pack block (reuse an existing bench model at first) that can be placed; interacting with it logs a message.
4. **Balance persistence**: block component with balance; survives world reload. Break → item metadata → place round-trip works. **Also test in this milestone:**
    - every way the block can be removed (player break, explosions, admin/builder tools). If some path can't carry the balance, report it and propose a fallback (e.g. block removal while balance > 0, or log it) — ask before choosing;
    - whether two chest items with different balances incorrectly stack together.
5. **UI**: custom UI page opened on interaction: shows balance, sell controls, buy list with prices. `.ui` files go in `src/main/resources/Common/UI/Custom/`. Multi-viewer refresh.
6. **Polish**: config reload command, permissions if needed, localization strings, final model/texture.

Milestones 4's risk checks come before UI work on purpose: they're the parts most likely to force a design change.

### Status (as of 2026-10-24): all milestones done, version 1.1.0 (tag `v1.1.0`; changes per version in CHANGELOG.md)

- 1–5 done and confirmed in game. Milestone 4: explosions and builder tools untested (no block-destroying explosive found in 0.7.2; builder tools skipped); removal is handled generically, so they should work. Multi-viewer refresh is implemented but untested with two real players.
- 6 done. Final model (2026-10-21): the owner's chest-style model, texture and icon, made in Blockbench from the vanilla Royal Magic large chest (`Common/Blocks/TransmutationChest/`, `Common/Icons/Items/Transmutation_Chest.png`), with a soft violet block light (`"Light": {"Color": "#425"}`, tuned by the owner). The untracked `blockbench/` folder holds the owner's working files and vanilla reference assets; don't commit it. Permissions: plugin commands are operator-only by default, nothing extra needed. Localization: English only, structured for more languages.
- Beyond the original plan (all owner-requested): price generator + loop checker, recipe, `/transmutationchest` admin commands (reload, generateprices, checkprices, sell/buy/setbalance, unlock/lock, item, unlockdef, spread), the unlock system, Sell/Buy/Unlock tabs in a workbench-style layout with grid, paging and per-tab search, first-launch generation of tradeables.json.
- Still open (owner): final names and meta information (`mod_description`, `mod_url`, possibly the mod name).
- **Unreleased on `main` (2026-11-01), planned as 1.2.0:** package/group rename to `olvrbu` (mod ID `olvrbu:transmutationchest`, data folder `mods/olvrbu_transmutationchest/`, permission nodes `olvrbu.*`), and the move to Hytale **0.7.5** (`hytale_version`/`server_version`, manifest `>=0.7.5 <0.8.0`; owner, 2026-11-01). Reason for the move: Hytale removed 0.7.2 from its Maven repo, so 0.7.2 builds fail on any machine without a cached jar (GitHub CI included). Compiles and passes all unit tests against 0.7.5; **not yet tested in game on 0.7.5** — before tagging 1.2.0 the owner checks open/buy/sell/unlock and `/transmutationchest generateprices` (vanilla recipes may have changed). Changelog entry is under `## Unreleased`; rename it to `## 1.2.0 (<date>)` when releasing.

**Releasing (since 2026-11-01):** bump `version`, `./gradlew updatePluginManifest`, give the `CHANGELOG.md` section its version heading (`## <version> (<date>)`), commit, tag `v<version>`, push the tag. `.github/workflows/release.yml` (same file as Builder Helper) builds the jar from the tag on GitHub and publishes a GitHub Release with it, notes from that changelog section. v1.0.0 and v1.1.0 were released with jars built in the sandbox and uploaded with `gh release create`, because they target 0.7.2, which GitHub can't download anymore.

## Code conventions

- One responsibility per class: config loading, pricing, chest state, interaction handling, UI.
- Log through the plugin logger with a clear prefix; no `System.out`.
- Unit-test anything that doesn't need the server (pricing, config parsing/validation).
- Small, reviewable changes. Explain non-obvious Hytale-specific code with a short comment.
- Don't add third-party dependencies without asking.