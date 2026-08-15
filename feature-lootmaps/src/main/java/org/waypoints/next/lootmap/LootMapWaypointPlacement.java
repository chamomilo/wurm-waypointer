package org.waypoints.next.lootmap;

import org.waypoints.next.source.MapBounds;

import java.util.HashMap;
import java.util.Map;

/** Moves an intermediate reading point off known water while preserving the clue. */
final class LootMapWaypointPlacement {
    private static final int LOCAL_SEARCH_RADIUS_TILES = 64;

    private LootMapWaypointPlacement() { }

    static LootMapDecision adjust(LootMapDecision decision,
                                  LootMapObservation observation,
                                  MapBounds bounds,
                                  LootMapTerrain terrain) {
        if (decision == null || observation == null || bounds == null
                || terrain == null || observation.getBand().isFinalPoint()) {
            return decision;
        }
        int targetX = clamp((int) Math.round(decision.getWaypointX()),
                bounds.getWidth());
        int targetY = clamp((int) Math.round(decision.getWaypointY()),
                bounds.getHeight());
        Search search = new Search(terrain, bounds, observation);
        if (search.state(targetX, targetY) != LootMapTerrain.TileState.WATER) {
            return decision;
        }

        Tile dry = search.nearestLocalLand(targetX, targetY);
        if (dry == null) {
            dry = search.firstLandTowardObservation(targetX, targetY);
        }
        if (dry == null) return decision;
        return decision.relocatedTo(dry.x, dry.y,
                Math.hypot(dry.x - observation.getOriginX(),
                        dry.y - observation.getOriginY()));
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static final class Search {
        private final LootMapTerrain terrain;
        private final MapBounds bounds;
        private final LootMapObservation observation;
        private final Map<Long, LootMapTerrain.TileState> states =
                new HashMap<Long, LootMapTerrain.TileState>();

        private Search(LootMapTerrain terrain, MapBounds bounds,
                       LootMapObservation observation) {
            this.terrain = terrain;
            this.bounds = bounds;
            this.observation = observation;
        }

        private Tile nearestLocalLand(int targetX, int targetY) {
            int minimumX = Math.max(0, targetX - LOCAL_SEARCH_RADIUS_TILES);
            int maximumX = Math.min(bounds.getWidth() - 1,
                    targetX + LOCAL_SEARCH_RADIUS_TILES);
            int minimumY = Math.max(0, targetY - LOCAL_SEARCH_RADIUS_TILES);
            int maximumY = Math.min(bounds.getHeight() - 1,
                    targetY + LOCAL_SEARCH_RADIUS_TILES);
            Tile best = null;
            long bestDistanceSquared = Long.MAX_VALUE;
            double bestWalkSquared = Double.POSITIVE_INFINITY;
            for (int y = minimumY; y <= maximumY; y++) {
                for (int x = minimumX; x <= maximumX; x++) {
                    if (state(x, y) != LootMapTerrain.TileState.DRY_LAND) continue;
                    long dx = x - targetX;
                    long dy = y - targetY;
                    long distanceSquared = dx * dx + dy * dy;
                    double walkSquared = squared(x - observation.getOriginX())
                            + squared(y - observation.getOriginY());
                    if (best == null || distanceSquared < bestDistanceSquared
                            || (distanceSquared == bestDistanceSquared
                            && walkSquared < bestWalkSquared)
                            || (distanceSquared == bestDistanceSquared
                            && walkSquared == bestWalkSquared
                            && (y < best.y || (y == best.y && x < best.x)))) {
                        best = new Tile(x, y);
                        bestDistanceSquared = distanceSquared;
                        bestWalkSquared = walkSquared;
                    }
                }
            }
            return best;
        }

        private Tile firstLandTowardObservation(int targetX, int targetY) {
            int originX = clamp((int) Math.round(observation.getOriginX()),
                    bounds.getWidth());
            int originY = clamp((int) Math.round(observation.getOriginY()),
                    bounds.getHeight());
            int steps = Math.max(Math.abs(originX - targetX),
                    Math.abs(originY - targetY));
            if (steps == 0) return null;
            int previousX = targetX;
            int previousY = targetY;
            for (int step = 1; step <= steps; step++) {
                double fraction = (double) step / steps;
                int x = (int) Math.round(targetX + (originX - targetX) * fraction);
                int y = (int) Math.round(targetY + (originY - targetY) * fraction);
                if (x == previousX && y == previousY) continue;
                previousX = x;
                previousY = y;
                if (state(x, y) == LootMapTerrain.TileState.DRY_LAND) {
                    return new Tile(x, y);
                }
            }
            return null;
        }

        private LootMapTerrain.TileState state(int x, int y) {
            long key = ((long) x << 32) ^ (y & 0xffffffffL);
            LootMapTerrain.TileState cached = states.get(Long.valueOf(key));
            if (cached != null) return cached;
            LootMapTerrain.TileState sampled;
            try { sampled = terrain.classify(x, y); }
            catch (RuntimeException unavailable) {
                sampled = LootMapTerrain.TileState.UNKNOWN;
            }
            if (sampled == null) sampled = LootMapTerrain.TileState.UNKNOWN;
            states.put(Long.valueOf(key), sampled);
            return sampled;
        }

        private static double squared(double value) { return value * value; }
    }

    private static final class Tile {
        private final int x;
        private final int y;
        private Tile(int x, int y) { this.x = x; this.y = y; }
    }
}
