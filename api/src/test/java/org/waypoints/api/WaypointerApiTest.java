package org.waypoints.api;

import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WaypointerApiTest {
    private WaypointerService installed;

    @After public void tearDown() {
        if (installed != null) WaypointerApi.uninstallRuntime(installed);
    }

    @Test public void reportsInstallationVersionAndCapabilities() {
        assertFalse(WaypointerApi.isInstalled());
        assertEquals(0, WaypointerApi.apiVersion());
        installed = service();
        WaypointerApi.installRuntime(installed);
        assertTrue(WaypointerApi.isInstalled());
        assertEquals(2, WaypointerApi.apiVersion());
        assertTrue(WaypointerApi.capabilities().contains(
                WaypointerCapability.OBJECT_MARKS));
    }

    @Test public void unavailableMarkIsNonThrowing() {
        ObjectMarkRequest request = ObjectMarkRequest.builder()
                .ownerId("test.mod").markerKey("spider")
                .subject(new WurmObjectRef(WurmObjectKind.CREATURE, 42L))
                .build();
        assertEquals(MarkResult.Status.WAYPOINTER_NOT_INSTALLED,
                WaypointerApi.markObject(request).getStatus());
    }

    @Test public void requestCarriesFallbackSnapshotForUncataloguedObjects() {
        WurmObjectSnapshot snapshot = new WurmObjectSnapshot(
                "Oleander hedge", 120.5d, 240.25d, 3.0d, 0);
        ObjectMarkRequest request = ObjectMarkRequest.builder()
                .ownerId("test.mod").markerKey("hedge")
                .subject(new WurmObjectRef(WurmObjectKind.AUTO, 77L))
                .snapshot(snapshot).build();

        assertEquals(snapshot, request.getSnapshot());
        assertEquals("Oleander hedge", request.getSnapshot().getName());
        assertEquals(120.5d, request.getSnapshot().getWorldX(), 0.0d);
    }

    @Test public void reflectionFriendlyMarkCommandUsesInstalledProvider() {
        installed = service();
        WaypointerApi.installRuntime(installed);
        assertTrue(WaypointerApi.markObject("test.mod", "selected:42",
                "CREATURE", 42L, "TARGET", true));
    }

    private static WaypointerService service() {
        return new WaypointerService() {
            @Override public int apiVersion() { return 2; }
            @Override public Set<WaypointerCapability> capabilities() {
                return Collections.singleton(WaypointerCapability.OBJECT_MARKS);
            }
            @Override public MarkResult markObject(ObjectMarkRequest request) {
                return MarkResult.success(UUID.randomUUID());
            }
            @Override public int subjectVanished(WurmObjectRef subject) { return 0; }
            @Override public boolean removeOwnedMarker(String ownerId, UUID id) {
                return false;
            }
            @Override public boolean setNavigation(String ownerId, UUID id,
                                                   boolean active) { return false; }
        };
    }
}
