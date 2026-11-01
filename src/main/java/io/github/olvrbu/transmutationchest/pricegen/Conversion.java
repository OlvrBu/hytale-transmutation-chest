package io.github.olvrbu.transmutationchest.pricegen;

import java.util.List;
import java.util.Map;

/**
 * A way to turn some items into others: a crafting recipe, swapping a block within its block group at
 * the Builder's Workbench, or placing a block and breaking it (its drop). Plain data, no server types.
 *
 * @param source  human-readable origin, for reports (e.g. "recipe Rock_Stone_Brick_Recipe_1")
 * @param inputs  what is consumed
 * @param outputs item ID to quantity produced
 */
public record Conversion(String source, List<Ingredient> inputs, Map<String, Integer> outputs) {

    public Conversion {
        inputs = List.copyOf(inputs);
        outputs = Map.copyOf(outputs);
    }

    /**
     * One input slot: {@code quantity} units, where each candidate item counts as some number of units.
     * A plain item input has one candidate worth 1 unit; a resource-type input ("any log") has many.
     */
    public record Ingredient(Map<String, Integer> unitsPerItem, int quantity) {
        public Ingredient {
            unitsPerItem = Map.copyOf(unitsPerItem);
        }

        public static Ingredient item(String itemId, int quantity) {
            return new Ingredient(Map.of(itemId, 1), quantity);
        }
    }
}
