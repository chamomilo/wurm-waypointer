package org.waypoints.next.render;

import com.wurmonline.client.game.CaveDataBuffer;
import com.wurmonline.client.game.DistantTerrainDataBuffer;
import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.client.game.World;

/** Resolves an omitted waypoint Z from the terrain at the target coordinate. */
public final class WaypointGroundHeight {
    private static final float TILE_SIZE = 4.0f;
    private static final float INVALID_TERRAIN_HEIGHT = -3000.0f;

    private WaypointGroundHeight() { }

    public static float resolve(World world, float worldX, float worldY,
                                int targetLayer, float fallbackHeight) {
        float fallback = usableHeight(fallbackHeight) ? fallbackHeight : 0.0f;
        if (world == null || !finite(worldX) || !finite(worldY)) return fallback;
        try {
            if (targetLayer < 0) {
                CaveDataBuffer cave = world.getCaveBuffer();
                if (cave == null || !cave.isValid(worldX, worldY)
                        || !loadedCaveFloor(cave, worldX, worldY)) return fallback;
                return usableOrFallback(
                        cave.getInterpolatedFloor(worldX, worldY), fallback);
            }
            NearTerrainDataBuffer near = world.getNearTerrainBuffer();
            if (near != null && near.isValid(worldX, worldY)) {
                float height = near.getInterpolatedHeight(worldX, worldY);
                if (usableHeight(height)) return height;
            }
            DistantTerrainDataBuffer distant = world.getDistantTerrainBuffer();
            if (distant != null && distant.isValid(worldX, worldY)) {
                return usableOrFallback(
                        distant.getInterpolatedHeight(worldX, worldY), fallback);
            }
        } catch (RuntimeException unavailableDuringTerrainRefresh) {
            // Terrain buffers are replaced while a world is loading. The
            // render path retries next frame instead of losing the marker.
        }
        return fallback;
    }

    static float usableOrFallback(float candidate, float fallback) {
        return usableHeight(candidate) ? candidate
                : (usableHeight(fallback) ? fallback : 0.0f);
    }

    static boolean usableHeight(float value) {
        return finite(value) && value > INVALID_TERRAIN_HEIGHT;
    }

    private static boolean loadedCaveFloor(CaveDataBuffer cave,
                                            float worldX, float worldY) {
        int tileX = (int) Math.floor(worldX / TILE_SIZE);
        int tileY = (int) Math.floor(worldY / TILE_SIZE);
        return cave.getRawFloor(tileX, tileY) != -100
                && cave.getRawFloor(tileX + 1, tileY) != -100
                && cave.getRawFloor(tileX, tileY + 1) != -100
                && cave.getRawFloor(tileX + 1, tileY + 1) != -100;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
