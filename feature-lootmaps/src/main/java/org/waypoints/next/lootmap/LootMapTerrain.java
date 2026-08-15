package org.waypoints.next.lootmap;

/** Minimal terrain view used to keep intermediate Loot Map readings on land. */
public interface LootMapTerrain {
    enum TileState { DRY_LAND, WATER, UNKNOWN }

    /** Classifies one server tile without assuming that distant terrain is loaded. */
    TileState classify(int tileX, int tileY);
}
