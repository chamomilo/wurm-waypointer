package org.waypoints.next.navigation;

/** Allocation-free deterministic clustering for projected compass markers. */
public final class CompassMarkerClusterer {
    private CompassMarkerClusterer() {
    }

    /**
     * Groups projected points whose centers are no farther apart than the
     * supplied screen-pixel threshold from the group's first member. Groups do
     * not grow transitively through a chain of nearby points, so a dense ring
     * cannot collapse into one cluster. Group ids are stable and ordered by
     * the first input member.
     *
     * @param x projected x coordinates
     * @param y projected y coordinates
     * @param count number of valid coordinates
     * @param thresholdPixels inclusive center-distance threshold
     * @param groups output group id for each coordinate
     * @param parents caller-owned scratch space for group anchor indexes
     * @return number of groups
     */
    public static int cluster(int[] x, int[] y, int count, int thresholdPixels,
                              int[] groups, int[] parents) {
        if (x == null || y == null || groups == null || parents == null) {
            throw new IllegalArgumentException("cluster arrays are required");
        }
        if (count < 0 || count > x.length || count > y.length
                || count > groups.length || count > parents.length) {
            throw new IllegalArgumentException("cluster count exceeds array capacity");
        }
        if (thresholdPixels < 0) {
            throw new IllegalArgumentException("cluster threshold must not be negative");
        }
        for (int i = 0; i < count; i++) {
            groups[i] = -1;
        }
        long thresholdSquared = (long) thresholdPixels * thresholdPixels;
        int groupCount = 0;
        for (int point = 0; point < count; point++) {
            int matchedGroup = -1;
            for (int group = 0; group < groupCount; group++) {
                int anchor = parents[group];
                long dx = (long) x[anchor] - x[point];
                long dy = (long) y[anchor] - y[point];
                if (dx * dx + dy * dy <= thresholdSquared) {
                    matchedGroup = group;
                    break;
                }
            }
            if (matchedGroup < 0) {
                matchedGroup = groupCount;
                parents[groupCount++] = point;
            }
            groups[point] = matchedGroup;
        }
        return groupCount;
    }
}
