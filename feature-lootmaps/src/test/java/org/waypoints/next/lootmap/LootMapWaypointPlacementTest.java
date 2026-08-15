package org.waypoints.next.lootmap;

import org.junit.Test;
import org.waypoints.next.source.MapBounds;

import java.time.Instant;
import java.util.Collections;

import static org.junit.Assert.*;

public class LootMapWaypointPlacementTest {
    private static final Instant NOW = Instant.parse("2026-08-14T23:00:00Z");
    private static final MapBounds BOUNDS = new MapBounds(4096, 4096);

    @Test public void knownWaterMovesToNearestDryTile() {
        LootMapObservation observation = observation(100, 100,
                LootMapDistanceBand.TEN_TO_NINETEEN);
        LootMapDecision decision = new LootMapPlanner().plan(
                Collections.singletonList(observation), BOUNDS);

        LootMapDecision adjusted = LootMapWaypointPlacement.adjust(decision,
                observation, BOUNDS, terrainWithDryTiles(99, 112, 101, 112));

        assertTrue(adjusted.isLandAdjusted());
        assertEquals(99.0d, adjusted.getWaypointX(), 0.0d);
        assertEquals(112.0d, adjusted.getWaypointY(), 0.0d);
        assertEquals(100.0d, adjusted.getPlannedWaypointX(), 0.0d);
        assertEquals(112.0d, adjusted.getPlannedWaypointY(), 0.0d);
    }

    @Test public void largeWaterCrossingFallsBackTowardTheReadingPosition() {
        LootMapObservation observation = observation(3062, 660,
                LootMapDistanceBand.TWO_HUNDRED_TO_FOUR_NINETY_NINE);
        LootMapDecision decision = new LootMapPlanner().plan(
                Collections.singletonList(observation), BOUNDS);
        LootMapTerrain coast = new LootMapTerrain() {
            @Override public TileState classify(int x, int y) {
                return y <= 667 ? TileState.DRY_LAND : TileState.WATER;
            }
        };

        LootMapDecision adjusted = LootMapWaypointPlacement.adjust(decision,
                observation, BOUNDS, coast);

        assertEquals(3062.0d, decision.getWaypointX(), 0.0d);
        assertEquals(900.0d, decision.getWaypointY(), 0.0d);
        assertEquals(3062.0d, adjusted.getWaypointX(), 0.0d);
        assertEquals(667.0d, adjusted.getWaypointY(), 0.0d);
        assertTrue(adjusted.isLandAdjusted());
        assertEquals(7.0d, adjusted.getWalkTiles(), 0.0d);
    }

    @Test public void unknownTerrainLeavesThePlannerDecisionUntouched() {
        LootMapObservation observation = observation(100, 100,
                LootMapDistanceBand.TEN_TO_NINETEEN);
        LootMapDecision decision = new LootMapPlanner().plan(
                Collections.singletonList(observation), BOUNDS);
        LootMapTerrain unknown = new LootMapTerrain() {
            @Override public TileState classify(int x, int y) {
                return TileState.UNKNOWN;
            }
        };

        assertSame(decision, LootMapWaypointPlacement.adjust(decision,
                observation, BOUNDS, unknown));
    }

    @Test public void finalLootLocationIsNeverMoved() {
        LootMapObservation observation = observation(100, 100,
                LootMapDistanceBand.ONE_TO_THREE);
        LootMapDecision decision = new LootMapPlanner().plan(
                Collections.singletonList(observation), BOUNDS);
        LootMapTerrain water = new LootMapTerrain() {
            @Override public TileState classify(int x, int y) {
                return TileState.WATER;
            }
        };

        assertSame(decision, LootMapWaypointPlacement.adjust(decision,
                observation, BOUNDS, water));
    }

    private static LootMapTerrain terrainWithDryTiles(final int firstX,
                                                       final int firstY,
                                                       final int secondX,
                                                       final int secondY) {
        return new LootMapTerrain() {
            @Override public TileState classify(int x, int y) {
                if ((x == firstX && y == firstY)
                        || (x == secondX && y == secondY)) {
                    return TileState.DRY_LAND;
                }
                return TileState.WATER;
            }
        };
    }

    private static LootMapObservation observation(double x, double y,
                                                   LootMapDistanceBand band) {
        return new LootMapObservation(x, y, 180.0d,
                LootMapRelativeDirection.AHEAD, band, NOW);
    }
}
