package org.waypoints.next.navigation;

/** Allocation-free vertical stacking for horizontally overlapping HUD labels. */
public final class WaypointLabelCollisionLayout {
    private WaypointLabelCollisionLayout() {
    }

    /**
     * Places labels in input priority order. Every label starts at {@code top}
     * and moves below all earlier labels whose horizontal ranges overlap.
     */
    public static void stack(int[] left, int[] widths, int[] heights, int count,
                             int top, int horizontalGap, int verticalGap,
                             int[] outputTop) {
        if (left == null || widths == null || heights == null || outputTop == null) {
            throw new IllegalArgumentException("label layout arrays are required");
        }
        if (count < 0 || count > left.length || count > widths.length
                || count > heights.length || count > outputTop.length) {
            throw new IllegalArgumentException("label count exceeds array capacity");
        }
        if (top < 0 || horizontalGap < 0 || verticalGap < 0) {
            throw new IllegalArgumentException("label layout offsets must not be negative");
        }
        for (int i = 0; i < count; i++) {
            if (widths[i] < 1 || heights[i] < 1) {
                throw new IllegalArgumentException("label dimensions must be positive");
            }
            int placedTop = top;
            long right = (long) left[i] + widths[i];
            for (int prior = 0; prior < i; prior++) {
                long priorRight = (long) left[prior] + widths[prior];
                boolean overlaps = (long) left[i] < priorRight + horizontalGap
                        && right + horizontalGap > left[prior];
                if (overlaps) {
                    placedTop = Math.max(placedTop,
                            outputTop[prior] + heights[prior] + verticalGap);
                }
            }
            outputTop[i] = placedTop;
        }
    }
}
