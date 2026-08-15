package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

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
}
