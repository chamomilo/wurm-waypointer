package org.waypoints.next.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.lootmap.LootMapTerrain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class LootMapRuntimeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void changedReadMapTargetCreatesASeparateHuntFile() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("lootMapLogDirectory",
                temporary.getRoot().getAbsolutePath());
        LootMapRuntime runtime = new LootMapRuntime(Logger.getAnonymousLogger());
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        runtime.configure(configuration);
        LootMapRuntime.EventContext context = new LootMapRuntime.EventContext(
                1000, 1000, 0.0d, 100.0d, WaypointLayer.SURFACE,
                server(), "Tester", Instant.parse("2026-08-08T12:00:00Z"),
                configuration.getMapBounds());

        runtime.observeAction(new long[]{111L}, "Read map");
        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                context));
        awaitRecords(runtime, 1);
        assertEquals("1", runtime.records().get(0).getExtensions()
                .get("lootmap.readingCount").get(0));
        assertEquals("111", runtime.records().get(0).getExtensions()
                .get("lootmap.mapItemId").get(0));
        UUID first = runtime.records().get(0).getId();

        runtime.observeAction(new long[]{222L}, "Read map");
        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                context));
        awaitDifferentRecord(runtime, first);
        assertEquals("1", runtime.records().get(0).getExtensions()
                .get("lootmap.readingCount").get(0));
        assertEquals("222", runtime.records().get(0).getExtensions()
                .get("lootmap.mapItemId").get(0));
        assertEquals(2, temporary.getRoot().listFiles().length);

        java.io.File firstFile = temporary.getRoot().listFiles()[0];
        java.io.File secondFile = temporary.getRoot().listFiles()[1];
        String left = new String(Files.readAllBytes(firstFile.toPath()),
                StandardCharsets.UTF_8);
        String right = new String(Files.readAllBytes(secondFile.toPath()),
                StandardCharsets.UTF_8);
        assertTrue(left.contains("\"event\":\"map_changed\"")
                || right.contains("\"event\":\"map_changed\""));
        assertFalse(left.contains("mapItemId"));
        assertFalse(right.contains("mapItemId"));
        runtime.dismiss();
    }

    @Test public void unfinishedHuntRetainsMapIdAfterActionWindowExpires()
            throws Exception {
        Properties properties = new Properties();
        properties.setProperty("lootMapLogDirectory",
                temporary.getRoot().getAbsolutePath());
        LootMapRuntime runtime = new LootMapRuntime(Logger.getAnonymousLogger());
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        runtime.configure(configuration);
        LootMapRuntime.EventContext context = new LootMapRuntime.EventContext(
                1000, 1000, 0.0d, 100.0d, WaypointLayer.SURFACE,
                server(), "Tester", Instant.parse("2026-08-08T12:00:00Z"),
                configuration.getMapBounds());

        runtime.observeAction(new long[]{111L}, "Read map");
        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                context));
        awaitRecords(runtime, 1);

        assertEquals(Long.valueOf(111L), runtime.correlatedMapItemId(
                System.nanoTime() + 61_000_000_000L));
        runtime.dismiss();
    }

    @Test public void everySuccessfulReadingRequestsIdempotentNavigation()
            throws Exception {
        Properties properties = new Properties();
        properties.setProperty("lootMapLogDirectory",
                temporary.getRoot().getAbsolutePath());
        LootMapRuntime runtime = new LootMapRuntime(Logger.getAnonymousLogger());
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        runtime.configure(configuration);
        LootMapRuntime.EventContext context = new LootMapRuntime.EventContext(
                1000, 1000, 0.0d, 100.0d, WaypointLayer.SURFACE,
                server(), "Tester", Instant.parse("2026-08-08T12:00:00Z"),
                configuration.getMapBounds());

        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                context));
        NavigationTargetKey first = awaitNavigationRequest(runtime);
        awaitRecords(runtime, 1);
        assertEquals(runtime.records().get(0).getId(), first.getWaypointId());
        assertEquals("1", runtime.records().get(0).getExtensions()
                .get("lootmap.readingCount").get(0));

        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                context));
        NavigationTargetKey second = awaitNavigationRequest(runtime);
        assertEquals(first, second);
        assertEquals("2", runtime.records().get(0).getExtensions()
                .get("lootmap.readingCount").get(0));
        assertEquals(0, runtime.records().get(0).getArrivalRadiusMetres());
        runtime.dismiss();
    }

    @Test public void firstAndRepeatedReadingsMoveWaterTargetsToDryLand()
            throws Exception {
        Properties properties = new Properties();
        properties.setProperty("lootMapLogDirectory",
                temporary.getRoot().getAbsolutePath());
        LootMapRuntime runtime = new LootMapRuntime(Logger.getAnonymousLogger());
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        runtime.configure(configuration);
        LootMapTerrain coast = new LootMapTerrain() {
            @Override public TileState classify(int x, int y) {
                return y <= 667 ? TileState.DRY_LAND : TileState.WATER;
            }
        };
        LootMapRuntime.EventContext first = new LootMapRuntime.EventContext(
                3062, 660, 180.0d, 0.2d, WaypointLayer.SURFACE,
                server(), "Tester", Instant.parse("2026-08-14T23:00:00Z"),
                configuration.getMapBounds(), coast);

        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                first));
        awaitReadingCount(runtime, "1");
        WaypointRecord firstRecord = runtime.records().get(0);
        assertEquals(3062.0d, Math.floor(
                firstRecord.getCoordinate().getTileX()), 0.0d);
        assertEquals(667.0d, Math.floor(
                firstRecord.getCoordinate().getTileY()), 0.0d);
        assertEquals("true", firstRecord.getExtensions()
                .get("lootmap.landAdjusted").get(0));
        assertTrue(firstRecord.getExtensions().get("lootmap.plannedTile")
                .get(0).startsWith("3062,"));
        assertFalse("3062,667".equals(firstRecord.getExtensions()
                .get("lootmap.plannedTile").get(0)));

        LootMapRuntime.EventContext repeated = new LootMapRuntime.EventContext(
                3062, 667, 180.0d, 0.2d, WaypointLayer.SURFACE,
                server(), "Tester", Instant.parse("2026-08-14T23:01:00Z"),
                configuration.getMapBounds(), coast);
        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                repeated));
        awaitReadingCount(runtime, "2");
        WaypointRecord repeatedRecord = runtime.records().get(0);
        assertEquals(667.0d, Math.floor(
                repeatedRecord.getCoordinate().getTileY()), 0.0d);
        assertEquals("true", repeatedRecord.getExtensions()
                .get("lootmap.landAdjusted").get(0));
        runtime.dismiss();
    }

    @Test public void exactReadingChimesOnceAndChangesOnlyTheMarkerIcon()
            throws Exception {
        Properties properties = new Properties();
        properties.setProperty("lootMapLogDirectory",
                temporary.getRoot().getAbsolutePath());
        LootMapRuntime runtime = new LootMapRuntime(Logger.getAnonymousLogger());
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        runtime.configure(configuration);
        LootMapRuntime.EventContext context = new LootMapRuntime.EventContext(
                1000, 1000, 0.0d, 100.0d, WaypointLayer.SURFACE,
                server(), "Tester", Instant.parse("2026-08-08T12:00:00Z"),
                configuration.getMapBounds());

        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                context));
        awaitRecords(runtime, 1);
        WaypointRecord approaching = runtime.records().get(0);
        MarkerStyle amber = approaching.getMarkerStyle();
        assertEquals(MarkerStyle.WorldStyle.LOOT_MAP_SCROLL,
                amber.getWorldStyle());

        assertTrue(runtime.observe(":Event",
                "You are practically standing on the marked spot!", context));
        awaitRecordName(runtime, "Dig for loot ! (use shovel on map)");
        WaypointRecord exact = runtime.records().get(0);
        assertEquals(MarkerStyle.WorldStyle.SHOVEL,
                exact.getMarkerStyle().getWorldStyle());
        assertEquals(amber.getRed(), exact.getMarkerStyle().getRed(), 0.0f);
        assertEquals(amber.getGreen(), exact.getMarkerStyle().getGreen(), 0.0f);
        assertEquals(amber.getBlue(), exact.getMarkerStyle().getBlue(), 0.0f);
        assertEquals(amber.getAlpha(), exact.getMarkerStyle().getAlpha(), 0.0f);
        assertEquals(amber.getMarkerSize(),
                exact.getMarkerStyle().getMarkerSize(), 0.0f);
        assertEquals(amber.getBeamWidth(),
                exact.getMarkerStyle().getBeamWidth(), 0.0f);
        assertTrue(awaitDigChime(runtime));

        assertTrue(runtime.observe(":Event",
                "You are practically standing on the marked spot!", context));
        awaitReadingCount(runtime, "3");
        assertFalse(runtime.pollDigChime());
        runtime.dismiss();
    }

    @Test public void readingMessageSeparatesReportedRangeFromPlannedStep()
            throws Exception {
        Properties properties = new Properties();
        properties.setProperty("lootMapLogDirectory",
                temporary.getRoot().getAbsolutePath());
        LootMapRuntime runtime = new LootMapRuntime(Logger.getAnonymousLogger());
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        runtime.configure(configuration);
        LootMapRuntime.EventContext context = new LootMapRuntime.EventContext(
                1000, 1000, 0.0d, 100.0d, WaypointLayer.SURFACE,
                server(), "Tester", Instant.parse("2026-08-08T12:00:00Z"),
                configuration.getMapBounds());

        assertTrue(runtime.observe(":Event",
                "The marked spot is pretty far away in front of you.", context));
        String message = awaitMessage(runtime, "Loot Map reading 1:");

        assertTrue(message.contains("reported loot range 500-999 tiles"));
        assertTrue(message.contains("next reading point 500 tiles away"));
        runtime.dismiss();
    }

    @Test public void dugUpChestKeepsMarkerUntilItsContainerActuallyOpens()
            throws Exception {
        Properties properties = new Properties();
        properties.setProperty("lootMapLogDirectory",
                temporary.getRoot().getAbsolutePath());
        LootMapRuntime runtime = new LootMapRuntime(Logger.getAnonymousLogger());
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        runtime.configure(configuration);
        LootMapRuntime.EventContext context = new LootMapRuntime.EventContext(
                1000, 1000, 0.0d, 100.0d, WaypointLayer.SURFACE,
                server(), "Tester", Instant.parse("2026-08-08T12:00:00Z"),
                configuration.getMapBounds());

        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                context));
        awaitRecords(runtime, 1);
        assertEquals(0.5d, fractional(runtime.records().get(0)
                .getCoordinate().getTileX()), 0.0d);
        assertEquals(0.5d, fractional(runtime.records().get(0)
                .getCoordinate().getTileY()), 0.0d);
        assertNull(runtime.records().get(0).getCoordinate().getHeight());
        assertEquals(22.0f, runtime.records().get(0).getMarkerStyle()
                .getMarkerSize(), 0.0f);
        assertEquals(4.8f, runtime.records().get(0).getMarkerStyle()
                .getBeamWidth(), 0.0f);
        assertEquals(0, runtime.records().get(0).getArrivalRadiusMetres());

        assertTrue(runtime.observe(":Event", "You find a loot chest!", context));
        awaitRecordName(runtime, "Clear ambush and open chest");
        assertEquals("AWAITING_CHEST_OPEN", runtime.records().get(0)
                .getExtensions().get("lootmap.phase").get(0));
        assertNotNull(awaitNavigationRequest(runtime));

        runtime.observeAction(new long[]{987L}, "Open");
        runtime.inventoryWindowOpened(654L, "loot chest");
        Thread.sleep(30L);
        assertEquals(1, runtime.records().size());
        runtime.inventoryWindowOpened(987L, "large chest");
        Thread.sleep(30L);
        assertEquals(1, runtime.records().size());
        runtime.inventoryWindowOpened(987L, "loot chest");
        awaitRecords(runtime, 0);
        java.io.File log = temporary.getRoot().listFiles()[0];
        String content = new String(Files.readAllBytes(log.toPath()),
                StandardCharsets.UTF_8);
        assertTrue(content.contains("\"event\":\"chest_dug_up\""));
        assertTrue(content.contains("\"event\":\"chest_opened\""));
    }

    @Test public void connectionEndFlushesTheActiveHuntBeforeReturning()
            throws Exception {
        Properties properties = new Properties();
        properties.setProperty("lootMapLogDirectory",
                temporary.getRoot().getAbsolutePath());
        LootMapRuntime runtime = new LootMapRuntime(Logger.getAnonymousLogger());
        WaypointClientConfiguration configuration =
                WaypointClientConfiguration.from(properties);
        runtime.configure(configuration);
        LootMapRuntime.EventContext context = new LootMapRuntime.EventContext(
                1000, 1000, 0.0d, 100.0d, WaypointLayer.SURFACE,
                server(), "Tester", Instant.parse("2026-08-08T12:00:00Z"),
                configuration.getMapBounds());

        assertTrue(runtime.observe(":Event",
                "The marked spot is quite some distance away in front of you.",
                context));
        awaitRecords(runtime, 1);
        runtime.connectionEnded();

        assertTrue(runtime.records().isEmpty());
        java.io.File log = temporary.getRoot().listFiles()[0];
        String content = new String(Files.readAllBytes(log.toPath()),
                StandardCharsets.UTF_8);
        assertTrue(content.contains("\"event\":\"connection_ended\""));
    }

    private static double fractional(double value) {
        return value - Math.floor(value);
    }

    private static void awaitRecords(LootMapRuntime runtime, int expected)
            throws Exception {
        for (int i = 0; i < 200; i++) {
            if (runtime.records().size() == expected) return;
            Thread.sleep(10L);
        }
        fail("Loot Map worker did not reach record count " + expected);
    }

    private static void awaitDifferentRecord(LootMapRuntime runtime, UUID first)
            throws Exception {
        for (int i = 0; i < 300; i++) {
            if (!runtime.records().isEmpty()
                    && !first.equals(runtime.records().get(0).getId())) return;
            Thread.sleep(10L);
        }
        fail("Loot Map worker did not split the changed map session");
    }

    private static void awaitRecordName(LootMapRuntime runtime, String expected)
            throws Exception {
        for (int i = 0; i < 200; i++) {
            if (!runtime.records().isEmpty()
                    && expected.equals(runtime.records().get(0).getName())) return;
            Thread.sleep(10L);
        }
        fail("Loot Map worker did not publish phase " + expected);
    }

    private static boolean awaitDigChime(LootMapRuntime runtime)
            throws Exception {
        for (int i = 0; i < 200; i++) {
            if (runtime.pollDigChime()) return true;
            Thread.sleep(10L);
        }
        fail("Loot Map worker did not queue the exact-reading chime");
        return false;
    }

    private static void awaitReadingCount(LootMapRuntime runtime,
                                          String expected) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (!runtime.records().isEmpty()
                    && expected.equals(runtime.records().get(0).getExtensions()
                    .get("lootmap.readingCount").get(0))) return;
            Thread.sleep(10L);
        }
        fail("Loot Map worker did not reach reading count " + expected);
    }

    private static String awaitMessage(LootMapRuntime runtime, String prefix)
            throws Exception {
        for (int i = 0; i < 300; i++) {
            String message;
            while ((message = runtime.pollMessage()) != null) {
                if (message.startsWith(prefix)) return message;
            }
            Thread.sleep(10L);
        }
        fail("Loot Map worker did not publish message " + prefix);
        return null;
    }

    private static NavigationTargetKey awaitNavigationRequest(
            LootMapRuntime runtime) throws Exception {
        for (int i = 0; i < 200; i++) {
            NavigationTargetKey request = runtime.pollNavigationRequest();
            if (request != null) return request;
            Thread.sleep(10L);
        }
        fail("Loot Map worker did not request navigation");
        return null;
    }

    private static ServerIdentity server() {
        return ServerIdentity.of(new ServerEndpoint("127.0.0.1", 3724, 27016),
                "Test Server", "Test", ServerIdentity.Resolution.RESOLVED);
    }
}
