package org.waypoints.next.archaeology;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArchaeologyTileCoordinatesTest {
    @Test public void convertsWurmTileIndexToExactTileCentre() {
        assertEquals(3068.5d, ArchaeologyTileCoordinates.centerOf(3068), 0.0d);
        assertEquals(961.5d, ArchaeologyTileCoordinates.centerOf(961), 0.0d);
    }

    @Test public void migratesV1IndexesAndClampsAtCentredMapEdges() {
        assertEquals(3072.5d,
                ArchaeologyTileCoordinates.migrateV1(3072.0d), 0.0d);
        assertEquals(0.5d,
                ArchaeologyTileCoordinates.clampToMapCentre(-20.0d, 4096), 0.0d);
        assertEquals(4095.5d,
                ArchaeologyTileCoordinates.clampToMapCentre(5000.0d, 4096), 0.0d);
    }
}
