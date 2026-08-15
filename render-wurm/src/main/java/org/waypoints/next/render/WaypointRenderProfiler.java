package org.waypoints.next.render;

/**
 * Approximate, allocation-free render counters. The render thread only writes
 * primitive fields; a String is created solely for an explicit /wp perf query.
 */
public final class WaypointRenderProfiler {
    private static final Channel COMPASS = new Channel();
    private static final Channel BEAM = new Channel();
    private static final Channel SYMBOL = new Channel();
    private static final Channel LABEL = new Channel();

    private static volatile int activeTargets;
    private static volatile int activeEffects;
    private static volatile int activeLabels;

    private WaypointRenderProfiler() { }

    public static void recordCompass(long nanos) { COMPASS.record(nanos); }
    public static void recordBeam(long nanos) { BEAM.record(nanos); }
    public static void recordSymbol(long nanos) { SYMBOL.record(nanos); }
    public static void recordLabel(long nanos) { LABEL.record(nanos); }

    public static void activeResources(int targets, int effects, int labels) {
        activeTargets = Math.max(0, targets);
        activeEffects = Math.max(0, effects);
        activeLabels = Math.max(0, labels);
    }

    public static String summary(boolean resetSamples) {
        StringBuilder result = new StringBuilder(256);
        result.append("Render perf: active targets=").append(activeTargets)
                .append(", effects=").append(activeEffects)
                .append(", labels=").append(activeLabels).append("; ");
        append(result, "compass", COMPASS);
        result.append("; ");
        append(result, "beams", BEAM);
        result.append("; ");
        append(result, "symbols", SYMBOL);
        result.append("; ");
        append(result, "labels", LABEL);
        if (resetSamples) {
            COMPASS.reset();
            BEAM.reset();
            SYMBOL.reset();
            LABEL.reset();
            result.append("; samples reset");
        }
        return result.toString();
    }

    private static void append(StringBuilder target, String name, Channel channel) {
        long count = channel.count;
        long total = channel.totalNanos;
        long maximum = channel.maximumNanos;
        long average = count == 0L ? 0L : total / count;
        target.append(name).append(" frames=").append(count)
                .append(", avg=").append(average / 1_000L).append("us")
                .append(", max=").append(maximum / 1_000L).append("us");
    }

    private static final class Channel {
        private volatile long count;
        private volatile long totalNanos;
        private volatile long maximumNanos;

        private void record(long nanos) {
            long duration = Math.max(0L, nanos);
            count = count + 1L;
            totalNanos = totalNanos + duration;
            if (duration > maximumNanos) maximumNanos = duration;
        }

        private void reset() {
            count = 0L;
            totalNanos = 0L;
            maximumNanos = 0L;
        }
    }
}
