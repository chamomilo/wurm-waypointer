package org.waypoints.next.integration;

import com.wurmonline.client.game.DistantTerrainDataBuffer;
import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.client.game.World;
import java.util.Arrays;

/** Received surface corner heights, with unknown terrain left as NaN. */
final class WurmTopographicTerrain {
    private WurmTopographicTerrain() { }

    static float[] surfaceHeights(World world, int originX, int originY, int columns, int rows) {
        float[] heights = new float[columns * rows];
        Arrays.fill(heights, Float.NaN);
        if (world == null) return heights;
        NearTerrainDataBuffer near = world.getNearTerrainBuffer();
        DistantTerrainDataBuffer distant = world.getDistantTerrainBuffer();
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
            int tileX = originX + x, tileY = originY + y;
            if (tileX < 0 || tileY < 0 || tileX >= world.getWorldSize()
                    || tileY >= world.getWorldSize()) continue;
            float worldX = tileX * 4.0f, worldY = tileY * 4.0f;
            float height = Float.NaN;
            try {
                if (near != null && WurmSurfaceTileDescription.withinLiveRange(
                        world.getPlayerCurrentTileX(), world.getPlayerCurrentTileY(), tileX, tileY)
                        && near.isValid(worldX, worldY)) height = near.getHeight(tileX, tileY);
                if (!finite(height) && distant != null && distant.isValid(worldX, worldY))
                    height = distant.getInterpolatedHeight(worldX, worldY);
            } catch (RuntimeException refreshing) { /* Leave this corner unknown. */ }
            if (finite(height)) heights[x + y * columns] = height;
        }
        return heights;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
