package io.github.obut.tradestation.pricegen;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.BlockGroup;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the server's loaded assets into plain {@link Conversion}s, using the same matching rules as
 * Hytale's crafting code (item ID / resource type with per-item units / tag, minus exclusions).
 * Includes recipes from all mods, block-group swaps and fixed block drops. Random drop lists are
 * ignored: they can't be priced as a fixed conversion.
 */
public final class AssetConversions {

    /** What the generator needs to know about an item besides its conversions. */
    public record ItemInfo(String id, boolean placeable, List<String> categories) {
    }

    public record Graph(List<Conversion> conversions, Map<String, ItemInfo> items) {
    }

    private AssetConversions() {
    }

    public static Graph build() {
        Collection<Item> allItems = Item.getAssetMap().getAssetMap().values();
        Map<String, ItemInfo> items = new LinkedHashMap<>();
        for (Item item : allItems) {
            String[] categories = item.getCategories();
            items.put(item.getId(), new ItemInfo(item.getId(), item.hasBlockType(),
                    categories == null ? List.of() : List.of(categories)));
        }

        List<Conversion> conversions = new ArrayList<>();
        Map<String, Map<String, Integer>> resourceCache = new HashMap<>();
        Map<Integer, Map<String, Integer>> tagCache = new HashMap<>();

        // 1. Crafting recipes (including those declared inline in item files).
        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            MaterialQuantity[] inputs = recipe.getInput();
            MaterialQuantity[] outputs = recipe.getOutputs();
            if (inputs == null || inputs.length == 0 || outputs == null) {
                continue;
            }
            List<Conversion.Ingredient> ingredients = new ArrayList<>();
            for (MaterialQuantity input : inputs) {
                Map<String, Integer> candidates = new HashMap<>(candidates(input, allItems, resourceCache, tagCache));
                candidates.keySet().removeIf(input::isItemExcluded);
                ingredients.add(new Conversion.Ingredient(candidates, input.getQuantity()));
            }
            Map<String, Integer> produced = new HashMap<>();
            for (MaterialQuantity output : outputs) {
                if (output.getItemId() != null && output.getQuantity() > 0) {
                    produced.merge(output.getItemId(), output.getQuantity(), Integer::sum);
                }
            }
            if (!produced.isEmpty()) {
                conversions.add(new Conversion("recipe " + recipe.getId(), ingredients, produced));
            }
        }

        // 2. Block groups: the Builder's Workbench swaps a stack to the next/previous member for free.
        @SuppressWarnings("unchecked")
        var groups = ((DefaultAssetMap<String, BlockGroup>) AssetRegistry.getAssetStore(BlockGroup.class).getAssetMap())
                .getAssetMap().values();
        for (BlockGroup group : groups) {
            for (int i = 0; i < group.size(); i++) {
                String from = group.get(i);
                String to = group.get((i + 1) % group.size());
                if (group.size() > 1 && items.containsKey(from) && items.containsKey(to)) {
                    // A ring of one-way swaps makes every member reachable from every other.
                    conversions.add(new Conversion("block group " + group.getId(),
                            List.of(Conversion.Ingredient.item(from, 1)), Map.of(to, 1)));
                }
            }
        }

        // 3. Fixed drops: place a block, break it, get a (possibly different) item.
        for (Item item : allItems) {
            if (!item.hasBlockType()) {
                continue;
            }
            BlockType block = BlockType.getAssetMap().getAsset(item.getBlockId());
            BlockGathering gathering = block == null ? null : block.getGathering();
            if (gathering == null) {
                continue;
            }
            BlockBreakingDropType breaking = gathering.getBreaking();
            if (breaking != null && breaking.getItemId() != null && breaking.getQuantity() > 0) {
                addDrop(conversions, items, item.getId(), breaking.getItemId(), breaking.getQuantity(), "breaking");
            }
            if (gathering.getSoft() != null && gathering.getSoft().getItemId() != null) {
                addDrop(conversions, items, item.getId(), gathering.getSoft().getItemId(), 1, "soft break");
            }
            if (gathering.getPhysics() != null && gathering.getPhysics().getItemId() != null) {
                addDrop(conversions, items, item.getId(), gathering.getPhysics().getItemId(), 1, "physics");
            }
        }
        return new Graph(conversions, items);
    }

    private static void addDrop(List<Conversion> conversions, Map<String, ItemInfo> items, String block,
                                String drop, int quantity, String kind) {
        if (!drop.equals(block) && items.containsKey(drop)) {
            conversions.add(new Conversion("drop (" + kind + ") of " + block,
                    List.of(Conversion.Ingredient.item(block, 1)), Map.of(drop, quantity)));
        }
    }

    /** Items that satisfy a recipe input, with how many units each counts as. */
    private static Map<String, Integer> candidates(MaterialQuantity input, Collection<Item> allItems,
                                                   Map<String, Map<String, Integer>> resourceCache,
                                                   Map<Integer, Map<String, Integer>> tagCache) {
        if (input.getItemId() != null) {
            return Map.of(input.getItemId(), 1);
        }
        if (input.getTagIndex() != Integer.MIN_VALUE) {
            return tagCache.computeIfAbsent(input.getTagIndex(), tag -> {
                Map<String, Integer> matches = new HashMap<>();
                for (Item item : allItems) {
                    AssetExtraInfo.Data data = item.getData();
                    if (data != null && data.getExpandedTagIndexes().contains(tag.intValue())) {
                        matches.put(item.getId(), 1);
                    }
                }
                return matches;
            });
        }
        if (input.getResourceTypeId() != null) {
            return resourceCache.computeIfAbsent(input.getResourceTypeId(), resource -> {
                Map<String, Integer> matches = new HashMap<>();
                for (Item item : allItems) {
                    ItemResourceType match = ItemContainer.getMatchingResourceType(item, resource);
                    if (match != null && match.quantity > 0) {
                        matches.put(item.getId(), match.quantity);
                    }
                }
                return matches;
            });
        }
        return Map.of();
    }
}
