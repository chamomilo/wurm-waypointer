package org.waypoints.next.render;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.navigation.GroundRouteTrace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NavigationRouteDiagnosticLogTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void writesPointsSlopesWaterAndBlockingDecisionAsJsonl()
            throws Exception {
        NavigationRouteDiagnosticLog log = new NavigationRouteDiagnosticLog(
                temporary.getRoot().toPath(),
                Instant.parse("2026-08-09T12:00:00Z"), "server-key",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "test waypoint", 2, 0, 0, 20.0f, 0.7f,
                Logger.getLogger("route-log-test"));
        GroundRouteTrace trace = GroundRouteTrace.analyse(2, 0, 0, 3, true,
                20.0f, 0.7f, Arrays.asList(
                        point(0, 0, 1.0f, 0.0f),
                        point(1, 0, 3.5f, 0.0f),
                        point(2, 0, -1.0f, 1.0f)));

        log.routeEvaluated(trace, "test_a_star", 17, 3, 4, 5, 6, 2L);
        log.close("test_complete");

        List<String> lines = Files.readAllLines(log.getFile(),
                StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("\"event\":\"route_session_started\""));
        assertTrue(lines.get(1).contains("\"absoluteSlopeDirtEstimate\":25.0000"));
        assertTrue(lines.get(1).contains("\"waterDepthMetres\":1.0000"));
        assertTrue(lines.get(1).contains("\"status\":\"BLOCKED_SLOPE"));
        assertTrue(lines.get(1).contains("\"strategy\":\"test_a_star\""));
        assertTrue(lines.get(1).contains("\"rejectedWaterEdges\":4"));
        assertTrue(lines.get(1).contains("\"highwayKind\":\"NONE\""));
        assertTrue(lines.get(1).contains("\"publishedHighway\":false"));
        assertTrue(lines.get(2).contains("\"reason\":\"test_complete\""));
    }

    @Test public void stopsBeforeAFullRouteWouldExceedTheSessionLimit()
            throws Exception {
        NavigationRouteDiagnosticLog log = new NavigationRouteDiagnosticLog(
                temporary.getRoot().toPath(),
                Instant.parse("2026-08-09T12:00:00Z"), "server-key",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "bounded waypoint", 20, 0, 0, 20.0f, 0.7f,
                Logger.getLogger("bounded-route-log-test"), 1000L);
        GroundRouteTrace trace = GroundRouteTrace.analyse(20, 0, 0, 21, true,
                20.0f, 0.7f, straightPoints(21));

        log.routeEvaluated(trace, "bounded_a_star", 17, 0, 0, 0, 0, 2L);
        log.routeEvaluated(trace, "must_not_be_queued", 17, 0, 0, 0, 0, 2L);
        log.close("test_complete");

        List<String> lines = Files.readAllLines(log.getFile(),
                StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("\"event\":\"route_session_started\""));
        assertTrue(lines.get(1).contains(
                "\"event\":\"route_log_limit_reached\""));
        assertTrue(lines.get(1).contains("\"maximumFileBytes\":1000"));
        assertFalse(lines.get(1).contains("must_not_be_queued"));
        assertTrue(lines.get(2).contains("\"event\":\"route_session_ended\""));
        assertTrue(Files.size(log.getFile()) < 1600L);
    }

    private static List<GroundRouteTrace.Point> straightPoints(int count) {
        GroundRouteTrace.Point[] points = new GroundRouteTrace.Point[count];
        for (int i = 0; i < count; i++) {
            points[i] = point(i, 0, 1.0f, 0.0f);
        }
        return Arrays.asList(points);
    }

    private static GroundRouteTrace.Point point(int x, int y, float height,
                                                 float waterDepth) {
        return new GroundRouteTrace.Point(x, y, height,
                GroundRouteTrace.HeightSource.NEAR, waterDepth,
                GroundRouteTrace.WaterSource.NEAR);
    }
}
