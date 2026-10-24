package io.github.obut.transmutationchest.ui;

/**
 * Groups of the Buy tab, derived from an item's creative-library categories ("Blocks.Rocks",
 * "Furniture.Doors", ...; the {@code Categories} key of item JSON), so other mods' items are grouped too.
 * Plain Java, so it's unit-testable. {@code id} matches the TabButton Ids of #Groups in ChestPage.ui.
 */
enum BuyGroup {
    /** Not a category: the player's recently bought items ({@link RecentItems}). The default group. */
    RECENTS("Recents", "ui.groupRecents"),
    ROCKS("Rocks", "ui.groupRocks"),
    WOOD("Wood", "ui.groupWood"),
    SOIL("Soil", "ui.groupSoil"),
    FURNITURE("Furniture", "ui.groupFurniture"),
    OTHER("Other", "ui.groupOther");

    final String id;
    final String titleKey;

    BuyGroup(String id, String titleKey) {
        this.id = id;
        this.titleKey = titleKey;
    }

    static BuyGroup byId(String id) {
        for (BuyGroup group : values()) {
            if (group.id.equals(id)) {
                return group;
            }
        }
        return null;
    }

    /** The group of the first category we recognise; OTHER if none (or no categories at all). */
    static BuyGroup of(String[] categories) {
        if (categories != null) {
            for (String category : categories) {
                BuyGroup group = ofCategory(category);
                if (group != null) {
                    return group;
                }
            }
        }
        return OTHER;
    }

    private static BuyGroup ofCategory(String category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case "Blocks.Rocks" -> ROCKS;
            case "Blocks.Wood" -> WOOD;
            // "Blocks.Soil" (singular) is used by one vanilla item, probably a typo there.
            case "Blocks.Soils", "Blocks.Soil" -> SOIL;
            case "Blocks.Deco" -> FURNITURE;
            default -> category.startsWith("Furniture.") ? FURNITURE : null;
        };
    }
}
