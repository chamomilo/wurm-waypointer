package org.waypoints.next.ui;

/** Platform-independent offset math for the native Surroundings list. */
public final class SurroundingsScrollGesture {
    private static final int WHEEL_ROWS = 3;

    private SurroundingsScrollGesture() { }

    public static int wheelTargetOffset(int currentOffset, int wheelDelta,
                                        int rowHeight) {
        if (wheelDelta == 0) return Math.max(0, currentOffset);
        long distance = (long) Integer.signum(wheelDelta)
                * rowHeight * WHEEL_ROWS;
        return clampOffset((long) currentOffset + distance);
    }

    public static int dragTargetOffset(int startOffset, int startMouseY,
                                       int currentMouseY) {
        return clampOffset((long) startOffset + startMouseY - currentMouseY);
    }

    /** The table drag gesture must never steal the native scrollbar thumb. */
    public static boolean startsContentDrag(boolean insidePanel,
                                            boolean overVerticalScrollBar,
                                            boolean overHorizontalScrollBar) {
        return insidePanel && !overVerticalScrollBar && !overHorizontalScrollBar;
    }

    private static int clampOffset(long value) {
        if (value <= 0L) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
