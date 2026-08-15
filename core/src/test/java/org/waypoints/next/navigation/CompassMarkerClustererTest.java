package org.waypoints.next.navigation;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class CompassMarkerClustererTest {
    @Test public void coincidentAndNearbyMarkersShareDeterministicGroups() {
        int[] x = {10, 10, 40, 47, 100};
        int[] y = {20, 20, 40, 40, 100};
        int[] groups = new int[x.length];
        int[] parents = new int[x.length];

        int count = CompassMarkerClusterer.cluster(
                x, y, x.length, 8, groups, parents);

        assertEquals(3, count);
        assertArrayEquals(new int[]{0, 0, 1, 1, 2}, groups);
    }

    @Test public void closeChainsDoNotCollapseAWholeBearingRing() {
        int[] x = {0, 8, 16, 40};
        int[] y = {0, 0, 0, 0};
        int[] groups = new int[x.length];
        int[] parents = new int[x.length];

        assertEquals(3, CompassMarkerClusterer.cluster(
                x, y, x.length, 8, groups, parents));
        assertArrayEquals(new int[]{0, 0, 1, 2}, groups);
    }

    @Test public void thresholdIsInclusiveAndZeroClustersExactPixelsOnly() {
        int[] x = {3, 3, 4};
        int[] y = {7, 7, 7};
        int[] groups = new int[x.length];
        int[] parents = new int[x.length];

        assertEquals(2, CompassMarkerClusterer.cluster(
                x, y, x.length, 0, groups, parents));
        assertArrayEquals(new int[]{0, 0, 1}, groups);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInsufficientScratchSpace() {
        CompassMarkerClusterer.cluster(new int[2], new int[2], 2, 8,
                new int[1], new int[2]);
    }
}
