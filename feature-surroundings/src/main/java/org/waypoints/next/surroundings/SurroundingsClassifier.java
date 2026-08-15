package org.waypoints.next.surroundings;

import java.util.Locale;

/** Conservative semantic classifier for the metadata exposed by the client. */
public final class SurroundingsClassifier {
    public static final String ANIMALS = "Animals";
    public static final String CHESTS = "Chests";
    public static final String CRATES = "Crates";
    public static final String BARRELS = "Barrels";
    public static final String BULK_STORAGE = "Bulk storage";
    public static final String VEHICLES = "Vehicles";
    public static final String SHIPS = "Ships";
    public static final String PORTABLE_CONTAINERS = "Portable containers";
    public static final String OTHER_CONTAINERS = "Other containers";
    public static final String MUSHROOMS = "Mushrooms";
    public static final String CORPSES = "Corpses";
    public static final String RESOURCES = "Resources";
    public static final String TOOLS = "Tools";
    public static final String FOOD = "Food";
    public static final String DECORATIONS = "Decorations";
    public static final String OTHER_ITEMS = "Other items";

    private SurroundingsClassifier() { }

    public static boolean isContainer(String name, String modelName) {
        String text = searchable(name, modelName);
        return hasAny(text,
                " container ", " chest ", " crate ", " barrel ", " storage bin ",
                " bulk storage ", " food storage ", " cupboard ", " wardrobe ",
                " coffin ", " rack ", " shelf ", " cart ", " wagon ",
                " boat ", " ship ", " knarr ", " caravel ", " corbita ",
                " cog ", " raft ", " backpack ", " satchel ", " quiver ",
                " saddlebag ", " amphora ", " lunchbox ", " inventory ");
    }

    public static String containerCategory(String name, String modelName) {
        String text = searchable(name, modelName);
        if (hasAny(text, " chest ", " coffin ")) return CHESTS;
        if (hasAny(text, " crate ")) return CRATES;
        if (hasAny(text, " barrel ", " amphora ")) return BARRELS;
        if (hasAny(text, " bulk storage ", " food storage ", " storage bin ")) {
            return BULK_STORAGE;
        }
        if (hasAny(text, " knarr ", " caravel ", " corbita ", " cog ",
                " sailboat ", " rowboat ", " boat ", " ship ", " raft ")) {
            return SHIPS;
        }
        if (hasAny(text, " cart ", " wagon ", " vehicle ")) return VEHICLES;
        if (hasAny(text, " backpack ", " satchel ", " quiver ", " saddlebag ",
                " lunchbox ")) return PORTABLE_CONTAINERS;
        return OTHER_CONTAINERS;
    }

    public static String itemCategory(String name, String modelName) {
        String text = searchable(name, modelName);
        if (hasAny(text, " mushroom ", " fungus ")) return MUSHROOMS;
        if (hasAny(text, " corpse ", " gravestone ")) return CORPSES;
        if (hasAny(text, " log ", " plank ", " shaft ", " ore ", " lump ",
                " shard ", " rock ", " clay ", " peat ", " tar ", " sand ",
                " sprout ", " seedling ", " hide ", " pelt ", " fibre ",
                " fiber ", " cotton ", " wemp ")) return RESOURCES;
        if (hasAny(text, " tool ", " pickaxe ", " shovel ", " hatchet ",
                " hammer ", " saw ", " chisel ", " knife ", " rake ",
                " scythe ", " sickle ", " brush ", " whetstone ")) return TOOLS;
        if (hasAny(text, " food ", " meal ", " meat ", " fish ", " bread ",
                " cheese ", " fruit ", " berry ", " vegetable ", " herb ",
                " egg ", " milk ")) return FOOD;
        if (hasAny(text, " decoration ", " statue ", " lamp ", " candle ",
                " tapestry ", " painting ", " rug ", " planter ", " trellis ")) {
            return DECORATIONS;
        }
        return OTHER_ITEMS;
    }

    /**
     * The pinned client exposes no isUnique/template flag. These exact creature
     * families are the vanilla unique templates and are stable in model/name data.
     */
    public static boolean isUniqueCreature(String name, String modelName) {
        String text = searchable(name, modelName);
        return hasAny(text, " dragon ", " drake ", " forest giant ",
                " goblin leader ", " kyklops ", " troll king ");
    }

    private static String searchable(String first, String second) {
        return " " + normalize(first) + " " + normalize(second) + " ";
    }

    static String normalize(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ENGLISH);
        return clean.replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean hasAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }
}
