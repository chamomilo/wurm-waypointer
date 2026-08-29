package org.waypoints.next.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.service.WaypointManagerQuery;
import org.waypoints.next.service.WaypointManagerSnapshot;
import org.waypoints.next.surroundings.CreatureModifier;
import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKind;
import org.waypoints.next.surroundings.SurroundingsClassifier;
import org.waypoints.next.ui.WaypointManagerContext;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class StaticWaypointRuntimeSurroundingsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void markCreatesListedFifteenMinuteWaypointAndClearDeletesIt()
            throws Exception {
        Path directory = temporary.newFolder("surroundings-mark").toPath();
        Properties properties = new Properties();
        properties.setProperty("waypointDataFile",
                directory.resolve("waypoints.wpt").toString());
        properties.setProperty("waypointTransferFile",
                directory.resolve("transfer.wpt").toString());
        properties.setProperty("vanillaLandmarkStateFile",
                directory.resolve("vanilla.state").toString());
        StaticWaypointRuntime runtime = new StaticWaypointRuntime(
                Logger.getAnonymousLogger());
        runtime.configureAndLoad(WaypointClientConfiguration.from(properties));

        ServerIdentity server = ServerIdentity.of(new ServerEndpoint(
                "example.test", 3724, 27016), "Example", "Example",
                ServerIdentity.Resolution.RESOLVED);
        WaypointManagerContext context = new WaypointManagerContext(
                "Tester", server, 10.0d, 20.0d, 2.0d, WaypointLayer.SURFACE);
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        SurroundingEntry animal = SurroundingEntry.builder()
                .kind(SurroundingKind.ANIMAL).wurmId(42L).name("raging wolf")
                .modelName("model.creature.quadraped.wolf.black")
                .category(SurroundingsClassifier.ANIMALS).material("flesh")
                .creatureModifier(CreatureModifier.RAGING)
                .position(40.0d, 80.0d, 2.0d).layer(0)
                .firstSeenAt(now).updatedAt(now).build();

        assertEquals(1, runtime.setSurroundingsWaypoints(
                Collections.singletonList(animal),
                Collections.singletonList(animal.getKey()), true, context, now));

        WaypointManagerSnapshot manager = runtime.managerSnapshot(
                WaypointManagerQuery.builder().allServers().build());
        assertEquals(1, manager.getRows().size());
        assertEquals(WaypointSourceType.MANAGED_ANIMAL,
                manager.getRows().get(0).getSourceType());
        assertEquals(WaypointResolution.STATIC_EXACT,
                manager.getRows().get(0).getResolution());
        assertEquals(MarkerStyle.WorldStyle.EXCLAMATION,
                runtime.revisionSnapshot().getRecords().get(0)
                        .getMarkerStyle().getWorldStyle());
        MarkerStyle editedColour = new MarkerStyle(
                MarkerStyle.WorldStyle.DIAMOND, 0.2f, 0.3f, 0.4f, 0.8f,
                11.0f, 2.0f, true, true);
        MarkerStyle preview = runtime.managerPreviewStyle(
                runtime.revisionSnapshot().getRecords().get(0).getId(),
                editedColour);
        assertEquals(MarkerStyle.WorldStyle.EXCLAMATION,
                preview.getWorldStyle());
        assertEquals(editedColour.getRed(), preview.getRed(), 0.0f);
        assertEquals(now.plusSeconds(15L * 60L),
                manager.getRows().get(0).getExpiresAt());
        assertTrue(runtime.surroundingsWaypointKeys().contains(animal.getKey()));

        assertEquals(1, runtime.setSurroundingsWaypoints(
                Collections.<SurroundingEntry>emptyList(),
                Collections.singletonList(animal.getKey()), false, null,
                now.plusSeconds(1L)));
        assertEquals(0, runtime.recordCount());
    }

    @Test public void vanishedObjectDeletesItsPersistedMark() throws Exception {
        Path directory = temporary.newFolder("surroundings-vanished").toPath();
        Properties properties = new Properties();
        properties.setProperty("waypointDataFile",
                directory.resolve("waypoints.wpt").toString());
        properties.setProperty("waypointTransferFile",
                directory.resolve("transfer.wpt").toString());
        properties.setProperty("vanillaLandmarkStateFile",
                directory.resolve("vanilla.state").toString());
        StaticWaypointRuntime runtime = new StaticWaypointRuntime(
                Logger.getAnonymousLogger());
        runtime.configureAndLoad(WaypointClientConfiguration.from(properties));
        ServerIdentity server = ServerIdentity.of(new ServerEndpoint(
                "example.test", 3724, 27016), "Example", "Example",
                ServerIdentity.Resolution.RESOLVED);
        WaypointManagerContext context = new WaypointManagerContext(
                "Tester", server, 10.0d, 20.0d, 2.0d, WaypointLayer.SURFACE);
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        SurroundingEntry item = SurroundingEntry.builder()
                .kind(SurroundingKind.ITEM).wurmId(77L).name("stone shard")
                .modelName("model.resource.stone")
                .category(SurroundingsClassifier.RESOURCES).material("stone")
                .position(40.0d, 80.0d, 2.0d).layer(0)
                .firstSeenAt(now).updatedAt(now).build();
        assertEquals(1, runtime.setSurroundingsWaypoints(
                Collections.singletonList(item),
                Collections.singletonList(item.getKey()), true, context, now));
        UUID markedId = runtime.revisionSnapshot().getRecords().get(0).getId();

        List<UUID> removed = runtime.removeVanishedSurroundingsWaypoint(
                item.getKey());

        assertEquals(Collections.singletonList(markedId), removed);
        assertEquals(0, runtime.recordCount());
        assertTrue(runtime.surroundingsWaypointKeys().isEmpty());
        assertTrue(runtime.removeVanishedSurroundingsWaypoint(
                item.getKey()).isEmpty());
    }

    @Test public void externalObjectMarkIsOwnedStyledAndRemovedWithSubject()
            throws Exception {
        Path directory = temporary.newFolder("external-object-mark").toPath();
        Properties properties = new Properties();
        properties.setProperty("waypointDataFile",
                directory.resolve("waypoints.wpt").toString());
        properties.setProperty("waypointTransferFile",
                directory.resolve("transfer.wpt").toString());
        properties.setProperty("vanillaLandmarkStateFile",
                directory.resolve("vanilla.state").toString());
        StaticWaypointRuntime runtime = new StaticWaypointRuntime(
                Logger.getAnonymousLogger());
        runtime.configureAndLoad(WaypointClientConfiguration.from(properties));
        ServerIdentity server = ServerIdentity.of(new ServerEndpoint(
                "example.test", 3724, 27016), "Example", "Example",
                ServerIdentity.Resolution.RESOLVED);
        WaypointManagerContext context = new WaypointManagerContext(
                "Tester", server, 10.0d, 20.0d, 2.0d, WaypointLayer.SURFACE);
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        SurroundingEntry spider = SurroundingEntry.builder()
                .kind(SurroundingKind.ANIMAL).wurmId(91L).name("spider")
                .modelName("model.creature.spider")
                .category(SurroundingsClassifier.ANIMALS).material("flesh")
                .position(40.0d, 80.0d, 2.0d).layer(0)
                .firstSeenAt(now).updatedAt(now).build();
        MarkerStyle target = new MarkerStyle(
                MarkerStyle.WorldStyle.TARGET_CROSSHAIR,
                1.0f, 0.2f, 0.1f, 0.9f, 12.0f, 2.0f, true, true);

        UUID id = runtime.upsertExternalObjectWaypoint(spider,
                "test.focus", "selected:91", target, 900, context, now);

        assertTrue(runtime.isOwnedExternalMarker("test.focus", id));
        assertEquals(target, runtime.revisionSnapshot().getRecords().get(0)
                .getMarkerStyle());
        assertEquals(now.plusSeconds(900), runtime.revisionSnapshot()
                .getRecords().get(0).getExpiresAt());

        SurroundingEntry movedSpider = SurroundingEntry.builder()
                .kind(SurroundingKind.ANIMAL).wurmId(91L).name("spider")
                .modelName("model.creature.spider")
                .category(SurroundingsClassifier.ANIMALS).material("flesh")
                .position(48.0d, 92.0d, 3.0d).layer(0)
                .firstSeenAt(now).updatedAt(now.plusSeconds(1)).build();
        assertEquals(1, runtime.refreshExternalObjectWaypoints(
                movedSpider, now.plusSeconds(1)));
        assertEquals(12.0d, runtime.revisionSnapshot().getRecords().get(0)
                .getCoordinate().getTileX(), 0.0001d);
        assertEquals(23.0d, runtime.revisionSnapshot().getRecords().get(0)
                .getCoordinate().getTileY(), 0.0001d);
        assertEquals(Double.valueOf(3.0d), runtime.revisionSnapshot()
                .getRecords().get(0).getCoordinate().getHeight());

        assertEquals(Collections.singletonList(id),
                runtime.removeVanishedSurroundingsWaypoint(spider.getKey()));
        assertEquals(0, runtime.recordCount());
    }

    @Test public void externalVehicleMarkIsAnchoredAboveVehicleRoof() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        SurroundingEntry wagon = SurroundingEntry.builder()
                .kind(SurroundingKind.CONTAINER).wurmId(500L)
                .name("rare wagon").modelName("model.transports.medium.wagon")
                .category(SurroundingsClassifier.VEHICLES).material("wood")
                .position(40.0d, 80.0d, 0.7d).layer(0)
                .firstSeenAt(now).updatedAt(now).build();

        assertEquals(3.1d,
                StaticWaypointRuntime.externalObjectCoordinate(wagon)
                        .getHeight().doubleValue(), 0.0001d);
    }
}
