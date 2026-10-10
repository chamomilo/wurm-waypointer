package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.*;

public class WaypointRenderProfilerTest {
    @Test public void reportsPrimitiveSamplesAndActiveResourceGauges() {
        WaypointRenderProfiler.summary(true);
        WaypointRenderProfiler.activeResources(9, 4, 3);
        WaypointRenderProfiler.recordCompass(2_000L);
        WaypointRenderProfiler.recordCompass(4_000L);
        WaypointRenderProfiler.recordBeam(7_000L);
        WaypointRenderProfiler.recordSymbol(5_000L);
        WaypointRenderProfiler.recordLabel(3_000L);

        String summary = WaypointRenderProfiler.summary(false);
        assertTrue(summary.contains("active targets=9, effects=4, labels=3"));
        assertTrue(summary.contains("compass frames=2, avg=3us, max=4us"));
        assertTrue(summary.contains("beams frames=1, avg=7us, max=7us"));
        assertTrue(summary.contains("symbols frames=1, avg=5us, max=5us"));
        assertTrue(summary.contains("labels frames=1, avg=3us, max=3us"));
    }

    @Test public void distinguishesNativeRenderingFromWindowOutputAndCoalescesSlowSamples() {
        WaypointRenderProfiler.summary(true);
        WaypointRenderProfiler.pollSlowNativeQueues();
        WaypointRenderProfiler.pollSlowFrameOutput();
        WaypointRenderProfiler.recordNativeQueue(12_000L);
        WaypointRenderProfiler.recordFrameOutput(25_000L);
        assertNull(WaypointRenderProfiler.pollSlowNativeQueues());
        assertNull(WaypointRenderProfiler.pollSlowFrameOutput());
        WaypointRenderProfiler.recordNativeQueue(300_000_000L);
        WaypointRenderProfiler.recordNativeQueue(500_000_000L);
        String queues=WaypointRenderProfiler.pollSlowNativeQueues();
        assertTrue(queues.contains("segment=native queue, durationMs=500"));
        assertTrue(queues.contains("endedAtUtc="));
        assertTrue(queues.contains("newSlowSamples=2"));
        assertNull(WaypointRenderProfiler.pollSlowNativeQueues());
        assertNull(WaypointRenderProfiler.pollSlowFrameOutput());
        WaypointRenderProfiler.recordFrameOutput(750_000_000L);
        assertTrue(WaypointRenderProfiler.pollSlowFrameOutput().contains("durationMs=750"));
        assertNull(WaypointRenderProfiler.pollSlowFrameOutput());
        String summary=WaypointRenderProfiler.summary(false);
        assertTrue(summary.contains("native queues frames=3"));
        assertTrue(summary.contains("frame output frames=2"));
    }
}
