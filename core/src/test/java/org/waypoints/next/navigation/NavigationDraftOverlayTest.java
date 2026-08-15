package org.waypoints.next.navigation;

import org.junit.Test;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class NavigationDraftOverlayTest {
    private static final String SERVER = "127.0.0.1:3724";

    @Test public void editDraftReplacesStoredTargetWithoutMutatingStoredSnapshot() {
        UUID id = UUID.randomUUID();
        NavigationTarget original = target(id, "Original", 10, 20);
        NavigationSnapshot stored = new NavigationSnapshot(7L, 9L,
                Arrays.asList(original));
        MarkerStyle changed = new MarkerStyle(MarkerStyle.WorldStyle.PLUS,
                0.2f, 0.8f, 0.4f, 0.35f, 14.0f, 3.0f, true, true);
        NavigationSnapshot preview = NavigationDraftOverlay.apply(stored, SERVER,
                UUID.randomUUID(), id, "Draft", coordinate(30, 40), changed, 1L);
        assertEquals(1, preview.getTargets().size());
        assertEquals("Draft", preview.getTargets().get(0).getName());
        assertEquals(changed, preview.getTargets().get(0).getMarkerStyle());
        assertTrue(preview.getTargets().get(0).isSelected());
        assertEquals("Original", stored.getTargets().get(0).getName());
        assertNotEquals(stored.getGeneration(), preview.getGeneration());
    }

    @Test public void addDraftUsesDedicatedIdAndDoesNotEnterStoredSnapshot() {
        UUID draftId = UUID.randomUUID();
        NavigationSnapshot stored = NavigationSnapshot.empty();
        NavigationSnapshot preview = NavigationDraftOverlay.apply(stored, SERVER,
                draftId, null, "", coordinate(12, 13),
                MarkerStyle.defaultColoredBeam(), 3L);
        assertEquals(1, preview.getTargets().size());
        assertEquals(draftId, preview.getTargets().get(0).getKey().getWaypointId());
        assertEquals("Live preview", preview.getTargets().get(0).getName());
        assertTrue(stored.getTargets().isEmpty());
    }

    @Test public void hiddenEditSuppressesTheStoredTargetDuringPreview() {
        UUID id = UUID.randomUUID();
        NavigationSnapshot stored = new NavigationSnapshot(2L, 3L,
                Arrays.asList(target(id, "Visible", 10, 20)));
        MarkerStyle hidden = new MarkerStyle(MarkerStyle.WorldStyle.HIDDEN,
                1.0f, 1.0f, 1.0f, 1.0f, 9.0f, 2.0f, true, true);
        NavigationSnapshot preview = NavigationDraftOverlay.apply(stored, SERVER,
                UUID.randomUUID(), id, "Hidden", coordinate(10, 20), hidden, 4L);
        assertTrue(preview.getTargets().isEmpty());
        assertEquals(1, stored.getTargets().size());
    }

    @Test public void editDraftPreservesTemporaryLabelDeadline() {
        UUID id = UUID.randomUUID();
        NavigationTarget original = new NavigationTarget(
                new NavigationTargetKey(SERVER, id), "Temporary",
                coordinate(10, 20), MarkerStyle.defaultColoredBeam(),
                org.waypoints.next.model.WaypointSourceType.STATIC,
                false, true, 0, 1_700_000_900_000L);
        NavigationSnapshot preview = NavigationDraftOverlay.apply(
                new NavigationSnapshot(2L, 3L, Arrays.asList(original)), SERVER,
                UUID.randomUUID(), id, "Draft", coordinate(10, 20),
                MarkerStyle.defaultColoredBeam(), 4L);
        assertEquals(1_700_000_900_000L,
                preview.getTargets().get(0).getExpiresAtEpochMillis());
    }

    @Test public void editDraftPreservesNavigatorOwnershipAndUsesDraftColor() {
        UUID id = UUID.randomUUID();
        NavigationTarget original = new NavigationTarget(
                new NavigationTargetKey(SERVER, id), "Active",
                coordinate(10, 20), MarkerStyle.defaultColoredBeam(),
                org.waypoints.next.model.WaypointSourceType.STATIC,
                true, true, 0, 0L, true);
        MarkerStyle changed = new MarkerStyle(MarkerStyle.WorldStyle.DIAMOND,
                0.1f, 0.6f, 0.9f, 0.45f, 11.0f, 2.0f, true, true);

        NavigationSnapshot preview = NavigationDraftOverlay.apply(
                new NavigationSnapshot(2L, 3L, Arrays.asList(original)), SERVER,
                UUID.randomUUID(), id, "Draft", coordinate(30, 40), changed, 5L);

        assertTrue(preview.getActiveNavigator().isNavigatorActive());
        assertEquals(changed, preview.getActiveNavigator().getMarkerStyle());
    }

    private static NavigationTarget target(UUID id, String name, double x, double y) {
        return new NavigationTarget(new NavigationTargetKey(SERVER, id), name,
                coordinate(x, y), MarkerStyle.defaultColoredBeam(),
                org.waypoints.next.model.WaypointSourceType.STATIC, false, true, 0);
    }

    private static WaypointCoordinate coordinate(double x, double y) {
        return new WaypointCoordinate(x, y, null, WaypointLayer.SURFACE);
    }
}
