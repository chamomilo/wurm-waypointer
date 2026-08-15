package org.waypoints.next.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.service.WaypointRevisionSnapshot;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VanillaLandmarkRuntimeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void capturesOnlyThreeProtocolTypesAsDefaultOnSystemRecords()
            throws Exception {
        Properties properties = new Properties();
        String stateFile = temporary.newFolder("state").toPath()
                .resolve("vanilla.state").toString();
        properties.setProperty("vanillaLandmarkStateFile", stateFile);
        VanillaLandmarkRuntime runtime = new VanillaLandmarkRuntime(
                Logger.getAnonymousLogger());
        runtime.configure(WaypointClientConfiguration.from(properties));

        assertFalse(runtime.capture(9L, (short) 1, 1.0f, 2.0f, 3.0f, 0));
        assertTrue(runtime.capture(10L, (short) 2, 40.0f, 80.0f, 5.0f, 0));
        assertTrue(runtime.capture(11L, (short) 3, 44.0f, 84.0f, 6.0f, 0));
        assertTrue(runtime.capture(12L, (short) 25, 48.0f, 88.0f, 7.0f, 0));
        runtime.bind(server());

        List<WaypointRecord> records = runtime.records();
        assertEquals(3, records.size());
        assertEquals("Vanilla White Light", records.get(0).getName());
        assertEquals("Vanilla Black Light", records.get(1).getName());
        assertEquals("Vanilla Rift", records.get(2).getName());
        for (WaypointRecord record : records) {
            assertTrue(record.isEnabled());
            assertEquals(WaypointSourceType.VANILLA_SYSTEM,
                    record.getSourceType());
        }
        assertEquals(MarkerStyle.WorldStyle.RIFT,
                records.get(2).getMarkerStyle().getWorldStyle());
        assertEquals(1.0f, records.get(2).getMarkerStyle().getRed(), 0.0f);
        assertEquals(0.0f, records.get(2).getMarkerStyle().getGreen(), 0.0f);

        WaypointRevisionSnapshot combined = runtime.combine(
                new WaypointRevisionSnapshot(7L,
                        Collections.<WaypointRecord>emptyList()));
        assertEquals(3, combined.getRecords().size());

        UUID riftId = records.get(2).getId();
        assertTrue(runtime.setEnabled(riftId, false));
        VanillaLandmarkRuntime restored = new VanillaLandmarkRuntime(
                Logger.getAnonymousLogger());
        restored.configure(WaypointClientConfiguration.from(properties));
        restored.capture(12L, (short) 25, 48.0f, 88.0f, 7.0f, 0);
        restored.bind(server());
        assertFalse(restored.records().get(0).isEnabled());

        runtime.removed(11L);
        assertEquals(2, runtime.records().size());
    }

    private static ServerIdentity server() {
        return ServerIdentity.of(new ServerEndpoint("example.test", 3724, 27016),
                "Example", "Example", ServerIdentity.Resolution.RESOLVED);
    }
}
