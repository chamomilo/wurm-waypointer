package org.waypoints.next.integration;

import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.client.game.World;
import com.wurmonline.mesh.Tiles;

/** Exact surface description for tiles currently present in the live client buffer. */
final class WurmSurfaceTileDescription {
    private static final float TILE_SIZE = 4.0f;
    private static final float DRY_EPSILON_METRES = 0.001f;

    private WurmSurfaceTileDescription() { }

    static String describe(World world, int tileX, int tileY) {
        if (world == null || tileX < 0 || tileY < 0) return "";
        try {
            NearTerrainDataBuffer near = world.getNearTerrainBuffer();
            float worldX = tileX * TILE_SIZE;
            float worldY = tileY * TILE_SIZE;
            if (near == null || !near.isValid(worldX + 2.0f, worldY + 2.0f)) {
                return "";
            }
            String tile = tileName(near.getRawType(tileX, tileY),
                    near.getData(tileX, tileY));
            float minimumGround = minimum(near.getHeight(tileX, tileY),
                    near.getHeight(tileX + 1, tileY),
                    near.getHeight(tileX, tileY + 1),
                    near.getHeight(tileX + 1, tileY + 1));
            float waterLevel = near.getWaterHeight(tileX, tileY) / 10.0f;
            if (finite(minimumGround) && finite(waterLevel)
                    && minimumGround + DRY_EPSILON_METRES < waterLevel) {
                return tile.isEmpty() ? "Water" : "Water (" + tile + ")";
            }
            return tile;
        } catch (RuntimeException terrainRefreshing) {
            return "";
        }
    }

    static String tileName(byte type, byte data) {
        Tiles.Tile tile = Tiles.getTile(type);
        if (tile == null) return "";
        String name = tile.getTileName(data);
        if (name == null || name.trim().isEmpty()) name = tile.getName();
        return name == null ? "" : name.trim();
    }

    private static float minimum(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
