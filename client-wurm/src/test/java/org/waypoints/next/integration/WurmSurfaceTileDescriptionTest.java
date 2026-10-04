package org.waypoints.next.integration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WurmSurfaceTileDescriptionTest {
    @Test
    public void onlyNearbyCoordinatesMayUseTheWrappedLiveBuffer() {
        assertTrue(WurmSurfaceTileDescription.withinLiveRange(
                3030, 1076, 3158, 1204));
        assertFalse(WurmSurfaceTileDescription.withinLiveRange(
                3030, 1076, 3401, 1981));
        assertFalse(WurmSurfaceTileDescription.withinLiveRange(
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0));
    }

    @Test public void exactWurmTypesRetainTreeSpecies() {
        assertEquals("Pine tree", WurmSurfaceTileDescription.tileName(
                (byte) 101, (byte) 0));
        assertEquals("Grass", WurmSurfaceTileDescription.tileName(
                (byte) 2, (byte) 0));
        assertEquals("Clay", WurmSurfaceTileDescription.tileName(
                (byte) 6, (byte) 0));
    }
}
