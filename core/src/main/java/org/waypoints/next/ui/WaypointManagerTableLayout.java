package org.waypoints.next.ui;

/** Pure responsive column policy shared by every Manager table row. */
public final class WaypointManagerTableLayout {
    public static final int COLUMN_COUNT = 13;
    public static final int WINDOW_CHROME = 38;

    private static final int[] MINIMUM = {
            42, 52, 128, 62, 76, 72, 82, 66, 82, 50, 56, 50, 64
    };
    private static final int[] PREFERRED = {
            44, 58, 208, 74, 108, 92, 104, 72, 106, 52, 58, 52, 68
    };
    private static final int[] SURPLUS_WEIGHT = {
            0, 0, 4, 0, 2, 2, 2, 0, 2, 0, 0, 0, 0
    };
    private static final int MINIMUM_CONTENT_WIDTH = sum(MINIMUM);
    private static final int PREFERRED_CONTENT_WIDTH = sum(PREFERRED);

    private WaypointManagerTableLayout() {
    }

    public static int minimumWindowWidth() {
        return MINIMUM_CONTENT_WIDTH + WINDOW_CHROME;
    }

    public static int contentWidth(int windowWidth) {
        return Math.max(MINIMUM_CONTENT_WIDTH, windowWidth - WINDOW_CHROME);
    }

    public static int[] columns(int windowWidth) {
        int contentWidth = contentWidth(windowWidth);
        int[] result = MINIMUM.clone();
        int preferredGrowth = Math.min(contentWidth - MINIMUM_CONTENT_WIDTH,
                PREFERRED_CONTENT_WIDTH - MINIMUM_CONTENT_WIDTH);
        distributeCapacity(result, preferredGrowth);
        int surplus = contentWidth - PREFERRED_CONTENT_WIDTH;
        if (surplus > 0) distributeSurplus(result, surplus);
        return result;
    }

    private static void distributeCapacity(int[] result, int amount) {
        if (amount <= 0) return;
        int capacity = PREFERRED_CONTENT_WIDTH - MINIMUM_CONTENT_WIDTH;
        int assigned = 0;
        for (int i = 0; i < result.length; i++) {
            int delta = amount * (PREFERRED[i] - MINIMUM[i]) / capacity;
            result[i] += delta;
            assigned += delta;
        }
        for (int i = 0; assigned < amount; i = (i + 1) % result.length) {
            if (result[i] < PREFERRED[i]) {
                result[i]++;
                assigned++;
            }
        }
    }

    private static void distributeSurplus(int[] result, int amount) {
        int totalWeight = sum(SURPLUS_WEIGHT);
        int assigned = 0;
        for (int i = 0; i < result.length; i++) {
            int delta = amount * SURPLUS_WEIGHT[i] / totalWeight;
            result[i] += delta;
            assigned += delta;
        }
        for (int i = 0; assigned < amount; i = (i + 1) % result.length) {
            if (SURPLUS_WEIGHT[i] > 0) {
                result[i]++;
                assigned++;
            }
        }
    }

    private static int sum(int[] values) {
        int result = 0;
        for (int value : values) result += value;
        return result;
    }
}
