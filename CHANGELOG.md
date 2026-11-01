# Changelog

## Unreleased

- Requires Hytale **0.7.5** or newer (was 0.7.2).
- The mod's ID changed from `obut:transmutationchest` to `olvrbu:transmutationchest` (new author
  name). **When updating:** delete the old `transmutationchest-1.1.0.jar` (or older) from `mods/`,
  rename the data folder `mods/obut_transmutationchest/` to `mods/olvrbu_transmutationchest/`
  (it holds `tradeables.json`, `price_seeds.json` and your generated prices), and change any
  `obut.transmutationchest...` permission entries in `permissions.json` to `olvrbu.transmutationchest...`.
- No gameplay changes.

## 1.1.0 (2026-10-24)

- **Buy** is now the first tab and always opens first (order: Buy, Sell, Unlock).
- **Buy tab groups:** Recents, Rocks, Wood, Soil & Clay, Furniture and Other, picked with small icons
  in the title bar. Groups follow the items' creative-menu categories, so other mods' items are
  sorted in too. Searching covers all groups.
- **Recents** (the default group): the last 50 items you bought at any chest, newest first. Kept
  until the server restarts.
- **Sell tab** is a list again (Sell 1 / Sell all, Shift+Sell 1 sells a stack) instead of a grid.
- **Unlock tab:** no more filters. Unlocks you have materials for come first, then ones with
  progress, then untouched ones, and completed ones last.
- Items are sorted alphabetically; the item grid stays left-aligned when a row isn't full.

## 1.0.0 (2026-10-21)

First release.

- **Transmutation Chest** block, crafted at the Arcanist's Workbench: sell building blocks to it
  for a balance stored on the chest, buy blocks back from that balance. Chests are shared by all
  players, and breaking one keeps its balance in the dropped item.
- **Unlocks:** each chest unlocks items by donating base materials (500 per unlock by default).
- **Prices** generated from the server's recipes so craft-and-sell loops are impossible;
  `tradeables.json` is created on first launch and can be edited and reloaded.
- Trading screen with Sell, Buy and Unlock tabs, search and paging; all open screens of a chest
  update after every trade.
- Admin commands under `/transmutationchest` (alias `/tchest`): reload, price generation and
  checks, balance, unlock and item editing.
