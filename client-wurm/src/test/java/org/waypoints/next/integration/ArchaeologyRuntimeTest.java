package org.waypoints.next.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.archaeology.ArchaeologyReportSession;
import org.waypoints.next.archaeology.ArchaeologyReportStatus;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.navigation.NavigationTargetKey;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class ArchaeologyRuntimeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void readyEventCreatesOneStableWhiteScrollAndOneChime()
            throws Exception {
        ArchaeologyRuntime runtime = runtime();
        ArchaeologyRuntime.EventContext context = context(server(), 1000, 1000);
        runtime.bind(context);
        String ready = ready("Old Haven");
        assertTrue(runtime.observe(":Event", ready, context));
        WaypointRecord first = awaitRecord(runtime);
        UUID id = first.getId();
        assertEquals(WaypointResolution.PENDING, first.getResolution());
        assertEquals(org.waypoints.next.model.MarkerStyle.WorldStyle
                .ARCHAEOLOGY_REPORT_SCROLL,
                first.getMarkerStyle().getWorldStyle());
        assertEquals(0, first.getArrivalRadiusMetres());
        assertEquals(ArchaeologyRuntime.SoundCue.REPORT_READY,
                awaitSound(runtime));

        assertTrue(runtime.observe(":Event", ready, context));
        Thread.sleep(80L);
        assertEquals(id, runtime.records().get(0).getId());
        assertNull(runtime.pollSoundCue());
        assertEquals(1, activeSessions(runtime));
    }

    @Test public void directionMovesTheSameUuidAndStartsNavigation()
            throws Exception {
        ArchaeologyRuntime runtime = runtime();
        ArchaeologyRuntime.EventContext context = context(server(), 1000, 1000);
        runtime.bind(context);
        runtime.observe(":Event", ready("Old Haven"), context);
        UUID id = awaitRecord(runtime).getId();
        awaitSound(runtime);

        runtime.observe(":Event",
                "Reading details from the report, Old Haven looks like it may have been nearby to the north-east.",
                context);
        WaypointRecord moved = awaitResolution(runtime,
                WaypointResolution.SEARCH_STEP);
        assertEquals(id, moved.getId());
        assertEquals(1024.5d, moved.getCoordinate().getTileX(), 0.0d);
        assertEquals(976.5d, moved.getCoordinate().getTileY(), 0.0d);
        NavigationTargetKey navigation = awaitNavigation(runtime);
        assertEquals(id, navigation.getWaypointId());
        assertEquals(ArchaeologyReportStatus.TRACKING,
                activeSession(runtime).getStatus());
    }

    @Test public void liveCompoundInvestigateResultCreatesMarkerAndChime()
            throws Exception {
        ArchaeologyRuntime runtime = runtime();
        ArchaeologyRuntime.EventContext context = context(server(), 3035, 945);
        runtime.bind(context);

        assertTrue(runtime.observe(":Event",
                "You can see signs of a single abandoned settlement here. "
                        + "Based on your knowledge of the area and small hints you can find, "
                        + "the settlement must have been called Haven. "
                        + "You feel confident you know exactly where Haven once lay, "
                        + "and complete the location details in the report. "
                        + "You find a scrap of washed out parchment signed by the last mayor, Enim. "
                        + "You write that down in your report. "
                        + "You recall this settlement, and remember the name of the founder as Enim.",
                context));

        WaypointRecord marker = awaitRecord(runtime);
        assertEquals("Haven - archaeology report ready", marker.getName());
        assertEquals(org.waypoints.next.model.MarkerStyle.WorldStyle
                .ARCHAEOLOGY_REPORT_SCROLL,
                marker.getMarkerStyle().getWorldStyle());
        assertEquals(ArchaeologyRuntime.SoundCue.REPORT_READY,
                awaitSound(runtime));
    }

    @Test public void liveTimestampedDirectionLazilyCreatesHavenWaypoint()
            throws Exception {
        ArchaeologyRuntime runtime = runtime();
        ArchaeologyRuntime.EventContext context = context(server(), 3035, 945);
        runtime.bind(context);

        assertTrue(runtime.observe(":Event",
                "[21:01:13] Reading details from the report, Haven looks like "
                        + "it may have been nearby to the northeast.", context));

        WaypointRecord marker = awaitRecord(runtime);
        assertEquals("Haven - archaeology next reading", marker.getName());
        assertEquals(WaypointResolution.SEARCH_STEP, marker.getResolution());
        assertEquals(org.waypoints.next.model.MarkerStyle.WorldStyle
                .ARCHAEOLOGY_REPORT_SCROLL,
                marker.getMarkerStyle().getWorldStyle());
        assertNull(runtime.pollSoundCue());
    }

    @Test public void cacheFoundPersistsExactTileAndNextReportReusesIt()
            throws Exception {
        Properties properties = properties();
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        ArchaeologyRuntime first = new ArchaeologyRuntime(Logger.getAnonymousLogger());
        first.configure(configuration);
        ArchaeologyRuntime.EventContext start = context(server(), 1000, 1000);
        first.bind(start);
        first.observe(":Event", ready("Old Haven"), start);
        awaitRecord(first);
        awaitSound(first);

        ArchaeologyRuntime.EventContext cache = context(server(), 777, 888);
        first.observe(":Event",
                "As you discover an Old Haven hidden cache the report is crumpled up and ruined.",
                cache);
        awaitNoRecords(first);
        assertEquals(ArchaeologyRuntime.SoundCue.CACHE_FOUND,
                awaitSound(first));
        assertEquals(1, first.knownLocations().size());
        assertEquals(777.5d, first.knownLocations().get(0).getTileX(), 0.0d);

        ArchaeologyRuntime restored = new ArchaeologyRuntime(Logger.getAnonymousLogger());
        restored.configure(configuration);
        ArchaeologyRuntime.EventContext nextReport = context(server(), 1200, 1200);
        restored.bind(nextReport);
        restored.observe(":Event", ready("Old Haven"), nextReport);
        WaypointRecord exact = awaitResolution(restored,
                WaypointResolution.EXACT_SAVED);
        assertEquals(777.5d, exact.getCoordinate().getTileX(), 0.0d);
        assertEquals(888.5d, exact.getCoordinate().getTileY(), 0.0d);
        assertEquals(0, exact.getArrivalRadiusMetres());
        assertTrue(exact.getName().contains("exact saved tile"));
    }

    @Test public void knownLocationIsServerScopedAndOverridesApproximateReading()
            throws Exception {
        Properties properties = properties();
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        ArchaeologyRuntime first = new ArchaeologyRuntime(Logger.getAnonymousLogger());
        first.configure(configuration);
        ArchaeologyRuntime.EventContext cache = context(server(), 1030, 970);
        first.observe(":Event",
                "As you discover an Old Haven hidden cache the report is crumpled up and ruined.",
                cache);
        awaitKnown(first);

        ArchaeologyRuntime restored = new ArchaeologyRuntime(Logger.getAnonymousLogger());
        restored.configure(configuration);
        ArchaeologyRuntime.EventContext same = context(server(), 1000, 1000);
        restored.bind(same);
        restored.observe(":Event",
                "Reading details from the report, Old Haven looks like it may have been far to the south.",
                same);
        WaypointRecord exact = awaitResolution(restored,
                WaypointResolution.EXACT_SAVED);
        assertEquals(1030.5d, exact.getCoordinate().getTileX(), 0.0d);
        assertEquals(970.5d, exact.getCoordinate().getTileY(), 0.0d);
        assertFalse(restored.knownLocations().get(0).isNeedsConfirmation());

        ArchaeologyRuntime other = new ArchaeologyRuntime(Logger.getAnonymousLogger());
        other.configure(configuration);
        ArchaeologyRuntime.EventContext otherContext = context(otherServer(), 400, 500);
        other.bind(otherContext);
        other.observe(":Event", ready("Old Haven"), otherContext);
        WaypointRecord pending = awaitResolution(other, WaypointResolution.PENDING);
        assertEquals(400.5d, pending.getCoordinate().getTileX(), 0.0d);
        assertEquals(500.5d, pending.getCoordinate().getTileY(), 0.0d);
    }

    @Test public void liveHavenReadingNeverMovesPreviouslyConfirmedCache()
            throws Exception {
        ArchaeologyRuntime runtime = runtime();
        ArchaeologyRuntime.EventContext cache = context(server(), 3071, 961);
        runtime.observe(":Event",
                "As you discover a Haven hidden cache the report is crumpled up and ruined.",
                cache);
        awaitKnown(runtime);

        ArchaeologyRuntime.EventContext reading = context(server(), 3031, 976);
        runtime.bind(reading);
        runtime.observe(":Event", ready("Haven"), reading);
        WaypointRecord ready = awaitResolution(runtime,
                WaypointResolution.EXACT_SAVED);
        assertEquals(3071.5d, ready.getCoordinate().getTileX(), 0.0d);
        assertEquals(961.5d, ready.getCoordinate().getTileY(), 0.0d);

        runtime.observe(":Event",
                "Reading details from the report, Haven looks like it may have been close to the east.",
                reading);
        awaitDirectionState(runtime);
        WaypointRecord afterDirection = awaitResolution(runtime,
                WaypointResolution.EXACT_SAVED);
        assertEquals(ready.getId(), afterDirection.getId());
        assertEquals(3071.5d, afterDirection.getCoordinate().getTileX(), 0.0d);
        assertEquals(961.5d, afterDirection.getCoordinate().getTileY(), 0.0d);
    }

    @Test public void getDirectionActionCorrelatesOnlyObservedItemId()
            throws Exception {
        ArchaeologyRuntime runtime = runtime();
        ArchaeologyRuntime.EventContext context = context(server(), 100, 100);
        runtime.bind(context);
        runtime.observeAction(new long[]{123456L}, "Get direction");
        runtime.observe(":Event",
                "Reading details from the report, Lost Home looks like it may have been close to the east.",
                context);
        awaitRecord(runtime);
        assertEquals(Long.valueOf(123456L), activeSession(runtime).getReportItemId());
    }

    @Test public void identicalReadingAfterMovingAdvancesTheSameReportAgain()
            throws Exception {
        ArchaeologyRuntime runtime = runtime();
        ArchaeologyRuntime.EventContext start = context(server(), 1000, 1000);
        runtime.bind(start);
        runtime.observe(":Event", ready("Lost Home"), start);
        UUID id = awaitRecord(runtime).getId();
        awaitSound(runtime);
        String reading = "Reading details from the report, Lost Home looks like it may have been nearby to the east.";

        runtime.observe(":Event", reading, start);
        WaypointRecord first = awaitCoordinate(runtime, 1031.5d, 1000.5d);
        assertEquals(id, first.getId());

        ArchaeologyRuntime.EventContext moved = context(server(), 1031, 1000);
        runtime.observe(":Event", reading, moved);
        WaypointRecord second = awaitCoordinate(runtime, 1062.5d, 1000.5d);
        assertEquals(id, second.getId());
        assertEquals(1, activeSessions(runtime));
    }

    private ArchaeologyRuntime runtime() throws Exception {
        ArchaeologyRuntime runtime = new ArchaeologyRuntime(Logger.getAnonymousLogger());
        runtime.configure(WaypointClientConfiguration.from(properties()));
        return runtime;
    }

    private Properties properties() throws Exception {
        File directory = temporary.newFolder();
        Properties properties = new Properties();
        properties.setProperty("archaeologySessionFile",
                new File(directory, "sessions.properties").getAbsolutePath());
        properties.setProperty("archaeologyKnownLocationsFile",
                new File(directory, "known.properties").getAbsolutePath());
        properties.setProperty("archaeologyHistoryLimit", "8");
        return properties;
    }

    private static ArchaeologyRuntime.EventContext context(ServerIdentity server,
                                                            double x, double y) {
        return new ArchaeologyRuntime.EventContext(x + 0.5d, y + 0.5d, 100.0d,
                WaypointLayer.SURFACE, server, "Tester",
                Instant.parse("2026-08-10T12:00:00Z"),
                new org.waypoints.next.source.MapBounds(4096, 4096));
    }

    private static String ready(String deed) {
        return "You feel confident you know exactly where " + deed
                + " once lay, and complete the location details in the report.";
    }

    private static WaypointRecord awaitRecord(ArchaeologyRuntime runtime)
            throws Exception {
        for (int i = 0; i < 300; i++) {
            List<WaypointRecord> records = runtime.records();
            if (!records.isEmpty()) return records.get(0);
            Thread.sleep(10L);
        }
        fail("Archaeology worker did not publish a marker");
        return null;
    }

    private static WaypointRecord awaitResolution(ArchaeologyRuntime runtime,
                                                   WaypointResolution resolution)
            throws Exception {
        for (int i = 0; i < 300; i++) {
            List<WaypointRecord> records = runtime.records();
            if (!records.isEmpty() && records.get(0).getResolution() == resolution) {
                return records.get(0);
            }
            Thread.sleep(10L);
        }
        fail("Archaeology marker did not reach " + resolution);
        return null;
    }

    private static WaypointRecord awaitCoordinate(ArchaeologyRuntime runtime,
                                                   double x, double y)
            throws Exception {
        for (int i = 0; i < 300; i++) {
            List<WaypointRecord> records = runtime.records();
            if (!records.isEmpty()
                    && records.get(0).getCoordinate().getTileX() == x
                    && records.get(0).getCoordinate().getTileY() == y) {
                return records.get(0);
            }
            Thread.sleep(10L);
        }
        fail("Archaeology marker did not reach X=" + x + " Y=" + y);
        return null;
    }

    private static NavigationTargetKey awaitNavigation(ArchaeologyRuntime runtime)
            throws Exception {
        for (int i = 0; i < 300; i++) {
            NavigationTargetKey key = runtime.pollNavigationRequest();
            if (key != null) return key;
            Thread.sleep(10L);
        }
        fail("Archaeology worker did not request navigation");
        return null;
    }

    private static ArchaeologyRuntime.SoundCue awaitSound(ArchaeologyRuntime runtime)
            throws Exception {
        for (int i = 0; i < 300; i++) {
            ArchaeologyRuntime.SoundCue cue = runtime.pollSoundCue();
            if (cue != null) return cue;
            Thread.sleep(10L);
        }
        fail("Archaeology worker did not queue a sound");
        return null;
    }

    private static void awaitNoRecords(ArchaeologyRuntime runtime)
            throws Exception {
        for (int i = 0; i < 300; i++) {
            if (runtime.records().isEmpty() && !runtime.knownLocations().isEmpty()) return;
            Thread.sleep(10L);
        }
        fail("Archaeology completion did not remove the marker");
    }

    private static void awaitKnown(ArchaeologyRuntime runtime) throws Exception {
        for (int i = 0; i < 300; i++) {
            if (!runtime.knownLocations().isEmpty()) return;
            Thread.sleep(10L);
        }
        fail("Exact archaeology location was not persisted");
    }

    private static void awaitDirectionState(ArchaeologyRuntime runtime)
            throws Exception {
        for (int i = 0; i < 300; i++) {
            ArchaeologyReportSession session = activeSession(runtime);
            if (session.getDistanceBand()
                    == org.waypoints.next.archaeology.ArchaeologyDistanceBand.CLOSE
                    && session.getDirection()
                    == org.waypoints.next.archaeology.ArchaeologyDirection.EAST) {
                return;
            }
            Thread.sleep(10L);
        }
        fail("Archaeology direction event was not applied");
    }

    private static ArchaeologyReportSession activeSession(ArchaeologyRuntime runtime) {
        for (ArchaeologyReportSession session : runtime.sessions()) {
            if (session.isActive()) return session;
        }
        fail("No active archaeology session");
        return null;
    }

    private static int activeSessions(ArchaeologyRuntime runtime) {
        int count = 0;
        for (ArchaeologyReportSession session : runtime.sessions()) {
            if (session.isActive()) count++;
        }
        return count;
    }

    private static ServerIdentity server() {
        return ServerIdentity.of(new ServerEndpoint("127.0.0.1", 3724, 27016),
                "Test Server", "Test", ServerIdentity.Resolution.RESOLVED);
    }

    private static ServerIdentity otherServer() {
        return ServerIdentity.of(new ServerEndpoint("127.0.0.2", 3724, 27016),
                "Other Server", "Other", ServerIdentity.Resolution.RESOLVED);
    }
}
