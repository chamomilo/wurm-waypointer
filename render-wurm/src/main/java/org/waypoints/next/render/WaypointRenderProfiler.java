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
    private static final Channel MAP = new Channel();
    private static final Channel CONTOUR = new Channel();
    private static final Channel MAP_UPLOAD = new Channel();
    private static final Channel NATIVE_QUEUE = new Channel();
    private static final Channel FRAME_OUTPUT = new Channel();
    private static final long SLOW_NANOS = 250_000_000L;
    private static volatile int mapQueueMaximum;

    private static volatile int activeTargets;
    private static volatile int activeEffects;
    private static volatile int activeLabels;

    private WaypointRenderProfiler() { }

    public static void recordCompass(long nanos) { COMPASS.record(nanos); }
    public static void recordBeam(long nanos) { BEAM.record(nanos); }
    public static void recordSymbol(long nanos) { SYMBOL.record(nanos); }
    public static void recordLabel(long nanos) { LABEL.record(nanos); }
    public static void recordMap(long nanos, int queueCount) {
        MAP.record(nanos);
        recordHudQueue(queueCount);
    }
    public static void recordHudQueue(int queueCount) {
        if (queueCount > mapQueueMaximum) mapQueueMaximum = queueCount;
    }
    public static void recordContour(long nanos) { CONTOUR.record(nanos); }
    public static void recordMapUpload(long nanos) { MAP_UPLOAD.record(nanos); }
    public static void recordNativeQueue(long nanos) { NATIVE_QUEUE.record(nanos); }
    public static void recordFrameOutput(long nanos) { FRAME_OUTPUT.record(nanos); }

    /** Called by the diagnostics worker; slow samples are coalesced without render-thread IO. */
    public static String pollSlowNativeQueues() { return NATIVE_QUEUE.pollSlow("native queue"); }
    public static String pollSlowFrameOutput() { return FRAME_OUTPUT.pollSlow("frame output/window events"); }

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
        result.append("; "); append(result, "map", MAP);
        result.append("; "); append(result, "contours", CONTOUR);
        result.append("; "); append(result, "map uploads", MAP_UPLOAD);
        result.append("; "); append(result, "native queues", NATIVE_QUEUE);
        result.append("; "); append(result, "frame output", FRAME_OUTPUT);
        result.append(", HUD queue max=").append(mapQueueMaximum);
        if (resetSamples) {
            COMPASS.reset();
            BEAM.reset();
            SYMBOL.reset();
            LABEL.reset();
            MAP.reset(); CONTOUR.reset(); MAP_UPLOAD.reset(); mapQueueMaximum = 0;
            NATIVE_QUEUE.reset(); FRAME_OUTPUT.reset();
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
        private volatile long slowCount;
        private volatile long slowEndMillis;
        private volatile long slowNanos;
        private long reportedSlowCount;

        private void record(long nanos) {
            long duration = Math.max(0L, nanos);
            count = count + 1L;
            totalNanos = totalNanos + duration;
            if (duration > maximumNanos) maximumNanos = duration;
            if (duration >= SLOW_NANOS) {
                slowNanos = duration;
                slowEndMillis = System.currentTimeMillis();
                slowCount++;
            }
        }

        private String pollSlow(String name) {
            long samples = slowCount;
            if (samples == reportedSlowCount) return null;
            long fresh = samples - reportedSlowCount;
            reportedSlowCount = samples;
            return "Slow render: segment=" + name + ", durationMs=" + slowNanos / 1_000_000L
                    + ", endedAtUtc=" + java.time.Instant.ofEpochMilli(slowEndMillis)
                    + ", newSlowSamples=" + fresh;
        }

        private void reset() {
            count = 0L;
            totalNanos = 0L;
            maximumNanos = 0L;
        }
    }
}
