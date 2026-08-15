package org.waypoints.next.navigation;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public final class WaypointLabelCollisionLayoutTest {
    @Test public void nonOverlappingLabelsShareTheTopRow() {
        int[] output = new int[3];
        WaypointLabelCollisionLayout.stack(
                new int[]{10, 80, 160}, new int[]{40, 40, 40},
                new int[]{20, 20, 20}, 3, 14, 2, 2, output);
        assertArrayEquals(new int[]{14, 14, 14}, output);
    }

    @Test public void coincidentLabelsStackInPriorityOrder() {
        int[] output = new int[3];
        WaypointLabelCollisionLayout.stack(
                new int[]{100, 100, 100}, new int[]{80, 60, 90},
                new int[]{18, 18, 18}, 3, 14, 2, 2, output);
        assertArrayEquals(new int[]{14, 34, 54}, output);
    }

    @Test public void horizontalCollisionChainsContinueBelowOccupiedRows() {
        int[] output = new int[3];
        WaypointLabelCollisionLayout.stack(
                new int[]{0, 45, 90}, new int[]{50, 50, 50},
                new int[]{10, 10, 10}, 3, 14, 2, 3, output);
        assertArrayEquals(new int[]{14, 27, 40}, output);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveLabelDimensions() {
        WaypointLabelCollisionLayout.stack(
                new int[]{0}, new int[]{0}, new int[]{10},
                1, 14, 2, 2, new int[1]);
    }
}
