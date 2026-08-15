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
import java.util.Properties;
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
}
