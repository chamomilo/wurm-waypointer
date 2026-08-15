package org.waypoints.next.integration;

import com.wurmonline.client.game.CaveDataBuffer;
import com.wurmonline.client.game.DistantTerrainDataBuffer;
import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.client.game.World;
import org.waypoints.next.lootmap.LootMapTerrain;
import org.waypoints.next.model.WaypointLayer;

/** Reads the currently available Wurm terrain buffers for Loot Map placement. */
final class WurmLootMapTerrain implements LootMapTerrain {
    private static final float TILE_SIZE = 4.0f;
    private static final float DRY_EPSILON_METRES = 0.001f;

    private final World world;
    private final WaypointLayer layer;

    WurmLootMapTerrain(World world, WaypointLayer layer) {
        this.world = world;
        this.layer = layer;
    }

    @Override public TileState classify(int tileX, int tileY) {
        if (world == null || tileX < 0 || tileY < 0) return TileState.UNKNOWN;
        try {
            return layer == WaypointLayer.CAVE
                    ? cave(tileX, tileY) : surface(tileX, tileY);
        } catch (RuntimeException terrainRefreshing) {
            return TileState.UNKNOWN;
        }
    }

    private TileState cave(int tileX, int tileY) {
        CaveDataBuffer cave = world.getCaveBuffer();
        float worldX = tileX * TILE_SIZE;
        float worldY = tileY * TILE_SIZE;
        if (cave == null || !cave.isValid(worldX + 2.0f, worldY + 2.0f)
                || cave.getRawFloor(tileX, tileY) == -100
                || cave.getRawFloor(tileX + 1, tileY) == -100
                || cave.getRawFloor(tileX, tileY + 1) == -100
                || cave.getRawFloor(tileX + 1, tileY + 1) == -100) {
            return TileState.UNKNOWN;
        }
        float minimumFloor = minimum(cave.getAdjustedFloor(tileX, tileY),
                cave.getAdjustedFloor(tileX + 1, tileY),
                cave.getAdjustedFloor(tileX, tileY + 1),
                cave.getAdjustedFloor(tileX + 1, tileY + 1));
        float waterLevel = cave.getWaterHeight(tileX, tileY) / 10.0f;
        return classify(minimumFloor, waterLevel);
    }

    private TileState surface(int tileX, int tileY) {
        float worldX = tileX * TILE_SIZE;
        float worldY = tileY * TILE_SIZE;
        NearTerrainDataBuffer near = world.getNearTerrainBuffer();
        if (near != null && near.isValid(worldX + 2.0f, worldY + 2.0f)) {
            float minimumHeight = minimum(near.getHeight(tileX, tileY),
                    near.getHeight(tileX + 1, tileY),
                    near.getHeight(tileX, tileY + 1),
                    near.getHeight(tileX + 1, tileY + 1));
            float waterLevel = near.getWaterHeight(tileX, tileY) / 10.0f;
            return classify(minimumHeight, waterLevel);
        }

        DistantTerrainDataBuffer distant = world.getDistantTerrainBuffer();
        if (distant == null || !distant.isValid(worldX, worldY)
                || !distant.isValid(worldX + TILE_SIZE, worldY)
                || !distant.isValid(worldX, worldY + TILE_SIZE)
                || !distant.isValid(worldX + TILE_SIZE, worldY + TILE_SIZE)) {
            return TileState.UNKNOWN;
        }
        float minimumHeight = minimum(
                distant.getInterpolatedHeight(worldX, worldY),
                distant.getInterpolatedHeight(worldX + TILE_SIZE, worldY),
                distant.getInterpolatedHeight(worldX, worldY + TILE_SIZE),
                distant.getInterpolatedHeight(worldX + TILE_SIZE,
                        worldY + TILE_SIZE));
        return classify(minimumHeight, 0.0f);
    }

    private static TileState classify(float minimumGround, float waterLevel) {
        if (!finite(minimumGround) || !finite(waterLevel)) {
            return TileState.UNKNOWN;
        }
        return minimumGround + DRY_EPSILON_METRES >= waterLevel
                ? TileState.DRY_LAND : TileState.WATER;
    }

    private static float minimum(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
